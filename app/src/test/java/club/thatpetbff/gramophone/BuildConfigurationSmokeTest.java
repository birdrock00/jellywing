package club.thatpetbff.gramophone;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class BuildConfigurationSmokeTest {
    @Test
    public void appBuildTargetsAndroidSdk36() throws IOException {
        String buildGradle = readProjectFile("app/build.gradle");

        assertTrue(
                "compileSdk must be 36 after the migration",
                Pattern.compile("\\bcompileSdk(?:Version)?\\s*=\\s*36\\b").matcher(buildGradle).find());
        assertTrue(
                "targetSdk must be 36 after the migration",
                Pattern.compile("\\btargetSdk(?:Version)?\\s*=\\s*36\\b").matcher(buildGradle).find());
    }

    @Test
    public void appDeclaresInstrumentationRunner() throws IOException {
        String buildGradle = readProjectFile("app/build.gradle");

        assertTrue(
                "instrumented UI tests require AndroidJUnitRunner",
                buildGradle.contains("androidx.test.runner.AndroidJUnitRunner"));
    }

    @Test
    public void launcherAndApplicationEntryPointsRemainWired() throws IOException {
        String manifest = readProjectFile("app/src/main/AndroidManifest.xml");

        assertTrue(manifest.contains("android:name=\".App\""));
        assertTrue(manifest.contains("android:name=\".activities.SplashActivity\""));
        assertTrue(manifest.contains("android.intent.action.MAIN"));
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"));
        assertTrue(manifest.contains("android.permission.INTERNET"));
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
