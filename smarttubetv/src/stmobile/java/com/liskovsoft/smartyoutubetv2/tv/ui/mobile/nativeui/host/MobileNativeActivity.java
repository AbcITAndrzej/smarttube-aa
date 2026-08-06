package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.host;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;

/** Preview host for the Leanback-free mobile UI. */
public final class MobileNativeActivity extends AppCompatActivity implements MobileNavigatorOwner {
    public static final String ACTION_OPEN_BACKGROUND_PLAYER =
            "app.smarttube.mobile.action.OPEN_BACKGROUND_PLAYER";
    public static final String EXTRA_MEDIA_ID = "mobile_media_id";
    public static final String EXTRA_POSITION_MS = "mobile_position_ms";
    private static final int REQUEST_POST_NOTIFICATIONS = 3208;
    private MobileFragmentNavigator navigator;
    private BottomNavigationView bottomNavigation;
    private boolean syncingBottomNavigation;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MobileNativeDependencies.isConfigured()) {
            MobileNativeDependencies.install(SmartTubeMobileNativeProvider.create(this));
        }
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
        requestNotificationPermissionIfNeeded();
        boolean openedFromNotification = openPlaybackIntent(getIntent());
        if (!openedFromNotification && savedInstanceState == null) {
            navigator.openBrowse("home");
        } else if (!openedFromNotification) {
            navigator.syncChromeWithCurrentFragment();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        ViewManager.instance(this).addTop(this);
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
