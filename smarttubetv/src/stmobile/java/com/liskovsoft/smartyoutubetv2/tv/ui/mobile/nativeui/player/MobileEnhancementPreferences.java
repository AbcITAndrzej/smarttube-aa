package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Mobile-only switches for optional integrations added on top of the native phone/tablet UI.
 *
 * <p>All options default to enabled, but they are independent from the global SponsorBlock /
 * DeArrow feature switches. In other words this class only decides whether the new mobile UI
 * participates in an already configured integration. Android Auto never reads this file.</p>
 */
public final class MobileEnhancementPreferences {
    private static final String PREF_FILE = "smarttube_mobile_enhancements";

    private static final String KEY_SPONSORBLOCK_SEEKBAR_MARKERS =
            "sponsorblock_seekbar_markers";
    private static final String KEY_DEARROW_NATIVE_LISTS =
            "dearrow_native_lists";
    private static final String KEY_UNLOCALIZED_TITLES_NATIVE_LISTS =
            "unlocalized_titles_native_lists";
    private static final String KEY_FALLBACK_THUMBNAILS_NATIVE_LISTS =
            "fallback_thumbnails_native_lists";

    private final SharedPreferences preferences;

    public MobileEnhancementPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    /** Render SponsorBlock/chapter colored segments on the new mobile seek bar. */
    public boolean isSponsorBlockSeekBarMarkersEnabled() {
        return preferences.getBoolean(KEY_SPONSORBLOCK_SEEKBAR_MARKERS, true);
    }

    public void setSponsorBlockSeekBarMarkersEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_SPONSORBLOCK_SEEKBAR_MARKERS, enabled).apply();
    }

    /** Apply DeArrow community titles/thumbnails to native mobile browse/search/channel lists. */
    public boolean isDeArrowNativeListsEnabled() {
        return preferences.getBoolean(KEY_DEARROW_NATIVE_LISTS, true);
    }

    public void setDeArrowNativeListsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DEARROW_NATIVE_LISTS, enabled).apply();
    }

    /** Apply the existing "original/unlocalized titles" option to native mobile lists. */
    public boolean isUnlocalizedTitlesNativeListsEnabled() {
        return preferences.getBoolean(KEY_UNLOCALIZED_TITLES_NATIVE_LISTS, true);
    }

    public void setUnlocalizedTitlesNativeListsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_UNLOCALIZED_TITLES_NATIVE_LISTS, enabled).apply();
    }

    /** Apply the existing start/middle/end YouTube fallback thumbnail choice to native lists. */
    public boolean isFallbackThumbnailsNativeListsEnabled() {
        return preferences.getBoolean(KEY_FALLBACK_THUMBNAILS_NATIVE_LISTS, true);
    }

    public void setFallbackThumbnailsNativeListsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_FALLBACK_THUMBNAILS_NATIVE_LISTS, enabled).apply();
    }

    public void reset() {
        preferences.edit().clear().apply();
    }
}
