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
 * Regression tests for "music suddenly stopped".
 *
 * Two distinct failure modes have hit playback:
 *
 *  1. (2026-08-27) MusicService.play() blindly re-started its own service
 *     from the background; Android 12+ denied the background FGS start
 *     ("Background started FGS: Disallowed"), and the repeated denial/retry
 *     loop pegged the CPU until the watchdog killed the process for
 *     EXCESSIVE CPU USAGE. LocalPlayer.onPlayerError additionally wiped the
 *     entire queue, dead-ending the player. Both were fixed: play() no
 *     longer startForegroundService()s itself, and onPlayerError skips to
 *     the next item.
 *
 *  2. (2026-10-14, device log 16:05) the service is only ever created by
 *     activity BIND_AUTO_CREATE bindings. When the app's task goes away
 *     (press Home on task-destroying OEM skins), the final unbind destroys
 *     a bound-only service and onDestroy() releases ExoPlayer - playback
 *     died a few seconds into the song. The fix promotes the service to the
 *     "started" lifecycle with a plain self startService() while playing.
 *     Unlike the old startForegroundService() retry loop, a self start on
 *     an already-running service is allowed from any app state, is guarded
 *     by try/catch, and happens at most once per playback start - so no
 *     watchdog CPU burn can recur.
 */
public class PlaybackForegroundServiceRegressionTest {

    @Test
    public void playbackPromotesItsOwnServiceToStartedState() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/service/MusicService.java");

        assertTrue("playback must promote the service to the started lifecycle so a "
                + "bound-only service is not destroyed when the app's task goes away",
                source.contains("ensureStartedForPlayback"));
        assertTrue("the self-start must be a plain startService on the running service",
                source.contains("startService(new Intent(this, MusicService.class));"));
        assertFalse("the self-start must never use startForegroundService (that is what "
                + "triggered the background-FGS denial loop and the watchdog kill)",
                source.contains("startForegroundService(new Intent(this, MusicService.class)"));
        assertTrue("denied self-starts must be swallowed, never retried in a loop",
                source.contains("catch (Exception ignored)"));
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
