package app.morphe.extension.newx.misc;

import static app.morphe.extension.newx.misc.ProfileBulkPager.Step.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Guards against losing the final page, false success on stalls, and duplicate cursor loops. */
public final class ProfileBulkPagerTest {
    @Test public void finalPageMustBeProcessedBeforeCompletion() {
        ProfileBulkPager pager = new ProfileBulkPager(0);
        Object first = new Object(), last = new Object();
        assertEquals(WAIT, pager.step(0, first, true, false, false, "one", false));
        assertEquals(PROCESS, pager.step(1000, first, true, false, false, "one", false));
        pager.processed(first, "one", 1000);
        assertEquals(NEXT, pager.step(1001, first, true, false, false, "one", false));
        pager.requested("one", 1001);
        assertEquals(WAIT, pager.step(1500, last, false, true, false, "end", false));
        assertEquals(PROCESS, pager.step(2500, last, false, true, false, "end", false));
        pager.processed(last, "end", 2500);
        assertEquals(DONE, pager.step(2501, last, false, true, false, "end", false));
    }

    @Test public void emptyIntermediatePageWithNewCursorContinues() {
        ProfileBulkPager pager = new ProfileBulkPager(0);
        Object empty = new Object();
        pager.processed(empty, "one", 0);
        pager.requested("one", 1);
        assertEquals(WAIT, pager.step(100, empty, true, false, false, "two", false));
        assertEquals(PROCESS, pager.step(1100, empty, true, false, false, "two", false));
        pager.processed(empty, "two", 1100);
        assertEquals(NEXT, pager.step(1101, empty, true, false, false, "two", false));
    }

    @Test public void unchangedCursorWaitsForEmissionThenFailsWithoutAnotherRequest() {
        ProfileBulkPager pager = new ProfileBulkPager(0);
        Object items = new Object();
        pager.processed(items, "one", 0);
        pager.requested("one", 10);
        assertEquals(WAIT, pager.step(1000, items, true, false, false, "one", false));
        assertEquals(FAILED, pager.step(45010, items, true, false, false, "one", false));
    }

    @Test public void cyclicCursorStopsInsteadOfDownloadingForever() {
        ProfileBulkPager pager = new ProfileBulkPager(0);
        Object items = new Object();
        pager.requested("one", 0);
        pager.processed(items, "two", 1);
        assertEquals(WAIT, pager.step(2, items, true, false, false, "two", false));
        assertEquals(NEXT, pager.step(1002, items, true, false, false, "two", false));
        pager.requested("two", 1002);
        assertEquals(WAIT, pager.step(2000, items, true, false, false, "one", false));
        assertEquals(PROCESS, pager.step(3000, items, true, false, false, "one", false));
        pager.processed(items, "one", 3000);
        assertEquals(FAILED, pager.step(3001, items, true, false, false, "one", false));
    }

    @Test public void networkFailureCannotBecomeSuccessEvenWithTerminationFlag() {
        ProfileBulkPager pager = new ProfileBulkPager(0);
        assertEquals(FAILED, pager.step(0, new Object(), false, true, false, "end", true));
    }

    @Test public void loadingAndMissingTimelineNeverCountAsAnEmptyCompletedProfile() {
        ProfileBulkPager pager = new ProfileBulkPager(0);
        assertEquals(WAIT, pager.step(0, null, false, true, false, "end", false));
        assertEquals(WAIT, pager.step(1000, new Object(), false, true, true, "end", false));
        assertEquals(FAILED, pager.step(45000, null, false, true, true, "end", false));
    }

    @Test public void lateTimelineEmissionResetsSettlingBeforeTheFinalDrain() {
        ProfileBulkPager pager = new ProfileBulkPager(0);
        Object old = new Object(), last = new Object();
        pager.processed(old, "one", 0);
        pager.requested("one", 10);
        assertEquals(WAIT, pager.step(100, old, false, true, false, "end", false));
        assertEquals(WAIT, pager.step(800, last, false, true, false, "end", false));
        assertEquals(WAIT, pager.step(1100, last, false, true, false, "end", false));
        assertEquals(PROCESS, pager.step(1800, last, false, true, false, "end", false));
    }
}
