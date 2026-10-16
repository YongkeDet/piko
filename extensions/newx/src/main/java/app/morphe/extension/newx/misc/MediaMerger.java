package app.morphe.extension.newx.misc;

import app.morphe.extension.shared.StringRef;

import android.content.Context;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.crimera.downloader.engine.DestinationLoss;
import app.morphe.extension.crimera.downloader.engine.Reservation;
import app.morphe.extension.crimera.downloader.engine.DestinationWriter;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.NewXUtils;

/**
 * Downloads multiple image slices in the background, stitches them horizontally
 * in 1-2-3-4 order, and saves the final merged image through the shared SAF destination so a
 * merge lands in the same folder, under the same filename template, as a plain download.
 * Intermediate splits are stored in cache and deleted immediately after merging.
 */
public final class MediaMerger {
    private static final String LOG_PREFIX = "[PikoNewX][MediaMerger] ";
    private static final ExecutorService MERGE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_DIMENSION = 16384;
    private static final long MAX_PIXELS = 40_000_000L; // ~160MB in ARGB_8888

    private MediaMerger() {
    }

    public static void downloadAndMerge(
            Context context,
            List<InlineDownloadButton.DownloadItem> items,
            String username,
            DownloadFileName.PostContext postContext
    ) {
        if (context == null || items == null || items.size() < 2) {
            NewXInAppNotification.showErrorForUser(StringRef.str("piko_newx_merge_too_few"), username);
            return;
        }

        Context applicationContext = context.getApplicationContext();
        Context safeContext = applicationContext != null ? applicationContext : context;

        if (!NewXInAppNotification.hasUnifiedHost()) {
            NewXInAppNotification.showForUser(
                    StringRef.str("piko_newx_merge_started", items.size()), username);
        }
        MERGE_EXECUTOR.execute(() -> performMerge(safeContext, items, username, postContext));
    }

    private static void performMerge(
            Context context,
            List<InlineDownloadButton.DownloadItem> items,
            String username,
            DownloadFileName.PostContext postContext
    ) {
        List<File> tempFiles = new ArrayList<>();
        int progressId = 0;
        try {
            progressId = DownloadProgressDisplay.beginLocal(StringRef.str("piko_newx_download_progress_merging"));
            File cacheDir = context.getCacheDir();
            for (int i = 0; i < items.size(); i++) {
                InlineDownloadButton.DownloadItem item = items.get(i);
                File tempFile = File.createTempFile("piko_merge_split_" + (i + 1) + "_", ".tmp", cacheDir);
                tempFiles.add(tempFile);

                DownloadProgressDisplay.localStage(progressId,
                        StringRef.str("piko_newx_download_progress_merge_part", i + 1, items.size()));
                boolean downloaded = downloadToFile(item.url, tempFile, progressId);
                final int sliceIndex = i + 1;
                if (!downloaded) {
                    NewXLogger.printInfo(() -> LOG_PREFIX + "Failed to download slice " + sliceIndex + " from " + item.url);
                    NewXInAppNotification.showErrorForUser(StringRef.str("piko_newx_merge_slice_download_failed", sliceIndex), username);
                    return;
                }
            }

            DownloadProgressDisplay.localStage(progressId, StringRef.str("piko_newx_download_progress_merging"));

            int count = tempFiles.size();
            int[] widths = new int[count];
            int[] heights = new int[count];
            int maxHeight = 0;

            for (int i = 0; i < count; i++) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(tempFiles.get(i).getAbsolutePath(), opts);
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    NewXInAppNotification.showErrorForUser(StringRef.str("piko_newx_merge_slice_size_failed", i + 1), username);
                    return;
                }
                widths[i] = opts.outWidth;
                heights[i] = opts.outHeight;
                if (opts.outHeight > maxHeight) {
                    maxHeight = opts.outHeight;
                }
            }

