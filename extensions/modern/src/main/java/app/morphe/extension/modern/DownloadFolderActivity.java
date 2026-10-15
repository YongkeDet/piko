/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 * Copyright (C) 2026 YongkeDet
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.modern;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;

/** Standalone settings for the rewritten X app, opened from its launcher shortcut. */
public final class DownloadFolderActivity extends Activity {
    private static final int PICK_FOLDER = 702;
    private TextView summary;

    private String text(String english, String chinese) {
        return "zh".equals(Locale.getDefault().getLanguage()) ? chinese : english;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        DownloadFolderHooks.initialize(this);
        setTitle(text("Piko download folder", "Piko 下载文件夹"));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        TextView explanation = new TextView(this);
        explanation.setText(text("Choose where X saves photos and videos. You can create a folder in the system picker.",
                "选择 X 保存图片和视频的文件夹。可以在系统选择器中新建文件夹。"));
        layout.addView(explanation);
        summary = new TextView(this);
        summary.setPadding(0, padding, 0, padding);
        layout.addView(summary);

        Button choose = new Button(this);
        choose.setText(text("Choose download folder", "选择下载文件夹"));
        choose.setOnClickListener(view -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            Uri folder = DownloadFolderHooks.getFolder(this);
            if (folder != null) picker.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder);
            try {
                startActivityForResult(picker, PICK_FOLDER);
            } catch (RuntimeException exception) {
                Toast.makeText(this, text("The system folder picker is unavailable.", "无法打开系统文件夹选择器。"),
                        Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(choose);

        Button reset = new Button(this);
        reset.setText(text("Restore X default folder", "恢复 X 默认目录"));
        reset.setOnClickListener(view -> {
            DownloadFolderHooks.resetFolder(this);
            refresh();
        });
        layout.addView(reset);
        setContentView(layout);
        refresh();
    }

    private void refresh() {
        Uri folder = DownloadFolderHooks.getFolder(this);
        summary.setText(folder == null ? text("Using X's default folder", "正在使用 X 默认目录")
                : DocumentsContract.getTreeDocumentId(folder));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            DownloadFolderHooks.saveFolder(this, data.getData(), data.getFlags());
            refresh();
            Toast.makeText(this, text("Download folder saved", "下载文件夹已保存"), Toast.LENGTH_SHORT).show();
        } catch (IOException | RuntimeException exception) {
            Toast.makeText(this, text("Choose a writable folder and allow access.", "请选择可写入的文件夹，并允许访问。"),
                    Toast.LENGTH_LONG).show();
        }
    }
}
