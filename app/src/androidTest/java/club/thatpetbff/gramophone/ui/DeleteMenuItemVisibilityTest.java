package club.thatpetbff.gramophone.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingRootException;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.activities.MainActivity;
import club.thatpetbff.gramophone.activities.base.AbsMusicPanelActivity;
import club.thatpetbff.gramophone.fragments.player.NowPlayingScreen;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.util.PreferenceUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R1: the player overflow shows "Delete from library" iff a server-backed song is
 * current, and hides it when no song is current. The item id is looked up by name at
 * runtime so this test compiles (and fails with a named reason) before the menu entry
 * exists.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class DeleteMenuItemVisibilityTest {
    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    @Test
    public void deleteItemShownWhenServerBackedSongIsCurrent() throws Exception {
        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);
        AppSessionGuard sessionGuard = new AppSessionGuard(null);
        List<Song> songs = RealSongTestData.loadPlayableSongs(1);
        String firstTitle = RealSongTestData.title(songs.get(0));

        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        try {
            MusicPlayerRemote.ServiceToken token = bind(scenario);
            tokenRef.set(token);
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();

            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(songs, 0, true);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            waitForCurrentSong(firstTitle);
            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            Thread.sleep(1_500);
            openPlayerOverflow();

            int deleteItemId = deleteMenuItemId();
            assertTrue("Player overflow must expose action_delete_from_library (R1)",
                    deleteItemId != 0);
            onView(allOf(withId(deleteItemId), isDisplayed()))
                    .check(matches(isDisplayed()));
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
            sessionGuard.close();
        }
    }

    @Test
    public void deleteItemHiddenWhenNoSongIsCurrent() throws Exception {
        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);
        AppSessionGuard sessionGuard = new AppSessionGuard(null);

        // First pass: make the queue empty so the relaunched player inflates with no current song.
        ActivityScenario<MainActivity> warmup = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> warmupTokenRef = new AtomicReference<>();
        try {
            warmupTokenRef.set(bind(warmup));
            dismissBatteryDialogIfPresent();
            warmup.onActivity(activity -> {
                MusicPlayerRemote.clearQueue();
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_000);
        } finally {
            warmup.onActivity(activity ->
                    MusicPlayerRemote.unbindFromService(warmupTokenRef.get()));
            warmup.close();
        }

        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        try {
            tokenRef.set(bind(scenario));
            dismissBatteryDialogIfPresent();
            assertTrue("Warm-up must leave no current song for the hidden-case leg",
                    MusicPlayerRemote.getCurrentSong() == null);

            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            Thread.sleep(1_500);
            openPlayerOverflow();

            int deleteItemId = deleteMenuItemId();
            assertTrue("Player overflow must expose action_delete_from_library (R1)",
                    deleteItemId != 0);
            onView(allOf(withId(deleteItemId), isDisplayed())).check(doesNotExist());
        } finally {
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
            sessionGuard.close();
        }
    }

    private static int deleteMenuItemId() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getResources()
                .getIdentifier("action_delete_from_library", "id",
                        InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
    }

    private static void openPlayerOverflow() {
        onView(allOf(withContentDescription("More options"),
                isDescendantOfA(withId(R.id.player_toolbar)))).perform(click());
    }

    private static MusicPlayerRemote.ServiceToken bind(ActivityScenario<MainActivity> scenario)
            throws InterruptedException {
        CountDownLatch serviceConnected = new CountDownLatch(1);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        scenario.onActivity(activity -> tokenRef.set(MusicPlayerRemote.bindToService(activity,
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        serviceConnected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                    }
                })));
        assertTrue("Timed out waiting for music service",
                serviceConnected.await(15_000, TimeUnit.MILLISECONDS));
        return tokenRef.get();
    }

    private static void waitForCurrentSong(String expectedTitle) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        do {
            Song current = MusicPlayerRemote.getCurrentSong();
            if (current != null && expectedTitle.equals(RealSongTestData.title(current))) {
                return;
            }
            Thread.sleep(250);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Timed out waiting for current song " + expectedTitle);
    }

    private static void dismissBatteryDialogIfPresent() throws InterruptedException {
        Thread.sleep(2_500);
        try {
            onView(withText(R.string.ignore)).inRoot(isDialog()).perform(click());
        } catch (NoMatchingViewException | NoMatchingRootException ignored) {
        }
    }
}
