package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Source-contract tests for the Delete-from-library wiring (repo precedent for prose
 * contracts: AbsBaseActivityPromptGuardSourceTest). These pin what the instrumented
 * and E2E cases cannot see from the device side: the menu entry statics and the
 * English-only string rule (R12).
 */
public class DeleteFromLibraryWiringSourceTest {
    /** Every user-facing string this feature adds (R12 key list, frozen with .tests.md). */
    private static final List<String> NEW_KEYS = Arrays.asList(
            "action_delete_from_library",   // menu title
            "delete_from_library_title",    // dialog title
            "delete_from_library_body",     // dialog body (destructive copy)
            "delete_from_library_confirm",  // dialog confirm button
            "delete_from_library_cancel",   // dialog cancel button
            "delete_from_library_success",  // success toast
            "delete_from_library_denied",   // 401 toast (names the fix)
            "delete_from_library_gone",     // 404 already-gone toast
            "delete_from_library_failed");  // network/server failure toast

    @Test
    public void testNewStringsLiveInDefaultEnglishOnly() throws Exception {
        String defaultStrings = read("src/main/res/values/strings.xml");
        for (String key : NEW_KEYS) {
            assertTrue("New string key missing from default values/strings.xml: " + key,
                    defaultStrings.contains("name=\"" + key + "\""));
        }

        File valuesDir = new File("src/main/res");
        File[] localeDirs = valuesDir.listFiles(
                file -> file.isDirectory() && file.getName().startsWith("values-"));
        assertTrue("Expected localized values-* directories to exist",
                localeDirs != null && localeDirs.length > 0);
        for (File localeDir : localeDirs) {
            File localeStrings = new File(localeDir, "strings.xml");
            if (!localeStrings.isFile()) {
                continue;
            }
            String locale = new String(Files.readAllBytes(localeStrings.toPath()),
                    StandardCharsets.UTF_8);
            for (String key : NEW_KEYS) {
                assertFalse("R12: key must not be added to localized " + localeDir.getName()
                                + " (37 locales fall back to English): " + key,
                        locale.contains("name=\"" + key + "\""));
            }
        }
    }

    @Test
    public void testMenuItemPresent() throws Exception {
        String menu = read("src/main/res/menu/menu_player.xml");
        assertTrue("Player menu must define action_delete_from_library (R1)",
                menu.contains("@+id/action_delete_from_library"));
        int itemStart = menu.indexOf("@+id/action_delete_from_library");
        int itemEnd = menu.indexOf("/>", itemStart);
        String deleteItem = menu.substring(itemStart, itemEnd);
        assertTrue("Delete item must live in overflow, never as an action button (R1)",
                deleteItem.contains("showAsAction=\"never\""));
        assertTrue("Delete item title must come from the string resource (R12)",
                deleteItem.contains("@string/action_delete_from_library"));
    }

    /**
     * R2: selecting Delete-from-library must open the confirmation dialog, and the
     * fragment handler must never reach the API directly — every network call lives
     * behind the dialog's positive button in LibraryDeleteUtil.
     */
    @Test
    public void testHandlerOpensDialogNotApiDirectly() throws Exception {
        String source = read("src/main/java/club/thatpetbff/gramophone/fragments/player/AbsPlayerFragment.java");
        int handlerStart = source.indexOf("onMenuItemClick");
        assertTrue("onMenuItemClick must exist", handlerStart >= 0);
        int handlerEnd = source.indexOf("protected void toggleFavorite", handlerStart);
        String handler = source.substring(handlerStart, handlerEnd > 0 ? handlerEnd : source.length());
        int branch = handler.indexOf("R.id.action_delete_from_library");
        assertTrue("onMenuItemClick must branch on the delete item (R2)", branch >= 0);
        int branchEnd = handler.indexOf("}", handler.indexOf("return true", branch));
        assertTrue("Delete branch must be present", branchEnd > branch);
        String deleteBranch = handler.substring(branch, branchEnd);
        assertTrue("Delete branch must open DeleteFromLibraryDialog (R2)",
                deleteBranch.contains("DeleteFromLibraryDialog"));
        assertFalse("Handler must not call the API DeleteItem directly — dialog first (R2/R3)",
                deleteBranch.contains("DeleteItem"));
        assertFalse("Handler must not touch LibraryDeleteUtil directly — dialog first (R2)",
                deleteBranch.contains("LibraryDeleteUtil"));
    }

