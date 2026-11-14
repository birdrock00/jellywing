package club.thatpetbff.gramophone.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

import android.view.View;

import androidx.test.core.app.ActivityScenario;

import club.thatpetbff.gramophone.activities.MainActivity;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Interacting with items inside the player toolbar's overflow menu.
 *
 * <p>Why this exists: appcompat shows an overflow whose items are all {@code showAsAction="never"}
 * in a popup that lives in its OWN window. {@code onView(withId(R.id.action_delete_from_library))}
 * searches the activity window, finds nothing, and raises NoMatchingViewException even when the row
 * is plainly on screen.
 *
 * <p>Measured rather than assumed: with a server-backed song current,
 * AbsPlayerFragment.applyDeleteFromLibraryVisibility logged {@code itemFound=true songPresent=true
 * serverBacked=true visible=true} on every call while the assertion still failed. The product was
 * correct and the query was wrong.
 *
 * <p>The root matcher used here is "a window whose decor view is not the activity's", which works
 * for any popup implementation. It cannot pass vacuously: if no popup window is open, the row is not
 * found and assertItemShown fails; if a popup IS open for the wrong menu, the text still has to
 * match.
 */
final class PlayerOverflow {
    private PlayerOverflow() {
    }

    /** The activity's own decor view, so "not this window" identifies the popup. */
    static View activityDecor(ActivityScenario<MainActivity> scenario) {
        AtomicReference<View> ref = new AtomicReference<>();
        scenario.onActivity(activity -> ref.set(activity.getWindow().getDecorView()));
        if (ref.get() == null) {
            fail("Could not read the activity decor view; no window to compare popup roots against");
        }
        return ref.get();
    }

    /** The row for {@code title} is visible in some window other than the activity. */
    static void assertItemShown(String title, View activityDecor) {
        try {
            onView(allOf(withText(title), isDisplayed()))
                    .inRoot(withDecorView(not(is(activityDecor))))
                    .check(matches(isDisplayed()));
        } catch (Throwable t) {
            fail("Overflow did not show \"" + title + "\" in any popup window: " + t.getMessage());
        }
    }

    /** No popup window may expose {@code title}. */
    static void assertItemAbsent(String title, View activityDecor) {
        boolean found;
        try {
            onView(allOf(withText(title), isDisplayed()))
                    .inRoot(withDecorView(not(is(activityDecor))))
                    .check(matches(isDisplayed()));
            found = true;
        } catch (Throwable t) {
            found = false;
        }
        if (found) {
            fail("Overflow exposed \"" + title + "\" when it must be hidden (R1)");
        }
    }

    static void clickItem(String title, View activityDecor) {
        try {
            onView(allOf(withText(title), isDisplayed()))
                    .inRoot(withDecorView(not(is(activityDecor))))
                    .perform(click());
        } catch (Throwable t) {
            fail("Could not click overflow row \"" + title + "\": " + t.getMessage());
        }
    }
}
