/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.fragments;

import android.content.Context;
import android.os.Bundle;
import android.preference.*;
import android.view.View;
import android.widget.ListView;
import androidx.annotation.Nullable;

import app.morphe.extension.twitter.settings.ActivityHook;
import app.morphe.extension.twitter.settings.SettingsSearchNavigator;
import app.morphe.extension.twitter.settings.widgets.Helper;
import app.morphe.extension.twitter.settings.ScreenBuilder;
import app.morphe.extension.twitter.settings.Settings;
import app.morphe.extension.twitter.downloads.DownloadFolder;

@SuppressWarnings("deprecation")
public class PageFragment extends PreferenceFragment {
    private Context context;
    private Preference searchTargetPreference;

//    @Override
//    public void onResume() {
//        super.onResume();
//        ActivityHook.toolbar.setTitle(StringRef.str("piko_title_settings"));
//    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        Context preferenceContext = ActivityHook.getPreferenceContext(context);

        PreferenceManager preferenceManager = getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(preferenceContext);
        preferenceManager.setSharedPreferencesName(Settings.SHARED_PREF_NAME);

        Helper helper = new Helper(context);
        ScreenBuilder screenBuilder = new ScreenBuilder(context, screen, helper);

        Bundle bundle = getArguments();
        String activity_name = bundle != null ? bundle.getString(Settings.ACT_NAME) : null;
        screenBuilder.buildSection(activity_name, false);
//        setSupportActionBar(ActivityHook.toolbar);
        setPreferenceScreen(screen);
        searchTargetPreference = SettingsSearchNavigator.findTargetPreference(screen, bundle);

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        scrollToSearchTarget();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshDownloadFolderPreferences();
        DownloadFolder.resumePendingDownloads(context);
    }

    public void refreshDownloadFolderPreferences() {
        Preference chooser = findPreference(Settings.SELECT_DOWNLOAD_FOLDER);
        if (chooser == null) return;
        chooser.setSummary(DownloadFolder.getFolderSummary(context));
        boolean useDefault = !DownloadFolder.isCustomFolderEnabled(context);
        Preference publicFolder = findPreference(Settings.VID_PUBLIC_FOLDER.key);
        Preference subfolder = findPreference(Settings.VID_SUBFOLDER.key);
        if (publicFolder != null) publicFolder.setEnabled(useDefault);
        if (subfolder != null) subfolder.setEnabled(useDefault);
        Preference reset = findPreference(Settings.RESET_DOWNLOAD_FOLDER);
        if (reset != null) reset.setEnabled(!useDefault);
    }

    private void scrollToSearchTarget() {
        if (searchTargetPreference == null) {
            return;
        }

        View view = getView();
        if (view == null) {
            return;
        }

        ListView listView = view.findViewById(android.R.id.list);
        if (listView == null) {
            return;
        }

        SettingsSearchNavigator.scrollToPreferenceAndHighlight(listView, searchTargetPreference);
    }

}
