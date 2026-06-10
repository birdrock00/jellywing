package club.thatpetbff.gramophone.player;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RepeatButtonVisibilityRegressionTest {
    @Test
    public void cardPlayerInitializesControlColorsBeforeRepeatStateCanRender() throws IOException {
        assertInitializesControlColorsBeforeControllerSetup(
                "app/src/main/java/club/thatpetbff/gramophone/fragments/player/card/CardPlayerPlaybackControlsFragment.java");
    }

    @Test
    public void flatPlayerInitializesControlColorsBeforeRepeatStateCanRender() throws IOException {
        assertInitializesControlColorsBeforeControllerSetup(
                "app/src/main/java/club/thatpetbff/gramophone/fragments/player/flat/FlatPlayerPlaybackControlsFragment.java");
    }

    @Test
    public void repeatButtonRemainsInBothPlayerControlLayouts() throws IOException {
        assertRepeatButtonLayout("app/src/main/res/layout/fragment_card_player_playback_controls.xml");
        assertRepeatButtonLayout("app/src/main/res/layout/fragment_flat_player_playback_controls.xml");
    }

    private static void assertInitializesControlColorsBeforeControllerSetup(String relativePath) throws IOException {
        String source = readProjectFile(relativePath);
        int initializeCall = source.indexOf("initializePlaybackControlColors();");
        int setupCall = source.indexOf("setUpMusicControllers();");

        assertTrue("Playback control colors must be initialized before controls are tinted", initializeCall >= 0);
        assertTrue("Playback controls must still be set up", setupCall >= 0);
        assertTrue("Playback control colors must be initialized before controls are tinted", initializeCall < setupCall);
        assertTrue(
                "Disabled repeat tint must have a visible fallback when no artwork palette arrives",
                source.contains("lastDisabledPlaybackControlsColor = ThemeUtil.getColorAlpha(requireContext(), R.color.color_text_primary_dark, 180);"));
    }

    private static void assertRepeatButtonLayout(String relativePath) throws IOException {
        String layout = readProjectFile(relativePath);

        assertTrue(layout.contains("android:id=\"@+id/player_repeat_button\""));
        assertTrue(layout.contains("app:srcCompat=\"@drawable/ic_repeat_white_24dp\""));
    }

    @Test
    public void repeatOneIconContainsVisibleOneGlyph() throws IOException {
        String drawable = readProjectFile("app/src/main/res/drawable/ic_repeat_one_white_24dp.xml");

        assertTrue(drawable.contains("M13,15"));
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
