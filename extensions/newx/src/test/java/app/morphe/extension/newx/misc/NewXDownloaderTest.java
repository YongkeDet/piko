package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class NewXDownloaderTest {
    @Test
    public void nativeBannerProgressReplacesInPlaceAndYieldsToErrorsResultsAndNativeMessages() {
        DownloadBannerPolicy p = new DownloadBannerPolicy();
        Object first = new Object(), next = new Object(), error = new Object(), saved = new Object();
        p.beginProgress();
        p.register(first, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(first, null, 0));
        p.register(next, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.REPLACE, p.decide(next, first, 1000));
        p.register(error, DownloadBannerPolicy.ERROR);
        assertEquals(DownloadBannerPolicy.REPLACE, p.decide(error, next, 1100));
        p.register(saved, DownloadBannerPolicy.SUCCESS);
        assertEquals(DownloadBannerPolicy.DROP, p.decide(saved, error, 1200));
        p.register(next, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.DROP, p.decide(next, error, 2000));
        p.register(next, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.REPLACE, p.decide(next, error, 6100));
        Object nativeNotice = new Object();
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(nativeNotice, next, 6200));
        p.register(first, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.DROP, p.decide(first, nativeNotice, 6300));
        p.register(saved, DownloadBannerPolicy.SUCCESS);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(saved, null, 6400));
        p.register(first, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.DROP, p.decide(first, saved, 7000));
    }

    @Test
    public void nativeBannerRejectsStaleProgressAndClearCannotEraseResultsOrNewTasks() {
        DownloadBannerPolicy p = new DownloadBannerPolicy();
        Object first = new Object(), late = new Object(), clear = new Object(), result = new Object();
        p.beginProgress(); p.register(first, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(first, null, 0));
        p.register(late, DownloadBannerPolicy.PROGRESS);
        assertTrue(p.endProgress()); assertFalse(p.endProgress());
        p.register(clear, DownloadBannerPolicy.CLEAR);
        assertEquals(DownloadBannerPolicy.DROP, p.decide(late, first, 100));
        assertEquals(DownloadBannerPolicy.DISMISS, p.decide(clear, first, 100));
        p.beginProgress(); p.register(first, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(first, null, 200));
        p.endProgress(); p.register(clear, DownloadBannerPolicy.CLEAR);
        p.beginProgress();
        assertEquals(DownloadBannerPolicy.DROP, p.decide(clear, first, 250));
        p.register(late, DownloadBannerPolicy.PROGRESS);
        assertEquals(DownloadBannerPolicy.REPLACE, p.decide(late, first, 300));
        p.endProgress(); p.register(clear, DownloadBannerPolicy.CLEAR);
        p.register(result, DownloadBannerPolicy.SUCCESS);
        assertEquals(DownloadBannerPolicy.REPLACE, p.decide(result, late, 400));
        assertEquals(DownloadBannerPolicy.DROP, p.decide(clear, result, 450));
        // Identity matters even if a native model's equals() considers messages identical.
        Object a = new String("same"), b = new String("same");
        p.register(a, DownloadBannerPolicy.ERROR);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(b, result, 500));
        assertEquals(DownloadBannerPolicy.REPLACE, p.decide(a, result, 500));
    }

    @Test
    public void concurrentProgressSeparatesBatchAndUnknownTotalsWithoutStaleCompletion() {
        DownloadProgressState state = new DownloadProgressState();
        state.queued(1, "video.mp4", false, 0);
        state.progress(1, 500, 1000, 1000);
        assertEquals(50, state.snapshot(false, 1000).percent);
        assertEquals(500, state.snapshot(false, 1000).speed);
        state.queued(2, "photo.jpg", false, 1000);
        state.progress(2, 50, -1, 1500);
        assertEquals(-1, state.snapshot(false, 1500).percent);
        assertEquals(550, state.snapshot(false, 1500).done);
        state.queued(3, "batch.mp4", true, 0);
        state.progress(3, 100, 400, 1000);
        assertEquals(25, state.snapshot(true, 1500).percent);
        assertEquals(2, state.snapshot(false, 1500).count);
        state.finished(2);
        state.progress(2, 999, 1000, 2000);
        assertEquals(1, state.snapshot(false, 2000).count);
        assertEquals(500, state.snapshot(false, 2000).done);
        state.progress(1, 1000, 1000, 2000);
        assertEquals(99, state.snapshot(false, 2000).percent); // Saving has not finished yet.
        state.finished(1);
        assertEquals(0, state.snapshot(false, 2000).count);
        assertEquals(1, state.snapshot(true, 2000).count);
        state.queued(-1, "merge 1/2", false, 2000);
        state.progress(-1, 50, 100, 2500);
        assertEquals(50, state.snapshot(false, 2500).percent);
        state.queued(-1, "merging", false, 2600);
        assertEquals(-1, state.snapshot(false, 2600).percent);
        state.finished(-1);
        assertEquals(0, state.snapshot(false, 2600).count);
        assertEquals(1, state.snapshot(true, 2600).count);
    }

    @Test
    public void progressRetryResetsSpeedAndWaitingDoesNotClaimActiveTransfer() {
        DownloadProgressState state = new DownloadProgressState();
        state.queued(1, "retry.mp4", false, 0);
        state.progress(1, 900, 1000, 1000);
        state.waiting(1, true);
        assertEquals(1, state.snapshot(false, 1100).waiting);
        assertEquals(0, state.snapshot(false, 1100).speed);
        state.progress(1, 100, 1000, 1500);
        assertEquals(10, state.snapshot(false, 1500).percent);
        assertEquals(0, state.snapshot(false, 1500).speed);
        assertEquals(0, state.snapshot(false, 1500).waiting);
        state.progress(1, 200, 1000, 2000);
        assertEquals(200, state.snapshot(false, 2000).speed);
        assertEquals(0, state.snapshot(false, 6000).speed);
        state.queued(1, "retry.mp4", false, 6000);
        assertEquals(0, state.snapshot(false, 6000).done);
        assertEquals(-1, state.snapshot(false, 6000).percent);
        state.finished(1);
        assertEquals(0, state.snapshot(false, 6000).count);
    }

    @Test
    public void largerVariantUrlReplacesOrigWith4096() {
        assertEquals(
                "https://pbs.twimg.com/media/test.jpg?format=jpg&name=4096x4096",
                NewXDownloader.largerVariantUrl("https://pbs.twimg.com/media/test.jpg?format=jpg&name=orig")
        );
    }

    @Test
    public void largerVariantUrlReturnsNullWhenNotOrig() {
        assertNull(NewXDownloader.largerVariantUrl("https://pbs.twimg.com/media/test.jpg?format=jpg&name=small"));
        assertNull(NewXDownloader.largerVariantUrl("https://pbs.twimg.com/media/test.jpg?format=jpg"));
    }

    @Test
    public void largerVariantUrlReturnsNullForNull() {
        assertNull(NewXDownloader.largerVariantUrl(null));
    }
}
