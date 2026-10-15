package app.morphe.extension.twitter.downloads;

import android.app.DownloadManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowDownloadManager;
import org.robolectric.shadows.ShadowLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import app.morphe.extension.twitter.settings.Settings;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, shadows = DownloadFolderTest.Downloads.class)
public class DownloadFolderTest {
    private static final String AUTHORITY = "piko.test.documents";
    private static final int READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Context context;
    private DownloadManager manager;
    private Downloads downloads;
    private LocalDocuments documents;
    private Uri first;
    private Uri second;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication();
        app.morphe.extension.shared.Utils.context = context;
        manager = context.getSystemService(DownloadManager.class);
        downloads = Shadow.extract(manager);
        Downloads.files.clear();
        documents = new LocalDocuments(temporary.newFolder());
        ProviderInfo info = new ProviderInfo();
        info.authority = AUTHORITY;
        info.exported = true;
        info.grantUriPermissions = true;
        info.readPermission = "android.permission.MANAGE_DOCUMENTS";
        info.writePermission = "android.permission.MANAGE_DOCUMENTS";
        documents.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal(AUTHORITY, documents);
        first = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "first");
        second = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "second");
    }

    private SharedPreferences pending() {
        return context.getSharedPreferences("piko_pending_downloads", Context.MODE_PRIVATE);
    }

    private void select(Uri tree) {
        DownloadFolder.saveFolder(context, tree, READ_WRITE);
        if (DownloadFolder.getTreeUri(context) == null) {
            for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("PikoDownloadFolder")) {
                if (item.throwable != null) item.throwable.printStackTrace();
            }
        }
        assertEquals(tree, DownloadFolder.getTreeUri(context));
    }

    private long start(String filename) {
        DownloadFolder.downloadFile(context, "https://example.com/media.mp4", filename);
        assertEquals(1, pending().getAll().size());
        return Long.parseLong(pending().getAll().keySet().iterator().next());
    }

    private void complete(long id, byte[] bytes) throws Exception {
        File source = temporary.newFile();
        Files.write(source.toPath(), bytes);
        Downloads.files.put(id, source);
        ShadowDownloadManager.ShadowRequest request = Shadow.extract(downloads.getRequest(id));
        request.setStatus(DownloadManager.STATUS_SUCCESSFUL);
    }

    @Test
    public void defaultsDelegateToTheOriginalPublicDirectory() {
        assertFalse(DownloadFolder.isCustomFolderEnabled(context));
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse("https://example.com/v.mp4"));
        assertSame(request, DownloadFolder.configureDestination(request, "Movies", "Twitter/v.mp4"));
        ShadowDownloadManager.ShadowRequest shadow = Shadow.extract(request);
        assertEquals(Uri.fromFile(new File(Environment.getExternalStoragePublicDirectory("Movies"),
                "Twitter/v.mp4")), shadow.getDestination());
        long id = DownloadFolder.enqueue(manager, request);
        assertSame(request, downloads.getRequest(id));
        assertTrue(pending().getAll().isEmpty());
    }

    @Test
    public void readOnlySelectionRetainsThePreviousFolder() {
        select(first);
        DownloadFolder.saveFolder(context, second, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        assertEquals(first, DownloadFolder.getTreeUri(context));
    }

    @Test
    public void customDestinationPreservesRequestHeadersAndUsesPrivateStaging() throws Exception {
        select(first);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse("https://example.com/v.mp4"));
        request.addRequestHeader("Accept", "video/mp4");
        DownloadFolder.configureDestination(request, "Movies", "Twitter/v.mp4");
        long id = DownloadFolder.enqueue(manager, request);
        ShadowDownloadManager.ShadowRequest shadow = Shadow.extract(request);
        assertTrue(shadow.getDestination().getPath().startsWith(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getPath()));
        assertEquals("Accept", shadow.getRequestHeaders().get(0).first);
        assertEquals("v.mp4", new JSONObject(pending().getString(Long.toString(id), "")).getString("filename"));
    }

    @Test
    public void switchingFoldersDoesNotRetargetAnInFlightDownload() throws Exception {
        select(first);
        long id = start("video.mp4");
        select(second);
        assertEquals(first.toString(), new JSONObject(pending().getString(Long.toString(id), "")).getString("tree"));
    }

    @Test
    public void revokedPermissionBlocksNewDownloads() {
        select(first);
        context.getContentResolver().releasePersistableUriPermission(first, READ_WRITE);
        DownloadFolder.downloadFile(context, "https://example.com/v.mp4", "video.mp4");
        assertEquals(0, downloads.getRequestCount());
        assertTrue(pending().getAll().isEmpty());
    }

    @Test
    public void copyIsScheduledOnlyAfterDownloadCompletes() throws Exception {
        select(first);
        long id = start("video.mp4");
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        assertTrue(jobs.getAllPendingJobs().isEmpty());
        complete(id, new byte[]{1, 2, 3});
        new DownloadCompleteReceiver().onReceive(context, new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                .putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, id));
        assertEquals(1, jobs.getAllPendingJobs().size());
        assertEquals(id, jobs.getAllPendingJobs().get(0).getExtras().getLong("download_id"));
    }

    @Test
    public void successfulCopyClosesTheFileBeforeRemovingStaging() throws Exception {
        select(first);
        long id = start("video.mp4");
        byte[] bytes = new byte[150_000];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31);
        complete(id, bytes);
        assertFalse(DownloadFolder.finishDownload(context, id));
        assertArrayEquals(bytes, Files.readAllBytes(documents.file("first/video.mp4").toPath()));
        assertTrue(pending().getAll().isEmpty());
        assertEquals(0, downloads.getRequestCount());
    }

    @Test
    public void copyFailureRetainsTheDownloadAndRetriesIntoTheSameDocument() throws Exception {
        select(first);
        long id = start("video.mp4");
        byte[] bytes = new byte[]{4, 5, 6};
        complete(id, bytes);
        documents.failWrites = true;
        assertTrue(DownloadFolder.finishDownload(context, id));
        assertTrue(pending().contains(Long.toString(id)));
        assertEquals(1, downloads.getRequestCount());
        documents.failWrites = false;
        assertFalse(DownloadFolder.finishDownload(context, id));
        assertEquals(1, documents.created);
        assertArrayEquals(bytes, Files.readAllBytes(documents.file("first/video.mp4").toPath()));
    }

    @Test
    public void existingFileIsNotOverwritten() throws Exception {
        select(first);
        File existing = documents.file("first/video.mp4");
        existing.getParentFile().mkdirs();
        Files.write(existing.toPath(), new byte[]{9});
        long id = start("video.mp4");
        complete(id, new byte[]{1, 2});
        assertFalse(DownloadFolder.finishDownload(context, id));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(existing.toPath()));
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(documents.file("first/video (1).mp4").toPath()));
    }

    @Test
    public void resetDoesNotDiscardPendingDownloads() {
        select(first);
        start("video.mp4");
        DownloadFolder.resetFolder(context);
        assertFalse(DownloadFolder.isCustomFolderEnabled(context));
        assertEquals(1, pending().getAll().size());
    }

    @Test
    public void untrackedCompletionBroadcastIsIgnored() {
        new DownloadCompleteReceiver().onReceive(context, new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                .putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 999));
        assertTrue(context.getSystemService(JobScheduler.class).getAllPendingJobs().isEmpty());
    }

    @Implements(DownloadManager.class)
    public static class Downloads extends ShadowDownloadManager {
        static final Map<Long, File> files = new HashMap<>();

        @Implementation
        protected ParcelFileDescriptor openDownloadedFile(long id) throws FileNotFoundException {
            File source = files.get(id);
            if (source == null) throw new FileNotFoundException();
            return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }

    public static class LocalDocuments extends DocumentsProvider {
        private final File directory;
        boolean failWrites;
        int created;

        LocalDocuments(File directory) { this.directory = directory; }
        File file(String id) { return new File(directory, id); }
        @Override public boolean onCreate() { return true; }
        @Override public Cursor queryRoots(String[] projection) { return new MatrixCursor(new String[]{"root_id"}); }
        @Override public Cursor queryChildDocuments(String id, String[] projection, String order) {
            return new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});
        }

        @Override
        public Cursor queryDocument(String id, String[] projection) {
            MatrixCursor cursor = new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_FLAGS});
            cursor.addRow(new Object[]{DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE});
            return cursor;
        }

        @Override
        public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
            File output = file(parent + "/" + name);
            int dot = name.lastIndexOf('.');
            String stem = dot < 0 ? name : name.substring(0, dot);
            String extension = dot < 0 ? "" : name.substring(dot);
            for (int number = 1; output.exists(); number++) {
                output = file(parent + "/" + stem + " (" + number + ")" + extension);
            }
            output.getParentFile().mkdirs();
            try {
                if (!output.createNewFile()) throw new IOException("Could not create file");
            } catch (IOException exception) {
                throw new FileNotFoundException(exception.toString());
            }
            created++;
            String id = parent + "/" + output.getName();
            Uri document = DocumentsContract.buildDocumentUriUsingTree(
                    DocumentsContract.buildTreeDocumentUri(AUTHORITY, parent), id);
            File destination = output;
            ShadowContentResolver resolver = Shadow.extract(getContext().getContentResolver());
            // Robolectric supplies document streams explicitly; use real filesystem bytes.
            resolver.registerOutputStreamSupplier(document, () -> {
                try {
                    return new FileOutputStream(destination) {
                        @Override
                        public void write(byte[] bytes, int offset, int length) throws IOException {
                            if (failWrites) throw new IOException("Simulated provider failure");
                            super.write(bytes, offset, length);
                        }
                    };
                } catch (FileNotFoundException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
            return id;
        }

        @Override
        public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
                throws FileNotFoundException {
            if (failWrites && mode.contains("w")) throw new FileNotFoundException("Simulated provider failure");
            return ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.parseMode(mode));
        }
    }
}
