package club.thatpetbff.gramophone.network;

import android.content.Context;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.interaction.VolleyErrorListener;
import org.jellyfin.apiclient.interaction.VolleyResponseListener;
import org.jellyfin.apiclient.interaction.http.HttpRequest;
import org.jellyfin.apiclient.interaction.http.IAsyncHttpClient;
import org.jellyfin.apiclient.logging.ILogger;

import android.util.Log;

import club.thatpetbff.gramophone.BuildConfig;

import java.io.File;

/**
 * Drop-in replacement for the bundled {@code VolleyHttpClient} that sends
 * Jellyfin API requests through {@link PatchedVolleyStringRequest}, adding the
 * standard {@code Authorization} header required by Jellyfin 12 while keeping
 * the legacy {@code X-Emby-Authorization} header for older servers.
 */
public class PatchedVolleyHttpClient implements IAsyncHttpClient {
    public static final String TAG = PatchedVolleyHttpClient.class.getSimpleName();

    private static final int DEFAULT_DISK_USAGE_BYTES = 100 * 1024 * 1024;

    private RequestQueue requestQueue;
    private final ILogger logger;
    private final Context context;

    public PatchedVolleyHttpClient(ILogger logger, Context context) {
        this.logger = logger;
        this.context = context;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            File cacheDir = context.getCacheDir();
            DiskBasedCache cache = new DiskBasedCache(cacheDir, DEFAULT_DISK_USAGE_BYTES);
            BasicNetwork network = new BasicNetwork(new HurlStack());
            requestQueue = new RequestQueue(cache, network);
            requestQueue.start();
        }

        return requestQueue;
    }

    public <T> void addToRequestQueue(Request<T> request) {
        logger.debug("Adding request to queue: %s", request.getUrl());
        getRequestQueue().add(request);
    }

    public void cancelPendingRequests(Object tag) {
        if (requestQueue != null) {
            requestQueue.cancelAll(tag);
        }
    }

    @Override
    public void Send(HttpRequest request, Response<String> response) {
        // URL only: request bodies may contain passwords, never log them.
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "sending " + request.getMethod() + " " + request.getUrl());
        }
        int method = Request.Method.GET;
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            method = Request.Method.POST;
        } else if ("DELETE".equalsIgnoreCase(request.getMethod())) {
            method = Request.Method.DELETE;
        }

        String url = request.getUrl();

        PatchedVolleyStringRequest stringRequest = new PatchedVolleyStringRequest(
                method,
                url,
                new VolleyResponseListener<>(response, logger, url),
                new LoggingErrorListener(url, new VolleyErrorListener<>(response, logger)),
                request);

        if (method != Request.Method.GET) {
            stringRequest.setShouldCache(false);
        }

        if (!request.getEnableCaching()) {
            stringRequest.setShouldCache(false);
        }

        stringRequest.setRetryPolicy(
                new DefaultRetryPolicy(request.getTimeout(), 0, 1f));

        addToRequestQueue(stringRequest);
    }

    /**
     * Diagnostic wrapper: records the HTTP status code and a short,
     * credential-free snippet of the failure body so login failures can be
     * diagnosed from logcat. Request bodies (which contain passwords) are
     * never logged.
     */
    private static class LoggingErrorListener implements com.android.volley.Response.ErrorListener {
        private final String url;
        private final com.android.volley.Response.ErrorListener delegate;

        LoggingErrorListener(String url, com.android.volley.Response.ErrorListener delegate) {
            this.url = url;
            this.delegate = delegate;
        }

        @Override
        public void onErrorResponse(com.android.volley.VolleyError error) {
            int status = -1;
            String snippet = "";
            if (error != null && error.networkResponse != null) {
                status = error.networkResponse.statusCode;
                byte[] data = error.networkResponse.data;
                if (data != null && data.length > 0) {
                    try {
                        snippet = new String(data, "UTF-8");
                        if (snippet.length() > 200) {
                            snippet = snippet.substring(0, 200);
                        }
                    } catch (Exception ignored) {
                        snippet = "<undecodable>";
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "request failed: " + url + " status=" + status
                        + " error=" + (error != null ? error.getClass().getSimpleName() : "null")
                        + " body=" + snippet);
            }
            delegate.onErrorResponse(error);
        }
    }
}
