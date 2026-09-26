package club.thatpetbff.gramophone.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds HTTP headers for ExoPlayer media requests against Jellyfin 12.
 *
 * <p>Volley API calls were fixed to send the canonical {@code Authorization}
 * header (see {@link AuthHeaderFix}), but ExoPlayer fetches
 * {@code /Audio/.../universal} directly via its own DataSource, bypassing
 * Volley entirely. Without {@code Authorization}, Jellyfin 12 challenges the
 * stream with {@code 401} and the player sits in {@code STATE_BUFFERING}
 * forever (spinner, never plays).
 *
 * <p>Header VALUES contain secrets and must never be logged; only header
 * names may appear in logs.
 */
public final class PlaybackAuthHeaders {
    public static final String AUTHORIZATION = "Authorization";
    public static final String TOKEN_HEADER = "X-MediaBrowser-Token";
    public static final String LEGACY_AUTHORIZATION = "X-Emby-Authorization";

    private PlaybackAuthHeaders() {
    }

    /**
     * Build playback headers from discrete client fields so callers do not
     * need an Android {@code ApiClient} instance (unit-testable on the JVM).
     */
    public static Map<String, String> build(String clientName,
                                            String deviceName,
                                            String deviceId,
                                            String appVersion,
                                            String accessToken) {
        String authParam = buildAuthParam(clientName, deviceName, deviceId, appVersion, accessToken);
        if (authParam == null || authParam.isEmpty()) {
            return Collections.emptyMap();
        }

        String authValue = "MediaBrowser " + authParam;
        Map<String, String> headers = new HashMap<>(3);
        headers.put(AUTHORIZATION, authValue);
        headers.put(LEGACY_AUTHORIZATION, authValue);
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.put(TOKEN_HEADER, accessToken);
        }
        return Collections.unmodifiableMap(headers);
    }

    static String buildAuthParam(String clientName,
                                 String deviceName,
                                 String deviceId,
                                 String appVersion,
                                 String accessToken) {
        StringBuilder param = new StringBuilder();
        appendQuoted(param, "Client", clientName);
        appendQuoted(param, "Device", deviceName);
        appendQuoted(param, "DeviceId", deviceId);
        appendQuoted(param, "Version", appVersion);
        appendQuoted(param, "Token", accessToken);
        return param.toString();
    }

    private static void appendQuoted(StringBuilder out, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append(", ");
        }
        out.append(key).append("=\"").append(value.replace("\"", "")).append("\"");
    }
}
