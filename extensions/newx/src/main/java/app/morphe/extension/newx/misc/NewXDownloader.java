/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.newx.misc;

import app.morphe.extension.shared.StringRef;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import app.morphe.extension.crimera.downloader.DownloadEngine;
import app.morphe.extension.crimera.downloader.engine.DownloadControllers;
import app.morphe.extension.crimera.downloader.engine.DownloadLog;
import app.morphe.extension.crimera.downloader.engine.DownloadNotifications;
import app.morphe.extension.crimera.downloader.engine.HttpTransfer;
import app.morphe.extension.crimera.downloader.engine.NotificationListener;
import app.morphe.extension.crimera.downloader.engine.SafDestination;
import app.morphe.extension.crimera.downloader.engine.DestinationWriter;
import app.morphe.extension.crimera.downloader.engine.Reservation;
import app.morphe.extension.crimera.downloader.events.DownloadEvent;
import app.morphe.extension.crimera.downloader.events.DownloadEvents;
import app.morphe.extension.crimera.downloader.events.DownloadListener;
import app.morphe.extension.crimera.downloader.events.FailureReason;
import app.morphe.extension.crimera.downloader.events.MainThreadExecutor;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.Utils;

/**
 * Wires NewX onto the shared download engine.
 */
public final class NewXDownloader {
    public static final String CHANNEL_ID = "piko_newx_downloads";
    private static final int TRANSFER_THREADS = 4;

    private static volatile DownloadEngine engine;

    private static final DownloadLog LOG = new DownloadLog() {
        @Override
        public void info(Supplier<String> message) {
            NewXLogger.printInfo(message::get);
        }

        @Override
        public void error(Supplier<String> message, Throwable cause) {
            NewXLogger.printException(message::get, cause);
        }
    };

    private static final DestinationWriter DESTINATION = new DestinationWriter() {
        private final SafDestination delegate = new SafDestination(LOG);

        @Override public synchronized Reservation reserve(Context context, DownloadRequest request)
                throws IOException {
            return delegate.reserve(context, request);
        }

        @Override public synchronized void discard(Context context, Reservation reservation) {
            delegate.discard(context, reservation);
        }
    };

    static DestinationWriter destinationWriter() {
        return DESTINATION;
    }

    private static final ExecutorService TRANSFER_EXECUTOR = Executors.newFixedThreadPool(
            TRANSFER_THREADS,
            runnable -> {
                Thread thread = new Thread(runnable, "download-transfer");
                thread.setDaemon(true);
                return thread;
            }
    );

    private static final Executor NOTIFICATION_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "download-notifications");
        thread.setDaemon(true);
        return thread;
    });

    private NewXDownloader() {
    }

    /**
     * Installs the download engine and registers the notification and status listeners.
     */
    public static synchronized void install(Context context) {
        if (engine != null) {
            return;
        }
        if (context == null) {
            context = Utils.getContext();
        }
        if (context == null) {
            return;
        }

        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }

        DownloadEvents events = new DownloadEvents(
                error -> NewXLogger.printException(() -> "Download event listener failed", error)
        );
        DownloadEngine created = new DownloadEngine(
                applicationContext,
                destinationWriter(),
                new HttpTransfer(LOG),
                events,
                TRANSFER_EXECUTOR,
                System::currentTimeMillis
        );
        events.register(
                new NotificationListener(
                        applicationContext,
                        CHANNEL_ID,
                        StringRef.str("piko_newx_download_channel"),
                        new NewXNotificationTexts(),
                        created::requestFor
                ),
                NOTIFICATION_EXECUTOR
        );
        events.register(
                new DownloadProgressDisplay(created),
                new MainThreadExecutor()
        );
        events.register(
                new StatusListener(applicationContext, created),
                new MainThreadExecutor()
        );
        DownloadControllers.install(created);
        engine = created;
    }

    /** The installed engine, installing lazily if needed. */
    @Nullable
    public static synchronized DownloadEngine get() {
        if (engine == null) {
            Context context = Utils.getContext();
            if (context != null) {
                install(context);
            }
        }
        return engine;
    }

    /** Image retry for unavailable {@code name=orig} variants. */
    @Nullable
    static String largerVariantUrl(String url) {
        if (url == null || !url.contains("name=orig")) return null;
        return url.replace("name=orig", "name=4096x4096");
    }

    static final class StatusListener implements DownloadListener {
        private final Context context;
        private final DownloadEngine engine;

        StatusListener(Context context, DownloadEngine engine) {
            this.context = context;
            this.engine = engine;
        }

        @Override
        public void onCompleted(DownloadEvent.Completed event) {
            if (ProfileBulkDownload.isBatchRequest(engine.requestFor(event.id()))) return;
            if (NewXInAppNotification.hasUnifiedHost()
                    || !DownloadNotifications.notificationsEnabled(context, CHANNEL_ID)) {
                // Completion feedback belongs to the top in-app banner only.
                NewXInAppNotification.tryShowSuccessForUser(StringRef.str("piko_newx_download_saved", event.fileName()), event.label());
            }
        }

        @Override
        public void onFailed(DownloadEvent.Failed event) {
            if (ProfileBulkDownload.isBatchRequest(engine.requestFor(event.id()))) return;
            String label = event.label();
            if (event.reason() == FailureReason.DESTINATION_LOST) {
                DownloadRequest request = engine.requestFor(event.id());
                if (request != null) {
                    try {
                        NewXDownloadFolders.MediaKind kind = NewXDownloadFolders.mediaKindFor(request.mimeType());
                        NewXDownloadFolders.captureDestination(context, kind, "transfer/folder-lost");
                        NewXDownloadFolders.invalidate(kind);
                    } catch (RuntimeException e) {
                        NewXLogger.printException(() -> "Could not invalidate lost destination", e);
                    }
                }
                InlineDownloadButton.reportDownloadStatus(InlineDownloadButton.folderLostMessage(), label);
            } else if (event.reason() == FailureReason.NO_CONNECTION) {
                InlineDownloadButton.reportDownloadStatus(StringRef.str("piko_newx_download_no_connection"), label);
            } else {
                DownloadRequest request = engine.requestFor(event.id());
                String fileName = request != null ? request.fileName() : "";
                InlineDownloadButton.reportDownloadStatus(StringRef.str("piko_newx_download_save_failed", fileName), label);
            }
        }

        @Override
        public void onCancelled(DownloadEvent.Cancelled event) {
            if (ProfileBulkDownload.isBatchRequest(engine.requestFor(event.id()))) return;
            NewXInAppNotification.showForUser(StringRef.str("piko_newx_download_cancelled"), event.label());
        }
    }
}
