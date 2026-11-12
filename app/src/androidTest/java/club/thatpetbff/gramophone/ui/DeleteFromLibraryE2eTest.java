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
import static org.junit.Assume.assumeTrue;

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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The six E2E cases of .tests.md, run inside the P0→P8 backup/restore protocol by the
 * QA driver (tools/qa-delete-cycle.sh wraps this class; the server is the second
 * oracle). NOTHING here is destructive by itself except the confirmed-delete cases,
 * and those only run when the driver passes the sacrificial item id as instrumentation
 * argument at run time — ids, servers and tokens are never committed (they arrive via
 * -e args from the operator's runtime procedure only).
 *
 * Instrumentation args consumed (all optional; missing ⇒ case skipped, never faked):
 *   qa_server  base URL of the QA server
 *   qa_token   auth token for the server oracle
 *   qa_user    user id for the server oracle
 *   qa_item    server item id of the nominated sacrificial track
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class DeleteFromLibraryE2eTest {
    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    /** E2E-4 (R2): Cancel leaves the item on the server and issues no DELETE. */
    @Test
    public void e2e4_cancelNoop() throws Exception {
        String server = arg("qa_server");
        String token = arg("qa_token");
        String user = arg("qa_user");
        String itemId = arg("qa_item");
        assumeTrue("E2E-4 needs driver args (qa_server/qa_token/qa_user/qa_item)",
                server != null && token != null && itemId != null);

        int statusBefore = serverItemStatus(server, token, user, itemId);
        assumeTrue("E2E-4 guard: sacrificial item must exist before the run (P0)",
                statusBefore == 200);

        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);
        Song sacrificial = findSong(itemId);
        assumeTrue("E2E-4 needs the sacrificial item synced in the app library", sacrificial != null);

        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        try {
            MusicPlayerRemote.ServiceToken token2 = bind(scenario);
            tokenRef.set(token2);
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();
            List<Song> queue = Collections.singletonList(sacrificial);
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(queue, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);

            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            Thread.sleep(1_000);
            openPlayerOverflowAndTapDelete();

            onView(withText(R.string.delete_from_library_body)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.delete_from_library_cancel)).inRoot(isDialog()).perform(click());
            Thread.sleep(1_000);

            // Server oracle: the item is still there; the request-log leg (no DELETE
            // line for this id) is asserted by the driver when logging is enabled —
            // .tests.md L2: never fabricated here.
            int statusAfter = serverItemStatus(server, token, user, itemId);
            assertEquals("Cancel must leave the item on the server (E2E-4)", 200, statusAfter);
            assertEquals("Cancel must leave the queue untouched", 1,
                    MusicPlayerRemote.getPlayingQueue().size());
            assertNotNull(MusicPlayerRemote.getCurrentSong());
            assertEquals(itemId, MusicPlayerRemote.getCurrentSong().id);
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
        }
    }

    static void openPlayerOverflowAndTapDelete() {
        onView(allOf(withContentDescription("More options"),
                isDescendantOfA(withId(R.id.player_toolbar)))).perform(click());
        int deleteItemId = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getResources().getIdentifier("action_delete_from_library", "id",
                        InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
        assertTrue("Player overflow must expose action_delete_from_library (R1)",
                deleteItemId != 0);
        onView(allOf(withId(deleteItemId), isDisplayed())).perform(click());
    }

    static String arg(String key) {
        String value = InstrumentationRegistry.getArguments().getString(key);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** GET {server}/Items/{id} with the canonical header; returns the HTTP status. */
    static int serverItemStatus(String server, String token, String user, String itemId)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(server + "/Items/" + itemId).openConnection();
        connection.setRequestMethod("GET");
        String uid = user == null ? "qa" : user;
        connection.setRequestProperty("Authorization",
                "MediaBrowser Client=\"jellywing-qa\", Device=\"qa\", DeviceId=\"qa\", Token=\""
                        + token + "\"");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        int status = connection.getResponseCode();
        try (InputStream ignored = status >= 400 ? connection.getErrorStream()
                : connection.getInputStream()) {
            // drain
        } catch (Exception ignored) {
        }
        connection.disconnect();
        return status;
    }

    private static Song findSong(String id) {
        for (Song song : App.getDatabase().songDao().getSongs()) {
            if (id.equals(song.id)) {
                return song;
            }
        }
        return null;
    }

    static MusicPlayerRemote.ServiceToken bind(ActivityScenario<MainActivity> scenario)
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

    static void dismissBatteryDialogIfPresent() throws InterruptedException {
        Thread.sleep(2_500);
        try {
            onView(withText(R.string.ignore)).inRoot(isDialog()).perform(click());
        } catch (NoMatchingViewException | NoMatchingRootException ignored) {
        }
    }
}
