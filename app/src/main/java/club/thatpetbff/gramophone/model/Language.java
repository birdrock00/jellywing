package club.thatpetbff.gramophone.model;

import androidx.annotation.NonNull;

import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.dto.MediaSourceInfo;
import org.jellyfin.apiclient.model.dto.NameIdPair;
import org.jellyfin.apiclient.model.entities.MediaStream;
import org.jellyfin.apiclient.model.entities.MediaStreamType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Language {
    public final String code;
    public final String name;
    public final String englishName;
    public int songCount;

    private static final Map<String, String> ISO3_TO_ISO2 = new HashMap<>();
    private static final Map<String, String> OVERRIDES = new HashMap<>();
    private static final Map<String, String> CODE_TO_ENGLISH = new HashMap<>();

    static {
        for (Locale locale : Locale.getAvailableLocales()) {
            try {
                String iso2 = locale.getLanguage();
                String iso3 = locale.getISO3Language();
                if (!isBlank(iso2) && !isBlank(iso3)) {
                    ISO3_TO_ISO2.put(iso3.toLowerCase(Locale.US), iso2.toLowerCase(Locale.US));
                }
            } catch (Exception ignored) {
            }
        }

        OVERRIDES.put("eng", "en");
        OVERRIDES.put("spa", "es");
        OVERRIDES.put("zho", "zh");
        OVERRIDES.put("chi", "zh");
        OVERRIDES.put("rus", "ru");
        OVERRIDES.put("vie", "vi");
        OVERRIDES.put("und", "und");

        CODE_TO_ENGLISH.put("en", "English");
        CODE_TO_ENGLISH.put("ja", "Japanese");
        CODE_TO_ENGLISH.put("jpn", "Japanese");
        CODE_TO_ENGLISH.put("zh", "Chinese");
        CODE_TO_ENGLISH.put("cmn", "Mandarin");
        CODE_TO_ENGLISH.put("yue", "Cantonese");
        CODE_TO_ENGLISH.put("ko", "Korean");
        CODE_TO_ENGLISH.put("kor", "Korean");
        CODE_TO_ENGLISH.put("es", "Spanish");
        CODE_TO_ENGLISH.put("spa", "Spanish");
        CODE_TO_ENGLISH.put("fr", "French");
        CODE_TO_ENGLISH.put("fra", "French");
        CODE_TO_ENGLISH.put("de", "German");
        CODE_TO_ENGLISH.put("deu", "German");
        CODE_TO_ENGLISH.put("it", "Italian");
        CODE_TO_ENGLISH.put("ita", "Italian");
        CODE_TO_ENGLISH.put("pt", "Portuguese");
        CODE_TO_ENGLISH.put("por", "Portuguese");
        CODE_TO_ENGLISH.put("ru", "Russian");
        CODE_TO_ENGLISH.put("rus", "Russian");
        CODE_TO_ENGLISH.put("ar", "Arabic");
        CODE_TO_ENGLISH.put("ara", "Arabic");
        CODE_TO_ENGLISH.put("hi", "Hindi");
        CODE_TO_ENGLISH.put("hin", "Hindi");
        CODE_TO_ENGLISH.put("vi", "Vietnamese");
        CODE_TO_ENGLISH.put("vie", "Vietnamese");
        CODE_TO_ENGLISH.put("th", "Thai");
        CODE_TO_ENGLISH.put("tha", "Thai");
        CODE_TO_ENGLISH.put("id", "Indonesian");
        CODE_TO_ENGLISH.put("ind", "Indonesian");
        CODE_TO_ENGLISH.put("tr", "Turkish");
        CODE_TO_ENGLISH.put("tur", "Turkish");
        CODE_TO_ENGLISH.put("nl", "Dutch");
        CODE_TO_ENGLISH.put("nld", "Dutch");
        CODE_TO_ENGLISH.put("pl", "Polish");
        CODE_TO_ENGLISH.put("pol", "Polish");
        CODE_TO_ENGLISH.put("sv", "Swedish");
        CODE_TO_ENGLISH.put("swe", "Swedish");
        CODE_TO_ENGLISH.put("und", "Undetermined");
    }

    public Language(String rawCode) {
        this.code = normalizeCode(rawCode);
        this.name = getDisplayName(rawCode, code);
        this.englishName = getEnglishDisplayName(rawCode, code);
    }

    public String getGenreName() {
        return englishName;
    }

    public static String getLanguageGenre(Language language) {
        return "Language: " + language.getGenreName();
    }

    public boolean matches(BaseItemDto itemDto) {
        for (String language : getAudioLanguages(itemDto)) {
            if (code.equals(normalizeCode(language))) {
                return true;
            }
        }

        return false;
    }

    public static List<String> getAudioLanguages(BaseItemDto itemDto) {
        List<String> languages = new ArrayList<>();
        if (itemDto == null) return languages;

        addAudioLanguages(languages, itemDto.getMediaStreams());

        if (itemDto.getMediaSources() != null) {
            for (MediaSourceInfo source : itemDto.getMediaSources()) {
                if (source == null) continue;

                MediaStream defaultStream = source.getDefaultAudioStream();
                if (defaultStream != null) {
                    addAudioLanguage(languages, defaultStream);
                }

                addAudioLanguages(languages, source.getMediaStreams());
            }
        }

        if (!languages.isEmpty()) {
            return languages;
        }

        addDetectedLanguage(languages, itemDto);
        return languages;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null) return "";

        String normalized = rawCode.trim().toLowerCase(Locale.US).replace('_', '-');
        int separator = normalized.indexOf('-');
        if (separator > 0) {
            normalized = normalized.substring(0, separator);
        }

        normalized = normalized.replaceAll("[^a-z]", "");
        if (OVERRIDES.containsKey(normalized)) {
            return OVERRIDES.get(normalized);
        }

        if (normalized.length() == 3 && ISO3_TO_ISO2.containsKey(normalized)) {
            return ISO3_TO_ISO2.get(normalized);
        }

        return normalized;
    }

    private static String getDisplayName(String rawCode, String normalizedCode) {
        if ("und".equals(normalizedCode)) {
            return "Undetermined";
        }

        if (normalizedCode.length() == 2) {
            Locale locale = new Locale(normalizedCode);
            String displayName = locale.getDisplayLanguage();
            if (!isBlank(displayName) && !displayName.equalsIgnoreCase(normalizedCode)) {
                return displayName;
            }
        }

        if (!isBlank(rawCode)) {
            return rawCode.trim().toUpperCase(Locale.US);
        }

        return normalizedCode.toUpperCase(Locale.US);
    }

    private static String getEnglishDisplayName(String rawCode, String normalizedCode) {
        String mapped = CODE_TO_ENGLISH.get(normalizedCode);
        if (mapped != null) {
            return mapped;
        }

        if (normalizedCode.length() == 2) {
            Locale locale = new Locale(normalizedCode);
            String displayName = locale.getDisplayLanguage(Locale.ENGLISH);
            if (!isBlank(displayName) && !displayName.equalsIgnoreCase(normalizedCode)) {
                return displayName;
            }
        }

        if (!isBlank(rawCode)) {
            return rawCode.trim().toUpperCase(Locale.US);
        }

        return normalizedCode.toUpperCase(Locale.US);
    }

    private static void addAudioLanguages(List<String> languages, List<MediaStream> streams) {
        if (streams == null) return;

        for (MediaStream stream : streams) {
            addAudioLanguage(languages, stream);
        }
    }

    private static void addAudioLanguage(List<String> languages, MediaStream stream) {
        if (stream == null || isBlank(stream.getLanguage())) return;

        if (stream.getType() == MediaStreamType.Audio) {
            languages.add(stream.getLanguage());
        }
    }

    private static void addDetectedLanguage(List<String> languages, BaseItemDto itemDto) {
        String metadataLanguage = normalizeCode(itemDto.getPreferredMetadataLanguage());
        if (isUsableDetectedCode(metadataLanguage)) {
            languages.add(metadataLanguage);
            return;
        }

        String detectedLanguage = detectTextLanguage(itemDto);
        if (!isBlank(detectedLanguage)) {
            languages.add(detectedLanguage);
        }
    }

    private static String detectTextLanguage(BaseItemDto itemDto) {
        List<String> primaryTexts = new ArrayList<>();
        addText(primaryTexts, itemDto.getName());
        addText(primaryTexts, itemDto.getOriginalTitle());
        addText(primaryTexts, itemDto.getAlbum());
        addText(primaryTexts, itemDto.getAlbumArtist());
        addTexts(primaryTexts, itemDto.getArtists());
        addNames(primaryTexts, itemDto.getArtistItems());
        addNames(primaryTexts, itemDto.getAlbumArtists());

        List<String> secondaryTexts = new ArrayList<>(primaryTexts);
        addTexts(secondaryTexts, itemDto.getTags());
        addTexts(secondaryTexts, itemDto.getGenres());
        addTexts(secondaryTexts, itemDto.getTaglines());
        addTexts(secondaryTexts, itemDto.getKeywords());
        addText(secondaryTexts, itemDto.getSortName());
        addText(secondaryTexts, itemDto.getForcedSortName());
        addText(secondaryTexts, itemDto.getOverview());
        addText(secondaryTexts, itemDto.getShortOverview());

        String scriptLanguage = detectScriptLanguage(secondaryTexts);
        if (!isBlank(scriptLanguage)) {
            return scriptLanguage;
        }

        String latinLanguage = detectLatinLanguage(secondaryTexts);
        if (!isBlank(latinLanguage)) {
            return latinLanguage;
        }

        if (looksLikeEnglish(primaryTexts)) {
            return "en";
        }

        return "";
    }

    private static String detectScriptLanguage(List<String> texts) {
        boolean hasHan = false;

        for (String text : texts) {
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                offset += Character.charCount(codePoint);

                Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
                if (block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA
                        || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS) {
                    return "ja";
                }
                if (block == Character.UnicodeBlock.HANGUL_SYLLABLES || block == Character.UnicodeBlock.HANGUL_JAMO
                        || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                    return "ko";
                }
                if (block == Character.UnicodeBlock.CYRILLIC || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                        || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B) {
                    return "ru";
                }
                if (block == Character.UnicodeBlock.DEVANAGARI) {
                    return "hi";
                }
                if (block == Character.UnicodeBlock.ARABIC || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                        || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                        || block == Character.UnicodeBlock.ARABIC_EXTENDED_A) {
                    return "ar";
                }
                if (block == Character.UnicodeBlock.HEBREW) {
                    return "he";
                }
                if (block == Character.UnicodeBlock.THAI) {
                    return "th";
                }
                if (isHan(codePoint, block)) {
                    hasHan = true;
                }
            }
        }

        return hasHan ? "zh" : "";
    }

    private static String detectLatinLanguage(List<String> texts) {
        String combined = joinLowercase(texts);
        if (isBlank(combined)) return "";

        if (combined.matches(".*[đăâêôơưĐĂÂÊÔƠƯ].*")) {
            return "vi";
        }

        if (combined.matches(".*[¿¡ñÑ].*") || containsSpanishMarker(combined)) {
            return "es";
        }

        return "";
    }

    private static boolean looksLikeEnglish(List<String> texts) {
        int asciiLetterTexts = 0;
        int totalLetters = 0;
        int asciiLetters = 0;
        Set<String> normalizedTexts = new HashSet<>();

        for (String text : texts) {
            int textAsciiLetters = 0;
            int textLetters = 0;
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                offset += Character.charCount(codePoint);

                if (Character.isLetter(codePoint)) {
                    textLetters++;
                    totalLetters++;
                    if (codePoint >= 'A' && codePoint <= 'Z' || codePoint >= 'a' && codePoint <= 'z') {
                        textAsciiLetters++;
                        asciiLetters++;
                    }
                }
            }

            if (textAsciiLetters >= 3 && textAsciiLetters == textLetters) {
                asciiLetterTexts++;
                normalizedTexts.add(text.trim().toLowerCase(Locale.US));
            }
        }

        return asciiLetterTexts >= 2 && normalizedTexts.size() >= 2 && totalLetters >= 6 && asciiLetters == totalLetters;
    }

    private static boolean containsSpanishMarker(String text) {
        String normalized = " " + text + " ";
        if (normalized.matches(".*[áéíóúüÁÉÍÓÚÜ].*")) {
            return containsAnyWord(normalized, " el ", " la ", " los ", " las ", " una ", " para ", " amor ", " corazon ", " corazón ");
        }

        return containsAnyWord(normalized, " de amor ", " por ti ", " te quiero ", " contigo ", " sin ti ", " mi vida ");
    }

    private static boolean containsAnyWord(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsableDetectedCode(String code) {
        return !isBlank(code) && !"und".equals(code);
    }

    private static boolean isHan(int codePoint, Character.UnicodeBlock block) {
        if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT) {
            return true;
        }

        return codePoint >= 0x20000 && codePoint <= 0x2EBEF;
    }

    private static String joinLowercase(List<String> texts) {
        StringBuilder builder = new StringBuilder();
        for (String text : texts) {
            builder.append(' ').append(text);
        }
        return builder.toString().toLowerCase(Locale.US);
    }

    private static void addText(List<String> texts, String text) {
        if (!isBlank(text)) {
            texts.add(text);
        }
    }

    private static void addTexts(List<String> texts, List<String> values) {
        if (values == null) return;

        for (String value : values) {
            addText(texts, value);
        }
    }

    private static void addNames(List<String> texts, List<NameIdPair> values) {
        if (values == null) return;

        for (NameIdPair value : values) {
            if (value == null) continue;
            addText(texts, value.getName());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Language language = (Language) o;
        return code.equals(language.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return code;
    }
}
