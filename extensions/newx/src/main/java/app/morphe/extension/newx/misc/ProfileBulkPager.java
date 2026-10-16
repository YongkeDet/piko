package app.morphe.extension.newx.misc;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** One native media timeline. A repeat cursor, error or timeout can never count as completion. */
final class ProfileBulkPager {
    enum Step { WAIT, PROCESS, NEXT, DONE, FAILED }
    private static final long TIMEOUT_MS = 45_000;
    private static final long SETTLE_MS = 1_000;
    private final Set<String> requested = new HashSet<>();
    private Object processedItems;
    private String processedCursor;
    private boolean processed;
    private boolean awaiting;
    private String requestCursor;
    private long lastProgress;
    private long stableSince = -1;
    private String stableCursor;
    private Object stableItems;
    private boolean stableTerminated;

    ProfileBulkPager(long now) { lastProgress = now; }

    Step step(long now, Object items, boolean canPage, boolean terminated, boolean loading,
              String cursor, boolean failure, boolean fetched) {
        if (failure) return Step.FAILED;
        if (now - lastProgress >= TIMEOUT_MS) return Step.FAILED;
        if (loading || items == null) {
            stableSince = -1;
            return Step.WAIT;
        }
        // Native NO_CURSOR is a separate end condition from an explicit terminate instruction.
        // It is only authoritative after a successful network fetch, never on an initial or
        // cached blank state while the first page is still unavailable.
        terminated = terminated || fetched && cursor == null && !canPage;
        if (awaiting) {
            boolean advanced = !Objects.equals(cursor, requestCursor) || terminated;
            if (!advanced) {
                // A network event can precede the cursor/state emission. Wait, then time out;
                // never re-request this cursor or mistake that intermediate state for the end.
                return Step.WAIT;
            }
        }
        // Native paging and timeline flows are distinct. Let both settle before consuming the
        // page, including the final page that arrived together with the termination marker.
        if (stableSince < 0 || !Objects.equals(stableCursor, cursor) ||
                stableItems != items || stableTerminated != terminated) {
            stableSince = now;
            stableCursor = cursor;
            stableItems = items;
            stableTerminated = terminated;
            return Step.WAIT;
        }
        if (now - stableSince < SETTLE_MS) return Step.WAIT;
        if (awaiting) {
            awaiting = false;
            lastProgress = now;
        }
        if (!processed || processedItems != items || !Objects.equals(processedCursor, cursor)) {
            return Step.PROCESS;
        }
        if (terminated) return Step.DONE;
        if (!canPage) return Step.WAIT;
        return requested.contains(cursor) ? Step.FAILED : Step.NEXT;
    }

    void processed(Object items, String cursor, long now) {
        processed = true;
        processedItems = items;
        processedCursor = cursor;
        lastProgress = now;
    }

    void requested(String cursor, long now) {
        requested.add(cursor);
        awaiting = true;
        requestCursor = cursor;
        lastProgress = now;
        stableSince = -1;
    }
}
