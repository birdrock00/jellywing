package club.thatpetbff.gramophone.activities.base;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AbsBaseActivityPromptGuardSourceTest {
    @Test
    public void batteryOptimizationDialogIsMarkedShownBeforeDisplay() throws Exception {
        String source = readSource();
        String method = methodBody(source, "showBatteryOptimizationDialogIfAlive");

        assertTrue(method.contains("BATTERY_OPTIMIZATION_PROMPT_SHOWN"));
        assertTrue("Battery prompt should be marked before builder.show()",
                method.indexOf("putBoolean(BATTERY_OPTIMIZATION_PROMPT_SHOWN, true)")
                        < method.indexOf("builder.show()"));
    }

    @Test
    public void permissionDialogAndSnackbarAreOneShotGuarded() throws Exception {
        String source = readSource();
        String dialogMethod = methodBody(source, "showPermissionDialogIfAlive");
        String warningMethod = methodBody(source, "showPermissionWarning");

        assertTrue("Permission dialog should be marked before builder.show()",
                dialogMethod.indexOf("markPermissionWarningShown()")
                        < dialogMethod.indexOf("builder.show()"));
        assertTrue("Snackbar warning should be marked when displayed",
                warningMethod.indexOf("markPermissionWarningShown()")
                        < warningMethod.indexOf("Snackbar.make("));
    }

    @Test
    public void batteryPromptConsumesEligiblePermissionWarningSlot() throws Exception {
        String source = readSource();
        String postCreate = methodBody(source, "onPostCreate");
        String batteryBranch = postCreate.substring(
                postCreate.indexOf("BATTERY_OPTIMIZATION_PROMPT_SHOWN"),
                postCreate.indexOf("} else if (canShowPermissionWarning)"));

        assertTrue("Battery branch should consume an eligible permission warning slot",
                batteryBranch.contains("if (canShowPermissionWarning)"));
        assertTrue("Permission warning should be marked before battery dialog is scheduled",
                batteryBranch.indexOf("markPermissionWarningShown()")
                        < batteryBranch.indexOf("showBatteryOptimizationDialogIfAlive"));
    }

    private static String readSource() throws Exception {
        return new String(Files.readAllBytes(
                Path.of("src/main/java/club/thatpetbff/gramophone/activities/base/AbsBaseActivity.java")),
                StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String methodName) {
        int start = source.indexOf("private void " + methodName);
        if (start < 0) {
            start = source.indexOf("protected void " + methodName);
        }
        assertTrue("Missing method " + methodName, start >= 0);
        int nextMethod = source.indexOf("\n    private ", start + 1);
        if (nextMethod < 0) {
            nextMethod = source.length();
        }
        return source.substring(start, nextMethod);
    }
}
