package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LegacyMediaMapper {
    private final LegacyMediaIndex index;
    private final MobileMetadataEnhancer metadataEnhancer;

    public LegacyMediaMapper(LegacyMediaIndex index) {
        this(index, null);
    }

    public LegacyMediaMapper(LegacyMediaIndex index, MobileMetadataEnhancer metadataEnhancer) {
        this.index = index;
        this.metadataEnhancer = metadataEnhancer;
    }

    public MobileMediaItem map(Video video) {
        return map(video, null);
    }

    public MobileMediaItem map(Video video, MobileMediaItem.Kind forcedKind) {
        if (video == null) throw new IllegalArgumentException("video == null");
        if (metadataEnhancer != null) metadataEnhancer.applyCached(video);
        String id = stableId(video);
        MobileMediaItem.Kind kind = forcedKind == null ? kindOf(video) : forcedKind;
        long duration = Math.max(0, video.getDurationMs());
        long progress = Math.max(0, video.getPositionMs());
        String durationText = video.isLive ? "LIVE" : formatDuration(duration);
        CharSequence secondary = video.getSecondTitleFull();
        // The raw author is already populated by service mappers. Avoid reparsing it via
        // Android TextUtils here; this keeps the mobile mapper deterministic in JVM tests too.
        String subtitle = secondary == null ? safe(video.author) : secondary.toString();
        boolean playable = video.videoId != null && !video.isUnplayable;
        MobileMediaItem item = new MobileMediaItem(id, kind, safe(video.getTitleFull()), subtitle,
                video.getCardImageUrl(), durationText, progress, duration, playable,
                video.getPlaylistId());
        index.put(id, video);
        return item;
    }

    public MobileSection map(MediaGroup source, int position) {
        return map(source, position, null);
    }

    public MobileSection map(MediaGroup source, int position,
                             MobileMediaItem.Kind forcedKind) {
        VideoGroup group = VideoGroup.from(source);
        return map(group, position, forcedKind);
    }

    public MobileSection map(VideoGroup group, int position) {
        return map(group, position, null);
    }

    public MobileSection map(VideoGroup group, int position,
                             MobileMediaItem.Kind forcedKind) {
        if (group == null) return new MobileSection("section:" + position, "", Collections.emptyList());
        List<MobileMediaItem> items = new ArrayList<>();
        List<Video> videos = group.getVideos();
        if (videos != null) for (Video video : videos) if (video != null) {
            items.add(map(video, forcedKind));
        }
        String title = safe(group.getTitle());
        String id = "section:" + group.getId() + ":" + position;
        return new MobileSection(id, title, items);
    }

    public List<MobileSection> mapGroups(List<MediaGroup> groups) {
        if (groups == null || groups.isEmpty()) return Collections.emptyList();
        List<MobileSection> result = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            MediaGroup group = groups.get(i);
            if (group == null) continue;
            MobileSection section = map(group, i);
            if (!section.getItems().isEmpty()) result.add(section);
        }
        return result;
    }

    public List<MobileSection> mapSingle(MediaGroup group, String fallbackTitle) {
        if (group == null) return Collections.emptyList();
        MobileSection section = map(group, 0);
        if (section.getTitle().isEmpty() && fallbackTitle != null) {
            section = new MobileSection(section.getId(), fallbackTitle, section.getItems());
        }
        return Collections.singletonList(section);
    }

    static String stableId(Video video) {
        if (video.videoId != null && !video.videoId.isEmpty()) return video.videoId;
        String playlist = video.getPlaylistId();
        if (playlist != null && !playlist.isEmpty()) return "playlist:" + playlist;
        if (video.channelId != null && !video.channelId.isEmpty()) return "channel:" + video.channelId;
        return "legacy:" + video.getId();
    }

    static MobileMediaItem.Kind kindOf(Video video) {
        // YouTube playlist cards also carry their owner's channelId. Classify the
        // more specific playlist shape first or every playlist becomes a channel.
        if (video.videoId == null && video.hasPlaylist()) return MobileMediaItem.Kind.PLAYLIST;
        if (video.isChannel()) return MobileMediaItem.Kind.CHANNEL;
        if (video.isLive) return MobileMediaItem.Kind.LIVE;
        if (video.isShorts) return MobileMediaItem.Kind.SHORT;
        return MobileMediaItem.Kind.VIDEO;
    }

    static String formatDuration(long durationMs) {
        if (durationMs <= 0) return "";
        long total = durationMs / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    static String safe(Object value) { return value == null ? "" : String.valueOf(value); }
}
