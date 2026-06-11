package club.thatpetbff.gramophone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import club.thatpetbff.gramophone.adapter.MusicLibraryPagerAdapter;
import club.thatpetbff.gramophone.model.Category;

import org.junit.Test;

public class LanguageCategoryWiringTest {
    @Test
    public void languageCategoryIsNextToFavorites() {
        assertEquals(Category.FAVORITES.ordinal() - 1, Category.LANGUAGE.ordinal());
    }

    @Test
    public void languageCategoryHasMatchingLibraryFragmentMapping() {
        assertEquals(
                MusicLibraryPagerAdapter.MusicFragments.LANGUAGE,
                MusicLibraryPagerAdapter.MusicFragments.valueOf(Category.LANGUAGE.toString()));
        assertTrue(MusicLibraryPagerAdapter.MusicFragments.LANGUAGE
                .getFragmentClass()
                .getName()
                .endsWith(".LanguageFragment"));
    }
}
