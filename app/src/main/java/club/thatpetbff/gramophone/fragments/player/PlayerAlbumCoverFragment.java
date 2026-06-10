package club.thatpetbff.gramophone.fragments.player;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.request.transition.Transition;
import club.thatpetbff.gramophone.adapter.AlbumCoverPagerAdapter;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.databinding.FragmentPlayerAlbumCoverBinding;
import club.thatpetbff.gramophone.glide.CustomGlideRequest;
import club.thatpetbff.gramophone.glide.CustomPaletteTarget;
import club.thatpetbff.gramophone.glide.palette.BitmapPaletteWrapper;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.interfaces.base.SimpleAnimatorListener;
import club.thatpetbff.gramophone.fragments.AbsMusicServiceFragment;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.util.ViewUtil;

import java.util.List;
import java.util.Objects;

public class PlayerAlbumCoverFragment extends AbsMusicServiceFragment implements ViewPager.OnPageChangeListener {
    public static final int VISIBILITY_ANIM_DURATION = 300;

    private FragmentPlayerAlbumCoverBinding binding;

    private Callbacks callbacks;
    private int currentPosition;
    private boolean syncingCurrentItem;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPlayerAlbumCoverBinding.inflate(inflater);

        return binding.getRoot();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.playerAlbumCoverViewPager.addOnPageChangeListener(this);
        binding.playerAlbumCoverViewPager.setOnTouchListener(new View.OnTouchListener() {
            GestureDetector gestureDetector = new GestureDetector(getActivity(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (callbacks != null) {
                        callbacks.onToolbarToggled();
                        return true;
                    }

                    return super.onSingleTapConfirmed(e);
                }
            });

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding.playerAlbumCoverViewPager.removeOnPageChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCurrentArtwork();
        updateCurrentSongText();
    }

    @Override
    public void onServiceConnected() {
        updatePlayingQueue();
        updateCurrentArtwork();
        updateCurrentSongText();
    }

    @Override
    public void onPlayMetadataChanged() {
        updatePlayingQueue();
        updateCurrentArtwork();
        updateCurrentSongText();
    }

    @Override
    public void onQueueChanged() {
        updatePlayingQueue();
        updateCurrentArtwork();
        updateCurrentSongText();
    }

    private void updatePlayingQueue() {
        if (binding == null) {
            return;
        }

        List<Song> queue = MusicPlayerRemote.getPlayingQueue();
        int position = MusicPlayerRemote.getPosition();
        if (queue == null || queue.isEmpty() || position < 0 || position >= queue.size()) {
            binding.playerAlbumCoverViewPager.setAdapter(null);
            clearCurrentArtwork();
            return;
        }

        AlbumCoverPagerAdapter adapter = new AlbumCoverPagerAdapter(getParentFragmentManager(), queue);
        syncingCurrentItem = true;
        binding.playerAlbumCoverViewPager.setAdapter(null);
        binding.playerAlbumCoverViewPager.removeAllViews();
        binding.playerAlbumCoverViewPager.setAdapter(adapter);
        binding.playerAlbumCoverViewPager.setOffscreenPageLimit(Math.max(1, Math.min(queue.size() - 1, 3)));

        binding.playerAlbumCoverViewPager.setCurrentItem(position, false);
        forceCurrentCoverFragment(adapter, position);
        currentPosition = position;
        adapter.receiveColor(colorReceiver, position);
        syncingCurrentItem = false;
    }

    private void updateCurrentArtwork() {
        if (binding == null) {
            return;
        }

        Song song = MusicPlayerRemote.getCurrentSong();
        if (song == null) {
            clearCurrentArtwork();
            return;
        }

        String artworkItemId = song.getArtworkItemId();
        binding.playerCurrentImage.setTag(R.id.current_album_artwork_song_id, song.id);
        binding.playerCurrentImage.setTag(R.id.current_album_artwork_id, artworkItemId);
        binding.playerCurrentImage.setImageResource(R.drawable.default_album_art);
        CustomGlideRequest.Builder
                .from(requireContext(), artworkItemId, song.blurHash)
                .palette()
                .build()
                .into(new CurrentArtworkTarget(binding.playerCurrentImage, song.id, artworkItemId));
    }

    private class CurrentArtworkTarget extends CustomPaletteTarget {
        private final String songId;
        private final String artworkItemId;

        private CurrentArtworkTarget(android.widget.ImageView view, String songId, String artworkItemId) {
            super(view);
            this.songId = songId;
            this.artworkItemId = artworkItemId;
        }

