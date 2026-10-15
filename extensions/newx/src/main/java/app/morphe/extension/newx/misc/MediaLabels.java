package app.morphe.extension.newx.misc;

import java.util.Locale;
import app.morphe.extension.shared.StringRef;

/** Translate presentation without changing the downloader's internal option keys. */
final class MediaLabels {
    private MediaLabels() {}
    static String localized(String value) {
        if (value == null) return null;
        switch (value.toLowerCase(Locale.ROOT)) {
            case "image": return StringRef.str("piko_newx_media_type_image");
            case "video": return StringRef.str("piko_newx_media_type_video");
            case "original": return StringRef.str("piko_newx_inline_download_quality_original");
            case "large": return StringRef.str("piko_newx_inline_download_quality_large");
            case "medium": return StringRef.str("piko_newx_inline_download_quality_medium");
            case "small": return StringRef.str("piko_newx_inline_download_quality_small");
            default: return value;
        }
    }
}
