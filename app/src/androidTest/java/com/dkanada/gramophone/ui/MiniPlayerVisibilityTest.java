package com.dkanada.gramophone.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.MainActivity;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.model.User;
import com.dkanada.gramophone.util.PreferenceUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class MiniPlayerVisibilityTest {
    private static final String CLICKED_SONG_TITLE = "Mini Player Regression Track";

    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    @Test
    public void selectingSongShowsBottomMiniPlayerBar() throws Exception {
        seedFakeUser();

        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();

        try {
            CountDownLatch serviceConnected = new CountDownLatch(1);
            scenario.onActivity(activity -> tokenRef.set(MusicPlayerRemote.bindToService(activity, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    serviceConnected.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            })));

            if (!serviceConnected.await(15_000, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out waiting for music service");
            }

            dismissBatteryDialogIfPresent();

            scenario.onActivity(activity -> MusicPlayerRemote.openQueue(
                    Collections.singletonList(testSong()),
                    0,
                    true));

            waitForMiniPlayerTitle(CLICKED_SONG_TITLE);
            onView(withId(R.id.mini_player_title))
                    .check(matches(withText(CLICKED_SONG_TITLE)))
                    .check(matches(isDisplayed()));
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
        }
    }

    private static Song testSong() {
        Song song = new Song();
        song.id = "mini-player-regression-track";
        song.title = CLICKED_SONG_TITLE;
        song.albumName = "Regression Album";
        song.artistName = "Regression Artist";
        song.container = "mp3";
        song.codec = "mp3";
        song.duration = 60_000;
        song.bitRate = 128_000;
        return song;
    }

    private static void seedFakeUser() {
        User user = new User();
        user.id = "mini-player-test-user";
        user.name = "Mini Player Test User";
        user.server = "https://example.invalid";
        user.token = "mini-player-test-token";

        App.getDatabase().userDao().insertUser(user);
        PreferenceUtil.getInstance(App.getInstance()).setServer(user.server);
        PreferenceUtil.getInstance(App.getInstance()).setUser(user.id);
        App.getApiClient().ChangeServerLocation(user.server);
        App.getApiClient().SetAuthenticationInfo(user.token, user.id);
    }

    private static void dismissBatteryDialogIfPresent() throws InterruptedException {
        Thread.sleep(2_500);
        try {
            onView(withText(R.string.ignore))
                    .inRoot(isDialog())
                    .perform(click());
        } catch (NoMatchingViewException ignored) {
        }
    }

    private static void waitForMiniPlayerTitle(String expectedTitle) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        AssertionError lastFailure = null;

        do {
            try {
                onView(withId(R.id.mini_player_title))
                        .check(matches(withText(expectedTitle)))
                        .check(matches(isDisplayed()));
                assertNotNull(MusicPlayerRemote.getCurrentSong());
                return;
            } catch (AssertionError error) {
                lastFailure = error;
                Thread.sleep(250);
            }
        } while (System.currentTimeMillis() < deadline);

        throw new AssertionError("Timed out waiting for visible mini player title", lastFailure);
    }
}
