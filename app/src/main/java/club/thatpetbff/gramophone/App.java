package club.thatpetbff.gramophone;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import androidx.room.Room;

import club.thatpetbff.gramophone.database.JellyDatabase;
import club.thatpetbff.gramophone.helper.EventListener;
import club.thatpetbff.gramophone.model.User;
import club.thatpetbff.gramophone.network.PatchedVolleyHttpClient;
import club.thatpetbff.gramophone.util.PreferenceUtil;
import club.thatpetbff.gramophone.util.ServerUrlUtil;
import club.thatpetbff.gramophone.views.shortcuts.DynamicShortcutManager;
import com.melegy.redscreenofdeath.RedScreenOfDeath;

import org.jellyfin.apiclient.interaction.AndroidDevice;
import org.jellyfin.apiclient.interaction.ApiClient;
import org.jellyfin.apiclient.interaction.device.IDevice;
import org.jellyfin.apiclient.interaction.http.IAsyncHttpClient;
import org.jellyfin.apiclient.logging.AndroidLogger;
import org.jellyfin.apiclient.logging.ILogger;

public class App extends Application {
    private static App app;

    private static JellyDatabase database;
    private static ApiClient apiClient;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            RedScreenOfDeath.init(this);
        }

        app = this;
        database = createDatabase(this);
        apiClient = createApiClient(this);

        if (database.userDao().getUsers().size() == 0) {
            PreferenceUtil.getInstance(this).setServer(null);
            PreferenceUtil.getInstance(this).setUser(null);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            new DynamicShortcutManager(this).initDynamicShortcuts();
        }
    }

    public static JellyDatabase createDatabase(Context context) {
        return Room.databaseBuilder(context, JellyDatabase.class, "database")
                .allowMainThreadQueries()
                .addMigrations(JellyDatabase.Migration2)
                .addMigrations(JellyDatabase.Migration3)
                .addMigrations(JellyDatabase.Migration4)
                .addMigrations(JellyDatabase.Migration5)
                .addMigrations(JellyDatabase.Migration6)
                .addMigrations(JellyDatabase.Migration7)
                .build();
    }

    public static ApiClient createApiClient(Context context) {
        String appName = context.getString(R.string.app_name);
        String appVersion = BuildConfig.VERSION_NAME;

        @SuppressLint("HardwareIds")
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceName = android.os.Build.MODEL;
        String server = ServerUrlUtil.normalize(PreferenceUtil.getInstance(context).getServer());

        ILogger logger = new AndroidLogger(context.getClass().getName());
        IAsyncHttpClient httpClient = new PatchedVolleyHttpClient(logger, context);
        IDevice device = new AndroidDevice(deviceId, deviceName);
        EventListener eventListener = new EventListener();

        return new ApiClient(httpClient, logger, server, appName, appVersion, device, eventListener);
    }

    public static JellyDatabase getDatabase() {
        return database;
    }

    public static ApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Rehydrates the in-memory ApiClient with the saved user's credentials when
     * it is not authenticated yet.
     *
     * <p>A fresh process creates the client without a token; the token only
     * arrives when {@code LoginService}'s async startup chain runs. If the user
     * taps play before that completes, stream URLs and ExoPlayer request headers
     * are built unauthenticated and the server rejects them (Jellyfin 12
     * returns 401), so playback dies a few seconds in. Restoring from the local
     * Room DB is synchronous and involves no network I/O, so it is safe to run
     * on any thread right before a stream request is built.
     */
    public static void ensureAuthenticatedApiClient() {
        if (apiClient == null || database == null || app == null) {
            return;
        }
        String accessToken = apiClient.getAccessToken();
        String currentUserId = apiClient.getCurrentUserId();
        if ((accessToken != null && !accessToken.isEmpty())
                || (currentUserId != null && !currentUserId.isEmpty())) {
            return;
        }
        String userName = PreferenceUtil.getInstance(app).getUser();
        if (userName == null) {
            return;
        }
        User user = database.userDao().getUser(userName);
        if (user == null || user.token == null) {
            return;
        }
        apiClient.ChangeServerLocation(ServerUrlUtil.normalize(user.server));
        apiClient.SetAuthenticationInfo(user.token, user.id);
    }

    public static App getInstance() {
        return app;
    }
}
