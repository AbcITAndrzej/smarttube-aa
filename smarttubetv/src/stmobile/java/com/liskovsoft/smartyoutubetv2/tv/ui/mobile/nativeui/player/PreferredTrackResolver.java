package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import java.util.List;
import java.util.Locale;

/** Finds the row to emphasize/scroll to without changing the active ExoPlayer track. */
public final class PreferredTrackResolver {
    private PreferredTrackResolver() { }

    public static int findPreferred(List<MobileTrack> tracks, String preference) {
        return findPreferred(tracks, preference, null);
    }

    public static int findPreferred(List<MobileTrack> tracks, String preference,
                                    String excludedTrackId) {
        if (tracks == null || tracks.isEmpty()) return -1;
        String wanted = normalize(preference);
        if (MobilePlayerPreferences.LANGUAGE_SYSTEM.equals(wanted)) {
            wanted = normalize(Locale.getDefault().getLanguage());
        }
        if (wanted.isEmpty()) return -1;

        for (int i = 0; i < tracks.size(); i++) {
            MobileTrack track = tracks.get(i);
            if (track == null) continue;
            if (excludedTrackId != null && excludedTrackId.equals(track.getId())) continue;
            if (matches(track.getLanguage(), wanted) || matchesLabel(track.getLabel(), wanted)) {
                return i;
            }
        }
        return -1;
    }

    public static MobileTrack preferredTrack(List<MobileTrack> tracks, String preference,
                                             String excludedTrackId) {
        int index = findPreferred(tracks, preference, excludedTrackId);
        return index >= 0 ? tracks.get(index) : null;
    }

    private static boolean matches(String value, String wanted) {
        String language = normalize(value);
        return language.equals(wanted)
                || language.startsWith(wanted + "-")
                || language.startsWith(wanted + "_")
                || language.startsWith(wanted + ".");
    }

    private static boolean matchesLabel(String label, String wanted) {
        String normalized = normalize(label);
        if (normalized.isEmpty()) return false;
        if ("pl".equals(wanted)) return normalized.contains("polski") || normalized.contains("polish");
        if ("en".equals(wanted)) return normalized.contains("english") || normalized.contains("angielski");
        if ("de".equals(wanted)) return normalized.contains("deutsch") || normalized.contains("german");
        if ("es".equals(wanted)) return normalized.contains("español") || normalized.contains("spanish");
        if ("fr".equals(wanted)) return normalized.contains("français") || normalized.contains("french");
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
