package club.thatpetbff.gramophone.dialogs;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.model.Song;

/**
 * Destructive-action confirmation for Delete-from-library (R2), modeled on
 * DeletePlaylistDialog. The copy must state plainly that the file leaves the server
 * and every playlist holding it and cannot be undone from the app; NO network request
 * happens before the positive button (R3 keeps the request itself behind the delete
 * pipeline in LibraryDeleteUtil — the fragment never calls the API).
 */
public class DeleteFromLibraryDialog extends DialogFragment {
    public static final String TAG = DeleteFromLibraryDialog.class.getSimpleName();

    @NonNull
    public static DeleteFromLibraryDialog create(Song song) {
        DeleteFromLibraryDialog dialog = new DeleteFromLibraryDialog();
        Bundle args = new Bundle();
        args.putString("song_id", song.id);
        args.putString("song_title", song.title);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String songId = getArguments().getString("song_id");

        return new MaterialDialog.Builder(requireActivity())
                .title(R.string.delete_from_library_title)
                .content(R.string.delete_from_library_body)
                .positiveText(R.string.delete_from_library_confirm)
                .negativeText(R.string.delete_from_library_cancel)
                .onPositive((dialog, which) ->
                        club.thatpetbff.gramophone.util.LibraryDeleteUtil
                                .deleteFromLibrary(requireContext(), songId))
                .build();
    }
}
