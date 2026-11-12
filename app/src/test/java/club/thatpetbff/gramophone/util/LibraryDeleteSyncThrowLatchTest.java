package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * R8's failure mode under the worst-case timing: the request call itself throws
 * synchronously instead of calling back.
 *
 * <p>The latch is armed <em>before</em> {@code App.getApiClient().DeleteItem(...)}, so an
 * un-guarded throw (no client bound yet, an apiclient {@code IllegalArgumentException} for a
 * malformed url) escapes into the dialog's click handler and leaves that item id latched for
 * the life of the process — every later Delete-from-library on the same song silently does
 * nothing, with no toast and no way out but a process restart. Callback outcomes already
 * release the latch first thing in {@code handleOutcome}; this is the only leak.
 *
 * <p>A JVM unit test reproduces the shape exactly: nothing binds an ApiClient here, so the
 * call throws on the spot, which is precisely the path under test.
 */
public class LibraryDeleteSyncThrowLatchTest {

    @Test
    public void testSynchronousThrowReleasesTheLatchAndNeverEscapes() {
        String itemId = "sync-throw-" + System.nanoTime();

        boolean escaped = false;
        try {
            LibraryDeleteUtil.deleteFromLibrary(null, itemId);
        } catch (Exception e) {
            escaped = true;
        }

        // First, the safety claim: the id must not stay latched behind a dead request.
        assertTrue("a synchronous throw must release the R8 latch — otherwise this song can "
                        + "never be deleted again for the life of the process",
                LibraryDeleteUtil.tryBeginDelete(itemId));
        LibraryDeleteUtil.endDelete(itemId);

        // Second, the user-facing claim: a crash must not ride out of the click handler.
        assertFalse("a synchronous API throw must be contained and toasted, not thrown into "
                + "the dialog's positive-button handler", escaped);
    }

    @Test
    public void testADeleteAfterAContainedThrowIsNotIgnored() {
        String itemId = "sync-throw-retry-" + System.nanoTime();

        // First activation: the request path throws; the pipeline must leave it terminal.
        swallow(() -> LibraryDeleteUtil.deleteFromLibrary(null, itemId));

        // A retry is a genuinely new request, so it runs the same path and again leaves the
        // latch free. A wedged latch would return silently here and stay armed forever.
        swallow(() -> LibraryDeleteUtil.deleteFromLibrary(null, itemId));

        assertTrue("after two contained activations the latch must be free (R8)",
                LibraryDeleteUtil.tryBeginDelete(itemId));
        LibraryDeleteUtil.endDelete(itemId);
    }

    private static void swallow(Runnable action) {
        try {
            action.run();
        } catch (Exception ignored) {
            // Asserted on explicitly in the first case; here the pair only needs the
            // post-conditions, which a wedged latch fails.
        }
    }
}
