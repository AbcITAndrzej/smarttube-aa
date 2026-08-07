package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LegacyTrackMapper {
    public List<MobileTrack> map(List<FormatItem> items, MobileTrack.Type type) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<FormatItem> ordered = new ArrayList<>();
        for (FormatItem item : items) if (item != null) ordered.add(item);
        if (type == MobileTrack.Type.SUBTITLE) {
            // Phone users most often want the device language. Keep Polish originals and
            // Polish auto-translations together at the top, while retaining an explicit Off.
            Collections.sort(ordered, Comparator.comparingInt(LegacyTrackMapper::subtitleRank));
        }
        List<MobileTrack> result = new ArrayList<>();
        for (FormatItem item : ordered) {
            if (item == null) continue;
            result.add(new MobileTrack(id(item), type, label(item, type), safe(item.getLanguage()), item.isSelected()));
        }
        return result;
    }

    public FormatItem find(List<FormatItem> items, String id) {
        if (items == null || id == null) return null;
        for (FormatItem item : items) if (item != null && id(item).equals(id)) return item;
        return null;
    }

    static String id(FormatItem item) {
        String formatId = item.getFormatId();
        String base = formatId == null || formatId.isEmpty()
                ? Integer.toString(item.getId()) : formatId;
        if (item.getType() == FormatItem.TYPE_AUDIO
                || item.getType() == FormatItem.TYPE_SUBTITLE) {
            // Translated captions can reuse format ids. Language/title make a tap resolve to
            // the exact current Exo track instead of a similarly named translation.
            CharSequence title = item.getTitle();
            return item.getType() + ":" + base + ":" + safe(item.getLanguage())
                    + ":" + (title == null ? "" : title.toString());
        }
        return item.getType() + ":" + base;
    }

    private static String label(FormatItem item, MobileTrack.Type type) {
        CharSequence title = item.getTitle();
        String value = title == null ? "" : title.toString();
        if (type == MobileTrack.Type.SUBTITLE) {
            if (item.isDefault()) return "Off";
            if (isPolish(item)) {
                return isAutomatic(item) ? "Polski (automatyczne)"
                        : value.isEmpty() ? "Polski" : value;
            }
        }
        if (!value.isEmpty()) return value;
        if (type == MobileTrack.Type.VIDEO) {
            int height = item.getHeight();
            float fps = item.getFrameRate();
            if (height > 0) {
                return height + "p" + (fps >= 50f ? Math.round(fps) : "");
            }
            return item.isDefault() ? "Auto" : "Video";
        }
        String language = item.getLanguage();
        if (language != null && !language.isEmpty()) return language;
        if (type == MobileTrack.Type.SUBTITLE) return "Off";
        if (type == MobileTrack.Type.AUDIO) return "Auto";
        return "Track " + item.getId();
    }

    private static int subtitleRank(FormatItem item) {
        if (isPolish(item)) return isAutomatic(item) ? 1 : 0;
        if (item.isDefault()) return 2;
        return isAutomatic(item) ? 4 : 3;
    }

    private static boolean isPolish(FormatItem item) {
        String language = normalize(item.getLanguage());
        CharSequence rawTitle = item.getTitle();
        String title = normalize(rawTitle == null ? "" : rawTitle.toString());
        return language.equals("pl") || language.startsWith("pl-")
                || language.startsWith("pl_") || language.contains("polish")
                || language.contains("polski") || title.contains("polish")
                || title.contains("polski");
    }

    private static boolean isAutomatic(FormatItem item) {
        String language = item.getLanguage();
        CharSequence rawTitle = item.getTitle();
        String title = normalize(rawTitle == null ? "" : rawTitle.toString());
        return SubtitleTrack.isAuto(language) || title.contains("automatic")
                || title.contains("automatycz") || title.contains("wygenerowan")
                || title.contains("auto-translat");
    }

    private static String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
