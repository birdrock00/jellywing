package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import club.thatpetbff.gramophone.util.LibraryDeleteUtil.DeleteOutcome;

/**
 * JVM unit tests for the pure status-to-outcome mapping in LibraryDeleteUtil.
 * The 401 case is FORCED here (limitation L1 in .tests.md): both accounts on the QA
 * server are administrators with delete rights, so a real 401 cannot be produced by
 * E2E without a forbidden server-side change. Forcing the status into the same pure
 * function the app uses is the honest coverage.
 */
public class LibraryDeleteOutcomeTest {

    @Test
    public void testStatus401MapsToPermissionDenied() {
        org.jellyfin.apiclient.model.net.HttpException refused =
                new org.jellyfin.apiclient.model.net.HttpException("VolleyError: ", null);
        refused.setStatusCode(401);

        assertEquals("A 401 from DELETE /Items/{id} must map to PERMISSION_DENIED (R5)",
                DeleteOutcome.PERMISSION_DENIED, LibraryDeleteUtil.mapOutcome(refused));
    }
}
