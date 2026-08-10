package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Locale-backed language list used by the player settings screen. */
public final class PlayerLanguageCatalog {
    public static final class Entry {
        private final String code;
        private final String label;

        Entry(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }
    }

    private PlayerLanguageCatalog() { }

    public static List<Entry> build(Context context) {
        Map<String, String> unique = new LinkedHashMap<>();
        Locale displayLocale = Locale.getDefault();
        for (Locale locale : Locale.getAvailableLocales()) {
            String code = locale.getLanguage();
            if (code == null || code.trim().isEmpty() || unique.containsKey(code)) continue;
            String label = locale.getDisplayLanguage(displayLocale);
            if (label == null || label.trim().isEmpty()) continue;
            unique.put(code, capitalize(label));
        }

        List<Entry> result = new ArrayList<>();
        result.add(new Entry(MobilePlayerPreferences.LANGUAGE_SYSTEM,
                context.getString(R.string.mobile_player_language_system)));
        result.add(new Entry(MobilePlayerPreferences.LANGUAGE_NONE,
                context.getString(R.string.mobile_player_language_none)));

        List<Entry> languages = new ArrayList<>();
        for (Map.Entry<String, String> item : unique.entrySet()) {
            languages.add(new Entry(item.getKey(), item.getValue()));
        }
        Collator collator = Collator.getInstance(displayLocale);
        Collections.sort(languages, (left, right) -> collator.compare(left.getLabel(), right.getLabel()));
        result.addAll(languages);
        return result;
    }

    public static String labelFor(Context context, String code) {
        String normalized = code == null ? MobilePlayerPreferences.LANGUAGE_SYSTEM : code.trim();
        for (Entry entry : build(context)) {
            if (entry.getCode().equalsIgnoreCase(normalized)) return entry.getLabel();
        }
        return normalized.isEmpty() ? context.getString(R.string.mobile_player_language_none) : normalized;
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }
}
