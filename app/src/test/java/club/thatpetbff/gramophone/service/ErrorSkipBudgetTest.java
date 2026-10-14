package club.thatpetbff.gramophone.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import club.thatpetbff.gramophone.service.playback.ErrorSkipBudget;

import org.junit.Test;

public class ErrorSkipBudgetTest {
    @Test
    public void allowsSkipsUpToTheCapThenStops() {
        ErrorSkipBudget budget = new ErrorSkipBudget();

        assertTrue("fresh budget must allow the first error skip", budget.allowSkip());

        for (int i = 0; i < ErrorSkipBudget.MAX_CONSECUTIVE_SKIPS; i++) {
            assertTrue("skip " + (i + 1) + " of " + ErrorSkipBudget.MAX_CONSECUTIVE_SKIPS
                    + " must be allowed", budget.allowSkip());
            budget.recordSkip();
        }

        assertFalse("budget must be exhausted after MAX_CONSECUTIVE_SKIPS skips",
                budget.allowSkip());
    }

    @Test
    public void resetReopensTheBudget() {
        ErrorSkipBudget budget = new ErrorSkipBudget();

        for (int i = 0; i < ErrorSkipBudget.MAX_CONSECUTIVE_SKIPS; i++) {
            budget.recordSkip();
        }
        assertFalse(budget.allowSkip());

        // Playback starting again (or a queue refresh) resets the budget so a
        // later isolated item failure can still skip forward.
        budget.reset();

        assertTrue(budget.allowSkip());
    }
}
