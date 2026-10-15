/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.downloads;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Copies run as jobs so a large video is not limited by a broadcast timeout. */
public class DownloadCopyJob extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, Future<?>> tasks = new ConcurrentHashMap<>();

    @Override
    public boolean onStartJob(JobParameters parameters) {
        Future<?> task = executor.submit(() -> {
            boolean retry = DownloadFolder.finishDownload(this,
                    parameters.getExtras().getLong("download_id", -1));
            if (!Thread.currentThread().isInterrupted()) {
                jobFinished(parameters, retry);
            }
        });
        tasks.put(parameters.getJobId(), task);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters parameters) {
        Future<?> task = tasks.remove(parameters.getJobId());
        if (task != null) task.cancel(true);
        return true;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
