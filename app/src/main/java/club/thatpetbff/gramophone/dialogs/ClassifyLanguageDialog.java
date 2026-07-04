package club.thatpetbff.gramophone.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.ArrayList;
import java.util.List;

import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.model.Language;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.util.LanguageUtil;

public class ClassifyLanguageDialog extends DialogFragment {

    @NonNull
    public static ClassifyLanguageDialog create(Song song) {
        ClassifyLanguageDialog dialog = new ClassifyLanguageDialog();
        Bundle args = new Bundle();
        args.putParcelable("song", song);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Song song = getArguments().getParcelable("song");
        List<Language> languages = Language.getSupportedLanguages();

        List<String> names = new ArrayList<>(languages.size());
        for (Language lang : languages) {
            names.add(lang.englishName);
        }

        return new MaterialDialog.Builder(requireActivity())
                .title(R.string.action_classify_language)
                .items(names)
                .itemsCallback((materialDialog, view, i, charSequence) -> {
                    materialDialog.dismiss();
                    if (song == null || i < 0 || i >= languages.size()) return;

                    Language chosen = languages.get(i);
                    LanguageUtil.setSongLanguage(song.id, chosen.code, new LanguageUtil.ClassificationCallback() {
                        @Override
                        public void onSuccess(String genreName) {
                            Toast.makeText(requireActivity(),
                                    getString(R.string.classified_as, genreName),
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception exception) {
                            Toast.makeText(requireActivity(),
                                    getString(R.string.classify_language_failed),
                                    Toast.LENGTH_SHORT).show();
                            exception.printStackTrace();
                        }
                    });
                })
                .build();
    }
}
