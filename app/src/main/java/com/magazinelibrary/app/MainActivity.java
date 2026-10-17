package com.magazinelibrary.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Discover, library, downloads and settings, with the site rendered in a system WebView. */
public class MainActivity extends Activity {
    static final String HOME = "https://magazinelib.com/";

    private Store store;
    private SharedPreferences prefs;
    private LinearLayout root, header, browserPane, listPane;
    private TextView brand;
    private FrameLayout content;
    private ScrollView scroll;
    private WebView web;
    private TextView browserStatus;
    private ProgressBar webProgress;
    private NativeCatalog catalog;
    private LinearLayout browserToolbar;
    private boolean browserVisible;
    private final Map<String, Integer> scrollPositions = new HashMap<>();
    private String renderedTab = "";
    private String currentTab = "discover", libraryQuery = "";
    private long verifyingId = -1;
    private String allowedVerification = "";
    private String browserEvents = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;
    private final ExecutorService fileWorker = Executors.newSingleThreadExecutor();
    private Click clicked, touchSelection;
    private String capturedKey = "";
    private int touchToken;
    private float touchStartX, touchStartY;
    private long touchStarted;
    private boolean touchMoved, touchRead;

    private static class Click {
        String url, title, source, key;

        Click(String url, String title, String source, String key) {
            this.url = url;
            this.title = Names.clickedTitle(title, url, Names.fromUrl(url));
            this.source = source;
            this.key = key;
        }
    }

