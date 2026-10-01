package com.tclbrowser.tv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;

public final class Dialogs {

    private Dialogs() {}

    public static void jsAlert(Context context, String message, final JsResult result) {
        try {
            new AlertDialog.Builder(context)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override public void onCancel(DialogInterface dialog) {
                            result.cancel();
                        }
                    })
                    .show();
        } catch (Throwable t) {
            result.confirm();
        }
    }

    public static void jsConfirm(Context context, String message, final JsResult result) {
        try {
            new AlertDialog.Builder(context)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            result.confirm();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            result.cancel();
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override public void onCancel(DialogInterface dialog) {
                            result.cancel();
                        }
                    })
                    .show();
        } catch (Throwable t) {
            result.confirm();
        }
    }

    public static void jsPrompt(Context context, String message, String defaultValue,
            final JsPromptResult result) {
        try {
            final EditText input = new EditText(context);
            input.setText(defaultValue);
            input.setSelection(input.getText().length());
            LinearLayout holder = new LinearLayout(context);
            holder.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (context.getResources().getDisplayMetrics().density * 20);
            holder.setPadding(pad, pad / 2, pad, 0);
            holder.addView(input);

            new AlertDialog.Builder(context)
                    .setMessage(message)
                    .setView(holder)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            result.confirm(input.getText().toString());
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            result.cancel();
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override public void onCancel(DialogInterface dialog) {
                            result.cancel();
                        }
                    })
                    .show();
        } catch (Throwable t) {
            result.confirm();
        }
    }
}
