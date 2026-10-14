package club.thatpetbff.gramophone.service.playback;

/**
 * Bounds how many consecutive skip-to-next actions ExoPlayer's error handler
 * may trigger.
 *
 * <p>When a single media item fails to load it is reasonable to move on to
 * the next item. But when every item fails in a row (logged out, server
 * offline, network down) an uncapped skip loop walks the entire queue in a
 * few seconds and stops on the last item, leaving playback in a state the
 * user can only escape by killing and relaunching the app. Capping the
 * budget makes the player stay on the failed item so the next play tap can
 * retry it with a freshly refreshed stream URL.
 */
public final class ErrorSkipBudget {
    public static final int MAX_CONSECUTIVE_SKIPS = 3;

    private int usedSkips = 0;

    public boolean allowSkip() {
        return usedSkips < MAX_CONSECUTIVE_SKIPS;
    }

    public void recordSkip() {
        usedSkips++;
    }

    public void reset() {
        usedSkips = 0;
    }
}
