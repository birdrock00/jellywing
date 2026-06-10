package club.thatpetbff.gramophone.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import club.thatpetbff.gramophone.model.Song;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class JellyDatabaseInstrumentedTest {
    private JellyDatabase database;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, JellyDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void userDaoStartsEmptyAndMissingUserLookupReturnsNull() {
        assertNull(database.userDao().getUser("missing-user"));
        assertTrue(database.userDao().getUsers().isEmpty());
    }

    @Test
    public void songDaoReplacesByIdAndCacheDaoFindsCachedSongs() {
        Song song = song("song-id", "Original title");
        database.songDao().insertSongs(Collections.singletonList(song));

        Song replacement = song("song-id", "Replacement title");
        database.songDao().insertSongs(Collections.singletonList(replacement));
        database.cacheDao().insertCache(new Cache(replacement));

        Song saved = database.songDao().getSong("song-id");
        assertNotNull(saved);
        assertEquals("Replacement title", saved.title);
        assertTrue(database.cacheDao().isCached("song-id"));

        List<Song> cachedSongs = database.cacheDao().getSongs(Collections.singletonList("song-id"));
        assertEquals(1, cachedSongs.size());
        assertEquals("song-id", cachedSongs.get(0).id);
    }

    @Test
    public void queueSongsKeepPerQueueOrderAndCascadeWhenSongIsDeleted() {
        Song first = song("first-song", "First");
        Song second = song("second-song", "Second");
        database.songDao().insertSongs(Arrays.asList(first, second));

        database.queueSongDao().insertQueueSongs(Arrays.asList(
                new QueueSong(second.id, 1, 0),
                new QueueSong(first.id, 0, 0),
                new QueueSong(first.id, 0, 1)));

        List<QueueSong> playingQueue = database.queueSongDao().getQueueSongs(0);
        assertEquals(2, playingQueue.size());
        assertEquals("first-song", playingQueue.get(0).songId);
        assertEquals("second-song", playingQueue.get(1).songId);

        database.songDao().deleteSongs();

        assertTrue(database.queueSongDao().getQueueSongs(0).isEmpty());
        assertTrue(database.queueSongDao().getQueueSongs(1).isEmpty());
    }

    @Test
    public void queuePersistenceSkipsSongsWithoutIds() {
        Song valid = song("valid-song", "Valid");
        Song missingId = song("missing-id", "Missing id");
        missingId.id = null;
        database.songDao().insertSongs(Collections.singletonList(valid));

        database.queueSongDao().setQueue(Arrays.asList(valid, missingId), 0);

        List<QueueSong> playingQueue = database.queueSongDao().getQueueSongs(0);
        assertEquals(1, playingQueue.size());
        assertEquals("valid-song", playingQueue.get(0).songId);
        assertEquals(0, playingQueue.get(0).index);
    }

    private static Song song(String id, String title) {
        Song song = new Song();
        song.id = id;
        song.title = title;
        song.albumName = "Album";
        song.artistName = "Artist";
        song.container = "mp3";
        song.codec = "mp3";
        song.cache = true;
        return song;
    }
}
