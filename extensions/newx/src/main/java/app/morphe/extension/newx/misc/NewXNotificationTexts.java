package app.morphe.extension.newx.misc;

import app.morphe.extension.crimera.downloader.engine.NotificationTexts;
import app.morphe.extension.shared.StringRef;

final class NewXNotificationTexts implements NotificationTexts {
    @Override public String waitingForConnection() { return StringRef.str("piko_newx_download_waiting"); }
    @Override public String cancelAction() { return StringRef.str("piko_newx_download_cancel_action"); }
    @Override public String shareAction() { return StringRef.str("piko_newx_download_share_action"); }
    @Override public String deleteAction() { return StringRef.str("piko_newx_download_delete_action"); }
    @Override public String retryAction() { return StringRef.str("piko_newx_download_retry_action"); }
    @Override public String downloadCompleted() { return StringRef.str("piko_newx_download_completed"); }
    @Override public String downloadFailed() { return StringRef.str("piko_newx_download_failed"); }
    @Override public String noConnection() { return StringRef.str("piko_newx_download_no_connection"); }
    @Override public String folderLost() { return StringRef.str("piko_newx_download_folder_lost"); }
    @Override public String shareChooserTitle(String fileName) {
        return fileName == null || fileName.isEmpty()
                ? StringRef.str("piko_newx_download_share_action")
                : StringRef.str("piko_newx_download_share_file", fileName);
    }
}
