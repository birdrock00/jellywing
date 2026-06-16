package club.thatpetbff.gramophone.fragments.library;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LanguageFragmentQueueBehaviorTest {
    @Test
    public void languageClicksOpenSampledQueueAtFirstSong() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/fragments/library/LanguageFragment.java");

        assertTrue(source.contains("MusicPlayerRemote.clearQueue();"));
        assertTrue(source.contains("new LanguageQueueSampler<>(QUEUE_LIMIT, new Random(), Song::new)"));
        assertTrue(source.contains("Collections.shuffle(songs);"));
        assertTrue(source.contains("MusicPlayerRemote.openQueue(songs, 0, true);"));
        assertFalse(source.contains("MusicPlayerRemote.openAndShuffleQueue(songs, true);"));
    }

    @Test
    public void languageQueueSamplesCurrentSongPlusVisibleUpNextRows() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/fragments/library/LanguageFragment.java");

        assertTrue(source.contains("private static final int VISIBLE_UP_NEXT_LIMIT = 50;"));
        assertTrue(source.contains("private static final int QUEUE_LIMIT = VISIBLE_UP_NEXT_LIMIT + 1;"));
        assertTrue(source.contains("if (songs.size() < QUEUE_LIMIT)"));
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        for (Path current = start; current != null; current = current.getParent()) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }

        throw new IOException("Unable to locate " + relativePath + " from " + start);
    }
}
