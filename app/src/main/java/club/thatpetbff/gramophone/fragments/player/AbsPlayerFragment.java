package club.thatpetbff.gramophone.fragments.player;

import android.animation.AnimatorSet;
import android.content.Context;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import android.view.Menu;

import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.dialogs.AddToPlaylistDialog;
import club.thatpetbff.gramophone.dialogs.DeleteFromLibraryDialog;
import club.thatpetbff.gramophone.dialogs.CreatePlaylistDialog;
import club.thatpetbff.gramophone.dialogs.SleepTimerDialog;
import club.thatpetbff.gramophone.dialogs.SongDetailDialog;
import club.thatpetbff.gramophone.dialogs.SongShareDialog;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.interfaces.PaletteColorHolder;
import club.thatpetbff.gramophone.model.Album;
import club.thatpetbff.gramophone.model.Artist;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.fragments.AbsMusicServiceFragment;
import club.thatpetbff.gramophone.util.MusicUtil;
import club.thatpetbff.gramophone.util.NavigationUtil;

public abstract class AbsPlayerFragment extends AbsMusicServiceFragment implements Toolbar.OnMenuItemClickListener, PaletteColorHolder {
    private static boolean isToolbarShown = true;

    protected static AnimatorSet currentAnimatorSet = null;

    private Callbacks callbacks;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            callbacks = (Callbacks) context;
        } catch (ClassCastException e) {
            throw new RuntimeException(context.getClass().getSimpleName() + " must implement " + Callbacks.class.getSimpleName());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callbacks = null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final Song song = MusicPlayerRemote.getCurrentSong();
        int itemId = item.getItemId();
        if (itemId == R.id.action_sleep_timer) {
            new SleepTimerDialog().show(getParentFragmentManager(), "SET_SLEEP_TIMER");
            return true;
        } else if (itemId == R.id.action_toggle_favorite) {
            toggleFavorite(song);
            return true;
        } else if (itemId == R.id.action_share) {
            SongShareDialog.create(song).show(getParentFragmentManager(), SongShareDialog.TAG);
            return true;
        } else if (itemId == R.id.action_add_to_playlist) {
            AddToPlaylistDialog.create(song).show(getParentFragmentManager(), "ADD_PLAYLIST");
            return true;
        } else if (itemId == R.id.action_clear_queue) {
            MusicPlayerRemote.clearQueue();
            return true;
        } else if (itemId == R.id.action_save_queue) {
            CreatePlaylistDialog.create(MusicPlayerRemote.getPlayingQueue()).show(getParentFragmentManager(), "ADD_TO_PLAYLIST");
            return true;
        } else if (itemId == R.id.action_delete_from_library) {
            if (song != null) {
                DeleteFromLibraryDialog.create(song)
                        .show(getParentFragmentManager(), DeleteFromLibraryDialog.TAG);
            }
            return true;
        } else if (itemId == R.id.action_details) {
            SongDetailDialog.create(song).show(getParentFragmentManager(), SongDetailDialog.TAG);
            return true;
        } else if (itemId == R.id.action_go_to_album) {
            NavigationUtil.startAlbum(requireActivity(), new Album(song), null);
            return true;
        } else if (itemId == R.id.action_go_to_artist) {
            NavigationUtil.startArtist(requireActivity(), new Artist(song), null);
            return true;
        }
        return false;
    }

    protected void toggleFavorite(Song song) {
        MusicUtil.toggleFavorite(song);
    }

    /**
     * R1 visibility gate for the Delete-from-library overflow item: show it only when a
     * server-backed song is current. With no service connection or an empty queue
     * {@link MusicPlayerRemote#getCurrentSong()} is null, which hides the item rather
     * than crashing. Permission is deliberately NOT probed here (operator decision:
     * refusals surface as toasts, see R5).
     */
    protected void applyDeleteFromLibraryVisibility(@Nullable Toolbar toolbar) {
        if (toolbar == null) return;
        Menu menu = toolbar.getMenu();
        if (menu == null) return;
        MenuItem deleteItem = menu.findItem(R.id.action_delete_from_library);
        if (deleteItem == null) return;
        Song song = MusicPlayerRemote.getCurrentSong();
        boolean serverBacked = song != null && song.id != null && !song.id.trim().isEmpty();
        deleteItem.setVisible(serverBacked);
        // Booleans only: no titles, ids or URLs. Tells a RED run apart from a hierarchy problem.
        android.util.Log.i("DeleteMenu",
                "apply toolbar=" + (toolbar != null) + " menu=" + (toolbar != null && toolbar.getMenu() != null)
                        + " itemFound=true songPresent=" + (song != null)
                        + " serverBacked=" + serverBacked + " visible=" + deleteItem.isVisible());
    }

    protected boolean isToolbarShown() {
        return isToolbarShown;
    }

    protected void setToolbarShown(boolean toolbarShown) {
        isToolbarShown = toolbarShown;
    }

    protected void showToolbar(@Nullable final View toolbar) {
        if (toolbar == null) return;

        setToolbarShown(true);

        toolbar.setVisibility(View.VISIBLE);
        toolbar.animate().alpha(1f).setDuration(PlayerAlbumCoverFragment.VISIBILITY_ANIM_DURATION);
    }

    protected void hideToolbar(@Nullable final View toolbar) {
        if (toolbar == null) return;

        setToolbarShown(false);

        toolbar.animate().alpha(0f).setDuration(PlayerAlbumCoverFragment.VISIBILITY_ANIM_DURATION).withEndAction(() -> toolbar.setVisibility(View.GONE));
    }

    protected void toggleToolbar(@Nullable final View toolbar) {
        if (isToolbarShown()) {
            hideToolbar(toolbar);
        } else {
            showToolbar(toolbar);
        }
    }

    protected void checkToggleToolbar(@Nullable final View toolbar) {
        if (toolbar != null && !isToolbarShown() && toolbar.getVisibility() != View.GONE) {
            hideToolbar(toolbar);
        } else if (toolbar != null && isToolbarShown() && toolbar.getVisibility() != View.VISIBLE) {
            showToolbar(toolbar);
        }
    }

    protected String getUpNextAndQueueTime() {
        final long duration = MusicPlayerRemote.getQueueDurationMillis(MusicPlayerRemote.getPosition());

        return MusicUtil.buildInfoString(
            getResources().getString(R.string.up_next),
            MusicUtil.getReadableDurationString(duration)
        );
    }

    public abstract void onShow();

    public abstract void onHide();

    public abstract boolean onBackPressed();

    public Callbacks getCallbacks() {
        return callbacks;
    }

    public interface Callbacks {
        void onPaletteColorChanged();
    }
}
