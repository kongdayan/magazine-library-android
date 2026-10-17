package com.magazinelibrary.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single-page and continuous PDF rendering with zoom, page jump and saved progress. */
public class ReaderActivity extends Activity {
    private Store.Item item;
    private PdfRenderer pdf;
    private PageView pageView;
    private ContinuousView continuousView;
    private TextView pageLabel;
    private FrameLayout viewport;
    private Button modeButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int page, pages;
    private volatile boolean closing;
    private volatile int request;
    private File copy;
    private float[] ratios;
    private boolean continuous;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        item = Store.get(this).item(getIntent().getLongExtra("id", -1));
        if (item == null) {
            finish();
            return;
        }
        page = saved == null ? item.page : saved.getInt("page", item.page);
        continuous = saved == null
                ? getSharedPreferences("settings", 0)
                .getString("readerMode", "single").equals("continuous")
                : saved.getBoolean("continuous");
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        LinearLayout top = Ui.row(this);
        Ui.pad(top, 8, 4);
        top.addView(Ui.button(this, getString(R.string.reader_back_library), this::finish));
        TextView title = Ui.heading(this, item.title, 14);
        title.setMaxLines(2);
        Ui.pad(title, 8, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        modeButton = Ui.button(this, getString(R.string.reader_mode), this::chooseMode);
        top.addView(modeButton);
        root.addView(top);

        viewport = new FrameLayout(this);
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        pageView = new PageView(this, this::move);
        continuousView = new ContinuousView(this);
        viewport.addView(pageView, new FrameLayout.LayoutParams(-1, -1));
        viewport.addView(continuousView, new FrameLayout.LayoutParams(-1, -1));
        continuousView.setVisibility(View.GONE);

        LinearLayout controls = Ui.row(this);
        Ui.pad(controls, 8, 4);
        controls.addView(Ui.button(this, getString(R.string.reader_previous), () -> move(-1)));
        pageLabel = Ui.text(this, getString(R.string.reader_opening), 14, Ui.GREEN);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setMinHeight(Ui.dp(this, 48));
        pageLabel.setContentDescription(getString(R.string.reader_page_accessibility));
        pageLabel.setOnClickListener(v -> jump());
        controls.addView(pageLabel, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(Ui.button(this, getString(R.string.reader_next), () -> move(1)));
        root.addView(controls);

        worker.execute(() -> {
            try {
                open();
                pages = pdf.getPageCount();
                ratios = new float[pages];
                for (int n = 0; n < pages && !closing; n++) {
                    try (PdfRenderer.Page page = pdf.openPage(n)) {
                        ratios[n] = page.getHeight() / (float) page.getWidth();
                    }
                }
                main.post(() -> {
                    if (closing) return;
                    page = Math.max(0, Math.min(page, pages - 1));
                    setMode(continuous);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (closing) return;
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.reader_open_failed_title)
                            .setMessage(R.string.reader_open_failed)
                            .setPositiveButton(R.string.action_back, (d, w) -> finish())
                            .setOnCancelListener(d -> finish())
                            .show();
                });
            }
        });
    }

