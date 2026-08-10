package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Separate durable queue database for Stage 8 playlist downloads. */
final class OfflinePlaylistDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "smarttube_mobile_offline_playlists.db";
    private static final int DB_VERSION = 1;
    private static final String P = "offline_playlists";
    private static final String I = "offline_playlist_items";

    OfflinePlaylistDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + P + " ("
                + "playlist_id TEXT PRIMARY KEY NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "thumbnail_url TEXT NOT NULL DEFAULT '',"
                + "state TEXT NOT NULL,"
                + "total_count INTEGER NOT NULL DEFAULT 0,"
                + "completed_count INTEGER NOT NULL DEFAULT 0,"
                + "failed_count INTEGER NOT NULL DEFAULT 0,"
                + "bytes_downloaded INTEGER NOT NULL DEFAULT 0,"
                + "bytes_total INTEGER NOT NULL DEFAULT 0,"
                + "failure_reason TEXT NOT NULL DEFAULT '',"
                + "created_at_ms INTEGER NOT NULL DEFAULT 0,"
                + "updated_at_ms INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX offline_playlist_state_idx ON " + P + "(state,updated_at_ms)");
        db.execSQL("CREATE TABLE " + I + " ("
                + "playlist_id TEXT NOT NULL,"
                + "position INTEGER NOT NULL,"
                + "media_id TEXT NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "author TEXT NOT NULL DEFAULT '',"
                + "thumbnail_url TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "state TEXT NOT NULL,"
                + "bytes_downloaded INTEGER NOT NULL DEFAULT 0,"
                + "bytes_total INTEGER NOT NULL DEFAULT 0,"
                + "failure_reason TEXT NOT NULL DEFAULT '',"
                + "attempts INTEGER NOT NULL DEFAULT 0,"
                + "PRIMARY KEY(playlist_id,position)"
                + ")");
        db.execSQL("CREATE INDEX offline_playlist_item_media_idx ON " + I + "(media_id)");
        db.execSQL("CREATE INDEX offline_playlist_item_state_idx ON " + I + "(playlist_id,state,position)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migrations only in future versions. Never drop downloaded playlist metadata.
    }

    synchronized void replacePlaylist(String playlistId, String title, String thumbnailUrl,
                                      List<OfflinePlaylistEntry> entries, long nowMs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            OfflinePlaylistRecord existing = findPlaylist(playlistId);
            ContentValues p = new ContentValues();
            p.put("playlist_id", playlistId);
            p.put("title", safe(title));
            p.put("thumbnail_url", safe(thumbnailUrl));
            p.put("state", OfflinePlaylistState.QUEUED.name());
            p.put("total_count", entries == null ? 0 : entries.size());
            p.put("completed_count", 0);
            p.put("failed_count", 0);
            p.put("bytes_downloaded", 0L);
            p.put("bytes_total", 0L);
            p.put("failure_reason", "");
            p.put("created_at_ms", existing == null ? nowMs : existing.getCreatedAtMs());
            p.put("updated_at_ms", nowMs);
            db.insertWithOnConflict(P, null, p, SQLiteDatabase.CONFLICT_REPLACE);
            db.delete(I, "playlist_id=?", new String[]{playlistId});
            if (entries != null) {
                for (OfflinePlaylistEntry entry : entries) {
                    ContentValues item = new ContentValues();
                    item.put("playlist_id", playlistId);
                    item.put("position", entry.getPosition());
                    item.put("media_id", entry.getMediaId());
                    item.put("title", entry.getTitle());
                    item.put("author", entry.getAuthor());
                    item.put("thumbnail_url", entry.getThumbnailUrl());
                    item.put("duration_ms", entry.getDurationMs());
                    item.put("state", entry.getState().name());
                    item.put("bytes_downloaded", entry.getBytesDownloaded());
                    item.put("bytes_total", entry.getBytesTotal());
                    item.put("failure_reason", entry.getFailureReason());
                    item.put("attempts", entry.getAttempts());
                    db.insertWithOnConflict(I, null, item, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        refreshAggregates(playlistId, nowMs);
    }

    synchronized OfflinePlaylistRecord findPlaylist(String playlistId) {
        if (playlistId == null || playlistId.isEmpty()) return null;
        try (Cursor c = getReadableDatabase().query(P, null, "playlist_id=?",
                new String[]{playlistId}, null, null, null, "1")) {
            return c.moveToFirst() ? readPlaylist(c) : null;
        }
    }

    synchronized List<OfflinePlaylistRecord> listPlaylists() {
        try (Cursor c = getReadableDatabase().query(P, null, null, null, null, null,
                "updated_at_ms DESC,title COLLATE NOCASE ASC")) {
            ArrayList<OfflinePlaylistRecord> out = new ArrayList<>();
            while (c.moveToNext()) out.add(readPlaylist(c));
            return out;
        }
    }

    synchronized List<OfflinePlaylistRecord> listRunnablePlaylists() {
        String selection = "state=? OR state=?";
        String[] args = {OfflinePlaylistState.QUEUED.name(), OfflinePlaylistState.DOWNLOADING.name()};
        try (Cursor c = getReadableDatabase().query(P, null, selection, args, null, null,
                "updated_at_ms ASC")) {
            ArrayList<OfflinePlaylistRecord> out = new ArrayList<>();
            while (c.moveToNext()) out.add(readPlaylist(c));
            return out;
        }
    }

    synchronized List<OfflinePlaylistEntry> listEntries(String playlistId) {
        try (Cursor c = getReadableDatabase().query(I, null, "playlist_id=?",
                new String[]{playlistId}, null, null, "position ASC")) {
            ArrayList<OfflinePlaylistEntry> out = new ArrayList<>();
            while (c.moveToNext()) out.add(readEntry(c));
            return out;
        }
    }

    synchronized OfflinePlaylistRecord nextRunnablePlaylist() {
        String selection = "state=? OR state=?";
        String[] args = {OfflinePlaylistState.QUEUED.name(), OfflinePlaylistState.DOWNLOADING.name()};
        try (Cursor c = getReadableDatabase().query(P, null, selection, args, null, null,
                "updated_at_ms ASC", "1")) {
            return c.moveToFirst() ? readPlaylist(c) : null;
        }
    }

    synchronized OfflinePlaylistEntry nextPendingEntry(String playlistId) {
        try (Cursor c = getReadableDatabase().query(I, null,
                "playlist_id=? AND state=?", new String[]{playlistId, OfflinePlaylistItemState.PENDING.name()},
                null, null, "position ASC", "1")) {
            return c.moveToFirst() ? readEntry(c) : null;
        }
    }

    synchronized void setPlaylistState(String playlistId, OfflinePlaylistState state,
                                       String reason, long nowMs) {
        ContentValues v = new ContentValues();
        v.put("state", state.name());
        v.put("failure_reason", compact(reason));
        v.put("updated_at_ms", nowMs);
        getWritableDatabase().update(P, v, "playlist_id=?", new String[]{playlistId});
    }

    synchronized void setEntryState(String playlistId, int position, OfflinePlaylistItemState state,
                                    long bytesDownloaded, long bytesTotal, String reason,
                                    int attempts, long nowMs) {
        ContentValues v = new ContentValues();
        v.put("state", state.name());
        v.put("bytes_downloaded", Math.max(0L, bytesDownloaded));
        v.put("bytes_total", Math.max(0L, bytesTotal));
        v.put("failure_reason", compact(reason));
        v.put("attempts", Math.max(0, attempts));
        getWritableDatabase().update(I, v, "playlist_id=? AND position=?",
                new String[]{playlistId, Integer.toString(position)});
        refreshAggregates(playlistId, nowMs);
    }

    synchronized void updateEntryProgress(String playlistId, int position,
                                          long bytesDownloaded, long bytesTotal, long nowMs) {
        ContentValues v = new ContentValues();
        v.put("bytes_downloaded", Math.max(0L, bytesDownloaded));
        v.put("bytes_total", Math.max(0L, bytesTotal));
        getWritableDatabase().update(I, v, "playlist_id=? AND position=?",
                new String[]{playlistId, Integer.toString(position)});
        refreshAggregates(playlistId, nowMs);
    }

    synchronized void resetFailedToPending(String playlistId, long nowMs) {
        ContentValues v = new ContentValues();
        v.put("state", OfflinePlaylistItemState.PENDING.name());
        v.put("failure_reason", "");
        v.put("attempts", 0);
        getWritableDatabase().update(I, v, "playlist_id=? AND state=?",
                new String[]{playlistId, OfflinePlaylistItemState.FAILED.name()});
        setPlaylistState(playlistId, OfflinePlaylistState.QUEUED, "", nowMs);
        refreshAggregates(playlistId, nowMs);
    }

    synchronized void recoverInterrupted(long nowMs) {
        ContentValues item = new ContentValues();
        item.put("state", OfflinePlaylistItemState.PENDING.name());
        item.put("failure_reason", "interrupted; queued again");
        getWritableDatabase().update(I, item, "state=?",
                new String[]{OfflinePlaylistItemState.DOWNLOADING.name()});
        ContentValues playlist = new ContentValues();
        playlist.put("state", OfflinePlaylistState.QUEUED.name());
        playlist.put("updated_at_ms", nowMs);
        getWritableDatabase().update(P, playlist, "state=?",
                new String[]{OfflinePlaylistState.DOWNLOADING.name()});
        for (OfflinePlaylistRecord record : listPlaylists()) refreshAggregates(record.getPlaylistId(), nowMs);
    }

    synchronized int countReferences(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return 0;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + I + " WHERE media_id=?", new String[]{mediaId})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    synchronized int countOtherReferences(String mediaId, String excludingPlaylistId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + I + " WHERE media_id=? AND playlist_id<>?",
                new String[]{mediaId, excludingPlaylistId == null ? "" : excludingPlaylistId})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    synchronized void deletePlaylist(String playlistId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(I, "playlist_id=?", new String[]{playlistId});
            db.delete(P, "playlist_id=?", new String[]{playlistId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    synchronized void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(I, null, null);
            db.delete(P, null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    synchronized void refreshAggregates(String playlistId, long nowMs) {
        int total = 0, completed = 0, failed = 0, pending = 0, downloading = 0;
        long bytes = 0L, bytesTotal = 0L;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT state,COUNT(*),SUM(bytes_downloaded),SUM(bytes_total) FROM " + I
                        + " WHERE playlist_id=? GROUP BY state", new String[]{playlistId})) {
            while (c.moveToNext()) {
                OfflinePlaylistItemState state = OfflinePlaylistItemState.fromStorage(c.getString(0));
                int count = c.getInt(1);
                total += count;
                bytes += c.isNull(2) ? 0L : Math.max(0L, c.getLong(2));
                bytesTotal += c.isNull(3) ? 0L : Math.max(0L, c.getLong(3));
                switch (state) {
                    case AVAILABLE: completed += count; break;
                    case FAILED: failed += count; break;
                    case DOWNLOADING: downloading += count; break;
                    case PENDING: default: pending += count; break;
                }
            }
        }
        OfflinePlaylistRecord current = findPlaylist(playlistId);
        if (current == null) return;
        OfflinePlaylistState state = current.getState();
        if (state != OfflinePlaylistState.PAUSED) {
            if (total > 0 && completed == total) state = OfflinePlaylistState.AVAILABLE;
            else if (total > 0 && completed + failed == total) {
                state = completed > 0 ? OfflinePlaylistState.PARTIAL : OfflinePlaylistState.FAILED;
            } else if (downloading > 0) state = OfflinePlaylistState.DOWNLOADING;
            else if (pending > 0) state = OfflinePlaylistState.QUEUED;
        }
        ContentValues p = new ContentValues();
        p.put("state", state.name());
        p.put("total_count", total);
        p.put("completed_count", completed);
        p.put("failed_count", failed);
        p.put("bytes_downloaded", bytes);
        p.put("bytes_total", bytesTotal);
        p.put("updated_at_ms", nowMs);
        getWritableDatabase().update(P, p, "playlist_id=?", new String[]{playlistId});
    }

    private static OfflinePlaylistRecord readPlaylist(Cursor c) {
        return new OfflinePlaylistRecord(s(c,"playlist_id"), s(c,"title"), s(c,"thumbnail_url"),
                OfflinePlaylistState.fromStorage(s(c,"state")), i(c,"total_count"),
                i(c,"completed_count"), i(c,"failed_count"), l(c,"bytes_downloaded"),
                l(c,"bytes_total"), s(c,"failure_reason"), l(c,"created_at_ms"), l(c,"updated_at_ms"));
    }

    private static OfflinePlaylistEntry readEntry(Cursor c) {
        return new OfflinePlaylistEntry(s(c,"playlist_id"), i(c,"position"), s(c,"media_id"),
                s(c,"title"), s(c,"author"), s(c,"thumbnail_url"), l(c,"duration_ms"),
                OfflinePlaylistItemState.fromStorage(s(c,"state")), l(c,"bytes_downloaded"),
                l(c,"bytes_total"), s(c,"failure_reason"), i(c,"attempts"));
    }

    private static String s(Cursor c, String name) { int x=c.getColumnIndex(name); return x<0||c.isNull(x)?"":c.getString(x); }
    private static long l(Cursor c, String name) { int x=c.getColumnIndex(name); return x<0||c.isNull(x)?0L:c.getLong(x); }
    private static int i(Cursor c, String name) { int x=c.getColumnIndex(name); return x<0||c.isNull(x)?0:c.getInt(x); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String compact(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }
}
