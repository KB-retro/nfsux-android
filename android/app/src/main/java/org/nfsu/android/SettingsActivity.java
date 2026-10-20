package org.nfsu.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;

/** Launcher settings: the in-game Camera rows plus runtime switches, applied at the next start. */
public final class SettingsActivity extends Activity {
    private static final int EXPORT_SAVES = 1, IMPORT_SAVES = 2;
    private LauncherSettings settings;
    private LinearLayout rows;
    private Spinner frameRate, scale, antialiasing, aniso, aspect, language, fsrSharpness;
    private Switch reflections, blur, square, showFps, fastCores, autoStart;
    private SeekBar volume;
    private int initialScale;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        View panel = findViewById(R.id.settings_panel);
        int width = Math.min(getResources().getConfiguration().screenWidthDp - 32, 640);
        panel.getLayoutParams().width = (int) (width * getResources().getDisplayMetrics().density);
        rows = findViewById(R.id.settings_rows);
        settings = new LauncherSettings(this);
        try {
            settings.load();
        } catch (IOException exception) {
            Log.w("NFSU", "Cannot read settings", exception);
        }

        section(R.string.settings_section_graphics);
        frameRate = choice(R.string.settings_frame_rate, R.array.settings_frame_rates,
            settings.option("fps", 60) == 60 ? 1 : 0);
        // 1.5x when nothing chose a scale yet, as in recomp_manual.c.
        initialScale = LauncherSettings.indexOf(LauncherSettings.SCALES, settings.option("scale", 150), 1);
        scale = choice(R.string.settings_scale, R.array.settings_scales, initialScale);
        // Same values as Options -> Camera in game: fxaa 0 off, 1 FXAA, 2 FSR 1.0.
        antialiasing = choice(R.string.settings_antialiasing, R.array.settings_antialiasing_modes,
            Math.max(0, Math.min(2, settings.option("fxaa", 1))));
        fsrSharpness = choice(R.string.settings_fsr_sharpness, R.array.settings_fsr_sharpness_levels,
            Math.max(0, Math.min(2, settings.option("fsr_sharpness", 1))));
        hint(R.string.settings_fsr_hint);
        aniso = choice(R.string.settings_aniso, R.array.settings_aniso_levels,
            LauncherSettings.indexOf(LauncherSettings.ANISO, settings.option("aniso", 16), 4));
        reflections = toggle(R.string.settings_reflections, settings.option("reflections", 1) != 0);
        blur = toggle(R.string.settings_blur, settings.option("blur", 1) != 0);
        square = toggle(R.string.settings_square, settings.option("square", 1) != 0);
        // Automatic leaves RECOMP_WIDESCREEN unset; GameActivity then picks it from the screen.
        String widescreen = settings.env.get("RECOMP_WIDESCREEN");
        aspect = choice(R.string.settings_aspect, R.array.settings_aspects,
            widescreen == null ? 0 : widescreen.startsWith("0") ? 2 : 1);
        showFps = toggle(R.string.settings_show_fps, LauncherSettings.showFps(this));
        fastCores = toggle(R.string.settings_fast_cores, settings.envValue("RECOMP_FAST_CORES", 0) == 1);
        hint(R.string.settings_fast_cores_hint);

        section(R.string.settings_section_audio);
        volume = slider(R.string.settings_volume, settings.envValue("RECOMP_AUDIO_VOLUME", 100));

        section(R.string.settings_section_language);
        language = choice(R.string.settings_language, R.array.settings_languages,
            LauncherSettings.indexOf(LauncherSettings.LANGUAGES, settings.envValue("RECOMP_LANGUAGE", 5), 0));
        hint(R.string.settings_language_hint);

