package club.thatpetbff.gramophone.fragments.player.flat;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import club.thatpetbff.gramophone.databinding.FragmentFlatPlayerPlaybackControlsBinding;
import club.thatpetbff.gramophone.util.ThemeUtil;
import club.thatpetbff.gramophone.service.QueueManager;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.helper.MusicProgressViewUpdateHelper;
import club.thatpetbff.gramophone.helper.PlaybackControlState;
import club.thatpetbff.gramophone.helper.PlayPauseButtonOnClickHandler;
import club.thatpetbff.gramophone.interfaces.base.SimpleOnSeekbarChangeListener;
import club.thatpetbff.gramophone.fragments.AbsMusicServiceFragment;
import club.thatpetbff.gramophone.util.MusicUtil;
import club.thatpetbff.gramophone.views.PlayPauseDrawable;

import java.util.Collection;
import java.util.LinkedList;

public class FlatPlayerPlaybackControlsFragment extends AbsMusicServiceFragment implements MusicProgressViewUpdateHelper.Callback {
    public FragmentFlatPlayerPlaybackControlsBinding binding;

    private PlayPauseDrawable playPauseDrawable;

    private int lastPlaybackControlsColor;
    private int lastDisabledPlaybackControlsColor;

    private MusicProgressViewUpdateHelper progressViewUpdateHelper;

    private AnimatorSet musicControllerAnimationSet;

    private boolean hidden = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        progressViewUpdateHelper = new MusicProgressViewUpdateHelper(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFlatPlayerPlaybackControlsBinding.inflate(inflater);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializePlaybackControlColors();
        setUpMusicControllers();
        updateProgressTextColor();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCurrentSongText();
        updatePlayPauseDrawableState(false);
        progressViewUpdateHelper.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        progressViewUpdateHelper.stop();
    }

    @Override
    public void onServiceConnected() {
        updateCurrentSongText();
        updatePlayPauseDrawableState(false);
        updateRepeatState();
        updateShuffleState();
    }

    @Override
    public void onQueueChanged() {
        updateCurrentSongText();
        updatePlayPauseDrawableState(false);
    }

    @Override
    public void onPlayMetadataChanged() {
        updateCurrentSongText();
        updatePlayPauseDrawableState(false);
    }

    @Override
    public void onPlayStateChanged() {
        updatePlayPauseDrawableState(true);
    }

    @Override
    public void onRepeatModeChanged() {
        updateRepeatState();
    }

    @Override
    public void onShuffleModeChanged() {
        updateShuffleState();
    }

    public void setDark(boolean dark) {
        if (dark) {
            lastPlaybackControlsColor = ThemeUtil.getSecondaryTextColor(requireActivity(), true);
            lastDisabledPlaybackControlsColor = ThemeUtil.getColorAlpha(requireActivity(), R.color.color_text_secondary_light, 180);
        } else {
            lastPlaybackControlsColor = ThemeUtil.getPrimaryTextColor(requireActivity(), false);
            lastDisabledPlaybackControlsColor = ThemeUtil.getColorAlpha(requireActivity(), R.color.color_text_primary_dark, 180);
        }

        updateRepeatState();
        updateShuffleState();
        updatePrevNextColor();
        updatePlayPauseColor();
        updateProgressTextColor();
    }

    private void initializePlaybackControlColors() {
        lastPlaybackControlsColor = ThemeUtil.getPrimaryTextColor(requireContext(), false);
        lastDisabledPlaybackControlsColor = ThemeUtil.getColorAlpha(requireContext(), R.color.color_text_primary_dark, 180);
    }

    private void setUpPlayPauseButton() {
        playPauseDrawable = new PlayPauseDrawable(requireActivity());
        binding.playerPlayPauseButton.setImageDrawable(playPauseDrawable);
        updatePlayPauseColor();
        binding.playerPlayPauseButton.setOnClickListener(new PlayPauseButtonOnClickHandler());
        binding.playerPlayPauseButton.post(() -> {
            binding.playerPlayPauseButton.setPivotX(binding.playerPlayPauseButton.getWidth() / 2f);
            binding.playerPlayPauseButton.setPivotY(binding.playerPlayPauseButton.getHeight() / 2f);
        });
        updatePlayPauseDrawableState(false);
    }

