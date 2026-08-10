package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Converts arbitrary media IDs into safe deterministic filenames without exposing the ID on disk. */
final class OfflineFileKey {
    private OfflineFileKey() { }

    static String fromMediaId(String mediaId) {
        String safe = mediaId == null ? "" : mediaId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) out.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(safe.hashCode());
        }
    }
}
