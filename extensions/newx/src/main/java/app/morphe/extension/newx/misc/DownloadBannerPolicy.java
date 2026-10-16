package app.morphe.extension.newx.misc;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;

/** Per-task ownership and acknowledgement of the native host, never a timer-based replacement. */
final class DownloadBannerPolicy {
    static final int INFO = 0, SUCCESS = 1, ERROR = 2, PROGRESS = 3, CLEAR = 4;
    static final int NATIVE = 0, DROP = 2, DISMISS = 3;
    final LinkedHashMap<Long, Notice> progress = new LinkedHashMap<>();
    final ArrayDeque<Notice> results = new ArrayDeque<>();
    private final LinkedHashMap<Long, Boolean> completed = new LinkedHashMap<>();
    private final ArrayList<Registration> owned = new ArrayList<>();
    private Object visible;
    private Notice visibleNotice;
    private Object clearTarget;

    synchronized void start(long id, String title, String detail, Runnable cancel) {
        if (progress.containsKey(id)) return;
        completed.remove(id);
        progress.put(id, new Notice(id, PROGRESS, title, detail, cancel));
    }

    synchronized Notice update(long id, String title, String detail, int percent) {
        Notice notice = progress.get(id);
        if (notice != null) { notice.title = title; notice.detail = detail; notice.percent = percent; }
        return notice;
    }

    synchronized void finish(long id) { progress.remove(id); }

    synchronized boolean result(long id, int kind, String title, String detail) {
        if (completed.containsKey(id)) return false;
        progress.remove(id);
        completed.put(id, true);
        if (completed.size() > 256) completed.remove(completed.keySet().iterator().next());
        results.add(new Notice(id, kind, title, detail, null));
        return true;
    }

    private Notice desired() {
        if (!results.isEmpty()) return results.peek();
        for (Notice notice : progress.values()) if (!notice.hidden) return notice;
        return null;
    }

    /** One dispatch until native admission/disposal acknowledges it. Progress edits never dispatch. */
    synchronized Action next() {
        Notice wanted = desired();
        Registration current = lookup(visible);
        Notice showing = visibleNotice;
        if (visible != null) {
            if (showing != null && showing.kind == PROGRESS && showing != wanted && clearTarget == null) {
                clearTarget = visible;
                return new Action(null, visible);
            }
            return null;
        }
        if (wanted == null || wanted.inFlight) return null;
        wanted.inFlight = true;
        return new Action(wanted, null);
    }

    synchronized void bind(Notice notice, Object model) {
        notice.model = model;
        owned.add(new Registration(model, notice, null));
    }

    synchronized void bindClear(Object model, Object target) {
        owned.add(new Registration(model, null, target));
    }

    synchronized void rejected(Notice notice) { if (notice != null) notice.inFlight = false; }
    synchronized void clearRejected() { clearTarget = null; }
    synchronized boolean hasWork() { return visible != null || !progress.isEmpty() || !results.isEmpty(); }

    /** Actor gate protects native messages and drops obsolete or already admitted extension events. */
    synchronized int decide(Object incoming, Object current) {
        visible = current;
        Registration entry = lookup(incoming);
        if (entry == null) return NATIVE;
        if (entry.clear != null) {
            Registration target = lookup(current);
            Notice notice = target == null ? null : target.notice.get();
            return current == entry.clear && current == clearTarget && notice != null
                    && notice.kind == PROGRESS && notice != desired() ? DISMISS : DROP;
        }
        Notice notice = entry.notice.get();
        if (notice == null || notice != desired() || current != null || notice.admitted) return DROP;
        notice.admitted = true;
        return NATIVE;
    }

    synchronized void visible(Object model) {
        visible = model;
        clearTarget = null;
        Registration entry = lookup(model);
        Notice notice = entry == null ? null : entry.notice.get();
        visibleNotice = notice;
        if (notice != null) { notice.inFlight = false; notice.admitted = true; }
    }

    /** Only disposing the actual current entry can acknowledge a result or cancel its task. */
    synchronized Runnable disposed(Object model, String reason, boolean wasCurrent) {
        Registration entry = lookup(model);
        if (entry == null) return null;
        if (entry.clear != null) { clearTarget = null; return null; }
        Notice notice = entry.notice.get();
        if (notice == null) return null;
        // A duplicate entry with the same model must not reset the real visible entry.
        if (!wasCurrent && visible == model) return null;
        notice.inFlight = false;
        notice.admitted = false;
        if (!wasCurrent) return null;
        visible = null;
        visibleNotice = null;
        if (notice.kind != PROGRESS) {
            if (!"Replaced".equals(reason) && results.peek() == notice) results.remove();
        } else if ("PrimaryButtonClick".equals(reason) || "DismissedByUser".equals(reason)) {
            notice.hidden = true;
            if ("PrimaryButtonClick".equals(reason)) return notice.cancel;
        }
        return null;
    }

    synchronized boolean fullContent(Object model) {
        Registration registration = lookup(model);
        return registration != null && registration.kind != CLEAR;
    }

    synchronized Notice progressNotice(Object model) {
        Registration registration = lookup(model);
        Notice notice = registration == null ? null : registration.notice.get();
        return notice != null && notice.kind == PROGRESS ? notice : null;
    }

    private Registration lookup(Object model) {
        if (model == null) return null;
        Registration found = null;
        for (Iterator<Registration> it = owned.iterator(); it.hasNext();) {
            Registration entry = it.next();
            Object value = entry.get();
            if (value == null) it.remove();
            else if (value == model) found = entry;
        }
        return found;
    }

    static final class Notice {
        final long id;
        final int kind;
        final Runnable cancel;
        String title, detail;
        Object model, titleState, detailState, fractionState, provider;
        int percent = -1;
        boolean inFlight, admitted, hidden;
        Notice(long id, int kind, String title, String detail, Runnable cancel) {
            this.id = id; this.kind = kind; this.title = title; this.detail = detail; this.cancel = cancel;
        }
    }

    static final class Action {
        final Notice notice;
        final Object clear;
        Action(Notice notice, Object clear) { this.notice = notice; this.clear = clear; }
    }

    private static final class Registration extends WeakReference<Object> {
        final WeakReference<Notice> notice;
        final Object clear;
        final int kind;
        Registration(Object model, Notice notice, Object clear) {
            super(model); this.notice = new WeakReference<>(notice); this.clear = clear; this.kind = notice == null ? CLEAR : notice.kind;
        }
    }
}
