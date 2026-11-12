package club.thatpetbff.gramophone.util;

import android.content.Context;

/**
 * The whole Delete-from-library pipeline (R3-R11): in-flight guard, the single stock
 * ApiClient.DeleteItem call, the pure status-to-outcome mapping, toasts, queue
 * remove-and-advance, favorites refresh, cache purge and suppression-guard cleanup.
 *
 * <p>The mapping and the latch are static and Android-free so the JVM unit tests can
 * force 401/404/transport outcomes that this all-admin QA server cannot produce
 * (.tests.md limitation L1).
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
     * Entry point behind the confirmation dialog's positive button. The request
     * pipeline itself lands with R3 (T4b); for the dialog-first pair (T3) this is
     * intentionally inert — no request, no state change.
     */
    public static void deleteFromLibrary(Context context, String itemId) {
        // T4b (R3/R4/R9): single App.getApiClient().DeleteItem(...) + outcome handling.
    }
}
