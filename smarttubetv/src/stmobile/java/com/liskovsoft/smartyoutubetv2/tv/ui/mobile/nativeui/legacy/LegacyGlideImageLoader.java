package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileImageLoader;

public final class LegacyGlideImageLoader implements MobileImageLoader {
    @Override public void load(ImageView target, String url) {
        if (target == null) return;
        if (url == null || url.trim().isEmpty()) {
            clear(target);
            target.setImageDrawable(null);
            return;
        }
        Object currentUrl = target.getTag(R.id.mobile_thumbnail);
        if (url.equals(currentUrl)) {
            return;
        }
        target.setTag(R.id.mobile_thumbnail, url);
        Drawable previous = target.getDrawable();
        if (previous != null) {
            Glide.with(target)
                    .load(url)
                    .placeholder(previous)
                    .centerCrop()
                    .dontAnimate()
                    .into(target);
        } else {
            Glide.with(target)
                    .load(url)
                    .centerCrop()
                    .dontAnimate()
                    .into(target);
        }
    }

    @Override public void clear(ImageView target) {
        if (target == null) return;
        target.setTag(R.id.mobile_thumbnail, null);
        Glide.with(target).clear(target);
    }
}
