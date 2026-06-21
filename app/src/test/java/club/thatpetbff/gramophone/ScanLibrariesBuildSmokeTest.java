package club.thatpetbff.gramophone;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ScanLibrariesBuildSmokeTest {

    @Test
    public void menuDeclaresScanLibrariesItem() throws IOException {
        String menu = readProjectFile("app/src/main/res/menu/menu_main.xml");

        assertTrue(
                "menu_main.xml must declare the action_scan_libraries item",
                menu.contains("@+id/action_scan_libraries"));
    }

    @Test
    public void stringsDeclareScanLibrariesCopy() throws IOException {
        String strings = readProjectFile("app/src/main/res/values/strings.xml");

        assertTrue(
                "strings.xml must define action_scan_libraries",
                strings.contains("name=\"action_scan_libraries\""));
        assertTrue(
                "strings.xml must define scan_libraries_started toast",
                strings.contains("name=\"scan_libraries_started\""));
        assertTrue(
                "strings.xml must define scan_libraries_success toast",
                strings.contains("name=\"scan_libraries_success\""));
        assertTrue(
                "strings.xml must define scan_libraries_failed toast",
                strings.contains("name=\"scan_libraries_failed\""));
    }

    @Test
    public void libraryFragmentWiresScanLibraries() throws IOException {
        String fragment = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/fragments/main/LibraryFragment.java");

        assertTrue(
                "LibraryFragment must reference R.id.action_scan_libraries",
                fragment.contains("R.id.action_scan_libraries"));
        assertTrue(
                "LibraryFragment must call QueryUtil.scanAllLibraries",
                fragment.contains("QueryUtil.scanAllLibraries"));
    }

    @Test
    public void queryUtilImplementsScanAllLibraries() throws IOException {
        String queryUtil = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/util/QueryUtil.java");

        assertTrue(
                "QueryUtil must declare a scanAllLibraries method",
                queryUtil.contains("scanAllLibraries"));
        assertTrue(
                "QueryUtil must target the /Library/Refresh endpoint",
                queryUtil.contains("/Library/Refresh"));
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        for (Path current = start; current != null; current = current.getParent()) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }

        throw new IOException("Unable to locate " + relativePath + " from " + start);
    }
}
