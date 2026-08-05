package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps complete legacy objects behind the lightweight IDs exposed to mobile UI. */
public final class LegacyMediaIndex {
    private static final int MAX_ITEMS = 2500;
    private final ConcurrentHashMap<String, Video> byId = new ConcurrentHashMap<>();

    public void put(String mobileId, Video video) {
        if (mobileId == null || video == null) return;
        if (byId.size() > MAX_ITEMS) byId.clear();
        byId.put(mobileId, video);
        if (video.videoId != null) byId.put(video.videoId, video);
        if (video.channelId != null) byId.put("channel:" + video.channelId, video);
        // A playable track also carries the ID of its parent playlist. Never let such
        // a track replace the real playlist card, otherwise reopening the playlist
        // loads watch-next suggestions for that track instead of the playlist.
        if (video.videoId == null && video.getPlaylistId() != null) {
            byId.put("playlist:" + video.getPlaylistId(), video);
        }
    }

    public Video get(String id) {
        if (id == null) return null;
        Video direct = byId.get(id);
        if (direct != null) return direct;
        direct = byId.get("channel:" + id);
        return direct != null ? direct : byId.get("playlist:" + id);
    }

    public void clear() { byId.clear(); }
}
