package club.thatpetbff.gramophone.service.playback;

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
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.util.MusicUtil;
import club.thatpetbff.gramophone.util.PlaybackAuthHeaders;
import club.thatpetbff.gramophone.util.PreferenceUtil;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * True while the player is in a fatal error state since the last time
     * playback actually started. Lets MusicService refresh the media source
     * (fresh stream URL + fresh auth headers) on the next play tap instead of
     * letting the user have to force-stop and relaunch the app.
     */
    private volatile boolean errored = false;

    /**
     * Budget of automatic skip-to-next actions triggered by player errors.
     * Without a cap, a systemic failure (logged out, server offline, Wi-Fi
     * loss) makes onPlayerError skip item after item until the whole queue
     * is burned through in a few seconds, ending on the last item with
     * playback stopped. Only touched on the player thread.
     */
    private final ErrorSkipBudget errorSkipBudget = new ErrorSkipBudget();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @SuppressWarnings("FieldCanBeLocal")
    private final Player.Listener eventListener = new Player.Listener() {
        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            Log.i(TAG, String.format("onPlayWhenReadyChanged: %b %d", playWhenReady, reason));
            if (playWhenReady) {
                // Playback is (re)starting; a past error no longer describes
                // the player's state.
                errored = false;
                errorSkipBudget.reset();
            }
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
            // Never log MediaItem.toString(): it renders the stream URI which
            // contains ?ApiKey=<token>. Log only the media ID and reason.
            String mediaId = mediaItem != null ? String.valueOf(mediaItem.mediaId) : "null";
            Log.i(TAG, String.format("onMediaItemTransition: id=%s reason=%d", mediaId, reason));
            if (listener != null) listener.onTrackChanged(reason);
        }

        @Override
        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
            Log.i(TAG, String.format("onPositionDiscontinuity: %d", reason));
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            // Error messages can echo the failing stream URL (?ApiKey=<token>);
            // log only the stable error name/code, never the message/URL.
            String errorName = "unknown";
            int errorCode = 0;
            try {
                errorName = error.getErrorCodeName();
                errorCode = error.errorCode;
            } catch (Exception ignored) {
            }
            Log.i(TAG, String.format("onPlayerError: %s(%d)", errorName, errorCode));

            errored = true;

            // Do not destroy the whole queue when a single item fails to load (e.g. a
            // dead direct-play/transcode URL after a server hiccup). Clearing the media
            // items dead-ends the player in STATE_ENDED, which makes MusicService drop
            // its foreground notification and churn foreground-service start/stop cycles
            // that the system rejects from the background - eventually the watchdog kills
            // the process for EXCESSIVE CPU USAGE. Skip to the next playable item instead.
            //
            // Cap consecutive error-driven skips: when every item fails (auth loss,
            // server offline, network down) skipping just walks the whole queue in a
            // few seconds and stops on the last item. Staying on the failed item lets
            // the next play tap retry it (MusicService refreshes the source first).
            int count = exoPlayer.getMediaItemCount();
            int current = exoPlayer.getCurrentMediaItemIndex();
            if (count > 0 && current >= 0 && current + 1 < count
                    && errorSkipBudget.allowSkip()) {
                errorSkipBudget.recordSkip();
                exoPlayer.seekToNextMediaItem();
                exoPlayer.prepare();
            } else {
                Toast.makeText(context, context.getResources().getString(R.string.unplayable_file), Toast.LENGTH_SHORT).show();
                exoPlayer.setPlayWhenReady(false);
            }
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
                    errored = false;
                    errorSkipBudget.reset();
                    exoPlayer.stop();
                    exoPlayer.clearMediaItems();
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

            if (isDirectPlay(uri.toString(), song, containers, codecs, maxBitrate)) {
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

    static boolean isDirectPlay(String uriString,
                                Song song,
                                List<String> containers,
                                List<String> codecs,
                                String maxBitrate) {
        if (uriString != null && uriString.contains("file://")) {
            return true;
        }
        if (song == null || song.container == null || song.codec == null) {
            return false;
        }
        int max;
        try {
            max = Integer.parseInt(maxBitrate);
        } catch (NumberFormatException e) {
            return false;
        }
        // bitRate==0 means unknown (MediaSources.bitrate null); preserve legacy
        // behavior and allow direct-play when container/codec match.
        return containers.contains(song.container.toLowerCase(Locale.ROOT))
                && codecs.contains(song.codec.toLowerCase(Locale.ROOT))
                && song.bitRate <= max;
    }

    static Map<String, String> buildPlaybackHeaders() {
        try {
            org.jellyfin.apiclient.interaction.ApiClient client = App.getApiClient();
            if (client == null) {
                return new HashMap<>();
            }
            // Right after a process restart the in-memory client is unauthenticated
            // until LoginService's async chain runs; rehydrate the saved token
            // (local DB only, no network) so stream requests never ship with an
            // empty Authorization header and 401 a few seconds into playback.
            App.ensureAuthenticatedApiClient();
            return new HashMap<>(PlaybackAuthHeaders.build(
                    client.getClientName(),
                    client.getDeviceName(),
                    client.getDeviceId(),
                    client.getApplicationVersion(),
                    client.getAccessToken()));
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private DataSource.Factory buildDataSourceFactory() {
        // Build fresh auth headers on every DataSource creation so token
        // refresh / re-login / server switch takes effect without restart.
        // Header VALUES contain secrets; only names are ever logged.
        return () -> {
            DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
            httpFactory.setDefaultRequestProperties(buildPlaybackHeaders());
            DefaultDataSource.Factory upstreamFactory = new DefaultDataSource.Factory(context, httpFactory);
            return new CacheDataSource(
                    simpleCache,
                    upstreamFactory.createDataSource(),
                    new FileDataSource(),
                    new CacheDataSink.Factory()
                            .setCache(simpleCache)
                            .setFragmentSize(10 * 1024 * 1024)
                            .createDataSink(),
                    CacheDataSource.FLAG_BLOCK_ON_CACHE,
                    null
            );
        };
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
    public boolean hasMediaItems() {
        return callOnPlayerThread(() -> exoPlayer.getMediaItemCount() > 0, false);
    }

    @Override
    public boolean isErrored() {
        return errored;
    }

    @Override
    public void start() {
        runOnPlayerThread(() -> {
            if (exoPlayer.getMediaItemCount() > 0
                    && (exoPlayer.getPlaybackState() == Player.STATE_IDLE || errored)) {
                // A play tap on an errored player must re-prepare: media3 leaves
                // the player in an ended state after onPlayerError, and
                // setPlayWhenReady(true) alone does nothing there. Without this
                // a play tap on a dead player does nothing, which is why the
                // user had to kill and relaunch the app to get playback back.
                // (errored is set in onPlayerError and cleared when playback
                // actually starts again.)
                exoPlayer.prepare();
            }
            exoPlayer.setPlayWhenReady(true);
        });
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
