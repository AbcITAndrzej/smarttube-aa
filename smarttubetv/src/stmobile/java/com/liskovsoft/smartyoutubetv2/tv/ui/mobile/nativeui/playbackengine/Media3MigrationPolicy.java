package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.playbackengine;

/** Pure decision helper kept free of Android classes so rollout rules are unit-testable. */
public final class Media3MigrationPolicy {
    public enum SourceKind {
        VOD,
        RADIO,
        OFFLINE
    }

    private Media3MigrationPolicy() { }

    public static boolean shouldUseMedia3(SourceKind source,
                                          boolean masterEnabled,
                                          boolean radioEnabled,
                                          boolean offlineEnabled) {
        if (!masterEnabled || source == null) return false;
        if (source == SourceKind.RADIO) return radioEnabled;
        if (source == SourceKind.OFFLINE) return offlineEnabled;
        // Stage 11 deliberately keeps YouTube VOD/Shorts on SmartTube's mature legacy controller.
        return false;
    }
}