        @Override
        public void onLoadFailed(Drawable errorDrawable) {
            if (isCurrentRequest()) {
                super.onLoadFailed(errorDrawable);
            }
        }

        @Override
        public void onResourceReady(@NonNull BitmapPaletteWrapper resource, Transition<? super BitmapPaletteWrapper> glideAnimation) {
            if (isCurrentRequest()) {
                super.onResourceReady(resource, glideAnimation);
            }
        }

        @Override
        public void onColorReady(int color) {
            if (isCurrentRequest()) {
                notifyColorChange(color);
            }
        }

        private boolean isCurrentRequest() {
            return binding != null
                    && Objects.equals(songId, binding.playerCurrentImage.getTag(R.id.current_album_artwork_song_id))
                    && Objects.equals(artworkItemId, binding.playerCurrentImage.getTag(R.id.current_album_artwork_id));
        }
    }

    private void clearCurrentArtwork() {
        if (binding == null) {
            return;
        }

        binding.playerCurrentImage.setTag(R.id.current_album_artwork_song_id, null);
        binding.playerCurrentImage.setTag(R.id.current_album_artwork_id, null);
        binding.playerCurrentImage.setImageResource(R.drawable.default_album_art);
    }

    private void forceCurrentCoverFragment(AlbumCoverPagerAdapter adapter, int position) {
        if (position < 0 || position >= adapter.getCount()) {
            return;
        }

        Object currentCover = adapter.instantiateItem(binding.playerAlbumCoverViewPager, position);
        adapter.setPrimaryItem(binding.playerAlbumCoverViewPager, position, currentCover);
        adapter.finishUpdate(binding.playerAlbumCoverViewPager);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        currentPosition = position;
        AlbumCoverPagerAdapter adapter = (AlbumCoverPagerAdapter) binding.playerAlbumCoverViewPager.getAdapter();
        if (adapter != null) {
            adapter.receiveColor(colorReceiver, position);
        }
        if (!syncingCurrentItem && position != MusicPlayerRemote.getPosition()) {
            MusicPlayerRemote.playSongAt(position);
        } else {
            updateCurrentSongText();
        }
    }

    private AlbumCoverPagerAdapter.AlbumCoverFragment.ColorReceiver colorReceiver = new AlbumCoverPagerAdapter.AlbumCoverFragment.ColorReceiver() {
        @Override
        public void onColorReady(int color, int requestCode) {
            if (currentPosition == requestCode) {
                notifyColorChange(color);
            }
        }
    };

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public void showHeartAnimation() {
        binding.playerFavoriteIcon.clearAnimation();

        binding.playerFavoriteIcon.setAlpha(0f);
        binding.playerFavoriteIcon.setScaleX(0f);
        binding.playerFavoriteIcon.setScaleY(0f);
        binding.playerFavoriteIcon.setVisibility(View.VISIBLE);
        binding.playerFavoriteIcon.setPivotX(binding.playerFavoriteIcon.getWidth() / 2f);
        binding.playerFavoriteIcon.setPivotY(binding.playerFavoriteIcon.getHeight() / 2f);

        binding.playerFavoriteIcon.animate()
                .setDuration(ViewUtil.PHONOGRAPH_ANIM_TIME / 2)
                .setInterpolator(new DecelerateInterpolator())
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        binding.playerFavoriteIcon.setVisibility(View.INVISIBLE);
                    }
                })
                .withEndAction(() -> binding.playerFavoriteIcon.animate()
                        .setDuration(ViewUtil.PHONOGRAPH_ANIM_TIME / 2)
                        .setInterpolator(new AccelerateInterpolator())
                        .scaleX(0f)
                        .scaleY(0f)
                        .alpha(0f)
                        .start())
                .start();
    }

    private void notifyColorChange(int color) {
        if (callbacks != null) callbacks.onColorChanged(color);
    }

    private void updateCurrentSongText() {
        // The playback controls render the single visible current-song title.
        // Keep the cover focused on artwork so the full player does not show two current-song rows.
        binding.playerSongInfo.setVisibility(View.GONE);
    }

    public void refreshCurrentSong() {
        updatePlayingQueue();
        updateCurrentArtwork();
        updateCurrentSongText();
    }

    public void setCallbacks(Callbacks listener) {
        callbacks = listener;
    }

    public interface Callbacks {
        void onColorChanged(int color);

        void onFavoriteToggled();

        void onToolbarToggled();
    }
}
