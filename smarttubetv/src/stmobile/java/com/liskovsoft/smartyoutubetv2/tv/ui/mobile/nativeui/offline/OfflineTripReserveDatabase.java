package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local playback/playlist history for Stage 10. No signed URL or stream URL is stored. */
final class OfflineTripReserveDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "smarttube_mobile_offline_trip_reserve.db";
    private static final int DB_VERSION = 1;
    private static final String R = "trip_recent";
    private static final String P = "trip_recent_playlists";
    private static final int MAX_HISTORY = 400;
    private static final int MAX_PLAYLIST_HISTORY = 40;

    OfflineTripReserveDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + R + " ("
                + "media_id TEXT PRIMARY KEY NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "author TEXT NOT NULL DEFAULT '',"
                + "thumbnail_url TEXT NOT NULL DEFAULT '',"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "last_played_at_ms INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX trip_recent_time_idx ON " + R + "(last_played_at_ms DESC)");
        db.execSQL("CREATE TABLE " + P + " ("
                + "playlist_id TEXT PRIMARY KEY NOT NULL,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "thumbnail_url TEXT NOT NULL DEFAULT '',"
                + "last_played_at_ms INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX trip_playlist_time_idx ON " + P + "(last_played_at_ms DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migrations only. Playback history is disposable but should not be dropped silently.
    }

    synchronized void recordPlayback(OfflineMediaDescriptor descriptor, long nowMs) {
        if (descriptor == null || !descriptor.isValid()) return;
        ContentValues v = new ContentValues();
        v.put("media_id", descriptor.getMediaId());
        v.put("title", descriptor.getTitle());
        v.put("author", descriptor.getAuthor());
        v.put("thumbnail_url", descriptor.getThumbnailUrl());
        v.put("duration_ms", descriptor.getDurationMs());
        v.put("last_played_at_ms", Math.max(0L, nowMs));
        getWritableDatabase().insertWithOnConflict(R, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        trimRecent();
    }

    synchronized void recordPlaylist(String playlistId, String title, String thumbnailUrl,
                                     long nowMs) {
        String id = safe(playlistId);
        if (id.isEmpty() || isEphemeralMix(id)) return;
        ContentValues v = new ContentValues();
        v.put("playlist_id", id);
        v.put("title", safe(title));
        v.put("thumbnail_url", safe(thumbnailUrl));
        v.put("last_played_at_ms", Math.max(0L, nowMs));
        getWritableDatabase().insertWithOnConflict(P, null, v, SQLiteDatabase.CONFLICT_REPLACE);
        trimPlaylists();
    }

    synchronized List<OfflineMediaDescriptor> listRecent(int limit) {
        String lim = limit > 0 ? Integer.toString(limit) : null;
        ArrayList<OfflineMediaDescriptor> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(R, null, null, null, null, null,
                "last_played_at_ms DESC", lim)) {
            while (c.moveToNext()) {
                out.add(new OfflineMediaDescriptor(s(c,"media_id"), s(c,"title"), s(c,"author"),
                        s(c,"thumbnail_url"), l(c,"duration_ms"), "", ""));
            }
        }
        return out;
    }

    synchronized List<OfflineTripReservePlaylistRef> listRecentPlaylists(int limit) {
        String lim = limit > 0 ? Integer.toString(limit) : null;
        ArrayList<OfflineTripReservePlaylistRef> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(P, null, null, null, null, null,
                "last_played_at_ms DESC", lim)) {
            while (c.moveToNext()) {
                out.add(new OfflineTripReservePlaylistRef(s(c,"playlist_id"), s(c,"title"),
                        s(c,"thumbnail_url"), l(c,"last_played_at_ms")));
            }
        }
        return out;
    }

    synchronized int recentCount() { return count(R); }
    synchronized int playlistHistoryCount() { return count(P); }

    synchronized void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(R, null, null);
            db.delete(P, null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private int count(String table) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private void trimRecent() {
        getWritableDatabase().execSQL("DELETE FROM " + R + " WHERE media_id NOT IN (SELECT media_id FROM "
                + R + " ORDER BY last_played_at_ms DESC LIMIT " + MAX_HISTORY + ")");
    }

    private void trimPlaylists() {
        getWritableDatabase().execSQL("DELETE FROM " + P + " WHERE playlist_id NOT IN (SELECT playlist_id FROM "
                + P + " ORDER BY last_played_at_ms DESC LIMIT " + MAX_PLAYLIST_HISTORY + ")");
    }

    private static boolean isEphemeralMix(String playlistId) {
        // YouTube auto-mixes/radios are often unbounded/volatile and are a poor fit for a finite reserve.
        return playlistId.startsWith("RD") || playlistId.startsWith("UL");
    }

    private static String s(Cursor c, String name) {
        int i=c.getColumnIndex(name); return i<0||c.isNull(i)?"":c.getString(i);
    }
    private static long l(Cursor c, String name) {
        int i=c.getColumnIndex(name); return i<0||c.isNull(i)?0L:c.getLong(i);
    }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
