package club.thatpetbff.gramophone.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import club.thatpetbff.gramophone.activities.MainActivity;
import club.thatpetbff.gramophone.activities.SearchActivity;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.model.Song;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SearchPlaybackCrashRegressionTest {
    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    @Test
    public void searchLaunchDoesNotCrashWhenSongIsAlreadyQueued() throws Exception {
        try (ActivityScenario<MainActivity> mainScenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue("music service connects", waitForMusicService(mainScenario));

            mainScenario.onActivity(activity ->
                    MusicPlayerRemote.openQueue(Collections.singletonList(worthSong()), 0, false));

            assertNotNull("current song is seeded", MusicPlayerRemote.getCurrentSong());
            assertEquals("Worth", MusicPlayerRemote.getCurrentSong().title);

            try (ActivityScenario<SearchActivity> searchScenario = ActivityScenario.launch(SearchActivity.class)) {
                searchScenario.moveToState(Lifecycle.State.RESUMED);
                searchScenario.onActivity(activity ->
                        assertEquals("SearchActivity", activity.getClass().getSimpleName()));
            }
        } finally {
            MusicPlayerRemote.pauseSong();
        }
    }

    private static boolean waitForMusicService(ActivityScenario<MainActivity> scenario) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;

        while (System.currentTimeMillis() < deadline) {
            AtomicBoolean connected = new AtomicBoolean(false);
            scenario.onActivity(activity -> connected.set(MusicPlayerRemote.musicService != null));
            if (connected.get()) {
                return true;
            }
            Thread.sleep(250);
        }

        return false;
    }

    private static Song worthSong() {
        Song song = new Song();
        song.title = "Worth";
        song.artistName = "QA Artist";
        song.albumName = "QA Album";
        song.duration = 180_000;
        song.container = "mp3";
        song.codec = "mp3";
        return song;
    }
}
