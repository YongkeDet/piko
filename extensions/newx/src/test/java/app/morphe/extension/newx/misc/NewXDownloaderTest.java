package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class NewXDownloaderTest {
    @Test
    public void pinnedProgressDispatchesOnceAndResultsAreAcknowledgedInOrder() {
        DownloadBannerPolicy p = new DownloadBannerPolicy();
        p.start(1, "download", "a.mp4", null);
        DownloadBannerPolicy.Notice first = p.next().notice;
        Object model = new Object(); p.bind(first, model);
        for (int i = 0; i < 100; i++) { p.update(1, "download " + i, "a.mp4", i); assertNull(p.next()); }
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(model, null));
        p.visible(model);
        assertTrue(p.fullContent(model));
        assertFalse(p.fullContent(new Object()));
        assertEquals(first, p.progressNotice(model));
        for (int i = 0; i < 100; i++) { p.update(1, "download " + i, "a.mp4", i); assertNull(p.next()); }
        p.start(2, "download", "b.jpg", null);
        assertTrue(p.result(1, DownloadBannerPolicy.SUCCESS, "saved", "a.mp4"));
        assertFalse(p.result(1, DownloadBannerPolicy.SUCCESS, "saved", "a.mp4"));
        DownloadBannerPolicy.Action clear = p.next();
        assertEquals(model, clear.clear);
        Object control = new Object(); p.bindClear(control, clear.clear);
        assertEquals(DownloadBannerPolicy.DISMISS, p.decide(control, model));
        p.disposed(model, "Replaced", true); p.visible(null);
        DownloadBannerPolicy.Notice saved = p.next().notice;
        Object savedModel = new Object(); p.bind(saved, savedModel);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(savedModel, null)); p.visible(savedModel);
        assertTrue(p.fullContent(savedModel));
        assertNull(p.progressNotice(savedModel));
        assertTrue(p.result(2, DownloadBannerPolicy.ERROR, "failed", "b.jpg"));
        assertNull(p.next()); // Even an error cannot overwrite an unacknowledged earlier result.
        p.disposed(savedModel, "Replaced", false); // A stale duplicate-entry cleanup is not an ACK.
        assertNull(p.next());
        p.disposed(savedModel, "AutoDismiss", true); p.visible(null);
        DownloadBannerPolicy.Notice failed = p.next().notice;
        assertEquals(2, failed.id); assertEquals(DownloadBannerPolicy.ERROR, failed.kind);
        Object failedModel = new Object(); p.bind(failed, failedModel);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(failedModel, null)); p.visible(failedModel);
        assertEquals(DownloadBannerPolicy.DROP, p.decide(control, failedModel));
        assertEquals(DownloadBannerPolicy.DROP, p.decide(savedModel, failedModel));
        p.disposed(failedModel, "DismissedByUser", true); p.visible(null);
        assertNull(p.next()); assertFalse(p.hasWork());
    }

    @Test
    public void profileCancellationAndForeignMessagesCannotConsumeAnotherTasksResult() {
        DownloadBannerPolicy p = new DownloadBannerPolicy();
        long bulk = 1L << 32;
        int[] cancelled = {0};
        p.start(bulk, "profile", "pages", () -> cancelled[0]++);
        p.start(7, "single", "video", () -> cancelled[0] += 100);
        DownloadBannerPolicy.Notice batch = p.next().notice;
        Object model = new String("same"); p.bind(batch, model);
        // Native equality may consider two models equal; only identity denotes ownership.
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(new String("same"), null));
        Object foreign = new Object(); p.visible(foreign);
        assertEquals(DownloadBannerPolicy.DROP, p.decide(model, foreign));
        p.disposed(model, "Replaced", false);
        assertNull(p.next());
        p.visible(null);
        assertEquals(batch, p.next().notice);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(model, null)); p.visible(model);
        Runnable cancel = p.disposed(model, "PrimaryButtonClick", true); p.visible(null);
        assertTrue(cancel != null); cancel.run(); assertEquals(1, cancelled[0]);
        assertNull(p.disposed(model, "PrimaryButtonClick", false));
        p.update(bulk, "late tick", "must stay hidden", 50);
        DownloadBannerPolicy.Notice other = p.next().notice;
        assertEquals(7, other.id); p.rejected(other);
        p.result(bulk, DownloadBannerPolicy.INFO, "cancelled", "summary");
        assertFalse(p.result(bulk, DownloadBannerPolicy.INFO, "cancelled", "duplicate"));
        DownloadBannerPolicy.Notice summary = p.next().notice;
        Object summaryModel = new Object(); p.bind(summary, summaryModel);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(summaryModel, null)); p.visible(summaryModel);
        p.disposed(summaryModel, "Replaced", true); p.visible(foreign);
        assertNull(p.next()); // Retain the interrupted result until the native message exits.
        p.visible(null); assertEquals(summary, p.next().notice);
        assertEquals(DownloadBannerPolicy.NATIVE, p.decide(summaryModel, null)); p.visible(summaryModel);
        p.disposed(summaryModel, "AutoDismiss", true); p.visible(null);
        assertEquals(other, p.next().notice); // Cancelling the bulk session did not cancel task 7.
    }

    @Test
    public void completionBeforeAdmissionRejectsLateProgressAndNeverClearsUnrelatedMessages() {
        DownloadBannerPolicy p = new DownloadBannerPolicy();
        p.start(-1, "merge", "part", null);
        DownloadBannerPolicy.Notice merge = p.next().notice;
        Object late = new Object(); p.bind(merge, late);
        p.finish(-1); p.result(-1, DownloadBannerPolicy.SUCCESS, "merged", "file");
        assertNull(p.update(-1, "late", "bytes", 99));
        assertEquals(DownloadBannerPolicy.DROP, p.decide(late, null));
        p.disposed(late, "Replaced", false);
        DownloadBannerPolicy.Notice result = p.next().notice;
        Object model = new Object(); p.bind(result, model);
        Object foreign = new Object(); p.visible(foreign);
        assertEquals(DownloadBannerPolicy.DROP, p.decide(model, foreign));
        p.disposed(model, "Replaced", false);
        assertNull(p.next()); p.visible(null);
        assertEquals(result, p.next().notice);
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
