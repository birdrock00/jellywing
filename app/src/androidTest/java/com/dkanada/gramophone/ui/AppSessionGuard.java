package com.dkanada.gramophone.ui;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.model.User;
import com.dkanada.gramophone.util.PreferenceUtil;

import java.util.List;

final class AppSessionGuard implements AutoCloseable {
    private final String server;
    private final String userId;
    private final User user;
    private final User testUser;
    private final List<Song> playingQueue;
    private final List<Song> shuffledQueue;
    private final int position;
    private final int progress;
    private final int repeat;
    private final int shuffle;

    AppSessionGuard(User testUser) {
        PreferenceUtil preferences = PreferenceUtil.getInstance(App.getInstance());
        this.server = preferences.getServer();
        this.userId = preferences.getUser();
        this.user = userId == null ? null : App.getDatabase().userDao().getUser(userId);
        this.testUser = testUser;
        this.playingQueue = App.getDatabase().queueSongDao().getQueue(0);
        this.shuffledQueue = App.getDatabase().queueSongDao().getQueue(1);
        this.position = preferences.getPosition();
        this.progress = preferences.getProgress();
        this.repeat = preferences.getRepeat();
        this.shuffle = preferences.getShuffle();
    }

    void installTestUser() {
        if (testUser == null) {
            return;
        }

        App.getDatabase().userDao().insertUser(testUser);
        PreferenceUtil.getInstance(App.getInstance()).setServer(testUser.server);
        PreferenceUtil.getInstance(App.getInstance()).setUser(testUser.id);
        App.getApiClient().ChangeServerLocation(testUser.server);
        App.getApiClient().SetAuthenticationInfo(testUser.token, testUser.id);
    }

    @Override
    public void close() {
        if (testUser != null) {
            App.getDatabase().userDao().deleteUser(testUser);
        }

        App.getDatabase().queueSongDao().updateQueues(playingQueue, shuffledQueue);

        PreferenceUtil preferences = PreferenceUtil.getInstance(App.getInstance());
        preferences.setPosition(position);
        preferences.setProgress(progress);
        preferences.setRepeat(repeat);
        preferences.setShuffle(shuffle);
        preferences.setServer(server);
        preferences.setUser(userId);
        if (user != null) {
            App.getApiClient().ChangeServerLocation(user.server);
            App.getApiClient().SetAuthenticationInfo(user.token, user.id);
        } else if (server != null) {
            App.getApiClient().ChangeServerLocation(server);
        }
    }
}
