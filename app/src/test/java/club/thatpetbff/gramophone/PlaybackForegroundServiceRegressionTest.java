package club.thatpetbff.gramophone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Regression tests for "music suddenly stopped" caused by the Android
 * watchdog killing jellywing for EXCESSIVE CPU USAGE
 * (ApplicationExitInfo reason=9 subreason=7, e.g. 2026-08-27 14:11:06).
 *
 * The CPU burn came from two cooperating flaws:
 *  1. MusicService.play() blindly restarted its own service from the
 *     background; Android 12+ denies background service starts
 *     ("Background started FGS: Disallowed"), and the repeated
 *     denial/retry loop pegged the CPU until the process was killed.
 *  2. LocalPlayer.onPlayerError wiped the entire queue
 *     (clearMediaItems + prepare), dead-ending the player and
 *     triggering more background foreground-service churn.
 */
public class PlaybackForegroundServiceRegressionTest {

    @Test
    public void playbackDoesNotRestartItsOwnServiceFromBackground() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/service/MusicService.java");

        assertFalse("play() must not blind-start the service it lives in",
                source.contains("startService(new Intent(this, MusicService.class));"));
        assertTrue("the redundant self-restart must be documented as a deliberate no-op",
                source.contains("ensureStartedForPlayback"));
    }

    @Test
    public void playerErrorSkipsToNextItemInsteadOfClearingTheQueue() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/service/playback/LocalPlayer.java");

        assertTrue("onPlayerError must skip to the next playable item",
                source.contains("exoPlayer.seekToNextMediaItem();"));
        assertFalse("onPlayerError must not wipe the queue with an immediately-following prepare",
                source.contains("exoPlayer.clearMediaItems();\n            exoPlayer.prepare();"));
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
