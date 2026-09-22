package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ServerUrlUtilTest {
    @Test
    public void bareHostnameGetsHttpsScheme() {
        assertEquals("https://jellyfin.example.com", ServerUrlUtil.normalize("jellyfin.example.com"));
    }

    @Test
    public void httpsUrlIsPreserved() {
        assertEquals("https://jellyfin.example.com", ServerUrlUtil.normalize("https://jellyfin.example.com"));
    }

    @Test
    public void httpUrlIsPreservedForLocalCleartext() {
        assertEquals("http://192.0.2.10:8096", ServerUrlUtil.normalize("http://192.0.2.10:8096"));
    }

    @Test
    public void whitespaceAndTrailingSlashAreRemoved() {
        assertEquals("https://jellyfin.example.com", ServerUrlUtil.normalize("  jellyfin.example.com/  "));
        assertEquals("https://jellyfin.example.com", ServerUrlUtil.normalize("https://jellyfin.example.com///"));
    }

    @Test
    public void nullIsPreserved() {
        assertNull(ServerUrlUtil.normalize(null));
    }
}
