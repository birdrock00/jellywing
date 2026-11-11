package club.thatpetbff.gramophone.fragments.library;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import club.thatpetbff.gramophone.adapter.song.ShuffleButtonSongAdapter;
import club.thatpetbff.gramophone.adapter.song.SongAdapter;
import club.thatpetbff.gramophone.util.FavoritesStore;

import org.jellyfin.apiclient.model.querying.ItemFilter;
import org.jellyfin.apiclient.model.querying.ItemQuery;

public class FavoritesFragment extends SongsFragment {
    private final FavoritesStore.Listener favoritesListener = new FavoritesStore.Listener() {
        @Override
        public void onFavoritesChanged() {
            // A favorite was toggled (from the player or anywhere else):
            // re-query the server so the row appears/disappears immediately
            // instead of only after an app restart.
            reloadNow();
        }
    };

    @NonNull
    @Override
    protected ItemQuery createQuery() {
        ItemQuery query = super.createQuery();

        // the only difference from the songs fragment is the favorite filter
        query.setFilters(new ItemFilter[]{ItemFilter.IsFavorite});

        return query;
    }

    @NonNull
    @Override
    protected SongAdapter createAdapter() {
        SongAdapter adapter = super.createAdapter();

        // set the shuffle button adapter to only shuffle favorites
        if (adapter instanceof ShuffleButtonSongAdapter) {
            ((ShuffleButtonSongAdapter) adapter).setFavorite(true);
        }

        return adapter;
    }

    @Override
    protected boolean shouldReloadOnVisible() {
        // Favorites are the one list that changes while the user is inside
        // the app (the favorite button), so always refresh it on display.
        return true;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FavoritesStore.addListener(favoritesListener);
    }

    @Override
    public void onDestroyView() {
        FavoritesStore.removeListener(favoritesListener);
        super.onDestroyView();
    }
}
