package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileBrowsePayload {
    private final String title;
    private final List<MobileSection> sections;
    private final boolean hasMore;

    public MobileBrowsePayload(String title, List<MobileSection> sections) {
        this(title, sections, false);
    }

    public MobileBrowsePayload(String title, List<MobileSection> sections, boolean hasMore) {
        this.title = title == null ? "" : title;
        this.sections = Collections.unmodifiableList(new ArrayList<>(
                sections == null ? Collections.<MobileSection>emptyList() : sections));
        this.hasMore = hasMore;
    }

    public String getTitle() { return title; }
    public List<MobileSection> getSections() { return sections; }
    public boolean hasMore() { return hasMore; }
}
