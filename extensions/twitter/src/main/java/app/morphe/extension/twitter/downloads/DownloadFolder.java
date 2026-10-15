/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.downloads;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import app.morphe.extension.twitter.settings.Settings;

/** DownloadManager fetches into app storage; a job copies into the granted document tree. */
public final class DownloadFolder {
    private static final String TAG = "PikoDownloadFolder";
    private static final String PENDING = "piko_pending_downloads";
    private static final String CHANNEL = "piko_custom_downloads";
    private static final Map<DownloadManager.Request, JSONObject> REQUESTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private DownloadFolder() {
    }

    private static SharedPreferences settings(Context context) {
        return context.getSharedPreferences(Settings.SHARED_PREF_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences pending(Context context) {
        return context.getSharedPreferences(PENDING, Context.MODE_PRIVATE);
    }

    public static Uri getTreeUri(Context context) {
        String value = settings(context).getString(Settings.DOWNLOAD_TREE_URI.key, "");
        return value.isEmpty() ? null : Uri.parse(value);
    }

    public static boolean isCustomFolderEnabled(Context context) {
        return getTreeUri(context) != null;
    }

    public static String getFolderSummary(Context context) {
        Uri tree = getTreeUri(context);
        if (tree == null) return text(context, "piko_pref_download_custom_folder_desc");
        try {
            return DocumentsContract.getTreeDocumentId(tree);
        } catch (IllegalArgumentException exception) {
            return tree.toString();
        }
    }

    public static void saveFolder(Context context, Uri tree, int resultFlags) {
        int permissions = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        try {
            if (!DocumentsContract.isTreeUri(tree) || (resultFlags & permissions) != permissions) {
                throw new SecurityException("Read/write directory permission is required");
            }
            context.getContentResolver().takePersistableUriPermission(tree, resultFlags & permissions);
            Uri document = DocumentsContract.buildDocumentUriUsingTree(tree,
                    DocumentsContract.getTreeDocumentId(tree));
            try (Cursor cursor = context.getContentResolver().query(document,
                    new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null)) {
                if (cursor == null || !cursor.moveToFirst()
                        || (cursor.getInt(0) & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0) {
                    throw new SecurityException("The selected directory is not writable");
                }
            }
            if (!settings(context).edit().putString(Settings.DOWNLOAD_TREE_URI.key, tree.toString()).commit()) {
                throw new IOException("Could not persist the download directory");
            }
            toast(context, text(context, "piko_pref_download_folder_saved"));
            resumePendingDownloads(context);
        } catch (IOException | RuntimeException exception) {
            Log.w(TAG, "Could not select download directory", exception);
            showFolderError(context);
        }
    }

    public static void resetFolder(Context context) {
        // Keep grants used by in-flight downloads; those retain their original destination.
        settings(context).edit().remove(Settings.DOWNLOAD_TREE_URI.key).apply();
    }

    private static boolean hasPermission(Context context, Uri tree) {
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (permission.getUri().equals(tree) && permission.isReadPermission()
                    && permission.isWritePermission()) return true;
        }
        return false;
    }

    /** This signature also replaces X's original public-directory call in the bytecode patch. */
    public static DownloadManager.Request configureDestination(DownloadManager.Request request,
            String publicFolder, String subpath) {
        Context context = app.morphe.extension.shared.Utils.getContext();
        Uri tree = getTreeUri(context);
        if (tree == null) return request.setDestinationInExternalPublicDir(publicFolder, subpath);
        return configureDestination(context, request, tree, new File(subpath).getName(), "");
    }

    private static DownloadManager.Request configureDestination(Context context,
            DownloadManager.Request request, Uri tree, String filename, String fallback) {
        try {
            JSONObject record = new JSONObject().put("tree", tree.toString())
                    .put("filename", filename).put("fallback", fallback);
            // DownloadManager cannot write to a content:// tree directly.
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                    "piko-" + UUID.randomUUID() + ".part");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            REQUESTS.put(request, record);
            return request;
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Returns the real DownloadManager ID so X's existing request handling still works. */
    public static long enqueue(DownloadManager manager, DownloadManager.Request request) {
        JSONObject record = REQUESTS.remove(request);
        if (record == null) return manager.enqueue(request);
        Context context = app.morphe.extension.shared.Utils.getContext();
        return enqueue(context, manager, request, record);
    }

    private static long enqueue(Context context, DownloadManager manager,
            DownloadManager.Request request, JSONObject record) {
        Uri tree = Uri.parse(record.optString("tree"));
        if (!hasPermission(context, tree)) {
            showFolderError(context);
            return -1;
        }
        long id = manager.enqueue(request);
        if (!pending(context).edit().putString(Long.toString(id), record.toString()).commit()) {
            manager.remove(id);
            showFolderError(context);
            return -1;
        }
        // A cached/small download can finish before its ID was persisted.
        scheduleCopy(context, id);
        return id;
    }

    public static void downloadFile(Context context, String url, String filename) {
        Uri tree = getTreeUri(context);
        if (tree == null) return;
        String fallback = url.contains("name=orig") ? url.replace("name=orig", "name=4096x4096") : "";
        downloadFile(context, url, filename, tree, fallback);
    }

    private static void downloadFile(Context context, String url, String filename, Uri tree, String fallback) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription(text(context, "piko_title_download") + ": " + filename);
            configureDestination(context, request, tree, filename, fallback);
            JSONObject record = REQUESTS.remove(request);
            enqueue(context, context.getSystemService(DownloadManager.class), request, record);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not start download", exception);
            showFolderError(context);
        }
    }

    public static void resumePendingDownloads(Context context) {
        for (String key : pending(context).getAll().keySet()) {
            try {
                scheduleCopy(context, Long.parseLong(key));
            } catch (NumberFormatException ignored) {
                // Ignore an unrelated/malformed preference rather than breaking settings.
            }
        }
    }

    public static void scheduleCopy(Context context, long id) {
        if (id < 0 || !pending(context).contains(Long.toString(id))) return;
        // Only schedule terminal downloads. A job that exits while a download is still running
        // could otherwise swallow its completion broadcast during the JobScheduler handoff.
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor != null && cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status != DownloadManager.STATUS_SUCCESSFUL && status != DownloadManager.STATUS_FAILED) return;
            }
        }
        PersistableBundle extras = new PersistableBundle();
        extras.putLong("download_id", id);
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        JobInfo job = new JobInfo.Builder(jobId(id), new ComponentName(context, DownloadCopyJob.class))
                .setExtras(extras).setOverrideDeadline(0)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();
        if (scheduler.getPendingJob(job.getId()) == null) scheduler.schedule(job);
    }

    private static int jobId(long id) {
        return 0x50490000 ^ (int) (id ^ (id >>> 32));
    }

    /** @return whether a temporary copy failure should be retried by JobScheduler. */
    static boolean finishDownload(Context context, long id) {
        SharedPreferences preferences = pending(context);
        String key = Long.toString(id);
        String saved = preferences.getString(key, "");
        if (saved.isEmpty()) return false;
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        try {
            JSONObject record = new JSONObject(saved);
            try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                if (cursor == null || !cursor.moveToFirst()) {
                    preferences.edit().remove(key).commit();
                    return false;
                }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_FAILED) {
                    String fallback = record.optString("fallback");
                    if (!fallback.isEmpty()) {
                        downloadFile(context, fallback, record.getString("filename"),
                                Uri.parse(record.getString("tree")), "");
                    } else {
                        toast(context, text(context, "piko_pref_download_save_failed"));
                    }
                    preferences.edit().remove(key).commit();
                    manager.remove(id);
                    return false;
                }
                if (status != DownloadManager.STATUS_SUCCESSFUL) return false;
            }

            Uri tree = Uri.parse(record.getString("tree"));
            if (!hasPermission(context, tree)) {
                showFolderError(context);
                return false;
            }
            String filename = record.getString("filename");
            String mime = URLConnection.guessContentTypeFromName(filename);
            if (mime == null) mime = "application/octet-stream";
            Uri document;
            if (record.has("document")) {
                document = Uri.parse(record.getString("document"));
            } else {
                Uri directory = DocumentsContract.buildDocumentUriUsingTree(tree,
                        DocumentsContract.getTreeDocumentId(tree));
                // Providers create a unique name when the requested filename already exists.
                document = DocumentsContract.createDocument(context.getContentResolver(), directory, mime, filename);
                if (document == null) throw new IOException("Could not create output document");
                record.put("document", document.toString());
                if (!preferences.edit().putString(key, record.toString()).commit()) {
                    DocumentsContract.deleteDocument(context.getContentResolver(), document);
                    throw new IOException("Could not persist output document");
                }
            }
            try (ParcelFileDescriptor inputDescriptor = manager.openDownloadedFile(id)) {
                if (inputDescriptor == null) throw new IOException("Could not open downloaded file");
                try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(inputDescriptor);
                     OutputStream output = context.getContentResolver().openOutputStream(document, "wt")) {
                    if (output == null) throw new IOException("Could not open output document");
                    byte[] buffer = new byte[64 * 1024];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                        output.write(buffer, 0, length);
                    }
                }
            }
            if (!preferences.edit().remove(key).commit()) return true;
            manager.remove(id); // Remove only our staging file, after the destination is fully closed.
            notifyCompleted(context, id, filename, document, mime);
            return false;
        } catch (InterruptedIOException exception) {
            Thread.currentThread().interrupt();
            return true;
        } catch (SecurityException exception) {
            Log.w(TAG, "Download directory permission was revoked", exception);
            showFolderError(context);
            return false;
        } catch (IOException | JSONException | RuntimeException exception) {
            Log.w(TAG, "Could not save downloaded file", exception);
            toast(context, text(context, "piko_pref_download_save_failed"));
            return true;
        }
    }

    private static void notifyCompleted(Context context, long id, String filename, Uri document, String mime) {
        String title = text(context, "exo_download_completed") + ": " + filename;
        toast(context, title);
        try {
            NotificationManager notifications = context.getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
                    text(context, "piko_title_download"), NotificationManager.IMPORTANCE_LOW));
            Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(document, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent content = PendingIntent.getActivity(context, jobId(id), open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            notifications.notify(TAG, jobId(id), new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(title)
                    .setContentIntent(content).setAutoCancel(true).build());
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not show download notification", exception);
        }
    }

    public static void showFolderError(Context context) {
        toast(context, text(context, "piko_pref_download_folder_failed"));
    }

    private static String text(Context context, String name) {
        int resource = context.getResources().getIdentifier(name, "string", context.getPackageName());
        return resource == 0 ? name : context.getString(resource);
    }

    private static void toast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }
}
