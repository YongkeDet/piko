package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.crimera.downloader.DownloadEngine;
import app.morphe.extension.crimera.downloader.events.DownloadEvent;
import app.morphe.extension.crimera.downloader.events.DownloadListener;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/** A single, non-modal foreground progress card; batch transfers use their existing dialog. */
final class DownloadProgressDisplay implements DownloadListener {
    private static final DownloadProgressState STATE = new DownloadProgressState();
    private static final AtomicInteger LOCAL_IDS = new AtomicInteger(-1);
    private static volatile DownloadProgressDisplay current;
    private final DownloadEngine engine;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout card;
    private TextView title, details;
    private ProgressBar bar;
    private FrameLayout parent;
    private boolean scheduled;

    DownloadProgressDisplay(DownloadEngine engine) { this.engine = engine; current = this; }

    /** Local merge downloads use negative IDs, separate from the shared engine's positive IDs. */
    static int beginLocal(String name) {
        NewXDownloader.get();
        int id = LOCAL_IDS.getAndDecrement();
        localStage(id, name);
        return id;
    }

    static void localStage(int id, String name) {
        DownloadProgressDisplay display = current;
        if (display == null) return;
        display.main.post(() -> {
            STATE.queued(id, name, false, SystemClock.elapsedRealtime());
            display.refresh();
        });
    }

    static void localProgress(int id, long done, long total) {
        DownloadProgressDisplay display = current;
        if (display == null) return;
        display.main.post(() -> {
            STATE.progress(id, done, total, SystemClock.elapsedRealtime());
            display.refresh();
        });
    }

    static void endLocal(int id) {
        DownloadProgressDisplay display = current;
        if (display != null) display.main.post(() -> display.end(id));
    }

    static DownloadProgressState.Snapshot batchSnapshot() {
        return STATE.snapshot(true, SystemClock.elapsedRealtime());
    }

    static String amount(Context context, DownloadProgressState.Snapshot snapshot) {
        String done = Formatter.formatShortFileSize(context, snapshot.done);
        String speed = Formatter.formatShortFileSize(context, snapshot.speed);
        return snapshot.total > 0
                ? StringRef.str("piko_newx_download_progress_amount", done,
                    Formatter.formatShortFileSize(context, snapshot.total), speed)
                : StringRef.str("piko_newx_download_progress_unknown_amount", done, speed);
    }

    @Override public void onQueued(DownloadEvent.Queued event) {
        DownloadRequest request = engine.requestFor(event.id());
        STATE.queued(event.id(), request == null ? event.label() : request.fileName(),
                ProfileBulkDownload.isBatchRequest(request), SystemClock.elapsedRealtime());
        refresh();
    }
    @Override public void onStarted(DownloadEvent.Started event) { STATE.waiting(event.id(), false); refresh(); }
    @Override public void onWaiting(DownloadEvent.Waiting event) { STATE.waiting(event.id(), true); refresh(); }
    @Override public void onProgress(DownloadEvent.Progress event) {
        STATE.progress(event.id(), event.bytesDone(), event.totalBytes(), SystemClock.elapsedRealtime());
        refresh();
    }
    @Override public void onCompleted(DownloadEvent.Completed event) { end(event.id()); }
    @Override public void onFailed(DownloadEvent.Failed event) { end(event.id()); }
    @Override public void onCancelled(DownloadEvent.Cancelled event) { end(event.id()); }
    private void end(int id) { STATE.finished(id); refresh(); }

    private void refresh() {
        if (scheduled) return;
        scheduled = true;
        main.post(this::tick);
    }

    private void tick() {
        DownloadProgressState.Snapshot snapshot = STATE.snapshot(false, SystemClock.elapsedRealtime());
        if (snapshot.count == 0) { detach(); scheduled = false; return; }
        try {
            Activity activity = NewXUtils.findUsableActivity(Utils.getContext());
            View content = activity == null ? null : activity.findViewById(android.R.id.content);
            if (!(content instanceof FrameLayout frame)) detach();
            else {
                if (parent != frame) { detach(); attach(activity, frame); }
                title.setText(snapshot.waiting == snapshot.count
                        ? StringRef.str("piko_newx_download_progress_waiting", snapshot.count)
                        : StringRef.str("piko_newx_download_progress_active", snapshot.count,
                            snapshot.percent < 0 ? "" : snapshot.percent + "%"));
                details.setText(snapshot.name + "\n" + amount(activity, snapshot));
                bar.setIndeterminate(snapshot.percent < 0 || snapshot.waiting == snapshot.count);
                if (snapshot.percent >= 0) bar.setProgress(snapshot.percent);
            }
        } catch (RuntimeException error) {
            detach();
        }
        main.postDelayed(this::tick, 500);
    }

    private void attach(Activity activity, FrameLayout frame) {
        parent = frame;
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Theme.dpToPx(activity, 12);
        card.setPadding(pad, pad / 2, pad, pad / 2);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.surfaceContainerHigh(activity));
        background.setCornerRadius(Theme.dpToPx(activity, 14));
        background.setStroke(Theme.dpToPx(activity, 1), Theme.dividerColor(activity));
        card.setBackground(background);
        card.setElevation(Theme.dpToPx(activity, 6));
        title = new TextView(activity);
        title.setTextSize(13); title.setTextColor(Theme.primaryText(activity));
        details = new TextView(activity);
        details.setTextSize(11); details.setTextColor(Theme.secondaryText(activity));
        details.setMaxLines(2); details.setEllipsize(TextUtils.TruncateAt.END);
        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(Theme.primaryAccent(activity)));
        bar.setIndeterminateTintList(ColorStateList.valueOf(Theme.primaryAccent(activity)));
        card.addView(title); card.addView(details);
        card.addView(bar, new LinearLayout.LayoutParams(-1, Theme.dpToPx(activity, 6)));
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        layout.leftMargin = pad; layout.rightMargin = pad;
        int[] location = new int[2]; frame.getLocationOnScreen(location);
        int inset = frame.getRootWindowInsets() == null ? 0 : frame.getRootWindowInsets().getSystemWindowInsetTop();
        layout.topMargin = Theme.dpToPx(activity, 64) + Math.max(0, inset - location[1]);
        frame.addView(card, layout);
    }

    private void detach() {
        if (parent != null && card != null) parent.removeView(card);
        parent = null; card = null; title = null; details = null; bar = null;
    }
}
