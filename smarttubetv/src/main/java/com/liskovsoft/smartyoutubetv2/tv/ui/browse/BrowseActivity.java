package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;
import android.view.MotionEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class BrowseActivity extends LeanbackActivity {
    private static final String TAG = BrowseActivity.class.getSimpleName();
    private BrowseFragment mBrowseFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.fragment_main);
            resolveBrowseFragment();
        } catch (NoClassDefFoundError e) {
            // Failed resolution of: Landroidx/lifecycle/ViewTreeLifecycleOwner;
            MessageHelpers.showMessage(this, e.getMessage());
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);

        BrowseFragment fragment = resolveBrowseFragment();
        if (fragment != null && fragment.onMobileBrowseTouchEvent(event)) {
            return true;
        }

        return handled;
    }

    public void toggleBrowseSidebar() {
        BrowseFragment fragment = resolveBrowseFragment();
        if (fragment != null) {
            fragment.toggleHeadersFromTopButton();
        }
    }

    private BrowseFragment resolveBrowseFragment() {
        if (mBrowseFragment == null) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_frame);
            if (fragment instanceof BrowseFragment) {
                mBrowseFragment = (BrowseFragment) fragment;
            }
        }

        return mBrowseFragment;
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }
}
