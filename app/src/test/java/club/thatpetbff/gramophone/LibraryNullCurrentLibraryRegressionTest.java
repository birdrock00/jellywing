package club.thatpetbff.gramophone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Regression test for the launch crash:
 *
 * <pre>
 * java.lang.NullPointerException: Attempt to invoke virtual method
 *   'String org.jellyfin.apiclient.model.dto.BaseItemDto.getId()'
 *   on a null object reference
 *   at club.thatpetbff.gramophone.fragments.library.SongsFragment.createQuery
 * </pre>
 *
 * QueryUtil.currentLibrary is only populated asynchronously after the Jellyfin
 * user views load. When the process is recreated while a library tab was open,
 * the fragment hierarchy is restored before the library is loaded, so every
 * library query builder must tolerate a null currentLibrary instead of
 * dereferencing it. MainActivity must also not dereference it from the
 * navigation listener.
 */
public class LibraryNullCurrentLibraryRegressionTest {

    private static final String LIBRARY_FRAGMENTS_PATH = "app/src/main/java/club/thatpetbff/gramophone/fragments/library/%s.java";
    private static final String[] LIBRARY_FRAGMENTS = {
            "SongsFragment",
            "AlbumsFragment",
            "ArtistsFragment",
            "GenresFragment",
            "PlaylistsFragment"
    };

    @Test
    public void libraryTabQueryBuildersGuardAgainstNullCurrentLibrary() throws IOException {
        for (String fragment : LIBRARY_FRAGMENTS) {
            String source = readProjectFile(String.format(LIBRARY_FRAGMENTS_PATH, fragment));

            assertTrue(fragment + " must null-check currentLibrary before setParentId",
                    source.contains("if (QueryUtil.currentLibrary != null) {"));
            assertTrue(fragment + " must wrap setParentId inside the null check",
                    source.contains("query.setParentId(QueryUtil.currentLibrary.getId());"));
        }
    }

    @Test
    public void navigationListenerDoesNotDereferenceNullCurrentLibrary() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/activities/MainActivity.java");

        assertTrue("drawer item id comparison must null-check currentLibrary first",
                source.contains("if (QueryUtil.currentLibrary != null && menuItemId == QueryUtil.currentLibrary.getId().hashCode())"));
        assertFalse("the unguarded comparison must no longer exist",
                source.contains("if (menuItemId == QueryUtil.currentLibrary.getId().hashCode())"));
    }

    @Test
    public void restoredFragmentHierarchyIsRebuiltAfterLibrariesLoad() throws IOException {
        String source = readProjectFile("app/src/main/java/club/thatpetbff/gramophone/activities/MainActivity.java");

        assertTrue("a rebuild flag must be recorded when the process has no library yet",
                source.contains("needsLibraryRebuild = QueryUtil.currentLibrary == null;"));
        assertTrue("the library fragment must be re-created after a rebuild is needed",
                source.contains("if (state == null || needsLibraryRebuild) {"));
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