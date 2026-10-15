package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.util.Arrays;
import java.util.HashSet;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/** Forwards the registered mirror links to the native, patch-time-validated X link activity. */
public final class CustomLinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            int hostsId = getResources().getIdentifier("piko_newx_custom_link_hosts", "array", getPackageName());
            int targetId = getResources().getIdentifier("piko_newx_custom_link_target", "string", getPackageName());
            Uri data = getIntent() == null ? null : getIntent().getData();
            String rewritten = CustomLinkRouter.rewrite(data == null ? null : data.toString(),
                    new HashSet<>(Arrays.asList(getResources().getStringArray(hostsId))));
            if (rewritten != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(rewritten))
                        .setClassName(getPackageName(), getString(targetId))
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            }
        } catch (RuntimeException exception) {
            Utils.showToastShort(StringRef.str("piko_newx_custom_link_failed"));
        } finally {
            finish();
        }
    }
}
