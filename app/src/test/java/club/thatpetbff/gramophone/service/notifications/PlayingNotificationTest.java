package club.thatpetbff.gramophone.service.notifications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayingNotificationTest {
    @Test
    public void recognizesForegroundServiceStartNotAllowedByPlatformClassName() {
        assertTrue(PlayingNotification.isForegroundServiceStartNotAllowedClassName(
                "android.app.ForegroundServiceStartNotAllowedException"));
    }

    @Test
    public void doesNotTreatOtherRuntimeFailuresAsForegroundStartRestrictions() {
        assertFalse(PlayingNotification.isForegroundServiceStartNotAllowedClassName(
                IllegalStateException.class.getName()));
        assertFalse(PlayingNotification.isForegroundServiceStartNotAllowedClassName(
                RuntimeException.class.getName()));
    }
}
