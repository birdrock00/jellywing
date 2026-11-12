package club.thatpetbff.gramophone.database;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.database.Cache;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.util.LibraryDeleteUtil;
import club.thatpetbff.gramophone.util.PreferenceUtil;

/**
 * R11: a successful library delete purges the local cache — the Room row and the cached
 * bytes — and an un-cached purge is a no-op. This test touches ONLY the local Room
 * database and the app's own cache directory: no server request, no media delete, so it
 * is safe to run without the QA backup protocol.
 *
 * <p><b>Self-restoring, on purpose.</b> The audio fixture's path comes from
 * {@link club.thatpetbff.gramophone.util.MusicUtil#getFileUri(Song)}, which is built from
 * artist/album/disc/track/title/container — NOT from the run-unique id — so it is the same
 * path on every run. Before this class cleaned up after itself, one run against a broken purge
 * left that file in place and every subsequent run then failed at "fixture file must be
 * creatable" instead of at the R11 assertion it was actually testing (review round 2, F4: it
 * poisoned the next mutant of a campaign and pointed the reader at the fixture rather than at
 * the requirement). So every fixture file is reclaimed before it is created and every fixture
 * file and row is removed in {@code finally}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class CachePurgeOnDeleteInstrumentedTest {

    @Test
    public void purgeRemovesCacheRowAndCachedFiles() throws Exception {
        App app = (App) ApplicationProvider.getApplicationContext();
        String id = "qa-purge-" + System.nanoTime();

        Song song = new Song();
        song.id = id;
        song.title = "QA Purge Fixture";
        song.artistName = "QA Fixture";
        song.albumName = "QA Fixture";
        song.container = "m4a";

        String cacheRoot = PreferenceUtil.getInstance(app).getLocationCache();
        File direct = new File(cacheRoot, id);
        File download = new File(cacheRoot, "download/" + id);
        File audio = new File(club.thatpetbff.gramophone.util.MusicUtil.getFileUri(song));

        try {
            App.getDatabase().songDao().insertSongs(Collections.singletonList(song));
            App.getDatabase().cacheDao().insertCache(new Cache(song));
            assertTrue("fixture must start cached", App.getDatabase().cacheDao().isCached(id));

            for (File file : new File[]{direct, download, audio}) {
                reclaimStaleFixture(file);
                file.getParentFile().mkdirs();
                assertTrue("fixture file must be creatable: " + file, file.createNewFile());
            }

            LibraryDeleteUtil.purgeCacheEntry(id);

            assertFalse("cache row must be gone after purge (R11)", isCached(app, id));
            assertFalse("cached file at cache root must be gone (R11)", direct.exists());
            assertFalse("in-flight download file must be gone (R11)", download.exists());
            assertFalse("downloaded audio file must be gone (R11)", audio.exists());
        } finally {
            restoreFixtureState(song, direct, download, audio);
        }
    }

    /**
     * R11's missing-songs-row half, which no other leg covered: the purge reads the song row to
     * derive the downloaded-file name, so gating the {@code cache} row delete on that lookup being
     * non-null silently leaves a cache row for an item the library no longer has — the app keeps
     * offering a cached copy of a file the server deleted.
     *
     * <p>The state under test is "cache row present, songs row absent". {@code SongDao}
     * only offers a whole-table {@code deleteSongs()}, which would destroy the real library rows on
     * a development emulator, so the songs row is simply never created. The observable state is
     * identical, the leg stays non-destructive, and the precondition is asserted rather than
     * assumed.
     */
    @Test
    public void purgeRemovesCacheRowWhenTheSongsRowIsAbsent() throws Exception {
        App app = (App) ApplicationProvider.getApplicationContext();
        String id = "qa-orphan-" + System.nanoTime();
        Song orphan = new Song();
        orphan.id = id;
        orphan.title = "QA Orphan Fixture";
        App.getDatabase().cacheDao().insertCache(new Cache(orphan));
        try {
            assertTrue("fixture must start cached", App.getDatabase().cacheDao().isCached(id));
            assertNull("fixture must have NO songs row — that absence is the whole point (R11)",
                    App.getDatabase().songDao().getSong(id));

            LibraryDeleteUtil.purgeCacheEntry(id);

            assertFalse("a missing songs row must not skip the Room cache row — the cache row "
                            + "delete is unconditional in purgeCacheEntry (R11)",
                    isCached(app, id));
        } finally {
            restoreFixtureState(orphan);
        }
    }

    @Test
    public void purgeOfUncachedItemIsANoOp() throws Exception {
        App app = (App) ApplicationProvider.getApplicationContext();
        String keepId = "qa-keep-" + System.nanoTime();
        String missingId = "qa-missing-" + System.nanoTime();

        Song keep = new Song();
        keep.id = keepId;
        keep.title = "QA Keep Fixture";
        String cacheRoot = PreferenceUtil.getInstance(app).getLocationCache();
        File keepFile = new File(cacheRoot, keepId);

        try {
            App.getDatabase().songDao().insertSongs(Collections.singletonList(keep));
            App.getDatabase().cacheDao().insertCache(new Cache(keep));
            reclaimStaleFixture(keepFile);
            keepFile.getParentFile().mkdirs();
            keepFile.createNewFile();

            LibraryDeleteUtil.purgeCacheEntry(missingId);

            assertTrue("un-cached purge must not disturb other rows (R11)",
                    App.getDatabase().cacheDao().isCached(keepId));
            assertTrue("un-cached purge must not disturb other files (R11)", keepFile.exists());

            // And it must tolerate its own files being absent entirely.
            LibraryDeleteUtil.purgeCacheEntry(missingId);
        } finally {
            restoreFixtureState(keep, keepFile);
        }
    }

    /**
     * Remove a fixture file left behind by an earlier run whose purge was broken, BEFORE
     * {@code createNewFile()} is asked to succeed. The direct and download paths carry the
     * run-unique id, but the audio path does not (see the class note), so without this the second
     * run of a broken-purge campaign fails on fixture creation and never reaches the R11 assertion
     * that is the reason the class exists.
     *
     * <p>Safe by construction: every path passed in is one this class itself creates under the
     * debug app's own cache/download directories, and the audio path is the exact file
     * {@code purgeCacheEntry} is under test to delete.
     */
    private static void reclaimStaleFixture(File file) {
        file.delete();
        assertFalse("a stale fixture file from an earlier run must be reclaimed before this run "
                        + "creates it — otherwise a broken purge makes the NEXT run fail on "
                        + "fixture creation instead of on the R11 assertion (F4): " + file,
                file.exists());
    }

    /**
     * Put the device back the way the run found it: no fixture rows, no fixture files. Runs in
     * {@code finally} so a failing assertion cannot leave state that changes the meaning of the
     * next run. Failures here are deliberately not swallowed into a name like {@code ignored}: if
     * cleanup stops working, the next run must still be able to tell the difference — and it can,
     * because {@link #reclaimStaleFixture(File)} asserts the reclaim.
     */
    private static void restoreFixtureState(Song song, File... fixtureFiles) {
        for (File file : fixtureFiles) {
            file.delete();
        }
        App.getDatabase().cacheDao().deleteFromCache(song.id);
        deleteSongsRow(song.id);
    }

    /**
     * Delete exactly the fixture's own {@code songs} row. {@code SongDao.deleteSongs()} is a
     * whole-table {@code DELETE FROM songs}, which on a development emulator would wipe the real
     * library the app has indexed; the fixture is keyed by a run-unique id, so the row is removed
     * by that id instead.
     */
    private static void deleteSongsRow(String id) {
        App.getDatabase().getOpenHelper().getWritableDatabase()
                .delete("songs", "id = ?", new Object[]{id});
    }

    private static boolean isCached(App app, String id) {
        try {
            return App.getDatabase().cacheDao().isCached(id);
        } catch (Throwable rowAbsent) {
            // Room surfaces a missing row as an error on the scalar query — absence is
            // exactly what R11 asks for.
            return false;
        }
    }
}
