package club.thatpetbff.gramophone.util;

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

    private static String read(String relativeToAppModule) throws Exception {
        return new String(Files.readAllBytes(
                new File(relativeToAppModule).toPath()), StandardCharsets.UTF_8);
    }
}
