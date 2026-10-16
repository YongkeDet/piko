package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.crimera.downloader.DownloadHandle;
import app.morphe.extension.crimera.downloader.EnqueueResult;
import app.morphe.extension.crimera.downloader.model.DownloadRequest;
import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.crimera.settings.SettingsUi;
import app.morphe.extension.crimera.ui.ButtonView;
import app.morphe.extension.crimera.ui.DialogView;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.StringRef;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground, bounded profile download using the app's authenticated Photos/ Videos paginators. */
public final class ProfileBulkDownload {
    private static final String SETTING = "newx.downloads.profile_bulk";
    private static volatile Session active;
    private static final List<RawPage> RAW_PAGES = new ArrayList<>();
    private static final ThreadLocal<List<?>> PENDING_PAGE = new ThreadLocal<>();
    private static final Set<DownloadRequest> REQUESTS = Collections.newSetFromMap(new WeakHashMap<>());

    private ProfileBulkDownload() { }

    public static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(SETTING, false);
    }

    public static boolean isRunning() { return active != null; }

    static synchronized void markRequest(DownloadRequest request) { REQUESTS.add(request); }
    static synchronized boolean isBatchRequest(DownloadRequest request) { return REQUESTS.contains(request); }

    /** Retain only the current few profile snapshots before display filters hide individual posts. */
    public static void rememberPage(Object type, Object raw, Object filtered) {
        PENDING_PAGE.remove();
        if (!isEnabled() || raw == filtered || !(raw instanceof List<?> source)
                || !(filtered instanceof List<?>) || !(type instanceof Enum<?> kind)) return;
        String name = kind.name();
        if (!name.equals("USER_PROFILE_PHOTOS") && !name.equals("USER_PROFILE_VIDEOS")) return;
        PENDING_PAGE.set(source);
    }

    /** Called at the constructor return, after the native Success instance is initialized. */
    public static synchronized void attachPage(Object success) {
        List<?> source = PENDING_PAGE.get();
        PENDING_PAGE.remove();
        if (source == null) return;
        RAW_PAGES.removeIf(page -> page.success.get() == null || page.success.get() == success);
        RAW_PAGES.add(new RawPage(success, source));
        while (RAW_PAGES.size() > 4) RAW_PAGES.remove(0);
    }

    static synchronized List<?> itemsBeforeFilters(Object success, List<?> visible) {
        for (int i = RAW_PAGES.size() - 1; i >= 0; i--) {
            RawPage page = RAW_PAGES.get(i);
            if (page.success.get() == success) return page.raw;
        }
        return visible;
    }

    private static final class RawPage {
        final WeakReference<Object> success;
        final List<?> raw;
        RawPage(Object success, List<?> raw) { this.success = new WeakReference<>(success); this.raw = raw; }
    }

    public static void start(Context context, Object callback) {
        Activity activity = NewXUtils.findUsableActivity(context);
        if (activity == null || !isEnabled()) return;
        if (active != null) { active.dialog.show(); return; }
        boolean imagesMissing = !NewXDownloadFolders.isConfigured(activity, NewXDownloadFolders.MediaKind.IMAGES);
        boolean videosMissing = !NewXDownloadFolders.isConfigured(activity, NewXDownloadFolders.MediaKind.VIDEOS);
        if (imagesMissing || videosMissing) {
            InlineDownloadButton.promptForDestination(activity, imagesMissing, videosMissing);
            return;
        }
        try {
            Object component = componentOf(callback);
            List<?> tabs = availableTabs(component);
            Object photos = null, videos = null;
            if (tabs != null) for (Object tab : tabs) {
                if (tab instanceof Enum<?> value) {
                    if (value.name().equals("Photos")) photos = tab;
                    if (value.name().equals("Videos")) videos = tab;
                }
            }
            if (photos == null || videos == null) {
                NewXInAppNotification.show(StringRef.str("piko_newx_profile_bulk_unavailable"));
                return;
            }
            Session session = new Session(activity, component, photos, videos);
            active = session;
            session.begin();
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Cannot start profile media download", exception);
            Session session = active;
            if (session != null) session.stop("piko_newx_profile_bulk_incomplete");
            else NewXInAppNotification.show(StringRef.str("piko_newx_profile_bulk_unavailable"));
        }
    }

    private static final class Session {
        final Activity activity;
        final Context context;
        final Object component;
        final Object originalTab;
        final Object photos;
        final Object videos;
        final Handler main = new Handler(Looper.getMainLooper());
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final Set<String> mediaKeys = new HashSet<>();
        final List<DownloadHandle> transfers = new ArrayList<>();
        final DialogView dialog;
        final TextView progress;
        ProfileBulkPager pager = new ProfileBulkPager(SystemClock.elapsedRealtime());
        Object desiredTab;
        volatile boolean stopped;
        volatile int saved, skipped, failed, discovered;
        boolean processing;
        boolean finished;
        String endReason;
        int pages;

        Session(Activity activity, Object component, Object photos, Object videos) {
            this.activity = activity;
            this.context = activity.getApplicationContext();
            this.component = component;
            this.originalTab = selectedTab(component);
            this.photos = photos;
            this.videos = videos;
            this.desiredTab = photos;
            progress = SettingsUi.summaryText(activity);
            LinearLayout body = new LinearLayout(activity);
            body.setPadding(Theme.dpToPx(activity, 24), 0, Theme.dpToPx(activity, 24), 0);
            body.addView(progress, new LinearLayout.LayoutParams(-1, -2));
            dialog = new DialogView(activity).setTitle(text("piko_newx_profile_bulk_button"))
                    .setSubtitle(text("piko_newx_profile_bulk_keep_open")).setBodyView(body);
            ButtonView cancel = SettingsUi.dialogButton(activity, text("piko_newx_settings_cancel"));
            cancel.setOnClickListener(view -> stop("piko_newx_profile_bulk_cancelled"));
            dialog.addButton(cancel);
            dialog.getDialog().setCanceledOnTouchOutside(false);
            dialog.getDialog().setOnCancelListener(ignored -> stop("piko_newx_profile_bulk_cancelled"));
        }

        void begin() {
            dialog.show();
            if (dialog.getDialog().getWindow() != null) {
                dialog.getDialog().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            selectTab(component, photos);
            updateProgress();
            main.post(this::tick);
        }

        void tick() {
            if (finished) return;
            if (activity.isDestroyed() || activity.isFinishing()) {
                stop("piko_newx_profile_bulk_cancelled");
                return;
            }
            updateProgress();
            if (stopped) { if (!processing) finish(); return; }
            if (processing) { main.postDelayed(this::tick, 500); return; }
            try {
                if (selectedTab(component) != desiredTab) {
                    stop("piko_newx_profile_bulk_incomplete");
                    return;
                }
                List<?> items = currentItems(component);
                Object[] snapshot = pagingSnapshot(component);
                if (snapshot == null || snapshot.length != 5) throw new IllegalStateException("Missing native paging state");
                String cursor = snapshot[3] == null ? null : snapshot[3].toString();
                Object event = snapshot[4];
                // Native network event labels are preserved data-model contracts, as with media.
                boolean networkFailed = event != null && event.toString().startsWith("Failure(requestType=");
                boolean fetched = event != null && event.toString().startsWith("Success(requestType=");
                ProfileBulkPager.Step step = pager.step(SystemClock.elapsedRealtime(), items,
                        Boolean.TRUE.equals(snapshot[0]), Boolean.TRUE.equals(snapshot[1]),
                        Boolean.TRUE.equals(snapshot[2]), cursor, networkFailed, fetched);
                switch (step) {
                    case PROCESS -> {
                        processing = true;
                        worker.execute(() -> process(items, cursor));
                    }
                    case NEXT -> {
                        pager.requested(cursor, SystemClock.elapsedRealtime());
                        requestNext(component);
                    }
                    case DONE -> {
                        if (desiredTab == photos) {
                            desiredTab = videos;
                            pager = new ProfileBulkPager(SystemClock.elapsedRealtime());
                            selectTab(component, videos);
                        } else {
                            endReason = failed == 0 ? "piko_newx_profile_bulk_completed" : "piko_newx_profile_bulk_with_failures";
                            finish();
                            return;
                        }
                    }
                    case FAILED -> { stop("piko_newx_profile_bulk_incomplete"); return; }
                    case WAIT -> { }
                }
            } catch (RuntimeException exception) {
                NewXLogger.printException(() -> "Profile media pagination stopped", exception);
                stop("piko_newx_profile_bulk_incomplete");
                return;
            }
            main.postDelayed(this::tick, 500);
        }

        void process(List<?> items, String cursor) {
            try {
                for (Object item : items) {
                    if (stopped) break;
                    List<?> media = ProfilePhotosGallery.getDownloadMedia(item);
                    if (media == null || media.isEmpty()) continue;
                    List<InlineDownloadButton.DownloadItem> downloads = InlineDownloadButton.downloadItems(media);
                    failed += Math.max(0, media.size() - downloads.size());
                    DownloadFileName.PostContext post = DownloadFileName.PostContext.from(item);
                    for (int index = 0; index < downloads.size() && !stopped; index++) {
                        InlineDownloadButton.DownloadItem download = downloads.get(index);
                        String key = InlineDownloadButton.urlKey(download.url);
                        if (!mediaKeys.add(key)) continue;
                        discovered++;
                        drain(false);
                        if (stopped) break;
                        InlineDownloadButton.DownloadItem chosen = InlineDownloadButton.autoSelectedOption(download);
                        if (chosen == null) chosen = download;
                        EnqueueResult result = InlineDownloadButton.enqueueProfileDownload(context, chosen, post,
                                post.screenName, index, downloads.size());
                        switch (result.state()) {
                            case QUEUED -> {
                                synchronized (transfers) { transfers.add(result.handle()); }
                                if (stopped) result.handle().cancel();
                            }
                            case SKIPPED -> skipped++;
                            case FAILED -> failed++;
                            case DESTINATION_LOST -> {
                                failed++;
                                endReason = "piko_newx_profile_bulk_folder_lost";
                                stopped = true;
                            }
                        }
                    }
                }
                drain(true);
            } catch (RuntimeException exception) {
                NewXLogger.printException(() -> "Profile batch download stopped", exception);
                endReason = "piko_newx_profile_bulk_incomplete";
                stopped = true;
                cancelTransfers();
            } finally {
                main.post(() -> {
                    processing = false;
                    pages++;
                    pager.processed(items, cursor, SystemClock.elapsedRealtime());
                    if (stopped) finish();
                });
            }
        }

        /** Never queue more than four transfers, even for a profile with thousands of files. */
        void drain(boolean all) {
            while (true) {
                int remaining;
                synchronized (transfers) {
                    Iterator<DownloadHandle> iterator = transfers.iterator();
                    while (iterator.hasNext()) {
                        DownloadHandle handle = iterator.next();
                        switch (handle.state()) {
                            case COMPLETED -> { saved++; iterator.remove(); }
                            case FAILED -> { failed++; iterator.remove(); }
                            case CANCELLED -> iterator.remove();
                            case QUEUED, RUNNING -> { }
                        }
                    }
                    remaining = transfers.size();
                }
                if (stopped) { cancelTransfers(); return; }
                if (remaining == 0 || !all && remaining < 4) return;
                try { Thread.sleep(200); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    stopped = true;
                    endReason = "piko_newx_profile_bulk_cancelled";
                }
            }
        }

        void cancelTransfers() {
            synchronized (transfers) { for (DownloadHandle handle : transfers) handle.cancel(); }
        }

        void stop(String reason) {
            if (finished) return;
            endReason = reason;
            stopped = true;
            cancelTransfers();
            if (!processing) finish();
        }

        void updateProgress() {
            String phase = text(desiredTab == photos ? "piko_newx_media_tab_photos" : "piko_newx_media_tab_videos");
            progress.setText(StringRef.str("piko_newx_profile_bulk_progress", phase, pages, discovered, saved, skipped, failed));
        }

        void finish() {
            if (finished || processing) return;
            finished = true;
            main.removeCallbacksAndMessages(null);
            worker.shutdown();
            try { selectTab(component, originalTab); }
            catch (RuntimeException error) { NewXLogger.printException(() -> "Cannot restore profile tab", error); }
            dialog.dismiss();
            active = null;
            if (activity.isDestroyed() || activity.isFinishing()) return;
            String reason = text(endReason == null ? "piko_newx_profile_bulk_cancelled" : endReason);
            String summary = StringRef.str("piko_newx_profile_bulk_result", reason, saved, skipped, failed);
            DialogView result = new DialogView(activity).setTitle(text("piko_newx_profile_bulk_button"))
                    .setSubtitle(summary);
            ButtonView close = SettingsUi.dialogButton(activity, text("piko_newx_settings_close"));
            close.setOnClickListener(view -> result.dismiss());
            result.addButton(close);
            result.show();
            // Preserve the top-only success feedback; batch members do not toast individually.
            NewXInAppNotification.tryShow(summary);
        }
    }

    private static String text(String key) { return StringRef.str(key); }
    private static boolean matchesTimeline(Object timelineType, Object tab) {
        if (!(timelineType instanceof Enum<?> kind) || !(tab instanceof Enum<?> selected)) return false;
        return selected.name().equals("Photos") && kind.name().equals("USER_PROFILE_PHOTOS")
                || selected.name().equals("Videos") && kind.name().equals("USER_PROFILE_VIDEOS");
    }
    private static IllegalStateException unpatched() { return new IllegalStateException("Profile download bridge was not patched"); }
    private static Object componentOf(Object callback) { throw unpatched(); }
    private static List<?> availableTabs(Object component) { throw unpatched(); }
    private static Object selectedTab(Object component) { throw unpatched(); }
    private static void selectTab(Object component, Object tab) { throw unpatched(); }
    private static List<?> currentItems(Object component) { throw unpatched(); }
    private static Object[] pagingSnapshot(Object component) { throw unpatched(); }
    private static void requestNext(Object component) { throw unpatched(); }
}
