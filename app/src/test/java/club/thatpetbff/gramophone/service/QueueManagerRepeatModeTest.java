package club.thatpetbff.gramophone.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import club.thatpetbff.gramophone.model.Song;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class QueueManagerRepeatModeTest {
    @Test
    public void repeatAllRemainsOnWhenNextSongIsSelected() throws Exception {
        QueueManager queueManager = createQueueManager();
        queueManager.setPlayingQueueAndPosition(songs("first", "second", "third"), 0);
        setRepeatMode(queueManager, QueueManager.REPEAT_MODE_ALL);

        queueManager.setNextPosition();

        assertEquals(1, queueManager.getPosition());
        assertEquals(QueueManager.REPEAT_MODE_ALL, queueManager.getRepeatMode());

        queueManager.setNextPosition();

        assertEquals(2, queueManager.getPosition());
        assertEquals(QueueManager.REPEAT_MODE_ALL, queueManager.getRepeatMode());
    }

    @Test
    public void repeatAllWrapsAtQueueEndWithoutTurningOff() throws Exception {
        QueueManager queueManager = createQueueManager();
        queueManager.setPlayingQueueAndPosition(songs("first", "second", "third"), 2);
        setRepeatMode(queueManager, QueueManager.REPEAT_MODE_ALL);

        queueManager.setNextPosition();

        assertEquals(0, queueManager.getPosition());
        assertEquals(QueueManager.REPEAT_MODE_ALL, queueManager.getRepeatMode());
    }

    @Test
    public void selectingSpecificSongDoesNotTurnRepeatOff() throws Exception {
        QueueManager queueManager = createQueueManager();
        queueManager.setPlayingQueueAndPosition(songs("first", "second", "third"), 0);
        setRepeatMode(queueManager, QueueManager.REPEAT_MODE_ALL);

        queueManager.setPosition(2);

        assertEquals(2, queueManager.getPosition());
        assertEquals(QueueManager.REPEAT_MODE_ALL, queueManager.getRepeatMode());
    }

    @Test
    public void repeatButtonCyclesOffToAllToOneAndBackOff() throws Exception {
        QueueManager queueManager = createQueueManager();

        setRepeatMode(queueManager, QueueManager.REPEAT_MODE_NONE);
        queueManager.cycleRepeatMode();
        assertEquals(QueueManager.REPEAT_MODE_ALL, queueManager.getRepeatMode());

        queueManager.cycleRepeatMode();
        assertEquals(QueueManager.REPEAT_MODE_THIS, queueManager.getRepeatMode());

        queueManager.cycleRepeatMode();
        assertEquals(QueueManager.REPEAT_MODE_NONE, queueManager.getRepeatMode());
    }

    @Test
    public void fullPlayerRepeatButtonsAreWiredToCycleRepeatMode() throws IOException {
        assertRepeatButtonWiring("app/src/main/java/club/thatpetbff/gramophone/fragments/player/card/CardPlayerPlaybackControlsFragment.java");
        assertRepeatButtonWiring("app/src/main/java/club/thatpetbff/gramophone/fragments/player/flat/FlatPlayerPlaybackControlsFragment.java");
    }

    private static QueueManager createQueueManager() {
        return new QueueManager(null, new QueueManager.QueueCallbacks() {
            @Override
            public void onQueueChanged() {
            }

            @Override
            public void onRepeatModeChanged() {
            }

            @Override
            public void onShuffleModeChanged() {
            }
        });
    }

    private static List<Song> songs(String... ids) {
        return Arrays.stream(ids)
                .map(QueueManagerRepeatModeTest::song)
                .toList();
    }

    private static Song song(String id) {
        Song song = new Song();
        song.id = id;
        song.title = id;
        return song;
    }

    private static void setRepeatMode(QueueManager queueManager, int repeatMode) throws Exception {
        Field field = QueueManager.class.getDeclaredField("repeatMode");
        field.setAccessible(true);
        field.setInt(queueManager, repeatMode);
    }

    private static void assertRepeatButtonWiring(String relativePath) throws IOException {
        String source = readProjectFile(relativePath);

        assertTrue(source.contains("binding.playerRepeatButton.setOnClickListener(v ->"));
        assertTrue(source.contains("MusicPlayerRemote.cycleRepeatMode()"));
        assertTrue(source.contains("public void onRepeatModeChanged()"));
        assertTrue(source.contains("updateRepeatState();"));
        assertTrue(source.contains("binding.playerRepeatButton.setImageResource(R.drawable.ic_repeat_one_white_24dp);"));
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
