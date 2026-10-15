/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * Copyright (C) 2026 YongkeDet
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.modern;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Redirects public media entries while X retains its original streaming and error handling. */
public final class DownloadFolderHooks {
    private static final String PREFERENCES = "piko_settings";
    private static final String TREE = "download_tree_uri";
    private static final Set<Uri> PENDING_DOCUMENTS =
            Collections.newSetFromMap(new ConcurrentHashMap<Uri, Boolean>());
    private static volatile Context application;

    private DownloadFolderHooks() { }

    public static void initialize(Context context) {
        Context app = context.getApplicationContext();
        application = app == null ? context : app;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public static Uri getFolder(Context context) {
        String value = preferences(context).getString(TREE, "");
        return value == null || value.isEmpty() ? null : Uri.parse(value);
    }

    public static void saveFolder(Context context, Uri tree, int resultFlags) throws IOException {
        int readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        if (tree == null || !DocumentsContract.isTreeUri(tree) || (resultFlags & readWrite) != readWrite) {
            throw new SecurityException("A writable document tree is required");
        }
        ContentResolver resolver = context.getContentResolver();
        Uri document = DocumentsContract.buildDocumentUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));
        try (Cursor cursor = resolver.query(document,
                new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null)) {
            if (cursor == null || !cursor.moveToFirst()
                    || (cursor.getInt(0) & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0) {
                throw new SecurityException("The directory does not support creating files");
            }
        }
        resolver.takePersistableUriPermission(tree, resultFlags & readWrite);
        if (!preferences(context).edit().putString(TREE, tree.toString()).commit()) {
            throw new IOException("Could not save the download directory");
        }
    }

    public static void resetFolder(Context context) {
        // Existing saves retain the Uri returned when the document was created.
        preferences(context).edit().remove(TREE).apply();
    }

    private static boolean isPublicMediaCollection(Uri collection) {
        if (collection == null || !"media".equals(collection.getAuthority())) return false;
        String path = collection.getPath();
        return path != null && (path.startsWith("/external/") || path.startsWith("/external_primary/"))
                && (path.endsWith("/images/media") || path.endsWith("/video/media")
                || path.endsWith("/downloads"));
    }

    public static Uri insert(ContentResolver resolver, Uri collection, ContentValues values)
            throws IOException {
        Context context = application;
        Uri tree = context == null ? null : getFolder(context);
        if (tree == null || !isPublicMediaCollection(collection)) {
            return resolver.insert(collection, values);
        }
        boolean permitted = false;
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (tree.equals(permission.getUri()) && permission.isReadPermission()
                    && permission.isWritePermission()) {
                permitted = true;
                break;
            }
        }
        if (!permitted) throw new SecurityException("Download folder permission has been revoked");

        String name = values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME);
        String mime = values.getAsString(MediaStore.MediaColumns.MIME_TYPE);
        if (name == null || name.isEmpty()) name = "download";
        name = name.replace('/', '_').replace('\\', '_').replace('\0', '_');
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree,
                DocumentsContract.getTreeDocumentId(tree));
        Uri document = DocumentsContract.createDocument(resolver, parent, mime, name);
        if (document == null) throw new IOException("The provider did not create a document");
        PENDING_DOCUMENTS.add(document);
        return document;
    }

    public static int update(ContentResolver resolver, Uri document, ContentValues values,
            String selection, String[] selectionArgs) {
        // DocumentsProvider has no MediaStore IS_PENDING column. X calls this after closing output.
        if (PENDING_DOCUMENTS.contains(document) && values != null
                && Integer.valueOf(0).equals(values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))) {
            PENDING_DOCUMENTS.remove(document);
            return 1;
        }
        return resolver.update(document, values, selection, selectionArgs);
    }

    public static int delete(ContentResolver resolver, Uri document, String selection,
            String[] selectionArgs) throws IOException {
        if (PENDING_DOCUMENTS.contains(document)) {
            boolean deleted = DocumentsContract.deleteDocument(resolver, document);
            if (deleted) PENDING_DOCUMENTS.remove(document);
            return deleted ? 1 : 0;
        }
        return resolver.delete(document, selection, selectionArgs);
    }
}
