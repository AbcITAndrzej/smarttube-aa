package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

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

    private SignInPresenter mSignInPresenter;
    private ImageView mQrCodeView;
    private TextView mUserCodeView;
    private TextView mDescriptionView;
    private Button mOpenBrowserButton;
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

        ImageButton backButton = view.findViewById(R.id.mobile_signin_back);
        Button continueButton = view.findViewById(R.id.mobile_signin_continue);

        backButton.setOnClickListener(v -> requireActivity().finish());
        continueButton.setOnClickListener(v -> mSignInPresenter.onActionClicked());
        mOpenBrowserButton.setOnClickListener(v -> openBrowser());
        mOpenBrowserButton.setEnabled(false);

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
        mOpenBrowserButton.setEnabled(!TextUtils.isEmpty(mFullSignInUrl));

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

    private void openBrowser() {
        if (!TextUtils.isEmpty(mFullSignInUrl)) {
            Utils.openLinkExt(requireContext(), mFullSignInUrl);
        }
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
