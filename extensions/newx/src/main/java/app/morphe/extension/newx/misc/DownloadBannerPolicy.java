package app.morphe.extension.newx.misc;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

/** Message ownership and precedence at the native notification actor, before its queue. */
final class DownloadBannerPolicy {
    static final int INFO = 0, SUCCESS = 1, ERROR = 2, PROGRESS = 3, CLEAR = 4;
    static final int NATIVE = 0, REPLACE = 1, DROP = 2, DISMISS = 3;
    private static final long RESULT_HOLD_MS = 5000;
    private final ArrayList<Notice> pending = new ArrayList<>();
    private Object visible;
    private int visibleKind;
    private long visibleUntil, generation;
    private boolean active;

    synchronized void beginProgress() {
        if (!active) { active = true; generation++; }
    }

    synchronized boolean endProgress() {
        if (!active) return false;
        active = false;
        generation++;
        return true;
    }

    synchronized void register(Object notification, int kind) {
        forget(notification);
        pending.add(new Notice(notification, kind, generation));
    }

    synchronized void forget(Object notification) { take(notification); }

    private Notice take(Object notification) {
        Notice found = null;
        for (Iterator<Notice> iterator = pending.iterator(); iterator.hasNext();) {
            Notice notice = iterator.next();
            Object value = notice.get();
            if (value == null || value == notification) {
                iterator.remove();
                if (value == notification) found = notice;
            }
        }
        return found;
    }

    /** Only messages registered by this extension are intercepted; native messages pass through. */
    synchronized int decide(Object incoming, Object current, long now) {
        Notice notice = take(incoming);
        if (notice == null) return NATIVE;
        int kind = notice.kind;
        if (kind == CLEAR) {
            return !active && notice.generation == generation && current != null
                    && current == visible && visibleKind == PROGRESS ? DISMISS : DROP;
        }
        if (kind == PROGRESS) {
            if (!active || notice.generation != generation) return DROP;
            if (current != null && (current != visible
                    || visibleKind != PROGRESS && now < visibleUntil)) return DROP;
        } else if (current != null && current == visible && visibleKind != PROGRESS
                && importance(kind) < importance(visibleKind) && now < visibleUntil) {
            return DROP;
        }
        boolean replace = current != null && current == visible;
        visible = incoming;
        visibleKind = kind;
        visibleUntil = now + RESULT_HOLD_MS;
        return replace ? REPLACE : NATIVE;
    }

    private static int importance(int kind) { return kind == ERROR ? 2 : kind == SUCCESS ? 1 : 0; }
    private static final class Notice extends WeakReference<Object> {
        final int kind;
        final long generation;
        Notice(Object model, int kind, long generation) { super(model); this.kind = kind; this.generation = generation; }
    }
}
