package com.dkanada.gramophone.player;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BottomNavigationInsetRegressionTest {
    @Test
    public void musicPanelAppliesNavigationBarInsetToBottomPlaybackArea() throws IOException {
        String source = readProjectFile("app/src/main/java/com/dkanada/gramophone/activities/base/AbsMusicPanelActivity.java");

        assertTrue(source.contains("WindowInsetsCompat.Type.navigationBars()"));
        assertTrue(source.contains("ViewCompat.setOnApplyWindowInsetsListener(binding.slidingPanel"));
        assertTrue(source.contains("slidingPanel.setPadding("));
        assertTrue(source.contains("navigationBarInsetBottom"));
    }

    @Test
    public void visibleMiniPlayerHeightIncludesNavigationBarInset() throws IOException {
        String source = readProjectFile("app/src/main/java/com/dkanada/gramophone/activities/base/AbsMusicPanelActivity.java");

        assertTrue(source.contains("binding.slidingLayout.setPanelHeight(getVisibleBottomBarHeight());"));
        assertTrue(source.contains("return getResources().getDimensionPixelSize(R.dimen.mini_player_height) + navigationBarInsetBottom;"));
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
