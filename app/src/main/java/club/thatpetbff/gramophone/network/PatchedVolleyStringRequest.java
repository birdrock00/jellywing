package club.thatpetbff.gramophone.network;

import com.android.volley.Response;

import org.jellyfin.apiclient.interaction.VolleyStringRequest;
import org.jellyfin.apiclient.interaction.http.HttpRequest;

import android.util.Log;

import club.thatpetbff.gramophone.util.AuthHeaderFix;

import java.util.Map;

/**
 * Same request as the bundled {@code VolleyStringRequest}, but mirrors the
 * legacy {@code X-Emby-Authorization} header into the standard
 * {@code Authorization} header required by Jellyfin 12.
 */
public class PatchedVolleyStringRequest extends VolleyStringRequest {
    public PatchedVolleyStringRequest(int method,
                                      String url,
                                      Response.Listener<String> listener,
                                      Response.ErrorListener errorListener,
                                      HttpRequest request) {
        super(method, url, listener, errorListener, request);
    }

    @Override
    public Map<String, String> getHeaders() throws com.android.volley.AuthFailureError {
        Map<String, String> headers = super.getHeaders();
        AuthHeaderFix.apply(headers);
        if (club.thatpetbff.gramophone.BuildConfig.DEBUG) {
            // Header NAMES only: values may contain tokens, never log them.
            Log.d(PatchedVolleyHttpClient.TAG,
                    "outgoing headers for " + getUrl() + ": " + headers.keySet());
        }
        return headers;
    }
}
