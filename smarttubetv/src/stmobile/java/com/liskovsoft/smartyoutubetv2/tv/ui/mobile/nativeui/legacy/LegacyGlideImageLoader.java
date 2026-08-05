package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileImageLoader;

public final class LegacyGlideImageLoader implements MobileImageLoader {
    @Override public void load(ImageView target, String url) {
        if (target == null) return;
        if (url == null || url.trim().isEmpty()) {
            clear(target);
            return;
        }
        Glide.with(target).load(url).centerCrop().into(target);
    }

    @Override public void clear(ImageView target) {
        if (target == null) return;
        Glide.with(target).clear(target);
        target.setImageDrawable(null);
    }
}
