package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Small local index for offline audio. Intentionally independent from legacy SmartTube databases. */
final class OfflineMediaDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "smarttube_mobile_offline.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "offline_media";

    private static final String C_MEDIA_ID = "media_id";
    private static final String C_TITLE = "title";
    private static final String C_AUTHOR = "author";
    private static final String C_THUMBNAIL = "thumbnail_url";
    private static final String C_DURATION = "duration_ms";
    private static final String C_MIME = "mime_type";
    private static final String C_CODEC = "codec";
    private static final String C_FILE_KEY = "file_key";
    private static final String C_BYTES_DOWNLOADED = "bytes_downloaded";
    private static final String C_BYTES_TOTAL = "bytes_total";
    private static final String C_STATE = "state";
    private static final String C_FAILURE = "failure_reason";
    private static final String C_CREATED = "created_at_ms";
    private static final String C_UPDATED = "updated_at_ms";
    private static final String C_LAST_ACCESS = "last_access_at_ms";
    private static final String C_EXPIRES = "expires_at_ms";

    OfflineMediaDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + C_MEDIA_ID + " TEXT PRIMARY KEY NOT NULL,"
                + C_TITLE + " TEXT NOT NULL DEFAULT '',"
                + C_AUTHOR + " TEXT NOT NULL DEFAULT '',"
                + C_THUMBNAIL + " TEXT NOT NULL DEFAULT '',"
                + C_DURATION + " INTEGER NOT NULL DEFAULT 0,"
                + C_MIME + " TEXT NOT NULL DEFAULT '',"
                + C_CODEC + " TEXT NOT NULL DEFAULT '',"
                + C_FILE_KEY + " TEXT NOT NULL,"
                + C_BYTES_DOWNLOADED + " INTEGER NOT NULL DEFAULT 0,"
                + C_BYTES_TOTAL + " INTEGER NOT NULL DEFAULT 0,"
                + C_STATE + " TEXT NOT NULL,"
                + C_FAILURE + " TEXT NOT NULL DEFAULT '',"
                + C_CREATED + " INTEGER NOT NULL DEFAULT 0,"
                + C_UPDATED + " INTEGER NOT NULL DEFAULT 0,"
                + C_LAST_ACCESS + " INTEGER NOT NULL DEFAULT 0,"
                + C_EXPIRES + " INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX offline_media_state_idx ON " + TABLE + "(" + C_STATE + ")");
        db.execSQL("CREATE INDEX offline_media_access_idx ON " + TABLE + "(" + C_LAST_ACCESS + ")");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 is intentionally tiny. Future schema changes should migrate rather than drop user audio.
    }

    synchronized void upsertDownloading(OfflineMediaDescriptor descriptor, String fileKey,
                                        long bytesDownloaded, long bytesTotal, long nowMs) {
        OfflineMediaRecord existing = find(descriptor.getMediaId());
        ContentValues values = new ContentValues();
        values.put(C_MEDIA_ID, descriptor.getMediaId());
        values.put(C_TITLE, descriptor.getTitle());
        values.put(C_AUTHOR, descriptor.getAuthor());
        values.put(C_THUMBNAIL, descriptor.getThumbnailUrl());
        values.put(C_DURATION, descriptor.getDurationMs());
        values.put(C_MIME, descriptor.getMimeType());
        values.put(C_CODEC, descriptor.getCodec());
        values.put(C_FILE_KEY, fileKey);
        values.put(C_BYTES_DOWNLOADED, Math.max(0L, bytesDownloaded));
        values.put(C_BYTES_TOTAL, Math.max(0L, bytesTotal));
        values.put(C_STATE, OfflineMediaState.DOWNLOADING.name());
        values.put(C_FAILURE, "");
        values.put(C_CREATED, existing == null ? nowMs : existing.getCreatedAtMs());
        values.put(C_UPDATED, nowMs);
        values.put(C_LAST_ACCESS, existing == null ? nowMs : existing.getLastAccessAtMs());
        values.put(C_EXPIRES, 0L);
        getWritableDatabase().insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    synchronized void updateProgress(String mediaId, long bytesDownloaded, long bytesTotal, long nowMs) {
        ContentValues values = new ContentValues();
        values.put(C_BYTES_DOWNLOADED, Math.max(0L, bytesDownloaded));
        values.put(C_BYTES_TOTAL, Math.max(0L, bytesTotal));
        values.put(C_UPDATED, nowMs);
        getWritableDatabase().update(TABLE, values, C_MEDIA_ID + "=?", new String[]{mediaId});
    }

    synchronized void updateState(String mediaId, OfflineMediaState state, long bytesDownloaded,
                                  long bytesTotal, String failureReason, long expiresAtMs, long nowMs) {
        ContentValues values = new ContentValues();
        values.put(C_STATE, state.name());
        values.put(C_BYTES_DOWNLOADED, Math.max(0L, bytesDownloaded));
        values.put(C_BYTES_TOTAL, Math.max(0L, bytesTotal));
        values.put(C_FAILURE, failureReason == null ? "" : failureReason);
        values.put(C_EXPIRES, Math.max(0L, expiresAtMs));
        values.put(C_UPDATED, nowMs);
        if (state == OfflineMediaState.AVAILABLE) values.put(C_LAST_ACCESS, nowMs);
        getWritableDatabase().update(TABLE, values, C_MEDIA_ID + "=?", new String[]{mediaId});
    }

    synchronized void touch(String mediaId, long nowMs) {
        ContentValues values = new ContentValues();
        values.put(C_LAST_ACCESS, nowMs);
        getWritableDatabase().update(TABLE, values, C_MEDIA_ID + "=?", new String[]{mediaId});
    }

    synchronized OfflineMediaRecord find(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return null;
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, C_MEDIA_ID + "=?",
                new String[]{mediaId}, null, null, null, "1")) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    synchronized List<OfflineMediaRecord> listAvailable(int limit) {
        String limitString = limit > 0 ? Integer.toString(limit) : null;
        try (Cursor cursor = getReadableDatabase().query(TABLE, null,
                C_STATE + "=?", new String[]{OfflineMediaState.AVAILABLE.name()},
                null, null, C_LAST_ACCESS + " DESC," + C_UPDATED + " DESC", limitString)) {
            return readAll(cursor);
        }
    }

    synchronized List<OfflineMediaRecord> listEvictionCandidates() {
        String order = "CASE " + C_STATE
                + " WHEN 'EXPIRED' THEN 0 WHEN 'FAILED' THEN 1 ELSE 2 END ASC,"
                + C_LAST_ACCESS + " ASC," + C_UPDATED + " ASC";
        try (Cursor cursor = getReadableDatabase().query(TABLE, null,
                C_STATE + "<>?", new String[]{OfflineMediaState.DOWNLOADING.name()},
                null, null, order)) {
            return readAll(cursor);
        }
    }

    synchronized List<OfflineMediaRecord> listAll() {
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, null, null,
                null, null, C_UPDATED + " DESC")) {
            return readAll(cursor);
        }
    }

    synchronized void delete(String mediaId) {
        getWritableDatabase().delete(TABLE, C_MEDIA_ID + "=?", new String[]{mediaId});
    }

    synchronized void clear() { getWritableDatabase().delete(TABLE, null, null); }

    synchronized DbStats stats() {
        int total = 0;
        int downloading = 0;
        int available = 0;
        int failed = 0;
        int expired = 0;
        long bytes = 0L;
        String sql = "SELECT " + C_STATE + ",COUNT(*),SUM(" + C_BYTES_DOWNLOADED + ") FROM "
                + TABLE + " GROUP BY " + C_STATE;
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                OfflineMediaState state = OfflineMediaState.fromStorage(cursor.getString(0));
                int count = cursor.getInt(1);
                long stateBytes = cursor.isNull(2) ? 0L : cursor.getLong(2);
                total += count;
                bytes += Math.max(0L, stateBytes);
                switch (state) {
                    case DOWNLOADING: downloading += count; break;
                    case AVAILABLE: available += count; break;
                    case EXPIRED: expired += count; break;
                    case FAILED:
                    default: failed += count; break;
                }
            }
        }
        return new DbStats(total, downloading, available, failed, expired, bytes);
    }

    private static List<OfflineMediaRecord> readAll(Cursor cursor) {
        ArrayList<OfflineMediaRecord> result = new ArrayList<>();
        while (cursor.moveToNext()) result.add(read(cursor));
        return result;
    }

    private static OfflineMediaRecord read(Cursor cursor) {
        return new OfflineMediaRecord(
                string(cursor, C_MEDIA_ID), string(cursor, C_TITLE), string(cursor, C_AUTHOR),
                string(cursor, C_THUMBNAIL), longValue(cursor, C_DURATION), string(cursor, C_MIME),
                string(cursor, C_CODEC), string(cursor, C_FILE_KEY),
                longValue(cursor, C_BYTES_DOWNLOADED), longValue(cursor, C_BYTES_TOTAL),
                OfflineMediaState.fromStorage(string(cursor, C_STATE)), string(cursor, C_FAILURE),
                longValue(cursor, C_CREATED), longValue(cursor, C_UPDATED),
                longValue(cursor, C_LAST_ACCESS), longValue(cursor, C_EXPIRES));
    }

    private static String string(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    static final class DbStats {
        final int total;
        final int downloading;
        final int available;
        final int failed;
        final int expired;
        final long bytes;

        DbStats(int total, int downloading, int available, int failed, int expired, long bytes) {
            this.total = total;
            this.downloading = downloading;
            this.available = available;
            this.failed = failed;
            this.expired = expired;
            this.bytes = bytes;
        }
    }
}
