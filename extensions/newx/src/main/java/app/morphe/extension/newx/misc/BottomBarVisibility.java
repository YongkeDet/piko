package app.morphe.extension.newx.misc;

import app.morphe.extension.crimera.settings.SettingsRegistry;

public final class BottomBarVisibility {
    private BottomBarVisibility() {}

    public static boolean keepVisible(boolean scrollVisible) {
        return scrollVisible || SettingsRegistry.getBooleanOrDefault("newx.navigation.fixed_bottom_bar", true);
    }
}
