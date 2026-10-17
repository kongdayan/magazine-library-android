package com.magazinelibrary.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String HOME="https://magazinelib.com/";
    private Store store;private android.content.SharedPreferences prefs;
    private LinearLayout root,header,browserPane,listPane;private TextView brand;private FrameLayout content;private ScrollView scroll;
    private WebView web;private TextView browserStatus;private ProgressBar webProgress;
    private NativeCatalog catalog;private LinearLayout browserToolbar;private boolean browserVisible;
    private final Map<String,Integer> scrollPositions=new HashMap<>();private String renderedTab="";private String currentTab="discover",libraryQuery="";private long verifyingId=-1;private String allowedVerification="";private String browserEvents="";
    private final Handler handler=new Handler(Looper.getMainLooper());private boolean registered;private final java.util.concurrent.ExecutorService fileWorker=Executors.newSingleThreadExecutor();
    private Click clicked,touchSelection;private String capturedKey="";private int touchToken;private float touchStartX,touchStartY;private long touchStarted;private boolean touchMoved,touchRead;
    private static class Click {String url,title,source,key;Click(String u,String t,String s,String k){url=u;title=Names.clickedTitle(t,u,Names.fromUrl(u));source=s;key=k;}}
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        Store.Item job=store.item(i.getLongExtra("id",-1));
        if(!currentTab.equals("discover"))renderList();
        else if(job!=null&&(Store.WAITING.equals(job.status)||Store.AUTH.equals(job.status))&&verifyingId!=job.id&&verifyingId<0)beginVerification(job,Store.AUTH.equals(job.status));
        else if(job!=null&&catalog!=null&&catalog.snapshot()!=null&&job.source.equals(catalog.snapshot().optString("url"))){if(Store.READY.equals(job.status))catalog.downloadStatus("下载完成，已保存到书库");else if(Store.FAILED.equals(job.status))catalog.downloadStatus("下载未完成，可在下载页查看原因并重试");}
    }};
    public void onCreate(Bundle saved) {
        super.onCreate(saved);store=Store.get(this);if(!DownloadService.isAlive)store.recover();prefs=getSharedPreferences("settings",MODE_PRIVATE);
        root=Ui.col(this);root.setBackgroundColor(Ui.BG);setContentView(root);
        header=Ui.col(this);Ui.pad(header,12,4);root.addView(header);
        brand=Ui.heading(this,"书库",20);header.addView(brand);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        if(saved!=null){verifyingId=saved.getLong("waitingJob",-1);Store.Item job=store.item(verifyingId);if(job!=null){clicked=new Click(job.link,job.title,job.source,"job:"+job.id);allowedVerification=job.verification;}}
        buildBrowser(saved);
        LinearLayout nav=Ui.row(this);Ui.pad(nav,10,4);nav.setBackgroundColor(Ui.WHITE);
        for(String[] pair:new String[][]{{"discover","发现"},{"library","书库"},{"downloads","下载"},{"settings","设置"}}){Button b=Ui.button(this,pair[1],()->showTab(pair[0]));b.setTag(pair[0]);nav.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));}root.addView(nav);
        String start=prefs.getString("startTab","last");currentTab=start.equals("last")?prefs.getString("tab","discover"):start;if(saved!=null){currentTab=saved.getString("tab","discover");browserVisible=saved.getBoolean("browserVisible",false);}
        catalog=new NativeCatalog(this,saved);content.addView(catalog.view,new FrameLayout.LayoutParams(-1,-1));catalog.start(saved);handleIntent(getIntent());showTab(currentTab);
    }
    private void buildBrowser(Bundle saved){
        browserPane=Ui.col(this);content.addView(browserPane,new FrameLayout.LayoutParams(-1,-1));
        browserToolbar=Ui.row(this);Ui.pad(browserToolbar,8,4);Button nativeView=Ui.button(this,"返回原生",()->{showBrowser(false);catalog.sync();});nativeView.setTag("returnNative");browserToolbar.addView(nativeView);browserStatus=Ui.text(this,"",11,Ui.MUTED);browserStatus.setMaxLines(2);Ui.pad(browserStatus,8,0);browserToolbar.addView(browserStatus,new LinearLayout.LayoutParams(0,-2,1));browserToolbar.addView(Ui.button(this,"↻",()->web.reload()));browserPane.addView(browserToolbar);browserToolbar.setVisibility(View.GONE);
        webProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);webProgress.setMax(100);browserPane.addView(webProgress,new LinearLayout.LayoutParams(-1,Ui.dp(this,2)));
        web=new WebView(this);web.setBackgroundColor(Ui.WHITE);browserPane.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setTextZoom(prefs.getInt("webTextZoom",100));s.setDomStorageEnabled(true);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);s.setSupportZoom(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        s.setSupportMultipleWindows(true);
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){
                browserEvents+="navigation(main="+r.isForMainFrame()+");";if(!r.isForMainFrame())return false;String url=r.getUrl().toString();
                if(Names.downloadUrl(url)){readClick(()->{if(verifyingId>0&&!url.contains("action=vkpd_file"))receiveDownload(url,view.getSettings().getUserAgentString(),null,view.getUrl());else enqueueSelected(url,clicked,view.getUrl(),view.getSettings().getUserAgentString());});return true;}
                if(url.equals(allowedVerification)||verifyingId>0)return !Names.https(url)&&!url.startsWith("about:");
                if(Names.https(url)||url.startsWith("about:"))return false;
                toast("此链接不是安全网页，已停止打开");return true;
            }
            public void onPageStarted(WebView v,String url,Bitmap icon){
                if(catalog!=null)catalog.pageStarted(url);
                if(clicked!=null&&Objects.equals(Uri.parse(url).getHost(),"magazinelib.com")&&!url.contains("action=vkpd_file")&&!Objects.equals(url,clicked.source))clearBinding();
                if(verifyingId>0&&Uri.parse(url).getHost()!=null&&Uri.parse(url).getHost().equals("magazinelib.com")) {
                    Store.Item job=store.item(verifyingId);if(job==null||!Objects.equals(url,job.source))clearBinding();
                }
            }
            public void onPageCommitVisible(WebView v,String url){installClickCapture(v);}
            public void onPageFinished(WebView v,String url){
                CookieManager.getInstance().flush();if(Names.https(url))prefs.edit().putString("lastUrl",url).apply();
                // Capture the exact anchor text before site handlers redirect or create a popup. No JS bridge.
                installClickCapture(v);
                String t=v.getTitle();boolean challenge=t!=null&&t.toLowerCase(Locale.ROOT).contains("just a moment");
                if(challenge){browserStatus.setText("请在网页内完成网站验证");if(verifyingId>0)store.status(verifyingId,Store.AUTH,"网站要求验证，请在网页内完成");if(catalog!=null)catalog.challenge();}
                else browserStatus.setText(verifyingId>0?"等待下载页跳转；出现预览时点击右上角下载":"点击杂志 PDF，自动保存到书库");
                if(catalog!=null)catalog.pageFinished(url);
            }
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()){browserStatus.setText("网页连接失败："+e.getDescription()+" · 可点击刷新");if(catalog!=null&&CatalogRoutes.site(r.getUrl().toString())&&!Names.downloadUrl(r.getUrl().toString()))catalog.error("连接失败，请刷新重试或打开网页");}}
            public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){h.cancel();browserStatus.setText("网页证书无效，连接已停止");if(catalog!=null)catalog.error("网页证书无效，连接已停止");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            public void onProgressChanged(WebView v,int p){webProgress.setProgress(p);webProgress.setVisibility(p>=100||!prefs.getBoolean("webLoading",true)?View.GONE:View.VISIBLE);}
            public boolean onCreateWindow(WebView parent,boolean dialog,boolean userGesture,Message message){
                browserEvents+="popup(gesture="+userGesture+");";if(!userGesture)return false;
                String source=parent.getUrl();WebView popup=new WebView(MainActivity.this);popup.getSettings().setJavaScriptEnabled(true);popup.getSettings().setDomStorageEnabled(true);popup.getSettings().setAllowFileAccess(false);popup.getSettings().setAllowContentAccess(false);CookieManager.getInstance().setAcceptThirdPartyCookies(popup,true);
                popup.setWebViewClient(new WebViewClient(){public boolean shouldOverrideUrlLoading(WebView p,WebResourceRequest r){if(!r.isForMainFrame())return false;String u=r.getUrl().toString();readClick(()->{
                    if(Names.https(u)){if(Names.downloadUrl(u))enqueueSelected(u,clicked,source,parent.getSettings().getUserAgentString());
                    else if((verifyingId>0&&isFileHost(u))||Objects.equals(Uri.parse(u).getHost(),Uri.parse(source).getHost()))web.loadUrl(u);}
                    p.destroy();});return true;}});
                popup.setDownloadListener((u,ua,cd,mime,size)->readClick(()->{receiveDownload(u,ua,cd,source);popup.destroy();}));
                ((WebView.WebViewTransport)message.obj).setWebView(popup);message.sendToTarget();return true;
            }
        });
        configurePdfTaps();
        web.setDownloadListener((url,ua,disposition,mime,length)->readClick(()->receiveDownload(url,ua,disposition,web.getUrl())));
        if(saved==null||web.restoreState(saved)==null)web.loadUrl(prefs.getBoolean("restoreWebPage",true)?prefs.getString("lastUrl",HOME):HOME);
    }
    private void configurePdfTaps(){
        web.setOnTouchListener((view,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                installClickCapture(web);int token=++touchToken;touchStartX=event.getX();touchStartY=event.getY();touchStarted=event.getEventTime();touchMoved=false;touchRead=false;touchSelection=null;
                float x=event.getX()/Math.max(1,web.getWidth()),y=event.getY()/Math.max(1,web.getWidth());
                String script="JSON.stringify((()=>{let v=visualViewport,a=document.elementFromPoint(v.offsetLeft+"+x+"*v.width,v.offsetTop+"+y+"*v.width)?.closest('a');if(a&&a.href.includes('action=vkpd_file'))return {url:a.href,title:a.innerText.trim()||a.title||'',source:location.href};return null;})())";
                web.evaluateJavascript(script,raw->{if(token!=touchToken)return;touchRead=true;try{Object value=new org.json.JSONTokener(raw).nextValue();if(value instanceof String&&!value.equals("null")){org.json.JSONObject o=new org.json.JSONObject((String)value);if(Names.downloadUrl(o.optString("url")))touchSelection=new Click(o.getString("url"),o.optString("title"),o.optString("source"),"touch:"+token);}}catch(Exception ignored){}});
            }else if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN||event.getActionMasked()==MotionEvent.ACTION_CANCEL){touchMoved=true;}else if(event.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.abs(event.getX()-touchStartX)>Ui.dp(this,12)||Math.abs(event.getY()-touchStartY)>Ui.dp(this,12))touchMoved=true;}
            else if(event.getActionMasked()==MotionEvent.ACTION_UP&&!touchMoved&&event.getEventTime()-touchStarted<700){int token=touchToken;handler.postDelayed(()->finishPdfTap(token,0),180);}
            return false;
        });
    }
    private void finishPdfTap(int token,int attempt){
        if(isDestroyed()||token!=touchToken)return;if(!touchRead){if(attempt<10)handler.postDelayed(()->finishPdfTap(token,attempt+1),100);return;}
        Click selection=touchSelection;if(selection==null||!Objects.equals(selection.source,web.getUrl()))return;
        Store.Item active=store.item(verifyingId);if(active!=null&&!selection.url.equals(active.link)){verifyingId=-1;allowedVerification="";}clicked=selection;
        // A site popup may stop DOM click listeners. Continue the user's PDF tap through the site's own preparation handler.
        web.evaluateJavascript("(()=>{let o=document.querySelector('#mzl-sl-ov');if(!o)return false;let s=getComputedStyle(o);return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0';})()",shown->{if(token!=touchToken||isDestroyed())return;if("true".equals(shown))awaitPrepared(selection.url,selection.title,selection.source,web.getSettings().getUserAgentString(),0);else preparePageDownload(selection.url,selection.title,selection.source,web.getSettings().getUserAgentString());});
    }
    private void installClickCapture(WebView v){v.evaluateJavascript("(()=>{if(window.__mlInstalled)return;window.__mlInstalled=true;window.addEventListener('click',e=>{let a=e.target.closest?.('a');if(a&&(a.href.includes('action=vkpd_file')||/\\.pdf(?:[?#]|$)/i.test(a.href)||/\\.pdf$/i.test(a.innerText.trim()))){window.__mlClick={url:a.href,title:a.innerText.trim()||a.title||'',source:location.href,key:location.href+'#'+Date.now()};}},true);})()",null);}
    private boolean isFileHost(String url){String host=Uri.parse(url).getHost();return host!=null&&(host.equals("vk.com")||host.equals("m.vk.com")||host.endsWith(".userapi.com")||host.endsWith(".vkuserphoto.ru"));}
    private void clearBinding(){verifyingId=-1;allowedVerification="";clicked=null;}
    private void readClick(Runnable after){web.evaluateJavascript("JSON.stringify(window.__mlClick||null)",raw->{try{
        Object value=new org.json.JSONTokener(raw).nextValue();if(value instanceof String&&!value.equals("null")){org.json.JSONObject o=new org.json.JSONObject((String)value);String key=o.optString("key");
            if(!key.equals(capturedKey)&&Names.https(o.optString("url"))){Click selection=new Click(o.getString("url"),o.optString("title"),o.optString("source",HOME),key);
                Store.Item active=store.item(verifyingId);if(active!=null&&!selection.url.equals(active.link)) {verifyingId=-1;allowedVerification="";}
                clicked=selection;capturedKey=key;
            }
        }
    }catch(Exception ignored){}after.run();});}
    private void enqueueSelected(String url,Click selection,String source,String ua){
        if(selection!=null){enqueue(url,selection.title,selection.source,ua);Store.Item job=store.all().stream().filter(i->url.equals(i.url)).findFirst().orElse(null);if(job!=null){ContentValues v=new ContentValues();v.put("link",selection.url);store.update(job.id,v);}}
        else enqueue(url,Names.fromUrl(url),source,ua);
    }
    void receiveDownload(String url,String ua,String disposition,String source){
        browserEvents+="download;";if(!Names.https(url)){toast("请选择 HTTPS PDF 下载链接");return;}
        String fallback=Names.fromUrl(url);if(disposition!=null&&!disposition.isEmpty())fallback=Names.title(URLUtil.guessFileName(url,disposition,"application/pdf"));
        Store.Item job=store.item(verifyingId);
        if(job!=null&&(Store.WAITING.equals(job.status)||Store.AUTH.equals(job.status))&&(clicked==null||clicked.url.equals(job.link))){
            ContentValues v=new ContentValues();v.put("url",url);v.put("ua",ua);v.put("status",Store.QUEUED);v.put("error","");store.update(job.id,v);String back=job.source;clearBinding();DownloadService.start(this);toast("已开始后台下载："+job.title);if(Names.https(back))web.loadUrl(back);
        }else {
            if(clicked!=null)enqueueSelected(url,clicked,source,ua);else enqueue(url,fallback,source,ua);clearBinding();
        }
    }
    void downloadOnPage(){
        String source=web.getUrl(),ua=web.getSettings().getUserAgentString();
        web.evaluateJavascript("JSON.stringify(Array.from(document.querySelectorAll('a')).filter(a=>a.innerText.toLowerCase().includes('.pdf')||a.href.toLowerCase().split('?')[0].endsWith('.pdf')).map(a=>({url:a.href,title:a.innerText.trim()})))",raw->{
            try {if(!java.util.Objects.equals(source,web.getUrl())){toast("页面已改变，请重新点击下载");return;}Object value=new org.json.JSONTokener(raw).nextValue();org.json.JSONArray a=new org.json.JSONArray((String)value);ArrayList<String> urls=new ArrayList<>(),titles=new ArrayList<>();for(int n=0;n<a.length();n++){org.json.JSONObject p=a.getJSONObject(n);String u=p.getString("url");if(Names.https(u)&&!urls.contains(u)){urls.add(u);String t=p.optString("title");titles.add(t.isEmpty()?Names.fromUrl(u):Names.title(t));}}
                if(urls.isEmpty()){toast("本页没有 PDF 链接，请先打开杂志详情页");return;}
                if(urls.size()==1)preparePageDownload(urls.get(0),titles.get(0),source,ua);else new AlertDialog.Builder(this).setTitle("选择本页 PDF").setItems(titles.toArray(new String[0]),(d,n)->preparePageDownload(urls.get(n),titles.get(n),source,ua)).setNegativeButton("取消",null).show();
            }catch(Exception e){toast("暂时未找到 PDF 链接，请等待页面加载完成");}
        });
    }
    private void preparePageDownload(String url,String title,String source,String ua){
        clearBinding();clicked=new Click(url,title,source,"native:"+System.currentTimeMillis());
        if(!url.contains("action=vkpd_file")){enqueue(url,title,source,ua);return;}
        // Let the site's own preparation handler establish its short-lived download cookie.
        browserStatus.setText("网站正在准备文件，完成后自动开始后台下载…");
        if(catalog!=null)catalog.downloadStatus("网站正在准备文件，完成后自动开始后台下载…");
        String quoted=org.json.JSONObject.quote(url);
        web.evaluateJavascript("(()=>{let u="+quoted+",a=Array.from(document.querySelectorAll('a')).find(a=>a.href===u||new URL(a.getAttribute('data-mzl-href')||a.href,location.href).href===u);if(a){a.focus({preventScroll:true});a.click();return true;}return false;})()",v->awaitPrepared(url,title,source,ua,0));
    }
    private void awaitPrepared(String url,String title,String source,String ua,int count){
        if(isFinishing()||isDestroyed()||!java.util.Objects.equals(source,web.getUrl()))return;
        web.evaluateJavascript("!!document.querySelector('#mzl-sl-ov.rdy')",ready->{
            String cookies=CookieManager.getInstance().getCookie(url);
            if("true".equals(ready)&&cookies!=null&&cookies.contains("mzl_dlt=")){CookieManager.getInstance().flush();enqueue(url,title,source,ua);browserStatus.setText("文件已加入后台下载，可在书库阅读");if(catalog!=null)catalog.downloadStatus("已加入后台下载，可在下载页查看进度");}
            else if(count<50)handler.postDelayed(()->awaitPrepared(url,title,source,ua,count+1),500);
            else {browserStatus.setText("网站尚未完成文件准备，请在网页内点击 Download now，或刷新后重试");if(catalog!=null)catalog.downloadStatus("文件仍在准备，可点击右上角网页查看并继续");}
        });
    }
    void enqueue(String url,String title,String source,String ua){
        if(!Names.https(url)){toast("只支持 HTTPS 下载链接");return;}
        Store.Item i=store.add(url,Names.https(source)?source:HOME,title,prefs.getString("tree",""),ua);
        if(Store.READY.equals(i.status)){toast("这期已在书库中");return;}
        if(Store.PAUSED.equals(i.status)||Store.FAILED.equals(i.status)||Store.AUTH.equals(i.status)||Store.WAITING.equals(i.status))store.status(i.id,Store.QUEUED,"");
        notifyPermission();DownloadService.start(this);toast("已加入后台下载："+i.title);DownloadService.changed(this);
    }
    private void notifyPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!prefs.getBoolean("askedNotify",false)){prefs.edit().putBoolean("askedNotify",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},21);}}
    public void showTab(String tab){
        currentTab=tab;header.setVisibility(tab.equals("discover")?View.GONE:View.VISIBLE);brand.setText(switch(tab){case "library"->"书库";case "downloads"->"下载";case "settings"->"设置";default->"";});prefs.edit().putString("tab",tab).apply();browserPane.setVisibility(tab.equals("discover")?View.VISIBLE:View.INVISIBLE);browserToolbar.setVisibility(browserVisible?View.VISIBLE:View.GONE);if(catalog!=null)catalog.view.setVisibility(tab.equals("discover")&&!browserVisible?View.VISIBLE:View.GONE);
        LinearLayout nav=(LinearLayout)root.getChildAt(root.getChildCount()-1);for(int n=0;n<nav.getChildCount();n++){View b=nav.getChildAt(n);b.setBackground(Ui.bg(tab.equals(b.getTag())?0xffe4efea:Ui.WHITE,Ui.dp(this,14)));}
        if(tab.equals("discover")){if(scroll!=null)scroll.setVisibility(View.GONE);}else renderList();
    }
    private void renderList(){
        if(scroll!=null&&!renderedTab.isEmpty())scrollPositions.put(renderedTab,scroll.getScrollY());int y=scrollPositions.getOrDefault(currentTab,0);renderedTab=currentTab;if(scroll!=null)content.removeView(scroll);scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);scroll.setFillViewport(true);listPane=Ui.col(this);Ui.pad(listPane,12,10);scroll.addView(listPane);content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(currentTab.equals("library"))renderLibrary();else if(currentTab.equals("settings"))renderSettings();else renderDownloads();scroll.post(()->scroll.scrollTo(0,y));
    }
    private void singleLine(TextView t){t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);}
    private Button more(String description,Runnable action){Button b=Ui.button(this,"⋮",action);b.setContentDescription(description);b.setMinimumWidth(Ui.dp(this,44));b.setMinWidth(Ui.dp(this,44));b.setPadding(0,0,0,0);b.setBackground(new android.graphics.drawable.InsetDrawable(Ui.bg(0xffe4efea,Ui.dp(this,14)),Ui.dp(this,8)));return b;}
    private String libraryLayout(){return prefs.getString("libraryLayout","grid");}
    void chooseLibraryLayout(){choice("书库展示方式",new String[]{"封面网格","列表"},new String[]{"grid","list"},"libraryLayout",libraryLayout());}
    private void renderLibrary(){
        List<Store.Item> ready=new ArrayList<>();for(Store.Item i:store.all())if(Store.READY.equals(i.status)&&(libraryQuery.isEmpty()||(i.title+" "+i.series).toLowerCase(Locale.ROOT).contains(libraryQuery.toLowerCase(Locale.ROOT))))ready.add(i);
        if(prefs.getString("librarySort","recent").equals("title"))ready.sort(Comparator.comparing(i->i.title,String.CASE_INSENSITIVE_ORDER));
        LinearLayout top=Ui.row(this);top.addView(Ui.text(this,ready.size()+" 期杂志",12,Ui.MUTED),new LinearLayout.LayoutParams(0,-2,1));top.addView(Ui.button(this,libraryLayout().equals("grid")?"网格 ▾":"列表 ▾",this::chooseLibraryLayout));listPane.addView(top);Ui.space(listPane,8);
        if(ready.isEmpty()){empty("书库还是空的","在发现页点击杂志的 PDF 链接，下载完成后会自动归入书库。");return;}
        Map<String,List<Store.Item>> groups=new LinkedHashMap<>();for(Store.Item i:ready)groups.computeIfAbsent(prefs.getBoolean("libraryGrouped",true)?i.series:"",k->new ArrayList<>()).add(i);
        for(Map.Entry<String,List<Store.Item>> e:groups.entrySet()){
            if(!e.getKey().isEmpty()){LinearLayout label=Ui.row(this);TextView name=Ui.heading(this,e.getKey(),14);name.setMaxLines(2);label.addView(name,new LinearLayout.LayoutParams(0,-2,1));label.addView(Ui.text(this,e.getValue().size()+" 期",11,Ui.MUTED));listPane.addView(label);Ui.space(listPane,6);}
            if(libraryLayout().equals("grid"))renderGrid(e.getValue());else for(Store.Item i:e.getValue())renderBookRow(i);
            Ui.space(listPane,8);
        }
    }
    private String issueTitle(Store.Item item){if(prefs.getBoolean("libraryGrouped",true)&&item.title.startsWith(item.series)){String suffix=item.title.substring(item.series.length()).replaceFirst("^[\\s–—:-]+","").trim();if(!suffix.isEmpty())return suffix;}return item.title;}
    private void setCover(ImageView v,Store.Item item){Bitmap b=BitmapFactory.decodeFile(Files.cover(this,item.id).getPath());if(b!=null)v.setImageBitmap(b);else v.setImageResource(R.drawable.ic_library);v.setContentDescription(item.title+" 封面");}
    private void renderBookRow(Store.Item i){
        LinearLayout card=Ui.row(this);card.setTag("book:"+i.id);card.setContentDescription(i.title);card.setBackground(Ui.bg(Ui.WHITE,Ui.dp(this,12)));Ui.pad(card,8,6);
        if(prefs.getBoolean("showCovers",true)){ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);setCover(cover,i);int h=prefs.getBoolean("compactLibrary",true)?54:76;card.addView(cover,new LinearLayout.LayoutParams(Ui.dp(this,h*3/4),Ui.dp(this,h)));}
        LinearLayout info=Ui.col(this);Ui.pad(info,8,0);TextView title=Ui.heading(this,issueTitle(i),14);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(title);TextView meta=Ui.text(this,i.pages+"页 · "+Ui.size(i.done)+(i.page>0?" · 第"+(i.page+1)+"页":""),11,Ui.MUTED);singleLine(meta);info.addView(meta);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(more("管理杂志 "+i.title,()->bookMenu(i)),new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));card.setOnClickListener(v->openReader(i));card.setOnLongClickListener(v->{bookMenu(i);return true;});listPane.addView(card);Ui.space(listPane,6);
    }
    private void renderGrid(List<Store.Item> books){
        int screenDp=Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);int columns=Math.min(prefs.getInt("gridColumns",2),Math.max(2,screenDp/110));
        for(int start=0;start<books.size();start+=columns){LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);
            for(int col=0;col<columns;col++){
                if(start+col>=books.size()){row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));continue;}
                Store.Item i=books.get(start+col);LinearLayout card=Ui.col(this);card.setTag("book:"+i.id);card.setContentDescription(i.title);card.setBackground(Ui.bg(Ui.WHITE,Ui.dp(this,12)));Ui.pad(card,7,6);
                int coverHeight=prefs.getBoolean("showCovers",true)?(prefs.getBoolean("compactLibrary",true)?136:176):44;
                FrameLayout art=new FrameLayout(this);if(prefs.getBoolean("showCovers",true)){ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);setCover(cover,i);art.addView(cover,new FrameLayout.LayoutParams(-1,-1));}
                FrameLayout.LayoutParams menu=new FrameLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44),Gravity.END|Gravity.TOP);Button actions=more("管理杂志 "+i.title,()->bookMenu(i));actions.setBackground(new android.graphics.drawable.InsetDrawable(Ui.bg(0xeef7f5ee,Ui.dp(this,14)),Ui.dp(this,8)));art.addView(actions,menu);card.addView(art,new LinearLayout.LayoutParams(-1,Ui.dp(this,coverHeight)));
                TextView title=Ui.heading(this,issueTitle(i),12);title.setLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title);TextView meta=Ui.text(this,i.pages+"页"+(i.page>0?" · 第"+(i.page+1)+"页":""),10,Ui.MUTED);singleLine(meta);card.addView(meta);
                card.setOnClickListener(v->openReader(i));card.setOnLongClickListener(v->{bookMenu(i);return true;});LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);cell.setMargins(0,0,col<columns-1?Ui.dp(this,6):0,0);row.addView(card,cell);
            }listPane.addView(row);Ui.space(listPane,6);
        }
    }
    private String downloadState(Store.Item i){return switch(i.status){case Store.READY->"已完成";case Store.RUNNING->i.total>0?(i.done*100/i.total)+"%":"下载中";case Store.QUEUED->"排队中";case Store.AUTH->"需要验证";case Store.WAITING->"等待跳转";case Store.PAUSED->"已暂停";default->"失败";};}
    private String itemDirectory(Store.Item i){if(i.url.isEmpty())return "原文件位置";if(i.tree.isEmpty())return "Download / Magazine Library";try{return DocumentsContract.getTreeDocumentId(Uri.parse(i.tree)).replace("primary:","内部存储 / ");}catch(Exception e){return "已选文件夹";}}
    private void renderDownloads(){
        List<Store.Item> history=store.history();boolean canClear=history.stream().anyMatch(i->Store.READY.equals(i.status)||Store.FAILED.equals(i.status));
        LinearLayout folder=Ui.row(this);TextView location=Ui.text(this,"保存到："+directoryLabel()+"  ›",12,Ui.GREEN);singleLine(location);location.setMinHeight(Ui.dp(this,44));location.setGravity(Gravity.CENTER_VERTICAL);location.setOnClickListener(v->chooseFolder());folder.addView(location,new LinearLayout.LayoutParams(0,-2,1));folder.addView(Ui.button(this,prefs.getBoolean("showCompleted",true)?"全部 ▾":"未完成 ▾",()->toggle("显示已完成任务","showCompleted",true)));
        Button clear=Ui.button(this,"清空记录",this::clearDownloadHistory);clear.setTag("clearDownloadHistory");clear.setContentDescription("清空已完成和失败的下载记录，保留 PDF 与未完成任务");clear.setTextSize(12);clear.setEnabled(canClear);clear.setAlpha(canClear?1f:.45f);LinearLayout.LayoutParams clearLayout=new LinearLayout.LayoutParams(-2,Ui.dp(this,44));clearLayout.setMargins(Ui.dp(this,6),0,0,0);folder.addView(clear,clearLayout);listPane.addView(folder);Ui.space(listPane,4);
        List<Store.Item> items=new ArrayList<>();for(Store.Item i:history)if(prefs.getBoolean("showCompleted",true)||!Store.READY.equals(i.status))items.add(i);
        if(items.isEmpty()){empty("没有下载记录","从网页点击 PDF 下载。清空记录后，已下载的 PDF 仍可在书库阅读。");return;}
        for(Store.Item i:items){
            LinearLayout cell=Ui.col(this);cell.setTag("download:"+i.id);cell.setBackground(Ui.bg(Ui.WHITE,Ui.dp(this,10)));LinearLayout line=Ui.row(this);Ui.pad(line,8,0);line.setMinimumHeight(Ui.dp(this,48));
            LinearLayout info=Ui.col(this);TextView title=Ui.heading(this,i.title,13);singleLine(title);title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);info.addView(title);if(prefs.getBoolean("downloadSizes",false)){TextView size=Ui.text(this,Ui.size(i.done)+(i.total>0?" / "+Ui.size(i.total):""),10,Ui.MUTED);singleLine(size);info.addView(size);}line.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView status=Ui.text(this,downloadState(i),11,Store.FAILED.equals(i.status)?0xffad493c:Ui.GREEN);status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);singleLine(status);line.addView(status,new LinearLayout.LayoutParams(Ui.dp(this,66),-2));Button action=more("下载操作 "+i.title,()->downloadMenu(i));action.setTag("downloadMenu:"+i.id);action.setBackgroundColor(Color.TRANSPARENT);line.addView(action,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,48)));cell.addView(line);
            if(Store.RUNNING.equals(i.status)||Store.QUEUED.equals(i.status)){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(100);p.setIndeterminate(i.total<=0);if(i.total>0)p.setProgress((int)(i.done*100/i.total));cell.addView(p,new LinearLayout.LayoutParams(-1,Ui.dp(this,2)));}
            cell.setOnClickListener(v->downloadDetails(store.item(i.id)));listPane.addView(cell);Ui.space(listPane,4);
        }
    }
    private void clearDownloadHistory(){int count=store.clearDownloadHistory();renderList();toast(count>0?"已清空 "+count+" 条记录，PDF 与未完成任务已保留":"没有可清空的记录");}
    private void downloadMenu(Store.Item i){ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();labels.add("任务详情与地址");actions.add(()->downloadDetails(store.item(i.id)));
        if(Store.READY.equals(i.status)){labels.add("阅读 PDF");actions.add(()->openReader(i));labels.add("删除或移除 PDF");actions.add(()->deleteBook(i));}
        else {if(Store.RUNNING.equals(i.status)||Store.QUEUED.equals(i.status)){labels.add("暂停下载");actions.add(()->{store.status(i.id,Store.PAUSED,"");renderList();});}else {labels.add(Store.PAUSED.equals(i.status)?"继续下载":"重试下载");actions.add(()->retry(i));}
            if(Store.WAITING.equals(i.status)||Store.AUTH.equals(i.status)){labels.add(Store.AUTH.equals(i.status)?"打开验证页":"打开下载页");actions.add(()->verify(i));}
            labels.add("移除下载任务");actions.add(()->new AlertDialog.Builder(this).setTitle("移除下载任务？").setMessage("停止这项下载，并删除它的未完成文件。").setPositiveButton("移除",(d,n)->{store.status(i.id,Store.PAUSED,"");removeBook(store.item(i.id),true);}).setNegativeButton("取消",null).show());
        }
        if(Names.https(i.source)){labels.add("打开杂志原页面");actions.add(()->{clearBinding();showTab("discover");web.loadUrl(i.source);});}
        new AlertDialog.Builder(this).setTitle(i.title).setItems(labels.toArray(new String[0]),(d,n)->actions.get(n).run()).setNegativeButton("关闭",null).show();
    }
    private void downloadDetails(Store.Item i){if(i==null)return;LinearLayout detail=Ui.col(this);Ui.pad(detail,18,12);detail.addView(Ui.text(this,downloadState(i)+" · "+Ui.size(i.done)+(i.total>0?" / "+Ui.size(i.total):"")+(i.pages>0?" · "+i.pages+"页":""),13,Ui.GREEN));
        detail.addView(Ui.text(this,"保存位置\n"+itemDirectory(i),13,Ui.INK));if(!i.error.isEmpty())detail.addView(Ui.text(this,"说明\n"+i.error,12,Ui.MUTED));
        String address=i.link.isEmpty()?i.url:i.link;if(!i.url.equals(address)){TextView resolved=Ui.text(this,"实际下载地址\n"+i.url,12,Ui.MUTED);resolved.setTextIsSelectable(true);Ui.pad(resolved,0,8);detail.addView(resolved);}TextView url=Ui.text(this,"下载地址\n"+address,12,Ui.MUTED);url.setTextIsSelectable(true);Ui.pad(url,0,8);detail.addView(url);ScrollView body=new ScrollView(this);body.addView(detail);
        new AlertDialog.Builder(this).setTitle(i.title).setView(body).setPositiveButton("关闭",null).setNeutralButton("复制地址",(d,n)->{getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("PDF 下载地址",address));toast("已复制下载地址");}).show();
    }
    private void empty(String title,String desc){Ui.space(listPane,40);listPane.addView(Ui.heading(this,title,21));Ui.space(listPane,10);TextView t=Ui.text(this,desc,15,Ui.MUTED);t.setLineSpacing(Ui.dp(this,5),1);listPane.addView(t);Ui.space(listPane,16);listPane.addView(Ui.button(this,"去发现杂志",()->showTab("discover")));}
    private void retry(Store.Item i){ContentValues v=new ContentValues();v.put("status",Store.QUEUED);v.put("error","");v.put("tree",prefs.getString("tree",""));store.update(i.id,v);DownloadService.start(this);renderList();}
    void verify(Store.Item i){beginVerification(i,true);}
    private void beginVerification(Store.Item i,boolean reveal){if(reveal)browserVisible=true;showTab("discover");verifyingId=i.id;clicked=new Click(i.link,i.title,i.source,"job:"+i.id);String target=i.verification.isEmpty()?i.url:i.verification;allowedVerification=target;
        String message=Store.AUTH.equals(i.status)?"请在网页内完成网站验证":"等待下载页跳转；出现预览时可点击右上角下载";browserStatus.setText(message);if(catalog!=null)catalog.downloadStatus(message+" · 可打开网页继续");Map<String,String> headers=new HashMap<>();if(Names.https(i.source))headers.put("Referer",i.source);web.loadUrl(target,headers);}
    void showBrowser(boolean show){browserVisible=show;showTab("discover");}
    void showVerificationBrowser(boolean show){browserVisible=show;if(currentTab.equals("discover"))showTab("discover");}
    void loadCatalogPage(String url){clearBinding();browserVisible=false;web.loadUrl(url);if(currentTab.equals("discover"))showTab("discover");}
    void prepareNativeDownload(String url,String title,String source){
        if(!source.equals(web.getUrl()))return;Store.Item existing=store.findDownload(url);if(existing!=null&&Store.READY.equals(existing.status)){catalog.downloadStatus("这期已在书库中，可以离线阅读");toast("这期已在书库中");return;}String ua=web.getSettings().getUserAgentString();if(!url.contains("action=vkpd_file")){enqueue(url,title,source,ua);return;}
        clearBinding();clicked=new Click(url,title,source,"native:"+System.currentTimeMillis());catalog.downloadStatus("网站正在准备文件，完成后自动开始后台下载…");String quoted=org.json.JSONObject.quote(url);
        // The site's focus warm-up establishes its ordinary download cookie in this browser session.
        web.evaluateJavascript("(()=>{let u="+quoted+",a=Array.from(document.querySelectorAll('a')).find(a=>a.href===u||new URL(a.getAttribute('data-mzl-href')||a.href,location.href).href===u);if(!a)return false;a.focus({preventScroll:true});return true;})()",found->{if("true".equals(found))awaitNativeSession(url,title,source,ua,0);else catalog.downloadStatus("文件链接尚未就绪，可打开网页继续");});
    }
    private void awaitNativeSession(String url,String title,String source,String ua,int count){
        if(isDestroyed()||!source.equals(web.getUrl())||clicked==null||!clicked.url.equals(url))return;String cookies=CookieManager.getInstance().getCookie(url);
        if(cookies!=null&&cookies.contains("mzl_dlt=")){CookieManager.getInstance().flush();enqueue(url,title,source,ua);catalog.downloadStatus("已加入后台下载，可在下载页查看进度");return;}
        // Use the same preparation request advertised by the loaded site's own script if focus did not start it.
        if(count==4){String quoted=org.json.JSONObject.quote(url);web.evaluateJavascript("(()=>{let u=new URL("+quoted+");if(u.origin!==location.origin)return;u.search='?action=mzl_dlt';fetch(u.href,{credentials:'same-origin',cache:'no-store'}).catch(()=>{});})()",null);}
        if(count<40)handler.postDelayed(()->awaitNativeSession(url,title,source,ua,count+1),400);else catalog.downloadStatus("网站尚未完成准备，可打开网页完成验证或继续下载");
    }
    NativeCatalog testCatalog(){return catalog;}
    private void openReader(Store.Item i){startActivity(new Intent(this,ReaderActivity.class).putExtra("id",i.id));}
    private void bookMenu(Store.Item i){new AlertDialog.Builder(this).setTitle(i.title).setItems(new String[]{"重命名","调整归类","查找同系列杂志","查看文件位置","删除 PDF"},(d,n)->{
        if(n==0||n==1){EditText input=new EditText(this);input.setText(n==0?i.title:i.series);Ui.pad(input,20,8);new AlertDialog.Builder(this).setTitle(n==0?"重命名杂志":"杂志归类名称").setView(input).setPositiveButton("保存",(x,w)->{
            String name=Names.title(input.getText().toString());fileWorker.execute(()->{try{if(n==0)Files.rename(this,i,name);else {ContentValues v=new ContentValues();v.put("series",name);store.update(i.id,v);}handler.post(()->{if(!isDestroyed())renderList();});}catch(Exception e){handler.post(()->toast(e.getMessage()));}});
        }).setNegativeButton("取消",null).show();}
        else if(n==2){showBrowser(false);catalog.load(HOME+"?s="+Uri.encode(i.series));}
        else if(n==3)new AlertDialog.Builder(this).setTitle("保存位置").setMessage(Uri.decode(i.uri)).setPositiveButton("关闭",null).show();
        else deleteBook(i);
    }).show();}
    private void deleteBook(Store.Item i){
        new AlertDialog.Builder(this).setTitle("删除 PDF？").setMessage(i.title+"\n\n删除文件会同时移除书库条目。也可以仅移出书库，保留原文件。").setPositiveButton("删除文件",(d,w)->removeBook(i,true)).setNeutralButton("仅移出书库",(d,w)->removeBook(i,false)).setNegativeButton("取消",null).show();
    }
    private void removeBook(Store.Item i,boolean delete){fileWorker.execute(()->{try{Files.remove(this,i,delete);handler.post(()->{if(!isDestroyed()){renderList();toast(delete?"PDF 已删除":"已移出书库，文件保留");}});}catch(Exception e){handler.post(()->toast(e.getMessage()));}});}
    void settings(){showTab("settings");}
    private String directoryLabel(){String tree=prefs.getString("tree","");if(tree.isEmpty())return "Download / Magazine Library";try{return DocumentsContract.getTreeDocumentId(Uri.parse(tree)).replace("primary:","内部存储 / ");}catch(Exception e){return "已选择的文件夹";}}
    private void settingRow(String title,String detail,Runnable action){LinearLayout row=Ui.row(this);Ui.pad(row,12,8);row.setBackground(Ui.bg(Ui.WHITE,Ui.dp(this,10)));LinearLayout text=Ui.col(this);text.addView(Ui.heading(this,title,14));if(detail!=null){TextView value=Ui.text(this,detail,11,Ui.MUTED);value.setMaxLines(2);text.addView(value);}row.addView(text,new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.text(this,"›",18,Ui.MUTED));row.setOnClickListener(v->action.run());row.setMinimumHeight(Ui.dp(this,48));listPane.addView(row);Ui.space(listPane,4);}
    private void section(String title){Ui.space(listPane,8);listPane.addView(Ui.heading(this,title,13));Ui.space(listPane,6);}
    private void choice(String title,String[] labels,String[] values,String key,String current){int checked=Arrays.asList(values).indexOf(current);new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,checked,(d,n)->{prefs.edit().putString(key,values[n]).apply();d.dismiss();renderList();}).setNegativeButton("取消",null).show();}
    private void toggle(String title,String key,boolean defaultValue){new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(new String[]{"开启","关闭"},prefs.getBoolean(key,defaultValue)?0:1,(d,n)->{prefs.edit().putBoolean(key,n==0).apply();d.dismiss();renderList();}).setNegativeButton("取消",null).show();}
    private void renderSettings(){
        section("下载");settingRow("下载目录",directoryLabel(),this::chooseFolder);
        if(!prefs.getString("tree","").isEmpty())settingRow("恢复默认下载目录","Download / Magazine Library",()->{prefs.edit().remove("tree").apply();renderList();});
        settingRow("任务显示",prefs.getBoolean("downloadSizes",false)?"标题、状态和大小":"仅标题和状态",()->toggle("在任务行显示文件大小","downloadSizes",false));
        settingRow("显示已完成任务",prefs.getBoolean("showCompleted",true)?"开启":"关闭",()->toggle("显示已完成任务","showCompleted",true));
        section("书库页面");settingRow("展示方式",libraryLayout().equals("grid")?"封面网格":"列表",this::chooseLibraryLayout);
        settingRow("网格列数",prefs.getInt("gridColumns",2)+" 列",()->new AlertDialog.Builder(this).setTitle("网格列数").setSingleChoiceItems(new String[]{"2 列","3 列"},prefs.getInt("gridColumns",2)-2,(d,n)->{prefs.edit().putInt("gridColumns",n+2).apply();d.dismiss();renderList();}).show());
        settingRow("按刊名分组",prefs.getBoolean("libraryGrouped",true)?"同刊不同月份放在一起":"全部杂志平铺",()->toggle("按刊名分组","libraryGrouped",true));
        settingRow("显示封面",prefs.getBoolean("showCovers",true)?"开启":"关闭",()->toggle("显示杂志封面","showCovers",true));
        settingRow("紧凑布局",prefs.getBoolean("compactLibrary",true)?"开启":"关闭",()->toggle("书库紧凑布局","compactLibrary",true));
        settingRow("排序",prefs.getString("librarySort","recent").equals("recent")?"最近下载在前":"按书名",()->choice("书库排序",new String[]{"最近下载在前","按书名"},new String[]{"recent","title"},"librarySort",prefs.getString("librarySort","recent")));
        section("阅读");settingRow("默认阅读方式",prefs.getString("readerMode","single").equals("continuous")?"连续滚动":"单页翻阅",()->choice("默认阅读方式",new String[]{"单页翻阅","连续滚动"},new String[]{"single","continuous"},"readerMode",prefs.getString("readerMode","single")));
        section("网页与启动");settingRow("网页字号",prefs.getInt("webTextZoom",100)+"%",()->new AlertDialog.Builder(this).setTitle("网页字号").setSingleChoiceItems(new String[]{"80%","100%","120%","140%"},Arrays.asList(80,100,120,140).indexOf(prefs.getInt("webTextZoom",100)),(d,n)->{int value=new int[]{80,100,120,140}[n];prefs.edit().putInt("webTextZoom",value).apply();web.getSettings().setTextZoom(value);d.dismiss();renderList();}).show());
        settingRow("网页加载进度",prefs.getBoolean("webLoading",true)?"顶部细线":"隐藏",()->toggle("显示网页加载进度","webLoading",true));
        settingRow("恢复上次网页",prefs.getBoolean("restoreWebPage",true)?"开启":"启动时打开网站首页",()->toggle("恢复上次网页","restoreWebPage",true));
        String start=prefs.getString("startTab","last");String[] starts={"last","discover","library","downloads"};String[] startLabels={"上次页面","发现","书库","下载"};int index=Arrays.asList(starts).indexOf(start);
        settingRow("启动页面",startLabels[Math.max(0,index)],()->choice("启动页面",startLabels,starts,"startTab",start));
        settingRow("打开网站首页",null,()->{showBrowser(false);catalog.load(HOME);});settingRow("刷新当前网页",null,()->{showTab("discover");web.reload();});
        settingRow("打开网址",null,this::enterUrl);
        section("关于");settingRow("Magazine Library","1.3.0",()->new AlertDialog.Builder(this).setTitle("Magazine Library 1.3.0").setMessage("发现页用原生界面展示浏览器当前返回的杂志数据，支持搜索、分类、发布月份、分页与 PDF 下载。只有用户导航时才请求相应页面。\n\n浏览器保留网站会话，验证时切到网页手动完成。普通 VK 跳转只需等待，预览页可点击网页下载按钮。下载记录与 PDF、书库分别管理。").setPositiveButton("关闭",null).show());
    }
    private void enterUrl(){EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setHint("https://…");new AlertDialog.Builder(this).setTitle("打开网址").setView(input).setPositiveButton("打开",(d,w)->{String u=input.getText().toString().trim();if(Names.https(u)){clearBinding();showTab("discover");web.loadUrl(u);}else toast("请输入完整 HTTPS 网址");}).setNegativeButton("取消",null).show();}
    void chooseFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,10);}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==10&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);prefs.edit().putString("tree",u.toString()).apply();toast("后续下载将保存到此目录");if(!currentTab.equals("discover"))renderList();}catch(Exception e){toast("目录授权失败，请重新选择");}}}
    protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);web.saveState(out);out.putString("tab",currentTab);out.putLong("waitingJob",verifyingId);out.putBoolean("browserVisible",browserVisible);catalog.save(out);CookieManager.getInstance().flush();}
    protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);showTab(currentTab);}
    private void handleIntent(Intent i){if(i.hasExtra("tab"))currentTab=i.getStringExtra("tab");if(Intent.ACTION_SEND.equals(i.getAction())){String u=i.getStringExtra(Intent.EXTRA_TEXT);if(Names.https(u)){currentTab="discover";if(CatalogRoutes.site(u)&&!Names.downloadUrl(u))catalog.load(u);else{browserVisible=true;web.loadUrl(u);}}}}
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Below API 33, only our package broadcasts this local refresh event.
    protected void onResume(){super.onResume();if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,new IntentFilter(DownloadService.EVENT),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(updates,new IntentFilter(DownloadService.EVENT));registered=true;if(!currentTab.equals("discover"))renderList();web.onResume();}
    protected void onPause(){CookieManager.getInstance().flush();if(registered){unregisterReceiver(updates);registered=false;}web.onPause();super.onPause();}
    protected void onDestroy(){handler.removeCallbacksAndMessages(null);fileWorker.shutdown();if(catalog!=null)catalog.close();web.destroy();super.onDestroy();}
    public void onBackPressed(){if(currentTab.equals("discover")&&browserVisible)showBrowser(false);else if(currentTab.equals("discover")&&catalog.back())return;else if(!currentTab.equals("discover"))showTab("discover");else super.onBackPressed();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    WebView testWeb(){return web;}
    String testEvents(){return browserEvents;}
}
