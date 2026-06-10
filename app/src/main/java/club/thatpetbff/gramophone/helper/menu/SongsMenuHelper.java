package club.thatpetbff.gramophone.helper.menu;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.dialogs.AddToPlaylistDialog;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.util.NavigationUtil;

import java.util.List;

public class SongsMenuHelper {
    public static boolean handleMenuClick(@NonNull FragmentActivity activity, @NonNull List<Song> songs, int menuItemId) {
        if (menuItemId == R.id.action_play) {
            MusicPlayerRemote.openQueue(songs, 0, true);
            return true;
        } else if (menuItemId == R.id.action_play_next) {
            MusicPlayerRemote.playNext(songs);
            return true;
        } else if (menuItemId == R.id.action_add_to_queue) {
            MusicPlayerRemote.enqueue(songs);
            return true;
        } else if (menuItemId == R.id.action_add_to_playlist) {
            AddToPlaylistDialog.create(songs).show(activity.getSupportFragmentManager(), "ADD_PLAYLIST");
            return true;
        } else if (menuItemId == R.id.action_download) {
            NavigationUtil.startDownload(activity, songs);
            return true;
        }

        return false;
    }
}
