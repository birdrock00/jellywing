package club.thatpetbff.gramophone.player;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SearchSelectionArtworkRegressionTest {
    @Test
    public void searchSongSelectionOpensSelectedSongQueueBeforeRefreshingPlaybackCallbacks() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/adapter/SearchAdapter.java");

        assertTrue(source.contains("import club.thatpetbff.gramophone.activities.base.AbsMusicServiceActivity;"));
        assertTrue(source.contains("List<Song> playList = getSongResults();"));
        assertInOrder(source,
                "MusicPlayerRemote.openQueue(playList, playList.indexOf(item), true);",
                "if (activity instanceof AbsMusicServiceActivity)",
                "AbsMusicServiceActivity musicActivity = (AbsMusicServiceActivity) activity;",
                "musicActivity.onQueueChanged();",
                "musicActivity.onPlayMetadataChanged();");
    }

    @Test
    public void searchPlaybackCallbacksReachCurrentArtworkRefreshInBothNowPlayingScreens() throws IOException {
        assertPlayerRefreshesCurrentArtworkOnMetadataChange(
                "app/src/main/java/club/thatpetbff/gramophone/fragments/player/card/CardPlayerFragment.java");
        assertPlayerRefreshesCurrentArtworkOnMetadataChange(
                "app/src/main/java/club/thatpetbff/gramophone/fragments/player/flat/FlatPlayerFragment.java");
    }

    @Test
    public void currentArtworkImageOnlyAcceptsLoadsForTheNewestSelectedSong() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/fragments/player/PlayerAlbumCoverFragment.java");

        assertInOrder(source,
                "String artworkItemId = song.getArtworkItemId();",
                "binding.playerCurrentImage.setTag(R.id.current_album_artwork_song_id, song.id);",
                "binding.playerCurrentImage.setTag(R.id.current_album_artwork_id, artworkItemId);",
                ".from(requireContext(), artworkItemId, song.blurHash)",
                ".into(new CurrentArtworkTarget(binding.playerCurrentImage, song.id, artworkItemId));");
        assertTrue(source.contains("private class CurrentArtworkTarget extends CustomPaletteTarget"));
        assertTrue(source.contains("public void onLoadFailed(Drawable errorDrawable)"));
        assertTrue(source.contains("public void onResourceReady(@NonNull BitmapPaletteWrapper resource, Transition<? super BitmapPaletteWrapper> glideAnimation)"));
        assertTrue(source.contains("public void onColorReady(int color)"));
        assertTrue(source.contains("private boolean isCurrentRequest()"));
        assertTrue(source.contains("Objects.equals(songId, binding.playerCurrentImage.getTag(R.id.current_album_artwork_song_id))"));
        assertTrue(source.contains("Objects.equals(artworkItemId, binding.playerCurrentImage.getTag(R.id.current_album_artwork_id))"));
    }

    private static void assertPlayerRefreshesCurrentArtworkOnMetadataChange(String relativePath) throws IOException {
        String source = readProjectFile(relativePath);

        assertInOrder(source,
                "public void onPlayMetadataChanged()",
                "updateCurrentSong();",
                "updateIsFavorite();",
                "updateQueue();");
        assertInOrder(source,
                "private void updateCurrentSong()",
                "impl.updateCurrentSong(MusicPlayerRemote.getCurrentSong());",
                "playerAlbumCoverFragment.refreshCurrentSong();");
    }

    private static void assertInOrder(String source, String... snippets) {
        int searchFrom = 0;
        for (String snippet : snippets) {
            int index = source.indexOf(snippet, searchFrom);
            assertTrue("Expected snippet after index " + searchFrom + ": " + snippet, index >= 0);
            searchFrom = index + snippet.length();
        }
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
