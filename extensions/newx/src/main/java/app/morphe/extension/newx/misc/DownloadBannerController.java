package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/** Main-thread adapter: native Compose state updates keep the same entry and animation lifetime. */
final class DownloadBannerController {
    private final DownloadBannerPolicy policy = new DownloadBannerPolicy();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<TextBinding> texts = new ArrayList<>();
    private boolean scheduled;

    void start(long id, String title, String detail, Runnable cancel) {
        main.post(() -> { policy.start(id, title, detail, cancel); schedule(); });
    }
    void progress(long id, String title, String detail, int percent) {
        main.post(() -> {
            DownloadBannerPolicy.Notice notice = policy.update(id, title, detail, percent);
            if (notice != null && notice.model != null) {
                NewXInAppNotification.writeState(notice.titleState, title);
                NewXInAppNotification.writeState(notice.detailState, detail == null ? "" : detail);
                NewXInAppNotification.writeState(notice.fractionState, percent < 0 ? -1f : Math.min(99, percent) / 100f);
            }
            schedule();
        });
    }
    void finish(long id) { main.post(() -> { policy.finish(id); schedule(); }); }
    void result(long id, int kind, String title, String detail) {
        main.post(() -> { policy.result(id, kind, title, detail); schedule(); });
    }
    int decide(Object incoming, Object current) { return policy.decide(incoming, current); }
    void visible(Object model) { policy.visible(model); main.post(this::schedule); }
    void disposed(Object model, String reason, boolean current) {
        Runnable cancel = policy.disposed(model, reason, current);
        main.post(() -> { if (cancel != null) cancel.run(); schedule(); });
    }

    boolean fullContent(Object model) { return policy.fullContent(model); }
    void drawProgress(Object model, Object composer) {
        DownloadBannerPolicy.Notice notice = policy.progressNotice(model);
        if (notice == null || notice.fractionState == null) return;
        Object value = NewXInAppNotification.readValue(notice.fractionState);
        NewXInAppNotification.drawNativeProgress(notice.provider, composer,
                !(value instanceof Number number) || number.floatValue() < 0);
    }

    /** Called inside the existing native text composable so Snapshot records its read dependency. */
    synchronized String text(Object literal) {
        String value = null;
        for (Iterator<TextBinding> iterator = texts.iterator(); iterator.hasNext();) {
            TextBinding binding = iterator.next();
            Object key = binding.get();
            if (key == null) iterator.remove();
            else if (key == literal) value = NewXInAppNotification.readState(binding.state);
        }
        return value;
    }

    private synchronized void bindText(Object literal, Object state) { texts.add(new TextBinding(literal, state)); }
    private void schedule() {
        if (!scheduled) { scheduled = true; main.post(this::pump); }
    }
    private void pump() {
        scheduled = false;
        DownloadBannerPolicy.Action action = null;
        try {
            Activity activity = NewXUtils.findUsableActivity(Utils.getContext());
            if (activity != null && activity.hasWindowFocus()) {
                action = policy.next();
                if (action != null) {
                    if (action.clear != null) {
                        Object model = NewXInAppNotification.createRich("", "", DownloadBannerPolicy.CLEAR, null);
                        if (model == null) policy.clearRejected();
                        else {
                            policy.bindClear(model, action.clear);
                            if (!NewXInAppNotification.postRich(model, DownloadBannerPolicy.CLEAR)) policy.clearRejected();
                        }
                    } else {
                        DownloadBannerPolicy.Notice notice = action.notice;
                        if (notice.model == null) {
                            Object button = notice.cancel == null ? null
                                    : NewXInAppNotification.cancelButton(StringRef.str("piko_newx_settings_cancel"));
                            Object model = NewXInAppNotification.createRich(notice.title,
                                    notice.detail == null ? "" : notice.detail, notice.kind, button);
                            if (model != null) {
                                if (notice.kind == DownloadBannerPolicy.PROGRESS) {
                                    notice.titleState = NewXInAppNotification.makeState(notice.title);
                                    notice.detailState = NewXInAppNotification.makeState(notice.detail == null ? "" : notice.detail);
                                    notice.fractionState = NewXInAppNotification.makeState(notice.percent < 0 ? -1f : notice.percent / 100f);
                                    notice.provider = new NewXInAppNotification.ProgressProvider(notice.fractionState);
                                    bindText(NewXInAppNotification.titleLiteral(model), notice.titleState);
                                    bindText(NewXInAppNotification.detailLiteral(model), notice.detailState);
                                }
                                policy.bind(notice, model);
                            }
                        }
                        if (notice.model == null || !NewXInAppNotification.postRich(notice.model, notice.kind)) policy.rejected(notice);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            if (action != null) { policy.rejected(action.notice); if (action.clear != null) policy.clearRejected(); }
        }
        if (policy.hasWork()) { scheduled = true; main.postDelayed(this::pump, 500); }
    }
    private static final class TextBinding extends WeakReference<Object> {
        final Object state;
        TextBinding(Object literal, Object state) { super(literal); this.state = state; }
    }
}
