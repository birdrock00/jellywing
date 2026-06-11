package club.thatpetbff.gramophone.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.dto.MediaSourceInfo;
import org.jellyfin.apiclient.model.entities.MediaStream;
import org.jellyfin.apiclient.model.entities.MediaStreamType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LanguageTest {
    @Test
    public void normalizesCommonJellyfinLanguageCodes() {
        assertEquals("en", Language.normalizeCode("eng"));
        assertEquals("en", Language.normalizeCode("EN_us"));
        assertEquals("es", Language.normalizeCode("spa"));
        assertEquals("zh", Language.normalizeCode("zho"));
        assertEquals("zh", Language.normalizeCode("chi"));
        assertEquals("vi", Language.normalizeCode("vie"));
        assertEquals("und", Language.normalizeCode("und"));
        assertEquals("", Language.normalizeCode(null));
        assertEquals("", Language.normalizeCode("  "));
    }

    @Test
    public void extractsOnlyAudioLanguagesFromItemsAndMediaSources() {
        BaseItemDto item = new BaseItemDto();
        item.setMediaStreams(new ArrayList<>(Arrays.asList(
                stream(MediaStreamType.Audio, "eng"),
                stream(MediaStreamType.Video, "spa"),
                stream(MediaStreamType.Subtitle, "fre"))));

        MediaSourceInfo source = new MediaSourceInfo();
        source.setDefaultAudioStreamIndex(0);
        source.setMediaStreams(new ArrayList<>(Arrays.asList(
                stream(MediaStreamType.Audio, "jpn"),
                stream(MediaStreamType.Audio, "deu"),
                stream(MediaStreamType.Video, "ita"))));
        item.setMediaSources(new ArrayList<>(Arrays.asList(source)));

        List<String> languages = Language.getAudioLanguages(item);

        assertTrue(languages.contains("eng"));
        assertTrue(languages.contains("jpn"));
        assertTrue(languages.contains("deu"));
        assertFalse(languages.contains("spa"));
        assertFalse(languages.contains("fre"));
        assertFalse(languages.contains("ita"));
    }

    @Test
    public void matchesAudioLanguageAfterNormalization() {
        BaseItemDto item = new BaseItemDto();
        item.setMediaStreams(new ArrayList<>(Arrays.asList(stream(MediaStreamType.Audio, "EN-us"))));

        assertTrue(new Language("eng").matches(item));
        assertFalse(new Language("spa").matches(item));
    }

    @Test
    public void streamLanguageTakesPriorityOverTextFallback() {
        BaseItemDto item = textItem("東京", "Artist", "Album");
        item.setMediaStreams(new ArrayList<>(Arrays.asList(stream(MediaStreamType.Audio, "spa"))));

        List<String> languages = Language.getAudioLanguages(item);

        assertEquals(1, languages.size());
        assertEquals("es", Language.normalizeCode(languages.get(0)));
        assertTrue(new Language("es").matches(item));
        assertFalse(new Language("ja").matches(item));
    }

    @Test
    public void detectsRequestedScriptFallbackLanguages() {
        assertDetectedLanguage("zh", textItem("月亮代表我的心", "", ""));
        assertDetectedLanguage("ja", textItem("さくら", "", ""));
        assertDetectedLanguage("ru", textItem("Моя любовь", "", ""));
        assertDetectedLanguage("ko", textItem("사랑", "", ""));
        assertDetectedLanguage("hi", textItem("दिल", "", ""));
        assertDetectedLanguage("ar", textItem("حب", "", ""));
        assertDetectedLanguage("he", textItem("אהבה", "", ""));
        assertDetectedLanguage("th", textItem("รัก", "", ""));
    }

    @Test
    public void detectsVietnameseSpanishAndConservativeEnglishFallbacks() {
        assertDetectedLanguage("vi", textItem("Tinh yeu", "Son Tung", "Chung ta", "đừng làm trái tim anh đau"));
        assertDetectedLanguage("es", textItem("Canción de amor", "Artista", "Album"));
        assertDetectedLanguage("en", textItem("Love Song", "Singer", "Album"));

        BaseItemDto titleOnlyAscii = textItem("Intro", "", "");
        assertTrue(Language.getAudioLanguages(titleOnlyAscii).isEmpty());
    }

    @Test
    public void genreNameUsesEnglishDisplayNameForJellyfinQuery() {
        assertEquals("Chinese", new Language("zh").getGenreName());
        assertEquals("Japanese", new Language("ja").getGenreName());
        assertEquals("English", new Language("en").getGenreName());
        assertEquals("Korean", new Language("ko").getGenreName());
        assertEquals("Spanish", new Language("es").getGenreName());
    }

    @Test
    public void genreNameResolvesCodesOutsideJavaLocaleDatabase() {
        assertEquals("Cantonese", new Language("yue").getGenreName());
        assertEquals("Mandarin", new Language("cmn").getGenreName());
        assertEquals("Undetermined", new Language("und").getGenreName());
    }

    @Test
    public void languageGenreMatchesJellyfinGenreFormat() {
        assertEquals("Language: Chinese", Language.getLanguageGenre(new Language("zh")));
        assertEquals("Language: Japanese", Language.getLanguageGenre(new Language("ja")));
        assertEquals("Language: English", Language.getLanguageGenre(new Language("eng")));
        assertEquals("Language: Cantonese", Language.getLanguageGenre(new Language("yue")));
        assertEquals("Language: Mandarin", Language.getLanguageGenre(new Language("cmn")));
    }

    private static void assertDetectedLanguage(String expectedCode, BaseItemDto item) {
        List<String> languages = Language.getAudioLanguages(item);

        assertEquals(1, languages.size());
        assertEquals(expectedCode, Language.normalizeCode(languages.get(0)));
        assertTrue(new Language(expectedCode).matches(item));
    }

    private static BaseItemDto textItem(String name, String artist, String album) {
        return textItem(name, artist, album, null);
    }

    private static BaseItemDto textItem(String name, String artist, String album, String originalTitle) {
        BaseItemDto item = new BaseItemDto();
        item.setName(name);
        item.setAlbum(album);
        item.setOriginalTitle(originalTitle);
        item.setArtists(new ArrayList<>(Arrays.asList(artist)));
        return item;
    }

    private static MediaStream stream(MediaStreamType type, String language) {
        MediaStream stream = new MediaStream();
        stream.setType(type);
        stream.setLanguage(language);
        return stream;
    }
}
