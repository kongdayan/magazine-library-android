package com.magazinelibrary.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ReaderActivity extends Activity {
    private Store.Item item;private PdfRenderer pdf;private PageView pageView;private ContinuousView continuousView;private TextView pageLabel;private FrameLayout viewport;private Button modeButton;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private int page,pages;private volatile boolean closing;private volatile int request;private File copy;private float[] ratios;private boolean continuous;
    public void onCreate(Bundle saved){super.onCreate(saved);item=Store.get(this).item(getIntent().getLongExtra("id",-1));if(item==null){finish();return;}
        page=saved==null?item.page:saved.getInt("page",item.page);continuous=saved==null?getSharedPreferences("settings",0).getString("readerMode","single").equals("continuous"):saved.getBoolean("continuous");
        LinearLayout root=Ui.col(this);root.setBackgroundColor(Ui.BG);setContentView(root);
        LinearLayout top=Ui.row(this);Ui.pad(top,8,4);top.addView(Ui.button(this,"‹ 书库",this::finish));TextView title=Ui.heading(this,item.title,14);title.setMaxLines(2);Ui.pad(title,8,0);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));modeButton=Ui.button(this,"阅读方式",this::chooseMode);top.addView(modeButton);root.addView(top);
        viewport=new FrameLayout(this);root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));pageView=new PageView(this,this::move);continuousView=new ContinuousView(this);viewport.addView(pageView,new FrameLayout.LayoutParams(-1,-1));viewport.addView(continuousView,new FrameLayout.LayoutParams(-1,-1));continuousView.setVisibility(View.GONE);
        LinearLayout controls=Ui.row(this);Ui.pad(controls,8,4);controls.addView(Ui.button(this,"上一页",()->move(-1)));pageLabel=Ui.text(this,"正在打开…",14,Ui.GREEN);pageLabel.setGravity(Gravity.CENTER);pageLabel.setMinHeight(Ui.dp(this,48));pageLabel.setContentDescription("页码，点击跳转");pageLabel.setOnClickListener(v->jump());controls.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));controls.addView(Ui.button(this,"下一页",()->move(1)));root.addView(controls);
        worker.execute(()->{try{open();pages=pdf.getPageCount();ratios=new float[pages];for(int n=0;n<pages&&!closing;n++)try(PdfRenderer.Page p=pdf.openPage(n)){ratios[n]=p.getHeight()/(float)p.getWidth();}main.post(()->{if(!closing){page=Math.max(0,Math.min(page,pages-1));setMode(continuous);}});}catch(Exception e){main.post(()->{if(!closing)new AlertDialog.Builder(this).setTitle("无法打开 PDF").setMessage("文件可能被移动、损坏、有密码，或目录授权已失效。请重新导入或下载。").setPositiveButton("返回",(d,w)->finish()).setOnCancelListener(d->finish()).show();});}});
    }
    private void open()throws Exception{
        ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(Uri.parse(item.uri),"r");if(fd==null)throw new IOException("File unavailable");
        try{pdf=new PdfRenderer(fd);return;}catch(Exception e){fd.close();}
        copy=new File(getCacheDir(),"reader-"+item.id+".pdf");try(InputStream in=getContentResolver().openInputStream(Uri.parse(item.uri));OutputStream out=new FileOutputStream(copy)){if(in==null)throw new IOException("File unavailable");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(closing)throw new IOException("Closed");out.write(b,0,n);}}
        pdf=new PdfRenderer(ParcelFileDescriptor.open(copy,ParcelFileDescriptor.MODE_READ_ONLY));
    }
    private void chooseMode(){new AlertDialog.Builder(this).setTitle("阅读方式").setSingleChoiceItems(new String[]{"单页翻阅 · 横滑翻页","连续多页 · 上下滚动"},continuous?1:0,(d,n)->{setMode(n==1);getSharedPreferences("settings",0).edit().putString("readerMode",n==1?"continuous":"single").apply();d.dismiss();}).setNegativeButton("关闭",null).show();}
    void setMode(boolean mode){continuous=mode;++request;pageView.clear();continuousView.clear();pageView.setVisibility(mode?View.GONE:View.VISIBLE);continuousView.setVisibility(mode?View.VISIBLE:View.GONE);modeButton.setText(mode?"连续滚动":"单页翻阅");if(pages>0){if(mode){continuousView.configure(ratios);continuousView.go(page);updatePage(page);}else render();}}
    private void move(int delta){goToPage(Math.max(0,Math.min(pages-1,page+delta)));}
    void goToPage(int target){if(pages<=0)return;page=Math.max(0,Math.min(pages-1,target));if(continuous){int targetPage=page;continuousView.go(targetPage);updatePage(targetPage);}else render();}
    private void jump(){if(pages<=0)return;EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(""+(page+1));input.selectAll();Ui.pad(input,20,8);new AlertDialog.Builder(this).setTitle("跳到第几页？ (1–"+pages+")").setView(input).setPositiveButton("跳转",(d,w)->{try{goToPage(Integer.parseInt(input.getText().toString())-1);}catch(NumberFormatException ignored){}}).setNegativeButton("取消",null).show();}
    private void updatePage(int target){page=target;pageLabel.setText((target+1)+" / "+pages+" · 跳页");ContentValues v=new ContentValues();v.put("page",target);Store.get(this).update(item.id,v);}
    private Bitmap bitmap(int target,int width,int maxPixels){try(PdfRenderer.Page p=pdf.openPage(target)){
        float aspect=p.getHeight()/(float)p.getWidth();width=Math.max(1,Math.min(width,(int)Math.sqrt(maxPixels/aspect)));int h=Math.max(1,Math.round(width*aspect));Bitmap b=Bitmap.createBitmap(width,h,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return b;
    }}
    private void render(){int target=page,version=++request;pageLabel.setText((page+1)+" / "+pages+" · 加载中");int width=Math.min(2200,getResources().getDisplayMetrics().widthPixels*2);
        worker.execute(()->{if(closing||version!=request||pdf==null)return;try{Bitmap b=bitmap(target,width,8000000);main.post(()->{if(closing||version!=request){b.recycle();return;}pageView.show(b);updatePage(target);});}catch(Exception|OutOfMemoryError e){main.post(()->{if(!closing&&version==request)pageLabel.setText("这一页无法显示，尝试下一页");});}});
    }
    protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("page",page);b.putBoolean("continuous",continuous);}
    protected void onDestroy(){closing=true;++request;main.removeCallbacksAndMessages(null);pageView.clear();continuousView.clear();worker.execute(()->{if(pdf!=null)pdf.close();if(copy!=null)copy.delete();});worker.shutdown();super.onDestroy();}
    int currentPage(){return page;}int cachedPages(){return continuousView.cache.size();}boolean isContinuous(){return continuous;}
    final class ContinuousView extends View {
        private final LinkedHashMap<Integer,Bitmap> cache=new LinkedHashMap<>(8,0.75f,true);private final Set<Integer> pending=new HashSet<>(),failed=new HashSet<>();
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final RectF rect=new RectF();private float[] offsets=new float[0];private float[] aspects=new float[0];private float position,zoom=1,pan;
        private volatile int generation;private long bytes;private int pendingTarget=-1;private final OverScroller scroller;private final GestureDetector gestures;private final ScaleGestureDetector scale;
        ContinuousView(Context c){super(c);setBackgroundColor(0xffe6e9e3);scroller=new OverScroller(c);
            gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){scroller.forceFinished(true);return true;}public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){if(!scale.isInProgress()){pan-=dx;if(zoom==1)pan=0;position+=dy;clamp();changed();}return true;}public boolean onFling(MotionEvent a,MotionEvent b,float vx,float vy){scroller.fling(0,(int)position,0,(int)-vy,0,0,0,(int)maxScroll());postInvalidateOnAnimation();return true;}public boolean onDoubleTap(MotionEvent e){rescale(zoom>1?1:2,e.getY());return true;}});
            scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){rescale(Math.max(1,Math.min(4,zoom*d.getScaleFactor())),d.getFocusY());return true;}});
        }
        void configure(float[] data){aspects=data;zoom=1;pan=0;layoutPages();}
        void layoutPages(){offsets=new float[aspects.length+1];float width=Math.max(1,getWidth()-Ui.dp(getContext(),12));for(int n=0;n<aspects.length;n++)offsets[n+1]=offsets[n]+width*aspects[n]+Ui.dp(getContext(),8);}
        protected void onSizeChanged(int w,int h,int oldw,int oldh){int target=pendingTarget>=0?pendingTarget:page;layoutPages();go(target);}
        void go(int target){if(target<0||target>=aspects.length)return;if(getWidth()==0||getHeight()==0){pendingTarget=target;return;}pendingTarget=-1;generation++;pending.clear();failed.clear();scroller.forceFinished(true);position=offsets[target]*zoom;clamp();changed();}
        private float maxScroll(){return offsets.length<2?0:Math.max(0,offsets[offsets.length-1]*zoom-getHeight());}
        private void clamp(){position=Math.max(0,Math.min(maxScroll(),position));float bound=Math.max(0,(getWidth()-Ui.dp(getContext(),12))*zoom-getWidth())/2;pan=Math.max(-bound,Math.min(bound,pan));}
        private int at(float y){if(aspects.length==0)return 0;int lo=0,hi=aspects.length-1;while(lo<hi){int mid=(lo+hi+1)/2;if(offsets[mid]<=y)lo=mid;else hi=mid-1;}return lo;}
        private void changed(){if(closing||!continuous)return;if(aspects.length>0){int top=at((position+Ui.dp(getContext(),12))/zoom);if(top!=page)updatePage(top);}invalidate();}
        private void rescale(float next,float focus){float factor=next/zoom;position=(position+focus)*factor-focus;zoom=next;if(zoom==1)pan=0;clamp();changed();}
        public void computeScroll(){if(scroller.computeScrollOffset()){position=scroller.getCurrY();clamp();changed();postInvalidateOnAnimation();}}
        protected void onDraw(Canvas c){super.onDraw(c);if(aspects.length==0)return;int first=at(position/zoom),last=at((position+getHeight())/zoom);float width=(getWidth()-Ui.dp(getContext(),12))*zoom,left=(getWidth()-width)/2+pan;
            for(int n=first;n<=last;n++){float y=offsets[n]*zoom-position,h=(getWidth()-Ui.dp(getContext(),12))*aspects[n]*zoom;rect.set(left,y,left+width,y+h);paint.setColor(Color.WHITE);c.drawRect(rect,paint);Bitmap b=cache.get(n);if(b!=null)c.drawBitmap(b,null,rect,paint);else {paint.setColor(Ui.MUTED);paint.setTextSize(Ui.dp(getContext(),15));c.drawText("第 "+(n+1)+" 页"+(failed.contains(n)?" · 暂时无法显示":" · 加载中"),Ui.dp(getContext(),20),Math.max(Ui.dp(getContext(),30),y+Ui.dp(getContext(),35)),paint);}requestPage(n);}
            if(last+1<aspects.length)requestPage(last+1);if(first>0)requestPage(first-1);
        }
        private void requestPage(int target){if(closing||!continuous||cache.containsKey(target)||pending.contains(target)||failed.contains(target))return;pending.add(target);int token=generation;int width=Math.min(1600,Math.max(600,getWidth()));
            worker.execute(()->{if(closing||token!=generation)return;try{Bitmap b=bitmap(target,width,2000000);main.post(()->{if(closing||token!=generation||!continuous){b.recycle();return;}pending.remove(target);int first=at(position/zoom),last=at((position+getHeight())/zoom);if(target<first-1||target>last+1){b.recycle();return;}cache.put(target,b);bytes+=b.getAllocationByteCount();trim(first,last);invalidate();});}catch(Exception|OutOfMemoryError e){main.post(()->{if(token==generation&&!closing){pending.remove(target);failed.add(target);invalidate();}});}});
        }
        private void trim(int first,int last){Iterator<Map.Entry<Integer,Bitmap>> it=cache.entrySet().iterator();while(it.hasNext()){Map.Entry<Integer,Bitmap> e=it.next();if(e.getKey()<first-1||e.getKey()>last+1){bytes-=e.getValue().getAllocationByteCount();e.getValue().recycle();it.remove();}}
            while((bytes>24000000||cache.size()>8)&&cache.size()>1){Integer victim=null;for(Integer key:cache.keySet())if(key<first||key>last){victim=key;break;}if(victim==null)victim=cache.keySet().iterator().next();Bitmap b=cache.remove(victim);bytes-=b.getAllocationByteCount();b.recycle();}
        }
        void clear(){scroller.forceFinished(true);generation++;pending.clear();failed.clear();for(Bitmap b:cache.values())b.recycle();cache.clear();bytes=0;}
        public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);gestures.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
        public boolean performClick(){super.performClick();return true;}
    }
    interface Turn{void page(int delta);}
    static class PageView extends View {
        private Bitmap bitmap;private float zoom=1,dx,dy,lastX,lastY,downX,downY;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final ScaleGestureDetector scale;private final GestureDetector gestures;private final Turn turn;
        PageView(Context c,Turn turn){super(c);this.turn=turn;setBackgroundColor(0xffe6e9e3);scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){zoom=Math.max(1,Math.min(5,zoom*d.getScaleFactor()));if(zoom==1){dx=dy=0;}invalidate();return true;}});gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(android.view.MotionEvent e){return true;}public boolean onDoubleTap(android.view.MotionEvent e){zoom=zoom>1?1:2;dx=dy=0;invalidate();return true;}});}
        void show(Bitmap b){Bitmap old=bitmap;bitmap=b;zoom=1;dx=dy=0;invalidate();if(old!=null)old.recycle();}
        void clear(){if(bitmap!=null){bitmap.recycle();bitmap=null;}}
        float testZoom(){return zoom;}
        protected void onDraw(Canvas c){super.onDraw(c);if(bitmap==null)return;float fit=Math.min((getWidth()-16f)/bitmap.getWidth(),(getHeight()-16f)/bitmap.getHeight());float w=bitmap.getWidth()*fit*zoom,h=bitmap.getHeight()*fit*zoom;
            dx=Math.max(-Math.max(0,(w-getWidth())/2),Math.min(Math.max(0,(w-getWidth())/2),dx));dy=Math.max(-Math.max(0,(h-getHeight())/2),Math.min(Math.max(0,(h-getHeight())/2),dy));
            c.drawBitmap(bitmap,null,new RectF((getWidth()-w)/2+dx,(getHeight()-h)/2+dy,(getWidth()+w)/2+dx,(getHeight()+h)/2+dy),paint);
        }
        public boolean onTouchEvent(MotionEvent e){gestures.onTouchEvent(e);scale.onTouchEvent(e);switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN->{lastX=downX=e.getX();lastY=downY=e.getY();}
            case MotionEvent.ACTION_MOVE->{if(!scale.isInProgress()&&zoom>1&&e.getPointerCount()==1){dx+=e.getX()-lastX;dy+=e.getY()-lastY;invalidate();}lastX=e.getX();lastY=e.getY();}
            case MotionEvent.ACTION_UP->{float x=e.getX()-downX,y=e.getY()-downY;if(zoom==1&&Math.abs(x)>Ui.dp(getContext(),80)&&Math.abs(x)>Math.abs(y)*1.5)turn.page(x<0?1:-1);performClick();}}
            return true;
        }
        public boolean performClick(){super.performClick();return true;}
    }
}
