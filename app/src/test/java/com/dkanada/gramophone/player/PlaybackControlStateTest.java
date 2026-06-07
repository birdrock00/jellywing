package com.dkanada.gramophone.player;

import static org.junit.Assert.assertEquals;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.helper.PlaybackControlState;

import org.junit.Test;

public class PlaybackControlStateTest {
    @Test
    public void activePlaybackShowsPauseAction() {
        PlaybackControlState state = PlaybackControlState.fromPlaying(true);

        assertEquals(PlaybackControlState.PAUSE, state);
        assertEquals(R.string.action_pause, state.getContentDescriptionRes());
    }

    @Test
    public void pausedPlaybackShowsPlayAction() {
        PlaybackControlState state = PlaybackControlState.fromPlaying(false);

        assertEquals(PlaybackControlState.PLAY, state);
        assertEquals(R.string.action_play, state.getContentDescriptionRes());
    }
}
