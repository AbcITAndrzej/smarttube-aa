package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == savedInstanceState) {
            if (isMobileTouchNavigationEnabled()) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(android.R.id.content, new MobileSignInFragment())
                        .commit();
            } else {
                GuidedStepSupportFragment.addAsRoot(this, new SignInFragment(), android.R.id.content);
            }
        }
    }

    @Override
    protected void initTheme() {
        // The mobile manifest supplies a touch-friendly Material theme. Keep it
        // instead of replacing it with the TV browse theme in MotherActivity.
        if (!isMobileTouchNavigationEnabled()) {
            super.initTheme();
        }
    }

    @Override
    public void finish() {
        super.finish();

        finishReally();
    }
}
