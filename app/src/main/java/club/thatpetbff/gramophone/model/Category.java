package club.thatpetbff.gramophone.model;

import androidx.annotation.StringRes;

import club.thatpetbff.gramophone.R;

public enum Category {
    SONGS(R.string.songs),
    ALBUMS(R.string.albums),
    ARTISTS(R.string.artists),
    GENRES(R.string.genres),
    PLAYLISTS(R.string.playlists),
    LANGUAGE(R.string.language),
    FAVORITES(R.string.favorites);

    @StringRes
    public final int title;

    public boolean select;

    Category(int title) {
        this.title = title;
    }
}
