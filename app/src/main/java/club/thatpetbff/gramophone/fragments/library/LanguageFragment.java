package club.thatpetbff.gramophone.fragments.library;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.adapter.LanguageAdapter;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.model.Language;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.util.PreferenceUtil;
import club.thatpetbff.gramophone.util.QueryUtil;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.querying.ItemFields;
import org.jellyfin.apiclient.model.querying.ItemQuery;
import org.jellyfin.apiclient.model.querying.ItemsResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LanguageFragment extends AbsLibraryPagerRecyclerViewFragment<LanguageAdapter, LinearLayoutManager, ItemQuery> implements LanguageAdapter.OnLanguageClickListener {
    private static final int QUEUE_LIMIT = 50;

    @NonNull
    @Override
    protected LinearLayoutManager createLayoutManager() {
        return new LinearLayoutManager(getActivity());
    }

    @NonNull
    @Override
    protected LanguageAdapter createAdapter() {
        List<Language> dataSet = getAdapter() == null ? new ArrayList<>() : getAdapter().getDataSet();
        return new LanguageAdapter(getLibraryFragment().getMainActivity(), dataSet, this);
    }

    @NonNull
    @Override
    protected ItemQuery createQuery() {
        return createLanguageQuery();
    }

    @Override
    protected void loadItems(int index) {
        if (index != 0) return;

        getAdapter().getDataSet().clear();
        loading = true;
        loadLanguagePage(0, new LinkedHashMap<>());
    }

    @Override
    protected int getEmptyMessage() {
        return R.string.no_languages;
    }

    @Override
    public void onLanguageClick(Language language) {
        MusicPlayerRemote.clearQueue();
        loadLanguageQueuePage(language, 0, new ArrayList<>());
    }

    private void loadLanguagePage(int startIndex, Map<String, Language> languages) {
        ItemQuery query = createLanguageQuery();
        query.setStartIndex(startIndex);

        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                for (BaseItemDto itemDto : result.getItems()) {
                    for (String rawLanguage : Language.getAudioLanguages(itemDto)) {
                        String code = Language.normalizeCode(rawLanguage);
                        if (code.isEmpty()) continue;

                        Language language = languages.get(code);
                        if (language == null) {
                            language = new Language(rawLanguage);
                            languages.put(code, language);
                        }
                        language.songCount++;
                    }
                }

                int nextIndex = startIndex + result.getItems().length;
                if (nextIndex < result.getTotalRecordCount()) {
                    loadLanguagePage(nextIndex, languages);
                    return;
                }

                List<Language> dataSet = getAdapter().getDataSet();
                dataSet.clear();
                dataSet.addAll(languages.values());
                Collections.sort(dataSet, Comparator.comparing(language -> language.name.toLowerCase(Locale.US)));

                size = dataSet.size();
                getAdapter().notifyDataSetChanged();
                loading = false;
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
                loading = false;
            }
        });
    }

    private void loadLanguageQueuePage(Language language, int startIndex, List<Song> songs) {
        ItemQuery query = createLanguageQuery();
        query.setStartIndex(startIndex);
        query.setLimit(200);

        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                for (BaseItemDto itemDto : result.getItems()) {
                    if (songs.size() >= QUEUE_LIMIT) break;
                    if (language.matches(itemDto)) {
                        songs.add(new Song(itemDto));
                    }
                }

                int nextIndex = startIndex + result.getItems().length;
                if (songs.size() < QUEUE_LIMIT && nextIndex < result.getTotalRecordCount()) {
                    loadLanguageQueuePage(language, nextIndex, songs);
                    return;
                }

                playLanguageQueue(language, songs);
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
            }
        });
    }

    private void playLanguageQueue(Language language, List<Song> songs) {
        if (songs.isEmpty()) {
            Toast.makeText(getActivity(), getResources().getString(R.string.no_language_songs, language.name), Toast.LENGTH_SHORT).show();
            return;
        }

        Collections.shuffle(songs);
        if (songs.size() < QUEUE_LIMIT) {
            Toast.makeText(getActivity(), getResources().getString(R.string.language_queue_low_count, songs.size(), language.name), Toast.LENGTH_SHORT).show();
        } else if (songs.size() > QUEUE_LIMIT) {
            songs = new ArrayList<>(songs.subList(0, QUEUE_LIMIT));
        }

        MusicPlayerRemote.openAndShuffleQueue(songs, true);
    }

    private ItemQuery createLanguageQuery() {
        ItemQuery query = new ItemQuery();

        query.setIncludeItemTypes(new String[]{"Audio"});
        query.setFields(new ItemFields[]{
                ItemFields.MediaStreams,
                ItemFields.MediaSources,
                ItemFields.OriginalTitle,
                ItemFields.Tags,
                ItemFields.Genres,
                ItemFields.Keywords
        });
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setRecursive(true);
        query.setLimit(PreferenceUtil.getInstance(App.getInstance()).getPageSize());
        query.setEnableTotalRecordCount(true);

        if (QueryUtil.currentLibrary != null) {
            query.setParentId(QueryUtil.currentLibrary.getId());
        }

        return query;
    }
}
