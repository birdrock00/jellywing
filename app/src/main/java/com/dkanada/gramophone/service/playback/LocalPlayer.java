package com.dkanada.gramophone.service.playback;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.MusicUtil;
import com.dkanada.gramophone.util.PreferenceUtil;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class LocalPlayer implements Playback {
    public static final String TAG = LocalPlayer.class.getSimpleName();

    private final Context context;
    private final ExoPlayer exoPlayer;
    private final Handler playerHandler;
    private final SimpleCache simpleCache;

    private PlaybackListener listener;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @SuppressWarnings("FieldCanBeLocal")
    private final Player.Listener eventListener = new Player.Listener() {
        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            Log.i(TAG, String.format("onPlayWhenReadyChanged: %b %d", playWhenReady, reason));
            if (listener != null) listener.onReadyChanged(playWhenReady, reason);
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            Log.i(TAG, String.format("onPlaybackStateChanged: %d", state));
            if (listener != null) listener.onStateChanged(state);
        }

        @Override
        public void onPlaybackSuppressionReasonChanged(@Player.PlaybackSuppressionReason int playbackSuppressionReason) {
            Log.i(TAG, String.format("onPlaybackSuppressionReasonChanged: %d", playbackSuppressionReason));
            if (listener != null) listener.onStateChanged(Player.STATE_READY);
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            Log.i(TAG, String.format("onMediaItemTransition: %s %d", mediaItem, reason));
            if (listener != null) listener.onTrackChanged(reason);
        }

        @Override
        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
            Log.i(TAG, String.format("onPositionDiscontinuity: %d", reason));
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.i(TAG, String.format("onPlayerError: %s", error.getMessage()));

            exoPlayer.clearMediaItems();
            exoPlayer.prepare();

            Toast.makeText(context, context.getResources().getString(R.string.unplayable_file), Toast.LENGTH_SHORT).show();
        }
    };

    public LocalPlayer(Context context) {
        this.context = context;

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MUSIC)
            .build();

        exoPlayer = new ExoPlayer.Builder(context)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(buildDataSourceFactory()))
            .setAudioAttributes(audioAttributes, true)
            .build();

        exoPlayer.addListener(eventListener);
        exoPlayer.prepare();
        playerHandler = new Handler(exoPlayer.getApplicationLooper());

        long cacheSize = PreferenceUtil.getInstance(context).getMediaCacheSize();
        LeastRecentlyUsedCacheEvictor recentlyUsedCache = new LeastRecentlyUsedCacheEvictor(cacheSize);
        StandaloneDatabaseProvider databaseProvider = new StandaloneDatabaseProvider(context);

        File cacheDirectory = new File(PreferenceUtil.getInstance(context).getLocationCache(), "exoplayer");
        simpleCache = new SimpleCache(cacheDirectory, recentlyUsedCache, databaseProvider);
    }

    @Override
    public void setQueue(List<Song> queue, int position, int progress, boolean resetCurrentSong) {
        executorService.submit(() -> {
            List<MediaItem> mediaItems = createMediaItems(queue);

            runOnPlayerThread(() -> {
                if (resetCurrentSong) {
                    exoPlayer.setMediaItems(mediaItems, position, progress);
                    exoPlayer.prepare();
                    return;
                }

                int currentPosition = exoPlayer.getCurrentMediaItemIndex();
                exoPlayer.removeMediaItems(0, currentPosition);

                if (exoPlayer.getMediaItemCount() > 1) {
                    exoPlayer.removeMediaItems(1, exoPlayer.getMediaItemCount());
                }

                if (position + 1 < mediaItems.size()) {
                    exoPlayer.addMediaItems(1, mediaItems.subList(position + 1, mediaItems.size()));
                }

                exoPlayer.addMediaItems(0, mediaItems.subList(0, position));
                exoPlayer.prepare();
            });
        });
    }

    private List<MediaItem> createMediaItems(List<Song> queue) {
        return queue.stream().map(song -> {
            File audio = new File(MusicUtil.getFileUri(song));
            Uri uri = Uri.fromFile(audio);

            if (!audio.exists()) {
                uri = Uri.parse(MusicUtil.getTranscodeUri(song));
            }

            List<String> containers = PreferenceUtil.getInstance(context).getDirectPlayCodecs().stream()
                    .map(codec -> codec.container.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
            List<String> codecs = PreferenceUtil.getInstance(context).getDirectPlayCodecs().stream()
                    .map(codec -> codec.codec.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
            String maxBitrate = PreferenceUtil.getInstance(context).getMaximumBitrate();

            MediaItem mediaItem;

            if (uri.toString().contains("file://") || (containers.contains(song.container.toLowerCase(Locale.ROOT)) && codecs.contains(song.codec.toLowerCase(Locale.ROOT)) && song.bitRate <= Integer.parseInt(maxBitrate))) {
                mediaItem = new MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId(song.id)
                        .build();
            } else {
                mediaItem = new MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId(song.id)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build();
            }

            return mediaItem;
        }).collect(Collectors.toList());
    }

    @Override
    public void playSongAt(int position) {
        runOnPlayerThread(() -> {
            if (exoPlayer.getMediaItemCount() > 0) {
                exoPlayer.seekTo(Math.max(0, position) % exoPlayer.getMediaItemCount(), 0);
            }
        });
    }

    private DataSource.Factory buildDataSourceFactory() {
        return () -> new CacheDataSource(
                simpleCache,
                new DefaultDataSource.Factory(context).createDataSource(),
                new FileDataSource(),
                new CacheDataSink.Factory()
                        .setCache(simpleCache)
                        .setFragmentSize(10 * 1024 * 1024)
                        .createDataSink(),
                CacheDataSource.FLAG_BLOCK_ON_CACHE,
                null
        );
    }

    @Override
    public void setListener(PlaybackListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isReady() {
        return callOnPlayerThread(exoPlayer::getPlayWhenReady, false);
    }

    @Override
    public boolean isPlaying() {
        return callOnPlayerThread(
                () -> exoPlayer.getPlayWhenReady() && exoPlayer.getPlaybackSuppressionReason() == Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                false);
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public boolean isLoading() {
        return callOnPlayerThread(() -> {
            MediaItem current = exoPlayer.getCurrentMediaItem();
            if (current != null && current.localConfiguration.uri.toString().contains("file://")) {
                return false;
            }

            return exoPlayer.getPlaybackState() == Player.STATE_BUFFERING;
        }, false);
    }

    @Override
    public void start() {
        runOnPlayerThread(() -> exoPlayer.setPlayWhenReady(true));
    }

    @Override
    public void pause() {
        runOnPlayerThread(() -> exoPlayer.setPlayWhenReady(false));
    }

    @Override
    public void stop() {
        callOnPlayerThread(() -> {
            exoPlayer.release();
            return null;
        }, null);
        simpleCache.release();
    }

    @Override
    public void previous() {
        runOnPlayerThread(exoPlayer::seekToPreviousMediaItem);
    }

    @Override
    public void next() {
        runOnPlayerThread(exoPlayer::seekToNextMediaItem);
    }

    @Override
    public void setRepeatMode(@Player.RepeatMode int repeatMode) {
        runOnPlayerThread(() -> exoPlayer.setRepeatMode(repeatMode));
    }

    @Override
    public int getProgress() {
        return callOnPlayerThread(() -> (int) exoPlayer.getCurrentPosition(), 0);
    }

    @Override
    public int getDuration() {
        return callOnPlayerThread(() -> (int) exoPlayer.getDuration(), 0);
    }

    @Override
    public void setProgress(int progress) {
        runOnPlayerThread(() -> exoPlayer.seekTo(progress));
    }

    @Override
    public void setVolume(int volume) {
        runOnPlayerThread(() -> exoPlayer.setVolume(volume / 100f));
    }

    @Override
    public int getVolume() {
        return callOnPlayerThread(() -> (int) (exoPlayer.getVolume() * 100), 100);
    }

    private void runOnPlayerThread(Runnable runnable) {
        if (Looper.myLooper() == exoPlayer.getApplicationLooper()) {
            runnable.run();
        } else {
            playerHandler.post(runnable);
        }
    }

    private <T> T callOnPlayerThread(Callable<T> callable, T fallback) {
        if (Looper.myLooper() == exoPlayer.getApplicationLooper()) {
            try {
                return callable.call();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        FutureTask<T> task = new FutureTask<>(callable);
        playerHandler.post(task);

        try {
            return task.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (TimeoutException exception) {
            return fallback;
        } catch (ExecutionException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
