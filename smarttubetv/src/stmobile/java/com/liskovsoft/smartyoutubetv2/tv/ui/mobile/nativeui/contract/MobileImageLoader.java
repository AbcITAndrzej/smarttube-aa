package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import android.widget.ImageView;

/** Keeps Glide or another image library outside fragments and adapters. */
public interface MobileImageLoader {
    void load(ImageView target, String url);
    void clear(ImageView target);
}
