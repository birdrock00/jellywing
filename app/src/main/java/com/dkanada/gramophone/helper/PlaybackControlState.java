package com.dkanada.gramophone.helper;

import androidx.annotation.StringRes;

import com.dkanada.gramophone.R;

public enum PlaybackControlState {
    PLAY(R.string.action_play),
    PAUSE(R.string.action_pause);

    @StringRes
    private final int contentDescriptionRes;

    PlaybackControlState(@StringRes int contentDescriptionRes) {
        this.contentDescriptionRes = contentDescriptionRes;
    }

    public static PlaybackControlState fromPlaying(boolean playing) {
        return playing ? PAUSE : PLAY;
    }

    @StringRes
    public int getContentDescriptionRes() {
        return contentDescriptionRes;
    }
}
