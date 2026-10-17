package com.magazinelibrary.app;

import android.graphics.*;
import android.os.Handler;
import android.util.*;
import android.webkit.WebView;
import android.widget.ImageView;
import org.json.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Cover requests use the same browser session; no separate HTTP or cookie transport. */
final class CatalogImages {
    private final WebView web;private final Handler handler;private int generation,attempts;private boolean scheduled;
    private final Map<String,List<WeakReference<ImageView>>> pending=new LinkedHashMap<>();
    private final LruCache<String,Bitmap> cache=new LruCache<>(8*1024*1024){protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}};
    CatalogImages(WebView w,Handler h){web=w;handler=h;}
    void reset(){generation++;pending.clear();scheduled=false;attempts=0;}
    void load(String url,ImageView view){
        view.setImageResource(R.drawable.ic_library);if(!CatalogRoutes.site(url))return;view.setTag(url);Bitmap b=cache.get(url);if(b!=null){view.setImageBitmap(b);return;}pending.computeIfAbsent(url,k->new ArrayList<>()).add(new WeakReference<>(view));if(!scheduled){scheduled=true;int g=generation;handler.post(()->start(g));}
    }
    private void start(int g){if(g!=generation)return;JSONArray urls=new JSONArray();for(String url:pending.keySet())urls.put(url);
        web.evaluateJavascript("(()=>{window.__mlCovers=window.__mlCovers||{};for(let u of "+urls+"){if(window.__mlCovers[u])continue;window.__mlCovers[u]={state:'loading'};let i=new Image();i.onload=()=>{try{let c=document.createElement('canvas'),s=Math.min(190/i.naturalWidth,260/i.naturalHeight,1);c.width=Math.max(1,Math.round(i.naturalWidth*s));c.height=Math.max(1,Math.round(i.naturalHeight*s));c.getContext('2d').drawImage(i,0,0,c.width,c.height);window.__mlCovers[u]={state:'ready',data:c.toDataURL('image/jpeg',.72).split(',')[1]};}catch(e){window.__mlCovers[u]={state:'failed'};}};i.onerror=()=>window.__mlCovers[u]={state:'failed'};i.src=u;}})()",v->{if(g==generation)handler.postDelayed(()->poll(g),350);});
    }
    private void poll(int g){if(g!=generation||pending.isEmpty()){if(g==generation)scheduled=false;return;}web.evaluateJavascript("JSON.stringify(Object.entries(window.__mlCovers||{}).filter(([u,x])=>x.state!=='loading').slice(0,8).map(([url,x])=>({url,state:x.state,data:x.data||''})))",raw->{if(g!=generation)return;JSONArray done=new JSONArray();try{Object value=new JSONTokener(raw).nextValue();JSONArray items=new JSONArray((String)value);for(int n=0;n<items.length();n++){JSONObject item=items.getJSONObject(n);String url=item.optString("url"),data=item.optString("data");done.put(url);List<WeakReference<ImageView>> views=pending.remove(url);if(views==null||data.isEmpty()||data.length()>180000)continue;byte[] bytes=android.util.Base64.decode(data,android.util.Base64.DEFAULT);Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)continue;cache.put(url,b);for(WeakReference<ImageView> ref:views){ImageView v=ref.get();if(v!=null&&url.equals(v.getTag()))v.setImageBitmap(b);}}}catch(Exception ignored){}
            web.evaluateJavascript("(()=>{for(let u of "+done+")delete (window.__mlCovers||{})[u];})()",null);if(++attempts<70&&!pending.isEmpty())handler.postDelayed(()->poll(g),400);else{pending.clear();scheduled=false;attempts=0;}
        });}
    void close(){reset();cache.evictAll();}
}
