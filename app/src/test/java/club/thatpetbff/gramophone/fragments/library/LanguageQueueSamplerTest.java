package club.thatpetbff.gramophone.fragments.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import club.thatpetbff.gramophone.model.Language;

import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.entities.MediaStream;
import org.jellyfin.apiclient.model.entities.MediaStreamType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LanguageQueueSamplerTest {
    @Test
    public void keepsEmptySampleWhenNothingMatches() {
        LanguageQueueSampler<String> sampler = new LanguageQueueSampler<>(50, new Random(1), BaseItemDto::getId);
        Language language = new Language("zh");

        for (int i = 0; i < 8; i++) {
            sampler.consider(language, item("en-" + i, "en"));
        }

        assertEquals(0, sampler.getMatchingItemCount());
        assertEquals(0, sampler.getSample().size());
    }

    @Test
    public void keepsLowCountMatchesWithoutPadding() {
        LanguageQueueSampler<String> sampler = new LanguageQueueSampler<>(50, new Random(1), BaseItemDto::getId);
        Language language = new Language("zh");

        for (int i = 0; i < 12; i++) {
            sampler.consider(language, item("zh-" + i, "zh"));
        }
        for (int i = 0; i < 8; i++) {
            sampler.consider(language, item("en-" + i, "en"));
        }

        assertEquals(12, sampler.getMatchingItemCount());
        assertEquals(12, sampler.getSample().size());
        for (String id : sampler.getSample()) {
            assertFalse(id.startsWith("en-"));
        }
    }

    @Test
    public void samplesAcrossAllMatchingItemsAfterLimitIsReached() {
        LanguageQueueSampler<String> sampler = new LanguageQueueSampler<>(
                3,
                new FixedRandom(2, 4, 0),
                BaseItemDto::getId);
        Language language = new Language("zh");

        for (int i = 0; i < 6; i++) {
            sampler.consider(language, item("zh-" + i, "zh"));
        }

        List<String> sample = sampler.getSample();

        assertEquals(6, sampler.getMatchingItemCount());
        assertEquals(3, sample.size());
        assertEquals("zh-5", sample.get(0));
        assertEquals("zh-1", sample.get(1));
        assertEquals("zh-3", sample.get(2));
    }

    private static BaseItemDto item(String id, String languageCode) {
        BaseItemDto item = new BaseItemDto();
        item.setId(id);
        item.setMediaStreams(new ArrayList<>());
        item.getMediaStreams().add(stream(languageCode));
        return item;
    }

    private static MediaStream stream(String languageCode) {
        MediaStream stream = new MediaStream();
        stream.setType(MediaStreamType.Audio);
        stream.setLanguage(languageCode);
        return stream;
    }

    private static class FixedRandom extends Random {
        private final int[] values;
        private int index;

        FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int value = values[index++];
            if (value >= bound) {
                throw new AssertionError("Fixed random value " + value + " exceeds bound " + bound);
            }
            return value;
        }
    }
}
