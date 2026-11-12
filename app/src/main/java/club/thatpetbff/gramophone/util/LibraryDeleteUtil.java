package club.thatpetbff.gramophone.util;

import android.content.Context;
import android.widget.Toast;

import java.util.List;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.model.Song;

import org.jellyfin.apiclient.interaction.EmptyResponse;

/**
 * The whole Delete-from-library pipeline (R3-R11): the single stock API call, the pure
 * status-to-outcome mapping, toasts, queue remove-and-advance, favorites refresh, cache
 * purge and suppression-guard cleanup.
 *
 * <p>The mapping and the latch are static and side-effect-free so the JVM unit tests can
 * force 401/404/transport outcomes that this all-admin QA server cannot produce
 * (.tests.md limitation L1). UI code only turns outcomes into toasts and state changes.
 */
public final class LibraryDeleteUtil {

    /** Outcome of one delete request, mapped once, off the UI (plan note 4). */
    public enum DeleteOutcome {
        SUCCESS,
        PERMISSION_DENIED,
        ALREADY_GONE,
        NETWORK_FAILURE,
        SERVER_FAILURE
    }

    private LibraryDeleteUtil() {
    }

    /**
     * Entry point behind the confirmation dialog's positive button. Exactly one stock
     * delete for the item id rides the existing apiclient (R3) — the same call
     * PlaylistUtil already uses; no new HTTP plumbing.
     */
    public static void deleteFromLibrary(Context context, String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) {
            return;
        }
        App.getApiClient().DeleteItem(itemId, new EmptyResponse() {
            @Override
            public void onResponse() {
                handleOutcome(itemId, DeleteOutcome.SUCCESS);
            }

            @Override
            public void onError(Exception exception) {
                handleOutcome(itemId, mapOutcome(exception));
            }
        });
    }

    /**
     * Pure mapping from a failed delete request to an outcome. The apiclient surfaces
     * HTTP failures as {@link org.jellyfin.apiclient.model.net.HttpException} with the
     * status already read off the network response, so the UI branches on the outcome,
     * never on transport types. R5: 401 is the item-level permission refusal this fork
     * returns for DELETE Items/{id}.
     */
    public static DeleteOutcome mapOutcome(Throwable error) {
        Integer status = null;
        if (error instanceof org.jellyfin.apiclient.model.net.HttpException) {
            status = ((org.jellyfin.apiclient.model.net.HttpException) error).getStatusCode();
        }
        if (status != null && status >= 200 && status < 300) {
            return DeleteOutcome.SUCCESS;
        }
        if (status != null && status == 401) {
            return DeleteOutcome.PERMISSION_DENIED;
        }
        if (status != null && status == 404) {
            // R6: the item is genuinely not on the server anymore — its own outcome with
            // its own toast and the R4/R9 client cleanup (no success claim, plan note 5).
            return DeleteOutcome.ALREADY_GONE;
        }
        return DeleteOutcome.SERVER_FAILURE;
    }

    /** Turns one outcome into its toast and (success/404 only) the client-side cleanup. */
    static void handleOutcome(String itemId, DeleteOutcome outcome) {
        switch (outcome) {
            case SUCCESS:
                toast(R.string.delete_from_library_success);
                removeFromQueueAndAdvance(itemId);
                FavoritesStore.notifyChanged();
                break;
            case ALREADY_GONE:
                // Quiet success minus the claim (plan note 5): same queue/refresh cleanup,
                // its own toast, but no cache purge and no suppression-guard clear — R10
                // and R11 are success-only, deliberately.
                toast(R.string.delete_from_library_gone);
                removeFromQueueAndAdvance(itemId);
                FavoritesStore.notifyChanged();
                break;
            case PERMISSION_DENIED:
                toast(R.string.delete_from_library_denied);
                break;
            case NETWORK_FAILURE:
            case SERVER_FAILURE:
            default:
                toast(R.string.delete_from_library_failed);
                break;
        }
    }

    /**
     * R4 remove-current-and-advance (plan note 3): QueueManager re-bases the index for
     * removals before the current one but has no current-entry semantics, so we drop the
     * deleted entry and start whatever now occupies that index. A lone deleted entry
     * stops playback cleanly instead of leaving the surface on a dead item.
     */
    private static void removeFromQueueAndAdvance(String itemId) {
        int pos = indexOfItem(MusicPlayerRemote.getPlayingQueue(), itemId);
        if (pos < 0) {
            return;
        }
        boolean wasCurrent = pos == MusicPlayerRemote.getPosition();
        MusicPlayerRemote.removeFromQueue(pos);
        List<Song> remaining = MusicPlayerRemote.getPlayingQueue();
        if (remaining.isEmpty()) {
            MusicPlayerRemote.clearQueue();
            return;
        }
        if (wasCurrent) {
            MusicPlayerRemote.playSongAt(Math.min(pos, remaining.size() - 1));
        }
    }


    private static int indexOfItem(List<Song> queue, String itemId) {
        for (int i = 0; i < queue.size(); i++) {
            if (itemId.equals(queue.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private static void toast(int stringRes) {
        try {
            Toast.makeText(App.getInstance(), stringRes, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}
