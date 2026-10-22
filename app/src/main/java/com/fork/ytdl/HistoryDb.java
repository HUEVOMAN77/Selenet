package com.fork.ytdl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Historial persistente de descargas terminadas. */
public class HistoryDb extends SQLiteOpenHelper {

    private static final String NAME = "historial";
    private static final String TABLE = "descargas";

    private static HistoryDb instance;

    public static synchronized HistoryDb get(Context context) {
        if (instance == null) {
            instance = new HistoryDb(context.getApplicationContext());
        }
        return instance;
    }

    private HistoryDb(Context context) {
        super(context, NAME, null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id TEXT PRIMARY KEY, url TEXT, title TEXT, fmt TEXT, label TEXT, path TEXT, "
                + "bytes INTEGER, finished_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int from, int to) {
        if (from < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN label TEXT");
        }
    }

    public void put(Task task) {
        ContentValues values = new ContentValues();
        values.put("id", task.id);
        values.put("url", task.url);
        values.put("title", task.title);
        values.put("fmt", task.fmt);
        values.put("label", task.label);
        values.put("path", task.path);
        values.put("bytes", task.bytes);
        values.put("finished_at", task.finishedAt);
        getWritableDatabase().replace(TABLE, null, values);
    }

    public void remove(String id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{id});
    }

    public List<Task> all() {
        List<Task> tasks = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, null, null, null, null,
                "finished_at DESC", "200")) {
            int index = cursor.getColumnIndexOrThrow("id");
            while (cursor.moveToNext()) {
                JSONObject json = new JSONObject();
                try {
                    json.put("id", cursor.getString(index))
                            .put("url", str(cursor, "url"))
                            .put("title", str(cursor, "title"))
                            .put("fmt", str(cursor, "fmt"))
                            .put("label", str(cursor, "label"))
                            .put("state", Task.DONE)
                            .put("path", str(cursor, "path"))
                            .put("bytes", cursor.getLong(cursor.getColumnIndexOrThrow("bytes")))
                            .put("finishedAt",
                                    cursor.getLong(cursor.getColumnIndexOrThrow("finished_at")));
                } catch (Exception ignored) {
                }
                tasks.add(new Task(json));
            }
        }
        return tasks;
    }

    private static String str(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }
}
