/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

import app.morphe.extension.twitter.downloads.DownloadFolder;

@SuppressWarnings("deprecation")
public class DownloadFolderPickerFragment extends Fragment {
    private static final int PICK_DIRECTORY = 46;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri previous = DownloadFolder.getTreeUri(getActivity());
        if (previous != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, previous);
        try {
            startActivityForResult(intent, PICK_DIRECTORY);
        } catch (RuntimeException exception) {
            DownloadFolder.showFolderError(getActivity());
            // onCreate runs inside a fragment transaction; return after it has committed.
            getActivity().getWindow().getDecorView().post(() -> {
                if (isAdded()) getFragmentManager().popBackStack();
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_DIRECTORY) return;
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            DownloadFolder.saveFolder(getActivity(), data.getData(), data.getFlags());
        }
        getFragmentManager().popBackStack();
    }
}
