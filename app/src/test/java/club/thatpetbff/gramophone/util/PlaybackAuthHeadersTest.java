package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class PlaybackAuthHeadersTest {
    @Test
    public void buildsAuthorizationWithToken() {
        Map<String, String> headers = PlaybackAuthHeaders.build(
                "Jellywing", "Pixel", "device-123", "1.3.5", "secret-token");

        String auth = headers.get(PlaybackAuthHeaders.AUTHORIZATION);
        assertTrue(auth.startsWith("MediaBrowser "));
        assertTrue(auth.contains("Client=\"Jellywing\""));
        assertTrue(auth.contains("Device=\"Pixel\""));
        assertTrue(auth.contains("DeviceId=\"device-123\""));
        assertTrue(auth.contains("Version=\"1.3.5\""));
        assertTrue(auth.contains("Token=\"secret-token\""));

        // legacy + token headers kept for older servers
        assertEquals(auth, headers.get(PlaybackAuthHeaders.LEGACY_AUTHORIZATION));
        assertEquals("secret-token", headers.get(PlaybackAuthHeaders.TOKEN_HEADER));
    }

    @Test
    public void omitsTokenWhenAbsent() {
        Map<String, String> headers = PlaybackAuthHeaders.build(
                "Jellywing", "Pixel", "device-123", "1.3.5", null);

        String auth = headers.get(PlaybackAuthHeaders.AUTHORIZATION);
        assertFalse(auth.contains("Token="));
        assertFalse(headers.containsKey(PlaybackAuthHeaders.TOKEN_HEADER));
    }

    @Test
    public void emptyClientInfoYieldsNoHeaders() {
        Map<String, String> headers = PlaybackAuthHeaders.build(null, null, null, null, null);
        assertTrue(headers.isEmpty());
    }

    @Test
    public void stripsQuotesToPreventHeaderInjection() {
        Map<String, String> headers = PlaybackAuthHeaders.build(
                "Jelly\"wing", "Pi\"xel", "id", "1", "tok\"en");
        String auth = headers.get(PlaybackAuthHeaders.AUTHORIZATION);
        assertFalse(auth.contains("\"wing\"") && auth.contains("Jelly\"wing"));
        assertTrue(auth.contains("Jellywing"));
    }
}
