package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Picks one direct, finite audio stream suitable for an explicit offline file. */
final class OfflineAudioFormatSelector {
    private OfflineAudioFormatSelector() {}

    static MediaFormat select(MediaItemFormatInfo info, String preferredLanguage) {
        if (info == null) return null;
        ArrayList<MediaFormat> candidates = new ArrayList<>();
        addCandidates(candidates, info.getAdaptiveFormats());
        addCandidates(candidates, info.getUrlFormats());
        if (candidates.isEmpty()) return null;

        String wanted = normalizeLanguage(preferredLanguage);
        MediaFormat best = null;
        long bestScore = Long.MIN_VALUE;
        for (MediaFormat format : candidates) {
            long score = 0L;
            String language = normalizeLanguage(format.getLanguage());
            if (!wanted.isEmpty() && languageMatches(language, wanted)) score += 10_000_000_000L;
            if (!format.isDrc()) score += 1_000_000_000L;
            String mime = safe(format.getMimeType()).toLowerCase(Locale.ROOT);
            // MP4/AAC is the broadest Android offline-audio compatibility fallback.
            if (mime.startsWith("audio/mp4")) score += 100_000_000L;
            else if (mime.startsWith("audio/webm")) score += 50_000_000L;
            score += Math.max(0L, parseLong(format.getBitrate()));
            if (score > bestScore) {
                bestScore = score;
                best = format;
            }
        }
        return best;
    }

    static long expectedBytes(MediaFormat format) {
        return format == null ? 0L : Math.max(0L, parseLong(format.getClen()));
    }

    static String codec(MediaFormat format) {
        String mime = format == null ? "" : safe(format.getMimeType());
        int codecs = mime.indexOf("codecs=");
        if (codecs < 0) return "";
        String value = mime.substring(codecs + 7).trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static void addCandidates(List<MediaFormat> out, List<MediaFormat> formats) {
        if (formats == null) return;
        for (MediaFormat format : formats) {
            if (format == null || format.isOtf()) continue;
            String url = safe(format.getUrl());
            String mime = safe(format.getMimeType()).toLowerCase(Locale.ROOT);
            if (url.isEmpty() || !mime.startsWith("audio/")) continue;
            out.add(format);
        }
    }

    private static boolean languageMatches(String language, String wanted) {
        if (wanted.isEmpty()) return false;
        return language.equals(wanted) || language.startsWith(wanted + "-")
                || wanted.startsWith(language + "-");
    }

    private static String normalizeLanguage(String value) {
        if (value == null) return "";
        String out = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (out.equals("system")) return Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT);
        return out;
    }

    private static long parseLong(String value) {
        if (value == null) return 0L;
        try { return Long.parseLong(value.trim()); } catch (NumberFormatException ignored) { return 0L; }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
