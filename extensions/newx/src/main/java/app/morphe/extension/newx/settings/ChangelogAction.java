package app.morphe.extension.newx.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import app.morphe.extension.crimera.settings.SettingsActionHandler;

public final class ChangelogAction implements SettingsActionHandler {
    @Override
    public void run(Activity activity) {
        if (activity != null) activity.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/YongkeDet/piko/releases")));
    }
}
