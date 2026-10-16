package app.morphe.extension.newx.misc;

import android.os.SystemClock;

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
    private static final DownloadBannerPolicy POLICY = new DownloadBannerPolicy();

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
            return sendRich(message, null, DownloadBannerPolicy.INFO) || send(message);
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
        try { return sendRich(message, null, DownloadBannerPolicy.SUCCESS) || send(message); }
        catch (Throwable ignored) { return false; }
    }

    public static boolean tryShowSuccessForUser(String message, String username) {
        return tryShowSuccess(formatForUser(message, username));
    }

    public static boolean tryShowError(String message) {
        try { return sendRich(message, null, DownloadBannerPolicy.ERROR) || send(message); }
        catch (Throwable ignored) { return false; }
    }

    public static void showError(String message) {
        if (!tryShowError(message)) Utils.showToastShort(message);
    }

    public static void showErrorForUser(String message, String username) {
        showError(formatForUser(message, username));
    }

    static void progress(String title, String detail) {
        POLICY.beginProgress();
        try { sendRich(title, detail, DownloadBannerPolicy.PROGRESS); }
        catch (Throwable ignored) { /* Try again on the next foreground tick. */ }
    }

    static void finishProgress() {
        if (!POLICY.endProgress()) return;
        try { sendRich("", null, DownloadBannerPolicy.CLEAR); }
        catch (Throwable ignored) { /* The native timeout still removes a stale progress notice. */ }
    }

    // These Object-only methods are called by direct, patch-time-resolved native bytecode.
    public static void registerNative(Object notification, int kind) { POLICY.register(notification, kind); }
    public static void forgetNative(Object notification) { POLICY.forget(notification); }
    public static int decideNative(Object incoming, Object current) {
        return POLICY.decide(incoming, current, SystemClock.elapsedRealtime());
    }

    /** Enabled only by the separately validated native update adapter. */
    static boolean hasUnifiedHost() { return false; }
    private static boolean sendRich(String title, String detail, int kind) { return false; }
    public static boolean interceptNative(Object manager, Object entry) { return false; }

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