    /**
     * R3: confirming issues exactly one stock DELETE through the existing apiclient —
     * no new HTTP plumbing. (Call-site source contract; the count of one request per
     * confirm on-device is E2E-1's leg in QA.)
     */
    @Test
    public void testUtilIssuesSingleStockDeleteCall() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        assertTrue("Delete pipeline must use App.getApiClient() (R3)",
                util.contains("App.getApiClient()"));
        long callSites = countCodeLinesWith(util, "DeleteItem(");
        assertEquals("Exactly one ApiClient.DeleteItem call site expected (R3), found: " + callSites,
                1L, callSites);
        assertFalse("No hand-rolled HTTP allowed (R3: no new HTTP plumbing)",
                util.contains("HttpURLConnection") || util.contains("Volley") || util.contains("OkHttp"));
    }

    /**
     * R4/R9: the success outcome toasts success, drops the deleted entry from the queue
     * and refreshes Favorites — the client-side aftermath the user must see.
     */
    @Test
    public void testSuccessOutcomeRunsQueueDropAndFavoritesRefresh() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        assertTrue("Success path must toast the success string (R4)",
                util.contains("R.string.delete_from_library_success"));
        assertTrue("Success path must drop the deleted entry from the queue (R4)",
                util.contains("removeFromQueue("));
        assertTrue("Success path must refresh Favorites without user action (R9)",
                util.contains("FavoritesStore.notifyChanged()"));
        assertTrue("Success path must advance playback to the next queued track (R4)",
                util.contains("playSongAt("));
    }

    /**
     * R6: the 404 path performs the queue/refresh cleanup with its own toast and never
     * claims this action deleted anything.
     */
    @Test
    public void testAlreadyGoneRunsCleanupWithoutSuccessClaim() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        int start = util.indexOf("case ALREADY_GONE:");
        assertTrue("handleOutcome must branch on ALREADY_GONE (R6)", start >= 0);
        int end = util.indexOf("case ", start + "case ALREADY_GONE:".length());
        String block = util.substring(start, end > 0 ? end : util.length());
        assertTrue("404 must use its own already-gone toast (R6)",
                block.contains("R.string.delete_from_library_gone"));
        assertFalse("404 must not show the success toast (R6: no deletion claim)",
                block.contains("R.string.delete_from_library_success"));
        assertTrue("404 still drops the dead entry from the queue (R6/R4)",
                block.contains("removeFromQueueAndAdvance("));
        assertTrue("404 still refreshes Favorites (R6/R9)",
                block.contains("FavoritesStore.notifyChanged()"));
    }

    /**
     * R7: a network/server failure names itself and changes nothing — no deletion
     * claim, no queue edit, no library refresh.
     */
    @Test
    public void testFailureOutcomesChangeNothingButToast() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        int start = util.indexOf("case NETWORK_FAILURE:");
        assertTrue("handleOutcome must branch on NETWORK_FAILURE (R7)", start >= 0);
        int end = util.indexOf("}", util.indexOf("break;", start));
        String block = util.substring(start, end > 0 ? end : util.length());
        assertTrue("Failure must show the failure toast (R7)",
                block.contains("R.string.delete_from_library_failed"));
        assertFalse("Failure must not touch the queue (R7)",
                block.contains("removeFromQueue"));
        assertFalse("Failure must not refresh favorites (R7)",
                block.contains("FavoritesStore.notifyChanged"));
        assertFalse("Failure must never claim success (R7)",
                block.contains("delete_from_library_success"));
    }

    /**
     * R8: the request path arms the latch before the API call and every outcome
     * releases it — a swallowed repeat issues no second request, and a released latch
     * never blocks a genuinely new delete.
     */
    @Test
    public void testInFlightGuardWrapsRequestAndReleasesOnOutcome() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        int entry = util.indexOf("public static void deleteFromLibrary");
        int apiCall = util.indexOf("DeleteItem(", entry);
        assertTrue("entry point and request must exist", entry >= 0 && apiCall > entry);
        String beforeRequest = util.substring(entry, apiCall);
        assertTrue("guard must arm the latch before the request (R8)",
                beforeRequest.contains("tryBeginDelete("));
        int handler = util.indexOf("static void handleOutcome");
        int switchStart = util.indexOf("switch (outcome)", handler);
        assertTrue("outcome handler must exist", handler >= 0 && switchStart > handler);
        String beforeSwitch = util.substring(handler, switchStart);
        assertTrue("every terminal outcome releases the latch (R8)",
                beforeSwitch.contains("endDelete(itemId)"));
    }

    private static long countCodeLinesWith(String source, String needle) {
        return java.util.Arrays.stream(source.split("\n"))
                .map(String::trim)
                .filter(line -> line.contains(needle))
                .filter(line -> !line.startsWith("*") && !line.startsWith("//"))
                .count();
    }

    private static String read(String relativeToAppModule) throws Exception {

        return new String(Files.readAllBytes(
                new File(relativeToAppModule).toPath()), StandardCharsets.UTF_8);
    }
}
