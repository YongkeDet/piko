package app.morphe.extension.newx.misc;

import app.morphe.extension.crimera.downloader.messages.DownloadText;
import app.morphe.extension.crimera.downloader.messages.DownloadTexts;
import app.morphe.extension.shared.StringRef;

final class NewXDownloadTexts implements DownloadTexts {
    @Override public String get(DownloadText id, int count) {
        switch (id) {
            case DOWNLOAD_STARTED: return StringRef.str("piko_newx_download_started");
            case DOWNLOADS_STARTED:
            case PART_QUEUED: return StringRef.str("piko_newx_downloads_started", count);
            case ALREADY_DOWNLOADED: return StringRef.str("piko_newx_download_already_downloaded");
            case MEDIA_ALREADY_DOWNLOADED: return StringRef.str("piko_newx_download_media_already_downloaded", count);
            case FOLDER_LOST: return StringRef.str("piko_newx_download_folder_lost");
            case COULD_NOT_START: return StringRef.str("piko_newx_download_start_failed");
            case PART_SKIPPED: return StringRef.str("piko_newx_download_part_skipped", count);
            case PART_FAILED: return StringRef.str("piko_newx_download_part_failed", count);
            case PART_LOST: return StringRef.str("piko_newx_download_part_lost", count);
            default: throw new IllegalArgumentException("Unknown download status: " + id);
        }
    }
}
