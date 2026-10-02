package com.tclbrowser.gecko;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

public class VirtualMouse extends View {

    private static final int UP = 0;
    private static final int DOWN = 1;
    private static final int LEFT = 2;
    private static final int RIGHT = 3;

    private static final float BASE_SPEED = 155f;
    private static final float MAX_SPEED = 560f;
    private static final float ACCEL = 470f;
    private static final float EDGE = 6f;
    private static final long SCROLL_INTERVAL_MS = 160;

    public interface ScrollListener {
        void onScroll(float vertical, float horizontal);
    }

    private final Activity activity;
    private View root;
    private ScrollListener scrollListener;
    private final boolean[] dirs = new boolean[4];
    private float mx;
    private float my;
    private boolean visible;
    private boolean ticking;
    private long moveStart;
    private boolean pressing;
    private long downTime;
    private float lastFrameTime;
    private boolean wasAtTop;
    private boolean wasAtBottom;
    private long lastScrollTime;

    private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VirtualMouse(Activity activity, View root) {
        super(activity);
        this.activity = activity;
        this.root = root;
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
        float d = getResources().getDisplayMetrics().density;
        // Outer dark stroke: guarantees visibility on white / light pages
        outerPaint.setColor(Color.argb(200, 0, 0, 0));
        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(4.5f * d);
        outerPaint.setStrokeJoin(Paint.Join.ROUND);
        // Inner white ring
        ringPaint.setColor(Color.argb(245, 255, 255, 255));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2.2f * d);
        ringPaint.setStrokeJoin(Paint.Join.ROUND);
        // Center dot
        dotPaint.setColor(Color.argb(245, 255, 255, 255));
        dotPaint.setStyle(Paint.Style.FILL);
        // Press halo
        haloPaint.setColor(Color.argb(60, 255, 255, 255));
        haloPaint.setStyle(Paint.Style.FILL);
    }

    private float ringRadius() {
        return getResources().getDisplayMetrics().density * 12f;
    }

    public void addToWindow(ViewGroup decorContent) {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        decorContent.addView(this, lp);
    }

    public void setTarget(View target) {
        this.root = target;
    }

    public void setScrollListener(ScrollListener listener) {
        this.scrollListener = listener;
    }

    public void show() {
        if (visible) return;
        visible = true;
        setVisibility(VISIBLE);
        mx = Math.max(mx, getWidth() * 0.5f);
        my = Math.max(my, getHeight() * 0.5f);
        if (mx <= 0 || my <= 0) {
            mx = getWidth() * 0.5f;
            my = getHeight() * 0.5f;
        }
        invalidate();
    }

    public void showAt(float x, float y) {
        mx = clamp(x, 2, Math.max(2, getWidth() - 2));
        my = clamp(y, 2, Math.max(2, getHeight() - 2));
        show();
    }

    public void hide() {
        visible = false;
        setVisibility(GONE);
        releaseAll();
        invalidate();
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        int d = directionOf(keyCode);
        if (d >= 0) {
            if (!dirs[d]) {
                boolean wasIdle = !anyDirection();
                dirs[d] = true;
                long now = SystemClock.uptimeMillis();
                // Restart acceleration on a fresh stroke or an immediate
                // reversal, so fine corrections begin at the base speed.
                if (wasIdle || dirs[opposite(d)]) moveStart = now;
                ensureTicking();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() == 0 && !pressing) {
                pointerDown();
            }
            return true;
        }
        return false;
    }

    public boolean handleKeyUp(int keyCode, KeyEvent event) {
        int d = directionOf(keyCode);
        if (d >= 0) {
            dirs[d] = false;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (pressing) pointerUp();
            return true;
        }
        return false;
    }

    private int directionOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return RIGHT;
            default: return -1;
        }
    }

    private int opposite(int d) {
        switch (d) {
            case UP: return DOWN;
            case DOWN: return UP;
            case LEFT: return RIGHT;
            default: return LEFT;
        }
    }

    private boolean anyDirection() {
        return dirs[UP] || dirs[DOWN] || dirs[LEFT] || dirs[RIGHT];
    }

    private void ensureTicking() {
        if (ticking) return;
        ticking = true;
        lastFrameTime = SystemClock.uptimeMillis() / 1000f;
        postOnAnimation(frame);
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!visible) {
                ticking = false;
                return;
            }
            float now = SystemClock.uptimeMillis() / 1000f;
            float dt = Math.min(now - lastFrameTime, 0.05f);
            lastFrameTime = now;

            long held = SystemClock.uptimeMillis() - moveStart;
            float speed = Math.min(BASE_SPEED + ACCEL * (held / 1000f), MAX_SPEED);
            float step = speed * dt;

            float dx = 0f;
            float dy = 0f;
            if (dirs[LEFT]) dx -= step;
            if (dirs[RIGHT]) dx += step;
            if (dirs[UP]) dy -= step;
            if (dirs[DOWN]) dy += step;

            if (dx != 0f && dy != 0f) {
                dx *= 0.7071f;
                dy *= 0.7071f;
            }

            float nx = mx + dx;
            float ny = my + dy;
            nx = clamp(nx, 1, Math.max(1, getWidth() - 1));
            ny = clamp(ny, 1, Math.max(1, getHeight() - 1));

            boolean moved = (nx != mx || ny != my);
            mx = nx;
            my = ny;

            if (pressing && moved) {
                sendPointer(MotionEvent.ACTION_MOVE);
            }

            handleEdgeScroll(dx, dy);
            invalidate();

            if (anyDirection()) {
                postOnAnimation(this);
            } else {
                ticking = false;
                wasAtTop = false;
                wasAtBottom = false;
            }
        }
    };

    private void handleEdgeScroll(float dx, float dy) {
        boolean atTop = dirs[UP] && my <= EDGE;
        boolean atBottom = dirs[DOWN] && my >= getHeight() - EDGE;
        boolean atLeft = dirs[LEFT] && mx <= EDGE;
        boolean atRight = dirs[RIGHT] && mx >= getWidth() - EDGE;

        if (!atTop && !atBottom && !atLeft && !atRight) {
            wasAtTop = false;
            wasAtBottom = false;
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastScrollTime < SCROLL_INTERVAL_MS) {
            return;
        }
        lastScrollTime = now;

        float vertical = 0f;
        float horizontal = 0f;
        if (atTop) vertical += 1f;
        if (atBottom) vertical -= 1f;
        if (atLeft) horizontal += 1f;
        if (atRight) horizontal -= 1f;

        if (scrollListener != null) {
            scrollListener.onScroll(vertical, horizontal);
        }
        wasAtTop = atTop;
        wasAtBottom = atBottom;
    }

    private void pointerDown() {
        downTime = SystemClock.uptimeMillis();
        sendPointer(MotionEvent.ACTION_DOWN);
        pressing = true;
    }

    private void pointerUp() {
        sendPointer(MotionEvent.ACTION_UP);
        pressing = false;
    }

    private void sendPointer(int action) {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(downTime, now, action, mx, my, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            root.dispatchTouchEvent(ev);
        } catch (Throwable t) {
            // ignore dispatch failures on TV
        }
        ev.recycle();
    }

    public void releaseAll() {
        for (int i = 0; i < dirs.length; i++) dirs[i] = false;
        if (pressing) {
            sendPointer(MotionEvent.ACTION_CANCEL);
            pressing = false;
        }
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!visible) return;
        float r = ringRadius();
        float dot = getResources().getDisplayMetrics().density * 1.8f;
        if (pressing) {
            canvas.drawCircle(mx, my, r, haloPaint);
        }
        // Outer dark ring first (visibility on light backgrounds)
        canvas.drawCircle(mx, my, r, outerPaint);
        // Inner white ring
        canvas.drawCircle(mx, my, r, ringPaint);
        // Center dot
        canvas.drawCircle(mx, my, dot, dotPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mx <= 0 || my <= 0) {
            mx = (right - left) * 0.5f;
            my = (bottom - top) * 0.5f;
        }
    }
}
