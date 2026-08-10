package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileSearchPayload {
    private final String query;
    private final List<MobileSection> sections;
    private final boolean hasMore;

    public MobileSearchPayload(String query, List<MobileSection> sections) {
        this(query, sections, false);
    }

    public MobileSearchPayload(String query, List<MobileSection> sections, boolean hasMore) {
        this.query = query == null ? "" : query;
        this.sections = Collections.unmodifiableList(new ArrayList<>(
                sections == null ? Collections.<MobileSection>emptyList() : sections));
        this.hasMore = hasMore;
    }

    public String getQuery() { return query; }
    public List<MobileSection> getSections() { return sections; }
    public boolean hasMore() { return hasMore; }
}