            int[] scaledWidths = new int[count];
            int totalWidth = 0;
            for (int i = 0; i < count; i++) {
                if (heights[i] == maxHeight) {
                    scaledWidths[i] = widths[i];
                } else {
                    scaledWidths[i] = Math.round((float) widths[i] * maxHeight / heights[i]);
                }
                totalWidth += scaledWidths[i];
            }

            // Step 4: Safety bounds check (avoid exceeding Android canvas limit or OOM)
            float downscale = 1.0f;
            if (totalWidth > MAX_DIMENSION) {
                downscale = Math.min(downscale, (float) MAX_DIMENSION / totalWidth);
            }
            if ((long) totalWidth * maxHeight > MAX_PIXELS) {
                downscale = Math.min(downscale, (float) Math.sqrt((double) MAX_PIXELS / ((long) totalWidth * maxHeight)));
            }

            if (downscale < 1.0f) {
                maxHeight = Math.max(1, Math.round(maxHeight * downscale));
                totalWidth = 0;
                for (int i = 0; i < count; i++) {
                    scaledWidths[i] = Math.max(1, Math.round(scaledWidths[i] * downscale));
                    totalWidth += scaledWidths[i];
                }
            }

            Bitmap mergedBitmap;
            try {
                mergedBitmap = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError oom) {
                NewXLogger.printException(() -> LOG_PREFIX + "OOM creating ARGB_8888 merged bitmap, trying RGB_565", oom);
                try {
                    mergedBitmap = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.RGB_565);
                } catch (OutOfMemoryError oom2) {
                    NewXInAppNotification.showErrorForUser(StringRef.str("piko_newx_merge_memory_failed"), username);
                    return;
                }
            }

            Canvas canvas = new Canvas(mergedBitmap);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

            int currentX = 0;
            for (int i = 0; i < count; i++) {
                Bitmap piece = BitmapFactory.decodeFile(tempFiles.get(i).getAbsolutePath());
                if (piece == null) {
                    mergedBitmap.recycle();
                    NewXInAppNotification.showErrorForUser(StringRef.str("piko_newx_merge_slice_decode_failed", i + 1), username);
                    return;
                }

                Rect srcRect = new Rect(0, 0, piece.getWidth(), piece.getHeight());
                Rect dstRect = new Rect(currentX, 0, currentX + scaledWidths[i], maxHeight);
                canvas.drawBitmap(piece, srcRect, dstRect, paint);
                piece.recycle(); // Immediately free piece memory
                currentX += scaledWidths[i];
            }

            boolean isAllPng = true;
            for (InlineDownloadButton.DownloadItem item : items) {
                if (item.extension == null || !item.extension.equalsIgnoreCase("png")) {
                    isAllPng = false;
                    break;
                }
            }

            String extension = isAllPng ? "png" : "jpg";
            String mimeType = isAllPng ? "image/png" : "image/jpeg";
            Bitmap.CompressFormat format = isAllPng ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;

            // One output for the whole action, so render without a media index.
            String fileName = DownloadFileName.render(
                    DownloadSettings.filenameTemplate(),
                    postContext,
                    0,
                    1,
                    extension
            );

            // Reserve before encoding so a skipped merge costs no work.
            final Uri destinationTree = NewXDownloadFolders.treeUri(NewXDownloadFolders.MediaKind.IMAGES);
            final DownloadRequest request;
            try {
                request = NewXDownloadFolders.createRequest(
                        items.get(0).url,
                        Collections.emptyList(),
                        destinationTree,
                        postContext,
                        DownloadSettings.authorFolders(),
                        fileName,
                        mimeType,
                        NewXDownloadFolders.conflictPolicy(),
                        username
                );
            } catch (IllegalArgumentException exception) {
                mergedBitmap.recycle();
                NewXLogger.printException(() -> LOG_PREFIX + "Invalid NewX download request: " + fileName, exception);
                InlineDownloadButton.reportDownloadStatus(StringRef.str("piko_newx_merge_save_failed", fileName), username);
                return;
            }

            DestinationWriter destination = NewXDownloader.destinationWriter();
            final Reservation reservation;
            try {
                reservation = destination.reserve(context, request);
            } catch (Exception exception) {
                mergedBitmap.recycle();
                NewXLogger.printException(() -> LOG_PREFIX + "Failed to create merged image document", exception);
                if (DestinationLoss.matches(exception)) {
                    NewXDownloadFolders.invalidate(NewXDownloadFolders.MediaKind.IMAGES);
                    InlineDownloadButton.reportDownloadStatus(
                            InlineDownloadButton.folderLostMessage(),
                            username
                    );
                } else {
                    InlineDownloadButton.reportDownloadStatus(
                            StringRef.str("piko_newx_merge_save_failed", fileName),
                            username
                    );
                }
                return;
            }
            if (reservation == null) {
                mergedBitmap.recycle();
                NewXInAppNotification.showForUser(StringRef.str("piko_newx_merge_exists", fileName), username);
                return;
            }

            try {
                writeMergedBitmap(context, mergedBitmap, reservation.documentUri(), format);
                NewXLogger.printInfo(() -> LOG_PREFIX + "Successfully merged and saved " + reservation.fileName());
                NewXInAppNotification.tryShowSuccessForUser(
                        StringRef.str("piko_newx_merge_saved", reservation.fileName()), username);
            } catch (Exception exception) {
                destination.discard(context, reservation);
                NewXLogger.printException(() -> LOG_PREFIX + "Failed to write merged image", exception);
                if (DestinationLoss.matches(exception)) {
                    NewXDownloadFolders.invalidate(NewXDownloadFolders.MediaKind.IMAGES);
                    InlineDownloadButton.reportDownloadStatus(
                            InlineDownloadButton.folderLostMessage(),
                            username
                    );
                } else {
                    InlineDownloadButton.reportDownloadStatus(
                            StringRef.str("piko_newx_merge_save_failed", fileName),
                            username
                    );
                }
            } finally {
                mergedBitmap.recycle();
            }

        } catch (Throwable t) {
            NewXLogger.printException(() -> LOG_PREFIX + "Failed to merge images", t);
            NewXInAppNotification.showErrorForUser(StringRef.str("piko_newx_merge_failed"), username);
        } finally {
            if (progressId != 0) DownloadProgressDisplay.endLocal(progressId);
            for (File tempFile : tempFiles) {
                try {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean downloadToFile(String url, File targetFile, int progressId) {
        if (!NewXUtils.isHttpUrl(url)) return false;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "image/*");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                if (url.contains("name=orig")) {
                    String fallback = url.replace("name=orig", "name=4096x4096");
                    return downloadToFile(fallback, targetFile, progressId);
                }
                return false;
            }

            long total = connection.getContentLengthLong();
            long done = 0, lastUpdate = SystemClock.elapsedRealtime();
            DownloadProgressDisplay.localProgress(progressId, 0, total);
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[64 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    done += read;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastUpdate >= 500) {
                        DownloadProgressDisplay.localProgress(progressId, done, total);
                        lastUpdate = now;
                    }
                }
            }
            DownloadProgressDisplay.localProgress(progressId, done, total);
            return true;
        } catch (Exception e) {
            NewXLogger.printException(() -> LOG_PREFIX + "Download error for " + url, e);
            if (url.contains("name=orig")) {
                String fallback = url.replace("name=orig", "name=4096x4096");
                return downloadToFile(fallback, targetFile, progressId);
            }
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void writeMergedBitmap(
            Context context,
            Bitmap bitmap,
            Uri uri,
            Bitmap.CompressFormat format
    ) throws IOException {
        int quality = format == Bitmap.CompressFormat.PNG ? 100 : 95;
        OutputStream stream = context.getContentResolver().openOutputStream(uri, "wt");
        if (stream == null) {
            throw new IOException("Could not open output stream for " + uri);
        }
        try (OutputStream out = stream) {
            if (!bitmap.compress(format, quality, out)) {
                throw new IOException("Failed to compress merged image");
            }
            out.flush();
        }
    }
}
