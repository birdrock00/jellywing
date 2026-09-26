package club.thatpetbff.gramophone.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Playback spinning (tap play, infinite spinner, never reaches READY) was
 * caused by ExoPlayer fetching /Audio/.../universal without the Jellyfin 12
 * {@code Authorization} header. Volley API calls already send it via
 * PatchedVolleyStringRequest, but LocalPlayer's DataSource did not.
 */
public class PlaybackAuthHeadersRegressionTest {
    @Test
    public void localPlayerSendsAuthorizationHeaders() throws IOException {
        String source = readProjectFile(
                "app/src/main/java/club/thatpetbff/gramophone/service/playback/LocalPlayer.java");
        assertTrue("ExoPlayer HTTP factory must set default request properties",
                source.contains("setDefaultRequestProperties"));
        assertTrue("LocalPlayer must build playback auth headers",
                source.contains("PlaybackAuthHeaders.build"));
        assertTrue("LocalPlayer must use an authenticated HTTP factory",
                source.contains("DefaultHttpDataSource.Factory"));
    }

    @Test
    public void transcodeContainerUsesContainerNamesNotPipeValues() throws IOException {
        String source = readProjectFile(
                "app/src/main/java/club/thatpetbff/gramophone/util/MusicUtil.java");
        assertFalse("Container param must not send Codec.value with pipes (400 on Jellyfin 12)",
                source.contains("codec.value"));
        assertTrue("Container param must be built from container names",
                source.contains("codec.container"));
    }

    @Test
    public void streamUrlsUseModernApiKey() throws IOException {
        String source = readProjectFile(
                "app/src/main/java/club/thatpetbff/gramophone/util/MusicUtil.java");
        assertTrue("Jellyfin 12 requires ?ApiKey= (capital A/K)",
                source.contains("?ApiKey=") || source.contains("&ApiKey="));
        assertFalse("legacy ?api_key= 401s on Jellyfin 12",
                source.contains("?api_key=") || source.contains("&api_key="));
    }

    @Test
    public void directPlayCheckIsNullSafe() throws IOException {
        String source = readProjectFile(
                "app/src/main/java/club/thatpetbff/gramophone/service/playback/LocalPlayer.java");
        assertTrue("direct-play check must be extracted for null-safety",
                source.contains("isDirectPlay("));
    }

    @Test
    public void playbackLogsNeverIncludeStreamUrls() throws IOException {
        String source = readProjectFile(
                "app/src/main/java/club/thatpetbff/gramophone/service/playback/LocalPlayer.java");
        assertFalse("MediaItem.toString() renders ?ApiKey=<token>; must not be logged",
                source.contains("onMediaItemTransition: %s"));
        assertTrue("transition log must use redacted media ID",
                source.contains("onMediaItemTransition: id="));
    }

    @Test
    public void playbackHeadersRefreshPerRequest() throws IOException {
        String source = readProjectFile(
                "app/src/main/java/club/thatpetbff/gramophone/service/playback/LocalPlayer.java");
        // setDefaultRequestProperties must be inside the DataSource lambda so
        // token refresh / re-login takes effect without process restart.
        int factoryIndex = source.indexOf("private DataSource.Factory buildDataSourceFactory()");
        int lambdaIndex = source.indexOf("return () ->", factoryIndex);
        int headersIndex = source.indexOf("setDefaultRequestProperties", factoryIndex);
        assertTrue("headers must be set inside the per-request DataSource lambda",
                lambdaIndex >= 0 && headersIndex > lambdaIndex);
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