    protected void updatePlayPauseDrawableState(boolean animate) {
        if (playPauseDrawable == null || binding == null) {
            return;
        }

        PlaybackControlState state = PlaybackControlState.fromPlaying(MusicPlayerRemote.isPlaying());
        binding.playerPlayPauseButton.setContentDescription(getString(state.getContentDescriptionRes()));

        if (state == PlaybackControlState.PAUSE) {
            playPauseDrawable.setPause(animate);
        } else {
            playPauseDrawable.setPlay(animate);
        }
    }

    public void updateBufferingIndicatorColor(int color) {
        binding.playerBufferingIndicator.setProgressBackgroundTintList(ColorStateList.valueOf(getResources().getColor(android.R.color.transparent)));
        binding.playerBufferingIndicator.setIndeterminateTintList(ColorStateList.valueOf(color));
    }

    private void setUpMusicControllers() {
        updateCurrentSongText();
        setUpPlayPauseButton();
        setUpPrevNext();
        setUpRepeatButton();
        setUpShuffleButton();
        setUpProgressSlider();
    }

    private void updateCurrentSongText() {
        if (binding == null) {
            return;
        }

        binding.playerSongTitle.setText(MusicUtil.getSongTitle(MusicPlayerRemote.getCurrentSong()));
        binding.playerSongSubtitle.setText(MusicUtil.getSongInfoString(MusicPlayerRemote.getCurrentSong()));
    }

    private void setUpPrevNext() {
        updatePrevNextColor();
        binding.playerNextButton.setOnClickListener(v -> MusicPlayerRemote.playNextSong());
        binding.playerPrevButton.setOnClickListener(v -> MusicPlayerRemote.back());
    }

    private void updateProgressTextColor() {
        int color = ThemeUtil.getPrimaryTextColor(requireContext(), false);
        binding.playerSongTotalTime.setTextColor(color);
        binding.playerSongCurrentProgress.setTextColor(color);
    }

