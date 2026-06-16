package club.thatpetbff.gramophone.fragments.library;

import club.thatpetbff.gramophone.model.Language;

import org.jellyfin.apiclient.model.dto.BaseItemDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

class LanguageQueueSampler<T> {
    private final int limit;
    private final Random random;
    private final Function<BaseItemDto, T> mapper;
    private final List<T> sample;
    private int matchingItemCount;

    LanguageQueueSampler(int limit, Random random, Function<BaseItemDto, T> mapper) {
        this.limit = limit;
        this.random = random;
        this.mapper = mapper;
        this.sample = new ArrayList<>(limit);
    }

    void consider(Language language, BaseItemDto itemDto) {
        if (!language.matches(itemDto)) {
            return;
        }

        matchingItemCount++;
        if (sample.size() < limit) {
            sample.add(mapper.apply(itemDto));
            return;
        }

        int replacementIndex = random.nextInt(matchingItemCount);
        if (replacementIndex < limit) {
            sample.set(replacementIndex, mapper.apply(itemDto));
        }
    }

    List<T> getSample() {
        return sample;
    }

    int getMatchingItemCount() {
        return matchingItemCount;
    }
}
