package club.thatpetbff.gramophone.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.activities.MainActivity;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.model.Song;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class MiniPlayerVisibilityTest {
    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    @Test
    public void selectingRealSongShowsBottomMiniPlayerBar() throws Exception {
        AppSessionGuard sessionGuard = new AppSessionGuard(null);
        List<Song> songs = RealSongTestData.loadPlayableSongs(1);
        String clickedSongTitle = RealSongTestData.title(songs.get(0));

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

            assertTrue("Timed out waiting for music service", serviceConnected.await(15_000, TimeUnit.MILLISECONDS));
            dismissBatteryDialogIfPresent();

            scenario.onActivity(activity -> MusicPlayerRemote.openQueue(songs, 0, true));

            waitForMiniPlayerTitle(clickedSongTitle);
            onView(withId(R.id.mini_player_title))
                    .check(matches(withText(clickedSongTitle)))
                    .check(matches(isDisplayed()));
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
            sessionGuard.close();
        }
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
