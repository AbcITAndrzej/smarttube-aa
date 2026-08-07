package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

public final class MobileTrack {
    public enum Type { VIDEO, AUDIO, SUBTITLE }

    private final String id;
    private final Type type;
    private final String label;
    private final String language;
    private final boolean selected;

    public MobileTrack(String id, Type type, String label, String language, boolean selected) {
        this.id = id;
        this.type = type;
        this.label = label == null ? "" : label;
        this.language = language == null ? "" : language;
        this.selected = selected;
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public String getLabel() { return label; }
    public String getLanguage() { return language; }
    public boolean isSelected() { return selected; }
}
