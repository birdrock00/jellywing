package club.thatpetbff.gramophone.util;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.model.SortMethod;
import club.thatpetbff.gramophone.model.SortOrder;
import club.thatpetbff.gramophone.interfaces.MediaCallback;
import club.thatpetbff.gramophone.model.Song;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.querying.ItemFields;
import org.jellyfin.apiclient.model.querying.ItemFilter;
import org.jellyfin.apiclient.model.querying.ItemQuery;
import org.jellyfin.apiclient.model.querying.ItemsResult;
import org.jellyfin.apiclient.model.querying.SimilarItemsQuery;

import java.util.ArrayList;
import java.util.List;

public class ShortcutUtil {
    public static void getFrequent(MediaCallback<Song> callback) {
        ItemQuery query = new ItemQuery();

        query.setSortBy(new String[]{SortMethod.COUNT.getApi()});
        query.setSortOrder(SortOrder.DESCENDING.getApi());

        getSongs(query, callback);
    }

    public static void getLatest(MediaCallback<Song> callback) {
        ItemQuery query = new ItemQuery();

        query.setSortBy(new String[]{SortMethod.ADDED.getApi()});
        query.setSortOrder(SortOrder.DESCENDING.getApi());

        getSongs(query, callback);
    }

    public static void getShuffle(MediaCallback<Song> callback, boolean onlyFavorites) {
        ItemQuery query = new ItemQuery();

        query.setSortBy(new String[]{SortMethod.RANDOM.getApi()});
        query.setSortOrder(SortOrder.DESCENDING.getApi());

        if (onlyFavorites) {
            query.setFilters(new ItemFilter[]{ItemFilter.IsFavorite});
        }

        getSongs(query, callback);
    }

    public static void getInstantMix(Song song, int limit, MediaCallback<Song> callback) {
        if (song == null || song.id == null || song.id.trim().isEmpty()) {
            callback.onLoadMedia(new ArrayList<>());
            return;
        }

        SimilarItemsQuery query = new SimilarItemsQuery();
        query.setId(song.id);
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setLimit(limit);
        query.setFields(new ItemFields[]{ItemFields.MediaSources});

        App.getApiClient().GetInstantMixFromItem(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                callback.onLoadMedia(songsFromItemsResult(result));
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
                callback.onLoadMedia(new ArrayList<>());
            }
        });
    }

    public static void getSongs(ItemQuery query, MediaCallback<Song> callback) {
        query.setIncludeItemTypes(new String[]{"Audio"});
        query.setFields(new ItemFields[]{ItemFields.MediaSources});

        query.setLimit(200);
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setRecursive(true);

        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                callback.onLoadMedia(songsFromItemsResult(result));
            }

            @Override
            public void onError(Exception exception) {
                exception.printStackTrace();
                callback.onLoadMedia(new ArrayList<>());
            }
        });
    }

    private static List<Song> songsFromItemsResult(ItemsResult result) {
        List<Song> songs = new ArrayList<>();
        if (result == null || result.getItems() == null) {
            return songs;
        }

        for (BaseItemDto itemDto : result.getItems()) {
            songs.add(new Song(itemDto));
        }
        return songs;
    }
}
