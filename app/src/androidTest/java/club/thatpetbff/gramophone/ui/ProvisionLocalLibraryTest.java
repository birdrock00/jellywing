package club.thatpetbff.gramophone.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.querying.ItemFields;
import org.jellyfin.apiclient.model.querying.ItemQuery;
import org.jellyfin.apiclient.model.querying.ItemsResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.model.Song;

/**
 * QA provisioning only — this asserts nothing about product behaviour.
 *
 * <p>The {@code songs} table is the app's local library view, and its only production writer is
 * {@code QueueSongDao.saveQueue}: a track appears locally once it has been in a playing queue. The
 * destructive delete E2E ({@link DeleteFromLibraryE2eTest}) fails its precondition unless the
 * sacrificial track is present there, so a QA device must be provisioned first.
 *
 * <p>This does that through the product's own paths and nothing else: the app's configured session,
 * {@code App.getApiClient()} for the server read, {@code new Song(dto)} for the mapping, and
 * {@code songDao().insertSongs} for the write. It is not a substitute for a user playing the track,
 * and it is not evidence of a sync feature — there isn't one. It exists so the destructive cycle can
 * run against a device that has the same local state a used device would have.
 *
 * <p>No library name or track name is committed here; the search string arrives as
 * {@code -e qa_fixture_search <term>} at invocation time.
 */
@RunWith(AndroidJUnit4.class)
public class ProvisionLocalLibraryTest {

    @Rule
    public GrantPermissionRule permissions = GrantPermissionRule.grant(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.POST_NOTIFICATIONS);

    private static String arg(String key) {
        return InstrumentationRegistry.getArguments().getString(key);
    }

    private static String lastNote = "not called";

    /** One blocking call through the product's api client; null means the call reported an error. */
    private static List<Song> collect(ItemQuery query, String[] errOut) throws Exception {
        final List<Song> out = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                lastNote = "ok total=" + (result == null ? "null-result" : result.getTotalRecordCount())
                        + " items=" + (result == null || result.getItems() == null ? -1 : result.getItems().length);
                if (result != null && result.getItems() != null) {
                    for (BaseItemDto dto : result.getItems()) {
                        out.add(new Song(dto));
                    }
                }
                latch.countDown();
            }

            @Override
            public void onError(Exception exception) {
                lastNote = "ERR " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
                out.clear();
                latch.countDown();
            }
        });
        if (!latch.await(45, TimeUnit.SECONDS)) {
            return null;
        }
        return out;
    }

    @Test
    public void seedFixtureFromServerIntoLocalLibrary() throws Exception {
        String needle = arg("qa_fixture_search");
        assertTrue("provisioning needs -e qa_fixture_search <term>", needle != null && !needle.isEmpty());
        // The instrumentation process starts its own App instance, so the api client is empty until
        // the product's own restore path runs. Using it (rather than poking tokens in here) keeps
        // provisioning on a code path real playback takes.
        App.ensureAuthenticatedApiClient();
        assertTrue("the app must already hold a signed-in session — provision the device first",
                App.getApiClient().getCurrentUserId() != null);

        ItemQuery query = new ItemQuery();
        query.setIncludeItemTypes(new String[]{"Audio"});
        query.setFields(new ItemFields[]{ItemFields.MediaSources});
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setRecursive(true);
        query.setLimit(20);
        query.setSearchTerm(needle);

        List<Song> found = collect(query, null);
        String termErr = "n/a";

        // Diagnosed rather than guessed: this server answers a raw SearchTerm query over HTTP but
        // returned nothing through the generated client, so capture the client error and fall back
        // to an unfiltered browse that filters on device. Both paths are the product's own client.
        if (found == null) {
            found = new ArrayList<>();
        }
        if (found.isEmpty()) {
            ItemQuery browse = new ItemQuery();
            browse.setIncludeItemTypes(new String[]{"Audio"});
            browse.setFields(new ItemFields[]{ItemFields.MediaSources});
            browse.setUserId(App.getApiClient().getCurrentUserId());
            browse.setRecursive(true);
            browse.setLimit(500);
            List<Song> all = collect(browse, null);
            if (all != null) {
                String lower = needle.toLowerCase();
                for (Song song : all) {
                    if (song.title != null && song.title.toLowerCase().contains(lower)) {
                        found.add(song);
                    }
                }
            }
        }
        assertFalse("server returned no audio matching the requested term through the app's client "
                + "(browse fallback also found none) — nothing to provision; client note: " + lastNote,
                found.isEmpty());

        // e2e1 leg 2 asserts the queue advanced, which needs at least one OTHER track locally, so
        // the device is provisioned the way a used phone looks: the target plus a handful of real
        // album tracks, not just the one row under test.
        List<Song> seed = new ArrayList<>(found);
        ItemQuery browse = new ItemQuery();
        browse.setIncludeItemTypes(new String[]{"Audio"});
        browse.setFields(new ItemFields[]{ItemFields.MediaSources});
        browse.setUserId(App.getApiClient().getCurrentUserId());
        browse.setRecursive(true);
        browse.setLimit(20);
        List<Song> more = collect(browse, null);
        if (more != null) {
            for (Song song : more) {
                boolean dup = false;
                for (Song have : seed) {
                    if (have.id.equals(song.id)) {
                        dup = true;
                    }
                }
                if (!dup && seed.size() < 8) {
                    seed.add(song);
                }
            }
        }
        App.getDatabase().songDao().insertSongs(seed);

        List<Song> local = App.getDatabase().songDao().getSongs();
        boolean present = false;
        for (Song song : local) {
            for (Song want : found) {
                if (want.id.equals(song.id)) {
                    present = true;
                }
            }
        }
        assertTrue("provisioned row is not readable back from the local library", present);
    }
}
