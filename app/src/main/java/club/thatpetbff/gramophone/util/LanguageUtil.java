package club.thatpetbff.gramophone.util;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.interaction.EmptyResponse;
import org.jellyfin.apiclient.model.dto.BaseItemDto;

import java.util.ArrayList;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.model.Language;

public class LanguageUtil {

    public interface ClassificationCallback {
        void onSuccess(String genreName);
        void onError(Exception exception);
    }

    public static void setSongLanguage(String itemId, String languageCode, ClassificationCallback callback) {
        String userId = App.getApiClient().getCurrentUserId();

        App.getApiClient().GetItemAsync(itemId, userId, new Response<BaseItemDto>() {
            @Override
            public void onResponse(BaseItemDto item) {
                ArrayList<String> genres = item.getGenres();
                if (genres == null) {
                    genres = new ArrayList<>();
                }

                genres.removeIf(g -> g != null && g.startsWith(Language.LANGUAGE_GENRE_PREFIX));
                genres.add(Language.getLanguageGenre(new Language(languageCode)));
                item.setGenres(genres);

                App.getApiClient().UpdateItem(item.getId(), item, new EmptyResponse() {
                    @Override
                    public void onResponse() {
                        if (callback != null) {
                            callback.onSuccess(new Language(languageCode).getGenreName());
                        }
                    }

                    @Override
                    public void onError(Exception exception) {
                        if (callback != null) {
                            callback.onError(exception);
                        } else {
                            exception.printStackTrace();
                        }
                    }
                });
            }

            @Override
            public void onError(Exception exception) {
                if (callback != null) {
                    callback.onError(exception);
                } else {
                    exception.printStackTrace();
                }
            }
        });
    }
}
