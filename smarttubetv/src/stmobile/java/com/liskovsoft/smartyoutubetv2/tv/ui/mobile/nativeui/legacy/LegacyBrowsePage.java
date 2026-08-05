package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import java.util.Locale;

/** Canonical mobile page identifiers and their user-facing fallback titles. */
public enum LegacyBrowsePage {
    HOME("home", "Home", Source.ROWS),
    TRENDING("trending", "Trending", Source.ROWS),
    SHORTS("shorts", "Shorts", Source.GRID),
    SUBSCRIPTIONS("subscriptions", "Subscriptions", Source.GRID),
    HISTORY("history", "History", Source.GRID),
    CHANNELS("channels", "Channels", Source.GRID),
    LIVE("live", "Live", Source.ROWS),
    MUSIC("music", "Music", Source.ROWS),
    YTMUSIC_HOME("ytmusic_home", "YouTube Music", Source.ROWS),
    YTMUSIC_QUICK_PICKS("ytmusic_quick_picks", "Szybkie wybieranie", Source.ROWS),
    YTMUSIC_LIKED("ytmusic_liked", "Muzyka, którą lubisz", Source.GRID),
    GAMING("gaming", "Gaming", Source.ROWS),
    NEWS("news", "News", Source.ROWS),
    SPORTS("sports", "Sports", Source.ROWS),
    KIDS("kids", "Kids", Source.ROWS),
    PLAYLISTS("playlists", "Playlists", Source.GRID),
    MY_VIDEOS("my_videos", "My videos", Source.GRID),
    NOTIFICATIONS("notifications", "Notifications", Source.GRID);

    public enum Source { ROWS, GRID }
    private final String id;
    private final String title;
    private final Source source;

    LegacyBrowsePage(String id, String title, Source source) {
        this.id = id;
        this.title = title;
        this.source = source;
    }

    public String id() { return id; }
    public String title() { return title; }
    public Source source() { return source; }

    public static LegacyBrowsePage from(String raw) {
        String value = raw == null ? "home" : raw.trim().toLowerCase(Locale.US)
                .replace('-', '_').replace(' ', '_');
        if (value.isEmpty()) value = "home";
        if ("start".equals(value) || "main".equals(value)) value = "home";
        if ("subs".equals(value)) value = "subscriptions";
        if ("subscribed_channels".equals(value) || "channel_uploads".equals(value)) value = "channels";
        if ("kids_home".equals(value)) value = "kids";
        if ("user_playlists".equals(value)) value = "playlists";
        if ("myvideos".equals(value)) value = "my_videos";
        if ("youtube_music".equals(value) || "yt_music".equals(value)) value = "ytmusic_home";
        if ("quick_picks".equals(value) || "szybkie_wybieranie".equals(value)) value = "ytmusic_quick_picks";
        if ("liked_music".equals(value) || "vllm".equals(value) || "lm".equals(value)) value = "ytmusic_liked";
        for (LegacyBrowsePage page : values()) {
            if (page.id.equals(value)) return page;
        }
        return HOME;
    }
}
