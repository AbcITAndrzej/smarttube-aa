package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * Runtime compatibility pass for Leanback settings shown on touch devices.
 *
 * It intentionally limits structural changes to AppDialogActivity. Applying
 * minimum row sizes to browse cards or playback controls globally would distort
 * the television layouts and create regressions.
 */
public final class MobileUiCompatibility implements ViewTreeObserver.OnGlobalLayoutListener {
    private static final long MIN_SCAN_INTERVAL_MS = 120L;

    private final Activity mActivity;
    private final boolean mSettingsScreen;
    private final int mMinimumTouchTargetPx;
    private final float mMinimumTextSp;
    private final int mTabletMinShortSideDp;
    private final int mTabletPaneMaxWidthPx;
    private View mRoot;
    private long mLastScanTime;
    private boolean mReleased;
    private final Runnable mAdaptRunnable = this::adaptNow;

    public MobileUiCompatibility(Activity activity) {
        mActivity = activity;
        mSettingsScreen = isSettingsActivity(activity);

        float density = activity.getResources().getDisplayMetrics().density;
        mMinimumTouchTargetPx = Math.round(
                activity.getResources().getInteger(R.integer.mobile_min_touch_target_dp) * density);
        mMinimumTextSp = activity.getResources().getInteger(R.integer.mobile_min_text_sp);
        mTabletMinShortSideDp = activity.getResources().getInteger(
                R.integer.mobile_tablet_min_short_side_dp);
        mTabletPaneMaxWidthPx = Math.round(
                activity.getResources().getInteger(
                        R.integer.mobile_tablet_settings_pane_max_width_dp) * density);
    }

    public void attach() {
        if (!mSettingsScreen || mActivity.getWindow() == null) {
            return;
        }

        prepareSettingsWindow();
        mRoot = mActivity.getWindow().getDecorView();
        if (mRoot == null) {
            return;
        }

        ViewTreeObserver observer = mRoot.getViewTreeObserver();
        if (observer != null && observer.isAlive()) {
            observer.addOnGlobalLayoutListener(this);
        }
        mRoot.post(mAdaptRunnable);
    }

    public void release() {
        mReleased = true;
        if (mRoot != null) {
            ViewTreeObserver observer = mRoot.getViewTreeObserver();
            if (observer != null && observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(this);
            }
            mRoot.removeCallbacks(mAdaptRunnable);
        }
        mRoot = null;
    }

    @Override
    public void onGlobalLayout() {
        if (mReleased || mRoot == null) {
            return;
        }

        long now = android.os.SystemClock.uptimeMillis();
        if (now - mLastScanTime < MIN_SCAN_INTERVAL_MS) {
            return;
        }
        mLastScanTime = now;
        adaptNow();
    }

    public void adaptNow() {
        if (mReleased || mRoot == null || !mSettingsScreen) {
            return;
        }
        adaptSettingsPane();
        adaptTree(mRoot);
    }


    private void adaptSettingsPane() {
        int paneId = findResourceId("settings_preference_fragment_container", "id");
        if (paneId == 0) {
            return;
        }

        View pane = mRoot.findViewById(paneId);
        if (pane == null || pane.getLayoutParams() == null) {
            return;
        }

        int shortSideDp = mActivity.getResources().getConfiguration().smallestScreenWidthDp;
        ViewGroup.LayoutParams params = pane.getLayoutParams();
        int desiredWidth = MobileSettingsLayoutPolicy.resolvePaneWidth(
                mRoot.getWidth(),
                shortSideDp,
                mTabletMinShortSideDp,
                mTabletPaneMaxWidthPx);
        // On phones this becomes MATCH_PARENT; tablets retain a wider bounded
        // pane so long summaries remain readable without stretching endlessly.

        if (params.width != desiredWidth) {
            params.width = desiredWidth;
            pane.setLayoutParams(params);
        }
    }

    private int findResourceId(String name, String type) {
        int id = mActivity.getResources().getIdentifier(
                name, type, mActivity.getPackageName());
        if (id == 0) {
            id = mActivity.getResources().getIdentifier(
                    name, type, "androidx.leanback.preference");
        }
        return id;
    }

    private void adaptTree(View view) {
        if (view == null || !view.isShown()) {
            return;
        }

        adaptInteractiveTarget(view);
        adaptText(view);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                adaptTree(group.getChildAt(i));
            }
        }
    }

    private void adaptInteractiveTarget(View view) {
        if ((!view.isClickable() && !view.isFocusable()) || !view.isEnabled()) {
            return;
        }

        // Preference rows are normally full-width. A 56dp minimum height makes
        // the whole row a comfortable finger target while preserving the
        // original icon/title arrangement.
        if (view.getMinimumHeight() < mMinimumTouchTargetPx) {
            view.setMinimumHeight(mMinimumTouchTargetPx);
        }

        // Small standalone controls (checkbox, radio, icon button) also need a
        // minimum width, but avoid forcing a huge width onto full preference rows.
        int measuredWidth = view.getMeasuredWidth();
        if (measuredWidth > 0
                && measuredWidth < mMinimumTouchTargetPx
                && view.getMinimumWidth() < mMinimumTouchTargetPx) {
            view.setMinimumWidth(mMinimumTouchTargetPx);
        }
    }

    private void adaptText(View view) {
        if (!(view instanceof TextView)) {
            return;
        }

        TextView textView = (TextView) view;
        if (textView.getText() == null || textView.getText().length() == 0) {
            return;
        }

        float scaledDensity = mActivity.getResources().getDisplayMetrics().scaledDensity;
        if (scaledDensity <= 0f) {
            return;
        }
        float currentSp = textView.getTextSize() / scaledDensity;
        if (currentSp + 0.01f < mMinimumTextSp) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, mMinimumTextSp);
        }
        textView.setIncludeFontPadding(true);
    }

    private void prepareSettingsWindow() {
        Window window = mActivity.getWindow();
        if (window == null) {
            return;
        }

        // Settings should respect status/navigation bars and display cutouts.
        // The video player may still use fullscreen; this is settings-only.
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = window.getDecorView();
        if (decor != null) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    public static boolean isSettingsActivity(Activity activity) {
        if (activity == null) {
            return false;
        }
        String simpleName = activity.getClass().getSimpleName();
        return "AppDialogActivity".equals(simpleName)
                || "AppDialogActivityOpaque".equals(simpleName);
    }

    public static boolean isPlaybackActivity(Activity activity) {
        return activity != null && "PlaybackActivity".equals(activity.getClass().getSimpleName());
    }
}
