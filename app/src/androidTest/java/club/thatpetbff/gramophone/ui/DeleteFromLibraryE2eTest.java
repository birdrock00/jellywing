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
import android.content.Context;
import android.util.Log;
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
import club.thatpetbff.gramophone.service.MusicService;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.util.FavoritesStore;
import club.thatpetbff.gramophone.util.MusicUtil;
import club.thatpetbff.gramophone.util.NavigationUtil;
import club.thatpetbff.gramophone.util.PreferenceUtil;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * E2E-2 (R4, R9, L4): after a successful delete the dead id must be gone from the
     * server-side playlist that held it, and gone from a *re-read* of that playlist —
     * Jellyfin playlists are backed by an .m3u8 file, so a stale listing is the failure
     * mode (limitation L4), not the happy path.
     *
     * <p><b>B6 — the vacuity rule this method exists to enforce.</b> "The playlist no
     * longer lists the dead id" is trivially true when the id was never in the listing, or
     * when the listing endpoint was wrong, or when no scan ever ran. So the POSITIVE half
     * runs first and is a hard fail: some server playlist must list the id BEFORE the
     * delete, and every membership read must be HTTP 200. A green here therefore means the
     * id was present, a scan completed, and the id stopped resolving.
     *
     * <p><b>Which half of the scan is proven where.</b> With {@code -e qa_scan delegated}
     * the driver (tools/qa-delete-cycle.sh step p3b_scan) owns {@code POST
     * /Library/Refresh} + poll + its own re-assertion; this method then proves the in-app
     * half (dead id out of the queue / off the now-playing surface) and re-reads the
     * membership until it is clean, failing if the effect never shows. Without that arg
     * this method triggers the scan itself over HTTP, and a non-2xx response is a fail()
     * naming the operator step — never a silently skipped scan.
     *
     * <p>Destructive exactly as E2E-1 is: driver P0→P8 cycle only, never standalone.
     * R-EV: no assumeTrue; every absent precondition is a fail() naming it.
     */
    @Test
    public void e2e2_playlistCleanAfterScan() throws Exception {
        String server = requireArg("qa_server");
        String token = requireArg("qa_token");
        String user = requireArg("qa_user");
        String itemId = requireArg("qa_item");
        String scanMode = arg("qa_scan");

        assertEquals("E2E-2 precondition: the sacrificial item must exist on the server (P0)",
                200, serverItemStatus(server, token, user, itemId));
        Song sacrificial = findSong(itemId);
        assertNotNull("E2E-2 needs the sacrificial item synced in the app library", sacrificial);
        Song nextTrack = findOtherSong(itemId);
        assertNotNull("E2E-2 needs a second local track so the queue can advance off the dead "
                + "item (R4)", nextTrack);

        // ---- positive half of B6: the id IS listed somewhere, before anything happens --
        List<String> holders = new ArrayList<>();
        for (String playlistId : serverPlaylistIds(server, token, user)) {
            if (playlistMembership(server, token, user, playlistId, null).contains(itemId)) {
                holders.add(playlistId);
            }
        }
        if (holders.isEmpty()) {
            fail("E2E-2 precondition: no server playlist lists the sacrificial id " + itemId
                    + ", so asserting afterwards that 'the playlist no longer lists it' would "
                    + "pass whether or not the purge and the scan work. Nominate a track that "
                    + "lives in a QA playlist.");
        }

        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        try {
            MusicPlayerRemote.ServiceToken bound = bind(scenario);
            tokenRef.set(bound);
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();

            List<Song> queue = Arrays.asList(sacrificial, nextTrack);
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(queue, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);
            tapDeleteOnCurrentTrack(scenario);
            assertToastShownEventually(R.string.delete_from_library_success, 15_000);

            // in-app half (R4): the dead id is out of the queue and off every surface.
            assertFalse("R4: the deleted track must leave the queue",
                    queueIds().contains(itemId));
            Song current = MusicPlayerRemote.getCurrentSong();
            assertTrue("R4: no surface may present the dead item", current == null
                    || !itemId.equals(current.id));
            assertEquals("server oracle: the delete reached the server", 404,
                    serverItemStatus(server, token, user, itemId));

            // ---- the scan itself: triggered here, or delegated to the driver ----------
            if (!"delegated".equalsIgnoreCase(scanMode)) {
                HttpResp refresh = authorized(server, "POST", "/Library/Refresh", token, null);
                if (refresh.status < 200 || refresh.status >= 300) {
                    fail("E2E-2 needs a library scan and could not trigger one: POST "
                            + "/Library/Refresh returned HTTP " + refresh.status + ". Grant the "
                            + "QA token that privilege, or run inside the driver with -e "
                            + "qa_scan delegated (step p3b_scan owns POST + poll). A green here "
                            + "with no scan is the false green L4 warns about.");
                }
            }

            // ---- re-read: the dead id must stop resolving from the playlist ------------
            long deadline = System.currentTimeMillis() + 180_000;
            String lastBody = "";
            while (true) {
                boolean clean = true;
                for (String playlistId : holders) {
                    // cacheBuster defeats any intermediary/HTTP cache: this must be a fresh
                    // read of the .m3u8-backed listing, not a remembered one (L4).
                    lastBody = playlistMembership(server, token, user, playlistId,
                            Long.toString(System.nanoTime()));
                    if (lastBody.contains(itemId)) {
                        clean = false;
                    }
                }
                if (clean) {
                    break;
                }
                if (System.currentTimeMillis() > deadline) {
                    fail("R4/L4: " + ("delegated".equalsIgnoreCase(scanMode)
                            ? "after 180 s of polling (the driver's p3b_scan owns the POST)"
                            : "a scan was POSTed here but after 180 s of polling")
                            + " the playlist(s) " + holders + " still list the deleted id "
                            + itemId + ". Either the library scan never completed or the "
                            + "playlist purge did not run — the stale listing is exactly what "
                            + "this case exists to catch.");
                }
                Thread.sleep(5_000);
            }
            // Honest bound on this leg: it proves the dead id no longer resolves from a
            // fresh membership read. It does not read the .m3u8 file on the server, and in
            // the delegated mode it does not prove who ran the scan — the driver's p3b_scan
            // assertion is what pins that half.
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
        }
    }

    /**
     * E2E-5 (R10): after a successful delete-and-advance, no {@code
     * Sessions/Playing/Progress} report may name the dead id, and the server-report
     * suppression guard must stop naming it (otherwise the next process start re-suppresses
     * reports for an item the library no longer has).
     *
     * <p><b>Leg 1 (request-log, L2 — the only skippable leg in this feature):</b> for 30 s
     * after the delete, the captured server request log must contain no progress line naming
     * the dead id. That oracle exists only when the driver passes {@code -e qa_reqlog <url>};
     * without it this leg prints exactly {@code SKIPPED (request-log oracle unavailable, L2)}
     * and nothing more — never a fabricated pass. With the arg present, a non-200 capture or
     * a capture holding no {@code Sessions/Playing} traffic at all is a <b>fail</b>, because
     * an empty capture cannot prove absence.
     *
     * <p><b>Leg 2 (guard, always asserted):</b> this method ARMS the guard for the dead id
     * itself ({@code favorites_guard}/{@code suppress_report_item_id}) and proves it armed,
     * so the post-delete assertion cannot be satisfied by a guard that was never set. Both
     * the in-process lookup ({@link MusicService#isServerReportSuppressed(String)}, what the
     * service actually consults before reporting) and the ON-DISK pref are checked. Bound,
     * stated plainly: <b>no process restart is performed here</b> — a force-stop would need
     * the driver. The disk read is the restart-equivalent, because after a restart
     * {@code isServerReportSuppressed} restores from exactly that value; the in-process read
     * alone would not be, since it only consults disk while its cached field is null.
     *
     * <p>Destructive as E2E-1; driver cycle only. R-EV: no assumeTrue anywhere.
     */
    @Test
    public void e2e5_noSessionReportsForDeadId() throws Exception {
        String server = requireArg("qa_server");
        String token = requireArg("qa_token");
        String user = requireArg("qa_user");
        String itemId = requireArg("qa_item");
        String reqLogUrl = arg("qa_reqlog");

        assertEquals("E2E-5 precondition: the sacrificial item must exist on the server (P0)",
                200, serverItemStatus(server, token, user, itemId));
        Song sacrificial = findSong(itemId);
        assertNotNull("E2E-5 needs the sacrificial item synced in the app library", sacrificial);
        Song nextTrack = findOtherSong(itemId);
        assertNotNull("E2E-5 needs a second local track: the advance is what a session report "
                + "would name instead of the dead id", nextTrack);

        // Deterministic starting state, then ARM the R10 guard for this id.
        MusicService.clearServerReportSuppression();
        App.getInstance().getSharedPreferences(MusicService.GUARD_PREFS, Context.MODE_PRIVATE)
                .edit().putString(MusicService.GUARD_KEY, itemId).apply();
        assertTrue("E2E-5 fixture: the R10 guard must name the dead id BEFORE the delete. If "
                + "this is false the arming did not take effect (or a stale in-memory guard "
                + "names another id) and 'the guard no longer names the dead id' below would "
                + "pass without the delete doing anything.",
                MusicService.isServerReportSuppressed(itemId));

        PreferenceUtil.getInstance(App.getInstance()).setNowPlayingScreen(NowPlayingScreen.CARD);
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        try {
            MusicPlayerRemote.ServiceToken bound = bind(scenario);
            tokenRef.set(bound);
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();

            List<Song> queue = Arrays.asList(sacrificial, nextTrack);
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(queue, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);
            tapDeleteOnCurrentTrack(scenario);
            assertToastShownEventually(R.string.delete_from_library_success, 15_000);

            // The advance is asserted because it is what makes "a report would name the NEW
            // item" meaningful; R3/R4 side of the same act.
            Song current = MusicPlayerRemote.getCurrentSong();
            assertNotNull("R3/R10: the queue must advance after the delete", current);
            assertEquals("R3/R10: the current item must be the next track, not the deleted id",
                    nextTrack.id, current.id);
            assertFalse("R10: the guard must stop naming the deleted id in-process — a surviving "
                    + "guard keeps suppressing Sessions/Playing reports for an id the library no "
                    + "longer has", MusicService.isServerReportSuppressed(itemId));
            String guardOnDisk = App.getInstance()
                    .getSharedPreferences(MusicService.GUARD_PREFS, Context.MODE_PRIVATE)
                    .getString(MusicService.GUARD_KEY, null);
            assertTrue("R10: the on-disk guard pref must not still name the deleted id (" + guardOnDisk
                    + "). isServerReportSuppressed() restores from this value whenever its cached "
                    + "field is null, i.e. on the next process start, so a surviving pref means "
                    + "reports stay suppressed after a restart. No restart was performed here; "
                    + "this read is the restart-equivalent bound of this leg.",
                    guardOnDisk == null || !itemId.equals(guardOnDisk));
            assertEquals("server oracle: the item is gone", 404,
                    serverItemStatus(server, token, user, itemId));

            // ---- leg 1: the request-log oracle, over the 30 s after delete --------------
            // Playback is (re)started so the window is one in which the app is expected to
            // emit progress reports — for the NEW item. That is what makes the positive
            // control below ("the capture contains Sessions/Playing traffic") reachable; the
            // negative assertion then means something.
            int nextIndex = queueIds().indexOf(nextTrack.id);
            assertTrue("R10 setup: the advanced item must still be in the playing queue for a "
                    + "progress report to be observable", nextIndex >= 0);
            MusicPlayerRemote.playSongAt(nextIndex);
            if (reqLogUrl == null) {
                String skipped = "SKIPPED (request-log oracle unavailable, L2)";
                System.out.println(skipped);
                Log.i("DeleteFromLibraryE2e", skipped);
            } else {
                long deadline = System.currentTimeMillis() + 30_000;
                boolean sawProgressTraffic = false;
                List<String> reportsNamingDeadId = new ArrayList<>();
                while (true) {
                    HttpResp capture = rawGet(reqLogUrl);
                    if (capture.status != 200) {
                        fail("E2E-5 could not read the request-log capture at " + reqLogUrl
                                + ": HTTP " + capture.status + ". With the oracle asked for, an "
                                + "unreadable log is a failure, not a skip.");
                    }
                    for (String line : capture.body.split("\n")) {
                        if (line.contains("Sessions/Playing")) {
                            sawProgressTraffic = true;
                        }
                        if (line.contains("Sessions/Playing/Progress") && line.contains(itemId)) {
                            reportsNamingDeadId.add(line);
                        }
                    }
                    if (System.currentTimeMillis() > deadline) {
                        break;
                    }
                    Thread.sleep(3_000);
                }
                if (!sawProgressTraffic) {
                    fail("E2E-5 leg 1: the capture at " + reqLogUrl + " contained no "
                            + "Sessions/Playing traffic at all during the 30 s window, so \"no "
                            + "progress report names the dead id\" would be vacuously true. The "
                            + "oracle is present but proves nothing here — fix the capture, or "
                            + "omit -e qa_reqlog so the leg reports the L2 skip honestly.");
                }
                assertTrue("R10: no Sessions/Playing/Progress request may name the deleted id, "
                        + "found " + reportsNamingDeadId.size() + " such line(s): "
                        + reportsNamingDeadId, reportsNamingDeadId.isEmpty());
            }
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
        }
    }

    /**
     * E2E-6 (R11): the device-level half of the cache purge. The JVM/instrumented
     * CachePurgeOnDeleteInstrumentedTest calls {@code purgeCacheEntry} directly; this case
     * drives the whole path — the app's OWN download action caches the fixture, the player
     * overflow deletes it from the library, and the row and the bytes must both disappear.
     *
     * <p>Non-vacuity, in order: the fixture is asserted NOT cached before the download, then
     * cached (row AND at least one on-disk path) after it, so "cached state is gone" is a
     * statement about a state that was really there. Stale fixture files are removed before
     * anything is created — {@code MusicUtil.getFileUri(song)} is built from
     * artist/album/track/title, not the run-unique id, so one leftover file from a previous
     * run makes the final "file is gone" assertion pass on a broken purge (the poisoned
     * fixture lesson).
     *
     * <p>If the app's own cache action cannot cache this item at run time the case FAILS
     * naming that (R-EV: no skip). Destructive as E2E-1 — driver cycle only.
     */
    @Test
    public void e2e6_cachedSongPurged() throws Exception {
        String server = requireArg("qa_server");
        String token = requireArg("qa_token");
        String user = requireArg("qa_user");
        String itemId = requireArg("qa_item");

        assertEquals("E2E-6 precondition: the sacrificial item must exist on the server (P0)",
                200, serverItemStatus(server, token, user, itemId));
        Song sacrificial = findSong(itemId);
        assertNotNull("E2E-6 needs the sacrificial item synced in the app library", sacrificial);
        Song nextTrack = findOtherSong(itemId);
        assertNotNull("E2E-6 needs a second local track so the queue can advance off the "
                + "deleted item", nextTrack);

        App app = App.getInstance();
        String cacheRoot = PreferenceUtil.getInstance(app).getLocationCache();
        File direct = new File(cacheRoot, itemId);
        File download = new File(cacheRoot, "download/" + itemId);
        File audio = new File(MusicUtil.getFileUri(sacrificial));
        File[] paths = new File[]{direct, download, audio};

        // Poisoned-fixture lesson: reclaim first, and prove the reclaim worked.
        for (File file : paths) {
            if (file.exists() && !file.delete()) {
                fail("E2E-6 could not remove a stale fixture file before starting: " + file
                        + ". A leftover cached file makes the final \"the purge removed the "
                        + "bytes\" assertion pass without any purge having run.");
            }
        }
        App.getDatabase().cacheDao().deleteFromCache(itemId);
        assertFalse("E2E-6 precondition: the item must start UNcached, otherwise \"the delete "
                + "purged the cache\" cannot be distinguished from \"it was never cached\"",
                App.getDatabase().cacheDao().isCached(itemId));

        PreferenceUtil.getInstance(app).setNowPlayingScreen(NowPlayingScreen.CARD);
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();
        try {
            MusicPlayerRemote.ServiceToken bound = bind(scenario);
            tokenRef.set(bound);
            scenario.onActivity(MainActivity::onServiceConnected);
            dismissBatteryDialogIfPresent();

            // ---- cache it through the app's own entry point ---------------------------
            scenario.onActivity(activity ->
                    NavigationUtil.startDownload(activity, Collections.singletonList(sacrificial)));
            long cacheDeadline = System.currentTimeMillis() + 60_000;
            while (!App.getDatabase().cacheDao().isCached(itemId)) {
                if (System.currentTimeMillis() > cacheDeadline) {
                    fail("R11 setup: NavigationUtil.startDownload() never produced a cache row "
                            + "for " + itemId + " within 60 s, so there is no cached state for "
                            + "the delete to purge and R11 is untested. Check the device's "
                            + "network path to " + server + " and the signed-in account.");
                }
                Thread.sleep(1_000);
            }
            // The row alone is not enough: with no bytes on disk the file-level purge
            // assertions would pass vacuously.
            long bytesDeadline = System.currentTimeMillis() + 180_000;
            while (!anyExists(paths)) {
                if (System.currentTimeMillis() > bytesDeadline) {
                    fail("R11 setup: " + itemId + " is in the cache table but none of its cache "
                            + "paths exist on disk (" + direct + ", " + download + ", " + audio
                            + "), so \"the purge removed the cached files\" would prove nothing. "
                            + "The download did not produce bytes on this device.");
                }
                Thread.sleep(2_000);
            }

            // ---- delete it through the UI --------------------------------------------
            List<Song> queue = Arrays.asList(sacrificial, nextTrack);
            scenario.onActivity(activity -> {
                MusicPlayerRemote.openQueue(queue, 0, false);
                activity.onQueueChanged();
                activity.onPlayMetadataChanged();
            });
            Thread.sleep(1_500);
            tapDeleteOnCurrentTrack(scenario);
            assertToastShownEventually(R.string.delete_from_library_success, 15_000);
            assertEquals("server oracle: the delete reached the server", 404,
                    serverItemStatus(server, token, user, itemId));

            // ---- the purge: row gone AND bytes gone ----------------------------------
            long purgeDeadline = System.currentTimeMillis() + 20_000;
            while (App.getDatabase().cacheDao().isCached(itemId) || anyExists(paths)) {
                if (System.currentTimeMillis() > purgeDeadline) {
                    fail("R11: after a successful library delete the cache row and the cached "
                            + "bytes must both be gone. Still cached row: "
                            + App.getDatabase().cacheDao().isCached(itemId) + "; still present: "
                            + existingPaths(paths) + ". A cached copy of a file the server "
                            + "deleted is exactly what R11 forbids.");
                }
                Thread.sleep(1_000);
            }
            assertFalse("R11: the cache row must be gone", 
                    App.getDatabase().cacheDao().isCached(itemId));
            for (File file : paths) {
                assertFalse("R11: the cached file must be gone: " + file, file.exists());
            }
            assertFalse("R4: the deleted item must also be out of the queue",
                    queueIds().contains(itemId));
        } finally {
            // Restores the state this method found (uncached, no fixture bytes) on the
            // failure paths too. Only this item's cache row and files — never library rows.
            try {
                App.getDatabase().cacheDao().deleteFromCache(itemId);
            } catch (Exception ignored) {
            }
            for (File file : paths) {
                if (file.exists()) {
                    file.delete();
                }
            }
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
        }
    }

    private static boolean anyExists(File[] files) {
        for (File file : files) {
            if (file.exists()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> existingPaths(File[] files) {
        List<String> present = new ArrayList<>();
        for (File file : files) {
            if (file.exists()) {
                present.add(file.getPath());
            }
        }
        return present;
    }

    /** GET that preserves line breaks, for scanning a captured request log. */
    private static HttpResp rawGet(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        int status = connection.getResponseCode();
        StringBuilder body = new StringBuilder();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append('\n');
                }
            } catch (Exception ignored) {
            }
        }
        connection.disconnect();
        return new HttpResp(status, body.toString());
    }

    /**
     * Expand the now-playing panel, open the player overflow and confirm the delete.
     * Shared by the methods added in part 2; part 1's methods keep their own inline idiom.
     */
    private static void tapDeleteOnCurrentTrack(ActivityScenario<MainActivity> scenario)
            throws Exception {
        scenario.onActivity(activity -> ((AbsMusicPanelActivity) activity).expandPanel());
        Thread.sleep(1_000);
        openPlayerOverflowAndTapDelete();
        onView(withText(R.string.delete_from_library_confirm)).inRoot(isDialog())
                .perform(click());
    }

    /** An authorized call against the QA server; body is the full response text. */
    private static final class HttpResp {
        final int status;
        final String body;

        HttpResp(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static HttpResp authorized(String server, String method, String path, String token,
            String cacheBuster) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(server + path).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization",
                "MediaBrowser Client=\"jellywing-qa\", Device=\"qa\", DeviceId=\"qa\", Token=\""
                        + token + "\"");
        if (cacheBuster != null) {
            connection.setRequestProperty("Cache-Control", "no-cache");
        }
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
            connection.getOutputStream().close();
        }
        int status = connection.getResponseCode();
        StringBuilder body = new StringBuilder();
        InputStream stream = status >= 400 ? connection.getErrorStream()
                : connection.getInputStream();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            } catch (Exception ignored) {
            }
        }
        connection.disconnect();
        return new HttpResp(status, body.toString());
    }

    /**
     * Ids of every playlist this user owns. A non-200 is a fail(), not an empty list: an
     * empty list here would make every later "the dead id is not listed" assertion pass.
     */
    private static List<String> serverPlaylistIds(String server, String token, String user)
            throws Exception {
        HttpResp resp = authorized(server, "GET", "/Users/" + user
                + "/Playlists?PageSize=100", token, null);
        if (resp.status != 200) {
            fail("E2E-2 could not enumerate the user's playlists: GET /Users/" + user
                    + "/Playlists returned HTTP " + resp.status + ". Without a listing there is "
                    + "no oracle for playlist cleanliness, so nothing here can be reported as "
                    + "passing.");
        }
        List<String> ids = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"Id\"\\s*:\\s*\"([^\"]+)\"").matcher(resp.body);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        if (ids.isEmpty()) {
            fail("E2E-2 found no playlists for user " + user + " (HTTP 200, empty collection). "
                    + "The sacrificial item must live in a QA playlist for this case to have "
                    + "any oracle at all.");
        }
        return ids;
    }

    /**
     * Raw membership JSON for one playlist.
     *
     * <p>Endpoint is {@code GET /Playlists/{playlistId}/Items?UserId={user}} — NOT
     * {@code /Items/{playlistId}/Items}, which returns 404 for a playlist id and would make
     * "the id is not in the listing" true for every id, dead or alive. Any non-200 fails
     * for that reason.
     */
    private static String playlistMembership(String server, String token, String user,
            String playlistId, String cacheBuster) throws Exception {
        String path = "/Playlists/" + playlistId + "/Items?UserId=" + user + "&Limit=1000"
                + (cacheBuster == null ? "" : "&X-QA-Read=" + cacheBuster);
        HttpResp resp = authorized(server, "GET", path, token, cacheBuster);
        if (resp.status != 200) {
            fail("E2E-2 membership read of playlist " + playlistId + " via GET " + path
                    + " returned HTTP " + resp.status + ". A failed listing is NOT evidence of "
                    + "cleanliness (that is why /Items/{playlistId}/Items is never used here).");
        }
        return resp.body;
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
