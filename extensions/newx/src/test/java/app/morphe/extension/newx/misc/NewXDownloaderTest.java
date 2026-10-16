package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class NewXDownloaderTest {
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
