package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Durable queue/history for Stage 7. It stores metadata only; signed media URLs never enter SQLite. */
final class OfflineListenSaveDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "smarttube_mobile_offline_listen.db";
    private static final int DB_VERSION = 1;
    private static final String T = "offline_listen_save";

    OfflineListenSaveDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "media_id TEXT PRIMARY KEY NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "author TEXT NOT NULL DEFAULT '',"
                + "thumbnail_url TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "state TEXT NOT NULL,"
                + "bytes_downloaded INTEGER NOT NULL DEFAULT 0,"
                + "bytes_total INTEGER NOT NULL DEFAULT 0,"
                + "failure_reason TEXT NOT NULL DEFAULT '',"
                + "attempts INTEGER NOT NULL DEFAULT 0,"
                + "created_at_ms INTEGER NOT NULL DEFAULT 0,"
                + "updated_at_ms INTEGER NOT NULL DEFAULT 0,"
                + "last_listened_at_ms INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX offline_listen_state_idx ON " + T + "(state,updated_at_ms)");
        db.execSQL("CREATE INDEX offline_listen_recent_idx ON " + T + "(last_listened_at_ms DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migrations only. Never drop offline history/audio metadata.
    }

    synchronized OfflineListenSaveEntry upsertRequested(OfflineMediaDescriptor descriptor,
                                                         OfflineListenSaveState state,
                                                         long bytesDownloaded, long bytesTotal,
                                                         long nowMs) {
        OfflineListenSaveEntry current = find(descriptor.getMediaId());
        ContentValues v = new ContentValues();
        v.put("media_id", descriptor.getMediaId());
        v.put("title", descriptor.getTitle());
        v.put("author", descriptor.getAuthor());
        v.put("thumbnail_url", descriptor.getThumbnailUrl());
        v.put("duration_ms", descriptor.getDurationMs());
        v.put("state", state.name());
        v.put("bytes_downloaded", Math.max(0L, bytesDownloaded));
        v.put("bytes_total", Math.max(0L, bytesTotal));
        v.put("failure_reason", "");
        v.put("attempts", current == null ? 0 : current.getAttempts());
        v.put("created_at_ms", current == null ? nowMs : current.getCreatedAtMs());
        v.put("updated_at_ms", nowMs);
        v.put("last_listened_at_ms", nowMs);
        getWritableDatabase().insertWithOnConflict(T, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return find(descriptor.getMediaId());
    }

    synchronized void setState(String mediaId, OfflineListenSaveState state,
                               long bytesDownloaded, long bytesTotal, String reason,
                               int attempts, long nowMs) {
        ContentValues v = new ContentValues();
        v.put("state", state.name());
        v.put("bytes_downloaded", Math.max(0L, bytesDownloaded));
        v.put("bytes_total", Math.max(0L, bytesTotal));
        v.put("failure_reason", compact(reason));
        v.put("attempts", Math.max(0, attempts));
        v.put("updated_at_ms", nowMs);
        getWritableDatabase().update(T, v, "media_id=?", new String[]{mediaId});
    }

    synchronized void touchListened(String mediaId, long nowMs) {
        ContentValues v = new ContentValues();
        v.put("last_listened_at_ms", nowMs);
        v.put("updated_at_ms", nowMs);
        getWritableDatabase().update(T, v, "media_id=?", new String[]{mediaId});
    }

    synchronized void updateProgress(String mediaId, long downloaded, long total, long nowMs) {
        ContentValues v = new ContentValues();
        v.put("bytes_downloaded", Math.max(0L, downloaded));
        v.put("bytes_total", Math.max(0L, total));
        v.put("updated_at_ms", nowMs);
        getWritableDatabase().update(T, v, "media_id=?", new String[]{mediaId});
    }

    synchronized OfflineListenSaveEntry find(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return null;
        try (Cursor c = getReadableDatabase().query(T, null, "media_id=?",
                new String[]{mediaId}, null, null, null, "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    synchronized OfflineListenSaveEntry nextPending() {
        try (Cursor c = getReadableDatabase().query(T, null, "state=?",
                new String[]{OfflineListenSaveState.PENDING.name()}, null, null,
                "last_listened_at_ms ASC", "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    synchronized List<OfflineListenSaveEntry> listRecent(int limit) {
        String lim = limit > 0 ? Integer.toString(limit) : null;
        try (Cursor c = getReadableDatabase().query(T, null, null, null, null, null,
                "last_listened_at_ms DESC,updated_at_ms DESC", lim)) {
            ArrayList<OfflineListenSaveEntry> out = new ArrayList<>();
            while (c.moveToNext()) out.add(read(c));
            return out;
        }
    }

    synchronized List<OfflineListenSaveEntry> listAvailable() {
        try (Cursor c = getReadableDatabase().query(T, null, "state=?",
                new String[]{OfflineListenSaveState.AVAILABLE.name()}, null, null,
                "last_listened_at_ms DESC")) {
            ArrayList<OfflineListenSaveEntry> out = new ArrayList<>();
            while (c.moveToNext()) out.add(read(c));
            return out;
        }
    }

    synchronized int countByState(OfflineListenSaveState state) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + T + " WHERE state=?", new String[]{state.name()})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    synchronized void recoverInterrupted(long nowMs) {
        ContentValues v = new ContentValues();
        v.put("state", OfflineListenSaveState.PENDING.name());
        v.put("failure_reason", "interrupted; queued again");
        v.put("updated_at_ms", nowMs);
        getWritableDatabase().update(T, v, "state=?",
                new String[]{OfflineListenSaveState.DOWNLOADING.name()});
    }

    synchronized void delete(String mediaId) {
        getWritableDatabase().delete(T, "media_id=?", new String[]{mediaId});
    }

    synchronized void clear() { getWritableDatabase().delete(T, null, null); }

    private static OfflineListenSaveEntry read(Cursor c) {
        return new OfflineListenSaveEntry(s(c,"media_id"), s(c,"title"), s(c,"author"),
                s(c,"thumbnail_url"), l(c,"duration_ms"),
                OfflineListenSaveState.fromStorage(s(c,"state")), l(c,"bytes_downloaded"),
                l(c,"bytes_total"), s(c,"failure_reason"), i(c,"attempts"),
                l(c,"created_at_ms"), l(c,"updated_at_ms"), l(c,"last_listened_at_ms"));
    }

    private static String s(Cursor c, String name) { int x=c.getColumnIndex(name); return x<0||c.isNull(x)?"":c.getString(x); }
    private static long l(Cursor c, String name) { int x=c.getColumnIndex(name); return x<0||c.isNull(x)?0L:c.getLong(x); }
    private static int i(Cursor c, String name) { int x=c.getColumnIndex(name); return x<0||c.isNull(x)?0:c.getInt(x); }
    private static String compact(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }
}
