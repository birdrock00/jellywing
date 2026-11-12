package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void testStatus404MapsToAlreadyGone() {
        org.jellyfin.apiclient.model.net.HttpException missing =
                new org.jellyfin.apiclient.model.net.HttpException("VolleyError: ", null);
        missing.setStatusCode(404);

        assertEquals("A 404 must map to ALREADY_GONE — its own toast, not a generic failure (R6)",
                DeleteOutcome.ALREADY_GONE, LibraryDeleteUtil.mapOutcome(missing));
    }

    @Test
    public void testTransportErrorMapsToNetworkFailure() {
        // A transport failure has no HTTP status at all: a bare exception, or an
        // HttpException whose status was never set (the socket/timeout shape).
        assertEquals("No status at all must map to NETWORK_FAILURE (R7)",
                DeleteOutcome.NETWORK_FAILURE,
                LibraryDeleteUtil.mapOutcome(new RuntimeException("connection reset")));

        org.jellyfin.apiclient.model.net.HttpException timedOut =
                new org.jellyfin.apiclient.model.net.HttpException("VolleyError TimeoutError: ", null);
        assertEquals("Statusless HttpException (timeout) must map to NETWORK_FAILURE (R7)",
                DeleteOutcome.NETWORK_FAILURE, LibraryDeleteUtil.mapOutcome(timedOut));
    }

    @Test
    public void testOtherStatusMapsToServerFailure() {
        org.jellyfin.apiclient.model.net.HttpException serverBoom =
                new org.jellyfin.apiclient.model.net.HttpException("VolleyError: ", null);
        serverBoom.setStatusCode(503);

        assertEquals("Any status other than 2xx/401/404 must map to SERVER_FAILURE (R7)",
                DeleteOutcome.SERVER_FAILURE, LibraryDeleteUtil.mapOutcome(serverBoom));
    }
}
