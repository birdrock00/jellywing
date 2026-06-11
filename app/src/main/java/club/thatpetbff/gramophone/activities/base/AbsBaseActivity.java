package club.thatpetbff.gramophone.activities.base;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import club.thatpetbff.gramophone.activities.MainActivity;
import club.thatpetbff.gramophone.util.NavigationUtil;
import club.thatpetbff.gramophone.R;
import club.thatpetbff.gramophone.util.PreferenceUtil;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public abstract class AbsBaseActivity extends AbsThemeActivity {
    private static final int PERMISSION_REQUEST = 100;
    private static final String BATTERY_OPTIMIZATION_PROMPT_SHOWN = "battery_optimization_prompt_shown";
    private static final String PERMISSION_WARNING_PROMPT_SHOWN = "permission_warning_prompt_shown";

    private List<String> permissions;
    private boolean allowed;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        permissions = getPermissionRequest();
        allowed = checkPermissions();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (!(this instanceof MainActivity)) {
            return;
        }

        PreferenceUtil preferenceUtil = PreferenceUtil.getInstance(this);
        boolean canShowPermissionWarning = canShowPermissionWarning(preferenceUtil);

        if (!preferenceUtil.getPreferences().getBoolean(BATTERY_OPTIMIZATION_PROMPT_SHOWN, false)
            && !checkBatteryOptimization()) {
            if (canShowPermissionWarning) {
                markPermissionWarningShown();
            }

            AlertDialog.Builder batteryOptimizationBuilder = new AlertDialog.Builder(this)
                .setMessage(R.string.battery_optimizations_message)
                .setTitle(R.string.battery_optimizations_title)
                .setPositiveButton(R.string.disable, (dialog, id) -> requestBatteryOptimization())
                .setNegativeButton(R.string.ignore, (dialog, id) -> dialog.dismiss());

            handler.postDelayed(() -> showBatteryOptimizationDialogIfAlive(batteryOptimizationBuilder), 2000);
        } else if (canShowPermissionWarning) {
            AlertDialog.Builder permissionBuilder = new AlertDialog.Builder(this)
                .setMessage(getPermissionMessage())
                .setTitle(R.string.permissions_denied)
                .setNegativeButton(R.string.ignore, (dialog, id) -> showPermissionWarning());

            if (shouldRequestPermissionRationale()) {
                permissionBuilder.setPositiveButton(R.string.action_grant, (dialog, id) -> requestPermissions());
            } else {
                permissionBuilder.setPositiveButton(R.string.action_settings, (dialog, id) -> NavigationUtil.openSettings(this));
            }

            handler.postDelayed(() -> showPermissionDialogIfAlive(permissionBuilder), 2000);
        }
    }

    private boolean hasShownPermissionWarning(PreferenceUtil preferenceUtil) {
        return preferenceUtil.getPreferences().getBoolean(PERMISSION_WARNING_PROMPT_SHOWN, false);
    }

    private boolean canShowPermissionWarning(PreferenceUtil preferenceUtil) {
        return !hasShownPermissionWarning(preferenceUtil)
            && (shouldRequestPermissionRationale() || !checkPermissions());
    }

    private boolean shouldRequestPermissionRationale() {
        return permissions.size() != 0
            && ActivityCompat.shouldShowRequestPermissionRationale(this, permissions.get(0));
    }

    private void markPermissionWarningShown() {
        PreferenceUtil.getInstance(this).getPreferences()
            .edit()
            .putBoolean(PERMISSION_WARNING_PROMPT_SHOWN, true)
            .apply();
    }

    private void showPermissionDialogIfAlive(AlertDialog.Builder builder) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        markPermissionWarningShown();
        builder.show();
    }

    private void showBatteryOptimizationDialogIfAlive(AlertDialog.Builder builder) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        PreferenceUtil.getInstance(this).getPreferences()
            .edit()
            .putBoolean(BATTERY_OPTIMIZATION_PROMPT_SHOWN, true)
            .apply();
        builder.show();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    protected void onResume() {
        super.onResume();

        if (checkPermissions() != allowed) {
            super.recreate();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected View getPermissionWindow() {
        return getWindow().getDecorView();
    }

    protected List<String> getPermissionRequest() {
        return new ArrayList<>();
    }

    protected String getPermissionMessage() {
        return getString(R.string.permissions_denied);
    }

    private void showPermissionWarning() {
        markPermissionWarningShown();
        Snackbar.make(getPermissionWindow(), getPermissionMessage(), Snackbar.LENGTH_SHORT)
            .setAction(R.string.ignore, view -> { })
            .setActionTextColor(PreferenceUtil.getInstance(this).getAccentColor())
            .show();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestBatteryOptimization() {
        Intent intent = new Intent();

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);

        startActivity(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean checkBatteryOptimization() {
        String packageName = getPackageName();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);

        return pm.isIgnoringBatteryOptimizations(packageName);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestPermissions() {
        requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean checkPermissions() {
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode != PERMISSION_REQUEST) {
            return;
        }

        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                showPermissionWarning();
                return;
            }
        }
    }
}
