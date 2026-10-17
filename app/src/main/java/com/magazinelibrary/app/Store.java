package com.magazinelibrary.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** SQLite store for downloads, library metadata and reading progress. Cookies are never stored. */
public class Store extends SQLiteOpenHelper {
    public static final String QUEUED = "queued", RUNNING = "running", PAUSED = "paused",
            AUTH = "auth", WAITING = "waiting", READY = "ready", FAILED = "failed";

    private static final int VERSION = 4;
    private static Store instance;

    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private Store(Context c) {
        this(c, "library.db");
    }

    Store(Context c, String name) {
        super(c, name, null, VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, source TEXT,"
                + " title TEXT, series TEXT, status TEXT, done INTEGER DEFAULT 0,"
                + " total INTEGER DEFAULT -1, uri TEXT DEFAULT '', tree TEXT DEFAULT '',"
                + " etag TEXT DEFAULT '', error TEXT DEFAULT '', ua TEXT DEFAULT '',"
                + " verification TEXT DEFAULT '', link TEXT DEFAULT '',"
                + " history_hidden INTEGER NOT NULL DEFAULT 0, page INTEGER DEFAULT 0,"
                + " pages INTEGER DEFAULT 0, created INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int old, int next) {
        if (old < 2) db.execSQL("ALTER TABLE items ADD COLUMN verification TEXT DEFAULT ''");
        if (old < 3) {
            db.execSQL("ALTER TABLE items ADD COLUMN link TEXT DEFAULT ''");
            db.execSQL("UPDATE items SET link=url");
            // Records written by 1.1 named the VK preview as an auth error; they now wait normally.
            db.execSQL("UPDATE items SET status='waiting', error=''"
                    + " WHERE status='auth' AND error LIKE 'VK 预览页%'");
        }
        if (old < 4) db.execSQL("ALTER TABLE items ADD COLUMN history_hidden INTEGER NOT NULL DEFAULT 0");
    }

    public synchronized Item add(String url, String source, String title, String tree, String ua) {
        Item existing = findDownload(url);
        if (existing != null) return existing;
        ContentValues values = new ContentValues();
        values.put("url", url);
        values.put("link", url);
        values.put("source", source);
        values.put("title", Names.title(title));
        values.put("series", Names.series(title));
        values.put("tree", tree);
        values.put("ua", ua);
        values.put("status", url.isEmpty() ? "importing" : QUEUED);
        values.put("created", System.currentTimeMillis());
        return item(getWritableDatabase().insertOrThrow("items", null, values));
    }

    public synchronized Item findDownload(String url) {
        if (!url.isEmpty()) {
            for (Item item : all()) {
                if ((url.equals(item.url) || url.equals(item.link)) && !FAILED.equals(item.status)) {
                    return item;
                }
            }
        }
        return null;
    }

    public synchronized void update(long id, ContentValues values) {
        if (QUEUED.equals(values.getAsString("status"))) values.put("history_hidden", 0);
        getWritableDatabase().update("items", values, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void remove(long id) {
        getWritableDatabase().delete("items", "id=?", new String[]{String.valueOf(id)});
    }

    public void status(long id, String status, String error) {
        ContentValues values = new ContentValues();
        values.put("status", status);
        values.put("error", error);
        update(id, values);
    }

    public synchronized List<Item> all() {
        List<Item> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "items", null, null, null, null, null, "created DESC")) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    public synchronized List<Item> history() {
        List<Item> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "items", null, "history_hidden=0", null, null, null, "created DESC")) {
            while (c.moveToNext()) list.add(read(c));
        }
        return list;
    }

    public synchronized int clearDownloadHistory() {
        // Keep the book metadata and files; only hide terminal download records.
        ContentValues values = new ContentValues();
        values.put("history_hidden", 1);
        return getWritableDatabase().update(
                "items", values, "history_hidden=0 AND status IN (?,?)", new String[]{READY, FAILED});
    }

    public synchronized Item item(long id) {
        try (Cursor c = getReadableDatabase().query(
                "items", null, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public synchronized Item next() {
        try (Cursor c = getReadableDatabase().query(
                "items", null, "status=?", new String[]{QUEUED}, null, null, "created ASC", "1")) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public void recover() {
        ContentValues waiting = new ContentValues();
        waiting.put("status", PAUSED);
        waiting.put("error", T.s(R.string.status_recover));
        getWritableDatabase().update(
                "items", waiting, "status IN (?,?)", new String[]{RUNNING, QUEUED});
        ContentValues importing = new ContentValues();
        importing.put("status", FAILED);
        importing.put("error", T.s(R.string.status_import_interrupted));
        getWritableDatabase().update("items", importing, "status=?", new String[]{"importing"});
    }

    private Item read(Cursor c) {
        Item item = new Item();
        item.id = num(c, "id");
        item.url = str(c, "url");
        item.source = str(c, "source");
        item.title = str(c, "title");
        item.series = str(c, "series");
        item.status = str(c, "status");
        item.done = num(c, "done");
        item.total = num(c, "total");
        item.uri = str(c, "uri");
        item.tree = str(c, "tree");
        item.etag = str(c, "etag");
        item.error = str(c, "error");
        item.ua = str(c, "ua");
        item.verification = str(c, "verification");
        item.link = str(c, "link");
        item.page = (int) num(c, "page");
        item.pages = (int) num(c, "pages");
        return item;
    }

    private static String str(Cursor c, String key) {
        return c.getString(c.getColumnIndexOrThrow(key));
    }

    private static long num(Cursor c, String key) {
        return c.getLong(c.getColumnIndexOrThrow(key));
    }

    public static class Item {
        public long id, done, total;
        public int page, pages;
        public String url, source, title, series, status, uri, tree, etag, error, ua,
                verification, link;
    }
}
