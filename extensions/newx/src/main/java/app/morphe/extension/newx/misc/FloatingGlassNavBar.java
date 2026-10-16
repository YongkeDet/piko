package app.morphe.extension.newx.misc;

import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.Utils;

/** Styles the host navigation container, keeping its items, semantics and inset handling. */
public final class FloatingGlassNavBar {
    private FloatingGlassNavBar() {}

    public static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault("newx.appearance.floating_glass_navbar", false);
    }

    public static Object decorate(Object modifier) {
        return isEnabled() ? nativeDecorate(modifier) : modifier;
    }

    public static boolean useBlur(boolean original) {
        // The native Haze capability gate and Disable blur patch still control actual rendering.
        return original || isEnabled();
    }

    public static Object haze(Object modifier, Object state, Object composer, int flags) {
        return isEnabled() ? nativeGlassHaze(modifier, state, composer, flags)
                : nativeHaze(modifier, state, composer, flags);
    }

    public static float tintAlpha() {
        return Theme.isDark(Utils.getContext()) ? 0.56f : 0.48f;
    }

    public static int borderArgb() {
        return Theme.isDark(Utils.getContext()) ? 0x55FFFFFF : 0x330F1419;
    }

    // Filled with typed, patch-time-resolved Compose calls. No unstable classes or reflection.
    private static Object nativeDecorate(Object modifier) { return modifier; }
    private static Object nativeHaze(Object modifier, Object state, Object composer, int flags) {
        return modifier;
    }
    private static Object nativeGlassHaze(Object modifier, Object state, Object composer, int flags) {
        return modifier;
    }
}
