package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio;

import org.json.JSONException;
import org.json.JSONObject;

/** Immutable subset of a Radio Browser station used by the experimental mobile screen. */
public final class RadioStation {
    private final String id;
    private final String name;
    private final String streamUrl;
    private final String faviconUrl;
    private final String country;
    private final String countryCode;
    private final String codec;
    private final String tags;
    private final int bitrate;
    private final int clickCount;
    private final boolean favorite;

    public RadioStation(String id, String name, String streamUrl, String faviconUrl,
                        String country, String countryCode, String codec, String tags,
                        int bitrate, int clickCount,
                        boolean favorite) {
        this.id = clean(id);
        this.name = clean(name);
        this.streamUrl = clean(streamUrl);
        this.faviconUrl = clean(faviconUrl);
        this.country = clean(country);
        this.countryCode = clean(countryCode);
        this.codec = clean(codec);
        this.tags = clean(tags);
        this.bitrate = Math.max(0, bitrate);
        this.clickCount = Math.max(0, clickCount);
        this.favorite = favorite;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getStreamUrl() { return streamUrl; }
    public String getFaviconUrl() { return faviconUrl; }
    public String getCountry() { return country; }
    public String getCountryCode() { return countryCode; }
    public String getCodec() { return codec; }
    public String getTags() { return tags; }
    public int getBitrate() { return bitrate; }
    public int getClickCount() { return clickCount; }
    public boolean isFavorite() { return favorite; }

    public RadioStation withFavorite(boolean value) {
        return new RadioStation(id, name, streamUrl, faviconUrl, country, countryCode, codec, tags,
                bitrate, clickCount, value);
    }

    /** Same logical station metadata with a different stream candidate. */
    public RadioStation withStreamUrl(String value) {
        return new RadioStation(id, name, value, faviconUrl, country, countryCode, codec, tags,
                bitrate, clickCount, favorite);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("stationuuid", id)
                .put("name", name)
                .put("url_resolved", streamUrl)
                .put("favicon", faviconUrl)
                .put("country", country)
                .put("countrycode", countryCode)
                .put("codec", codec)
                .put("tags", tags)
                .put("bitrate", bitrate)
                .put("clickcount", clickCount);
    }

    static RadioStation fromJson(JSONObject value) {
        if (value == null) return null;
        String id = clean(value.optString("stationuuid"));
        String name = clean(value.optString("name"));
        String stream = clean(value.optString("url_resolved"));
        if (stream.isEmpty()) stream = clean(value.optString("url"));
        if (id.isEmpty() || name.isEmpty() || !isSupportedStream(stream)) return null;
        return new RadioStation(id, name, stream, value.optString("favicon"),
                value.optString("country"), value.optString("countrycode"),
                value.optString("codec"), value.optString("tags"),
                value.optInt("bitrate", 0), value.optInt("clickcount", 0), false);
    }

    static boolean isSupportedStream(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(java.util.Locale.US);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
