package app.morphe.extension.newx.misc;

import java.util.concurrent.atomic.AtomicLong;
import app.morphe.extension.shared.StringRef;

import app.morphe.extension.shared.Utils;

/**
 * Small release-neutral bridge to NewX's in-app notification pipeline.
 *
 * <p>The native notification implementation and model classes are obfuscated and are therefore
 * filled in by the NewX patch at patch time.  This class deliberately exposes only Object/String
 * contracts to the extension.  If the native facade has not been constructed yet, or if the
 * injected call cannot be completed, the original short Toast remains the fallback.</p>
 */
public final class NewXInAppNotification {
    private static volatile Object facade;
    private static final AtomicLong IDS = new AtomicLong(1L << 32);
    private static final class Host { static final DownloadBannerController INSTANCE = new DownloadBannerController(); }
    static long nextTaskId() { return IDS.getAndIncrement(); }

    private NewXInAppNotification() {
    }

    /** Stores the native in-app notification facade once its constructor has completed. */
    public static void capture(Object value) {
        if (value != null) facade = value;
    }

    /** Shows a status message in NewX, falling back to the existing short Toast. */
    public static void show(String message) {
        if (!tryShow(message)) Utils.showToastShort(message);
    }

    /** Shows a status message; reports whether the in-app host took it. */
    public static boolean tryShow(String message) {
        try {
            return queue(message, DownloadBannerPolicy.INFO) || send(message);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Shows a status message while identifying the post's author. */
    public static void showForUser(String message, String username) {
        show(formatForUser(message, username));
    }

    /** Shows a post-author message; reports whether the in-app host took it. */
    public static boolean tryShowForUser(String message, String username) {
        return tryShow(formatForUser(message, username));
    }

    public static boolean tryShowSuccess(String message) {
        try { return queue(message, DownloadBannerPolicy.SUCCESS) || send(message); }
        catch (Throwable ignored) { return false; }
    }

    public static boolean tryShowSuccessForUser(String message, String username) {
        return tryShowSuccess(formatForUser(message, username));
    }

    public static boolean tryShowError(String message) {
        try { return queue(message, DownloadBannerPolicy.ERROR) || send(message); }
        catch (Throwable ignored) { return false; }
    }

    public static void showError(String message) {
        if (!tryShowError(message)) Utils.showToastShort(message);
    }

    public static void showErrorForUser(String message, String username) {
        showError(formatForUser(message, username));
    }

    private static boolean queue(String message, int kind) {
        if (!hasUnifiedHost()) return false;
        Host.INSTANCE.result(nextTaskId(), kind, headline(kind), message);
        return true;
    }

    private static String headline(int kind) {
        return StringRef.str(kind == DownloadBannerPolicy.ERROR ? "piko_newx_download_banner_failed"
                : kind == DownloadBannerPolicy.SUCCESS ? "piko_newx_download_banner_saved"
                : "piko_newx_download_banner_info");
    }

    static void startProgress(long id, String title, String detail, Runnable cancel) {
        if (hasUnifiedHost()) Host.INSTANCE.start(id, title, detail, cancel);
    }
    static void progress(long id, String title, String detail, int percent) {
        if (hasUnifiedHost()) Host.INSTANCE.progress(id, title, detail, percent);
    }
    static void finishProgress(long id) {
        if (hasUnifiedHost()) Host.INSTANCE.finish(id);
    }
    static void taskResult(long id, int kind, String title, String detail) {
        if (hasUnifiedHost()) Host.INSTANCE.result(id, kind, title, detail);
        else if (kind == DownloadBannerPolicy.ERROR) showError(detail);
        else if (kind == DownloadBannerPolicy.SUCCESS) tryShowSuccess(detail);
        else show(detail);
    }
    static void taskSuccess(long id, String message, String username) {
        taskResult(id, DownloadBannerPolicy.SUCCESS, headline(DownloadBannerPolicy.SUCCESS), formatForUser(message, username));
    }
    static void taskError(long id, String message, String username) {
        taskResult(id, DownloadBannerPolicy.ERROR, headline(DownloadBannerPolicy.ERROR), formatForUser(message, username));
    }
    static void taskInfo(long id, String message, String username) {
        taskResult(id, DownloadBannerPolicy.INFO, headline(DownloadBannerPolicy.INFO), formatForUser(message, username));
    }

    // Direct patch-time-resolved native calls; no reflection or release identities in the extension.
    public static int decideNative(Object incoming, Object current) { return Host.INSTANCE.decide(incoming, current); }
    public static void nativeVisible(Object model) { Host.INSTANCE.visible(model); }
    public static void nativeDisposed(Object model, Object reason, boolean current) {
        Host.INSTANCE.disposed(model, reason instanceof Enum<?> value ? value.name() : "", current);
    }
    public static String progressText(Object literal) { return Host.INSTANCE.text(literal); }
    static boolean hasUnifiedHost() { return false; }
    static Object createRich(String title, String detail, int kind, Object button) { return null; }
    static boolean postRich(Object model, int kind) { return false; }
    static Object cancelButton(String text) { return null; }
    static Object titleLiteral(Object model) { return null; }
    static Object detailLiteral(Object model) { return null; }
    static Object makeState(Object value) { return null; }
    static void writeState(Object state, Object value) { }
    static String readState(Object state) { return null; }
    public static boolean interceptNative(Object manager, Object entry) { return false; }
    public static void observeNative(Object manager) { }
    public static void disposingNative(Object manager, Object entry, Object reason) { }

    public static boolean fullContent(Object model) { return Host.INSTANCE.fullContent(model); }
    public static void drawProgress(Object model, Object composer) { Host.INSTANCE.drawProgress(model, composer); }
    static Object readValue(Object state) { return null; }
    static void drawNativeProgress(Object provider, Object composer, boolean unknown) { }
    static int progressAccent() { return app.morphe.extension.newx.ui.Theme.primaryAccent(Utils.getContext()); }
    static int progressTrack() { return (progressAccent() & 0x00ffffff) | 0x33000000; }

    /** Function0 is attached at patch time, keeping obfuscated Compose types out of the extension. */
    public static final class ProgressProvider {
        private final Object state;
        public ProgressProvider(Object state) { this.state = state; }
        public Object invoke() {
            Object value = readValue(state);
            return value instanceof Number number ? Math.max(0f, Math.min(1f, number.floatValue())) : 0f;
        }
    }

    static String formatForUser(String message, String username) {
        if (username == null) return message;

        String normalizedUsername = username.trim();
        if (normalizedUsername.isEmpty()) return message;
        if (normalizedUsername.charAt(0) == '@') {
            normalizedUsername = normalizedUsername.substring(1).trim();
        }
        if (normalizedUsername.isEmpty()) return message;

        return message + " — @" + normalizedUsername;
    }

    // Replaced with direct smali calls to the resolved NewX notification model/sender.
    private static boolean send(String message) {
        return false;
    }
}
