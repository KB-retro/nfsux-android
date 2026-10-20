package org.nfsu.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;

/**
 * In-game quick menu (Back, or Select + Start on a controller): the picture
 * rows of Options -> Camera plus FSR sharpness, the FPS counter and fast
 * cores, applied at once. Language and picture format need a restart and
 * stay in the launcher settings.
 */
final class QuickMenuDialog {
    /** A value row: one label, n values, cycled with left/right or the arrows. */
    private interface Row {
        String[] values();
        int get();
        void set(int value);
    }

    private final GameActivity activity;
    private final Dialog dialog;
    private final LinearLayout rows;

    private QuickMenuDialog(GameActivity activity) {
        this.activity = activity;
        dialog = new Dialog(activity);
        rows = new LinearLayout(activity);
    }

    static Dialog show(GameActivity activity) {
        QuickMenuDialog menu = new QuickMenuDialog(activity);
        menu.build();
        return menu.dialog;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics()));
    }

    private void build() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xF0182024);
        card.setStroke(dp(1), 0xFF405761);
        card.setCornerRadius(dp(2));
        panel.setBackground(card);

        TextView title = text(R.string.quick_title, 20, 0xFFF3F6F7);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD_ITALIC));
        panel.addView(title);

        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        for (int i = 0; i < GameActivity.nativeOptionCount(); i++) addGameRow(i);
        addRow(activity.getString(R.string.settings_show_fps), new Row() {
            @Override public String[] values() { return offOn(); }
            @Override public int get() { return LauncherSettings.showFps(activity) ? 1 : 0; }
            @Override public void set(int value) {
                LauncherSettings.setShowFps(activity, value == 1);
                activity.setFpsOverlayVisible(value == 1);
            }
        });
        addRow(activity.getString(R.string.settings_fast_cores), new Row() {
            private int value = -1;
            @Override public String[] values() { return offOn(); }
            @Override public int get() {
                if (value < 0) {
                    try {
                        value = new LauncherSettings(activity).load().envValue("RECOMP_FAST_CORES", 0) == 1 ? 1 : 0;
                    } catch (IOException exception) {
                        value = 0;
                    }
                }
                return value;
            }
            @Override public void set(int next) {
                value = next;
                GameActivity.nativeSetFastCores(next == 1);
                try {
                    LauncherSettings settings = new LauncherSettings(activity).load();
                    settings.env.put("RECOMP_FAST_CORES", next == 1 ? "1" : "0");
                    settings.saveEnv();
                } catch (IOException exception) {
                    Log.w("NFSU", "Cannot save fast cores", exception);
                }
            }
        });

        TextView note = text(R.string.quick_note, 12, 0xFF87929C);
        note.setPadding(0, dp(8), 0, 0);
        panel.addView(note);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setPadding(0, dp(10), 0, 0);
        Button exit = button(R.string.quick_exit);
        exit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { confirmExit(); }
        });
        Button resume = button(R.string.quick_resume);
        resume.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        half.setMarginEnd(dp(8));
        buttons.addView(exit, half);
        buttons.addView(resume, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        panel.addView(buttons);

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        dialog.show();
        if (window != null) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(android.view.WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(android.view.WindowInsets.Type.systemBars());
            }
            android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setLayout(Math.min(dp(560), metrics.widthPixels - dp(16)),
                    Math.min(dp(520), metrics.heightPixels - dp(16)));
        }
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    private String[] offOn() {
        return activity.getResources().getStringArray(R.array.settings_off_on);
    }

    /** A native row, with its English label and values translated when known. */
    private void addGameRow(final int index) {
        String label = GameActivity.nativeOptionLabel(index);
        final String[] nativeValues = GameActivity.nativeOptionValues(index);
        if (label == null || nativeValues == null) return;
        int labelRes = 0, valuesRes = 0;
        switch (label) {
            case "Car Reflections": labelRes = R.string.settings_reflections; valuesRes = R.array.settings_off_on; break;
            case "Resolution Scale": labelRes = R.string.settings_scale; valuesRes = R.array.settings_scales; break;
            case "Anti-Aliasing": labelRes = R.string.settings_antialiasing; valuesRes = R.array.settings_antialiasing_modes; break;
            case "Anisotropic": labelRes = R.string.settings_aniso; valuesRes = R.array.settings_aniso_levels; break;
            case "Square Pixels": labelRes = R.string.settings_square; valuesRes = R.array.settings_off_on; break;
            case "Frame Rate": labelRes = R.string.settings_frame_rate; valuesRes = R.array.settings_frame_rates; break;
            case "Blur/Bloom": labelRes = R.string.settings_blur; valuesRes = R.array.settings_off_on; break;
            case "FSR Sharpness": labelRes = R.string.settings_fsr_sharpness; valuesRes = R.array.settings_fsr_sharpness_levels; break;
            default: break;
        }
        String[] translated = valuesRes != 0 ? activity.getResources().getStringArray(valuesRes) : null;
        final String[] values = translated != null && translated.length == nativeValues.length ? translated : nativeValues;
        addRow(labelRes != 0 ? activity.getString(labelRes) : label, new Row() {
            @Override public String[] values() { return values; }
            @Override public int get() { return GameActivity.nativeOptionGet(index); }
            @Override public void set(int value) { GameActivity.nativeOptionSet(index, value); }
        });
    }

    private void addRow(String label, final Row row) {
        final LinearLayout line = new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(44));
        line.setPadding(dp(8), 0, dp(4), 0);
        line.setFocusable(true);
        line.setClickable(true);
        StateListDrawable highlight = new StateListDrawable();
        highlight.addState(new int[] {android.R.attr.state_focused}, new ColorDrawable(0x4055BCD8));
        highlight.addState(new int[] {android.R.attr.state_pressed}, new ColorDrawable(0x3055BCD8));
        highlight.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        line.setBackground(highlight);

        TextView name = new TextView(activity);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(15);
        line.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        final TextView value = new TextView(activity);
        value.setTextColor(0xFFB8E9F5);
        value.setTextSize(15);
        value.setGravity(Gravity.CENTER);
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                String[] values = row.values();
                int current = Math.max(0, Math.min(values.length - 1, row.get()));
                value.setText(values[current]);
            }
        };
        refresh.run();
        Button left = arrow("‹"), right = arrow("›");
        left.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { step(row, -1); refresh.run(); }
        });
        right.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { step(row, 1); refresh.run(); }
        });
        line.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { step(row, 1); refresh.run(); }
        });
        // Controller: up/down move between rows (default focus), left/right change the value.
        line.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    step(row, keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    refresh.run();
                }
                return true;
            }
        });
        line.addView(left, new LinearLayout.LayoutParams(dp(40), dp(40)));
        line.addView(value, new LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));
        line.addView(right, new LinearLayout.LayoutParams(dp(40), dp(40)));
        rows.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static void step(Row row, int delta) {
        int n = row.values().length;
        if (n > 0) row.set(((row.get() + delta) % n + n) % n);
    }

    private TextView text(int res, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(res);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button arrow(String symbol) {
        Button button = new Button(activity);
        button.setText(symbol);
        button.setTextSize(20);
        button.setPadding(0, 0, 0, 0);
        button.setFocusable(false);      // the row keeps the controller focus
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setTextColor(0xFFF3F6F7);
        return button;
    }

    private Button button(int res) {
        Button button = new Button(activity);
        button.setText(res);
        button.setAllCaps(false);
        return button;
    }

    private void confirmExit() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.quick_exit)
                .setMessage(R.string.quick_exit_confirm)
                .setPositiveButton(R.string.quick_exit, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface confirmation, int which) {
                        dialog.dismiss();
                        activity.exitGame();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
