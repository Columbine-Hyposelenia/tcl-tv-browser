package com.tclbrowser.gecko;

import android.app.Activity;
import android.content.res.TypedArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

public class ChromeToolbar extends LinearLayout {

    public interface Listener {
        void onBack();
        void onForward();
        void onReload();
        void onUrlSubmit(String text);
        void onToggleDesktop();
        void onMenu();
    }

    private final ImageView back;
    private final ImageView forward;
    private final ImageView reload;
    private final ImageView desktop;
    private final ImageView menu;
    private final UrlBar urlBar;
    private final ProgressBar progress;
    private Listener listener;

    public ChromeToolbar(Activity activity) {
        super(activity);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(6);
        int padV = dp(5);
        setPadding(padH, padV, padH, padV);
        setBackgroundColor(0xFFF6F7F9);

        back = makeButton(R.drawable.ic_back);
        forward = makeButton(R.drawable.ic_forward);
        reload = makeButton(R.drawable.ic_reload);
        desktop = makeButton(R.drawable.ic_desktop);
        menu = makeButton(R.drawable.ic_menu);

        addView(back);
        addView(forward);
        addView(reload);

        urlBar = new UrlBar(activity);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        up.setMarginStart(dp(4));
        up.setMarginEnd(dp(4));
        addView(urlBar, up);

        addView(desktop);
        addView(menu);

        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(GONE);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF2F6FEB));

        back.setOnClickListener(v -> { if (listener != null) listener.onBack(); });
        forward.setOnClickListener(v -> { if (listener != null) listener.onForward(); });
        reload.setOnClickListener(v -> { if (listener != null) listener.onReload(); });
        desktop.setOnClickListener(v -> { if (listener != null) listener.onToggleDesktop(); });
        menu.setOnClickListener(v -> { if (listener != null) listener.onMenu(); });

        urlBar.setListener(new UrlBar.Listener() {
            @Override public void onSubmit(String text) {
                if (listener != null) listener.onUrlSubmit(text);
            }
            @Override public void onFocusGained() {}
            @Override public void onFocusLost() {}
        });
    }

    public UrlBar getUrlBar() {
        return urlBar;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setUrl(String url) {
        urlBar.setUrl(url);
    }

    public void setProgress(int value) {
        if (value < 100) {
            progress.setVisibility(VISIBLE);
            progress.setProgress(value);
        } else {
            progress.setProgress(100);
            progress.setVisibility(GONE);
        }
    }

    public void setLoading(boolean loading) {
        if (loading) {
            reload.setImageResource(R.drawable.ic_close);
        } else {
            reload.setImageResource(R.drawable.ic_reload);
        }
    }

    public void setCanBack(boolean can) {
        back.setEnabled(can);
        back.setAlpha(can ? 1f : 0.4f);
    }

    public void setCanForward(boolean can) {
        forward.setEnabled(can);
        forward.setAlpha(can ? 1f : 0.4f);
    }

    public void setDesktop(boolean desktopMode) {
        desktop.setAlpha(desktopMode ? 1f : 0.4f);
    }

    private ImageView makeButton(int icon) {
        ImageView b = new ImageView(getContext());
        b.setImageResource(icon);
        int size = dp(44);
        b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        try {
            TypedArray arr = getContext().getTheme().obtainStyledAttributes(
                    new int[]{android.R.attr.selectableItemBackgroundBorderless});
            b.setBackground(arr.getDrawable(0));
            arr.recycle();
        } catch (Throwable ignored) {}
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
