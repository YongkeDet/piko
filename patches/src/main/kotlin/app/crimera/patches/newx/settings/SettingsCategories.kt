package app.crimera.patches.newx.settings

import app.crimera.patches.settings.SettingsCategory
import app.crimera.patches.settings.SettingsGroupMetadata

internal object Categories {
    val DOWNLOADS =
        SettingsCategory(
            id = "newx.downloads",
            titleResourceName = "piko_newx_category_downloads_title",
            summaryResourceName = "piko_newx_category_downloads_summary",
            iconResourceName = "ic_vector_arrow_down_circle_stroke",
            order = 100,
        )

    val PROFILE =
        SettingsCategory(
            id = "newx.profile",
            titleResourceName = "piko_newx_category_profile_title",
            summaryResourceName = "piko_newx_category_profile_summary",
            iconResourceName = "ic_vector_post_with_media",
            order = 200,
        )

    val TIMELINE =
        SettingsCategory(
            id = "newx.timeline",
            titleResourceName = "piko_newx_category_timeline_title",
            summaryResourceName = "piko_newx_category_timeline_summary",
            iconResourceName = "ic_vector_home_stroke",
            order = 300,
        )

    val CONTENT =
        SettingsCategory(
            id = "newx.content",
            titleResourceName = "piko_newx_category_content_title",
            summaryResourceName = "piko_newx_category_content_summary",
            iconResourceName = "ic_vector_bulleted_list",
            order = 400,
        )

    val APPEARANCE =
        SettingsCategory(
            id = "newx.appearance",
            titleResourceName = "piko_newx_category_appearance_title",
            summaryResourceName = "piko_newx_category_appearance_summary",
            iconResourceName = "ic_vector_paintbrush_box",
            order = 700,
        )

    val POST_ACTIONS_MEDIA =
        SettingsCategory(
            id = "newx.post_actions_media",
            titleResourceName = "piko_newx_category_post_actions_media_title",
            summaryResourceName = "piko_newx_category_post_actions_media_summary",
            iconResourceName = "ic_vector_post_with_media",
            order = 500,
        )

    val NAVIGATION =
        SettingsCategory(
            id = "newx.navigation",
            titleResourceName = "piko_newx_category_navigation_title",
            summaryResourceName = "piko_newx_category_navigation_summary",
            iconResourceName = "ic_vector_menu",
            order = 600,
        )

    val ADVANCED =
        SettingsCategory(
            id = "newx.advanced",
            titleResourceName = "piko_newx_category_advanced_title",
            summaryResourceName = "piko_newx_category_advanced_summary",
            iconResourceName = "ic_vector_wrench",
            order = 800,
        )
}

internal object Groups {
    val TIMELINE_TABS =
        SettingsGroupMetadata(
            id = "newx.timeline.tabs",
            titleResourceName = "piko_newx_group_timeline_tabs_title",
            summaryResourceName = "piko_newx_group_timeline_tabs_summary",
            iconResourceName = "ic_vector_bulleted_list",
            order = 160,
        )

    val FOR_YOU_FILTERING =
        SettingsGroupMetadata(
            id = "newx.timeline.for_you_filtering",
            titleResourceName = "piko_newx_group_for_you_filtering_title",
            summaryResourceName = "piko_newx_group_for_you_filtering_summary",
            iconResourceName = "ic_vector_filter",
            order = 300,
        )

    val FEATURE_SWITCHES =
        SettingsGroupMetadata(
            id = "newx.advanced.feature_switches",
            titleResourceName = "piko_newx_feature_switches_title",
            summaryResourceName = "piko_newx_feature_switches_summary",
            iconResourceName = "ic_vector_flask_stroke",
            order = 100,
        )

    val VERIFIED_ACCOUNT_FILTERING =
        SettingsGroupMetadata(
            id = "newx.content.verified_account_filtering",
            titleResourceName = "piko_newx_group_verified_account_filtering_title",
            summaryResourceName = "piko_newx_group_verified_account_filtering_summary",
            iconResourceName = "ic_vector_verified_blue",
            order = 600,
        )

    val THEME =
        SettingsGroupMetadata(
            id = "newx.appearance.theme",
            titleResourceName = "piko_newx_group_theme_title",
            summaryResourceName = "piko_newx_group_theme_summary",
            iconResourceName = "ic_vector_paintbrush_box",
            order = 100,
        )

    val INLINE_DOWNLOAD =
        SettingsGroupMetadata(
            id = "newx.post_actions_media.inline_download",
            titleResourceName = "piko_newx_group_inline_download_title",
            summaryResourceName = "piko_newx_group_inline_download_summary",
            iconResourceName = "ic_vector_arrow_down_circle_stroke",
            order = 200,
        )

    val DOWNLOAD_QUALITY =
        SettingsGroupMetadata(
            id = "newx.downloads.quality",
            titleResourceName = "piko_newx_group_download_quality_title",
            summaryResourceName = "piko_newx_group_download_quality_summary",
            iconResourceName = "ic_vector_post_with_media",
            order = 300,
        )

    val MEDIA_PICKER =
        SettingsGroupMetadata(
            id = "newx.downloads.media_picker",
            titleResourceName = "piko_newx_group_media_picker_title",
            summaryResourceName = "piko_newx_group_media_picker_summary",
            iconResourceName = "ic_vector_bulleted_list",
            order = 400,
        )

    val REPLY_SORTING =
        SettingsGroupMetadata(
            id = "newx.post_actions_media.reply_sorting",
            titleResourceName = "piko_newx_group_reply_sorting_title",
            summaryResourceName = "piko_newx_group_reply_sorting_summary",
            iconResourceName = "ic_vector_sort_arrows",
            order = 300,
        )

    val DEBUG_TOOLS =
        SettingsGroupMetadata(
            id = "newx.advanced.debug_tools",
            titleResourceName = "piko_newx_group_debug_tools_title",
            summaryResourceName = "piko_newx_group_debug_tools_summary",
            iconResourceName = "ic_vector_bug_stroke",
            order = 300,
        )
}
