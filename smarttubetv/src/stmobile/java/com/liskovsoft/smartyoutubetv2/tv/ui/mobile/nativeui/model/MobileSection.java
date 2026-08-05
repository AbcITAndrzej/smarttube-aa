package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileSection {
    private final String id;
    private final String title;
    private final List<MobileMediaItem> items;

    public MobileSection(String id, String title, List<MobileMediaItem> items) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.items = Collections.unmodifiableList(new ArrayList<>(
                items == null ? Collections.<MobileMediaItem>emptyList() : items));
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public List<MobileMediaItem> getItems() { return items; }
}
