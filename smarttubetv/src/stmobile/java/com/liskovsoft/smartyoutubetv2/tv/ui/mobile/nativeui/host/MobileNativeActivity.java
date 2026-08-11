package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.host;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileNavigator;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileNavigatorOwner;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment.MobileBrowseFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment.MobileSearchFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment.MobileSettingsFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileNativeDependencies;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy.SmartTubeMobileNativeProvider;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.performance.MobilePerformanceMonitor;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.startup.MobileStartupPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.update.MobileUpdateController;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive.AndroidAutoPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive.ExperimentalCarVideoGate;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.signin.SignInActivity;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/** Preview host for the Leanback-free mobile UI. */
public class MobileNativeActivity extends AppCompatActivity implements MobileNavigatorOwner {
    public static final String ACTION_OPEN_BACKGROUND_PLAYER =
            "app.smarttube.mobile.action.OPEN_BACKGROUND_PLAYER";
    public static final String EXTRA_MEDIA_ID = "mobile_media_id";
    public static final String EXTRA_POSITION_MS = "mobile_position_ms";
    private static final int REQUEST_POST_NOTIFICATIONS = 3208;
    private static final int REQUEST_STARTUP_INSTALL_PERMISSION = 3209;
    private static final long STARTUP_FLOW_DELAY_MS = 700L;
    private MobileFragmentNavigator navigator;
    private BottomNavigationView bottomNavigation;
    private boolean syncingBottomNavigation;
    private MobilePerformanceMonitor performanceMonitor;
    private final Handler startupHandler = new Handler(Looper.getMainLooper());
    private final Runnable startupRunnable = this::runStartupFlow;
    private MobileStartupPreferences startupPreferences;
    private MobileUpdateController startupUpdateController;
    private AlertDialog signInDialog;
    private boolean startupFlowHandled;
    private boolean waitingForSignInReturn;
    private boolean startupUpdateStarted;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isHostAllowed()) {
            finish();
            return;
        }
        // Package updates may restore a manifest-disabled component even though the opt-in
        // preference survives. Reconcile both states on every normal app start.
        ExperimentalCarVideoGate.setEnabled(this,
                new AndroidAutoPreferences(this).isExperimentalParkedVideoEnabled());
        performanceMonitor = MobilePerformanceMonitor.get(this);
        performanceMonitor.onActivityCreated();
        if (!MobileNativeDependencies.isConfigured()) {
            MobileNativeDependencies.install(SmartTubeMobileNativeProvider.create(this));
        }
        startupPreferences = new MobileStartupPreferences(this);
        startupUpdateController = new MobileUpdateController(this,
                this::requestStartupInstallPermission);
        setContentView(R.layout.mobile_native_activity);
        bottomNavigation = findViewById(R.id.mobile_bottom_navigation);
        navigator = new MobileFragmentNavigator(this);
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (syncingBottomNavigation) return true;
            int id = item.getItemId();
            if (id == R.id.mobile_nav_home) {
                navigator.openBrowse("home");
                return true;
            }
            if (id == R.id.mobile_nav_shorts) {
                navigator.openBrowse("shorts");
                return true;
            }
            if (id == R.id.mobile_nav_subscriptions) {
                navigator.openBrowse("subscriptions");
                return true;
            }
            if (id == R.id.mobile_nav_search) {
                navigator.openSearch("");
                return true;
            }
            if (id == R.id.mobile_nav_settings) {
                navigator.openSettings();
                return true;
            }
            return false;
        });
        getSupportFragmentManager().addOnBackStackChangedListener(navigator::syncChromeWithCurrentFragment);
        if (shouldRequestNotificationPermission()) requestNotificationPermissionIfNeeded();
        boolean openedFromNotification = openPlaybackIntent(getIntent());
        if (!openedFromNotification && savedInstanceState == null) {
            navigator.openBrowse("home");
        } else if (!openedFromNotification) {
            navigator.syncChromeWithCurrentFragment();
        }
        if (shouldRunStartupFlow()) {
            startupHandler.postDelayed(startupRunnable, STARTUP_FLOW_DELAY_MS);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (performanceMonitor != null) performanceMonitor.onActivityResumed();
        ViewManager.instance(this).addTop(this);
        if (waitingForSignInReturn) {
            waitingForSignInReturn = false;
            startupHandler.postDelayed(this::startAutomaticUpdateIfEnabled, 350L);
        }
    }

    @Override protected void onPause() {
        if (performanceMonitor != null) performanceMonitor.onActivityPaused();
        super.onPause();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (performanceMonitor != null) performanceMonitor.onTrimMemory(level);
    }

    @Override protected void onDestroy() {
        startupHandler.removeCallbacksAndMessages(null);
        if (signInDialog != null) {
            signInDialog.dismiss();
            signInDialog = null;
        }
        if (startupUpdateController != null) {
            startupUpdateController.close();
            startupUpdateController = null;
        }
        super.onDestroy();
    }

    public void openClassicHome() {
        Intent intent = new Intent(this, BrowseActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPlaybackIntent(intent);
    }

    private boolean openPlaybackIntent(Intent intent) {
        if (intent == null || !ACTION_OPEN_BACKGROUND_PLAYER.equals(intent.getAction())) return false;
        String mediaId = intent.getStringExtra(EXTRA_MEDIA_ID);
        if (mediaId == null || mediaId.trim().isEmpty()) return false;
        long positionMs = Math.max(0L, intent.getLongExtra(EXTRA_POSITION_MS, 0L));
        navigator.openPlayback(mediaId, positionMs);
        intent.setAction(null); // Avoid reopening after Activity recreation.
        return true;
    }

    /** Parked/embedded hosts can suppress a phone-only permission prompt. */
    protected boolean shouldRequestNotificationPermission() { return true; }

    /** Projected/embedded hosts must not display phone startup dialogs. */
    protected boolean shouldRunStartupFlow() { return true; }

    /** Specialized hosts may reject creation before dependencies, UI or a player are created. */
    protected boolean isHostAllowed() { return true; }

    private void runStartupFlow() {
        if (startupFlowHandled || isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        // Wait until notification permission or another system window no longer covers the host.
        if (!hasWindowFocus()) {
            startupHandler.postDelayed(startupRunnable, 400L);
            return;
        }
        startupFlowHandled = true;
        boolean signedIn;
        try {
            signedIn = YouTubeServiceManager.instance().getSignInService().isSigned();
        } catch (RuntimeException error) {
            MobileDiagnostics.error("StartupAuth", "unable to read sign-in state", error);
            startAutomaticUpdateIfEnabled();
            return;
        }
        MobileDiagnostics.info("StartupAuth", "signedIn=" + signedIn);
        if (signedIn) {
            startAutomaticUpdateIfEnabled();
        } else {
            showSignInPrompt();
        }
    }

    private void showSignInPrompt() {
        if (isFinishing() || signInDialog != null) return;
        signInDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.mobile_startup_signin_title)
                .setMessage(R.string.mobile_startup_signin_message)
                .setNegativeButton(R.string.mobile_startup_signin_later,
                        (dialog, which) -> startAutomaticUpdateIfEnabled())
                .setPositiveButton(R.string.mobile_startup_signin_action, (dialog, which) -> {
                    waitingForSignInReturn = true;
                    startActivity(new Intent(this, SignInActivity.class));
                })
                .create();
        signInDialog.setOnCancelListener(dialog -> startAutomaticUpdateIfEnabled());
        signInDialog.setOnDismissListener(dialog -> signInDialog = null);
        signInDialog.show();
    }

    private void startAutomaticUpdateIfEnabled() {
        if (startupUpdateStarted || startupUpdateController == null || startupPreferences == null) {
            return;
        }
        startupUpdateStarted = true;
        if (startupPreferences.isStartupUpdateCheckDisabled()) {
            MobileDiagnostics.info("StartupUpdate", "automatic update check disabled by user");
            return;
        }
        startupUpdateController.checkAutomatically();
    }

    private void requestStartupInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (startupUpdateController != null) {
                startupUpdateController.resumeInstallAfterPermission();
            }
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_STARTUP_INSTALL_PERMISSION);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode,
                                              @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_STARTUP_INSTALL_PERMISSION
                && startupUpdateController != null) {
            startupUpdateController.resumeInstallAfterPermission();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_POST_NOTIFICATIONS);
    }

    void updateChrome(int selectedItemId, boolean showBottomNavigation) {
        bottomNavigation.setVisibility(showBottomNavigation ? View.VISIBLE : View.GONE);
        if (showBottomNavigation && selectedItemId != View.NO_ID
                && bottomNavigation.getSelectedItemId() != selectedItemId) {
            syncingBottomNavigation = true;
            bottomNavigation.setSelectedItemId(selectedItemId);
            syncingBottomNavigation = false;
        }
    }

    int destinationFor(Fragment fragment) {
        if (fragment instanceof MobileBrowseFragment) {
            MobileBrowseFragment browse = (MobileBrowseFragment) fragment;
            if (browse.isItemDetail()) return View.NO_ID;
            if ("shorts".equals(browse.getPageId())) return R.id.mobile_nav_shorts;
            if ("subscriptions".equals(browse.getPageId())) return R.id.mobile_nav_subscriptions;
            return R.id.mobile_nav_home;
        }
        if (fragment instanceof MobileSearchFragment) return R.id.mobile_nav_search;
        if (fragment instanceof MobileSettingsFragment) return R.id.mobile_nav_settings;
        return View.NO_ID;
    }

    @Override public MobileNavigator getMobileNavigator() { return navigator; }
}
