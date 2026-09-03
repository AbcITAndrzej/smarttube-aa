package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LegacyTrackMapper {
    public List<MobileTrack> map(List<FormatItem> items, MobileTrack.Type type) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        List<FormatItem> ordered = type == MobileTrack.Type.AUDIO
                ? logicalAudioFormats(items) : copyNonNull(items);
        if (type == MobileTrack.Type.SUBTITLE) {
            // Phone users most often want the device language. Keep Polish originals and
            // Polish auto-translations together at the top, while retaining an explicit Off.
            Collections.sort(ordered, Comparator.comparingInt(LegacyTrackMapper::subtitleRank));
        }
        List<MobileTrack> result = new ArrayList<>();
        for (FormatItem item : ordered) {
            if (item == null) continue;
            result.add(new MobileTrack(id(item), type, label(item, type), safe(item.getLanguage()),
                    item.isSelected(), type == MobileTrack.Type.VIDEO ? item.getWidth() : 0,
                    type == MobileTrack.Type.VIDEO ? item.getHeight() : 0));
        }
        return result;
    }

    /**
     * ExoPlayer exposes every bitrate/codec/DRC representation as a separate FormatItem.
     * The mobile picker must expose logical YouTube audio tracks instead: one row per
     * audioTrack.id (or language/default bucket when no audioTrack metadata exists).
     */
    public List<FormatItem> logicalAudioFormats(List<FormatItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();

        FormatItem auto = null;
        Map<String, FormatItem> logical = new LinkedHashMap<>();
        for (FormatItem item : items) {
            if (item == null) continue;
            if (item.isDefault()) {
                if (auto == null || item.isSelected()) auto = item;
                continue;
            }

            String key = audioLogicalKey(item);
            FormatItem current = logical.get(key);
            if (current == null || preferAudioRepresentative(item, current)) {
                logical.put(key, item);
            }
        }

        List<FormatItem> result = new ArrayList<>();
        if (auto != null) result.add(auto);
        result.addAll(logical.values());
        return result;
    }

    private static List<FormatItem> copyNonNull(List<FormatItem> items) {
        List<FormatItem> result = new ArrayList<>();
        for (FormatItem item : items) if (item != null) result.add(item);
        return result;
    }

    private static String audioLogicalKey(FormatItem item) {
        String language = normalize(item.getLanguage());
        if (!language.isEmpty()) return language;

        Format format = formatOf(item);
        String label = format != null ? normalize(format.label) : "";
        return !label.isEmpty() ? "label:" + label : "default-audio";
    }

    private static boolean preferAudioRepresentative(FormatItem candidate, FormatItem current) {
        if (candidate.isSelected() != current.isSelected()) return candidate.isSelected();

        Format candidateFormat = formatOf(candidate);
        Format currentFormat = formatOf(current);
        boolean candidateDrc = candidateFormat != null && candidateFormat.isDrc;
        boolean currentDrc = currentFormat != null && currentFormat.isDrc;
        if (candidateDrc != currentDrc) return !candidateDrc;

        int candidateBitrate = candidateFormat != null ? candidateFormat.bitrate : -1;
        int currentBitrate = currentFormat != null ? currentFormat.bitrate : -1;
        if (candidateBitrate != currentBitrate) return candidateBitrate > currentBitrate;

        // Stable tie-breaker: mp4a is universally supported on Android/AA.
        boolean candidateMp4a = candidateFormat != null
                && candidateFormat.codecs != null && candidateFormat.codecs.contains("mp4a");
        boolean currentMp4a = currentFormat != null
                && currentFormat.codecs != null && currentFormat.codecs.contains("mp4a");
        return candidateMp4a && !currentMp4a;
    }

    private static Format formatOf(FormatItem item) {
        MediaTrack track = item != null ? item.getTrack() : null;
        return track != null ? track.format : null;
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
        if (type == MobileTrack.Type.AUDIO) {
            if (item.isDefault()) return "Auto";

            Format format = formatOf(item);
            if (format != null && format.label != null && !format.label.trim().isEmpty()) {
                return format.label.trim();
            }

            String displayLanguage = displayLanguage(item.getLanguage());
            return !displayLanguage.isEmpty() ? displayLanguage : "Original";
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
        return "Track " + item.getId();
    }

    private static String displayLanguage(String rawLanguage) {
        String raw = safe(rawLanguage).trim();
        if (raw.isEmpty()) return "";

        // audioTrack.id examples: pl.4, en-US.10. Strip only the track-id
        // suffix; keep the BCP-47 language/region portion for Locale.
        int dot = raw.indexOf('.');
        String tag = dot > 0 ? raw.substring(0, dot) : raw;
        int space = tag.indexOf(' ');
        if (space > 0) tag = tag.substring(0, space);
        tag = tag.replace('_', '-');

        Locale locale = Locale.forLanguageTag(tag);
        String display = locale.getDisplayLanguage(Locale.getDefault());
        return display == null || display.trim().isEmpty() ? tag : display;
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
