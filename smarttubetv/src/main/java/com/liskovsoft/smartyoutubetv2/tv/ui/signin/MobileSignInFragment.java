package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/** Touch-friendly sign-in presentation used only by the mobile flavor. */
public class MobileSignInFragment extends Fragment implements SignInView {
    private static final String TAG = MobileSignInFragment.class.getSimpleName();
    /** YouTube device login works in these browsers. A generic view intent lets the YouTube app win. */
    private static final String[] SIGN_IN_BROWSERS = {
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta"
    };

    private SignInPresenter mSignInPresenter;
    private ImageView mQrCodeView;
    private TextView mUserCodeView;
    private TextView mDescriptionView;
    private Button mOpenBrowserButton;
    private Button mCopyCodeButton;
    private Button mCopyAddressButton;
    private String mUserCode;
    private String mSignInUrl;
    private String mFullSignInUrl;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSignInPresenter = SignInPresenter.instance(requireContext());
        mSignInPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_signin_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mQrCodeView = view.findViewById(R.id.mobile_signin_qr_code);
        mUserCodeView = view.findViewById(R.id.mobile_signin_user_code);
        mDescriptionView = view.findViewById(R.id.mobile_signin_description);
        mOpenBrowserButton = view.findViewById(R.id.mobile_signin_open_browser);
        mCopyCodeButton = view.findViewById(R.id.mobile_signin_copy_code);
        mCopyAddressButton = view.findViewById(R.id.mobile_signin_copy_address);

        ImageButton backButton = view.findViewById(R.id.mobile_signin_back);
        Button continueButton = view.findViewById(R.id.mobile_signin_continue);

        backButton.setOnClickListener(v -> requireActivity().finish());
        continueButton.setOnClickListener(v -> mSignInPresenter.onActionClicked());
        mOpenBrowserButton.setOnClickListener(v -> openInChrome());
        mCopyCodeButton.setOnClickListener(v -> copyCode());
        mCopyAddressButton.setOnClickListener(v -> copyAddress());
        mOpenBrowserButton.setEnabled(false);
        mCopyCodeButton.setEnabled(false);
        mCopyAddressButton.setEnabled(false);

        renderCode();
        mSignInPresenter.onViewInitialized();
    }

    @Override
    public void onDestroyView() {
        mSignInPresenter.onViewDestroyed();
        mQrCodeView = null;
        mUserCodeView = null;
        mDescriptionView = null;
        mOpenBrowserButton = null;
        mCopyCodeButton = null;
        mCopyAddressButton = null;
        super.onDestroyView();
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        mUserCode = userCode;
        mSignInUrl = signInUrl;
        mFullSignInUrl = !TextUtils.isEmpty(fullSignInUrl) ? fullSignInUrl : signInUrl;
        renderCode();
    }

    private void renderCode() {
        if (mUserCodeView == null || TextUtils.isEmpty(mUserCode)) {
            return;
        }

        mUserCodeView.setText(mUserCode);
        boolean hasAddress = !TextUtils.isEmpty(mFullSignInUrl);
        mOpenBrowserButton.setEnabled(hasAddress);
        mCopyCodeButton.setEnabled(true);
        mCopyAddressButton.setEnabled(hasAddress);

        Glide.with(this)
                .load(Utils.toQrCodeLink(mFullSignInUrl))
                .placeholder(R.drawable.activate_account_qrcode)
                .apply(ViewUtil.glideOptions())
                .error(R.drawable.activate_account_qrcode)
                .listener(mErrorListener)
                .into(mQrCodeView);

        String description = getString(R.string.signin_view_description, mSignInUrl);
        int start = description.indexOf(mSignInUrl);
        if (start >= 0) {
            int end = start + mSignInUrl.length();
            mDescriptionView.setText(Utils.color(description,
                    ContextCompat.getColor(requireContext(), R.color.red), start, end));
        } else {
            mDescriptionView.setText(description);
        }
    }

    private void copyCode() {
        if (TextUtils.isEmpty(mUserCode) || !putOnClipboard(mUserCode)) return;
        Toast.makeText(requireContext(), R.string.mobile_signin_copied_code, Toast.LENGTH_LONG).show();
    }

    private void copyAddress() {
        if (TextUtils.isEmpty(mFullSignInUrl) || !putOnClipboard(mFullSignInUrl)) return;
        Toast.makeText(requireContext(), R.string.mobile_signin_copied_address, Toast.LENGTH_LONG).show();
    }

    /** Opens Chrome (then Firefox) with the code already on the clipboard. Never uses a generic view. */
    private void openInChrome() {
        if (TextUtils.isEmpty(mFullSignInUrl)) return;
        putOnClipboard(mUserCode);
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(mFullSignInUrl));
        PackageManager packages = requireContext().getPackageManager();
        for (String browser : SIGN_IN_BROWSERS) {
            Intent targeted = new Intent(view);
            targeted.setPackage(browser);
            if (targeted.resolveActivity(packages) == null) continue;
            try {
                startActivity(targeted);
                Toast.makeText(requireContext(), R.string.mobile_signin_chrome_opened, Toast.LENGTH_LONG).show();
                return;
            } catch (ActivityNotFoundException ignored) {
                // The next listed browser is tried. A generic intent would open the YouTube app.
            }
        }
        putOnClipboard(mFullSignInUrl);
        Toast.makeText(requireContext(), R.string.mobile_signin_no_browser, Toast.LENGTH_LONG).show();
    }

    private boolean putOnClipboard(String value) {
        if (TextUtils.isEmpty(value)) return false;
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return false;
        clipboard.setPrimaryClip(ClipData.newPlainText("waveaa", value));
        return true;
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private final RequestListener<Drawable> mErrorListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                    Target<Drawable> target, boolean isFirstResource) {
            Log.e(TAG, "QR code load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                       DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
}