    private void updatePrevNextColor() {
        binding.playerNextButton.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN);
        binding.playerPrevButton.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN);
    }

    private void updatePlayPauseColor() {
        binding.playerPlayPauseButton.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN);
    }

    private void setUpShuffleButton() {
        binding.playerShuffleButton.setOnClickListener(v -> MusicPlayerRemote.toggleShuffleMode());
    }

    private void updateShuffleState() {
        switch (MusicPlayerRemote.getShuffleMode()) {
            case QueueManager.SHUFFLE_MODE_SHUFFLE:
                binding.playerShuffleButton.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN);
                break;
            case QueueManager.SHUFFLE_MODE_NONE:
            default:
                binding.playerShuffleButton.setColorFilter(lastDisabledPlaybackControlsColor, PorterDuff.Mode.SRC_IN);
                break;
        }
    }

    private void setUpRepeatButton() {
        binding.playerRepeatButton.setOnClickListener(v -> {
            if (MusicPlayerRemote.cycleRepeatMode()) {
                updateRepeatState();
            }
        });
        updateRepeatState();
    }

    private void updateRepeatState() {
        int repeatMode = MusicPlayerRemote.getRepeatMode();
        binding.playerRepeatButton.setSelected(repeatMode != QueueManager.REPEAT_MODE_NONE);

        switch (repeatMode) {
            case QueueManager.REPEAT_MODE_NONE:
                binding.playerRepeatButton.setContentDescription(getString(R.string.action_repeat_off));
                binding.playerRepeatButton.setImageResource(R.drawable.ic_repeat_white_24dp);
                binding.playerRepeatButton.setColorFilter(lastDisabledPlaybackControlsColor, PorterDuff.Mode.SRC_IN);
                break;
            case QueueManager.REPEAT_MODE_ALL:
                binding.playerRepeatButton.setContentDescription(getString(R.string.action_repeat_on));
                binding.playerRepeatButton.setImageResource(R.drawable.ic_repeat_white_24dp);
                binding.playerRepeatButton.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN);
                break;
            case QueueManager.REPEAT_MODE_THIS:
                binding.playerRepeatButton.setContentDescription(getString(R.string.action_repeat_one));
                binding.playerRepeatButton.setImageResource(R.drawable.ic_repeat_one_white_24dp);
                binding.playerRepeatButton.setColorFilter(lastPlaybackControlsColor, PorterDuff.Mode.SRC_IN);
                break;
        }
    }

    public void show() {
        updatePlayPauseDrawableState(false);
        if (hidden) {
            if (musicControllerAnimationSet == null) {
                TimeInterpolator interpolator = new FastOutSlowInInterpolator();
                final int duration = 300;

                LinkedList<Animator> animators = new LinkedList<>();

                addAnimation(animators, binding.playerPlayPauseButton, interpolator, duration, 0);
                addAnimation(animators, binding.playerNextButton, interpolator, duration, 100);
                addAnimation(animators, binding.playerPrevButton, interpolator, duration, 100);
                addAnimation(animators, binding.playerShuffleButton, interpolator, duration, 200);
                addAnimation(animators, binding.playerRepeatButton, interpolator, duration, 200);

                musicControllerAnimationSet = new AnimatorSet();
                musicControllerAnimationSet.playTogether(animators);
            } else {
                musicControllerAnimationSet.cancel();
            }

            musicControllerAnimationSet.start();
        }

        hidden = false;
    }

    public void hide() {
        if (musicControllerAnimationSet != null) {
            musicControllerAnimationSet.cancel();
        }

        prepareForAnimation(binding.playerPlayPauseButton);
        prepareForAnimation(binding.playerNextButton);
        prepareForAnimation(binding.playerPrevButton);
        prepareForAnimation(binding.playerShuffleButton);
        prepareForAnimation(binding.playerRepeatButton);

        hidden = true;
    }

    private static void addAnimation(Collection<Animator> animators, View view, TimeInterpolator interpolator, int duration, int delay) {
        Animator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0f, 1f);
        scaleX.setInterpolator(interpolator);
        scaleX.setDuration(duration);
        scaleX.setStartDelay(delay);
        animators.add(scaleX);

        Animator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0f, 1f);
        scaleY.setInterpolator(interpolator);
        scaleY.setDuration(duration);
        scaleY.setStartDelay(delay);
        animators.add(scaleY);
    }

    private static void prepareForAnimation(View view) {
        if (view != null) {
            view.setScaleX(0f);
            view.setScaleY(0f);
        }
    }

    private void setUpProgressSlider() {
        int color = ThemeUtil.getPrimaryTextColor(requireContext(), false);
        binding.playerProgressSlider.getThumb().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        binding.playerProgressSlider.getProgressDrawable().mutate().setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.SRC_IN);

        binding.playerProgressSlider.setOnSeekBarChangeListener(new SimpleOnSeekbarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    MusicPlayerRemote.seekTo(progress);
                    onUpdateProgressViews(MusicPlayerRemote.getSongProgressMillis(), MusicPlayerRemote.getSongDurationMillis());
                }
            }
        });
    }

    @Override
    public void onUpdateProgressViews(int progress, int total) {
        updateCurrentSongText();
        updatePlayPauseDrawableState(false);
        binding.playerBufferingIndicator.setVisibility(MusicPlayerRemote.isLoading() ? View.VISIBLE : View.GONE);

        binding.playerProgressSlider.setMax(total);
        binding.playerProgressSlider.setProgress(progress);

        binding.playerSongTotalTime.setText(MusicUtil.getReadableDurationString(total));
        binding.playerSongCurrentProgress.setText(MusicUtil.getReadableDurationString(progress));
    }
}
