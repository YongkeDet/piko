package app.morphe.extension.newx.misc;

import static app.morphe.extension.newx.misc.ProfileBulkPager.Step.*;
import static app.morphe.extension.newx.misc.ProfileBulkPager.Failure.*;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import org.junit.Test;

/** Real regressions: native Empty tabs, cached failures, lost bootstrap and bounded retries. */
public final class ProfileBulkPagerTest {
    private static final ProfileBulkPager.Network IDLE = ProfileBulkPager.Network.NONE;
    private static ProfileBulkPager.Network event(String value) {
        return ProfileBulkPager.Network.read(new String(value));
    }
    private static ProfileBulkPager.Network success() {
        return event("Success(requestType=OLDER_THAN, cursor=null, durationMs=100)");
    }
    private static ProfileBulkPager.Network failure(int status) {
        return event("Failure(requestType=OLDER_THAN, cursor=null, throwable=null, httpStatus=" + status + ")");
    }
    private static ProfileBulkPager.Step step(ProfileBulkPager p, long now, Object items,
            boolean canPage, boolean end, String cursor, ProfileBulkPager.Network event) {
        return p.step(now, items, canPage, end, false, cursor, event, false);
    }

    @Test public void finalPageMustBeProcessedBeforeCompletion() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object first = new Object(), last = new Object();
        assertEquals(WAIT, step(p, 0, first, true, false, "one", IDLE));
        assertEquals(PROCESS, step(p, 1000, first, true, false, "one", IDLE));
        p.processed(first, "one", 1000);
        assertEquals(NEXT, step(p, 1001, first, true, false, "one", IDLE));
        p.requested("one", 1001, IDLE);
        assertEquals(WAIT, step(p, 1500, last, false, true, "end", IDLE));
        assertEquals(PROCESS, step(p, 2500, last, false, true, "end", IDLE));
        p.processed(last, "end", 2500);
        assertEquals(DONE, step(p, 2501, last, false, true, "end", IDLE));
    }

    @Test public void emptyIntermediatePageWithNewCursorContinues() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object empty = Collections.emptyList();
        p.processed(empty, "one", 0);
        p.requested("one", 1, IDLE);
        assertEquals(WAIT, step(p, 100, empty, true, false, "two", IDLE));
        assertEquals(PROCESS, step(p, 1100, empty, true, false, "two", IDLE));
        p.processed(empty, "two", 1100);
        assertEquals(NEXT, step(p, 1101, empty, true, false, "two", IDLE));
    }

    @Test public void nativeEmptyAfterSuccessfulFetchCompletesInsteadOfTimingOut() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object empty = Collections.emptyList();
        ProfileBulkPager.Network ok = success();
        assertEquals(WAIT, step(p, 0, null, false, false, null, IDLE));
        assertEquals(WAIT, step(p, 100, empty, false, false, null, ok));
        assertEquals(PROCESS, step(p, 1100, empty, false, false, null, ok));
        p.processed(empty, null, 1100);
        assertEquals(DONE, step(p, 1101, empty, false, false, null, ok));
    }

    @Test public void cachedEmptyWithoutSuccessfulFetchIsRefreshedAndNeverFalselyCompleted() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object empty = Collections.emptyList();
        assertEquals(WAIT, step(p, 0, empty, false, false, null, IDLE));
        assertEquals(PROCESS, step(p, 1000, empty, false, false, null, IDLE));
        p.processed(empty, null, 1000);
        assertEquals(WAIT, step(p, 11000, empty, false, false, null, IDLE));
        assertEquals(REFRESH, step(p, 12500, empty, false, false, null, IDLE));
    }

    @Test public void cachedFailureDoesNotAbortAndItsSameEventIsNotAResponseToRetry() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object items = new Object();
        ProfileBulkPager.Network old = failure(503);
        assertEquals(WAIT, step(p, 0, items, false, false, "one", old));
        assertEquals(PROCESS, step(p, 1000, items, false, false, "one", old));
        p.processed(items, "one", 1000);
        assertEquals(WAIT, step(p, 1001, items, false, false, "one", old));
        assertEquals(RETRY, step(p, 2501, items, false, false, "one", old));
        p.requested("one", 2501, old);
        assertEquals(WAIT, step(p, 3000, items, false, false, "one", old));
        ProfileBulkPager.Network ok = success();
        Object next = new Object();
        assertEquals(WAIT, step(p, 4000, next, true, false, "two", ok));
        assertEquals(PROCESS, step(p, 5000, next, true, false, "two", ok));
        p.processed(next, "two", 5000);
        assertEquals(NEXT, step(p, 5001, next, true, false, "two", ok));
    }

    @Test public void newFailuresRetryAtMostThreeAttemptsEvenWithOldTerminationFlag() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object items = new Object();
        step(p, 0, items, true, false, "one", IDLE);
        p.processed(items, "one", 0);
        p.requested("one", 1, IDLE);
        ProfileBulkPager.Network first = failure(503), second = failure(502), third = failure(503);
        assertEquals(WAIT, step(p, 10, items, false, true, "one", first));
        assertEquals(RETRY, step(p, 3010, items, false, true, "one", first));
        p.requested("one", 3010, first);
        assertEquals(WAIT, step(p, 3020, items, false, true, "one", second));
        assertEquals(RETRY, step(p, 7520, items, false, true, "one", second));
        p.requested("one", 7520, second);
        assertEquals(FAILED, step(p, 7530, items, false, true, "one", third));
        assertEquals(NETWORK, p.failure());
        assertEquals(3, p.attempts());
    }

    @Test public void freshAccessDenialAndRateLimitStopWithoutMoreRequests() {
        for (int status : new int[]{401, 403, 404, 429}) {
            ProfileBulkPager p = new ProfileBulkPager(0);
            Object items = new Object();
            step(p, 0, items, true, false, "one", IDLE);
            p.processed(items, "one", 0);
            p.requested("one", 1, IDLE);
            assertEquals(FAILED, step(p, 2, items, false, false, "one", failure(status)));
            assertEquals(status == 429 ? RATE_LIMIT : ACCESS, p.failure());
            assertEquals(status, p.failureStatus());
        }
    }

    @Test public void historicalAccessErrorCanBeRecheckedOnceRatherThanBlockingEveryNewTask() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        ProfileBulkPager.Network old = failure(403);
        assertEquals(WAIT, step(p, 0, null, false, false, null, old));
        assertEquals(REFRESH, step(p, 1500, null, false, false, null, old));
        p.refreshed(1500, null, old);
        assertEquals(WAIT, step(p, 2000, null, false, false, null, old));
        assertEquals(FAILED, step(p, 2500, null, false, false, null, failure(403)));
        assertEquals(ACCESS, p.failure());
    }

    @Test public void missingFirstPageBootstrapsWithNativeRefreshButHasFiniteAttempts() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        assertEquals(WAIT, step(p, 10000, null, false, false, null, IDLE));
        assertEquals(REFRESH, step(p, 11500, null, false, false, null, IDLE));
        p.refreshed(11500, null, IDLE);
        assertEquals(WAIT, step(p, 56500, null, false, false, null, IDLE));
        assertEquals(REFRESH, step(p, 59500, null, false, false, null, IDLE));
        p.refreshed(59500, null, IDLE);
        assertEquals(WAIT, step(p, 104500, null, false, false, null, IDLE));
        assertEquals(REFRESH, step(p, 109000, null, false, false, null, IDLE));
        p.refreshed(109000, null, IDLE);
        assertEquals(FAILED, step(p, 154000, null, false, false, null, IDLE));
        assertEquals(TIMEOUT, p.failure());
    }

    @Test public void refreshWaitsForNewSuccessAndCanFinishAnEmptyVideoTab() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        ProfileBulkPager.Network old = failure(503);
        Object empty = Collections.emptyList();
        step(p, 0, empty, false, false, null, old);
        p.refreshed(10, null, old);
        assertEquals(WAIT, step(p, 1000, empty, false, false, null, old));
        ProfileBulkPager.Network ok = success();
        assertEquals(WAIT, step(p, 1500, empty, false, false, null, ok));
        assertEquals(PROCESS, step(p, 2500, empty, false, false, null, ok));
        p.processed(empty, null, 2500);
        assertEquals(DONE, step(p, 2501, empty, false, false, null, ok));
    }

    @Test public void nativeLoadingFetchingAndBackoffAreNotEndMarkers() {
        Object items = Collections.emptyList();
        for (int mode = 0; mode < 3; mode++) {
            ProfileBulkPager p = new ProfileBulkPager(0);
            ProfileBulkPager.Network event = mode == 1
                    ? event("Fetching(requestType=OLDER_THAN, cursor=null)") : success();
            assertEquals(WAIT, p.step(45000, items, false, false, mode == 0, null, event, mode == 2));
            assertEquals(FAILED, p.step(120000, items, false, false, mode == 0, null, event, mode == 2));
            assertEquals(TIMEOUT, p.failure());
        }
    }

    @Test public void cyclicCursorStopsInsteadOfDownloadingForever() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object items = new Object();
        p.processed(items, "one", 0);
        p.requested("one", 1, IDLE);
        assertEquals(WAIT, step(p, 10, items, true, false, "two", IDLE));
        assertEquals(PROCESS, step(p, 1010, items, true, false, "two", IDLE));
        p.processed(items, "two", 1010);
        p.requested("two", 1011, IDLE);
        assertEquals(WAIT, step(p, 2000, items, true, false, "one", IDLE));
        assertEquals(PROCESS, step(p, 3000, items, true, false, "one", IDLE));
        p.processed(items, "one", 3000);
        assertEquals(FAILED, step(p, 3001, items, true, false, "one", IDLE));
        assertEquals(CURSOR_LOOP, p.failure());
    }

    @Test public void lateTimelineEmissionResetsSettlingBeforeTheFinalDrain() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object old = new Object(), last = new Object();
        p.processed(old, "one", 0);
        p.requested("one", 10, IDLE);
        assertEquals(WAIT, step(p, 100, old, false, true, "end", IDLE));
        assertEquals(WAIT, step(p, 800, last, false, true, "end", IDLE));
        assertEquals(WAIT, step(p, 1100, last, false, true, "end", IDLE));
        assertEquals(PROCESS, step(p, 1800, last, false, true, "end", IDLE));
    }

    @Test public void changingListsCannotKeepTheTaskWaitingForever() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        assertEquals(WAIT, step(p, 119999, new Object(), true, false, "one", IDLE));
        assertEquals(FAILED, step(p, 120000, new Object(), true, false, "one", IDLE));
        assertEquals(TIMEOUT, p.failure());
    }

    @Test public void staleFailureAndStaleTerminationCannotResetRetryAttempts() {
        ProfileBulkPager p = new ProfileBulkPager(0);
        Object items = new Object();
        ProfileBulkPager.Network old = failure(503);
        step(p, 0, items, false, true, "one", old);
        p.processed(items, "one", 0);
        p.requested("one", 1, old);
        assertEquals(WAIT, step(p, 1001, items, false, true, "one", old));
        assertEquals(WAIT, step(p, 45001, items, false, true, "one", old));
        assertEquals(RETRY, step(p, 48001, items, false, true, "one", old));
        p.requested("one", 48001, old);
        assertEquals(2, p.attempts());
        assertEquals(WAIT, step(p, 49001, items, false, true, "one", old));
        assertEquals(2, p.attempts());
    }
}
