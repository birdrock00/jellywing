package club.thatpetbff.gramophone.ui;

import static org.junit.Assume.assumeTrue;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.model.User;
import club.thatpetbff.gramophone.util.PreferenceUtil;
import club.thatpetbff.gramophone.util.ShortcutUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class RealSongTestData {
    private static final long LOAD_TIMEOUT_MS = 30_000;

    private RealSongTestData() {
    }

    static List<Song> loadPlayableSongs(int minimumCount) throws InterruptedException {
        requireExistingSession();

        List<Song> songs = usableSongs(App.getDatabase().songDao().getSongs());
        if (songs.size() < minimumCount) {
            songs.addAll(loadRandomLibrarySongs());
        }

        List<Song> uniqueSongs = uniqueByTitle(songs);
        assumeTrue("Need at least " + minimumCount + " real library songs for this UI test",
                uniqueSongs.size() >= minimumCount);
        return new ArrayList<>(uniqueSongs.subList(0, minimumCount));
    }

    static String title(Song song) {
        return song.getDisplayTitle();
    }

    private static void requireExistingSession() {
        PreferenceUtil preferences = PreferenceUtil.getInstance(App.getInstance());
        String userId = preferences.getUser();
        User user = hasText(userId) ? App.getDatabase().userDao().getUser(userId) : null;

        assumeTrue("Need an existing real Jellyfin session for real-song UI tests",
                user != null && hasText(user.server) && hasText(user.token) && hasText(user.id));

        App.getApiClient().ChangeServerLocation(user.server);
        App.getApiClient().SetAuthenticationInfo(user.token, user.id);
    }

    private static List<Song> loadRandomLibrarySongs() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Song>> songsRef = new AtomicReference<>(new ArrayList<>());

        ShortcutUtil.getShuffle(songs -> {
            songsRef.set(songs);
            latch.countDown();
        }, false);

        latch.await(LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return usableSongs(songsRef.get());
    }

    private static List<Song> usableSongs(List<Song> songs) {
        List<Song> usableSongs = new ArrayList<>();
        if (songs == null) {
            return usableSongs;
        }

        for (Song song : songs) {
            if (isUsable(song)) {
                usableSongs.add(song);
            }
        }
        return usableSongs;
    }

    private static boolean isUsable(Song song) {
        if (song == null || !hasText(song.id) || !song.hasDisplayableTitle()) {
            return false;
        }

        return true;
    }

    private static List<Song> uniqueByTitle(List<Song> songs) {
        Map<String, Song> uniqueSongs = new LinkedHashMap<>();
        for (Song song : songs) {
            uniqueSongs.put(song.getDisplayTitle(), song);
        }
        return new ArrayList<>(uniqueSongs.values());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
