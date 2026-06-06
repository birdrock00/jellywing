package com.dkanada.gramophone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class JellyfinServerStreamingSmokeTest {
    private static final String CLIENT_NAME = "Jellywing JVM smoke";
    private static final String CLIENT_VERSION = "1";

    private String server;
    private String username;
    private String password;

    @Before
    public void readRuntimeCredentials() {
        server = System.getProperty("jellyfinServer");
        username = System.getProperty("jellyfinUsername");
        password = System.getProperty("jellyfinPassword");

        assumeTrue("Pass Jellyfin credentials with -DjellyfinServer, -DjellyfinUsername, and -DjellyfinPassword",
                hasText(server) && hasText(username) && hasText(password));
    }

    @Test
    public void jellyfinServerAuthenticatesAndStreamsAudioBytes() throws Exception {
        JsonObject authentication = authenticate();
        String accessToken = authentication.get("AccessToken").getAsString();
        String userId = authentication.getAsJsonObject("User").get("Id").getAsString();

        JsonObject song = firstAudioItem(accessToken, userId);
        assertFalse("Jellyfin server must expose at least one audio item", song.get("Id").getAsString().isEmpty());

        int bytesRead = readStreamBytes(accessToken, song.get("Id").getAsString());
        assertTrue("Jellyfin audio stream should return media bytes", bytesRead > 1024);
    }

    private JsonObject authenticate() throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("Username", username);
        body.addProperty("Pw", password);

        return requestJson("POST", "/Users/AuthenticateByName", null, body.toString());
    }

    private JsonObject firstAudioItem(String accessToken, String userId) throws IOException {
        String path = "/Users/" + encode(userId) + "/Items"
                + "?Recursive=true&IncludeItemTypes=Audio&Limit=1&Fields=MediaSources";
        JsonObject response = requestJson("GET", path, accessToken, null);
        JsonArray items = response.getAsJsonArray("Items");

        assertTrue("Jellyfin server should return an Items array", items != null && items.size() > 0);
        return items.get(0).getAsJsonObject();
    }

    private int readStreamBytes(String accessToken, String itemId) throws IOException {
        String path = "/Audio/" + encode(itemId) + "/stream?static=true&api_key=" + encode(accessToken);
        HttpURLConnection connection = openConnection(path);
        connection.setRequestProperty("X-Emby-Token", accessToken);
        connection.setRequestProperty("Range", "bytes=0-4095");

        int status = connection.getResponseCode();
        assertTrue("Audio stream should return HTTP 200 or 206, got " + status, status == 200 || status == 206);

        try (InputStream inputStream = connection.getInputStream()) {
            byte[] buffer = new byte[4096];
            return inputStream.read(buffer);
        } finally {
            connection.disconnect();
        }
    }

    private JsonObject requestJson(String method, String path, String accessToken, String body) throws IOException {
        HttpURLConnection connection = openConnection(path);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Emby-Authorization", authorizationHeader());
        if (accessToken != null) {
            connection.setRequestProperty("X-Emby-Token", accessToken);
        }

        if (body != null) {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", Integer.toString(payload.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload);
            }
        }

        int status = connection.getResponseCode();
        String response = readResponse(connection);
        connection.disconnect();

        assertTrue("Jellyfin JSON request should return HTTP 2xx, got " + status,
                status >= 200 && status < 300);
        return new JsonParser().parse(response).getAsJsonObject();
    }

    private HttpURLConnection openConnection(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(trimTrailingSlash(server) + path).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private String readResponse(HttpURLConnection connection) throws IOException {
        InputStream inputStream = connection.getResponseCode() >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
        if (inputStream == null) {
            return "";
        }

        try (InputStream stream = inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String authorizationHeader() {
        return "MediaBrowser Client=\"" + CLIENT_NAME + "\", Device=\"JUnit\", DeviceId=\""
                + UUID.nameUUIDFromBytes(CLIENT_NAME.getBytes(StandardCharsets.UTF_8))
                + "\", Version=\"" + CLIENT_VERSION + "\"";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }
}
