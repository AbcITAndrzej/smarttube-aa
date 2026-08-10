package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class MobileTrack implements Parcelable {
    public enum Type { VIDEO, AUDIO, SUBTITLE }

    private final String id;
    private final Type type;
    private final String label;
    private final String language;
    private final boolean selected;
    private final int width;
    private final int height;

    public MobileTrack(String id, Type type, String label, String language, boolean selected) {
        this(id, type, label, language, selected, 0, 0);
    }

    public MobileTrack(String id, Type type, String label, String language, boolean selected,
                       int width, int height) {
        this.id = id;
        this.type = type;
        this.label = label == null ? "" : label;
        this.language = language == null ? "" : language;
        this.selected = selected;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public String getLabel() { return label; }
    public String getLanguage() { return language; }
    public boolean isSelected() { return selected; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public float getAspectRatio() { return width > 0 && height > 0 ? (float) width / height : 0f; }

    private MobileTrack(Parcel in) {
        id = in.readString();
        int typeOrdinal = in.readInt();
        type = typeOrdinal >= 0 && typeOrdinal < Type.values().length
                ? Type.values()[typeOrdinal] : Type.VIDEO;
        label = in.readString();
        language = in.readString();
        selected = in.readByte() != 0;
        width = in.readInt();
        height = in.readInt();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeInt(type.ordinal());
        dest.writeString(label);
        dest.writeString(language);
        dest.writeByte((byte) (selected ? 1 : 0));
        dest.writeInt(width);
        dest.writeInt(height);
    }

    public static final Creator<MobileTrack> CREATOR = new Creator<MobileTrack>() {
        @Override public MobileTrack createFromParcel(Parcel in) { return new MobileTrack(in); }
        @Override public MobileTrack[] newArray(int size) { return new MobileTrack[size]; }
    };
}
