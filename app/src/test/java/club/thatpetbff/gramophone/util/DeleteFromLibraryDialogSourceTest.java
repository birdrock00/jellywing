package club.thatpetbff.gramophone.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Source contract on the confirmation dialog itself (R2/R3).
 *
 * <p>The safety property this pins is the one the fragment-side contract cannot see: the
 * network request must exist <em>only</em> inside the dialog's {@code onPositive} handler.
 * Moving the {@code LibraryDeleteUtil.deleteFromLibrary(...)} call up into
 * {@code onCreateDialog} — so a real track is deleted while the confirm dialog is still
 * open — keeps every other contract green, because the call-site count in
 * {@code LibraryDeleteUtil} stays 1 and no other test reads this file. Hence: scope the
 * read to this file, and scope the assertions to the two regions of that file
 * (construction vs. positive button) rather than grepping the whole file.
 */
public class DeleteFromLibraryDialogSourceTest {

    private static final String DIALOG =
            "src/main/java/club/thatpetbff/gramophone/dialogs/DeleteFromLibraryDialog.java";

    /** Region between onCreateDialog opening and the onPositive handler: pure construction. */
    @Test
    public void testConstructingTheDialogIssuesNoRequest() throws Exception {
        String source = read(DIALOG);
        int create = source.indexOf("public Dialog onCreateDialog");
        assertTrue("DeleteFromLibraryDialog must build its dialog in onCreateDialog", create >= 0);
        int positive = source.indexOf("onPositive(", create);
        assertTrue("the dialog must register an onPositive handler (R2)", positive > create);

        String construction = source.substring(create, positive);
        assertFalse("R3: building the confirm dialog must not delete anything — the request "
                        + "belongs to the positive button (opening the dialog must never "
                        + "destroy data)",
                construction.contains("deleteFromLibrary("));
        assertFalse("R2: the dialog must not call ApiClient.DeleteItem directly",
                construction.contains("DeleteItem("));
        assertFalse("R2: the dialog must not call the API client directly",
                construction.contains("getApiClient("));
    }

    /** The positive-button region must be the only place the delete request is issued. */
    @Test
    public void testDeleteCallSitsInsideOnPositive() throws Exception {
        String source = read(DIALOG);
        int positive = source.indexOf("onPositive(");
        assertTrue("the dialog must register an onPositive handler (R2)", positive >= 0);
        int build = source.indexOf(".build()", positive);
        assertTrue("the dialog must be built after the handlers", build > positive);
        String positiveBlock = source.substring(positive, build);

        assertTrue("the positive button must issue the library delete (R2/R3)",
                positiveBlock.contains("deleteFromLibrary("));
        assertFalse("the positive button must not bypass the pipeline with a direct API call (R3)",
                positiveBlock.contains("DeleteItem(") || positiveBlock.contains("getApiClient("));

        // Nothing outside the positive button may issue the request either — the pair of
        // assertions above (construction) and this one (whole file minus positive button)
        // leave no third place to hide it.
        String outsidePositive = source.substring(0, positive) + source.substring(build);
        assertFalse("no code path other than onPositive may call deleteFromLibrary (R3)",
                outsidePositive.contains("deleteFromLibrary("));
    }

    /**
     * R2: the confirm copy is the human gate on an irreversible, server-side action, so the
     * words themselves are load-bearing — a key-exists assertion is not coverage. Replacing
     * the body with "Remove this song?" must not stay green: it drops all three of the
     * warnings the operator approved.
     */
    @Test
    public void testConfirmCopyCarriesTheThreeWarnings() throws Exception {
        String body = stringValue("delete_from_library_body").toLowerCase(java.util.Locale.US);

        assertTrue("confirm copy must name the server as the place the file leaves (R2): " + body,
                body.contains("server"));
        assertTrue("confirm copy must say the deletion is permanent (R2): " + body,
                body.contains("permanent") || body.contains("cannot be undone"));
        assertTrue("confirm copy must warn it leaves every playlist holding the song (R2): " + body,
                body.contains("playlist") && (body.contains("every") || body.contains("all")));
        assertTrue("confirm copy must say it cannot be undone from the app (R2): " + body,
                body.contains("undone") || body.contains("irreversible"));
    }

    /** The confirm/cancel buttons must be unambiguous verbs, not a generic OK (R2). */
    @Test
    public void testConfirmAndCancelButtonsAreDistinctVerbs() throws Exception {
        String confirm = stringValue("delete_from_library_confirm").trim();
        String cancel = stringValue("delete_from_library_cancel").trim();
        assertTrue("the positive button must be an explicit delete verb (R2): " + confirm,
                confirm.toLowerCase(java.util.Locale.US).contains("delete"));
        assertTrue("the negative button must be an explicit cancel verb (R2): " + cancel,
                cancel.toLowerCase(java.util.Locale.US).contains("cancel"));
        assertFalse("positive and negative buttons must not be the same word (R2)",
                confirm.equalsIgnoreCase(cancel));
    }

    /** Reads one <string name="key">value</string> out of the default English resources. */
    private static String stringValue(String key) throws Exception {
        String strings = read("src/main/res/values/strings.xml");
        int start = strings.indexOf("name=\"" + key + "\"");
        assertTrue("missing string resource: " + key, start >= 0);
        int open = strings.indexOf('>', start);
        int close = strings.indexOf("</string>", open);
        assertTrue("unterminated string resource: " + key, open > 0 && close > open);
        return strings.substring(open + 1, close).trim();
    }

    private static String read(String relativeToAppModule) throws Exception {
        return new String(Files.readAllBytes(
                new File(relativeToAppModule).toPath()), StandardCharsets.UTF_8);
    }
}
