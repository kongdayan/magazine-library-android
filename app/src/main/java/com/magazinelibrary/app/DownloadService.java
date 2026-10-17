package com.magazinelibrary.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A persisted queue with a foreground worker, domain-scoped cookies and validated output. */
public class DownloadService extends Service {
    static volatile boolean isAlive;
    static final String EVENT = "com.magazinelibrary.app.CHANGED";
    private static final int NOTIFICATION_ID = 7;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Store store;
    private volatile boolean draining, stopping;
    private volatile HttpURLConnection active;

    static void start(Context c) {
        c.startForegroundService(new Intent(c, DownloadService.class));
    }

    static void changed(Context c) {
        c.sendBroadcast(new Intent(EVENT).setPackage(c.getPackageName()));
    }

    private static void changed(Context c, long id) {
        c.sendBroadcast(new Intent(EVENT).setPackage(c.getPackageName()).putExtra("id", id));
    }

    @Override public void onCreate() {
        super.onCreate();
        isAlive = true;
        store = Store.get(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                "downloads", getString(R.string.downloads_notification_channel),
                NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID,
                notification(getString(R.string.downloads_notification_preparing), 0, -1),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        synchronized (this) {
            if (!draining) {
                draining = true;
                worker.execute(this::drain);
            }
        }
        return START_NOT_STICKY;
    }

    private void drain() {
        try {
            Store.Item item;
            while (!stopping && (item = store.next()) != null) {
                try {
                    transfer(item);
                } catch (Pause e) {
                    store.status(item.id, Store.PAUSED, T.s(R.string.status_paused));
                } catch (Landing e) {
                    store.status(item.id, Store.WAITING, e.getMessage());
                } catch (Auth e) {
                    store.status(item.id, Store.AUTH, T.s(R.string.error_need_verify));
                    ContentValues values = new ContentValues();
                    values.put("error", e.getMessage());
                    store.update(item.id, values);
                } catch (Exception e) {
                    store.status(item.id, Store.FAILED,
                            e.getMessage() == null ? T.s(R.string.error_download_failed) : e.getMessage());
                }
                changed(this, item.id);
            }
        } finally {
            main.post(() -> {
                synchronized (this) {
                    draining = false;
                    if (!stopping && store.next() != null) {
                        draining = true;
                        worker.execute(this::drain);
                        return;
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                }
            });
        }
    }

    private void check(long id) throws Pause {
        Store.Item current = store.item(id);
        if (stopping || current == null || Store.PAUSED.equals(current.status)) throw new Pause();
    }

    private String cookie(String url) throws Exception {
        FutureTask<String> task = new FutureTask<>(() -> CookieManager.getInstance().getCookie(url));
        main.post(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private void responseCookies(String url, HttpURLConnection connection) throws Exception {
        List<String> values = connection.getHeaderFields().entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase("Set-Cookie"))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList());
        if (values.isEmpty()) return;
        CountDownLatch latch = new CountDownLatch(values.size());
        main.post(() -> {
            for (String value : values) {
                CookieManager.getInstance().setCookie(url, value, ok -> latch.countDown());
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new IOException(T.s(R.string.error_cookie_timeout));
        main.post(() -> CookieManager.getInstance().flush());
    }

    private HttpURLConnection open(Store.Item item, long offset) throws Exception {
        String url = item.url;
        for (int hop = 0; hop < 12; hop++) {
            check(item.id);
            if (!Names.https(url)) throw new IOException(T.s(R.string.error_https_only));
            ContentValues hopInfo = new ContentValues();
            hopInfo.put("verification", url);
            store.update(item.id, hopInfo);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            active = connection;
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(25000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", item.ua);
            connection.setRequestProperty("Accept", "application/pdf,*/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            String cookies = cookie(url);
            if (cookies != null && !cookies.isEmpty()) connection.setRequestProperty("Cookie", cookies);
            if (Names.https(item.source)) connection.setRequestProperty("Referer", item.source);
            if (offset > 0 && !item.etag.isEmpty()) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
                connection.setRequestProperty("If-Range", item.etag);
            }
            int code;
            try {
                code = connection.getResponseCode();
            } catch (java.net.SocketTimeoutException e) {
                connection.disconnect();
                throw new IOException(T.s(R.string.error_connect_timeout, new URL(url).getHost()), e);
            }
            responseCookies(url, connection);
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException(T.s(R.string.error_redirect_missing));
                url = new URL(new URL(url), location).toString();
                URL next = new URL(url);
                if ((next.getHost().equals("vk.com") || next.getHost().equals("m.vk.com"))
                        && next.getPath().startsWith("/doc")) {
                    ContentValues landing = new ContentValues();
                    landing.put("verification", url);
                    store.update(item.id, landing);
                    throw new Landing(T.s(R.string.error_vk_landing));
                }
                continue;
            }
            if ("challenge".equalsIgnoreCase(connection.getHeaderField("cf-mitigated"))) {
                connection.disconnect();
                throw new Auth(T.s(R.string.error_auth));
            }
            String type = connection.getContentType();
            if (type != null && type.toLowerCase(Locale.ROOT).contains("text/html")) {
                String html;
                try (InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while (in != null && out.size() < 32768
                            && (n = in.read(buffer, 0, Math.min(buffer.length, 32768 - out.size()))) != -1) {
                        out.write(buffer, 0, n);
                    }
                    html = out.toString("UTF-8");
                } finally {
                    connection.disconnect();
                }
                if (Names.challenge(html)) throw new Auth(T.s(R.string.error_auth));
                if (code >= 400) throw new IOException(T.s(R.string.error_link_gone, code));
                throw new Landing(T.s(R.string.error_landing));
            }
            if (code != 200 && code != 206) {
                connection.disconnect();
                throw new IOException(T.s(R.string.error_http, code));
            }
            return connection;
        }
        throw new IOException(T.s(R.string.error_too_many_redirects));
    }

    private void transfer(Store.Item item) throws Exception {
        store.status(item.id, Store.RUNNING, "");
        changed(this);
        File part = Files.part(this, item.id);
        long offset = part.exists() ? part.length() : 0;
        if (item.etag.isEmpty()) offset = 0;
        HttpURLConnection connection = open(item, offset);
        try {
            boolean append = offset > 0 && connection.getResponseCode() == 206;
            String range = connection.getHeaderField("Content-Range");
            if (append && (range == null || !range.startsWith("bytes " + offset + "-"))) {
                throw new IOException(T.s(R.string.error_resume_mismatch));
            }
            if (!append) offset = 0;
            long length = connection.getContentLengthLong();
            long total = length >= 0 ? offset + length : -1;
            String etag = connection.getHeaderField("ETag");
            if (etag == null) etag = connection.getHeaderField("Last-Modified");
            ContentValues meta = new ContentValues();
            meta.put("etag", etag == null ? "" : etag);
            meta.put("total", total);
            meta.put("done", offset);
            store.update(item.id, meta);
            long done = offset, last = 0;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(part, append)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    check(item.id);
                    out.write(buffer, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - last > 600) {
                        last = now;
                        progress(item, done, total);
                    }
                }
            }
            check(item.id);
            progress(item, done, total);
            if (total >= 0 && done != total) throw new IOException(T.s(R.string.error_incomplete));
            int pages = Files.validate(part);
            Files.makeCover(this, item.id, part);
            check(item.id);
            Uri uri = Files.publish(this, item, part);
            ContentValues values = new ContentValues();
            values.put("uri", uri.toString());
            values.put("pages", pages);
            values.put("done", done);
            values.put("total", done);
            values.put("status", Store.READY);
            values.put("error", "");
            try {
                synchronized (store) {
                    check(item.id);
                    store.update(item.id, values);
                }
            } catch (Pause canceled) {
                Files.deleteOutput(this, uri);
                throw canceled;
            }
            part.delete();
        } finally {
            connection.disconnect();
            active = null;
        }
    }

    private void progress(Store.Item item, long done, long total) {
        ContentValues values = new ContentValues();
        values.put("done", done);
        values.put("total", total);
        store.update(item.id, values);
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID, notification(item.title, done, total));
        changed(this);
    }

    private Notification notification(String title, long done, long total) {
        PendingIntent intent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).putExtra("tab", "downloads"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, "downloads")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(Ui.size(done) + (total > 0 ? " / " + Ui.size(total) : ""))
                .setContentIntent(intent)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (total > 0) builder.setProgress(100, (int) (done * 100 / total), false);
        else builder.setProgress(0, 0, true);
        return builder.build();
    }

    @Override public void onTimeout(int startId, int fgsType) {
        stopping = true;
        if (active != null) active.disconnect();
        store.recover();
        changed(this);
        stopSelf();
    }

    @Override public void onDestroy() {
        isAlive = false;
        stopping = true;
        if (active != null) active.disconnect();
        worker.shutdown();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private static class Landing extends IOException {
        Landing(String message) {
            super(message);
        }
    }

    private static class Auth extends IOException {
        Auth(String message) {
            super(message);
        }
    }

    private static class Pause extends IOException {
    }
}
