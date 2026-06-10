package club.thatpetbff.gramophone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.model.User;
import club.thatpetbff.gramophone.util.MusicUtil;

import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.entities.ImageType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

public class ModelSmokeTest {
    @Test
    public void newSongsReceiveUniqueStableIds() {
        Song first = new Song();
        Song second = new Song();

        assertNotNull(first.id);
        assertNotNull(second.id);
        assertNotEquals(first.id, second.id);
        assertEquals(first.id, first.toString());
    }

    @Test
    public void songIdentityIsBasedOnlyOnId() {
        Song first = new Song();
        first.id = "song-id";
        first.title = "Before";

        Song second = new Song();
        second.id = "song-id";
        second.title = "After";

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        second.id = "other-song-id";
        assertNotEquals(first, second);
    }

    @Test
    public void songUsesAlbumPrimaryImageWhenAvailable() {
        BaseItemDto item = createSongItem("song-id");
        item.setAlbumId("album-id");
        item.setAlbumPrimaryImageTag("album-image-tag");
        item.setImageTags(imageTagsWithPrimary());

        Song song = new Song(item);

        assertEquals("album-id", song.primary);
    }

    @Test
    public void songUsesOwnPrimaryImageWhenAlbumImageIsMissing() {
        BaseItemDto item = createSongItem("song-id");
        item.setImageTags(imageTagsWithPrimary());

        Song song = new Song(item);

        assertEquals("song-id", song.primary);
    }

    @Test
    public void songUsesParentPrimaryImageWhenAvailable() {
        BaseItemDto item = createSongItem("song-id");
        item.setParentPrimaryImageItemId("parent-id");

        Song song = new Song(item);

        assertEquals("parent-id", song.primary);
    }

    @Test
    public void songLeavesPrimaryImageEmptyWhenNoImageIsAvailable() {
        BaseItemDto item = createSongItem("song-id");

        Song song = new Song(item);

        assertNull(song.primary);
    }

    @Test
    public void songArtworkItemFallsBackToAlbumIdThenSongId() {
        Song song = new Song();
        song.id = "song-id";
        song.albumId = "album-id";

        assertEquals("album-id", song.getArtworkItemId());

        song.primary = "primary-id";
        assertEquals("primary-id", song.getArtworkItemId());

        song.primary = null;
        song.albumId = null;
        assertEquals("song-id", song.getArtworkItemId());
    }

    @Test
    public void songDisplayTitleUsesTitleThenFilenameThenId() {
        Song song = new Song();
        song.id = "song-id";
        song.title = "  Visible Title  ";
        song.path = "/music/album/file-title.flac";

        assertEquals("Visible Title", song.getDisplayTitle());

        song.title = " ";
        assertEquals("file-title", song.getDisplayTitle());

        song.path = "";
        assertEquals("song-id", song.getDisplayTitle());

        song.id = "";
        assertEquals("Unknown song", song.getDisplayTitle());
    }

    @Test
    public void musicUtilAlwaysReturnsNonBlankSongTitleFallback() {
        assertEquals("Unknown song", MusicUtil.getSongTitle(null));

        Song song = new Song();
        song.id = "fallback-id";
        song.title = " ";
        song.path = " ";

        assertEquals("fallback-id", MusicUtil.getSongTitle(song));
    }

    @Test
    public void musicUtilSongInfoCombinesArtistAndAlbumWithoutNullText() {
        Song song = new Song();
        song.artistName = "Artist";
        song.albumName = "Album";

        assertEquals("Artist  •  Album", MusicUtil.getSongInfoString(song));
        assertEquals("", MusicUtil.getSongInfoString(null));
    }

    @Test
    public void userDefaultConstructorCreatesEmptyPersistableUser() {
        User user = new User();

        assertNotNull(user.id);
        assertFalse(user.id.trim().isEmpty());
        assertTrue("name is optional until authentication fills it", user.name == null);
        assertTrue("server is optional until authentication fills it", user.server == null);
        assertTrue("token is optional until authentication fills it", user.token == null);
    }

    private static BaseItemDto createSongItem(String id) {
        BaseItemDto item = new BaseItemDto();
        item.setId(id);
        item.setName("Song");
        item.setArtistItems(new ArrayList<>());
        item.setAlbumArtists(new ArrayList<>());
        return item;
    }

    private static HashMap<ImageType, String> imageTagsWithPrimary() {
        HashMap<ImageType, String> imageTags = new HashMap<>();
        imageTags.put(ImageType.Primary, "primary-tag");
        return imageTags;
    }
}
