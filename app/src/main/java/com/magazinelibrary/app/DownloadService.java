package com.magazinelibrary.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.*;
import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** A persisted queue with a foreground worker, domain-scoped cookies and validated output. */
public class DownloadService extends Service {
    static volatile boolean isAlive;
    static final String EVENT="com.magazinelibrary.app.CHANGED";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Store store;private volatile boolean draining=false,stopping=false;private volatile HttpURLConnection active;
    static void start(Context c){c.startForegroundService(new Intent(c,DownloadService.class));}
    static void changed(Context c){c.sendBroadcast(new Intent(EVENT).setPackage(c.getPackageName()));}
    private static void changed(Context c,long id){c.sendBroadcast(new Intent(EVENT).setPackage(c.getPackageName()).putExtra("id",id));}
    public void onCreate(){super.onCreate();isAlive=true;store=Store.get(this);NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("downloads","PDF 下载",NotificationManager.IMPORTANCE_LOW));}
    public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(7,notification("准备下载",0,-1),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        synchronized(this){if(!draining){draining=true;worker.execute(this::drain);}}
        return START_NOT_STICKY;
    }
    private void drain(){
        try {
            Store.Item i;
            while(!stopping && (i=store.next())!=null) {
                try{transfer(i);}catch(Pause e){store.status(i.id,Store.PAUSED,"已暂停，可继续");}
                catch(Landing e){store.status(i.id,Store.WAITING,e.getMessage());}
                catch(Auth e){store.status(i.id,Store.AUTH,"需要网页验证，点「去验证」后重试");ContentValues v=new ContentValues();v.put("error",e.getMessage());store.update(i.id,v);}
                catch(Exception e){store.status(i.id,Store.FAILED,e.getMessage()==null?"下载失败，请重试":e.getMessage());}
                changed(this,i.id);
            }
        } finally {
            main.post(()->{synchronized(this){draining=false;if(!stopping && store.next()!=null){draining=true;worker.execute(this::drain);return;}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}});
        }
    }
    private void check(long id)throws Pause {Store.Item current=store.item(id);if(stopping||current==null||Store.PAUSED.equals(current.status))throw new Pause();}
    private String cookie(String url)throws Exception {
        FutureTask<String> task=new FutureTask<>(()->CookieManager.getInstance().getCookie(url));main.post(task);return task.get(10,TimeUnit.SECONDS);
    }
    private void responseCookies(String url,HttpURLConnection c) throws Exception {
        java.util.List<String> values=c.getHeaderFields().entrySet().stream().filter(e->e.getKey()!=null&&e.getKey().equalsIgnoreCase("Set-Cookie")).flatMap(e->e.getValue().stream()).collect(java.util.stream.Collectors.toList());
        if(!values.isEmpty()){CountDownLatch latch=new CountDownLatch(values.size());main.post(()->{for(String value:values)CookieManager.getInstance().setCookie(url,value,ok->latch.countDown());});if(!latch.await(10,TimeUnit.SECONDS))throw new IOException("会话保存超时，请重试");main.post(()->CookieManager.getInstance().flush());}
    }
    private HttpURLConnection open(Store.Item i,long offset)throws Exception {
        String url=i.url;
        for(int hop=0;hop<12;hop++) {
            check(i.id);if(!Names.https(url))throw new IOException("下载链接必须使用 HTTPS");ContentValues hopInfo=new ContentValues();hopInfo.put("verification",url);store.update(i.id,hopInfo);
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();active=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(25000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent",i.ua);c.setRequestProperty("Accept","application/pdf,*/*;q=0.8");c.setRequestProperty("Accept-Encoding","identity");
            String cookies=cookie(url);if(cookies!=null&&!cookies.isEmpty())c.setRequestProperty("Cookie",cookies);
            if(Names.https(i.source)) c.setRequestProperty("Referer",i.source);
            if(offset>0&&!i.etag.isEmpty()){c.setRequestProperty("Range","bytes="+offset+"-");c.setRequestProperty("If-Range",i.etag);}
            int code;try{code=c.getResponseCode();}catch(java.net.SocketTimeoutException e){c.disconnect();throw new IOException("连接 "+new URL(url).getHost()+" 超时，请重试或回原页面获取新链接",e);}responseCookies(url,c);
            if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("下载跳转缺少地址");url=new URL(new URL(url),location).toString();URL next=new URL(url);if((next.getHost().equals("vk.com")||next.getHost().equals("m.vk.com"))&&next.getPath().startsWith("/doc")){ContentValues landing=new ContentValues();landing.put("verification",url);store.update(i.id,landing);throw new Landing("等待 VK 下载页跳转；若出现预览，可点击右上角下载");}continue;}
            if("challenge".equalsIgnoreCase(c.getHeaderField("cf-mitigated"))){c.disconnect();throw new Auth("网站要求验证，请在网页内完成后继续下载");}
            String type=c.getContentType();if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).contains("text/html")){
                String html;try(InputStream in=code>=400?c.getErrorStream():c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while(in!=null&&out.size()<32768&&(n=in.read(buf,0,Math.min(buf.length,32768-out.size())))!=-1)out.write(buf,0,n);html=out.toString("UTF-8");}finally{c.disconnect();}
                if(Names.challenge(html))throw new Auth("网站要求验证，请在网页内完成后继续下载");
                if(code>=400)throw new IOException("下载链接失效或没有访问权限 (HTTP "+code+")，请回原页面重新获取链接");
                throw new Landing("等待下载页跳转；若出现预览，可点击页面下载按钮");
            }
            if(code!=200&&code!=206){c.disconnect();throw new IOException("服务器返回 HTTP "+code+"，可回原页面获取新链接");}
            return c;
        }
        throw new IOException("下载跳转次数过多");
    }
    private void transfer(Store.Item i)throws Exception {
        store.status(i.id,Store.RUNNING,"");changed(this);File part=Files.part(this,i.id);long offset=part.exists()?part.length():0;
        if(i.etag.isEmpty())offset=0;
        HttpURLConnection c=open(i,offset);
        try {
            boolean append=offset>0&&c.getResponseCode()==206;
            String range=c.getHeaderField("Content-Range");
            if(append&&(range==null||!range.startsWith("bytes "+offset+"-")))throw new IOException("服务器返回的续传范围不匹配，请重新下载");
            if(!append)offset=0;
            long length=c.getContentLengthLong();long total=length>=0?offset+length:-1;
            String etag=c.getHeaderField("ETag");if(etag==null)etag=c.getHeaderField("Last-Modified");
            ContentValues meta=new ContentValues();meta.put("etag",etag==null?"":etag);meta.put("total",total);meta.put("done",offset);store.update(i.id,meta);
            long done=offset,last=0;
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part,append)) {
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){check(i.id);out.write(buf,0,n);done+=n;
                    long now=System.currentTimeMillis();if(now-last>600){last=now;progress(i,done,total);}}
            }
            check(i.id);progress(i,done,total);if(total>=0&&done!=total)throw new IOException("下载不完整，请重试续传");
            int pages=Files.validate(part);Files.makeCover(this,i.id,part);check(i.id);
            Uri uri=Files.publish(this,i,part);ContentValues v=new ContentValues();v.put("uri",uri.toString());v.put("pages",pages);v.put("done",done);v.put("total",done);v.put("status",Store.READY);v.put("error","");try{synchronized(store){check(i.id);store.update(i.id,v);}}catch(Pause canceled){Files.deleteOutput(this,uri);throw canceled;}part.delete();
        } finally {c.disconnect();active=null;}
    }
    private void progress(Store.Item i,long done,long total){ContentValues v=new ContentValues();v.put("done",done);v.put("total",total);store.update(i.id,v);getSystemService(NotificationManager.class).notify(7,notification(i.title,done,total));changed(this);}
    private Notification notification(String title,long done,long total){
        PendingIntent p=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).putExtra("tab","downloads"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,"downloads").setSmallIcon(com.magazinelibrary.app.R.drawable.ic_library).setContentTitle(title).setContentText(Ui.size(done)+(total>0?" / "+Ui.size(total):"")).setContentIntent(p).setOngoing(true).setOnlyAlertOnce(true);
        if(total>0)b.setProgress(100,(int)(done*100/total),false);else b.setProgress(0,0,true);return b.build();
    }
    public void onTimeout(int startId,int fgsType){stopping=true;if(active!=null)active.disconnect();store.recover();changed(this);stopSelf();}
    public void onDestroy(){isAlive=false;stopping=true;if(active!=null)active.disconnect();worker.shutdown();super.onDestroy();}
    public IBinder onBind(Intent i){return null;}
    private static class Landing extends IOException{Landing(String message){super(message);}}
    private static class Auth extends IOException{Auth(String message){super(message);}}
    private static class Pause extends IOException{}
}
