package club.thatpetbff.gramophone.adapter.song;

import android.view.MenuItem;
import android.view.View;
import android.os.SystemClock;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemState;
import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemViewHolder;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.draggable.annotation.DraggableItemStateFlags;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.activities.base.AbsMusicServiceActivity;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.interfaces.CabHolder;
import club.thatpetbff.gramophone.model.Song;

import java.util.ArrayList;
import java.util.List;

public class PlayingQueueAdapter extends SongAdapter implements DraggableItemAdapter<PlayingQueueAdapter.ViewHolder> {
    private static final int HISTORY = 0;
    private static final int CURRENT = 1;
    private static final int NEXT = 2;
    private static final long PLAY_CLICK_DEBOUNCE_MS = 2000;

    private int current;
    private final List<Song> playingQueue = new ArrayList<>();
    private final List<Integer> queuePositions = new ArrayList<>();
    private static long lastPlayClickUptime;

    public PlayingQueueAdapter(AppCompatActivity activity, List<Song> dataSet, int current, @LayoutRes int itemLayoutRes, boolean usePalette, @Nullable CabHolder cabHolder) {
        super(activity, createUpNextQueue(dataSet, current), itemLayoutRes, usePalette, cabHolder);
        updateQueue(dataSet, current);
    }

    @Override
    protected SongAdapter.ViewHolder createViewHolder(View view) {
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongAdapter.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        setAlpha(holder, 1.0f);
        holder.itemView.setTag(R.id.playing_queue_position, getQueuePosition(position));
    }

    @Override
    public int getItemViewType(int position) {
        return NEXT;
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= dataSet.size()) {
            return RecyclerView.NO_ID;
        }

