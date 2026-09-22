package club.thatpetbff.gramophone.util;

/**
 * Normalizes user-entered Jellyfin server addresses.
 *
 * <p>Browsers auto-prepend {@code https://} when the user types a bare hostname,
 * but {@code ApiClient.ChangeServerLocation} passes the raw string to Volley.
 * A bare hostname produces
 * {@code MalformedURLException: no protocol} which surfaces as
 * "Unable to reach server at this address" while the same hostname works fine
 * in Fennec. Centralize the fix here and apply it at every server-address
 * boundary (login input, stored prefs, saved users).
 */
public final class ServerUrlUtil {
    private ServerUrlUtil() {
    }

    public static String normalize(String server) {
        if (server == null) {
            return null;
        }

        String trimmed = server.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        if (!trimmed.regionMatches(true, 0, "http://", 0, 7)
                && !trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            trimmed = "https://" + trimmed;
        }

        while (trimmed.endsWith("/") && trimmed.length() > 0) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }
}
