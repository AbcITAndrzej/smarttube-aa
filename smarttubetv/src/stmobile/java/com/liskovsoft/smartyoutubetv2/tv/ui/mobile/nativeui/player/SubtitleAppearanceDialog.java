package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager.SubtitleStyle;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/** Shared subtitle-appearance editor for mobile settings and the active player. */
public final class SubtitleAppearanceDialog {
    private static final float[] SCALES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final int[] FONTS = {
            PlayerData.SUBTITLE_FONT_DEFAULT_BOLD,
            PlayerData.SUBTITLE_FONT_SANS_SERIF,
            PlayerData.SUBTITLE_FONT_SERIF,
            PlayerData.SUBTITLE_FONT_MONOSPACE
    };

    private SubtitleAppearanceDialog() {
    }

    public static void show(Context context, @Nullable Runnable onChanged) {
        PlayerData data = PlayerData.instance(context);
        String[] options = {
                context.getString(R.string.mobile_subtitle_style_value,
                        context.getString(data.getSubtitleStyle().nameResId)),
                context.getString(R.string.mobile_subtitle_size_value, scaleLabel(data.getSubtitleScale())),
                context.getString(R.string.mobile_subtitle_font_value, fontLabel(context, data.getSubtitleFont())),
                context.getString(R.string.mobile_subtitle_reset)
        };

        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_subtitle_appearance_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) chooseStyle(context, onChanged);
                    else if (which == 1) chooseSize(context, onChanged);
                    else if (which == 2) chooseFont(context, onChanged);
                    else showResetConfirmation(context, onChanged);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static String summary(Context context) {
        PlayerData data = PlayerData.instance(context);
        return context.getString(R.string.mobile_subtitle_appearance_value,
                context.getString(data.getSubtitleStyle().nameResId),
                scaleLabel(data.getSubtitleScale()),
                fontLabel(context, data.getSubtitleFont()));
    }

    private static void chooseStyle(Context context, @Nullable Runnable onChanged) {
        PlayerData data = PlayerData.instance(context);
        List<SubtitleStyle> styles = data.getSubtitleStyles();
        String[] labels = new String[styles.size()];
        int selected = styles.indexOf(data.getSubtitleStyle());
        for (int index = 0; index < styles.size(); index++) {
            labels[index] = context.getString(styles.get(index).nameResId);
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_subtitle_style)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    data.setSubtitleStyle(styles.get(which));
                    data.persistNow();
                    changed(onChanged);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void chooseSize(Context context, @Nullable Runnable onChanged) {
        PlayerData data = PlayerData.instance(context);
        String[] labels = new String[SCALES.length];
        int selected = 2;
        for (int index = 0; index < SCALES.length; index++) {
            labels[index] = scaleLabel(SCALES[index]);
            if (Math.abs(data.getSubtitleScale() - SCALES[index]) < 0.01f) selected = index;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_subtitle_size)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    data.setSubtitleScale(SCALES[which]);
                    data.persistNow();
                    changed(onChanged);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void chooseFont(Context context, @Nullable Runnable onChanged) {
        PlayerData data = PlayerData.instance(context);
        String[] labels = new String[FONTS.length];
        int selected = 0;
        for (int index = 0; index < FONTS.length; index++) {
            labels[index] = fontLabel(context, FONTS[index]);
            if (data.getSubtitleFont() == FONTS[index]) selected = index;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_subtitle_font)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    data.setSubtitleFont(FONTS[which]);
                    data.persistNow();
                    changed(onChanged);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void showResetConfirmation(Context context, @Nullable Runnable onChanged) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_subtitle_reset_title)
                .setMessage(R.string.mobile_subtitle_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mobile_player_reset_confirm, (dialog, which) -> {
                    PlayerData data = PlayerData.instance(context);
                    data.resetSubtitleAppearance();
                    data.persistNow();
                    changed(onChanged);
                })
                .show();
    }

    private static String scaleLabel(float scale) {
        return Math.round(scale * 100f) + "%";
    }

    private static String fontLabel(Context context, int font) {
        switch (font) {
            case PlayerData.SUBTITLE_FONT_SANS_SERIF:
                return context.getString(R.string.mobile_subtitle_font_sans);
            case PlayerData.SUBTITLE_FONT_SERIF:
                return context.getString(R.string.mobile_subtitle_font_serif);
            case PlayerData.SUBTITLE_FONT_MONOSPACE:
                return context.getString(R.string.mobile_subtitle_font_monospace);
            case PlayerData.SUBTITLE_FONT_DEFAULT_BOLD:
            default:
                return context.getString(R.string.mobile_subtitle_font_default);
        }
    }

    private static void changed(@Nullable Runnable onChanged) {
        if (onChanged != null) onChanged.run();
    }
}
