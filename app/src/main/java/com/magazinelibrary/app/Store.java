package com.magazinelibrary.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class Store extends SQLiteOpenHelper {
    public static final String QUEUED="queued", RUNNING="running", PAUSED="paused", AUTH="auth", WAITING="waiting", READY="ready", FAILED="failed";
    private static Store instance;
    public static synchronized Store get(Context c) { if(instance==null) instance=new Store(c.getApplicationContext()); return instance; }
    private Store(Context c) { this(c,"library.db"); }
    Store(Context c,String name) { super(c, name, null, 4); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, source TEXT, title TEXT, series TEXT, status TEXT, done INTEGER DEFAULT 0, total INTEGER DEFAULT -1, uri TEXT DEFAULT '', tree TEXT DEFAULT '', etag TEXT DEFAULT '', error TEXT DEFAULT '', ua TEXT DEFAULT '', verification TEXT DEFAULT '', link TEXT DEFAULT '', history_hidden INTEGER NOT NULL DEFAULT 0, page INTEGER DEFAULT 0, pages INTEGER DEFAULT 0, created INTEGER)");
    }
    public void onUpgrade(SQLiteDatabase db,int old,int next) {if(old<2)db.execSQL("ALTER TABLE items ADD COLUMN verification TEXT DEFAULT ''");if(old<3){db.execSQL("ALTER TABLE items ADD COLUMN link TEXT DEFAULT ''");db.execSQL("UPDATE items SET link=url");db.execSQL("UPDATE items SET status='waiting',error='等待下载页跳转；可点击页面下载按钮' WHERE status='auth' AND error LIKE 'VK 预览页%'");}if(old<4)db.execSQL("ALTER TABLE items ADD COLUMN history_hidden INTEGER NOT NULL DEFAULT 0");}
    public synchronized Item add(String url,String source,String title,String tree,String ua) {
        Item existing=findDownload(url);if(existing!=null)return existing;
        ContentValues v=new ContentValues(); v.put("url",url);v.put("link",url);v.put("source",source);v.put("title",Names.title(title));v.put("series",Names.series(title));v.put("tree",tree);v.put("ua",ua);v.put("status",url.isEmpty()?"importing":QUEUED);v.put("created",System.currentTimeMillis());
        return item(getWritableDatabase().insertOrThrow("items",null,v));
    }
    public synchronized Item findDownload(String url){if(!url.isEmpty())for(Item i:all())if((url.equals(i.url)||url.equals(i.link))&&!FAILED.equals(i.status))return i;return null;}
    public synchronized void update(long id,ContentValues v) { if(QUEUED.equals(v.getAsString("status")))v.put("history_hidden",0);getWritableDatabase().update("items",v,"id=?",new String[]{""+id}); }
    public synchronized void remove(long id){getWritableDatabase().delete("items","id=?",new String[]{""+id});}
    public void status(long id,String s,String error) { ContentValues v=new ContentValues();v.put("status",s);v.put("error",error);update(id,v); }
    public synchronized List<Item> all() {
        List<Item> list=new ArrayList<>();try(Cursor c=getReadableDatabase().query("items",null,null,null,null,null,"created DESC")) {while(c.moveToNext()) list.add(read(c));}return list;
    }
    public synchronized List<Item> history() {
        List<Item> list=new ArrayList<>();try(Cursor c=getReadableDatabase().query("items",null,"history_hidden=0",null,null,null,"created DESC")) {while(c.moveToNext())list.add(read(c));}return list;
    }
    public synchronized int clearDownloadHistory() {
        // Keep the shared book metadata and files; only hide terminal download records.
        ContentValues v=new ContentValues();v.put("history_hidden",1);
        return getWritableDatabase().update("items",v,"history_hidden=0 AND status IN (?,?)",new String[]{READY,FAILED});
    }
    public synchronized Item item(long id) {
        try(Cursor c=getReadableDatabase().query("items",null,"id=?",new String[]{""+id},null,null,null)) {return c.moveToFirst()?read(c):null;}
    }
    public synchronized Item next() {
        try(Cursor c=getReadableDatabase().query("items",null,"status=?",new String[]{QUEUED},null,null,"created ASC","1")) {return c.moveToFirst()?read(c):null;}
    }
    public void recover() { ContentValues v=new ContentValues();v.put("status",PAUSED);v.put("error","下载中断，点继续重试");getWritableDatabase().update("items",v,"status IN (?,?)",new String[]{RUNNING,QUEUED});ContentValues local=new ContentValues();local.put("status",FAILED);local.put("error","导入中断，请重新导入文件");getWritableDatabase().update("items",local,"status=?",new String[]{"importing"}); }
    private Item read(Cursor c) {
        Item i=new Item();i.id=num(c,"id");i.url=str(c,"url");i.source=str(c,"source");i.title=str(c,"title");i.series=str(c,"series");i.status=str(c,"status");i.done=num(c,"done");i.total=num(c,"total");i.uri=str(c,"uri");i.tree=str(c,"tree");i.etag=str(c,"etag");i.error=str(c,"error");i.ua=str(c,"ua");i.verification=str(c,"verification");i.link=str(c,"link");i.page=(int)num(c,"page");i.pages=(int)num(c,"pages");return i;
    }
    private static String str(Cursor c,String k){return c.getString(c.getColumnIndexOrThrow(k));}
    private static long num(Cursor c,String k){return c.getLong(c.getColumnIndexOrThrow(k));}
    public static class Item {
        public long id,done,total;public int page,pages; public String url,source,title,series,status,uri,tree,etag,error,ua,verification,link;
    }
}
