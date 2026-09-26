package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AuthHeaderFixTest {
    @Test
    public void mirrorsLegacyHeaderToAuthorization() {
        Map<String, String> headers = new HashMap<>();
        headers.put(AuthHeaderFix.LEGACY_AUTHORIZATION_HEADER,
                "MediaBrowser Client=\"Jellywing\", DeviceId=\"id\", Device=\"dev\", Version=\"1.3.5\"");

        AuthHeaderFix.apply(headers);

        assertEquals("MediaBrowser Client=\"Jellywing\", DeviceId=\"id\", Device=\"dev\", Version=\"1.3.5\"",
                headers.get(AuthHeaderFix.AUTHORIZATION_HEADER));
        // legacy header is kept for older servers
        assertEquals("MediaBrowser Client=\"Jellywing\", DeviceId=\"id\", Device=\"dev\", Version=\"1.3.5\"",
                headers.get(AuthHeaderFix.LEGACY_AUTHORIZATION_HEADER));
    }

    @Test
    public void doesNotOverwriteExistingAuthorization() {
        Map<String, String> headers = new HashMap<>();
        headers.put(AuthHeaderFix.LEGACY_AUTHORIZATION_HEADER, "MediaBrowser Client=\"Old\"");
        headers.put(AuthHeaderFix.AUTHORIZATION_HEADER, "MediaBrowser Token=\"abc\"");

        AuthHeaderFix.apply(headers);

        assertEquals("MediaBrowser Token=\"abc\"", headers.get(AuthHeaderFix.AUTHORIZATION_HEADER));
    }

    @Test
    public void noLegacyHeaderLeavesMapUntouched() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        AuthHeaderFix.apply(headers);

        assertEquals(1, headers.size());
        assertNull(headers.get(AuthHeaderFix.AUTHORIZATION_HEADER));
    }

    @Test
    public void tokenOnlyMapSynthesizesAuthorization() {
        Map<String, String> headers = new HashMap<>();
        headers.put(AuthHeaderFix.TOKEN_HEADER, "only-token");

        AuthHeaderFix.apply(headers);

        assertEquals("MediaBrowser Token=\"only-token\"",
                headers.get(AuthHeaderFix.AUTHORIZATION_HEADER));
    }

    @Test
    public void nullMapIsIgnored() {
        AuthHeaderFix.apply(null);
    }

    @Test
    public void mergesAccessTokenIntoAuthorization() {
        Map<String, String> headers = new HashMap<>();
        headers.put(AuthHeaderFix.LEGACY_AUTHORIZATION_HEADER,
                "MediaBrowser Client=\"Jellywing\", DeviceId=\"id\", Device=\"dev\", Version=\"1.3.5\"");
        headers.put(AuthHeaderFix.TOKEN_HEADER, "test-token");

        AuthHeaderFix.apply(headers);

        assertEquals(
                "MediaBrowser Client=\"Jellywing\", DeviceId=\"id\", Device=\"dev\", Version=\"1.3.5\", Token=\"test-token\"",
                headers.get(AuthHeaderFix.AUTHORIZATION_HEADER));
        // legacy headers are kept for older servers
        assertEquals("test-token", headers.get(AuthHeaderFix.TOKEN_HEADER));
    }

    @Test
    public void doesNotDuplicateTokenAlreadyPresent() {
        Map<String, String> headers = new HashMap<>();
        headers.put(AuthHeaderFix.LEGACY_AUTHORIZATION_HEADER,
                "MediaBrowser Client=\"Jellywing\", Token=\"test-token\"");
        headers.put(AuthHeaderFix.TOKEN_HEADER, "test-token");

        AuthHeaderFix.apply(headers);

        assertEquals("MediaBrowser Client=\"Jellywing\", Token=\"test-token\"",
                headers.get(AuthHeaderFix.AUTHORIZATION_HEADER));
    }

    @Test
    public void noTokenStillMirrorsAuthInfo() {
        Map<String, String> headers = new HashMap<>();
        headers.put(AuthHeaderFix.LEGACY_AUTHORIZATION_HEADER, "MediaBrowser Client=\"Jellywing\"");

        AuthHeaderFix.apply(headers);

        assertEquals("MediaBrowser Client=\"Jellywing\"",
                headers.get(AuthHeaderFix.AUTHORIZATION_HEADER));
    }
}
