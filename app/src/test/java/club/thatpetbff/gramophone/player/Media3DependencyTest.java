package club.thatpetbff.gramophone.player;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class Media3DependencyTest {
    @Test
    public void hlsMediaSourceFactoryIsAvailable() throws Exception {
        assertNotNull(Class.forName("androidx.media3.exoplayer.hls.HlsMediaSource$Factory"));
    }
}
