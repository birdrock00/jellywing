package com.dkanada.gramophone.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.adapter.song.PlayingQueueAdapter;
import com.dkanada.gramophone.model.Song;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class UpNextStructureRegressionTest {
    @Test
    public void cardPlayerKeepsUpNextHeaderAndQueueList() throws IOException {
        assertUpNextLayout("app/src/main/res/layout/fragment_card_player.xml");
    }

    @Test
    public void flatPlayerKeepsUpNextHeaderAndQueueList() throws IOException {
        assertUpNextLayout("app/src/main/res/layout/fragment_flat_player.xml");
    }

    @Test
    public void cardPlayerRefreshesUpNextWhenQueueOrCurrentSongChanges() throws IOException {
        assertUpNextRefreshHooks("app/src/main/java/com/dkanada/gramophone/fragments/player/card/CardPlayerFragment.java");
    }

    @Test
    public void flatPlayerRefreshesUpNextWhenQueueOrCurrentSongChanges() throws IOException {
        assertUpNextRefreshHooks("app/src/main/java/com/dkanada/gramophone/fragments/player/flat/FlatPlayerFragment.java");
    }

    @Test
    public void playersFillEmptyUpNextWithInstantMixRecommendations() throws IOException {
        assertRecommendedUpNextFallback("app/src/main/java/com/dkanada/gramophone/fragments/player/card/CardPlayerFragment.java");
        assertRecommendedUpNextFallback("app/src/main/java/com/dkanada/gramophone/fragments/player/flat/FlatPlayerFragment.java");

        String remote = readProjectFile("app/src/main/java/com/dkanada/gramophone/helper/MusicPlayerRemote.java");
        assertTrue(remote.contains("public static boolean enqueueSilently(@NonNull List<Song> songs)"));

        String shortcut = readProjectFile("app/src/main/java/com/dkanada/gramophone/util/ShortcutUtil.java");
        assertTrue(shortcut.contains("public static void getInstantMix(Song song, int limit, MediaCallback<Song> callback)"));
        assertTrue(shortcut.contains("App.getApiClient().GetInstantMixFromItem(query, new Response<ItemsResult>()"));
    }

    @Test
    public void searchSongClicksOpenAllSongResultsForUpNext() throws IOException {
        String source = readProjectFile("app/src/main/java/com/dkanada/gramophone/adapter/SearchAdapter.java");

        assertTrue(source.contains("private List<Song> getSongResults()"));
        assertTrue(source.contains("if (item instanceof Song)"));
        assertTrue(source.contains("MusicPlayerRemote.openQueue(playList, playList.indexOf(item), true);"));
    }

    private static void assertUpNextLayout(String relativePath) throws IOException {
        String layout = readProjectFile(relativePath);

        assertTrue(layout.contains("android:id=\"@+id/player_queue_sub_header\""));
        assertTrue(layout.contains("android:text=\"@string/up_next\""));
        assertTrue(layout.contains("android:id=\"@+id/player_recycler_viewport\""));
        assertTrue(layout.contains("android:id=\"@+id/player_recycler_view\""));
        assertTrue(layout.contains("android:layout_height=\"0dp\""));
        assertTrue(layout.contains("android:layout_weight=\"1\""));
        assertTrue(layout.contains("android:clipChildren=\"true\""));
        assertTrue(layout.contains("android:clipToPadding=\"true\""));
        assertTrue(layout.contains("android:layout_height=\"match_parent\""));
    }

    private static void assertUpNextRefreshHooks(String relativePath) throws IOException {
        String source = readProjectFile(relativePath);

        assertTrue(source.contains("playingQueueAdapter.swapDataSet(MusicPlayerRemote.getPlayingQueue(), MusicPlayerRemote.getPosition());"));
        assertTrue(source.contains("playingQueueAdapter.setCurrent(MusicPlayerRemote.getPosition());"));
        assertTrue(source.contains("binding.playerQueueSubHeader.setText(getUpNextAndQueueTime());"));
    }

    private static void assertRecommendedUpNextFallback(String relativePath) throws IOException {
        String source = readProjectFile(relativePath);

        assertTrue(source.contains("ensureRecommendedUpNext();"));
        assertTrue(source.contains("hasUpcomingSongs()"));
        assertTrue(source.contains("ShortcutUtil.getInstantMix(MusicPlayerRemote.getCurrentSong(), RECOMMENDED_UP_NEXT_LIMIT, songs ->"));
        assertTrue(source.contains("isUsableRecommendedUpNextSong(song, currentSong)"));
        assertTrue(source.contains("MusicPlayerRemote.enqueueSilently(upNext)"));
        assertTrue(source.contains("binding.playerRecyclerView.post(this::updateQueue);"));
    }

    @Test
    public void playingQueueAdapterOnlyShowsSongsAfterCurrentPosition() throws IOException {
        String source = readProjectFile("app/src/main/java/com/dkanada/gramophone/adapter/song/PlayingQueueAdapter.java");

        assertTrue(source.contains("for (int position = current + 1; position < queue.size(); position++)"));
        assertTrue(source.contains("positions.add(position);"));
        assertTrue(!source.contains("(current + offset) % queue.size()"));
    }

    @Test
    public void visibleUpNextRowsMapToExactUnderlyingQueuePositions() {
        List<Song> queue = Arrays.asList(song("current"), song("next-one"), song("next-two"), song("next-three"));

        List<Integer> positions = PlayingQueueAdapter.createUpNextQueuePositionsForTest(queue, 0);

        assertEquals(Arrays.asList(1, 2, 3), positions);
        assertEquals(1, PlayingQueueAdapter.getQueuePositionForTest(positions, 0));
        assertEquals(2, PlayingQueueAdapter.getQueuePositionForTest(positions, 1));
        assertEquals(3, PlayingQueueAdapter.getQueuePositionForTest(positions, 2));
    }

    @Test
    public void visibleUpNextRowsRemapAfterCurrentSongChanges() {
        List<Song> queue = Arrays.asList(song("first"), song("selected"), song("after-selected"), song("last"));

        List<Integer> positions = PlayingQueueAdapter.createUpNextQueuePositionsForTest(queue, 1);

        assertEquals(Arrays.asList(2, 3), positions);
        assertEquals(2, PlayingQueueAdapter.getQueuePositionForTest(positions, 0));
        assertEquals(3, PlayingQueueAdapter.getQueuePositionForTest(positions, 1));
        assertEquals(RecyclerView.NO_POSITION, PlayingQueueAdapter.getQueuePositionForTest(positions, 2));
        assertEquals(RecyclerView.NO_POSITION, PlayingQueueAdapter.getQueuePositionForTest(positions, RecyclerView.NO_POSITION));
    }

    @Test
    public void visibleUpNextRowsDoNotRepeatTheCurrentSong() {
        List<Song> queue = Arrays.asList(song("current"), song("current"), song("next-one"), song("next-two"));

        List<Integer> positions = PlayingQueueAdapter.createUpNextQueuePositionsForTest(queue, 0);

        assertEquals(Arrays.asList(2, 3), positions);
        assertEquals(2, PlayingQueueAdapter.getQueuePositionForTest(positions, 0));
        assertEquals(3, PlayingQueueAdapter.getQueuePositionForTest(positions, 1));
    }

    @Test
    public void upNextClicksUseRealQueuePositionsAndIgnoreRapidDoubleTaps() throws IOException {
        String adapter = readProjectFile("app/src/main/java/com/dkanada/gramophone/adapter/song/PlayingQueueAdapter.java");
        String service = readProjectFile("app/src/main/java/com/dkanada/gramophone/service/MusicService.java");

        assertTrue(adapter.contains("private static final long PLAY_CLICK_DEBOUNCE_MS = 2000;"));
        assertTrue(adapter.contains("private static long lastPlayClickUptime;"));
        assertTrue(adapter.contains("holder.itemView.setTag(R.id.playing_queue_position, getQueuePosition(position));"));
        assertTrue(adapter.contains("!isSameSong(currentSong, queue.get(position))"));
        assertTrue(adapter.contains("if (now - lastPlayClickUptime < PLAY_CLICK_DEBOUNCE_MS)"));
        assertTrue(adapter.contains("int queuePosition = getBoundQueuePosition();"));
        assertTrue(adapter.contains("MusicPlayerRemote.openQueue(new ArrayList<>(playingQueue), queuePosition, true);"));
        assertTrue(adapter.contains("lastPlayClickUptime = now;"));
        assertTrue(adapter.contains("return false;"));
        assertTrue(adapter.contains("public long getItemId(int position)"));
        assertTrue(service.contains("if (position < 0 || position >= queueManager.getPlayingQueue().size())"));
        assertTrue(service.contains("playback.setQueue(queueManager.getPlayingQueue(), queueManager.getPosition(), 0, true);"));
        assertTrue(service.contains("saveState();"));
        assertTrue(service.contains("notifyChange(STATE_CHANGED);"));
        assertTrue(service.contains("notifyChange(META_CHANGED);"));
        assertTrue(service.contains("notifyChange(QUEUE_CHANGED);"));
    }

    @Test
    public void selectingUpNextRefreshesCurrentSongTitleArtworkAndQueueViews() throws IOException {
        String service = readProjectFile("app/src/main/java/com/dkanada/gramophone/service/MusicService.java");
        String albumCover = readProjectFile("app/src/main/java/com/dkanada/gramophone/fragments/player/PlayerAlbumCoverFragment.java");
        String albumAdapter = readProjectFile("app/src/main/java/com/dkanada/gramophone/adapter/AlbumCoverPagerAdapter.java");

        assertTrue(service.contains("queueManager.setPosition(position);"));
        assertTrue(service.contains("playback.setQueue(queueManager.getPlayingQueue(), queueManager.getPosition(), 0, true);"));
        assertTrue(service.contains("notifyChange(STATE_CHANGED);"));
        assertTrue(service.contains("notifyChange(META_CHANGED);"));
        assertTrue(service.contains("notifyChange(QUEUE_CHANGED);"));
        assertTrue(albumCover.contains("binding.playerAlbumCoverViewPager.setAdapter(null);"));
        assertTrue(albumCover.contains("binding.playerAlbumCoverViewPager.setCurrentItem(position, false);"));
        assertTrue(albumCover.contains("forceCurrentCoverFragment(adapter, position);"));
        assertTrue(albumCover.contains("adapter.instantiateItem(binding.playerAlbumCoverViewPager, position);"));
        assertTrue(albumCover.contains("updateCurrentArtwork();"));
        assertTrue(albumCover.contains("binding.playerCurrentImage.setTag(R.id.current_album_artwork_song_id, song.id);"));
        assertTrue(albumCover.contains("binding.playerCurrentImage.setTag(R.id.current_album_artwork_id, artworkItemId);"));
        assertTrue(albumCover.contains(".from(requireContext(), artworkItemId, song.blurHash)"));
        assertTrue(albumCover.contains("if (!syncingCurrentItem && position != MusicPlayerRemote.getPosition())"));
        assertTrue(albumCover.contains("public void onPlayMetadataChanged()"));
        assertTrue(albumCover.contains("updatePlayingQueue();"));
        assertTrue(albumCover.contains("public void refreshCurrentSong()"));
        assertTrue(albumCover.contains("binding.playerSongInfo.setVisibility(View.GONE);"));
        assertTrue(albumAdapter.contains("this.dataSet = dataSet == null ? new ArrayList<>() : new ArrayList<>(dataSet);"));
        assertTrue(albumAdapter.contains("public int getItemPosition(@NonNull Object object)"));
        assertTrue(albumAdapter.contains("return POSITION_NONE;"));
        assertTrue(albumAdapter.contains(".from(getContext(), song.getArtworkItemId(), song.blurHash)"));
    }

    private static Song song(String id) {
        Song song = new Song();
        song.id = id;
        song.title = id;
        return song;
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        for (Path current = start; current != null; current = current.getParent()) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }

        throw new IOException("Unable to locate " + relativePath + " from " + start);
    }
}
