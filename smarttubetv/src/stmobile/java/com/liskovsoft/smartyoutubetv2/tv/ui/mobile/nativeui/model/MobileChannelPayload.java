package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileChannelPayload {
    private final String channelId;
    private final String title;
    private final String description;
    private final String avatarUrl;
    private final String bannerUrl;
    private final String subscriberText;
    private final boolean subscribed;
    private final List<MobileSection> sections;

    public MobileChannelPayload(String channelId, String title, String description,
                                String avatarUrl, String bannerUrl, String subscriberText,
                                boolean subscribed, List<MobileSection> sections) {
        this.channelId = channelId;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.avatarUrl = avatarUrl;
        this.bannerUrl = bannerUrl;
        this.subscriberText = subscriberText == null ? "" : subscriberText;
        this.subscribed = subscribed;
        this.sections = Collections.unmodifiableList(new ArrayList<>(
                sections == null ? Collections.<MobileSection>emptyList() : sections));
    }

    public String getChannelId() { return channelId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public String getSubscriberText() { return subscriberText; }
    public boolean isSubscribed() { return subscribed; }
    public List<MobileSection> getSections() { return sections; }
}
