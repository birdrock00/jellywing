package com.dkanada.gramophone.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.doubleClick;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.IBinder;
import android.widget.ImageView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.NoMatchingRootException;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;
import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.MainActivity;
import com.dkanada.gramophone.activities.base.AbsMusicPanelActivity;
import com.dkanada.gramophone.fragments.player.NowPlayingScreen;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.PreferenceUtil;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FullPlayerRealSongsTest {
    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    @Test
    public void fullPlayerShowsCurrentSongTitleAndRealUpNextSongs() throws Exception {
        AppSessionGuard sessionGuard = new AppSessionGuard(null);
        List<Song> songs = RealSongTestData.loadPlayableSongs(3);
        String firstTitle = RealSongTestData.title(songs.get(0));
        String secondTitle = RealSongTestData.title(songs.get(1));
        String thirdTitle = RealSongTestData.title(songs.get(2));
        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);

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
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();

            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(songs, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            waitForCurrentSong(firstTitle);
            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());

            waitForFullPlayerSongTitle(firstTitle);
            waitForCurrentCoverFragment(scenario, firstTitle);
            assertUpNextSongVisible(secondTitle);
            assertUpNextSongVisible(thirdTitle);
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
            sessionGuard.close();
        }
    }

    @Test
    public void selectingRealUpNextSongChangesCurrentSongAndSurvivesDoubleClick() throws Exception {
        AppSessionGuard sessionGuard = new AppSessionGuard(null);
        List<Song> songs = RealSongTestData.loadPlayableSongs(4);
        String firstTitle = RealSongTestData.title(songs.get(0));
        String secondTitle = RealSongTestData.title(songs.get(1));
        String thirdTitle = RealSongTestData.title(songs.get(2));
        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);

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
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();

            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(songs, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            waitForCurrentSong(firstTitle);
            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            waitForFullPlayerSongTitle(firstTitle);
            waitForCurrentCoverFragment(scenario, firstTitle);
            long firstArtworkSignature = waitForCurrentArtworkSignature(scenario);
            assertUpNextSongVisible(secondTitle);

            performOnUpNextSong(secondTitle, click());
            waitForCurrentSong(secondTitle);
            waitForFullPlayerSongTitle(secondTitle);
            waitForCurrentCoverFragment(scenario, secondTitle);
            long secondArtworkSignature = waitForCurrentArtworkSignatureDifferentFrom(scenario, firstArtworkSignature);
            assertUpNextSongVisible(thirdTitle);

            Thread.sleep(2_100);
            performOnUpNextSong(thirdTitle, doubleClick());
            waitForCurrentSong(thirdTitle);
            waitForFullPlayerSongTitle(thirdTitle);
            waitForCurrentCoverFragment(scenario, thirdTitle);
            waitForCurrentArtworkSignatureDifferentFrom(scenario, secondArtworkSignature);
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
            sessionGuard.close();
        }
    }

    private static void waitForFullPlayerSongTitle(String expectedTitle) throws Exception {
        waitUntil("full player song title " + expectedTitle, () -> {
            onView(allOf(withId(R.id.player_song_title), isDescendantOfA(withId(R.id.player_song_text_container))))
                    .check(matches(withText(expectedTitle)))
                    .check(matches(isDisplayed()));
            return true;
        });
    }

    private static void waitForCurrentCoverFragment(ActivityScenario<MainActivity> scenario, String expectedTitle) throws Exception {
        waitUntil("current cover artwork " + expectedTitle, () -> {
            AtomicReference<Boolean> matches = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                Song currentSong = MusicPlayerRemote.getCurrentSong();
                if (currentSong == null
                        || !expectedTitle.equals(RealSongTestData.title(currentSong))) {
                    matches.set(false);
                    return;
                }

                Object visibleArtworkSongId = activity.findViewById(R.id.player_current_image)
                        .getTag(R.id.current_album_artwork_song_id);
                Object visibleArtworkId = activity.findViewById(R.id.player_current_image)
                        .getTag(R.id.current_album_artwork_id);
                matches.set(currentSong.id.equals(visibleArtworkSongId)
                        && currentSong.getArtworkItemId().equals(visibleArtworkId));
            });

            return Boolean.TRUE.equals(matches.get());
        });
    }

    private static long waitForCurrentArtworkSignature(ActivityScenario<MainActivity> scenario) throws Exception {
        AtomicReference<Long> signatureRef = new AtomicReference<>(0L);
        waitUntil("visible artwork pixels", () -> {
            scenario.onActivity(activity -> signatureRef.set(captureArtworkSignature(
                    activity.findViewById(R.id.player_current_image))));
            return signatureRef.get() != 0L;
        });
        return signatureRef.get();
    }

    private static long waitForCurrentArtworkSignatureDifferentFrom(ActivityScenario<MainActivity> scenario, long previousSignature) throws Exception {
        AtomicReference<Long> signatureRef = new AtomicReference<>(0L);
        waitUntil("visible artwork pixels to change", () -> {
            scenario.onActivity(activity -> signatureRef.set(captureArtworkSignature(
                    activity.findViewById(R.id.player_current_image))));
            return signatureRef.get() != 0L && signatureRef.get() != previousSignature;
        });
        assertNotEquals("Visible current artwork pixels did not change", previousSignature, signatureRef.get().longValue());
        return signatureRef.get();
    }

    private static long captureArtworkSignature(ImageView imageView) {
        if (imageView == null || imageView.getWidth() <= 0 || imageView.getHeight() <= 0) {
            return 0L;
        }

        Bitmap bitmap = Bitmap.createBitmap(imageView.getWidth(), imageView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        imageView.draw(canvas);

        long signature = 1125899906842597L;
        int xStep = Math.max(1, bitmap.getWidth() / 24);
        int yStep = Math.max(1, bitmap.getHeight() / 24);
        for (int y = 0; y < bitmap.getHeight(); y += yStep) {
            for (int x = 0; x < bitmap.getWidth(); x += xStep) {
                signature = (signature * 31) + bitmap.getPixel(x, y);
            }
        }
        bitmap.recycle();
        return signature;
    }

    private static void performOnUpNextSong(String title, ViewAction action) {
        onView(withId(R.id.player_recycler_view))
                .perform(actionOnItem(hasDescendant(withText(title)), action));
    }

    private static void assertUpNextSongVisible(String title) throws Exception {
        waitUntil("up next song " + title, () -> {
            onView(allOf(withText(title), isDescendantOfA(withId(R.id.player_recycler_view))))
                    .check(matches(isDisplayed()));
            return true;
        });
    }

    private static void dismissBatteryDialogIfPresent() throws InterruptedException {
        Thread.sleep(2_500);
        try {
            onView(withText(R.string.ignore))
                    .inRoot(isDialog())
                    .perform(click());
        } catch (NoMatchingViewException | NoMatchingRootException ignored) {
        }
    }

    private static void waitForCurrentSong(String expectedTitle) throws Exception {
        waitUntil("current song " + expectedTitle, () -> {
            assertNotNull(MusicPlayerRemote.getCurrentSong());
            return expectedTitle.equals(RealSongTestData.title(MusicPlayerRemote.getCurrentSong()));
        });
    }

    private static void waitUntil(String description, Condition condition) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        Throwable lastFailure = null;

        do {
            try {
                if (condition.isSatisfied()) {
                    return;
                }
            } catch (Throwable error) {
                lastFailure = error;
            }

            Thread.sleep(250);
        } while (System.currentTimeMillis() < deadline);

        throw new AssertionError("Timed out waiting for " + description, lastFailure);
    }

    private interface Condition {
        boolean isSatisfied() throws Exception;
    }
}
