package com.dkanada.gramophone.player;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NowPlayingVisualRegressionTest {
    @Test
    public void fullPlayerAlbumCoverDoesNotDuplicateCurrentSongTitleAndSubtitle() throws IOException {
        String layout = readProjectFile("app/src/main/res/layout/fragment_player_album_cover.xml");
        String source = readProjectFile("app/src/main/java/com/dkanada/gramophone/fragments/player/PlayerAlbumCoverFragment.java");

        assertTrue(layout.contains("android:id=\"@+id/player_song_info\""));
        assertTrue(layout.contains("android:id=\"@+id/player_song_title\""));
        assertTrue(layout.contains("android:id=\"@+id/player_song_subtitle\""));
        assertTrue(source.contains("binding.playerSongInfo.setVisibility(View.GONE);"));
        assertTrue(source.contains("updateCurrentSongText();"));
    }

    @Test
    public void playbackControlsShowCurrentSongTitleOnVisibleScreen() throws IOException {
        assertPlaybackControlsCurrentSongText("app/src/main/res/layout/fragment_card_player_playback_controls.xml",
                "app/src/main/java/com/dkanada/gramophone/fragments/player/card/CardPlayerPlaybackControlsFragment.java");
        assertPlaybackControlsCurrentSongText("app/src/main/res/layout/fragment_flat_player_playback_controls.xml",
                "app/src/main/java/com/dkanada/gramophone/fragments/player/flat/FlatPlayerPlaybackControlsFragment.java");
    }

    @Test
    public void fullPlayerAlbumCoverUsesFitCenterSoArtworkIsNotCropped() throws IOException {
        String layout = readProjectFile("app/src/main/res/layout/fragment_album_cover.xml");

        assertTrue(layout.contains("android:id=\"@+id/player_image\""));
        assertTrue(layout.contains("android:scaleType=\"fitCenter\""));
    }

    @Test
    public void currentSongQueueRowUsesFitCenterArtworkInBothPlayers() throws IOException {
        assertCurrentSongArtworkFitCenter("app/src/main/java/com/dkanada/gramophone/fragments/player/card/CardPlayerFragment.java");
        assertCurrentSongArtworkFitCenter("app/src/main/java/com/dkanada/gramophone/fragments/player/flat/FlatPlayerFragment.java");
    }

    @Test
    public void fullPlayerDoesNotRenderDuplicateCurrentSongRowsInsideUpNext() throws IOException {
        assertCurrentSongRowHidden("app/src/main/java/com/dkanada/gramophone/fragments/player/card/CardPlayerFragment.java");
        assertCurrentSongRowHidden("app/src/main/java/com/dkanada/gramophone/fragments/player/flat/FlatPlayerFragment.java");
    }

    private static void assertCurrentSongArtworkFitCenter(String relativePath) throws IOException {
        String source = readProjectFile(relativePath);

        assertTrue(source.contains("currentSongViewHolder.title.setText(MusicUtil.getSongTitle(song));"));
        assertTrue(source.contains("currentSongViewHolder.image.setScaleType(ImageView.ScaleType.FIT_CENTER);"));
        assertTrue(source.contains("CustomGlideRequest.Builder"));
        assertTrue(source.contains("song.getArtworkItemId()"));
        assertTrue(source.contains(".into(currentSongViewHolder.image);"));
        assertTrue(source.contains("playerAlbumCoverFragment.refreshCurrentSong();"));
    }

    private static void assertPlaybackControlsCurrentSongText(String layoutPath, String sourcePath) throws IOException {
        String layout = readProjectFile(layoutPath);
        String source = readProjectFile(sourcePath);

        assertTrue(layout.contains("android:id=\"@+id/player_song_text_container\""));
        assertTrue(layout.contains("android:id=\"@+id/player_song_title\""));
        assertTrue(source.contains("updateCurrentSongText();"));
        assertTrue(source.contains("binding.playerSongTitle.setText(MusicUtil.getSongTitle(MusicPlayerRemote.getCurrentSong()));"));
    }

    private static void assertCurrentSongRowHidden(String sourcePath) throws IOException {
        String source = readProjectFile(sourcePath);

        assertTrue(source.contains("currentSongViewHolder.itemView.setVisibility(View.GONE);"));
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
