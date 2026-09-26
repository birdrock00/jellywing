package club.thatpetbff.gramophone.util;

import java.util.Map;

/**
 * Makes the bundled jellyfin-apiclient-java 0.7.3 talk to Jellyfin 12.
 *
 * <ul>
 *   <li>Jellyfin 12 removed support for the legacy {@code X-Emby-Authorization}
 *       header: authentication-info requests (notably
 *       {@code Users/AuthenticateByName}) fail with {@code 400} and
 *       {@code request.App} null unless the client sends the standard
 *       {@code Authorization} header instead. Browsers send
 *       {@code Authorization}, which is why the same server works in Fennec
 *       while the app fails.</li>
 *   <li>Authenticated calls after login send the access token only as
 *       {@code X-MediaBrowser-Token}, which Jellyfin 12 challenges with
 *       {@code 401}. The canonical modern form carries the token inside the
 *       {@code Authorization} header
 *       ({@code MediaBrowser Client="...", ..., Token="..."}), so the token is
 *       merged into {@code Authorization} as well.</li>
 * </ul>
 *
 * <p>Legacy headers are always kept, so older servers keep working.
 */
public final class AuthHeaderFix {
    public static final String LEGACY_AUTHORIZATION_HEADER = "X-Emby-Authorization";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String TOKEN_HEADER = "X-MediaBrowser-Token";

    private AuthHeaderFix() {
    }

    public static void apply(Map<String, String> headers) {
        if (headers == null) {
            return;
        }

        if (headers.containsKey(AUTHORIZATION_HEADER)) {
            return;
        }

        String legacy = headers.get(LEGACY_AUTHORIZATION_HEADER);
        String token = headers.get(TOKEN_HEADER);

        if (legacy == null || legacy.isEmpty()) {
            // Token-only state (e.g. right after SetAuthenticationInfo) would
            // otherwise 401 on Jellyfin 12; synthesize minimal modern auth.
            if (token != null && !token.isEmpty()) {
                headers.put(AUTHORIZATION_HEADER,
                        "MediaBrowser Token=\"" + token.replace("\"", "") + "\"");
            }
            return;
        }

        if (token != null && !token.isEmpty() && !legacy.contains("Token=")) {
            legacy = legacy + ", Token=\"" + token.replace("\"", "") + "\"";
        }

        headers.put(AUTHORIZATION_HEADER, legacy);
    }
}
