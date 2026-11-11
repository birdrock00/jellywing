package club.thatpetbff.gramophone.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tiny registry that lets the player notify library screens (notably the
 * Favorites tab) that a favorite was toggled server-side, so they can
 * re-fetch instead of showing a stale list until the next app restart.
 *
 * <p>Listeners are held weakly: fragments register in onViewCreated and
 * unregister in onDestroyView, and a leaked fragment is simply skipped.
 */
public final class FavoritesStore {
    public interface Listener {
        void onFavoritesChanged();
    }

    private static final List<WeakReference<Listener>> listeners = new ArrayList<>();
    private static long version = 0;

    private FavoritesStore() {
    }

    public static synchronized void addListener(Listener listener) {
        if (listener == null) return;
        for (WeakReference<Listener> ref : listeners) {
            if (ref.get() == listener) return;
        }
        listeners.add(new WeakReference<>(listener));
    }

    public static synchronized void removeListener(Listener listener) {
        Iterator<WeakReference<Listener>> it = listeners.iterator();
        while (it.hasNext()) {
            WeakReference<Listener> ref = it.next();
            if (ref.get() == null || ref.get() == listener) {
                it.remove();
            }
        }
    }

    public static synchronized void notifyChanged() {
        version++;
        for (WeakReference<Listener> ref : new ArrayList<>(listeners)) {
            Listener listener = ref.get();
            if (listener != null) {
                listener.onFavoritesChanged();
            }
        }
    }

    public static synchronized long getVersion() {
        return version;
    }
}
