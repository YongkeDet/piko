package app.morphe.extension.twitter.downloads;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import app.morphe.extension.modern.DownloadFolderActivity;
import app.morphe.extension.modern.DownloadFolderHooks;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowContentResolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, qualifiers = "en")
public class ModernDownloadFolderTest {
    private static final String AUTHORITY = "piko.test.documents";
    private static final int READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private static final Uri IMAGES = Uri.parse("content://media/external/images/media");
    private static final Uri VIDEOS = Uri.parse("content://media/external/video/media");
    private static final Uri DOWNLOADS = Uri.parse("content://media/external/downloads");
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Context context;
    private ContentResolver resolver;
    private Documents documents;
    private Media media;
    private Uri first;
    private Uri second;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        resolver = context.getContentResolver();
        DownloadFolderHooks.initialize(context);
        documents = new Documents(temporary.newFolder());
        ProviderInfo info = new ProviderInfo();
        info.authority = AUTHORITY;
        info.exported = true;
        info.grantUriPermissions = true;
        info.readPermission = "android.permission.MANAGE_DOCUMENTS";
        info.writePermission = "android.permission.MANAGE_DOCUMENTS";
        documents.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal(AUTHORITY, documents);
        media = new Media();
        ProviderInfo mediaInfo = new ProviderInfo();
        mediaInfo.authority = "media";
        mediaInfo.exported = true;
        media.attachInfo(context, mediaInfo);
        ShadowContentResolver.registerProviderInternal("media", media);
        first = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "first");
        second = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "second");
    }

    private ContentValues values(String name, String mime) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/X");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        return values;
    }

    private void finish(Uri document, byte[] bytes) throws Exception {
        try (OutputStream output = resolver.openOutputStream(document)) {
            assertNotNull(output);
            output.write(bytes);
        }
        ContentValues published = new ContentValues();
        published.put(MediaStore.MediaColumns.IS_PENDING, 0);
        assertEquals(1, DownloadFolderHooks.update(resolver, document, published, null, null));
    }

    @Test
    public void defaultFolderDelegatesToXMediaStoreOperations() throws Exception {
        Uri result = DownloadFolderHooks.insert(resolver, IMAGES, values("photo.jpg", "image/jpeg"));
        assertEquals(Uri.withAppendedPath(IMAGES, "1"), result);
        ContentValues published = new ContentValues();
        published.put(MediaStore.MediaColumns.IS_PENDING, 0);
        assertEquals(1, DownloadFolderHooks.update(resolver, result, published, null, null));
        assertEquals(1, DownloadFolderHooks.delete(resolver, result, null, null));
        assertEquals(1, media.inserts);
        assertEquals(1, media.updates);
        assertEquals(1, media.deletes);
        assertEquals(0, documents.created);
    }

    @Test
    public void imageVideoAndDownloadStreamsAreSavedIntoTheChosenTree() throws Exception {
        DownloadFolderHooks.saveFolder(context, first, READ_WRITE);
        Uri[] collections = {IMAGES, VIDEOS, DOWNLOADS};
        for (int index = 0; index < collections.length; index++) {
            String name = "media-" + index + (index == 0 ? ".jpg" : ".mp4");
            Uri document = DownloadFolderHooks.insert(resolver, collections[index],
                    values(name, index == 0 ? "image/jpeg" : "video/mp4"));
            byte[] bytes = {1, 9, (byte) index, 0, 7};
            finish(document, bytes);
            assertArrayEquals(bytes, Files.readAllBytes(documents.file("first/" + name).toPath()));
        }
        assertEquals(3, documents.created);
        assertEquals(0, media.inserts);
        assertEquals(0, media.updates);
        assertEquals(first, resolver.getPersistedUriPermissions().get(0).getUri());
    }

    @Test
    public void completionAcceptsTheOriginalMediaMetadataAlongsideThePendingFlag() throws Exception {
        DownloadFolderHooks.saveFolder(context, first, READ_WRITE);
        ContentValues metadata = values("video.mp4", "video/mp4");
        Uri document = DownloadFolderHooks.insert(resolver, VIDEOS, metadata);
        try (OutputStream output = resolver.openOutputStream(document)) {
            assertNotNull(output);
            output.write(new byte[]{4, 7}, 0, 2);
        }
        metadata.put(MediaStore.MediaColumns.IS_PENDING, 0);
        assertEquals(1, DownloadFolderHooks.update(resolver, document, metadata, null, null));
        assertEquals(0, media.updates);
        assertArrayEquals(new byte[]{4, 7}, Files.readAllBytes(documents.file("first/video.mp4").toPath()));
    }

    @Test
    public void changingTheFolderDoesNotMoveAnInFlightSave() throws Exception {
        DownloadFolderHooks.saveFolder(context, first, READ_WRITE);
        Uri document = DownloadFolderHooks.insert(resolver, VIDEOS, values("video.mp4", "video/mp4"));
        DownloadFolderHooks.saveFolder(context, second, READ_WRITE);
        finish(document, new byte[]{3, 4});
        assertArrayEquals(new byte[]{3, 4}, Files.readAllBytes(documents.file("first/video.mp4").toPath()));
        assertFalse(documents.file("second/video.mp4").exists());
    }

    @Test
    public void resetRestoresXDefaultsAndLetsAnInFlightSaveFinish() throws Exception {
        DownloadFolderHooks.saveFolder(context, first, READ_WRITE);
        Uri document = DownloadFolderHooks.insert(resolver, IMAGES, values("photo.jpg", "image/jpeg"));
        DownloadFolderHooks.resetFolder(context);
        assertNull(DownloadFolderHooks.getFolder(context));
        assertEquals(Uri.withAppendedPath(IMAGES, "1"),
                DownloadFolderHooks.insert(resolver, IMAGES, values("new.jpg", "image/jpeg")));
        finish(document, new byte[]{8});
        assertArrayEquals(new byte[]{8}, Files.readAllBytes(documents.file("first/photo.jpg").toPath()));
    }

    @Test
    public void failedWriteRemovesOnlyTheNewPartialDocument() throws Exception {
        DownloadFolderHooks.saveFolder(context, first, READ_WRITE);
        File existing = documents.file("first/photo.jpg");
        existing.getParentFile().mkdirs();
        Files.write(existing.toPath(), new byte[]{6, 6});
        Uri document = DownloadFolderHooks.insert(resolver, IMAGES, values("photo.jpg", "image/jpeg"));
        documents.failWrites = true;
        try (OutputStream output = resolver.openOutputStream(document)) {
            assertNotNull(output);
            assertThrows(IOException.class, () -> output.write(new byte[]{1, 2, 3}, 0, 3));
        }
        assertEquals(1, DownloadFolderHooks.delete(resolver, document, null, null));
        assertFalse(documents.file("first/photo (1).jpg").exists());
        assertArrayEquals(new byte[]{6, 6}, Files.readAllBytes(existing.toPath()));
        assertEquals(1, documents.deletes);
        assertEquals(0, media.deletes);
    }

    @Test
    public void revokedPermissionFailsWithoutSavingIntoTheWrongDirectory() throws Exception {
        DownloadFolderHooks.saveFolder(context, first, READ_WRITE);
        resolver.releasePersistableUriPermission(first, READ_WRITE);
        assertThrows(SecurityException.class,
                () -> DownloadFolderHooks.insert(resolver, IMAGES, values("photo.jpg", "image/jpeg")));
        assertEquals(0, documents.created);
        assertEquals(0, media.inserts);
    }

    @Test
    public void invalidOrReadOnlySelectionPreservesTheExistingFolder() throws Exception {
        DownloadFolderHooks.saveFolder(context, first, READ_WRITE);
        assertThrows(SecurityException.class,
                () -> DownloadFolderHooks.saveFolder(context, second, Intent.FLAG_GRANT_READ_URI_PERMISSION));
        assertEquals(first, DownloadFolderHooks.getFolder(context));
        documents.writable = false;
        assertThrows(SecurityException.class, () -> DownloadFolderHooks.saveFolder(context, second, READ_WRITE));
        assertEquals(first, DownloadFolderHooks.getFolder(context));
    }

    @Test
    public void settingsOpensTheSystemTreePickerAndCancellationKeepsTheFolder() throws Exception {
        DownloadFolderHooks.saveFolder(context, first, READ_WRITE);
        DownloadFolderActivity activity = Robolectric.buildActivity(DownloadFolderActivity.class).setup().get();
        Button choose = button((ViewGroup) activity.findViewById(android.R.id.content), "Choose download folder");
        assertNotNull(choose);
        choose.performClick();
        ShadowActivity shadow = Shadows.shadowOf(activity);
        ShadowActivity.IntentForResult request = shadow.getNextStartedActivityForResult();
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, request.intent.getAction());
        assertEquals(READ_WRITE, request.intent.getFlags() & READ_WRITE);
        assertTrue((request.intent.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0);
        shadow.receiveResult(request.intent, Activity.RESULT_CANCELED, null);
        assertEquals(first, DownloadFolderHooks.getFolder(context));
        activity.finish();
    }

    private static Button button(ViewGroup parent, String text) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View view = parent.getChildAt(index);
            if (view instanceof Button && ((Button) view).getText().toString().equals(text)) return (Button) view;
            if (view instanceof ViewGroup) {
                Button found = button((ViewGroup) view, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static class Documents extends DownloadFolderTest.LocalDocuments {
        boolean writable = true;
        int deletes;
        Documents(File root) { super(root); }
        @Override public boolean isChildDocument(String parent, String document) {
            return document.startsWith(parent + "/");
        }
        @Override public Cursor queryDocument(String id, String[] projection) {
            MatrixCursor cursor = new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_FLAGS});
            cursor.addRow(new Object[]{writable ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE : 0});
            return cursor;
        }
        @Override public void deleteDocument(String id) throws FileNotFoundException {
            if (!file(id).delete()) throw new FileNotFoundException(id);
            deletes++;
        }
    }

    public static class Media extends ContentProvider {
        int inserts;
        int updates;
        int deletes;
        @Override public boolean onCreate() { return true; }
        @Override public String getType(Uri uri) { return "image/jpeg"; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
            return null;
        }
        @Override public Uri insert(Uri uri, ContentValues values) { return Uri.withAppendedPath(uri, "" + ++inserts); }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { updates++; return 1; }
        @Override public int delete(Uri uri, String selection, String[] args) { deletes++; return 1; }
    }
}
