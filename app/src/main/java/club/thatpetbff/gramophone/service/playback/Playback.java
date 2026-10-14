package club.thatpetbff.gramophone.service.playback;

import club.thatpetbff.gramophone.model.Song;
import androidx.media3.common.Player;

import java.util.List;

public interface Playback {
    void setQueue(List<Song> queue, int position, int progress, boolean resetCurrentSong);

    void playSongAt(int position);

    void setListener(PlaybackListener listener);

    boolean isReady();

    boolean isPlaying();

    boolean isLoading();

    boolean hasMediaItems();

    /**
     * Whether the player is (or has been left) in a fatal error state since
     * the last time playback actually started. Callers should refresh the
     * media source before starting playback when this is true.
     */
    boolean isErrored();

    void start();

    void pause();

    void stop();

    void previous();

    void next();

    void setRepeatMode(@Player.RepeatMode int repeatMode);

    int getProgress();

    int getDuration();

    void setProgress(int progress);

    void setVolume(int volume);

    int getVolume();
}
