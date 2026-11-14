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
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
 * R2 instrumented leg: selecting Delete-from-library opens the confirmation dialog
 * whose copy states the file leaves the server and every playlist holding it and that
 * this cannot be undone; Cancel leaves playback and the queue unchanged (the no-DELETE
 * leg rides the driver request log when enabled, per .tests.md limitation L2).
 *
 * EXECUTION: deferred to the QA stage (device + P0-P8 guard rails). The item id is
 * resolved by name at runtime so this source compiles independently of the menu edit.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class DeleteConfirmDialogTest {
    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    @Test
    public void selectDeleteOpensDestructiveConfirmDialogAndCancelIsNoop() throws Exception {
        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);
        AppSessionGuard sessionGuard = new AppSessionGuard(null);
        List<Song> songs = RealSongTestData.loadPlayableSongs(2);
        String firstTitle = RealSongTestData.title(songs.get(0));

        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        try {
            MusicPlayerRemote.ServiceToken token = bind(scenario);
            tokenRef.set(token);
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(songs, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);
            int queueSizeBefore = MusicPlayerRemote.getPlayingQueue().size();
            Song currentBefore = MusicPlayerRemote.getCurrentSong();
            assertNotNull(currentBefore);

            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            Thread.sleep(1_000);
            onView(allOf(withContentDescription("More options"),
                    isDescendantOfA(withId(R.id.player_toolbar)))).perform(click());

            int deleteItemId = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getResources().getIdentifier("action_delete_from_library", "id",
                            InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
            assertTrue("Player overflow must expose action_delete_from_library (R1)",
                    deleteItemId != 0);
            // Clicked by row content, not by id: see PlayerOverflow for why the id query cannot
            // reach a popup that lives in its own window.
            PlayerOverflow.clickItem(InstrumentationRegistry.getInstrumentation().getTargetContext()
                            .getString(R.string.action_delete_from_library),
                    PlayerOverflow.activityDecor(scenario));

            // R2 destructive copy must be stated before any request could be issued.
            onView(withText(R.string.delete_from_library_body)).inRoot(isDialog())
                    .check(matches(isDisplayed()));

            onView(withText(R.string.delete_from_library_cancel)).inRoot(isDialog()).perform(click());
            Thread.sleep(1_000);

            // Cancel is a no-op: playback, queue and current item unchanged. The
            // "no DELETE reached the server" leg runs in the driver (request log, L2).
            assertEquals("Queue must be unchanged after cancel",
                    queueSizeBefore, MusicPlayerRemote.getPlayingQueue().size());
            assertEquals("Current song must be unchanged after cancel",
                    currentBefore.id, MusicPlayerRemote.getCurrentSong().id);
            assertEquals(firstTitle, RealSongTestData.title(MusicPlayerRemote.getCurrentSong()));
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
            sessionGuard.close();
        }
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

    private static void dismissBatteryDialogIfPresent() throws InterruptedException {
        Thread.sleep(2_500);
        try {
            onView(withText(R.string.ignore)).inRoot(isDialog()).perform(click());
        } catch (NoMatchingViewException | NoMatchingRootException ignored) {
        }
    }
}
