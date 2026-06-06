package com.dkanada.gramophone.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.activities.LoginActivity;
import com.dkanada.gramophone.helper.MusicPlayerRemote;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.model.User;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.QueryUtil;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.querying.ItemQuery;
import org.jellyfin.apiclient.model.users.AuthenticationResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class JellyfinPlaybackSmokeTest {
    private static final long NETWORK_TIMEOUT_MS = 90_000;
    private static final long PLAYBACK_TIMEOUT_MS = 60_000;

    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    private String server;
    private String username;
    private String password;

    @Before
    public void readRuntimeCredentials() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        server = arguments.getString("jellyfinServer");
        username = arguments.getString("jellyfinUsername");
        password = arguments.getString("jellyfinPassword");

        assumeTrue("Pass -e jellyfinServer, -e jellyfinUsername, and -e jellyfinPassword to run playback smoke",
                hasText(server) && hasText(username) && hasText(password));
    }

    @Test
    public void logsIntoJellyfinAndStartsSongPlayback() throws Exception {
        AuthenticationResult authentication = authenticate();
        User user = new User(authentication, server);

        App.getDatabase().userDao().insertUser(user);
        PreferenceUtil.getInstance(App.getInstance()).setServer(user.server);
        PreferenceUtil.getInstance(App.getInstance()).setUser(user.id);
        App.getApiClient().SetAuthenticationInfo(user.token, user.id);

        QueryUtil.currentLibrary = findMusicLibrary();
        List<Song> songs = loadSongs();
        Song song = firstPlayableSong(songs);
        assertNotNull("Jellyfin library did not return a playable song", song);

        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        AtomicReference<MusicPlayerRemote.ServiceToken> tokenRef = new AtomicReference<>();

        try {
            CountDownLatch serviceConnected = new CountDownLatch(1);
            scenario.onActivity(activity -> tokenRef.set(MusicPlayerRemote.bindToService(activity, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    serviceConnected.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            })));

            assertTrueWithin("music service binds", serviceConnected, 15_000);

            List<Song> queue = new ArrayList<>();
            queue.add(song);
            scenario.onActivity(activity -> MusicPlayerRemote.openQueue(queue, 0, true));

            waitUntil("Media3 playback starts streaming a Jellyfin song", PLAYBACK_TIMEOUT_MS, () ->
                    MusicPlayerRemote.getCurrentSong() != null
                            && MusicPlayerRemote.isPlaying()
                            && MusicPlayerRemote.getSongDurationMillis() > 0
                            && MusicPlayerRemote.getSongProgressMillis() > 0);
        } finally {
            MusicPlayerRemote.pauseSong();
            MusicPlayerRemote.unbindFromService(tokenRef.get());
            scenario.close();
        }
    }

    private AuthenticationResult authenticate() throws InterruptedException {
        long deadline = System.currentTimeMillis() + NETWORK_TIMEOUT_MS;
        Throwable lastFailure = null;

        while (System.currentTimeMillis() < deadline) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AuthenticationResult> resultRef = new AtomicReference<>();
            AtomicReference<Exception> errorRef = new AtomicReference<>();

            App.getApiClient().ChangeServerLocation(server);
            App.getApiClient().AuthenticateUserAsync(username, password, new Response<AuthenticationResult>() {
                @Override
                public void onResponse(AuthenticationResult result) {
                    resultRef.set(result);
                    latch.countDown();
                }

                @Override
                public void onError(Exception exception) {
                    errorRef.set(exception);
                    latch.countDown();
                }
            });

            if (!latch.await(15_000, TimeUnit.MILLISECONDS)) {
                lastFailure = new AssertionError("Timed out waiting for Jellyfin authentication attempt");
            } else if (errorRef.get() == null) {
                AuthenticationResult result = resultRef.get();
                assertNotNull("Jellyfin authentication returned no result", result);
                assertNotNull("Jellyfin authentication returned no access token", result.getAccessToken());
                return result;
            } else {
                lastFailure = errorRef.get();
            }

            Thread.sleep(2_000);
        }

        throw new AssertionError("Jellyfin authentication failed", lastFailure);
    }

    private BaseItemDto findMusicLibrary() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<BaseItemDto>> librariesRef = new AtomicReference<>();

        QueryUtil.getLibraries(libraries -> {
            librariesRef.set(libraries);
            latch.countDown();
        });

        assertTrueWithin("Jellyfin libraries load", latch, NETWORK_TIMEOUT_MS);
        List<BaseItemDto> libraries = librariesRef.get();
        assertNotNull("Jellyfin returned no libraries", libraries);
        assertFalse("Jellyfin returned an empty library list", libraries.isEmpty());

        for (BaseItemDto library : libraries) {
            if ("music".equals(library.getCollectionType())) {
                return library;
            }
        }

        return libraries.get(0);
    }

    private List<Song> loadSongs() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Song>> songsRef = new AtomicReference<>();

        QueryUtil.getSongs(new ItemQuery(), songs -> {
            songsRef.set(songs);
            latch.countDown();
        });

        assertTrueWithin("Jellyfin songs load through QueryUtil", latch, NETWORK_TIMEOUT_MS);
        List<Song> songs = songsRef.get();
        assertNotNull("Jellyfin returned no songs", songs);
        assertFalse("Jellyfin returned an empty song list", songs.isEmpty());
        return songs;
    }

    private Song firstPlayableSong(List<Song> songs) {
        for (Song song : songs) {
            if (hasText(song.id) && hasText(song.container) && hasText(song.codec)) {
                return song;
            }
        }

        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void assertTrueWithin(String description, CountDownLatch latch, long timeoutMs) throws InterruptedException {
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Timed out waiting for " + description);
        }
    }

    private static void waitUntil(String description, long timeoutMs, Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            if (condition.isSatisfied()) {
                return;
            }
            Thread.sleep(250);
        } while (System.currentTimeMillis() < deadline);

        throw new AssertionError("Timed out waiting for " + description);
    }

    private interface Condition {
        boolean isSatisfied();
    }
}
