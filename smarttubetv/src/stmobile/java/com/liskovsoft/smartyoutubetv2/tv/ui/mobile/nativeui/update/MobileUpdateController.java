package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import androidx.annotation.Nullable;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.appupdatechecker2.AppUpdateCheckerListener;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.Collections;
import java.util.List;

/** Mobile-safe UI around the shared APK update checker. */
public final class MobileUpdateController implements AppUpdateCheckerListener {
    public interface PermissionRequester {
        void requestInstallPermission();
    }

    private final Activity activity;
    private final PermissionRequester permissionRequester;
    private AppUpdateChecker checker;
    private AlertDialog dialog;
    private boolean active;
    private boolean updateReady;

    public MobileUpdateController(Activity activity, PermissionRequester permissionRequester) {
        this.activity = activity;
        this.permissionRequester = permissionRequester;
    }

    public void check() {
        dismissDialog();
        active = true;
        updateReady = false;
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.mobile_update_check)
                .setMessage(R.string.mobile_update_checking)
                .setCancelable(false)
                .create();
        dialog.show();
        checker = new AppUpdateChecker(activity, this);
        checker.forceCheckForUpdates(withCacheBuster(
                activity.getResources().getStringArray(R.array.update_urls)));
    }

    @Override public void onUpdateFound(String versionName, List<String> changelog, String apkPath) {
        if (!canShow()) return;
        updateReady = true;
        String details = joinChangelog(changelog);
        dismissDialog();
        dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.mobile_update_available_title, versionName))
                .setMessage(activity.getString(R.string.mobile_update_available_message, details))
                .setNegativeButton(R.string.mobile_update_later, null)
                .setPositiveButton(R.string.mobile_update_install, (d, which) -> installDownloadedUpdate())
                .create();
        dialog.show();
    }

    @Override public void onUpdateError(Exception error) {
        if (!canShow()) return;
        dismissDialog();
        if (error != null && AppUpdateCheckerListener.LATEST_VERSION.equals(error.getMessage())) {
            dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.mobile_update_latest_title)
                    .setMessage(R.string.mobile_update_latest_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        } else {
            String message = error == null || error.getMessage() == null
                    ? error == null ? "Unknown error" : error.getClass().getSimpleName()
                    : error.getMessage();
            dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.mobile_update_error_title)
                    .setMessage(activity.getString(R.string.mobile_update_error_message, message))
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }
        dialog.show();
    }

    public void resumeInstallAfterPermission() {
        if (!updateReady || checker == null || !canShow()) return;
        if (canInstallPackages()) {
            checker.installUpdate();
        } else {
            dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.mobile_update_permission_title)
                    .setMessage(R.string.mobile_update_permission_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
            dialog.show();
        }
    }

    public void close() {
        active = false;
        dismissDialog();
    }

    private void installDownloadedUpdate() {
        if (checker == null || !updateReady) return;
        if (canInstallPackages()) {
            checker.installUpdate();
        } else {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.mobile_update_permission_title)
                    .setMessage(R.string.mobile_update_permission_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.mobile_update_permission_open,
                            (d, which) -> permissionRequester.requestInstallPermission())
                    .show();
        }
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls();
    }

    private boolean canShow() {
        return active && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed());
    }

    private void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private static String joinChangelog(@Nullable List<String> changelog) {
        List<String> values = changelog == null ? Collections.emptyList() : changelog;
        if (values.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append("• ").append(value.trim());
        }
        return result.toString();
    }

    private static String[] withCacheBuster(String[] urls) {
        if (urls == null) return new String[0];
        String[] result = urls.clone();
        String value = Long.toString(System.currentTimeMillis());
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null || result[i].isEmpty()) continue;
            result[i] = result[i] + (result[i].contains("?") ? "&" : "?") + "ota_check=" + value;
        }
        return result;
    }
}
