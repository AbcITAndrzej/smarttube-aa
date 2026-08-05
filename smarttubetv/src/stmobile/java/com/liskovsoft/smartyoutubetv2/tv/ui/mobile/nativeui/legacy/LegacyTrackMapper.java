package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LegacyTrackMapper {
    public List<MobileTrack> map(List<FormatItem> items, MobileTrack.Type type) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<MobileTrack> result = new ArrayList<>();
        for (FormatItem item : items) {
            if (item == null) continue;
            result.add(new MobileTrack(id(item), type, label(item), safe(item.getLanguage()), item.isSelected()));
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
        return item.getType() + ":" + (formatId == null || formatId.isEmpty() ? item.getId() : formatId);
    }

    private static String label(FormatItem item) {
        CharSequence title = item.getTitle();
        String value = title == null ? "" : title.toString();
        if (!value.isEmpty()) return value;
        String language = item.getLanguage();
        return language == null || language.isEmpty() ? "Track " + item.getId() : language;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
