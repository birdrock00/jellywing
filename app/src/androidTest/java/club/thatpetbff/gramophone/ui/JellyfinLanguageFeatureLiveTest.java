package club.thatpetbff.gramophone.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.hamcrest.Matchers.allOf;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import club.thatpetbff.gramophone.App;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.activities.MainActivity;
import club.thatpetbff.gramophone.helper.MusicPlayerRemote;
import club.thatpetbff.gramophone.model.Category;
import club.thatpetbff.gramophone.model.Language;
import club.thatpetbff.gramophone.model.Song;
import club.thatpetbff.gramophone.model.User;
import club.thatpetbff.gramophone.util.PreferenceUtil;
import club.thatpetbff.gramophone.util.QueryUtil;

import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.model.dto.BaseItemDto;
import org.jellyfin.apiclient.model.querying.ItemFields;
import org.jellyfin.apiclient.model.querying.ItemQuery;
import org.jellyfin.apiclient.model.querying.ItemsResult;
import org.jellyfin.apiclient.model.users.AuthenticationResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class JellyfinLanguageFeatureLiveTest {
    private static final long NETWORK_TIMEOUT_MS = 90_000;
    private static final int PAGE_SIZE = 100;
    private static final int LANGUAGE_QUEUE_LIMIT = 50;

    @Rule
    public GrantPermissionRule mediaPermissions = GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS);

    private String server;
    private String username;
    private String password;

    @Before
    public void readRuntimeCredentials() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        server = arguments.getString("jellyfinServer");
        username = arguments.getString("jellyfinUsername");
        password = arguments.getString("jellyfinPassword");

        assumeTrue("Pass -e jellyfinServer, -e jellyfinUsername, and -e jellyfinPassword to run live Language tests",
                hasText(server) && hasText(username) && hasText(password));
    }

    @Test
    public void languageTabClickBuildsRealLimitedQueueFromLiveJellyfinSongs() throws Exception {
        AuthenticationResult authentication = authenticate();
        User user = new User(authentication, server);

        try (AppSessionGuard sessionGuard = new AppSessionGuard(user)) {
            sessionGuard.installTestUser();
            QueryUtil.currentLibrary = findMusicLibrary();

            List<BaseItemDto> audioItems = loadAudioItemsWithMediaStreams();
            assertFalse("Jellyfin returned no real audio items for the configured user/library", audioItems.isEmpty());

            Map<String, List<BaseItemDto>> itemsByLanguage = groupItemsByNormalizedLanguage(audioItems);
            if (itemsByLanguage.isEmpty()) {
                fail("No language-detectable songs were found on the real Jellyfin server. "
                        + "Language feature tests require stream language metadata or fallback-detectable song metadata.");
            }

            Map.Entry<String, List<BaseItemDto>> selected = largestLanguageGroup(itemsByLanguage);
            Language language = new Language(selected.getKey());
            List<BaseItemDto> matchingItems = selected.getValue();
            assertNotNull("Expected a live language group", matchingItems);
            assertFalse("Expected at least one live song for language " + language.code, matchingItems.isEmpty());

            PreferenceSnapshot preferences = PreferenceSnapshot.capture();
            configureLanguageTabPreferences();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                waitUntil("music service connects", 15_000, () -> MusicPlayerRemote.musicService != null);

                onView(allOf(withText("LANGUAGE"), isDescendantOfA(withId(R.id.tabs)), isDisplayed()))
                        .perform(click());
                waitForLanguageRow(language);
                onView(allOf(withText(language.name), isDisplayed()))
                        .perform(click());

                int expectedSize = Math.min(LANGUAGE_QUEUE_LIMIT, matchingItems.size());
                waitUntil("Language tab click creates a fully loaded Jellyfin playback queue", NETWORK_TIMEOUT_MS,
                        () -> {
                            List<Song> current = MusicPlayerRemote.getPlayingQueue();
                            return !current.isEmpty() && current.size() == expectedSize;
                        });

                List<Song> queue = new ArrayList<>(MusicPlayerRemote.getPlayingQueue());
                assertTrue("Language UI queue should contain at least one real song", queue.size() >= 1);
                assertTrue("Language UI queue should contain at most 50 real songs", queue.size() <= LANGUAGE_QUEUE_LIMIT);
                assertEqualsByMessage("Language UI queue should use all songs when fewer than 50 exist, otherwise cap at 50",
                        expectedSize, queue.size());
                assertQueueContainsOnlyMatchingRealSongs(language, audioItems, queue);
            } finally {
                MusicPlayerRemote.pauseSong();
                preferences.restore();
            }
        }
    }

    @Test
    public void reportLiveLanguageDistributionForRealJellyfinLibrary() throws Exception {
        AuthenticationResult authentication = authenticate();
        User user = new User(authentication, server);

        try (AppSessionGuard sessionGuard = new AppSessionGuard(user)) {
            sessionGuard.installTestUser();
            QueryUtil.currentLibrary = findMusicLibrary();

            List<BaseItemDto> audioItems = loadAudioItemsWithMediaStreams();
            assertFalse("Jellyfin returned no real audio items for the configured user/library", audioItems.isEmpty());

            Map<String, List<BaseItemDto>> itemsByLanguage = groupItemsByNormalizedLanguage(audioItems);
            int totalDetected = 0;
            for (List<BaseItemDto> group : itemsByLanguage.values()) {
                totalDetected += group.size();
            }

            android.util.Log.i("JELLYWING_LANG",
                    "library=" + QueryUtil.currentLibrary.getName()
                            + " totalSongs=" + audioItems.size()
                            + " songsInSomeLanguageGroup=" + totalDetected
                            + " distinctLanguages=" + itemsByLanguage.size());

            List<Map.Entry<String, List<BaseItemDto>>> sorted = new ArrayList<>(itemsByLanguage.entrySet());
            Collections.sort(sorted, (left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()));

            for (Map.Entry<String, List<BaseItemDto>> entry : sorted) {
                Language language = new Language(entry.getKey());
                android.util.Log.i("JELLYWING_LANG",
                        "lang=" + language.code + " name=" + language.name + " count=" + entry.getValue().size());
            }

            int zhCount = itemsByLanguage.containsKey("zh") ? itemsByLanguage.get("zh").size() : 0;
            android.util.Log.i("JELLYWING_LANG", "CHINESE_COUNT=" + zhCount);
        }
    }

    @Test
    public void clickingChineseBuildsFiftySongQueueOnRealJellyfinLibrary() throws Exception {
        AuthenticationResult authentication = authenticate();
        User user = new User(authentication, server);

        try (AppSessionGuard sessionGuard = new AppSessionGuard(user)) {
            sessionGuard.installTestUser();
            QueryUtil.currentLibrary = findMusicLibrary();

            List<BaseItemDto> audioItems = loadAudioItemsWithMediaStreams();
            assertFalse("Jellyfin returned no real audio items for the configured user/library", audioItems.isEmpty());

            Map<String, List<BaseItemDto>> itemsByLanguage = groupItemsByNormalizedLanguage(audioItems);
            int zhCount = itemsByLanguage.containsKey("zh") ? itemsByLanguage.get("zh").size() : 0;
            android.util.Log.i("JELLYWING_LANG", "CHINESE_COUNT_BEFORE_CLICK=" + zhCount);

            assertTrue("Test requires Chinese songs to be present in this library", zhCount > 0);
            Language chinese = new Language("zh");

            PreferenceSnapshot preferences = PreferenceSnapshot.capture();
            configureLanguageTabPreferences();
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                waitUntil("music service connects", 15_000, () -> MusicPlayerRemote.musicService != null);

                onView(allOf(withText("LANGUAGE"), isDescendantOfA(withId(R.id.tabs)), isDisplayed()))
                        .perform(click());
                waitForLanguageRow(chinese);
                onView(allOf(withText(chinese.name), isDisplayed()))
                        .perform(click());

                int expectedSize = Math.min(LANGUAGE_QUEUE_LIMIT, zhCount);
                final int expected = expectedSize;
                android.util.Log.i("JELLYWING_LANG", "EXPECTED_CHINESE_QUEUE_SIZE=" + expected);

                waitUntil("Chinese language click creates a fully loaded Jellyfin playback queue", NETWORK_TIMEOUT_MS,
                        () -> {
                            List<Song> current = MusicPlayerRemote.getPlayingQueue();
                            android.util.Log.i("JELLYWING_LANG", "OBSERVED_CHINESE_QUEUE_SIZE=" + current.size());
                            return !current.isEmpty() && current.size() == expected;
                        });

                List<Song> queue = new ArrayList<>(MusicPlayerRemote.getPlayingQueue());
                android.util.Log.i("JELLYWING_LANG", "FINAL_CHINESE_QUEUE_SIZE=" + queue.size()
                        + " currentPos=" + MusicPlayerRemote.getPosition());
                assertEqualsByMessage("Chinese click should load min(50, count) songs", expected, queue.size());
            } finally {
                MusicPlayerRemote.pauseSong();
                preferences.restore();
            }
        }
    }

    private AuthenticationResult authenticate() throws InterruptedException {
        long deadline = System.currentTimeMillis() + NETWORK_TIMEOUT_MS;
        Throwable lastFailure = null;

        while (System.currentTimeMillis() < deadline) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AuthenticationResult> resultRef = new AtomicReference<>();
            AtomicReference<Exception> errorRef = new AtomicReference<>();

            App.getApiClient().ChangeServerLocation(server);
            App.getApiClient().AuthenticateUserAsync(username, password, new Response<AuthenticationResult>() {
                @Override
                public void onResponse(AuthenticationResult result) {
                    resultRef.set(result);
                    latch.countDown();
                }

                @Override
                public void onError(Exception exception) {
                    errorRef.set(exception);
                    latch.countDown();
                }
            });

            if (!latch.await(15_000, TimeUnit.MILLISECONDS)) {
                lastFailure = new AssertionError("Timed out waiting for Jellyfin authentication attempt");
            } else if (errorRef.get() == null) {
                AuthenticationResult result = resultRef.get();
                assertNotNull("Jellyfin authentication returned no result", result);
                assertNotNull("Jellyfin authentication returned no access token", result.getAccessToken());
                return result;
            } else {
                lastFailure = errorRef.get();
            }

            Thread.sleep(2_000);
        }

        throw new AssertionError("Jellyfin authentication failed", lastFailure);
    }

    private BaseItemDto findMusicLibrary() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<BaseItemDto>> librariesRef = new AtomicReference<>();

        QueryUtil.getLibraries(libraries -> {
            librariesRef.set(libraries);
            latch.countDown();
        });

        assertTrueWithin("Jellyfin libraries load", latch, NETWORK_TIMEOUT_MS);
        List<BaseItemDto> libraries = librariesRef.get();
        assertNotNull("Jellyfin returned no libraries", libraries);
        assertFalse("Jellyfin returned an empty library list", libraries.isEmpty());

        for (BaseItemDto library : libraries) {
            if ("music".equals(library.getCollectionType())) {
                return library;
            }
        }

        return libraries.get(0);
    }

    private List<BaseItemDto> loadAudioItemsWithMediaStreams() throws InterruptedException {
        List<BaseItemDto> items = new ArrayList<>();
        int startIndex = 0;
        int totalRecordCount;

        do {
            ItemsResult result = loadAudioPage(startIndex);
            assertNotNull("Jellyfin returned no item page", result);
            assertNotNull("Jellyfin returned a page with no item array", result.getItems());
            Collections.addAll(items, result.getItems());
            startIndex += result.getItems().length;
            totalRecordCount = result.getTotalRecordCount();
        } while (startIndex < totalRecordCount);

        return items;
    }

    private ItemsResult loadAudioPage(int startIndex) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ItemsResult> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        ItemQuery query = new ItemQuery();
        query.setIncludeItemTypes(new String[]{"Audio"});
        query.setFields(new ItemFields[]{ItemFields.MediaStreams, ItemFields.MediaSources});
        query.setUserId(App.getApiClient().getCurrentUserId());
        query.setRecursive(true);
        query.setLimit(PAGE_SIZE);
        query.setStartIndex(startIndex);
        query.setEnableTotalRecordCount(true);

        if (QueryUtil.currentLibrary != null) {
            query.setParentId(QueryUtil.currentLibrary.getId());
        }

        App.getApiClient().GetItemsAsync(query, new Response<ItemsResult>() {
            @Override
            public void onResponse(ItemsResult result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(Exception exception) {
                errorRef.set(exception);
                latch.countDown();
            }
        });

        assertTrueWithin("Jellyfin audio page " + startIndex + " loads", latch, NETWORK_TIMEOUT_MS);
        if (errorRef.get() != null) {
            throw new AssertionError("Jellyfin audio page " + startIndex + " failed", errorRef.get());
        }
        return resultRef.get();
    }

    private Map<String, List<BaseItemDto>> groupItemsByNormalizedLanguage(List<BaseItemDto> audioItems) {
        Map<String, List<BaseItemDto>> itemsByLanguage = new LinkedHashMap<>();

        for (BaseItemDto item : audioItems) {
            Set<String> itemLanguages = new HashSet<>();
            for (String rawLanguage : Language.getAudioLanguages(item)) {
                String code = Language.normalizeCode(rawLanguage);
                if (hasText(code)) {
                    itemLanguages.add(code);
                }
            }

            for (String code : itemLanguages) {
                if (!itemsByLanguage.containsKey(code)) {
                    itemsByLanguage.put(code, new ArrayList<>());
                }
                itemsByLanguage.get(code).add(item);
            }
        }

        return itemsByLanguage;
    }

    private Map.Entry<String, List<BaseItemDto>> largestLanguageGroup(Map<String, List<BaseItemDto>> itemsByLanguage) {
        Map.Entry<String, List<BaseItemDto>> largest = null;
        for (Map.Entry<String, List<BaseItemDto>> entry : itemsByLanguage.entrySet()) {
            if (largest == null || entry.getValue().size() > largest.getValue().size()) {
                largest = entry;
            }
        }
        return largest;
    }

    private void configureLanguageTabPreferences() {
        for (Category category : Category.values()) {
            category.select = true;
        }
        List<Category> categories = new ArrayList<>();
        categories.add(Category.LANGUAGE);
        for (Category category : Category.values()) {
            if (category != Category.LANGUAGE) {
                categories.add(category);
            }
        }
        PreferenceUtil preferences = PreferenceUtil.getInstance(App.getInstance());
        preferences.setCategories(categories);
        preferences.setLastTab(1);
        preferences.getPreferences().edit()
                .putBoolean("battery_optimization_prompt_shown", true)
                .putBoolean("permission_warning_prompt_shown", true)
                .apply();
    }

    private void waitForLanguageRow(Language language) throws InterruptedException {
        waitUntil("Language tab shows real row for " + language.name, NETWORK_TIMEOUT_MS, () -> {
            try {
                onView(allOf(withText(language.name), isDisplayed())).check(matches(isDisplayed()));
                return true;
            } catch (RuntimeException | AssertionError exception) {
                return false;
            }
        });
    }

    private void assertQueueContainsOnlyMatchingRealSongs(Language language, List<BaseItemDto> audioItems, List<Song> queue) {
        Map<String, BaseItemDto> itemsById = new LinkedHashMap<>();
        for (BaseItemDto item : audioItems) {
            if (hasText(item.getId())) {
                itemsById.put(item.getId(), item);
            }
        }

        for (Song song : queue) {
            assertTrue("Queued song should come from Jellyfin and have an id", hasText(song.id));
            BaseItemDto item = itemsById.get(song.id);
            assertNotNull("Queued song should come from the fetched real Jellyfin library: " + song.id, item);
            assertTrue("Queued song should match clicked language " + language.code + ": " + song.id,
                    language.matches(item));
        }
    }

    private static void assertTrueWithin(String description, CountDownLatch latch, long timeoutMs) throws InterruptedException {
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Timed out waiting for " + description);
        }
    }

    private static void assertEqualsByMessage(String message, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(message + ". expected:<" + expected + "> but was:<" + actual + ">");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void waitUntil(String description, long timeoutMs, Condition condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            if (condition.isSatisfied()) {
                return;
            }
            Thread.sleep(250);
        } while (System.currentTimeMillis() < deadline);

        throw new AssertionError("Timed out waiting for " + description);
    }

    private interface Condition {
        boolean isSatisfied();
    }

    private static final class PreferenceSnapshot {
        private final String categories;
        private final int lastTab;
        private final boolean batteryPromptShown;
        private final boolean permissionPromptShown;
        private final boolean hadBatteryPromptShown;
        private final boolean hadPermissionPromptShown;

        private PreferenceSnapshot(String categories, int lastTab, SharedPreferences sharedPreferences) {
            this.categories = categories;
            this.lastTab = lastTab;
            this.hadBatteryPromptShown = sharedPreferences.contains("battery_optimization_prompt_shown");
            this.hadPermissionPromptShown = sharedPreferences.contains("permission_warning_prompt_shown");
            this.batteryPromptShown = sharedPreferences.getBoolean("battery_optimization_prompt_shown", false);
            this.permissionPromptShown = sharedPreferences.getBoolean("permission_warning_prompt_shown", false);
        }

        static PreferenceSnapshot capture() {
            PreferenceUtil preferences = PreferenceUtil.getInstance(App.getInstance());
            SharedPreferences sharedPreferences = preferences.getPreferences();
            return new PreferenceSnapshot(
                    sharedPreferences.getString(PreferenceUtil.CATEGORIES, null),
                    preferences.getLastTab(),
                    sharedPreferences);
        }

        void restore() {
            PreferenceUtil preferences = PreferenceUtil.getInstance(App.getInstance());
            SharedPreferences.Editor editor = preferences.getPreferences().edit();
            if (categories == null) {
                editor.remove(PreferenceUtil.CATEGORIES);
            } else {
                editor.putString(PreferenceUtil.CATEGORIES, categories);
            }
            if (hadBatteryPromptShown) {
                editor.putBoolean("battery_optimization_prompt_shown", batteryPromptShown);
            } else {
                editor.remove("battery_optimization_prompt_shown");
            }
            if (hadPermissionPromptShown) {
                editor.putBoolean("permission_warning_prompt_shown", permissionPromptShown);
            } else {
                editor.remove("permission_warning_prompt_shown");
            }
            editor.apply();
            preferences.setLastTab(lastTab);
        }
    }
}