        section(R.string.settings_section_saves);
        action(R.string.settings_export_saves, new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE, "nfsu-saves-"
                    + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(new java.util.Date()) + ".zip");
                pick(intent, EXPORT_SAVES);
            }
        });
        action(R.string.settings_import_saves, new View.OnClickListener() {
            @Override public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                pick(intent, IMPORT_SAVES);
            }
        });
        hint(R.string.settings_saves_hint);

        section(R.string.settings_section_launcher);
        autoStart = toggle(R.string.settings_auto_start, LauncherSettings.autoStart(this));

        findViewById(R.id.settings_cancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        findViewById(R.id.settings_save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { save(); }
        });
    }

    private void save() {
        settings.options.put("fps", frameRate.getSelectedItemPosition() == 1 ? "60" : "30");
        // Leave RECOMP_GL_SCALE in charge until a scale is chosen here or in game.
        if (settings.options.containsKey("scale") || scale.getSelectedItemPosition() != initialScale)
            settings.options.put("scale", Integer.toString(LauncherSettings.SCALES[scale.getSelectedItemPosition()]));
        settings.options.put("fxaa", Integer.toString(antialiasing.getSelectedItemPosition()));
        settings.options.put("fsr_sharpness", Integer.toString(fsrSharpness.getSelectedItemPosition()));
        settings.options.put("aniso", Integer.toString(LauncherSettings.ANISO[aniso.getSelectedItemPosition()]));
        settings.options.put("reflections", reflections.isChecked() ? "1" : "0");
        settings.options.put("blur", blur.isChecked() ? "1" : "0");
        settings.options.put("square", square.isChecked() ? "1" : "0");
        // Earlier builds wrote RECOMP_HORPLUS here, which only stretched 4:3 to 16:9.
        settings.env.remove("RECOMP_HORPLUS");
        if (aspect.getSelectedItemPosition() == 0) settings.env.remove("RECOMP_WIDESCREEN");
        else settings.env.put("RECOMP_WIDESCREEN", aspect.getSelectedItemPosition() == 1 ? "1" : "0");
        // FSR now lives in the options file, shared with the in-game rows.
        settings.env.remove("RECOMP_VK_FSR");
        settings.env.remove("RECOMP_VK_FSR_SHARPNESS");
        settings.env.put("RECOMP_FAST_CORES", fastCores.isChecked() ? "1" : "0");
        settings.env.put("RECOMP_AUDIO_VOLUME", Integer.toString(volume.getProgress()));
        settings.env.put("RECOMP_LANGUAGE",
            Integer.toString(LauncherSettings.LANGUAGES[language.getSelectedItemPosition()]));
        try {
            settings.save();
        } catch (IOException exception) {
            Log.w("NFSU", "Cannot save settings", exception);
            Toast.makeText(this, R.string.settings_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        LauncherSettings.setAutoStart(this, autoStart.isChecked());
        LauncherSettings.setShowFps(this, showFps.isChecked());
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void pick(Intent intent, int request) {
        try {
            startActivityForResult(intent, request);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.preset_picker_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (request == EXPORT_SAVES) {
            try {
                int files = SaveBackup.export(this, uri);
                Toast.makeText(this, getString(R.string.settings_saves_exported, files), Toast.LENGTH_LONG).show();
            } catch (IOException | SecurityException exception) {
                Log.w("NFSU", "Cannot export saves", exception);
                Toast.makeText(this, R.string.settings_saves_export_failed, Toast.LENGTH_LONG).show();
            }
        } else if (request == IMPORT_SAVES) {
            new AlertDialog.Builder(this).setTitle(R.string.settings_import_saves)
                .setMessage(R.string.settings_import_confirm)
                .setPositiveButton(R.string.settings_import_saves, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { restore(uri); }
                })
                .setNegativeButton(R.string.action_cancel, null).show();
        }
    }

    private void restore(Uri uri) {
        try {
            int files = SaveBackup.restore(this, uri);
            Toast.makeText(this, getString(R.string.settings_saves_imported, files), Toast.LENGTH_LONG).show();
            // The restored options file replaces the values shown here.
            recreate();
        } catch (IOException | SecurityException exception) {
            Log.w("NFSU", "Cannot import saves", exception);
            Toast.makeText(this, R.string.settings_saves_import_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void action(int title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        rows.addView(button, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private TextView label(int text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void section(int title) {
        TextView view = label(title, 12, 0xFF95A4A8);
        view.setLetterSpacing(0.18f);
        view.setPadding(0, dp(20), 0, dp(4));
        rows.addView(view);
    }

    private void hint(int text) {
        TextView view = label(text, 12, 0xFF87929C);
        view.setPadding(0, dp(2), 0, 0);
        rows.addView(view);
    }

    private LinearLayout row(int title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        TextView name = label(title, 15, Color.WHITE);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        rows.addView(row);
        return row;
    }

    private Spinner choice(int title, int items, int selected) {
        LinearLayout row = row(title);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, items,
            android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(selected, adapter.getCount() - 1)));
        row.addView(spinner, new LinearLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT));
        return spinner;
    }

    private Switch toggle(int title, boolean checked) {
        LinearLayout row = row(title);
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        row.addView(toggle);
        // The whole row toggles, which also suits controller focus.
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggle.toggle(); }
        });
        return toggle;
    }

    private SeekBar slider(int title, int value) {
        LinearLayout row = row(title);
        final TextView percent = label(R.string.settings_volume, 15, 0xFFABB4BC);
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Math.max(0, Math.min(100, value)));
        percent.setText(bar.getProgress() + "%");
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                percent.setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        row.addView(bar, new LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(percent, new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));
        return bar;
    }
}
