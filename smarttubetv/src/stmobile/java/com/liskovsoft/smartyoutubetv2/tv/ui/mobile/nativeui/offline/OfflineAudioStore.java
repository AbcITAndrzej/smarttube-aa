package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;
import android.os.StatFs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Owns private audio files. The directory is no-backup storage and never requires media permissions. */
final class OfflineAudioStore {
    private static final String DIRECTORY = "offline_audio_v1";
    private static final String PART_SUFFIX = ".part";
    private static final String AUDIO_SUFFIX = ".audio";

    private final File root;

    OfflineAudioStore(Context context) {
        root = new File(context.getApplicationContext().getNoBackupFilesDir(), DIRECTORY);
    }

    File getRoot() { return root; }

    String fileKey(String mediaId) { return OfflineFileKey.fromMediaId(mediaId); }

    File partialFile(String fileKey) { return new File(root, fileKey + PART_SUFFIX); }

    File finalFile(String fileKey) { return new File(root, fileKey + AUDIO_SUFFIX); }

    OutputStream openPartial(String fileKey, boolean append) throws IOException {
        ensureRoot();
        return new BufferedOutputStream(new FileOutputStream(partialFile(fileKey), append));
    }

    InputStream openFinal(String fileKey) throws IOException {
        File file = finalFile(fileKey);
        if (!file.isFile()) throw new IOException("Offline audio file is missing");
        return new BufferedInputStream(new FileInputStream(file));
    }

    boolean finalExists(String fileKey) { return finalFile(fileKey).isFile(); }

    long bytesFor(String fileKey) {
        File finalFile = finalFile(fileKey);
        if (finalFile.isFile()) return finalFile.length();
        File part = partialFile(fileKey);
        return part.isFile() ? part.length() : 0L;
    }

    long commit(String fileKey) throws IOException {
        ensureRoot();
        File part = partialFile(fileKey);
        File target = finalFile(fileKey);
        if (!part.isFile()) throw new IOException("Partial offline audio file is missing");
        if (target.exists() && !target.delete()) throw new IOException("Cannot replace existing offline file");
        if (!part.renameTo(target)) {
            copy(part, target);
            part.delete();
        }
        return target.length();
    }

    long delete(String fileKey) {
        long removed = 0L;
        File part = partialFile(fileKey);
        if (part.isFile()) {
            removed += part.length();
            if (!part.delete()) removed -= part.length();
        }
        File target = finalFile(fileKey);
        if (target.isFile()) {
            long size = target.length();
            if (target.delete()) removed += size;
        }
        return Math.max(0L, removed);
    }

    long availableDeviceBytes() {
        File base = root.exists() ? root : root.getParentFile();
        if (base == null) return 0L;
        try {
            StatFs statFs = new StatFs(base.getAbsolutePath());
            return statFs.getAvailableBytes();
        } catch (Throwable ignored) {
            return Math.max(0L, base.getUsableSpace());
        }
    }

    void clearAllFiles() {
        if (!root.isDirectory()) return;
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child != null && child.isFile()) child.delete();
            }
        }
    }

    private void ensureRoot() throws IOException {
        if (root.isDirectory()) return;
        if (!root.mkdirs() && !root.isDirectory()) throw new IOException("Cannot create offline audio directory");
    }

    private static void copy(File from, File to) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = new BufferedInputStream(new FileInputStream(from));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(to))) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            if (to.exists()) to.delete();
            throw error;
        }
    }
}
