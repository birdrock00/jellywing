package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import club.thatpetbff.gramophone.model.Song;

/**
 * R4, the case the queue itself can hide: the same server-backed song can occupy two queue
 * entries (Add-to-queue on a song that is already queued), and after the server delete both
 * of them name a dead item.
 *
 * <p>Removing only the first match leaves the second one in the queue as a dead entry that
 * fails to play — the user has to find and remove it by hand, which is precisely what "the
 * library and the player agree again" rules out. The queue position lookup is asserted here
 * because {@code removeFromQueueAndAdvance} needs a bound MusicService that a JVM test cannot
 * give it; the source contract in DeleteFromLibraryWiringSourceTest pins that the cleanup
 * helper actually iterates this list.
 */
public class LibraryDeleteDuplicateQueueTest {

    private static Song song(String id) {
        Song song = new Song();
        song.id = id;
        song.title = "QA fixture " + id;
        return song;
    }

    @Test
    public void testEveryEntryOfATwiceQueuedSongIsFound() {
        List<Song> queue = Arrays.asList(song("keep"), song("dead"), song("keep"), song("dead"));

        List<Integer> positions = LibraryDeleteUtil.positionsOfItem(queue, "dead");

        assertEquals("a song queued twice must yield BOTH entries — a leftover entry names a "
                        + "file the server no longer has (R4)",
                Arrays.asList(1, 3), positions);
    }

    /**
     * The LOOKUP being ascending is not enough: the removal loop has to walk the found indices
     * highest-first. Removing a lower index first shifts every later index down by one, so the
     * next removal names a position the queue no longer has, {@code MusicPlayerRemote}
     * .removeFromQueue's {@code 0 <= p < size()} guard silently refuses it, and the second dead
     * entry survives — with the whole test suite green, because nothing else exercised the order.
     * This helper is the order, so the order is now assertable without a bound MusicService.
     */
    @Test
    public void testRemovalOrderIsHighestIndexFirst() {
        assertEquals("two dead entries must be removed highest-index-first — an ascending pass "
                        + "makes the second index out of range and the guard silently skips it, "
                        + "leaving a dead entry in the queue (R4)",
                Arrays.asList(3, 1), LibraryDeleteUtil.removalOrder(Arrays.asList(1, 3)));
        assertEquals("a single dead entry removes at its own index (R4)",
                Arrays.asList(0), LibraryDeleteUtil.removalOrder(Arrays.asList(0)));
        assertTrue("an item with no queue entry must produce no removal at all (R4: no queue edit)",
                LibraryDeleteUtil.removalOrder(new ArrayList<Integer>()).isEmpty());
    }

    @Test
    public void testSingleAndAbsentEntriesAreUnchanged() {
        List<Song> queue = Arrays.asList(song("a"), song("b"), song("a"));

        assertEquals("an entry queued once must yield exactly one index",
                Arrays.asList(0, 2), LibraryDeleteUtil.positionsOfItem(queue, "a"));
        assertTrue("an item that is not queued must yield nothing (R4: no queue edit)",
                LibraryDeleteUtil.positionsOfItem(queue, "absent").isEmpty());
        assertTrue("an empty queue must yield nothing",
                LibraryDeleteUtil.positionsOfItem(new ArrayList<Song>(), "a").isEmpty());
    }
}
