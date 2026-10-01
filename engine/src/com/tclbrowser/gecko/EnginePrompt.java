package com.tclbrowser.gecko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

public class EnginePrompt implements GeckoSession.PromptDelegate {

    private final Activity activity;
    private final GeckoSession session;

    public EnginePrompt(Activity activity, GeckoSession session) {
        this.activity = activity;
        this.session = session;
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }

    @Override
    public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession s, final AlertPrompt prompt) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                new AlertDialog.Builder(activity)
                        .setTitle(prompt.title)
                        .setMessage(prompt.message)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        prompt.dismiss();
                                    }
                                })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override public void onCancel(DialogInterface d) {
                                prompt.dismiss();
                            }
                        })
                        .show();
            }
        });
        return null;
    }

    @Override
    public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession s, final ButtonPrompt prompt) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                new AlertDialog.Builder(activity)
                        .setTitle(prompt.title)
                        .setMessage(prompt.message)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        prompt.confirm(0);
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        prompt.confirm(1);
                                    }
                                })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override public void onCancel(DialogInterface d) {
                                prompt.dismiss();
                            }
                        })
                        .show();
            }
        });
        return null;
    }

    @Override
    public GeckoResult<PromptResponse> onTextPrompt(GeckoSession s, final TextPrompt prompt) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                final EditText input = new EditText(activity);
                input.setText(prompt.defaultValue);
                LinearLayout holder = new LinearLayout(activity);
                holder.setPadding(dp(20), dp(10), dp(20), 0);
                holder.addView(input);
                new AlertDialog.Builder(activity)
                        .setTitle(prompt.title)
                        .setMessage(prompt.message)
                        .setView(holder)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        prompt.confirm(input.getText().toString());
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        prompt.dismiss();
                                    }
                                })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override public void onCancel(DialogInterface d) {
                                prompt.dismiss();
                            }
                        })
                        .show();
            }
        });
        return null;
    }

    @Override
    public GeckoResult<PromptResponse> onAuthPrompt(GeckoSession s, final AuthPrompt prompt) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                final EditText user = new EditText(activity);
                user.setHint("Username");
                final EditText pass = new EditText(activity);
                pass.setHint("Password");
                pass.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                LinearLayout holder = new LinearLayout(activity);
                holder.setOrientation(LinearLayout.VERTICAL);
                holder.setPadding(dp(20), dp(10), dp(20), 0);
                holder.addView(user);
                holder.addView(pass);
                new AlertDialog.Builder(activity)
                        .setTitle(prompt.title)
                        .setMessage(prompt.message)
                        .setView(holder)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        prompt.confirm(user.getText().toString(),
                                                pass.getText().toString());
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        prompt.dismiss();
                                    }
                                })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override public void onCancel(DialogInterface d) {
                                prompt.dismiss();
                            }
                        })
                        .show();
            }
        });
        return null;
    }
}
