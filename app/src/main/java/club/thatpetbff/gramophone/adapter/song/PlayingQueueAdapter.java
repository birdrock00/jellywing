package club.thatpetbff.gramophone.adapter.song;

import android.view.MenuItem;
import android.view.View;
import android.os.SystemClock;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
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
    }

    public void setCurrent(int current) {
        updateQueue(playingQueue, current);
    }

    private void updateQueue(List<Song> queue, int current) {
        // MusicPlayerRemote.getPlayingQueue() returns the LIVE, mutating list reference, so
        // snapshot it before doing any diffing/comparison.
        List<Song> queueCopy = queue == null ? new ArrayList<>() : new ArrayList<>(queue);

        playingQueue.clear();
        playingQueue.addAll(queueCopy);

        this.current = current;

        List<Integer> oldQueuePositions = new ArrayList<>(queuePositions);
        List<Song> oldDataSet = dataSet == null ? new ArrayList<>() : new ArrayList<>(dataSet);

        List<Integer> newQueuePositions = createUpNextQueuePositionsForTest(playingQueue, current);
        List<Song> newDataSet = createSongsForPositions(playingQueue, newQueuePositions);

        queuePositions.clear();
        queuePositions.addAll(newQueuePositions);
        dataSet = newDataSet;

        // The adapter inherits setHasStableIds(true) (required by RecyclerViewDragDropManager for
        // reorder animations) and getItemId() is keyed on song.id.hashCode(). Under stable IDs the
        // RecyclerView reuses ViewHolders for IDs it believes are unchanged, so a bare
        // notifyDataSetChanged() will NOT visibly repopulate the Up Next list when the queue is
        // rebuilt (e.g. picking a language) or the position advances (next-song). Dispatching a real
        // DiffUtil result forces the correct insert/remove/move/change events so every row rebinds.
        DiffUtil.calculateDiff(new QueueDiffCallback(oldDataSet, oldQueuePositions, newDataSet, newQueuePositions))
                .dispatchUpdatesTo(this);
    }

    private static final class QueueDiffCallback extends DiffUtil.Callback {
        private final List<Song> oldDataSet;
        private final List<Integer> oldQueuePositions;
        private final List<Song> newDataSet;
        private final List<Integer> newQueuePositions;

        QueueDiffCallback(List<Song> oldDataSet, List<Integer> oldQueuePositions,
                          List<Song> newDataSet, List<Integer> newQueuePositions) {
            this.oldDataSet = oldDataSet;
            this.oldQueuePositions = oldQueuePositions;
            this.newDataSet = newDataSet;
            this.newQueuePositions = newQueuePositions;
        }

        @Override
        public int getOldListSize() {
            return oldDataSet.size();
        }

        @Override
        public int getNewListSize() {
            return newDataSet.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return isSameSong(oldDataSet.get(oldItemPosition), newDataSet.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Song oldSong = oldDataSet.get(oldItemPosition);
            Song newSong = newDataSet.get(newItemPosition);
            if (!isSameSong(oldSong, newSong)) {
                return false;
            }

            // The row also carries its underlying queue position (used for play/remove/reorder
            // actions), so a change there must rebind even when the rendered song id matches.
            Integer oldQueuePosition = oldItemPosition < oldQueuePositions.size()
                    ? oldQueuePositions.get(oldItemPosition) : null;
            Integer newQueuePosition = newItemPosition < newQueuePositions.size()
                    ? newQueuePositions.get(newItemPosition) : null;
            return oldQueuePosition != null && oldQueuePosition.equals(newQueuePosition);
        }
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
