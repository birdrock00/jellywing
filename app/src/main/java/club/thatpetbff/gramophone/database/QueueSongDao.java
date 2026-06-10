package club.thatpetbff.gramophone.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.model.Song;

import java.util.ArrayList;
import java.util.List;

@Dao
public abstract class QueueSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertQueueSongs(List<QueueSong> queueSongs);

    @Query("DELETE FROM queueSongs")
    public abstract void deleteQueueSongs();

    @Query("SELECT * from queueSongs WHERE queue = :queue ORDER BY `index`")
    public abstract List<QueueSong> getQueueSongs(int queue);

    @Transaction
    public List<Song> getQueue(int queue) {
        List<QueueSong> queueSongs = getQueueSongs(queue);
        List<Song> songs = new ArrayList<>();

        for (QueueSong queueSong : queueSongs) {
            Song song = App.getDatabase().songDao().getSong(queueSong.songId);
            if (song != null) songs.add(song);
        }

        return songs;
    }

    @Transaction
    public void setQueue(List<Song> songs, int queue) {
        List<QueueSong> queueSongs = new ArrayList<>();
        if (songs == null) {
            insertQueueSongs(queueSongs);
            return;
        }

        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            if (song != null && song.id != null) {
                queueSongs.add(new QueueSong(song.id, queueSongs.size(), queue));
            }
        }

        insertQueueSongs(queueSongs);
    }

    @Transaction
    public void updateQueues(List<Song> playingQueue, List<Song> shuffledQueue) {
        // copy queues by value to avoid concurrent modification exceptions from database
        App.getDatabase().songDao().deleteSongs();
        App.getDatabase().songDao().insertSongs(validSongs(playingQueue));

        deleteQueueSongs();
        setQueue(validSongs(playingQueue), 0);
        setQueue(validSongs(shuffledQueue), 1);
    }

    private List<Song> validSongs(List<Song> songs) {
        List<Song> validSongs = new ArrayList<>();
        if (songs == null) {
            return validSongs;
        }

        for (Song song : songs) {
            if (song != null && song.id != null) {
                validSongs.add(song);
            }
        }

        return validSongs;
    }
}
