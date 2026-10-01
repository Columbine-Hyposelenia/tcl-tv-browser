package com.tclbrowser.gecko;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class UrlBar extends LinearLayout {

    public interface Listener {
        void onSubmit(String text);
        void onFocusGained();
        void onFocusLost();
    }

    private final EditText edit;
    private final ImageView clear;
    private Listener listener;
    private boolean showingUrl = true;
    private String currentUrl = "";

    public UrlBar(Activity activity) {
        super(activity);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int padV = dp(7);
        int padH = dp(14);
        setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFECEEF2);
        bg.setCornerRadius(dp(20));
        setBackground(bg);

        edit = new EditText(activity);
        edit.setSingleLine(true);
        edit.setBackground(null);
        edit.setTextColor(0xFF1A1B1C);
        edit.setHintTextColor(0xFF9AA0A8);
        edit.setHint("搜索或输入网址");
        edit.setTextSize(15);
        edit.setPadding(0, 0, 0, 0);
        edit.setInputType(EditorInfo.TYPE_TEXT_VARIATION_URI
                | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        addView(edit, ep);

        clear = new ImageView(activity);
        clear.setImageResource(R.drawable.ic_close);
        int cs = dp(22);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(cs, cs);
        cp.setMarginStart(dp(6));
        clear.setVisibility(GONE);
        addView(clear, cp);

        edit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP)) {
                submit();
                return true;
            }
            return false;
        });

        edit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                edit.selectAll();
                showKeyboard();
                if (listener != null) listener.onFocusGained();
            } else {
                hideKeyboard();
                setUrl(currentUrl);
                if (listener != null) listener.onFocusLost();
            }
        });

        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                clear.setVisibility(s.length() > 0 ? VISIBLE : GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        clear.setOnClickListener(v -> edit.setText(""));
    }

    private void submit() {
        String text = edit.getText().toString();
        clearFocusAndKeyboard();
        if (listener != null) listener.onSubmit(text);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isEditing() {
        return edit.isFocused();
    }

    public void setUrl(String url) {
        currentUrl = url != null ? url : "";
        if (!edit.isFocused()) {
            showingUrl = true;
            edit.setText(displayText(currentUrl));
        }
    }

    private String displayText(String url) {
        if (url == null || url.isEmpty()) return "";
        return url;
    }

    public void focusForInput() {
        edit.requestFocus();
        edit.selectAll();
        showKeyboard();
    }

    public void clearFocusAndKeyboard() {
        edit.clearFocus();
        hideKeyboard();
    }

    private void showKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
        } catch (Throwable ignored) {}
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        } catch (Throwable ignored) {}
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
