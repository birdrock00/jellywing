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
    /**
     * R12's frozen key list, used only as a floor. The contract itself reads the keys out of
     * the default resources (see deleteFromLibraryKeys), so a tenth delete string added next
     * month is covered by the same rule; a hand-typed list let one escape (an untracked key
     * added to values-de stayed green).
     */
    private static final List<String> MIN_KEYS = Arrays.asList(
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
    public void testDeleteStringKeyListIsDerivedNotEmpty() throws Exception {
        List<String> keys = deleteFromLibraryKeys();
        assertTrue("the delete_from_library key pattern must match the shipped strings — an "
                + "empty match makes the English-only rule vacuous", keys.size() >= MIN_KEYS.size());
        assertTrue("frozen R12 keys missing from default values/strings.xml: " + keys,
                keys.containsAll(MIN_KEYS));
    }

    /**
     * R12: no localized file may carry a delete_from_library* string at all — the 37 locales
     * fall back to English by design.
     *
     * <p>The key names are matched by pattern on each locale file, not by a list copied from
     * the default resources: a hand-typed list only covers the keys someone remembered, so
     * adding a new {@code delete_from_library_undo} to values-de stayed green. Asserting "zero
     * matches in every locale" covers keys that exist only there.
     */
    @Test
    public void testNewStringsLiveInDefaultEnglishOnly() throws Exception {
        File valuesDir = new File("src/main/res");
        File[] localeDirs = valuesDir.listFiles(
                file -> file.isDirectory() && file.getName().startsWith("values-"));
        assertTrue("Expected localized values-* directories to exist",
                localeDirs != null && localeDirs.length > 0);

        int localeFilesChecked = 0;
        for (File localeDir : localeDirs) {
            File localeStrings = new File(localeDir, "strings.xml");
            if (!localeStrings.isFile()) {
                continue;
            }
            localeFilesChecked++;
            String locale = new String(Files.readAllBytes(localeStrings.toPath()),
                    StandardCharsets.UTF_8);
            List<String> translated = stringKeysMatching(locale, "delete_from_library");
            assertTrue("R12: " + localeDir.getName() + " must not translate any "
                            + "delete_from_library* string (English-only by design); found "
                            + translated.size() + ": " + translated,
                    translated.isEmpty());
        }
        assertTrue("the contract must actually read the localized files",
                localeFilesChecked > 0);
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
     * R1's gate, in the suite the release gate actually runs. Forcing
     * {@code applyDeleteFromLibraryVisibility} to always show the item was green, because the
     * only leg covering it is the instrumented hidden-queue case, which the gate never
     * executes. Asserted on the helper body (not the file) so the neighbouring toolbar code
     * cannot satisfy it.
     */
    @Test
    public void testVisibilityGateRequiresAServerBackedSongId() throws Exception {
        String source = read("src/main/java/club/thatpetbff/gramophone/fragments/player/AbsPlayerFragment.java");
        int start = source.indexOf("protected void applyDeleteFromLibraryVisibility");
        assertTrue("the visibility helper must exist (R1)", start >= 0);
        int end = source.indexOf("\n    }", start);
        assertTrue("the visibility helper must have a body (R1)", end > start);
        String body = source.substring(start, end);

        assertTrue("the gate must read the current song (R1)",
                body.contains("MusicPlayerRemote.getCurrentSong()"));
        assertTrue("the gate must require a server id, not merely a non-null song (R1)",
                body.contains("song.id != null"));
        assertTrue("a blank/whitespace id must not count as server-backed (R1)",
                body.contains("isEmpty()"));
        assertTrue("the gate must drive the item's visibility (R1)", body.contains("setVisible("));
        assertFalse("the gate may not hard-code the item visible (R1: no current song ⇒ hidden)",
                body.contains("setVisible(true)"));
    }

    /**
     * R1: both player themes own a toolbar, so a gate only wired into one of them would show
     * the item under the other without ever consulting it.
     */
    @Test
    public void testBothPlayerThemesApplyTheGateOnInflateAndSongChange() throws Exception {
        for (String theme : new String[]{
                "src/main/java/club/thatpetbff/gramophone/fragments/player/card/CardPlayerFragment.java",
                "src/main/java/club/thatpetbff/gramophone/fragments/player/flat/FlatPlayerFragment.java"}) {
            String source = read(theme);
            String name = theme.substring(theme.lastIndexOf('/') + 1);
            long calls = java.util.Arrays.stream(source.split("\n"))
                    .map(String::trim)
                    .filter(line -> line.startsWith("applyDeleteFromLibraryVisibility("))
                    .count();
            assertTrue(name + " must apply the R1 gate", calls >= 1);

            int update = source.indexOf("private void updateCurrentSong()");
            int setup = source.indexOf("private void setUpPlayerToolbar()");
            assertTrue(name + " must refresh the gate when the current song changes (R1)",
                    update >= 0 && source.indexOf("applyDeleteFromLibraryVisibility(", update)
                            < source.indexOf("\n    }", update));
            assertTrue(name + " must apply the gate when the toolbar menu is inflated (R1)",
                    setup >= 0 && source.indexOf("applyDeleteFromLibraryVisibility(", setup)
                            < source.indexOf("\n    }", setup));
        }
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
     * R4/R9: the SUCCESS outcome itself toasts success, drops the deleted entry from the
     * queue and refreshes Favorites.
     *
     * <p>Scope matters: whole-file contains() assertions are satisfied by the ALREADY_GONE
     * branch, so deleting notifyChanged() from the SUCCESS branch alone stayed green. The
     * R10 test already read the SUCCESS block; this one now does the same, and the helper
     * it delegates to is pinned separately below so its body cannot be hollowed out.
     */
    @Test
    public void testSuccessOutcomeRunsQueueDropAndFavoritesRefresh() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        int start = util.indexOf("case SUCCESS:");
        assertTrue("handleOutcome must branch on SUCCESS", start >= 0);
        int end = util.indexOf("break;", start);
        assertTrue("the SUCCESS branch must terminate with break;", end > start);
        String success = util.substring(start, end);

        assertTrue("SUCCESS must toast the success string (R4)",
                success.contains("R.string.delete_from_library_success"));
        assertTrue("SUCCESS must drop the deleted entry from the queue (R4)",
                success.contains("removeFromQueueAndAdvance("));
        assertTrue("SUCCESS must refresh Favorites without user action (R9)",
                success.contains("FavoritesStore.notifyChanged()"));
    }

    /**
     * R4 (inside the helper the SUCCESS branch calls): remove the entry, start whatever now
     * occupies that index, and stop cleanly when that removal empties the queue. Asserted on
     * the helper body, because both SUCCESS and ALREADY_GONE call it — an assertion anywhere
     * else would be satisfied by the other branch's call site.
     */
    @Test
    public void testRemoveFromQueueAndAdvanceHandlesCurrentLoneAndLaterEntries() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        int start = util.indexOf("private static void removeFromQueueAndAdvance");
        assertTrue("the queue cleanup helper must exist (R4)", start >= 0);
        int end = util.indexOf("\n    }", start);
        assertTrue("the queue cleanup helper must have a body", end > start);
        String body = util.substring(start, end);

        assertTrue("the helper must remove the deleted entry (R4)",
                body.contains("removeFromQueue("));
        assertTrue("the helper must find EVERY entry naming the deleted item (R4)",
                body.contains("positionsOfItem("));
        // The ORDER is what makes the every-entry removal correct, and it is now assertable in the
        // JVM suite (LibraryDeleteDuplicateQueueTest#testRemovalOrderIsHighestIndexFirst). These
        // three assertions are what stop the loop from quietly going around that helper — in round
        // 2 nothing did, so an ascending loop that leaves a dead entry behind stayed green while a
        // neighbouring test proved only that the lookup returns both indices.
        assertTrue("the removal order must come from removalOrder(), the pure helper whose order "
                        + "is asserted (R4)",
                body.contains("removalOrder("));
        assertFalse("the removal loop must not walk the ascending lookup directly: once a lower "
                        + "index is removed the next one is out of range, removeFromQueue's "
                        + "0 <= p < size() guard silently refuses it, and a dead entry stays "
                        + "queued with the suite green (R4)",
                body.contains("removeFromQueue(positions.get("));
        assertTrue("the removal loop must consume the list removalOrder() returns — dropping back "
                        + "to a single index would leave a twice-queued song's second entry "
                        + "pointing at a file the server no longer has (R4)",
                body.contains("removeFromQueue(order.get("));
        assertTrue("the helper must know whether the removed entry was the current one (R4)",
                body.contains("getPosition()"));
        assertTrue("the helper must start the track that now occupies the index (R4)",
                body.contains("playSongAt("));
        assertTrue("a lone deleted entry must stop playback rather than sit on a dead item (R4)",
                body.contains("isEmpty()") && body.contains("clearQueue()"));
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
        String beforeRequest = codeOnly(util.substring(entry, apiCall));
        assertTrue("guard must arm the latch before the request (R8)",
                beforeRequest.contains("tryBeginDelete("));
        // R3/R4: nothing may be reported to the user before the server has answered. This is
        // the optimistic-success mutant the whole-file reading let through — the outcome
        // dispatch belongs to the callbacks, not to the request path.
        assertFalse("no outcome may be dispatched before the request is issued (R3/R4: the "
                        + "success claim must follow the server)",
                beforeRequest.contains("handleOutcome("));
        assertFalse("the request path may not claim success up front (R4)",
                beforeRequest.contains("DeleteOutcome.SUCCESS"));
        int handler = util.indexOf("static void handleOutcome");
        int switchStart = util.indexOf("switch (outcome)", handler);
        assertTrue("outcome handler must exist", handler >= 0 && switchStart > handler);
        String beforeSwitch = util.substring(handler, switchStart);
        assertTrue("every terminal outcome releases the latch (R8)",
                beforeSwitch.contains("endDelete(itemId)"));
    }

    /**
     * R10 (code leg; E2E-5 with QA): on success a suppression guard armed for the
     * deleted id is cleared — a stale guard would otherwise persist against a dead item
     * — and the advance runs before the guard cleanup so subsequent session reports name
     * the new current item. MusicService's own semantics are untouched (call-only).
     */
    @Test
    public void testSuccessClearsStaleSuppressionGuardOnlyForTheDeletedId() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        int start = util.indexOf("case SUCCESS:");
        assertTrue("success branch must exist", start >= 0);
        int end = util.indexOf("break;", start);
        String block = util.substring(start, end > 0 ? end : util.length());
        assertTrue("success must clear the guard via the existing helper (R10)",
                block.contains("clearSuppressionIfOurs("));
        assertTrue("advance must run inside the success branch (R10 sequencing)",
                block.contains("removeFromQueueAndAdvance("));
        int advance = block.indexOf("removeFromQueueAndAdvance(");
        int clear = block.indexOf("clearSuppressionIfOurs(");
        assertTrue("advance precedes guard cleanup so reports follow the new item (R10)",
                advance < clear);

        int guard = util.indexOf("private static void clearSuppressionIfOurs");
        assertTrue("guard helper must exist", guard >= 0);
        String guardBody = util.substring(guard, util.indexOf("\n    }", guard));
        assertTrue("guard is cleared ONLY when it names the deleted id (R10)",
                guardBody.contains("MusicService.isServerReportSuppressed(itemId)"));
        assertTrue(guardBody.contains("MusicService.clearServerReportSuppression()"));
        // The 404 quiet-success branch must NOT clear the guard (plan note 5).
        int gone = util.indexOf("case ALREADY_GONE:");
        int goneEnd = util.indexOf("break;", gone);
        assertFalse("404 path must not clear the suppression guard (success-only)",
                util.substring(gone, goneEnd).contains("clearSuppressionIfOurs"));
    }

    /**
     * R11, cost and diagnosability. purgeCacheEntry runs on the Volley callback, which this
     * app delivers on the UI thread, so a full {@code getSongs()} scan plus three file deletes
     * is an ANR risk on a large library; and a silent {@code catch (Exception ignored)} makes a
     * device-side purge failure invisible — the exact case the R11 instrumented test would have
     * to catch. Asserted on the method body so another method's logging cannot satisfy it.
     */
    @Test
    public void testCachePurgeIsNarrowAndLogsItsCatch() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        int start = util.indexOf("public static void purgeCacheEntry");
        assertTrue("the cache purge entry point must exist (R11)", start >= 0);
        int end = util.indexOf("\n    }", start);
        assertTrue("the cache purge must have a body (R11)", end > start);
        // Comments are stripped so prose can neither satisfy this nor trip it.
        String body = codeOnly(util.substring(start, end));

        assertFalse("the purge must not scan the whole songs table on the UI thread (R11)",
                body.contains("getSongs()"));
        assertTrue("the purge must look up the single cached song by the deleted id (R11)",
                body.contains("getSong(itemId)"));
        assertTrue("the purge must still delete the cache row (R11)",
                body.contains("deleteFromCache(itemId)"));
        assertTrue("a swallowed purge failure must be logged, not silent (R11)",
                body.contains("Log.w(") || body.contains("Log.e("));
        assertFalse("the purge catch must not be named ignored / left empty (R11)",
                body.contains("catch (Exception ignored)"));
    }

    /**
     * R11 + plan note 5: the cache purge runs on the success outcome only — the 404
     * quiet-success leaves cached bytes alone (the file may still exist on storage).
     */
    @Test
    public void testCachePurgeIsSuccessOutcomeOnly() throws Exception {
        String util = read("src/main/java/club/thatpetbff/gramophone/util/LibraryDeleteUtil.java");
        int success = util.indexOf("case SUCCESS:");
        String successBlock = util.substring(success, util.indexOf("break;", success));
        assertTrue("success purges the local cache row and files (R11)",
                successBlock.contains("purgeCacheEntry("));

        int gone = util.indexOf("case ALREADY_GONE:");
        String goneBlock = util.substring(gone, util.indexOf("break;", gone));
        assertFalse("404 must not purge the cache (plan note 5)",
                goneBlock.contains("purgeCacheEntry("));
    }

    /**
     * Every string key in the default resources whose name carries the feature's
     * {@code delete_from_library} prefix (which also covers {@code action_delete_from_library}).
     */
    private static List<String> deleteFromLibraryKeys() throws Exception {
        return stringKeysMatching(read("src/main/res/values/strings.xml"), "delete_from_library");
    }

    /** Drops comment lines, so a source contract asserts on code rather than prose. */
    private static String codeOnly(String block) {
        StringBuilder kept = new StringBuilder();
        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            kept.append(line).append('\n');
        }
        return kept.toString();
    }

    /** All {@code <string name="...">} keys in an XML document containing the given fragment. */
    private static List<String> stringKeysMatching(String xml, String nameFragment) {
        List<String> keys = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<string\\s+name=\"([^\"]*)\"")
                .matcher(xml);
        while (m.find()) {
            if (m.group(1).contains(nameFragment)) {
                keys.add(m.group(1));
            }
        }
        java.util.Collections.sort(keys);
        return keys;
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