    private final BroadcastReceiver updates = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Store.Item job = store.item(intent.getLongExtra("id", -1));
            if (!currentTab.equals("discover")) {
                renderList();
            } else if (job != null && (Store.WAITING.equals(job.status) || Store.AUTH.equals(job.status))
                    && verifyingId != job.id && verifyingId < 0) {
                beginVerification(job, Store.AUTH.equals(job.status));
            } else if (job != null && catalog != null && catalog.snapshot() != null
                    && job.source.equals(catalog.snapshot().optString("url"))) {
                if (Store.READY.equals(job.status)) {
                    catalog.downloadStatus(getString(R.string.downloads_finished_toast));
                } else if (Store.FAILED.equals(job.status)) {
                    catalog.downloadStatus(getString(R.string.downloads_failed_toast));
                }
            }
        }
    };

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = Store.get(this);
        if (!DownloadService.isAlive) store.recover();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        header = Ui.col(this);
        Ui.pad(header, 12, 4);
        root.addView(header);
        brand = Ui.heading(this, getString(R.string.library_title), 20);
        header.addView(brand);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        if (saved != null) {
            verifyingId = saved.getLong("waitingJob", -1);
            Store.Item job = store.item(verifyingId);
            if (job != null) {
                clicked = new Click(job.link, job.title, job.source, "job:" + job.id);
                allowedVerification = job.verification;
            }
        }
        buildBrowser(saved);

        LinearLayout nav = Ui.row(this);
        Ui.pad(nav, 10, 4);
        nav.setBackgroundColor(Ui.WHITE);
        String[][] tabs = {
                {"discover", getString(R.string.nav_discover)},
                {"library", getString(R.string.nav_library)},
                {"downloads", getString(R.string.nav_downloads)},
                {"settings", getString(R.string.nav_settings)}};
        for (String[] tab : tabs) {
            Button button = Ui.button(this, tab[1], () -> showTab(tab[0]));
            button.setTag(tab[0]);
            // Longer translations (Russian, Japanese) must stay on one line in equal-width tabs.
            button.setTextSize(13);
            button.setSingleLine(true);
            button.setEllipsize(android.text.TextUtils.TruncateAt.END);
            button.setPadding(0, 0, 0, 0);
            nav.addView(button, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        }
        root.addView(nav);

        String start = prefs.getString("startTab", "last");
        currentTab = start.equals("last") ? prefs.getString("tab", "discover") : start;
        if (saved != null) {
            currentTab = saved.getString("tab", "discover");
            browserVisible = saved.getBoolean("browserVisible", false);
        }
        catalog = new NativeCatalog(this, saved);
        content.addView(catalog.view, new FrameLayout.LayoutParams(-1, -1));
        catalog.start(saved);
        handleIntent(getIntent());
        showTab(currentTab);
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void buildBrowser(Bundle saved) {
        browserPane = Ui.col(this);
        content.addView(browserPane, new FrameLayout.LayoutParams(-1, -1));
        browserToolbar = Ui.row(this);
        Ui.pad(browserToolbar, 8, 4);
        Button nativeView = Ui.button(this, getString(R.string.browser_return_native),
                () -> {
                    showBrowser(false);
                    catalog.sync();
                });
        nativeView.setTag("returnNative");
        browserToolbar.addView(nativeView);
        browserStatus = Ui.text(this, "", 11, Ui.MUTED);
        browserStatus.setMaxLines(2);
        Ui.pad(browserStatus, 8, 0);
        browserToolbar.addView(browserStatus, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = Ui.button(this, "↻", () -> web.reload());
        refresh.setContentDescription(getString(R.string.action_refresh));
        browserToolbar.addView(refresh);
        browserPane.addView(browserToolbar);
        browserToolbar.setVisibility(View.GONE);

        webProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        webProgress.setMax(100);
        browserPane.addView(webProgress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 2)));

        web = new WebView(this);
        web.setBackgroundColor(Ui.WHITE);
        browserPane.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setTextZoom(prefs.getInt("webTextZoom", 100));
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        settings.setSupportMultipleWindows(true);

        web.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                browserEvents += "navigation(main=" + request.isForMainFrame() + ");";
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (Names.downloadUrl(url)) {
                    readClick(() -> {
                        if (verifyingId > 0 && !url.contains("action=vkpd_file")) {
                            receiveDownload(url, view.getSettings().getUserAgentString(), null, view.getUrl());
                        } else {
                            enqueueSelected(url, clicked, view.getUrl(),
                                    view.getSettings().getUserAgentString());
                        }
                    });
                    return true;
                }
                if (url.equals(allowedVerification) || verifyingId > 0) {
                    return !Names.https(url) && !url.startsWith("about:");
                }
                if (Names.https(url) || url.startsWith("about:")) return false;
                toast(getString(R.string.browser_insecure));
                return true;
            }

            public void onPageStarted(WebView view, String url, Bitmap icon) {
                if (catalog != null) catalog.pageStarted(url);
                if (clicked != null
                        && Objects.equals(Uri.parse(url).getHost(), "magazinelib.com")
                        && !url.contains("action=vkpd_file")
                        && !Objects.equals(url, clicked.source)) {
                    clearBinding();
                }
                if (verifyingId > 0 && Uri.parse(url).getHost() != null
                        && Uri.parse(url).getHost().equals("magazinelib.com")) {
                    Store.Item job = store.item(verifyingId);
                    if (job == null || !Objects.equals(url, job.source)) clearBinding();
                }
            }

            public void onPageCommitVisible(WebView view, String url) {
                installClickCapture(view);
            }

            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (Names.https(url)) prefs.edit().putString("lastUrl", url).apply();
                // Capture the exact anchor text before site handlers redirect or create a popup.
                installClickCapture(view);
                String title = view.getTitle();
                boolean challenge = title != null
                        && title.toLowerCase(Locale.ROOT).contains("just a moment");
                if (challenge) {
                    browserStatus.setText(R.string.browser_verify_hint);
                    if (verifyingId > 0) {
                        store.status(verifyingId, Store.AUTH, getString(R.string.browser_verify_hint));
                    }
                    if (catalog != null) catalog.challenge();
                } else {
                    browserStatus.setText(verifyingId > 0
                            ? R.string.browser_wait_hint : R.string.browser_tap_hint);
                }
                if (catalog != null) catalog.pageFinished(url);
            }

            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                String description = String.valueOf(error.getDescription());
                browserStatus.setText(getString(R.string.browser_network_error, description));
                if (catalog != null && CatalogRoutes.site(request.getUrl().toString())
                        && !Names.downloadUrl(request.getUrl().toString())) {
                    catalog.error(getString(R.string.browser_network_error_short));
                }
            }

            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                browserStatus.setText(R.string.browser_cert_error);
                if (catalog != null) catalog.error(getString(R.string.browser_cert_error));
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                webProgress.setProgress(progress);
                webProgress.setVisibility(progress >= 100 || !prefs.getBoolean("webLoading", true)
                        ? View.GONE : View.VISIBLE);
            }

            public boolean onCreateWindow(WebView parent, boolean dialog, boolean userGesture,
                                          Message message) {
                browserEvents += "popup(gesture=" + userGesture + ");";
                if (!userGesture) return false;
                String source = parent.getUrl();
                WebView popup = new WebView(MainActivity.this);
                WebSettings popupSettings = popup.getSettings();
                popupSettings.setJavaScriptEnabled(true);
                popupSettings.setDomStorageEnabled(true);
                popupSettings.setAllowFileAccess(false);
                popupSettings.setAllowContentAccess(false);
                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true);
                popup.setWebViewClient(new WebViewClient() {
                    public boolean shouldOverrideUrlLoading(WebView p, WebResourceRequest request) {
                        if (!request.isForMainFrame()) return false;
                        String url = request.getUrl().toString();
                        readClick(() -> {
                            if (Names.https(url)) {
                                if (Names.downloadUrl(url)) {
                                    enqueueSelected(url, clicked, source,
                                            parent.getSettings().getUserAgentString());
                                } else if ((verifyingId > 0 && isFileHost(url))
                                        || Objects.equals(Uri.parse(url).getHost(),
                                        Uri.parse(source).getHost())) {
                                    web.loadUrl(url);
                                }
                            }
                            p.destroy();
                        });
                        return true;
                    }
                });
                popup.setDownloadListener((url, userAgent, contentDisposition, mimeType, size) ->
                        readClick(() -> {
                            receiveDownload(url, userAgent, contentDisposition, source);
                            popup.destroy();
                        }));
                ((WebView.WebViewTransport) message.obj).setWebView(popup);
                message.sendToTarget();
                return true;
            }
        });
        configurePdfTaps();
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, size) ->
                readClick(() -> receiveDownload(url, userAgent, contentDisposition, web.getUrl())));
        if (saved == null || web.restoreState(saved) == null) {
            web.loadUrl(prefs.getBoolean("restoreWebPage", true)
                    ? prefs.getString("lastUrl", HOME) : HOME);
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void configurePdfTaps() {
        web.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                installClickCapture(web);
                int token = ++touchToken;
                touchStartX = event.getX();
                touchStartY = event.getY();
                touchStarted = event.getEventTime();
                touchMoved = false;
                touchRead = false;
                touchSelection = null;
                float x = event.getX() / Math.max(1, web.getWidth());
                float y = event.getY() / Math.max(1, web.getWidth());
                String script = "JSON.stringify((()=>{let v=visualViewport,"
                        + "a=document.elementFromPoint(v.offsetLeft+" + x
                        + "*v.width,v.offsetTop+" + y
                        + "*v.width)?.closest('a');if(a&&a.href.includes('action=vkpd_file'))"
                        + "return {url:a.href,title:a.innerText.trim()||a.title||'',source:location.href};"
                        + "return null;})())";
                web.evaluateJavascript(script, raw -> {
                    if (token != touchToken) return;
                    touchRead = true;
                    try {
                        Object value = new JSONTokener(raw).nextValue();
                        if (value instanceof String && !value.equals("null")) {
                            JSONObject object = new JSONObject((String) value);
                            if (Names.downloadUrl(object.optString("url"))) {
                                touchSelection = new Click(object.getString("url"),
                                        object.optString("title"), object.optString("source"),
                                        "touch:" + token);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });
            } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                touchMoved = true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (Math.abs(event.getX() - touchStartX) > Ui.dp(this, 12)
                        || Math.abs(event.getY() - touchStartY) > Ui.dp(this, 12)) {
                    touchMoved = true;
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    && !touchMoved && event.getEventTime() - touchStarted < 700) {
                int token = touchToken;
                handler.postDelayed(() -> finishPdfTap(token, 0), 180);
            }
            return false;
        });
    }

    private void finishPdfTap(int token, int attempt) {
        if (isDestroyed() || token != touchToken) return;
        if (!touchRead) {
            if (attempt < 10) handler.postDelayed(() -> finishPdfTap(token, attempt + 1), 100);
            return;
        }
        Click selection = touchSelection;
        if (selection == null || !Objects.equals(selection.source, web.getUrl())) return;
        Store.Item active = store.item(verifyingId);
        if (active != null && !selection.url.equals(active.link)) {
            verifyingId = -1;
            allowedVerification = "";
        }
        clicked = selection;
        // A site popup may stop DOM click listeners. Continue the user's tap through the
        // site's own preparation handler.
        web.evaluateJavascript("(()=>{let o=document.querySelector('#mzl-sl-ov');if(!o)return false;"
                + "let s=getComputedStyle(o);return s.display!=='none'&&s.visibility!=='hidden'"
                + "&&s.opacity!=='0';})()", shown -> {
            if (token != touchToken || isDestroyed()) return;
            if ("true".equals(shown)) {
                awaitPrepared(selection.url, selection.title, selection.source,
                        web.getSettings().getUserAgentString(), 0);
            } else {
                preparePageDownload(selection.url, selection.title, selection.source,
                        web.getSettings().getUserAgentString());
            }
        });
    }

    private void installClickCapture(WebView view) {
        view.evaluateJavascript("(()=>{if(window.__mlInstalled)return;window.__mlInstalled=true;"
                + "window.addEventListener('click',e=>{let a=e.target.closest?.('a');"
                + "if(a&&(a.href.includes('action=vkpd_file')||/\\.pdf(?:[?#]|$)/i.test(a.href)"
                + "||/\\.pdf$/i.test(a.innerText.trim()))){window.__mlClick={url:a.href,"
                + "title:a.innerText.trim()||a.title||'',source:location.href,"
                + "key:location.href+'#'+Date.now()};}},true);})()", null);
    }

    private boolean isFileHost(String url) {
        String host = Uri.parse(url).getHost();
        return host != null && (host.equals("vk.com") || host.equals("m.vk.com")
                || host.endsWith(".userapi.com") || host.endsWith(".vkuserphoto.ru"));
    }

    private void clearBinding() {
        verifyingId = -1;
        allowedVerification = "";
        clicked = null;
    }

    private void readClick(Runnable after) {
        web.evaluateJavascript("JSON.stringify(window.__mlClick||null)", raw -> {
            try {
                Object value = new JSONTokener(raw).nextValue();
                if (value instanceof String && !value.equals("null")) {
                    JSONObject object = new JSONObject((String) value);
                    String key = object.optString("key");
                    if (!key.equals(capturedKey) && Names.https(object.optString("url"))) {
                        Click selection = new Click(object.getString("url"),
                                object.optString("title"), object.optString("source", HOME), key);
                        Store.Item active = store.item(verifyingId);
                        if (active != null && !selection.url.equals(active.link)) {
                            verifyingId = -1;
                            allowedVerification = "";
                        }
                        clicked = selection;
                        capturedKey = key;
                    }
                }
            } catch (Exception ignored) {
            }
            after.run();
        });
    }

    private void enqueueSelected(String url, Click selection, String source, String userAgent) {
        if (selection != null) {
            enqueue(url, selection.title, selection.source, userAgent);
            Store.Item job = store.all().stream()
                    .filter(i -> url.equals(i.url)).findFirst().orElse(null);
            if (job != null) {
                ContentValues values = new ContentValues();
                values.put("link", selection.url);
                store.update(job.id, values);
            }
        } else {
            enqueue(url, Names.fromUrl(url), source, userAgent);
        }
    }

    void receiveDownload(String url, String userAgent, String disposition, String source) {
        browserEvents += "download;";
        if (!Names.https(url)) {
            toast(getString(R.string.downloads_choose_https_pdf));
            return;
        }
        String fallback = Names.fromUrl(url);
        if (disposition != null && !disposition.isEmpty()) {
            fallback = Names.title(URLUtil.guessFileName(url, disposition, "application/pdf"));
        }
        Store.Item job = store.item(verifyingId);
        if (job != null && (Store.WAITING.equals(job.status) || Store.AUTH.equals(job.status))
                && (clicked == null || clicked.url.equals(job.link))) {
            ContentValues values = new ContentValues();
            values.put("url", url);
            values.put("ua", userAgent);
            values.put("status", Store.QUEUED);
            values.put("error", "");
            store.update(job.id, values);
            String back = job.source;
            clearBinding();
            DownloadService.start(this);
            toast(getString(R.string.downloads_started, job.title));
            if (Names.https(back)) web.loadUrl(back);
        } else {
            if (clicked != null) enqueueSelected(url, clicked, source, userAgent);
            else enqueue(url, fallback, source, userAgent);
            clearBinding();
        }
    }

    void downloadOnPage() {
        String source = web.getUrl(), userAgent = web.getSettings().getUserAgentString();
        web.evaluateJavascript("JSON.stringify(Array.from(document.querySelectorAll('a'))"
                + ".filter(a=>a.innerText.toLowerCase().includes('.pdf')"
                + "||a.href.toLowerCase().split('?')[0].endsWith('.pdf'))"
                + ".map(a=>({url:a.href,title:a.innerText.trim()})))", raw -> {
            try {
                if (!Objects.equals(source, web.getUrl())) {
                    toast(getString(R.string.downloads_page_changed));
                    return;
                }
                Object value = new JSONTokener(raw).nextValue();
                JSONArray array = new JSONArray((String) value);
                ArrayList<String> urls = new ArrayList<>(), titles = new ArrayList<>();
                for (int n = 0; n < array.length(); n++) {
                    JSONObject item = array.getJSONObject(n);
                    String url = item.getString("url");
                    if (Names.https(url) && !urls.contains(url)) {
                        urls.add(url);
                        String title = item.optString("title");
                        titles.add(title.isEmpty() ? Names.fromUrl(url) : Names.title(title));
                    }
                }
                if (urls.isEmpty()) {
                    toast(getString(R.string.downloads_no_pdf_page));
                    return;
                }
                if (urls.size() == 1) {
                    preparePageDownload(urls.get(0), titles.get(0), source, userAgent);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.downloads_choose_pdf)
                            .setItems(titles.toArray(new String[0]), (dialog, which) ->
                                    preparePageDownload(urls.get(which), titles.get(which),
                                            source, userAgent))
                            .setNegativeButton(R.string.action_cancel, null)
                            .show();
                }
            } catch (Exception e) {
                toast(getString(R.string.downloads_pdf_link_later));
            }
        });
    }

    private void preparePageDownload(String url, String title, String source, String userAgent) {
        clearBinding();
        clicked = new Click(url, title, source, "native:" + System.currentTimeMillis());
        if (!url.contains("action=vkpd_file")) {
            enqueue(url, title, source, userAgent);
            return;
        }
        // Let the site's own preparation handler establish its short-lived download cookie.
        browserStatus.setText(R.string.downloads_status_preparing);
        if (catalog != null) catalog.downloadStatus(getString(R.string.downloads_status_preparing));
        String quoted = JSONObject.quote(url);
        web.evaluateJavascript("(()=>{let u=" + quoted + ",a=Array.from(document.querySelectorAll('a'))"
                + ".find(a=>a.href===u||new URL(a.getAttribute('data-mzl-href')||a.href,"
                + "location.href).href===u);if(a){a.focus({preventScroll:true});a.click();"
                + "return true;}return false;})()",
                value -> awaitPrepared(url, title, source, userAgent, 0));
    }

    private void awaitPrepared(String url, String title, String source, String userAgent, int count) {
        if (isFinishing() || isDestroyed() || !Objects.equals(source, web.getUrl())) return;
        web.evaluateJavascript("!!document.querySelector('#mzl-sl-ov.rdy')", ready -> {
            String cookies = CookieManager.getInstance().getCookie(url);
            if ("true".equals(ready) && cookies != null && cookies.contains("mzl_dlt=")) {
                CookieManager.getInstance().flush();
                enqueue(url, title, source, userAgent);
                browserStatus.setText(R.string.downloads_status_file_ready);
                if (catalog != null) {
                    catalog.downloadStatus(getString(R.string.downloads_status_file_ready));
                }
            } else if (count < 50) {
                handler.postDelayed(() -> awaitPrepared(url, title, source, userAgent, count + 1), 500);
            } else {
                browserStatus.setText(R.string.downloads_status_file_pending);
                if (catalog != null) {
                    catalog.downloadStatus(getString(R.string.downloads_status_file_pending));
                }
            }
        });
    }

    void enqueue(String url, String title, String source, String userAgent) {
        if (!Names.https(url)) {
            toast(getString(R.string.downloads_https_only));
            return;
        }
        Store.Item item = store.add(url, Names.https(source) ? source : HOME, title,
                prefs.getString("tree", ""), userAgent);
        if (Store.READY.equals(item.status)) {
            toast(getString(R.string.library_already));
            return;
        }
        if (Store.PAUSED.equals(item.status) || Store.FAILED.equals(item.status)
                || Store.AUTH.equals(item.status) || Store.WAITING.equals(item.status)) {
            store.status(item.id, Store.QUEUED, "");
        }
        notifyPermission();
        DownloadService.start(this);
        toast(getString(R.string.downloads_added, item.title));
        DownloadService.changed(this);
    }

    private void notifyPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("askedNotify", false)) {
            prefs.edit().putBoolean("askedNotify", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 21);
        }
    }

    public void showTab(String tab) {
        currentTab = tab;
        header.setVisibility(tab.equals("discover") ? View.GONE : View.VISIBLE);
        brand.setText(switch (tab) {
            case "library" -> getString(R.string.library_title);
            case "downloads" -> getString(R.string.downloads_title);
            case "settings" -> getString(R.string.settings_title);
            default -> "";
        });
        prefs.edit().putString("tab", tab).apply();
        browserPane.setVisibility(tab.equals("discover") ? View.VISIBLE : View.INVISIBLE);
        browserToolbar.setVisibility(browserVisible ? View.VISIBLE : View.GONE);
        if (catalog != null) {
            catalog.view.setVisibility(tab.equals("discover") && !browserVisible
                    ? View.VISIBLE : View.GONE);
        }
        LinearLayout nav = (LinearLayout) root.getChildAt(root.getChildCount() - 1);
        for (int n = 0; n < nav.getChildCount(); n++) {
            View button = nav.getChildAt(n);
            button.setBackground(Ui.bg(tab.equals(button.getTag()) ? 0xffe4efea : Ui.WHITE,
                    Ui.dp(this, 14)));
        }
        if (tab.equals("discover")) {
            if (scroll != null) scroll.setVisibility(View.GONE);
        } else {
            renderList();
        }
    }

    private void renderList() {
        if (scroll != null && !renderedTab.isEmpty()) {
            scrollPositions.put(renderedTab, scroll.getScrollY());
        }
        int y = scrollPositions.getOrDefault(currentTab, 0);
        renderedTab = currentTab;
        if (scroll != null) content.removeView(scroll);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        listPane = Ui.col(this);
        Ui.pad(listPane, 12, 10);
        scroll.addView(listPane);
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (currentTab.equals("library")) renderLibrary();
        else if (currentTab.equals("settings")) renderSettings();
        else renderDownloads();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void singleLine(TextView text) {
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    private Button more(String description, Runnable action) {
        Button button = Ui.button(this, "⋮", action);
        button.setContentDescription(description);
        button.setMinimumWidth(Ui.dp(this, 44));
        button.setMinWidth(Ui.dp(this, 44));
        button.setPadding(0, 0, 0, 0);
        button.setBackground(new android.graphics.drawable.InsetDrawable(
                Ui.bg(0xffe4efea, Ui.dp(this, 14)), Ui.dp(this, 8)));
        return button;
    }

    private String libraryLayout() {
        return prefs.getString("libraryLayout", "grid");
    }

    void chooseLibraryLayout() {
        choice(getString(R.string.library_layout),
                new String[]{getString(R.string.library_grid), getString(R.string.library_list)},
                new String[]{"grid", "list"}, "libraryLayout", libraryLayout());
    }

    private void renderLibrary() {
        List<Store.Item> ready = new ArrayList<>();
        for (Store.Item item : store.all()) {
            if (Store.READY.equals(item.status)
                    && (libraryQuery.isEmpty() || (item.title + " " + item.series)
                    .toLowerCase(Locale.ROOT)
                    .contains(libraryQuery.toLowerCase(Locale.ROOT)))) {
                ready.add(item);
            }
        }
        if (prefs.getString("librarySort", "recent").equals("title")) {
            ready.sort(Comparator.comparing(i -> i.title, String.CASE_INSENSITIVE_ORDER));
        }
        LinearLayout top = Ui.row(this);
        top.addView(Ui.text(this, getString(R.string.library_count, ready.size()), 12, Ui.MUTED),
                new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(Ui.button(this, (libraryLayout().equals("grid")
                ? getString(R.string.library_layout_value_grid)
                : getString(R.string.library_layout_value_list)) + " ▾",
                this::chooseLibraryLayout));
        listPane.addView(top);
        Ui.space(listPane, 8);
        if (ready.isEmpty()) {
            empty(getString(R.string.library_empty_title), getString(R.string.library_empty_desc));
            return;
        }
        Map<String, List<Store.Item>> groups = new LinkedHashMap<>();
        for (Store.Item item : ready) {
            groups.computeIfAbsent(prefs.getBoolean("libraryGrouped", true) ? item.series : "",
                    key -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, List<Store.Item>> entry : groups.entrySet()) {
            if (!entry.getKey().isEmpty()) {
                LinearLayout label = Ui.row(this);
                TextView name = Ui.heading(this, entry.getKey(), 14);
                name.setMaxLines(2);
                label.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                label.addView(Ui.text(this, getString(R.string.library_count, entry.getValue().size()),
                        11, Ui.MUTED));
                listPane.addView(label);
                Ui.space(listPane, 6);
            }
            if (libraryLayout().equals("grid")) renderGrid(entry.getValue());
            else for (Store.Item item : entry.getValue()) renderBookRow(item);
            Ui.space(listPane, 8);
        }
    }

    private String issueTitle(Store.Item item) {
        if (prefs.getBoolean("libraryGrouped", true) && item.title.startsWith(item.series)) {
            String suffix = item.title.substring(item.series.length())
                    .replaceFirst("^[\\s–—:-]+", "").trim();
            if (!suffix.isEmpty()) return suffix;
        }
        return item.title;
    }

    private void setCover(ImageView view, Store.Item item) {
        Bitmap bitmap = BitmapFactory.decodeFile(Files.cover(this, item.id).getPath());
        if (bitmap != null) view.setImageBitmap(bitmap);
        else view.setImageResource(R.drawable.ic_library);
        view.setContentDescription(getString(R.string.library_cover_desc, item.title));
    }

    private void renderBookRow(Store.Item item) {
        LinearLayout card = Ui.row(this);
        card.setTag("book:" + item.id);
        card.setContentDescription(item.title);
        card.setBackground(Ui.bg(Ui.WHITE, Ui.dp(this, 12)));
        Ui.pad(card, 8, 6);
        if (prefs.getBoolean("showCovers", true)) {
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            setCover(cover, item);
            int height = prefs.getBoolean("compactLibrary", true) ? 54 : 76;
            card.addView(cover, new LinearLayout.LayoutParams(
                    Ui.dp(this, height * 3 / 4), Ui.dp(this, height)));
        }
        LinearLayout info = Ui.col(this);
        Ui.pad(info, 8, 0);
        TextView title = Ui.heading(this, issueTitle(item), 14);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        StringBuilder meta = new StringBuilder(getString(R.string.library_meta_pages, item.pages))
                .append(" · ").append(Ui.size(item.done));
        if (item.page > 0) {
            meta.append(" · ").append(getString(R.string.library_meta_page, item.page + 1));
        }
        TextView metaView = Ui.text(this, meta, 11, Ui.MUTED);
        singleLine(metaView);
        info.addView(metaView);
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(more(getString(R.string.library_manage, item.title), () -> bookMenu(item)),
                new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 48)));
        card.setOnClickListener(v -> openReader(item));
        card.setOnLongClickListener(v -> {
            bookMenu(item);
            return true;
        });
        listPane.addView(card);
        Ui.space(listPane, 6);
    }

    private void renderGrid(List<Store.Item> books) {
        int screenDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int columns = Math.min(prefs.getInt("gridColumns", 2), Math.max(2, screenDp / 110));
        for (int start = 0; start < books.size(); start += columns) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.TOP);
            for (int col = 0; col < columns; col++) {
                if (start + col >= books.size()) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
                    continue;
                }
                Store.Item item = books.get(start + col);
                LinearLayout card = Ui.col(this);
                card.setTag("book:" + item.id);
                card.setContentDescription(item.title);
                card.setBackground(Ui.bg(Ui.WHITE, Ui.dp(this, 12)));
                Ui.pad(card, 7, 6);
                int coverHeight = prefs.getBoolean("showCovers", true)
                        ? (prefs.getBoolean("compactLibrary", true) ? 136 : 176) : 44;
                FrameLayout art = new FrameLayout(this);
                if (prefs.getBoolean("showCovers", true)) {
                    ImageView cover = new ImageView(this);
                    cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    setCover(cover, item);
                    art.addView(cover, new FrameLayout.LayoutParams(-1, -1));
                }
                FrameLayout.LayoutParams menu = new FrameLayout.LayoutParams(
                        Ui.dp(this, 44), Ui.dp(this, 44), Gravity.END | Gravity.TOP);
                Button actions = more(getString(R.string.library_manage, item.title),
                        () -> bookMenu(item));
                actions.setBackground(new android.graphics.drawable.InsetDrawable(
                        Ui.bg(0xeef7f5ee, Ui.dp(this, 14)), Ui.dp(this, 8)));
                art.addView(actions, menu);
                card.addView(art, new LinearLayout.LayoutParams(-1, Ui.dp(this, coverHeight)));
                TextView title = Ui.heading(this, issueTitle(item), 12);
                title.setLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(title);
                String meta = getString(R.string.library_meta_pages, item.pages)
                        + (item.page > 0 ? " · " + getString(R.string.library_meta_page, item.page + 1) : "");
                TextView metaView = Ui.text(this, meta, 10, Ui.MUTED);
                singleLine(metaView);
                card.addView(metaView);
                card.setOnClickListener(v -> openReader(item));
                card.setOnLongClickListener(v -> {
                    bookMenu(item);
                    return true;
                });
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -2, 1);
                cell.setMargins(0, 0, col < columns - 1 ? Ui.dp(this, 6) : 0, 0);
                row.addView(card, cell);
            }
            listPane.addView(row);
            Ui.space(listPane, 6);
        }
    }

    private String downloadState(Store.Item item) {
        return switch (item.status) {
            case Store.READY -> getString(R.string.downloads_state_ready);
            case Store.RUNNING -> item.total > 0
                    ? (item.done * 100 / item.total) + "%"
                    : getString(R.string.downloads_state_running);
            case Store.QUEUED -> getString(R.string.downloads_state_queued);
            case Store.AUTH -> getString(R.string.downloads_state_auth);
            case Store.WAITING -> getString(R.string.downloads_state_waiting);
            case Store.PAUSED -> getString(R.string.downloads_state_paused);
            default -> getString(R.string.downloads_state_failed);
        };
    }

    private String itemDirectory(Store.Item item) {
        if (item.url.isEmpty()) return getString(R.string.downloads_location_original);
        if (item.tree.isEmpty()) return getString(R.string.downloads_location_default);
        try {
            return getString(R.string.downloads_location_internal,
                    DocumentsContract.getTreeDocumentId(Uri.parse(item.tree))
                            .replace("primary:", ""));
        } catch (Exception e) {
            return getString(R.string.downloads_location_selected);
        }
    }

    private void renderDownloads() {
        List<Store.Item> history = store.history();
        boolean canClear = history.stream().anyMatch(i ->
                Store.READY.equals(i.status) || Store.FAILED.equals(i.status));
        LinearLayout folder = Ui.row(this);
        TextView location = Ui.text(this,
                getString(R.string.downloads_save_to, directoryLabel()), 12, Ui.GREEN);
        singleLine(location);
        location.setMinHeight(Ui.dp(this, 44));
        location.setGravity(Gravity.CENTER_VERTICAL);
        location.setOnClickListener(v -> chooseFolder());
        folder.addView(location, new LinearLayout.LayoutParams(0, -2, 1));
        folder.addView(Ui.button(this, (prefs.getBoolean("showCompleted", true)
                        ? getString(R.string.downloads_show_all)
                        : getString(R.string.downloads_show_unfinished)) + " ▾",
                () -> toggle(getString(R.string.downloads_show_completed), "showCompleted", true)));
        Button clear = Ui.button(this, getString(R.string.downloads_clear),
                this::clearDownloadHistory);
        clear.setTag("clearDownloadHistory");
        clear.setContentDescription(getString(R.string.downloads_clear_desc));
        clear.setTextSize(12);
        clear.setEnabled(canClear);
        clear.setAlpha(canClear ? 1f : .45f);
        LinearLayout.LayoutParams clearLayout = new LinearLayout.LayoutParams(-2, Ui.dp(this, 44));
        clearLayout.setMargins(Ui.dp(this, 6), 0, 0, 0);
        folder.addView(clear, clearLayout);
        listPane.addView(folder);
        Ui.space(listPane, 4);

        List<Store.Item> items = new ArrayList<>();
        for (Store.Item item : history) {
            if (prefs.getBoolean("showCompleted", true) || !Store.READY.equals(item.status)) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            empty(getString(R.string.downloads_empty_title), getString(R.string.downloads_empty_desc));
            return;
        }
        for (Store.Item item : items) {
            LinearLayout cell = Ui.col(this);
            cell.setTag("download:" + item.id);
            cell.setBackground(Ui.bg(Ui.WHITE, Ui.dp(this, 10)));
            LinearLayout line = Ui.row(this);
            Ui.pad(line, 8, 0);
            line.setMinimumHeight(Ui.dp(this, 48));
            LinearLayout info = Ui.col(this);
            TextView title = Ui.heading(this, item.title, 13);
            singleLine(title);
            title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            info.addView(title);
            if (prefs.getBoolean("downloadSizes", false)) {
                TextView size = Ui.text(this, Ui.size(item.done)
                        + (item.total > 0 ? " / " + Ui.size(item.total) : ""), 10, Ui.MUTED);
                singleLine(size);
                info.addView(size);
            }
            line.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            TextView status = Ui.text(this, downloadState(item), 11,
                    Store.FAILED.equals(item.status) ? 0xffad493c : Ui.GREEN);
            status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            singleLine(status);
            line.addView(status, new LinearLayout.LayoutParams(Ui.dp(this, 76), -2));
            Button action = more(getString(R.string.downloads_details) + " " + item.title,
                    () -> downloadMenu(item));
            action.setTag("downloadMenu:" + item.id);
            action.setBackgroundColor(Color.TRANSPARENT);
            line.addView(action, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 48)));
            cell.addView(line);
            if (Store.RUNNING.equals(item.status) || Store.QUEUED.equals(item.status)) {
                ProgressBar progress = new ProgressBar(this, null,
                        android.R.attr.progressBarStyleHorizontal);
                progress.setMax(100);
                progress.setIndeterminate(item.total <= 0);
                if (item.total > 0) progress.setProgress((int) (item.done * 100 / item.total));
                cell.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 2)));
            }
            cell.setOnClickListener(v -> downloadDetails(store.item(item.id)));
            listPane.addView(cell);
            Ui.space(listPane, 4);
        }
    }

    private void clearDownloadHistory() {
        int count = store.clearDownloadHistory();
        renderList();
        toast(count > 0 ? getString(R.string.downloads_cleared, count)
                : getString(R.string.downloads_nothing_to_clear));
    }

    private void downloadMenu(Store.Item item) {
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.downloads_details));
        actions.add(() -> downloadDetails(store.item(item.id)));
        if (Store.READY.equals(item.status)) {
            labels.add(getString(R.string.downloads_read));
            actions.add(() -> openReader(item));
            labels.add(getString(R.string.downloads_delete_pdf));
            actions.add(() -> deleteBook(item));
        } else {
            if (Store.RUNNING.equals(item.status) || Store.QUEUED.equals(item.status)) {
                labels.add(getString(R.string.downloads_pause));
                actions.add(() -> {
                    store.status(item.id, Store.PAUSED, "");
                    renderList();
                });
            } else {
                labels.add(Store.PAUSED.equals(item.status)
                        ? getString(R.string.downloads_resume)
                        : getString(R.string.downloads_retry));
                actions.add(() -> retry(item));
            }
            if (Store.WAITING.equals(item.status) || Store.AUTH.equals(item.status)) {
                labels.add(Store.AUTH.equals(item.status)
                        ? getString(R.string.downloads_open_verify)
                        : getString(R.string.downloads_open_page));
                actions.add(() -> verify(item));
            }
            labels.add(getString(R.string.downloads_remove));
            actions.add(() -> new AlertDialog.Builder(this)
                    .setTitle(R.string.downloads_remove_title)
                    .setMessage(R.string.downloads_remove_message)
                    .setPositiveButton(R.string.downloads_remove_action, (dialog, which) -> {
                        store.status(item.id, Store.PAUSED, "");
                        removeBook(store.item(item.id), true);
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show());
        }
        if (Names.https(item.source)) {
            labels.add(getString(R.string.downloads_open_source));
            actions.add(() -> {
                clearBinding();
                showTab("discover");
                web.loadUrl(item.source);
            });
        }
        new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void downloadDetails(Store.Item item) {
        if (item == null) return;
        LinearLayout detail = Ui.col(this);
        Ui.pad(detail, 18, 12);
        String size = Ui.size(item.done)
                + (item.total > 0 ? " / " + Ui.size(item.total) : "");
        StringBuilder status = new StringBuilder(
                getString(R.string.downloads_details_status, downloadState(item), size));
        if (item.pages > 0) {
            status.append(" · ").append(getString(R.string.library_meta_pages, item.pages));
        }
        detail.addView(Ui.text(this, status, 13, Ui.GREEN));
        detail.addView(Ui.text(this,
                getString(R.string.downloads_details_location, itemDirectory(item)), 13, Ui.INK));
        if (!item.error.isEmpty()) {
            detail.addView(Ui.text(this,
                    getString(R.string.downloads_details_note, item.error), 12, Ui.MUTED));
        }
        String address = item.link.isEmpty() ? item.url : item.link;
        if (!item.url.equals(address)) {
            TextView resolved = Ui.text(this,
                    getString(R.string.downloads_details_resolved, item.url), 12, Ui.MUTED);
            resolved.setTextIsSelectable(true);
            Ui.pad(resolved, 0, 8);
            detail.addView(resolved);
        }
        TextView url = Ui.text(this, getString(R.string.downloads_details_url, address), 12, Ui.MUTED);
        url.setTextIsSelectable(true);
        Ui.pad(url, 0, 8);
        detail.addView(url);
        ScrollView body = new ScrollView(this);
        body.addView(detail);
        new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setView(body)
                .setPositiveButton(R.string.action_close, null)
                .setNeutralButton(R.string.downloads_copy, (dialog, which) -> {
                    getSystemService(ClipboardManager.class).setPrimaryClip(
                            ClipData.newPlainText(getString(R.string.downloads_clip_label), address));
                    toast(getString(R.string.downloads_copied));
                })
                .show();
    }

    private void empty(String title, String description) {
        Ui.space(listPane, 40);
        listPane.addView(Ui.heading(this, title, 21));
        Ui.space(listPane, 10);
        TextView text = Ui.text(this, description, 15, Ui.MUTED);
        text.setLineSpacing(Ui.dp(this, 5), 1);
        listPane.addView(text);
        Ui.space(listPane, 16);
        listPane.addView(Ui.button(this, getString(R.string.library_go_discover),
                () -> showTab("discover")));
    }

    private void retry(Store.Item item) {
        ContentValues values = new ContentValues();
        values.put("status", Store.QUEUED);
        values.put("error", "");
        values.put("tree", prefs.getString("tree", ""));
        store.update(item.id, values);
        DownloadService.start(this);
        renderList();
    }

    void verify(Store.Item item) {
        beginVerification(item, true);
    }

    private void beginVerification(Store.Item item, boolean reveal) {
        if (reveal) browserVisible = true;
        showTab("discover");
        verifyingId = item.id;
        clicked = new Click(item.link, item.title, item.source, "job:" + item.id);
        String target = item.verification.isEmpty() ? item.url : item.verification;
        allowedVerification = target;
        String message = Store.AUTH.equals(item.status)
                ? getString(R.string.downloads_status_verifying)
                : getString(R.string.downloads_status_waiting);
        browserStatus.setText(message);
        if (catalog != null) catalog.downloadStatus(message);
        Map<String, String> headers = new HashMap<>();
        if (Names.https(item.source)) headers.put("Referer", item.source);
        web.loadUrl(target, headers);
    }

    void showBrowser(boolean show) {
        browserVisible = show;
        showTab("discover");
    }

    void showVerificationBrowser(boolean show) {
        browserVisible = show;
        if (currentTab.equals("discover")) showTab("discover");
    }

    void loadCatalogPage(String url) {
        clearBinding();
        browserVisible = false;
        web.loadUrl(url);
        if (currentTab.equals("discover")) showTab("discover");
    }

    void prepareNativeDownload(String url, String title, String source) {
        if (!source.equals(web.getUrl())) return;
        Store.Item existing = store.findDownload(url);
        if (existing != null && Store.READY.equals(existing.status)) {
            catalog.downloadStatus(getString(R.string.library_already_offline));
            toast(getString(R.string.library_already));
            return;
        }
        String userAgent = web.getSettings().getUserAgentString();
        if (!url.contains("action=vkpd_file")) {
            enqueue(url, title, source, userAgent);
            return;
        }
        clearBinding();
        clicked = new Click(url, title, source, "native:" + System.currentTimeMillis());
        catalog.downloadStatus(getString(R.string.downloads_status_preparing));
        String quoted = JSONObject.quote(url);
        // The site's focus warm-up establishes its ordinary download cookie in this session.
        web.evaluateJavascript("(()=>{let u=" + quoted + ",a=Array.from(document.querySelectorAll('a'))"
                + ".find(a=>a.href===u||new URL(a.getAttribute('data-mzl-href')||a.href,"
                + "location.href).href===u);if(!a)return false;a.focus({preventScroll:true});"
                + "return true;})()", found -> {
            if ("true".equals(found)) {
                awaitNativeSession(url, title, source, userAgent, 0);
            } else {
                catalog.downloadStatus(getString(R.string.catalog_not_ready));
            }
        });
    }

    private void awaitNativeSession(String url, String title, String source,
                                    String userAgent, int count) {
        if (isDestroyed() || !source.equals(web.getUrl()) || clicked == null
                || !clicked.url.equals(url)) return;
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && cookies.contains("mzl_dlt=")) {
            CookieManager.getInstance().flush();
            enqueue(url, title, source, userAgent);
            catalog.downloadStatus(getString(R.string.downloads_status_file_ready));
            return;
        }
        // Use the same preparation request advertised by the loaded site's own script if focus
        // did not start it.
        if (count == 4) {
            String quoted = JSONObject.quote(url);
            web.evaluateJavascript("(()=>{let u=new URL(" + quoted + ");"
                    + "if(u.origin!==location.origin)return;u.search='?action=mzl_dlt';"
                    + "fetch(u.href,{credentials:'same-origin',cache:'no-store'})"
                    + ".catch(()=>{});})()", null);
        }
        if (count < 40) {
            handler.postDelayed(() -> awaitNativeSession(url, title, source, userAgent, count + 1), 400);
        } else {
            catalog.downloadStatus(getString(R.string.downloads_status_prepare_timeout));
        }
    }

    NativeCatalog testCatalog() {
        return catalog;
    }

    private void openReader(Store.Item item) {
        startActivity(new Intent(this, ReaderActivity.class).putExtra("id", item.id));
    }

    private void bookMenu(Store.Item item) {
        String[] labels = {
                getString(R.string.library_menu_rename),
                getString(R.string.library_menu_group),
                getString(R.string.library_menu_similar),
                getString(R.string.library_menu_location),
                getString(R.string.library_menu_delete)};
        new AlertDialog.Builder(this).setTitle(item.title).setItems(labels, (dialog, which) -> {
            if (which == 0 || which == 1) {
                EditText input = new EditText(this);
                input.setText(which == 0 ? item.title : item.series);
                Ui.pad(input, 20, 8);
                new AlertDialog.Builder(this)
                        .setTitle(which == 0
                                ? R.string.library_rename_title : R.string.library_group_title)
                        .setView(input)
                        .setPositiveButton(R.string.action_save, (d, w) -> {
                            String name = Names.title(input.getText().toString());
                            fileWorker.execute(() -> {
                                try {
                                    if (which == 0) {
                                        Files.rename(this, item, name);
                                    } else {
                                        ContentValues values = new ContentValues();
                                        values.put("series", name);
                                        store.update(item.id, values);
                                    }
                                    handler.post(() -> {
                                        if (!isDestroyed()) renderList();
                                    });
                                } catch (Exception e) {
                                    handler.post(() -> toast(e.getMessage()));
                                }
                            });
                        })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            } else if (which == 2) {
                showBrowser(false);
                catalog.load(HOME + "?s=" + Uri.encode(item.series));
            } else if (which == 3) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.library_file_location)
                        .setMessage(Uri.decode(item.uri))
                        .setPositiveButton(R.string.action_close, null)
                        .show();
            } else {
                deleteBook(item);
            }
        }).show();
    }

    private void deleteBook(Store.Item item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.library_delete_title)
                .setMessage(getString(R.string.library_delete_message, item.title))
                .setPositiveButton(R.string.library_delete_file, (dialog, which) -> removeBook(item, true))
                .setNeutralButton(R.string.library_remove_only, (dialog, which) -> removeBook(item, false))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void removeBook(Store.Item item, boolean delete) {
        fileWorker.execute(() -> {
            try {
                Files.remove(this, item, delete);
                handler.post(() -> {
                    if (!isDestroyed()) {
                        renderList();
                        toast(delete ? getString(R.string.library_deleted)
                                : getString(R.string.library_removed));
                    }
                });
            } catch (Exception e) {
                handler.post(() -> toast(e.getMessage()));
            }
        });
    }

    void settings() {
        showTab("settings");
    }

    private String directoryLabel() {
        String tree = prefs.getString("tree", "");
        if (tree.isEmpty()) return getString(R.string.downloads_location_default);
        try {
            return getString(R.string.downloads_location_internal,
                    DocumentsContract.getTreeDocumentId(Uri.parse(tree)).replace("primary:", ""));
        } catch (Exception e) {
            return getString(R.string.downloads_location_selected);
        }
    }

    private void settingRow(String title, String detail, Runnable action) {
        LinearLayout row = Ui.row(this);
        Ui.pad(row, 12, 8);
        row.setBackground(Ui.bg(Ui.WHITE, Ui.dp(this, 10)));
        LinearLayout text = Ui.col(this);
        text.addView(Ui.heading(this, title, 14));
        if (detail != null) {
            TextView value = Ui.text(this, detail, 11, Ui.MUTED);
            value.setMaxLines(2);
            text.addView(value);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.text(this, "›", 18, Ui.MUTED));
        row.setOnClickListener(v -> action.run());
        row.setMinimumHeight(Ui.dp(this, 48));
        listPane.addView(row);
        Ui.space(listPane, 4);
    }

    private void section(String title) {
        Ui.space(listPane, 8);
        listPane.addView(Ui.heading(this, title, 13));
        Ui.space(listPane, 6);
    }

    private void choice(String title, String[] labels, String[] values, String key, String current) {
        int checked = Arrays.asList(values).indexOf(current);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    prefs.edit().putString(key, values[which]).apply();
                    dialog.dismiss();
                    renderList();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void toggle(String title, String key, boolean defaultValue) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(new String[]{
                        getString(R.string.action_on), getString(R.string.action_off)},
                        prefs.getBoolean(key, defaultValue) ? 0 : 1, (dialog, which) -> {
                            prefs.edit().putBoolean(key, which == 0).apply();
                            dialog.dismiss();
                            renderList();
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void renderSettings() {
        section(getString(R.string.settings_downloads_section));
        settingRow(getString(R.string.settings_download_dir), directoryLabel(), this::chooseFolder);
        if (!prefs.getString("tree", "").isEmpty()) {
            settingRow(getString(R.string.settings_reset_dir),
                    getString(R.string.downloads_location_default), () -> {
                        prefs.edit().remove("tree").apply();
                        renderList();
                    });
        }
        settingRow(getString(R.string.settings_task_display),
                prefs.getBoolean("downloadSizes", false)
                        ? getString(R.string.settings_task_display_sizes)
                        : getString(R.string.settings_task_display_title),
                () -> toggle(getString(R.string.downloads_show_sizes), "downloadSizes", false));
        settingRow(getString(R.string.settings_show_completed),
                prefs.getBoolean("showCompleted", true)
                        ? getString(R.string.action_on) : getString(R.string.action_off),
                () -> toggle(getString(R.string.downloads_show_completed), "showCompleted", true));

        section(getString(R.string.settings_library_section));
        settingRow(getString(R.string.settings_layout), libraryLayout().equals("grid")
                ? getString(R.string.library_grid) : getString(R.string.library_list),
                this::chooseLibraryLayout);
        settingRow(getString(R.string.settings_columns),
                getString(R.string.library_columns_value, prefs.getInt("gridColumns", 2)),
                () -> new AlertDialog.Builder(this)
                        .setTitle(R.string.settings_columns)
                        .setSingleChoiceItems(new String[]{
                                getString(R.string.library_columns_value, 2),
                                getString(R.string.library_columns_value, 3)},
                                prefs.getInt("gridColumns", 2) - 2, (d, n) -> {
                                    prefs.edit().putInt("gridColumns", n + 2).apply();
                                    d.dismiss();
                                    renderList();
                                }).show());
        settingRow(getString(R.string.settings_group), prefs.getBoolean("libraryGrouped", true)
                ? getString(R.string.library_group_on) : getString(R.string.library_group_off),
                () -> toggle(getString(R.string.settings_group), "libraryGrouped", true));
        settingRow(getString(R.string.settings_covers), prefs.getBoolean("showCovers", true)
                ? getString(R.string.action_on) : getString(R.string.action_off),
                () -> toggle(getString(R.string.library_covers), "showCovers", true));
        settingRow(getString(R.string.settings_compact), prefs.getBoolean("compactLibrary", true)
                ? getString(R.string.action_on) : getString(R.string.action_off),
                () -> toggle(getString(R.string.library_compact), "compactLibrary", true));
        settingRow(getString(R.string.settings_sort),
                prefs.getString("librarySort", "recent").equals("recent")
                        ? getString(R.string.library_sort_recent)
                        : getString(R.string.library_sort_title),
                () -> choice(getString(R.string.library_sort), new String[]{
                        getString(R.string.library_sort_recent),
                        getString(R.string.library_sort_title)},
                        new String[]{"recent", "title"}, "librarySort",
                        prefs.getString("librarySort", "recent")));

        section(getString(R.string.settings_reading_section));
        settingRow(getString(R.string.settings_reader_mode),
                prefs.getString("readerMode", "single").equals("continuous")
                        ? getString(R.string.reader_continuous) : getString(R.string.reader_single),
                () -> choice(getString(R.string.settings_reader_mode), new String[]{
                        getString(R.string.reader_single),
                        getString(R.string.reader_continuous)},
                        new String[]{"single", "continuous"}, "readerMode",
                        prefs.getString("readerMode", "single")));

        section(getString(R.string.settings_web_section));
        settingRow(getString(R.string.settings_web_zoom),
                getString(R.string.settings_zoom_value, prefs.getInt("webTextZoom", 100)),
                () -> new AlertDialog.Builder(this)
                        .setTitle(R.string.settings_web_zoom)
                        .setSingleChoiceItems(new String[]{
                                getString(R.string.settings_zoom_value, 80),
                                getString(R.string.settings_zoom_value, 100),
                                getString(R.string.settings_zoom_value, 120),
                                getString(R.string.settings_zoom_value, 140)},
                                Arrays.asList(80, 100, 120, 140)
                                        .indexOf(prefs.getInt("webTextZoom", 100)), (d, n) -> {
                                    int value = new int[]{80, 100, 120, 140}[n];
                                    prefs.edit().putInt("webTextZoom", value).apply();
                                    web.getSettings().setTextZoom(value);
                                    d.dismiss();
                                    renderList();
                                }).show());
        settingRow(getString(R.string.settings_web_progress),
                prefs.getBoolean("webLoading", true)
                        ? getString(R.string.settings_web_loading_show)
                        : getString(R.string.settings_web_loading_hide),
                () -> toggle(getString(R.string.settings_web_progress), "webLoading", true));
        settingRow(getString(R.string.settings_web_restore),
                prefs.getBoolean("restoreWebPage", true)
                        ? getString(R.string.action_on)
                        : getString(R.string.settings_web_restore_off),
                () -> toggle(getString(R.string.settings_web_restore), "restoreWebPage", true));
        String start = prefs.getString("startTab", "last");
        String[] starts = {"last", "discover", "library", "downloads"};
        String[] startLabels = {
                getString(R.string.settings_start_last),
                getString(R.string.nav_discover),
                getString(R.string.nav_library),
                getString(R.string.nav_downloads)};
        int index = Arrays.asList(starts).indexOf(start);
        settingRow(getString(R.string.settings_start_page), startLabels[Math.max(0, index)],
                () -> choice(getString(R.string.settings_start_page), startLabels, starts,
                        "startTab", start));
        settingRow(getString(R.string.settings_open_home), null, () -> {
            showBrowser(false);
            catalog.load(HOME);
        });
        settingRow(getString(R.string.settings_refresh_web), null, () -> {
            showTab("discover");
            web.reload();
        });
        settingRow(getString(R.string.settings_open_url), null, this::enterUrl);

        section(getString(R.string.settings_language_section));
        settingRow(getString(R.string.settings_language), languageLabel(), this::chooseLanguage);

        section(getString(R.string.settings_about_section));
        settingRow(getString(R.string.settings_about),
                getString(R.string.settings_about_detail,
                        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                () -> startActivity(new Intent(this, AboutActivity.class)));
    }

    private String languageLabel() {
        String tag = LocaleHelper.current(this);
        if (tag.startsWith("zh")) return getString(R.string.language_chinese);
        if (tag.startsWith("ja")) return getString(R.string.language_japanese);
        if (tag.startsWith("ko")) return getString(R.string.language_korean);
        if (tag.startsWith("ru")) return getString(R.string.language_russian);
        if (tag.startsWith("en")) return getString(R.string.language_english);
        return getString(R.string.language_system);
    }

    private void chooseLanguage() {
        String[] tags = {LocaleHelper.SYSTEM, "en", "zh-CN", "ja", "ko", "ru"};
        String[] labels = {
                getString(R.string.language_system),
                getString(R.string.language_english),
                getString(R.string.language_chinese),
                getString(R.string.language_japanese),
                getString(R.string.language_korean),
                getString(R.string.language_russian)};
        String current = LocaleHelper.current(this);
        int checked = 0;
        for (int n = 1; n < tags.length; n++) {
            if (current.startsWith(tags[n])) checked = n;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    LocaleHelper.set(this, tags[which]);
                    recreate();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void enterUrl() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.settings_enter_url_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_open_url)
                .setView(input)
                .setPositiveButton(R.string.settings_open_url, (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (Names.https(url)) {
                        clearBinding();
                        showTab("discover");
                        web.loadUrl(url);
                    } else {
                        toast(getString(R.string.settings_enter_url_invalid));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, 10);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 10 && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                prefs.edit().putString("tree", uri.toString()).apply();
                toast(getString(R.string.downloads_saved_to));
                if (!currentTab.equals("discover")) renderList();
            } catch (Exception e) {
                toast(getString(R.string.downloads_folder_failed));
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
        out.putString("tab", currentTab);
        out.putLong("waitingJob", verifyingId);
        out.putBoolean("browserVisible", browserVisible);
        catalog.save(out);
        CookieManager.getInstance().flush();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        showTab(currentTab);
    }

    private void handleIntent(Intent intent) {
        if (intent.hasExtra("tab")) currentTab = intent.getStringExtra("tab");
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String url = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (Names.https(url)) {
                currentTab = "discover";
                if (CatalogRoutes.site(url) && !Names.downloadUrl(url)) catalog.load(url);
                else {
                    browserVisible = true;
                    web.loadUrl(url);
                }
            }
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updates, new IntentFilter(DownloadService.EVENT),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updates, new IntentFilter(DownloadService.EVENT));
        }
        registered = true;
        if (!currentTab.equals("discover")) renderList();
        web.onResume();
    }

    @Override protected void onPause() {
        CookieManager.getInstance().flush();
        if (registered) {
            unregisterReceiver(updates);
            registered = false;
        }
        web.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        fileWorker.shutdown();
        if (catalog != null) catalog.close();
        web.destroy();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (currentTab.equals("discover") && browserVisible) showBrowser(false);
        else if (currentTab.equals("discover") && catalog.back()) return;
        else if (!currentTab.equals("discover")) showTab("discover");
        else super.onBackPressed();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    WebView testWeb() {
        return web;
    }

    String testEvents() {
        return browserEvents;
    }
}
