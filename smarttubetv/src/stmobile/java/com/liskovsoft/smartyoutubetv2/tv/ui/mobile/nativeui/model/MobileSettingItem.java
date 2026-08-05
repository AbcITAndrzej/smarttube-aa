package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobileSettingItem {
    public enum Type { HEADER, SWITCH, CHOICE, SLIDER, ACTION, INFO }

    private final String id;
    private final Type type;
    private final String title;
    private final String summary;
    private final String value;
    private final boolean enabled;
    private final List<String> options;

    public MobileSettingItem(String id, Type type, String title, String summary,
                             String value, boolean enabled, List<String> options) {
        this.id = id;
        this.type = type == null ? Type.INFO : type;
        this.title = title == null ? "" : title;
        this.summary = summary == null ? "" : summary;
        this.value = value == null ? "" : value;
        this.enabled = enabled;
        this.options = Collections.unmodifiableList(new ArrayList<>(
                options == null ? Collections.<String>emptyList() : options));
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getValue() { return value; }
    public boolean isEnabled() { return enabled; }
    public List<String> getOptions() { return options; }
    public boolean isChecked() { return Boolean.parseBoolean(value); }
}
