package com.dkanada.gramophone.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assume.assumeTrue;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.test.core.app.ApplicationProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.LoginActivity;
import com.dkanada.gramophone.helper.MusicPlayerRemote;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class JellyfinPlaybackSmokeTest {
    private static final long LOGIN_TIMEOUT_MS = 60_000;
    private static final long LIBRARY_TIMEOUT_MS = 90_000;
    private static final long PLAYBACK_TIMEOUT_MS = 45_000;

    private String server;
    private String username;
    private String password;

    @Before
    public void readRuntimeCredentials() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        server = arguments.getString("jellyfinServer");
        username = arguments.getString("jellyfinUsername");
        password = arguments.getString("jellyfinPassword");

        assumeTrue("Pass -e jellyfinServer, -e jellyfinUsername, and -e jellyfinPassword to run playback smoke",
                hasText(server) && hasText(username) && hasText(password));
    }

    @Test
    public void logsIntoJellyfinAndStartsSongPlayback() {
        ActivityScenario.launch(LoginActivity.class);

        onView(isRoot()).perform(waitForDisplayed(withId(R.id.login), 10_000));
        onView(withId(R.id.server)).perform(replaceText(server), closeSoftKeyboard());
        onView(withId(R.id.username)).perform(replaceText(username), closeSoftKeyboard());
        onView(withId(R.id.password)).perform(replaceText(password), closeSoftKeyboard());
        onView(withId(R.id.login)).perform(click());

        String songsTab = ApplicationProvider.getApplicationContext()
                .getString(R.string.songs)
                .toUpperCase(Locale.US);

        onView(isRoot()).perform(waitForDisplayed(withText(songsTab), LOGIN_TIMEOUT_MS));
        onView(withText(songsTab)).perform(click());
        onView(isRoot()).perform(waitForRecyclerItemCount(R.id.recycler_view, 2, LIBRARY_TIMEOUT_MS));

        onView(allOf(withId(R.id.recycler_view), isDisplayed())).perform(actionOnItemAtPosition(1, click()));

        onView(isRoot()).perform(waitUntil("music service starts playback", PLAYBACK_TIMEOUT_MS, () ->
                MusicPlayerRemote.getCurrentSong() != null
                        && MusicPlayerRemote.isPlaying()
                        && MusicPlayerRemote.getSongDurationMillis() > 0));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static ViewAction waitForDisplayed(Matcher<View> matcher, long timeoutMs) {
        return waitUntil("view matching " + matcher + " is displayed", timeoutMs, () -> false, matcher);
    }

    private static ViewAction waitForRecyclerItemCount(@IdRes int recyclerViewId, int minimumCount, long timeoutMs) {
        return new PollingViewAction("RecyclerView " + recyclerViewId + " has at least " + minimumCount + " items", timeoutMs) {
            @Override
            protected boolean isSatisfied(View root) {
                RecyclerView recyclerView = findDisplayedRecyclerView(root, recyclerViewId);
                if (recyclerView == null) {
                    return false;
                }

                RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                return adapter != null && adapter.getItemCount() >= minimumCount;
            }
        };
    }

    private static ViewAction waitUntil(String description, long timeoutMs, Condition condition) {
        return new PollingViewAction(description, timeoutMs) {
            @Override
            protected boolean isSatisfied(View root) {
                return condition.isSatisfied();
            }
        };
    }

    private static ViewAction waitUntil(String description, long timeoutMs, Condition condition, Matcher<View> matcher) {
        return new PollingViewAction(description, timeoutMs) {
            @Override
            protected boolean isSatisfied(View root) {
                return condition.isSatisfied() || hasDisplayedMatch(root, matcher);
            }
        };
    }

    private static boolean hasDisplayedMatch(View root, Matcher<View> matcher) {
        Queue<View> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            View view = queue.remove();
            if (matcher.matches(view) && isDisplayed().matches(view)) {
                return true;
            }

            if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }

        return false;
    }

    private static RecyclerView findDisplayedRecyclerView(View root, @IdRes int recyclerViewId) {
        Queue<View> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            View view = queue.remove();
            if (view.getId() == recyclerViewId && view instanceof RecyclerView && isDisplayed().matches(view)) {
                return (RecyclerView) view;
            }

            if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }

        return null;
    }

    private interface Condition {
        boolean isSatisfied();
    }

    private abstract static class PollingViewAction implements ViewAction {
        private final String description;
        private final long timeoutMs;

        private PollingViewAction(String description, long timeoutMs) {
            this.description = description;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public Matcher<View> getConstraints() {
            return isRoot();
        }

        @Override
        public String getDescription() {
            return String.format(Locale.US, "Wait up to %d ms until %s", timeoutMs, description);
        }

        @Override
        public void perform(UiController uiController, View root) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            do {
                if (isSatisfied(root)) {
                    return;
                }
                uiController.loopMainThreadForAtLeast(250);
            } while (System.currentTimeMillis() < deadline);

            throw new AssertionError(getDescription());
        }

        protected abstract boolean isSatisfied(View root);
    }
}