    private void open() throws Exception {
        ParcelFileDescriptor fd = getContentResolver()
                .openFileDescriptor(Uri.parse(item.uri), "r");
        if (fd == null) throw new IOException(getString(R.string.reader_open_failed));
        try {
            pdf = new PdfRenderer(fd);
            return;
        } catch (Exception e) {
            fd.close();
        }
        copy = new File(getCacheDir(), "reader-" + item.id + ".pdf");
        try (InputStream in = getContentResolver().openInputStream(Uri.parse(item.uri));
             OutputStream out = new FileOutputStream(copy)) {
            if (in == null) throw new IOException(getString(R.string.reader_open_failed));
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (closing) throw new IOException(getString(R.string.error_read_closed));
                out.write(buffer, 0, n);
            }
        }
        pdf = new PdfRenderer(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY));
    }

    private void chooseMode() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reader_mode)
                .setSingleChoiceItems(new String[]{
                        getString(R.string.reader_mode_single),
                        getString(R.string.reader_mode_continuous)}, continuous ? 1 : 0, (d, n) -> {
                    setMode(n == 1);
                    getSharedPreferences("settings", 0).edit()
                            .putString("readerMode", n == 1 ? "continuous" : "single").apply();
                    d.dismiss();
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    void setMode(boolean mode) {
        continuous = mode;
        ++request;
        pageView.clear();
        continuousView.clear();
        pageView.setVisibility(mode ? View.GONE : View.VISIBLE);
        continuousView.setVisibility(mode ? View.VISIBLE : View.GONE);
        modeButton.setText(mode ? R.string.reader_continuous : R.string.reader_single);
        if (pages > 0) {
            if (mode) {
                continuousView.configure(ratios);
                continuousView.go(page);
                updatePage(page);
            } else {
                render();
            }
        }
    }

    private void move(int delta) {
        goToPage(Math.max(0, Math.min(pages - 1, page + delta)));
    }

    void goToPage(int target) {
        if (pages <= 0) return;
        page = Math.max(0, Math.min(pages - 1, target));
        if (continuous) {
            continuousView.go(page);
            updatePage(page);
        } else {
            render();
        }
    }

    private void jump() {
        if (pages <= 0) return;
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(page + 1));
        input.selectAll();
        Ui.pad(input, 20, 8);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.reader_page_prompt, pages))
                .setView(input)
                .setPositiveButton(R.string.reader_go, (d, w) -> {
                    try {
                        goToPage(Integer.parseInt(input.getText().toString()) - 1);
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void updatePage(int target) {
        page = target;
        pageLabel.setText(getString(R.string.reader_page_label, target + 1, pages));
        ContentValues values = new ContentValues();
        values.put("page", target);
        Store.get(this).update(item.id, values);
    }

    private Bitmap bitmap(int target, int width, int maxPixels) {
        try (PdfRenderer.Page page = pdf.openPage(target)) {
            float aspect = page.getHeight() / (float) page.getWidth();
            width = Math.max(1, Math.min(width, (int) Math.sqrt(maxPixels / aspect)));
            int height = Math.max(1, Math.round(width * aspect));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        }
    }

    private void render() {
        int target = page, version = ++request;
        pageLabel.setText(getString(R.string.reader_page_loading, page + 1, pages));
        int width = Math.min(2200, getResources().getDisplayMetrics().widthPixels * 2);
        worker.execute(() -> {
            if (closing || version != request || pdf == null) return;
            try {
                Bitmap bitmap = bitmap(target, width, 8000000);
                main.post(() -> {
                    if (closing || version != request) {
                        bitmap.recycle();
                        return;
                    }
                    pageView.show(bitmap);
                    updatePage(target);
                });
            } catch (Exception | OutOfMemoryError e) {
                main.post(() -> {
                    if (!closing && version == request) {
                        pageLabel.setText(R.string.reader_page_unavailable);
                    }
                });
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
        out.putBoolean("continuous", continuous);
    }

    @Override protected void onDestroy() {
        closing = true;
        ++request;
        main.removeCallbacksAndMessages(null);
        pageView.clear();
        continuousView.clear();
        worker.execute(() -> {
            if (pdf != null) pdf.close();
            if (copy != null) copy.delete();
        });
        worker.shutdown();
        super.onDestroy();
    }

    int currentPage() {
        return page;
    }

    int cachedPages() {
        return continuousView.cache.size();
    }

    boolean isContinuous() {
        return continuous;
    }

    final class ContinuousView extends View {
        private final LinkedHashMap<Integer, Bitmap> cache =
                new LinkedHashMap<>(8, 0.75f, true);
        private final Set<Integer> pending = new HashSet<>(), failed = new HashSet<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF rect = new RectF();
        private float[] offsets = new float[0];
        private float[] aspects = new float[0];
        private float position, zoom = 1, pan;
        private volatile int generation;
        private long bytes;
        private int pendingTarget = -1;
        private final OverScroller scroller;
        private final GestureDetector gestures;
        private final ScaleGestureDetector scale;

        ContinuousView(Context c) {
            super(c);
            setBackgroundColor(0xffe6e9e3);
            scroller = new OverScroller(c);
            gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                public boolean onDown(MotionEvent e) {
                    scroller.forceFinished(true);
                    return true;
                }

                public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                    if (!scale.isInProgress()) {
                        pan -= dx;
                        if (zoom == 1) pan = 0;
                        position += dy;
                        clamp();
                        changed();
                    }
                    return true;
                }

                public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                    scroller.fling(0, (int) position, 0, (int) -vy, 0, 0, 0, (int) maxScroll());
                    postInvalidateOnAnimation();
                    return true;
                }

                public boolean onDoubleTap(MotionEvent e) {
                    rescale(zoom > 1 ? 1 : 2, e.getY());
                    return true;
                }
            });
            scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) {
                    rescale(Math.max(1, Math.min(4, zoom * d.getScaleFactor())), d.getFocusY());
                    return true;
                }
            });
        }

        void configure(float[] data) {
            aspects = data;
            zoom = 1;
            pan = 0;
            layoutPages();
        }

        void layoutPages() {
            offsets = new float[aspects.length + 1];
            float width = Math.max(1, getWidth() - Ui.dp(getContext(), 12));
            for (int n = 0; n < aspects.length; n++) {
                offsets[n + 1] = offsets[n] + width * aspects[n] + Ui.dp(getContext(), 8);
            }
        }

        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            int target = pendingTarget >= 0 ? pendingTarget : page;
            layoutPages();
            go(target);
        }

        void go(int target) {
            if (target < 0 || target >= aspects.length) return;
            if (getWidth() == 0 || getHeight() == 0) {
                pendingTarget = target;
                return;
            }
            pendingTarget = -1;
            generation++;
            pending.clear();
            failed.clear();
            scroller.forceFinished(true);
            position = offsets[target] * zoom;
            clamp();
            changed();
        }

        private float maxScroll() {
            return offsets.length < 2 ? 0
                    : Math.max(0, offsets[offsets.length - 1] * zoom - getHeight());
        }

        private void clamp() {
            position = Math.max(0, Math.min(maxScroll(), position));
            float bound = Math.max(0, (getWidth() - Ui.dp(getContext(), 12)) * zoom - getWidth()) / 2;
            pan = Math.max(-bound, Math.min(bound, pan));
        }

        private int at(float y) {
            if (aspects.length == 0) return 0;
            int lo = 0, hi = aspects.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) / 2;
                if (offsets[mid] <= y) lo = mid;
                else hi = mid - 1;
            }
            return lo;
        }

        private void changed() {
            if (closing || !continuous) return;
            if (aspects.length > 0) {
                int top = at((position + Ui.dp(getContext(), 12)) / zoom);
                if (top != page) updatePage(top);
            }
            invalidate();
        }

        private void rescale(float next, float focus) {
            float factor = next / zoom;
            position = (position + focus) * factor - focus;
            zoom = next;
            if (zoom == 1) pan = 0;
            clamp();
            changed();
        }

        public void computeScroll() {
            if (scroller.computeScrollOffset()) {
                position = scroller.getCurrY();
                clamp();
                changed();
                postInvalidateOnAnimation();
            }
        }

        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (aspects.length == 0) return;
            int first = at(position / zoom), last = at((position + getHeight()) / zoom);
            float width = (getWidth() - Ui.dp(getContext(), 12)) * zoom,
                    left = (getWidth() - width) / 2 + pan;
            for (int n = first; n <= last; n++) {
                float y = offsets[n] * zoom - position,
                        h = (getWidth() - Ui.dp(getContext(), 12)) * aspects[n] * zoom;
                rect.set(left, y, left + width, y + h);
                paint.setColor(Color.WHITE);
                c.drawRect(rect, paint);
                Bitmap bitmap = cache.get(n);
                if (bitmap != null) {
                    c.drawBitmap(bitmap, null, rect, paint);
                } else {
                    paint.setColor(Ui.MUTED);
                    paint.setTextSize(Ui.dp(getContext(), 15));
                    c.drawText(getContext().getString(R.string.reader_page_placeholder, n + 1,
                                    getContext().getString(failed.contains(n)
                                            ? R.string.reader_page_failed_short
                                            : R.string.reader_page_loading_short)),
                            Ui.dp(getContext(), 20),
                            Math.max(Ui.dp(getContext(), 30), y + Ui.dp(getContext(), 35)), paint);
                }
                requestPage(n);
            }
            if (last + 1 < aspects.length) requestPage(last + 1);
            if (first > 0) requestPage(first - 1);
        }

        private void requestPage(int target) {
            if (closing || !continuous || cache.containsKey(target)
                    || pending.contains(target) || failed.contains(target)) return;
            pending.add(target);
            int token = generation;
            int width = Math.min(1600, Math.max(600, getWidth()));
            worker.execute(() -> {
                if (closing || token != generation) return;
                try {
                    Bitmap bitmap = bitmap(target, width, 2000000);
                    main.post(() -> {
                        if (closing || token != generation || !continuous) {
                            bitmap.recycle();
                            return;
                        }
                        pending.remove(target);
                        int first = at(position / zoom), last = at((position + getHeight()) / zoom);
                        if (target < first - 1 || target > last + 1) {
                            bitmap.recycle();
                            return;
                        }
                        cache.put(target, bitmap);
                        bytes += bitmap.getAllocationByteCount();
                        trim(first, last);
                        invalidate();
                    });
                } catch (Exception | OutOfMemoryError e) {
                    main.post(() -> {
                        if (token == generation && !closing) {
                            pending.remove(target);
                            failed.add(target);
                            invalidate();
                        }
                    });
                }
            });
        }

        private void trim(int first, int last) {
            Iterator<Map.Entry<Integer, Bitmap>> iterator = cache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Bitmap> entry = iterator.next();
                if (entry.getKey() < first - 1 || entry.getKey() > last + 1) {
                    bytes -= entry.getValue().getAllocationByteCount();
                    entry.getValue().recycle();
                    iterator.remove();
                }
            }
            while ((bytes > 24000000 || cache.size() > 8) && cache.size() > 1) {
                Integer victim = null;
                for (Integer key : cache.keySet()) {
                    if (key < first || key > last) {
                        victim = key;
                        break;
                    }
                }
                if (victim == null) victim = cache.keySet().iterator().next();
                Bitmap bitmap = cache.remove(victim);
                bytes -= bitmap.getAllocationByteCount();
                bitmap.recycle();
            }
        }

        void clear() {
            scroller.forceFinished(true);
            generation++;
            pending.clear();
            failed.clear();
            for (Bitmap bitmap : cache.values()) bitmap.recycle();
            cache.clear();
            bytes = 0;
        }

        public boolean onTouchEvent(MotionEvent e) {
            scale.onTouchEvent(e);
            gestures.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return true;
        }

        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    interface Turn {
        void page(int delta);
    }

    @android.annotation.SuppressLint("ViewConstructor")
    static class PageView extends View {
        private Bitmap bitmap;
        private float zoom = 1, dx, dy, lastX, lastY, downX, downY;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF destination = new RectF();
        private final ScaleGestureDetector scale;
        private final GestureDetector gestures;
        private final Turn turn;

        PageView(Context c, Turn turn) {
            super(c);
            this.turn = turn;
            setBackgroundColor(0xffe6e9e3);
            scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) {
                    zoom = Math.max(1, Math.min(5, zoom * d.getScaleFactor()));
                    if (zoom == 1) dx = dy = 0;
                    invalidate();
                    return true;
                }
            });
            gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                public boolean onDoubleTap(MotionEvent e) {
                    zoom = zoom > 1 ? 1 : 2;
                    dx = dy = 0;
                    invalidate();
                    return true;
                }
            });
        }

        void show(Bitmap bitmap) {
            Bitmap old = this.bitmap;
            this.bitmap = bitmap;
            zoom = 1;
            dx = dy = 0;
            invalidate();
            if (old != null) old.recycle();
        }

        void clear() {
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
        }

        float testZoom() {
            return zoom;
        }

        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (bitmap == null) return;
            float fit = Math.min((getWidth() - 16f) / bitmap.getWidth(),
                    (getHeight() - 16f) / bitmap.getHeight());
            float w = bitmap.getWidth() * fit * zoom, h = bitmap.getHeight() * fit * zoom;
            dx = Math.max(-Math.max(0, (w - getWidth()) / 2),
                    Math.min(Math.max(0, (w - getWidth()) / 2), dx));
            dy = Math.max(-Math.max(0, (h - getHeight()) / 2),
                    Math.min(Math.max(0, (h - getHeight()) / 2), dy));
            destination.set((getWidth() - w) / 2 + dx,
                    (getHeight() - h) / 2 + dy,
                    (getWidth() + w) / 2 + dx,
                    (getHeight() + h) / 2 + dy);
            c.drawBitmap(bitmap, null, destination, paint);
        }

        public boolean onTouchEvent(MotionEvent e) {
            gestures.onTouchEvent(e);
            scale.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    lastX = downX = e.getX();
                    lastY = downY = e.getY();
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (!scale.isInProgress() && zoom > 1 && e.getPointerCount() == 1) {
                        dx += e.getX() - lastX;
                        dy += e.getY() - lastY;
                        invalidate();
                    }
                    lastX = e.getX();
                    lastY = e.getY();
                }
                case MotionEvent.ACTION_UP -> {
                    float x = e.getX() - downX, y = e.getY() - downY;
                    if (zoom == 1 && Math.abs(x) > Ui.dp(getContext(), 80)
                            && Math.abs(x) > Math.abs(y) * 1.5) {
                        turn.page(x < 0 ? 1 : -1);
                    }
                    performClick();
                }
            }
            return true;
        }

        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
