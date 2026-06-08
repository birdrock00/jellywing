package com.dkanada.gramophone;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ActivityLifecycleRegressionTest {
    @Test
    public void delayedPermissionDialogsCheckActivityStateBeforeShowing() throws IOException {
        String source = readProjectFile("app/src/main/java/com/dkanada/gramophone/activities/base/AbsBaseActivity.java");

        assertTrue(source.contains("handler.postDelayed(() -> showDialogIfAlive(builder), 2000);"));
        assertTrue(source.contains("private void showDialogIfAlive(AlertDialog.Builder builder)"));
        assertTrue(source.contains("if (isFinishing() || isDestroyed())"));
        assertTrue(source.contains("builder.show();"));
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
