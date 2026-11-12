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

    private static String read(String relativeToAppModule) throws Exception {
        return new String(Files.readAllBytes(
                new File(relativeToAppModule).toPath()), StandardCharsets.UTF_8);
    }
}