        Song song = dataSet.get(position);
        return song != null && song.id != null ? song.id.hashCode() : position;
    }

    @Override
    protected void loadAlbumCover(Song song, SongAdapter.ViewHolder holder) {
        super.loadAlbumCover(song, holder);
    }

    public void swapDataSet(List<Song> dataSet, int position) {
        updateQueue(dataSet, position);
        notifyDataSetChanged();
    }

    public void setCurrent(int current) {
        updateQueue(playingQueue, current);
        notifyDataSetChanged();
    }

    private void updateQueue(List<Song> queue, int current) {
        List<Song> queueCopy = queue == null ? new ArrayList<>() : new ArrayList<>(queue);

        playingQueue.clear();
        playingQueue.addAll(queueCopy);

        this.current = current;
        queuePositions.clear();
        queuePositions.addAll(createUpNextQueuePositionsForTest(playingQueue, current));
        dataSet = createSongsForPositions(playingQueue, queuePositions);
    }

    private int getQueuePosition(int adapterPosition) {
        return getQueuePositionForTest(queuePositions, adapterPosition);
    }

    private static List<Song> createUpNextQueue(List<Song> queue, int current) {
        return createSongsForPositions(queue, createUpNextQueuePositionsForTest(queue, current));
    }

    private static List<Song> createSongsForPositions(List<Song> queue, List<Integer> positions) {
        List<Song> songs = new ArrayList<>();
        if (queue == null) {
            return songs;
        }

        for (int position : positions) {
            songs.add(queue.get(position));
        }

        return songs;
    }

    public static List<Integer> createUpNextQueuePositionsForTest(List<Song> queue, int current) {
        List<Integer> positions = new ArrayList<>();
        if (queue == null || queue.size() <= 1 || current < 0 || current >= queue.size()) {
            return positions;
        }

        Song currentSong = queue.get(current);
        for (int position = current + 1; position < queue.size(); position++) {
            if (!isSameSong(currentSong, queue.get(position))) {
                positions.add(position);
            }
        }

        return positions;
    }

    private static boolean isSameSong(Song first, Song second) {
        return first != null
                && second != null
                && first.id != null
                && first.id.equals(second.id);
    }

    public static int getQueuePositionForTest(List<Integer> queuePositions, int adapterPosition) {
        if (adapterPosition == RecyclerView.NO_POSITION
                || queuePositions == null
                || adapterPosition < 0
                || adapterPosition >= queuePositions.size()) {
            return RecyclerView.NO_POSITION;
        }

        return queuePositions.get(adapterPosition);
    }

    protected void setAlpha(SongAdapter.ViewHolder holder, float alpha) {
        if (holder.image != null) {
            holder.image.setAlpha(alpha);
        }

        if (holder.title != null) {
            holder.title.setAlpha(alpha);
        }

        if (holder.text != null) {
            holder.text.setAlpha(alpha);
        }

        if (holder.imageText != null) {
            holder.imageText.setAlpha(alpha);
        }

        if (holder.paletteColorContainer != null) {
            holder.paletteColorContainer.setAlpha(alpha);
        }
    }

    @Override
    public boolean onCheckCanStartDrag(ViewHolder holder, int position, int x, int y) {
        return false;
    }

    @Override
    public ItemDraggableRange onGetItemDraggableRange(@NonNull ViewHolder holder, int position) {
        return null;
    }

    @Override
    public void onMoveItem(int fromPosition, int toPosition) {
        int fromQueuePosition = getQueuePosition(fromPosition);
        int toQueuePosition = getQueuePosition(toPosition);
        if (fromQueuePosition != RecyclerView.NO_POSITION && toQueuePosition != RecyclerView.NO_POSITION) {
            MusicPlayerRemote.moveSong(fromQueuePosition, toQueuePosition);
        }
    }

    @Override
    public boolean onCheckCanDrop(int draggingPosition, int dropPosition) {
        return true;
    }

    @Override
    public void onItemDragStarted(int position) {
        notifyDataSetChanged();
    }

    @Override
    public void onItemDragFinished(int fromPosition, int toPosition, boolean result) {
        notifyDataSetChanged();
    }

    public class ViewHolder extends SongAdapter.ViewHolder implements DraggableItemViewHolder {
        @DraggableItemStateFlags
        private int mDragStateFlags;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            if (imageText != null) {
                imageText.setVisibility(View.GONE);
            }

            if (image != null) {
                image.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected int getSongMenuRes() {
            return R.menu.menu_item_queue_song;
        }

        @Override
        protected boolean onSongMenuItemClick(MenuItem item) {
            if (item.getItemId() == R.id.action_remove_from_queue) {
                int queuePosition = getBoundQueuePosition();
                if (queuePosition != RecyclerView.NO_POSITION) {
                    MusicPlayerRemote.removeFromQueue(queuePosition);
                }
                return true;
            }

            return super.onSongMenuItemClick(item);
        }

        @Override
        public void onClick(View v) {
            if (isActive()) {
                int adapterPosition = getBindingAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;
                toggleChecked(adapterPosition);
                return;
            }

            long now = SystemClock.uptimeMillis();
            if (now - lastPlayClickUptime < PLAY_CLICK_DEBOUNCE_MS) {
                return;
            }

            int queuePosition = getBoundQueuePosition();
            if (queuePosition != RecyclerView.NO_POSITION) {
                lastPlayClickUptime = now;
                MusicPlayerRemote.openQueue(new ArrayList<>(playingQueue), queuePosition, true);
                refreshPlayerUi();
            }
        }

        private void refreshPlayerUi() {
            if (activity instanceof AbsMusicServiceActivity) {
                activity.runOnUiThread(() -> {
                    AbsMusicServiceActivity musicActivity = (AbsMusicServiceActivity) activity;
                    musicActivity.onQueueChanged();
                    musicActivity.onPlayMetadataChanged();
                });
            }
        }

        private int getBoundQueuePosition() {
            Object queuePosition = itemView.getTag(R.id.playing_queue_position);
            if (queuePosition instanceof Integer) {
                return (Integer) queuePosition;
            }

            return getQueuePosition(getBindingAdapterPosition());
        }

        @Override
        public boolean onLongClick(View view) {
            int adapterPosition = getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return false;
            return toggleChecked(adapterPosition);
        }

        @Override
        public void setDragStateFlags(@DraggableItemStateFlags int flags) {
            mDragStateFlags = flags;
        }

        @Override
        @DraggableItemStateFlags
        public int getDragStateFlags() {
            return mDragStateFlags;
        }

        @NonNull
        @Override
        public DraggableItemState getDragState() {
            return new DraggableItemState();
        }
    }
}
