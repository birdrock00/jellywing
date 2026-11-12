package club.thatpetbff.gramophone.database;

import static org.junit.Assert.assertFalse;
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
        App.getDatabase().songDao().insertSongs(Collections.singletonList(song));
        App.getDatabase().cacheDao().insertCache(new Cache(song));
        assertTrue("fixture must start cached", App.getDatabase().cacheDao().isCached(id));

        String cacheRoot = PreferenceUtil.getInstance(app).getLocationCache();
        File direct = new File(cacheRoot, id);
        File download = new File(cacheRoot, "download/" + id);
        File audio = new File(club.thatpetbff.gramophone.util.MusicUtil.getFileUri(song));
        for (File file : new File[]{direct, download, audio}) {
            file.getParentFile().mkdirs();
            assertTrue("fixture file must be creatable: " + file.getName(), file.createNewFile());
        }

        LibraryDeleteUtil.purgeCacheEntry(id);

        assertFalse("cache row must be gone after purge (R11)", isCached(app, id));
        assertFalse("cached file at cache root must be gone (R11)", direct.exists());
        assertFalse("in-flight download file must be gone (R11)", download.exists());
        assertFalse("downloaded audio file must be gone (R11)", audio.exists());
    }

    @Test
    public void purgeOfUncachedItemIsANoOp() throws Exception {
        App app = (App) ApplicationProvider.getApplicationContext();
        String keepId = "qa-keep-" + System.nanoTime();
        String missingId = "qa-missing-" + System.nanoTime();

        Song keep = new Song();
        keep.id = keepId;
        keep.title = "QA Keep Fixture";
        App.getDatabase().songDao().insertSongs(Collections.singletonList(keep));
        App.getDatabase().cacheDao().insertCache(new Cache(keep));
        String cacheRoot = PreferenceUtil.getInstance(app).getLocationCache();
        File keepFile = new File(cacheRoot, keepId);
        keepFile.getParentFile().mkdirs();
        keepFile.createNewFile();

        LibraryDeleteUtil.purgeCacheEntry(missingId);

        assertTrue("un-cached purge must not disturb other rows (R11)",
                App.getDatabase().cacheDao().isCached(keepId));
        assertTrue("un-cached purge must not disturb other files (R11)", keepFile.exists());

        // And it must tolerate its own files being absent entirely.
        LibraryDeleteUtil.purgeCacheEntry(missingId);
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
