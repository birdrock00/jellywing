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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.WindowManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingRootException;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.Root;
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
import club.thatpetbff.gramophone.util.FavoritesStore;
import club.thatpetbff.gramophone.util.PreferenceUtil;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * E2E-1 (R3, R4, R9): the happy path, exercised twice — once as the only queued
     * entry, once from a playlist with a next track to advance to.
     *
     * <p>DESTRUCTIVE: this method deletes the sacrificial item from the server. It runs
     * ONLY inside the driver's P0→P8 cycle (tools/qa-delete-cycle.sh wraps this class);
     * the P5 restore is the operator's responsibility. Never run it standalone.
     *
     * <p>Leg order is mandatory (round-4 checklist B5): the lone-entry leg runs FIRST,
     * because removeFromQueueAndAdvance() with nothing left to advance to is the only
     * executed backstop for R3-B1 and R4's where-clause — a JVM test cannot cover it.
     *
     * <p>R-EV: there is deliberately no assumeTrue in this method. Every missing
     * precondition is a fail() naming what was missing, so an under-provisioned run is
     * RED rather than a green in which nothing executed.
     */
    @Test
    public void e2e1_happyPathCycle() throws Exception {
        String server = requireArg("qa_server");
        String token = requireArg("qa_token");
        String user = requireArg("qa_user");
        String itemId = requireArg("qa_item");

        assertEquals("E2E-1 precondition: the sacrificial item must exist on the server (P0)",
                200, serverItemStatus(server, token, user, itemId));
        Song sacrificial = findSong(itemId);
        assertNotNull("E2E-1 needs the sacrificial item synced in the app library", sacrificial);
        Song nextTrack = findOtherSong(itemId);
        assertNotNull("E2E-1 leg 2 needs a second local track to advance to — the library has "
                + "only the sacrificial item, so 'the queue advanced' is unobservable", nextTrack);

        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        // R9 observation channel: notifyChanged() calls onFavoritesChanged() on every
        // live listener; the local library screens register here, so a delivered call is
        // the refresh the Favorites tab acts on.
        AtomicReference<Boolean> favoritesRefreshed = new AtomicReference<>(Boolean.FALSE);
        FavoritesStore.Listener listener = () -> favoritesRefreshed.set(Boolean.TRUE);
        long favoritesVersionBefore = FavoritesStore.getVersion();
        FavoritesStore.addListener(listener);
        try {
            MusicPlayerRemote.ServiceToken bound = bind(scenario);
            tokenRef.set(bound);
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();

            // ---- leg 1: the deleted song is the ONLY queued entry (R3-B1, R4) ----
            List<Song> lone = Collections.singletonList(sacrificial);
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(lone, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);
            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            Thread.sleep(1_000);
            openPlayerOverflowAndTapDelete();
            onView(withText(R.string.delete_from_library_confirm)).inRoot(isDialog())
                    .perform(click());
            Thread.sleep(2_500);

            assertFalse("R4: a deleted lone item must not stay in the queue",
                    queueIds().contains(itemId));
            Song afterLone = MusicPlayerRemote.getCurrentSong();
            assertTrue("R4: after deleting the only queued song, no surface may present the "
                    + "dead item (current song is " + (afterLone == null ? "null" : afterLone.id)
                    + ")", afterLone == null || !itemId.equals(afterLone.id));
            assertFalse("R3-B1: playback must stop cleanly rather than keep running a deleted "
                    + "lone item", MusicPlayerRemote.isPlaying());
            assertEquals("server oracle: the lone delete really reached the server", 404,
                    serverItemStatus(server, token, user, itemId));

            // ---- leg 2: delete from a playlist (R3 advance, R9 favorites refresh) ----
            List<Song> playlist = Arrays.asList(sacrificial, nextTrack);
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(playlist, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);
            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            Thread.sleep(1_000);
            favoritesRefreshed.set(Boolean.FALSE); // arm the R9 leg for THIS act only
            openPlayerOverflowAndTapDelete();
            onView(withText(R.string.delete_from_library_confirm)).inRoot(isDialog())
                    .perform(click());

            assertToastShownEventually(R.string.delete_from_library_success, 15_000);

            // R9: the notifyChanged() call on the SUCCESS path must reach listeners.
            for (int i = 0; i < 60 && !Boolean.TRUE.equals(favoritesRefreshed.get()); i++) {
                Thread.sleep(250);
            }
            assertTrue("R9: FavoritesStore.notifyChanged() must reach listeners after a "
                    + "successful delete (this is the Favorites-tab refresh); it did not, so "
                    + "the R9 UI leg is not proven", Boolean.TRUE.equals(favoritesRefreshed.get()));
            assertTrue("R9: the FavoritesStore version must advance on a successful delete",
                    FavoritesStore.getVersion() > favoritesVersionBefore);

            // The now-playing assertion is taken at the model layer on purpose: the same
            // title string renders in the now-playing panel AND the queue list, so a
            // withText(title) Espresso check is ambiguous. getCurrentSong() is the sole
            // source the now-playing panel renders from (fed by onPlayMetadataChanged()).
            assertFalse("R3: the deleted track must be gone from the playlist queue",
                    queueIds().contains(itemId));
            Song advancedTo = MusicPlayerRemote.getCurrentSong();
            assertNotNull("R3: the queue must advance to the next track", advancedTo);
            assertEquals("R3: the now-playing surface must show the next track, not the "
                    + "deleted one", nextTrack.id, advancedTo.id);
            assertEquals("server oracle: the playlist delete reached the server", 404,
                    serverItemStatus(server, token, user, itemId));
        } finally {
            FavoritesStore.removeListener(listener);
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
        }
    }

    /**
     * E2E-3 (R6): deleting the same id twice inside one cycle. The second act must be
     * reported as ALREADY_GONE (_gone) and must NOT claim a fresh success (_success).
     *
     * <p>Destructive in the same way and to the same degree as E2E-1 (it deletes the
     * sacrificial item once): driver P0→P8 cycle only, never standalone.
     *
     * <p>R-EV: no assumeTrue. A missing arg, a missing local copy or a first delete that
     * did not take effect is a fail(), never a skip.
     */
    @Test
    public void e2e3_alreadyGoneSecondDelete() throws Exception {
        String server = requireArg("qa_server");
        String token = requireArg("qa_token");
        String user = requireArg("qa_user");
        String itemId = requireArg("qa_item");

        assertEquals("E2E-3 precondition: the sacrificial item must exist on the server (P0)",
                200, serverItemStatus(server, token, user, itemId));
        Song sacrificial = findSong(itemId);
        assertNotNull("E2E-3 needs the sacrificial item synced in the app library", sacrificial);
        Song nextTrack = findOtherSong(itemId);
        assertNotNull("E2E-3 needs a second local track so the dead item can be re-queued "
                + "beside a live one", nextTrack);

        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        try {
            MusicPlayerRemote.ServiceToken bound = bind(scenario);
            tokenRef.set(bound);
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();

            // ---- act 1: a real delete, so the id is genuinely dead ----------------
            List<Song> queue = Arrays.asList(sacrificial, nextTrack);
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(queue, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);
            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            Thread.sleep(1_000);
            openPlayerOverflowAndTapDelete();
            onView(withText(R.string.delete_from_library_confirm)).inRoot(isDialog())
                    .perform(click());
            assertToastShownEventually(R.string.delete_from_library_success, 15_000);
            assertEquals("E2E-3 precondition: act 1 must have deleted the item server-side",
                    404, serverItemStatus(server, token, user, itemId));

            // Let act 1's _success toast expire so that the assertToastAbsent below is a
            // statement about act 2 only. If a device somehow held that toast longer than
            // this wait, the assertion FAILS (a false red) — it can never hide a wrong
            // toast behind a stale one.
            Thread.sleep(5_000);

            // ---- act 2: same dead id, re-queued from the object we still hold ----
            List<Song> again = Arrays.asList(sacrificial, nextTrack);
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(again, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);
            scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
            Thread.sleep(1_000);
            openPlayerOverflowAndTapDelete();
            onView(withText(R.string.delete_from_library_confirm)).inRoot(isDialog())
                    .perform(click());

            assertToastShownEventually(R.string.delete_from_library_gone, 15_000);
            assertToastAbsent(R.string.delete_from_library_success,
                    "R6: the second delete of a dead id must not be reported as a fresh "
                            + "success — nothing may be falsely claimed as deleted");

            assertFalse("R6: the already-gone item must stay out of the queue",
                    queueIds().contains(itemId));
            Song current = MusicPlayerRemote.getCurrentSong();
            assertTrue("R6: no surface may present the dead item", current == null
                    || !itemId.equals(current.id));
            assertEquals("server oracle: the id is still gone after the second attempt", 404,
                    serverItemStatus(server, token, user, itemId));
            // R10/R11 (cache purge + guard clear) are success-only by design and are
            // deliberately NOT asserted here; ALREADY_GONE purges nothing.
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
        }
    }

    /**
     * An instrumentation arg that this destructive case cannot run without. R-EV: absent
     * ⇒ fail naming the arg, never assumeTrue — a skipped leg prints as SUCCESS in both
     * am instrument and Gradle, which is exactly the false green this lane keeps hitting.
     */
    static String requireArg(String key) {
        String value = arg(key);
        if (value == null) {
            fail("Missing required instrumentation arg -e " + key + " (supplied by the QA "
                    + "driver); refusing to report a green run in which nothing executed");
        }
        return value;
    }

    /** Any local track other than {@code id} — the "next track" of the advance legs. */
    private static Song findOtherSong(String id) {
        for (Song song : App.getDatabase().songDao().getSongs()) {
            if (!id.equals(song.id)) {
                return song;
            }
        }
        return null;
    }

    static List<String> queueIds() {
        List<String> ids = new ArrayList<>();
        for (Song song : MusicPlayerRemote.getPlayingQueue()) {
            ids.add(song.id);
        }
        return ids;
    }

    /**
     * A toast lives in its own window (type TYPE_TOAST), not in the activity's view
     * hierarchy, so Espresso needs this root to see one at all.
     *
     * <p>How this distinguishes _gone from _success: the matcher selects the toast
     * <em>window</em>; {@code withText(resolved string)} then selects the view whose text
     * equals one specific string. {@code delete_from_library_success} ("Removed from
     * library") and {@code delete_from_library_gone} ("This song is no longer on the
     * server") are distinct resources with distinct text, so if handleOutcome toasted
     * _success where _gone was due, {@code assertToastShownEventually(_gone)} times out
     * RED and {@code assertToastAbsent(_success)} fails RED — both directions fail, and
     * neither can be satisfied by the other string.
     *
     * <p>Honest runtime bound (not yet executed): since Android 11 (API 30) toast windows
     * are added by system_server, so on an API 30+ runner Espresso may see no toast root
     * at all. That surfaces as a loud failure of the POSITIVE assertion, never as a
     * silent pass; the remedy then is a log/state-based leg, not an assumeTrue.
     */
    static TypeSafeMatcher<Root> isToastRoot() {
        return new TypeSafeMatcher<Root>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("a toast window root");
            }

            @Override
            protected boolean matchesSafely(Root root) {
                try {
                    return root.getWindowLayoutParams().get().type
                            == WindowManager.LayoutParams.TYPE_TOAST;
                } catch (Exception ignored) {
                    return false;
                }
            }
        };
    }

    private static boolean toastShowing(String text) {
        try {
            onView(withText(text)).inRoot(isToastRoot()).check(matches(isDisplayed()));
            return true;
        } catch (NoMatchingViewException | NoMatchingRootException | AssertionError ignored) {
            return false;
        }
    }

    /** Polls for a toast carrying {@code resId}; times out into a fail(), never a skip. */
    static void assertToastShownEventually(int resId, long timeoutMillis) throws InterruptedException {
        String text = App.getInstance().getString(resId);
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (toastShowing(text)) {
                return;
            }
            Thread.sleep(250);
        }
        fail("Expected a toast showing \"" + text + "\" (" + resId + ") within "
                + timeoutMillis + " ms and saw none. Either the toast was never issued, the "
                + "wrong string was issued, or this runner's Android version hides toast "
                + "windows from Espresso (API 30+) — see isToastRoot().");
    }

    static void assertToastAbsent(int resId, String message) {
        String text = App.getInstance().getString(resId);
        if (toastShowing(text)) {
            fail(message + " (but a toast showing \"" + text + "\" (" + resId + ") is on screen)");
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
