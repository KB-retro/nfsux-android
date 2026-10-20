package org.nfsu.android;

import java.io.File;
import android.net.Uri;
import android.content.Context;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.SurfaceHolder;
import org.libsdl.app.SDLSurface;
import org.libsdl.app.SDLActivity;

/** SDL hosts the game, controller input and audio on Android. */
public final class GameActivity extends SDLActivity {
    private android.app.Dialog portCredits;
    private GameRumble gameRumble;

    // Invoked from the native stack; serialize commands with Activity lifecycle.
    public void setGameRumble(final int left, final int right) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (!isFinishing() && !isDestroyed() && gameRumble != null)
                    gameRumble.setSpeeds(left, right);
            }
        });
    }


    // Called through JNI on the guest thread; all window work belongs to UI.
    public void showPortCredits() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (isFinishing() || isDestroyed()
                        || (portCredits != null && portCredits.isShowing())) return;
                raceControls.cancelInput();
                portCredits = PortCreditsDialog.show(GameActivity.this);
                portCredits.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(android.content.DialogInterface dialog) {
                        portCredits = null;
                        restoreFullscreen();
                    }
                });
            }
        });
    }

    @Override protected void onDestroy() {
        if (portCredits != null) {
            portCredits.dismiss();
            portCredits = null;
        }
        if (quickMenu != null) {
            quickMenu.dismiss();
            quickMenu = null;
        }
        if (gameRumble != null) gameRumble.setActive(false);
        super.onDestroy();
    }

    private RaceControlsView raceControls;
    private android.widget.TextView fpsOverlay;
    private int fpsLastFrames = -1;
    private long fpsLastTime;
    private final Runnable fpsUpdate = new Runnable() {
        @Override public void run() {
            int frames;
            try {
                frames = nativePresentedFrames();
            } catch (UnsatisfiedLinkError error) {
                return; // Native library not loaded: no overlay.
            }
            long now = android.os.SystemClock.elapsedRealtime();
            if (fpsLastFrames >= 0 && now > fpsLastTime)
                fpsOverlay.setText(Math.round((frames - fpsLastFrames) * 1000f / (now - fpsLastTime)) + " FPS");
            fpsLastFrames = frames;
            fpsLastTime = now;
            fpsOverlay.postDelayed(this, 1000);
        }
    };
    private TouchLayoutStore touchLayoutStore;
    private android.view.View editorToolbar;
    private TouchPresetController touchPresets;

    private File gameDirectory() {
        File directory = getExternalFilesDir(null);
        return directory != null ? directory : getFilesDir();
    }

    /** Touch control layout editor; also opened from the quick menu. */
    void openTouchEditor() {
        if (editorToolbar != null) return;
        raceControls.beginEditing();
        android.widget.LinearLayout toolbar = new android.widget.LinearLayout(this);
        toolbar.setOrientation(android.widget.LinearLayout.VERTICAL);
        toolbar.setPadding(12, 4, 12, 4);
        toolbar.setBackgroundColor(0xEE102F40);
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText(R.string.editor_hint);
        title.setPadding(8, 8, 8, 8);
        title.setOnTouchListener(new View.OnTouchListener() {
            private float offsetX, offsetY;
            @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    offsetX = event.getRawX() - toolbar.getX();
                    offsetY = event.getRawY() - toolbar.getY();
                } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    toolbar.setX(Math.max(0, Math.min(mLayout.getWidth() - toolbar.getWidth(),
                            event.getRawX() - offsetX)));
                    toolbar.setY(Math.max(0, Math.min(mLayout.getHeight() - toolbar.getHeight(),
                            event.getRawY() - offsetY)));
                }
                return true;
            }
        });
        title.setTextColor(android.graphics.Color.WHITE);
        toolbar.addView(title);
        android.widget.LinearLayout actions = new android.widget.LinearLayout(this);
        toolbar.addView(actions);
        addEditorButton(actions, getString(R.string.action_save), new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    touchLayoutStore.save(raceControls.getLayout());
                    closeTouchEditor(true);
                } catch (java.io.IOException exception) {
                    android.util.Log.e("TouchLayout", "Cannot save layout", exception);
                    android.widget.Toast.makeText(GameActivity.this,
                            R.string.editor_save_failed, android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
        addEditorButton(actions, getString(R.string.editor_reset), new View.OnClickListener() {
            @Override public void onClick(View view) { raceControls.resetLayout(); }
        });
        addEditorButton(actions, getString(R.string.action_cancel), new View.OnClickListener() {
            @Override public void onClick(View view) { closeTouchEditor(false); }
        });
        android.content.SharedPreferences editorPreferences =
                getSharedPreferences("touch-editor", MODE_PRIVATE);
        android.widget.CheckBox snap = new android.widget.CheckBox(this);
        snap.setText(R.string.editor_snap);
        snap.setTextColor(android.graphics.Color.WHITE);
        snap.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF55BCD8));
        snap.setChecked(editorPreferences.getBoolean("snap_to_grid", false));
        raceControls.setSnapToGrid(snap.isChecked());
        snap.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton button, boolean checked) {
                raceControls.setSnapToGrid(checked);
                editorPreferences.edit().putBoolean("snap_to_grid", checked).apply();
            }
        });
        toolbar.addView(snap);
        android.widget.TextView sizeLabel = new android.widget.TextView(this);
        sizeLabel.setTextColor(android.graphics.Color.WHITE);
        toolbar.addView(sizeLabel);
        android.widget.SeekBar sizeSlider = new android.widget.SeekBar(this);
        sizeSlider.setMax(Math.round((TouchLayout.MAX_SIZE - TouchLayout.MIN_SIZE)*100));
        toolbar.addView(sizeSlider);
        Runnable updateSize = new Runnable() {
            @Override public void run() {
                int selected = raceControls.getSelectedButton();
                sizeSlider.setEnabled(selected >= 0);
                sizeSlider.setProgress(Math.round((raceControls.getSelectedSize() - TouchLayout.MIN_SIZE)*100));
                sizeLabel.setText(selected < 0 ? getString(R.string.editor_select_button)
                        : TouchLayout.IDS[selected] + " • " + Math.round(raceControls.getSelectedSize()*100) + "%");
            }
        };
        raceControls.setSelectionChanged(updateSize);
        sizeSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar slider, int progress, boolean fromUser) {
                if (fromUser) {
                    raceControls.resizeSelected(TouchLayout.MIN_SIZE + progress/100f);
                    updateSize.run();
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar slider) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar slider) { }
        });
        updateSize.run();
        touchPresets = new TouchPresetController(this, raceControls, gameDirectory(), updateSize);
        addEditorButton(actions, getString(R.string.editor_presets), new View.OnClickListener() {
            @Override public void onClick(View view) { touchPresets.show(); }
        });
        editorToolbar = toolbar;
        android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        mLayout.addView(toolbar, params);
    }

    private void addEditorButton(android.widget.LinearLayout parent, String text,
            View.OnClickListener listener) {
        android.widget.Button button = new android.widget.Button(this);
        button.setText(text);
        button.setOnClickListener(listener);
        parent.addView(button);
    }

    private void closeTouchEditor(boolean save) {
        if (editorToolbar == null) return;
        mLayout.removeView(editorToolbar);
        editorToolbar = null;
        touchPresets = null;
        raceControls.setSelectionChanged(null);
        raceControls.endEditing(save);
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        if (touchPresets != null && touchPresets.onActivityResult(request, result, data)) return;
        super.onActivityResult(request, result, data);
    }

    // Back opens the quick menu; leaving the game is its "Exit game" entry.
    @Override public void onBackPressed() {
        if (editorToolbar != null) closeTouchEditor(false);
        else showQuickMenu();
    }

    private android.app.Dialog quickMenu;
    private boolean selectHeld, startHeld;

    private void showQuickMenu() {
        if (isFinishing() || isDestroyed() || (quickMenu != null && quickMenu.isShowing())) return;
        if (raceControls != null) raceControls.cancelInput();
        selectHeld = startHeld = false;
        quickMenu = QuickMenuDialog.show(this);
        quickMenu.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface dialog) {
                quickMenu = null;
                restoreFullscreen();
            }
        });
    }

    /** The quick menu's "Exit game": the system back behaviour this activity replaced. */
    void exitGame() {
        super.onBackPressed();
    }

    /** New settings need a new native game: the launcher starts it again in a fresh :game process. */
    void restartGame() {
        android.content.Intent launcher = new android.content.Intent(this, LauncherActivity.class)
            .putExtra(LauncherActivity.EXTRA_RESTART, true)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(launcher);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (editorToolbar != null) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP
                    && event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) closeTouchEditor(false);
            return true;
        }
        // Controller: Select + Start together open the quick menu (Start still
        // reaches the game first, which pauses a race).
        int code = event.getKeyCode();
        if (code == android.view.KeyEvent.KEYCODE_BUTTON_SELECT || code == android.view.KeyEvent.KEYCODE_BUTTON_START) {
            boolean down = event.getAction() == android.view.KeyEvent.ACTION_DOWN;
            if (code == android.view.KeyEvent.KEYCODE_BUTTON_SELECT) selectHeld = down;
            else startHeld = down;
            if (down && event.getRepeatCount() == 0 && selectHeld && startHeld) {
                boolean handled = super.dispatchKeyEvent(event);
                showQuickMenu();
                return handled;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    // Activity is the sole owner of system bars. SDL owns the surface/events.
    private final Runnable fullscreen = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        }
    };

    private void restoreFullscreen() {
        View decor = getWindow().getDecorView();
        decor.removeCallbacks(fullscreen);
        // Run after SDL's initial window-style command and focus processing.
        decor.post(fullscreen);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        touchLayoutStore = new TouchLayoutStore(gameDirectory());
        raceControls = new RaceControlsView(this);
        raceControls.setLayout(touchLayoutStore.load());
        raceControls.setEditorRequest(new Runnable() {
            @Override public void run() { openTouchEditor(); }
        });
        mLayout.addView(raceControls, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        setFpsOverlayVisible(LauncherSettings.showFps(this));
        restoreFullscreen();
    }

    /** FPS counter in the top-left corner; the quick menu switches it at run time. */
    void setFpsOverlayVisible(boolean show) {
        if (show && fpsOverlay == null) {
            fpsOverlay = new android.widget.TextView(this);
            fpsOverlay.setTextColor(0xFFB8E9F5);
            fpsOverlay.setTextSize(13);
            fpsOverlay.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            fpsOverlay.setShadowLayer(3, 1, 1, android.graphics.Color.BLACK);
            fpsOverlay.setPadding(24, 12, 24, 12);
            fpsOverlay.setText("-- FPS");
            mLayout.addView(fpsOverlay, new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            fpsLastFrames = -1;
            fpsOverlay.post(fpsUpdate);
        } else if (!show && fpsOverlay != null) {
            fpsOverlay.removeCallbacks(fpsUpdate);
            mLayout.removeView(fpsOverlay);
            fpsOverlay = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameRumble == null) gameRumble = new GameRumble(this);
        gameRumble.setActive(true);
        if (raceControls != null) raceControls.resumeControls();
        if (fpsOverlay != null) {
            fpsLastFrames = -1;
            fpsOverlay.removeCallbacks(fpsUpdate);
            fpsOverlay.post(fpsUpdate);
        }
        restoreFullscreen();
    }

    @Override
    protected void onPause() {
        // A document picker is part of the editor session, not an abandoned edit.
        if (touchPresets == null || !touchPresets.isPickingDocument()) closeTouchEditor(false);
        if (raceControls != null) raceControls.pauseControls();
        if (fpsOverlay != null) fpsOverlay.removeCallbacks(fpsUpdate);
        if (gameRumble != null) gameRumble.setActive(false);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) restoreFullscreen();
        else if (raceControls != null) raceControls.cancelInput();
    }

    private static native int nativePresentedFrames();
    // Quick menu (recomp_manual.c, android_perf_hint.c).
    static native int nativeOptionCount();
    static native String nativeOptionLabel(int index);
    static native String[] nativeOptionValues(int index);
    static native int nativeOptionGet(int index);
    static native void nativeOptionSet(int index, int value);
    static native void nativeSetFastCores(boolean on);
    static native boolean nativeTouchWithController();
    static native void nativeSetTouchWithController(boolean on);

    private static native void nativeSurfaceChanged(Surface surface, int width, int height);

    @Override
    protected SDLSurface createSDLSurface(Context context) {
        return new SDLSurface(context) {
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                nativeSurfaceChanged(holder.getSurface(), width, height);
                super.surfaceChanged(holder, format, width, height);
                if (GameActivity.this.hasWindowFocus()) restoreFullscreen();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                nativeSurfaceChanged(null, 0, 0);
                super.surfaceDestroyed(holder);
            }
        };
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "c++_shared", "SDL2", "nfsu2_recomp" };
    }

    /**
     * Automatic picture format: unless the launcher or nfsu1x_env.txt sets
     * RECOMP_WIDESCREEN, screens narrower than 3:2 (4:3, 5:4) get the
     * original 4:3 picture instead of a letterboxed 16:9 one.
     */
    private void chooseAspect() {
        try {
            if (new LauncherSettings(this).load().env.containsKey("RECOMP_WIDESCREEN")) return;
        } catch (java.io.IOException exception) {
            android.util.Log.w("NFSU", "Cannot read launcher settings", exception);
        }
        android.graphics.Rect bounds = getWindowManager().getMaximumWindowMetrics().getBounds();
        float ratio = (float) Math.max(bounds.width(), bounds.height())
            / Math.max(1, Math.min(bounds.width(), bounds.height()));
        try {
            android.system.Os.setenv("RECOMP_WIDESCREEN", ratio < 1.45f ? "0" : "1", true);
        } catch (android.system.ErrnoException exception) {
            android.util.Log.w("NFSU", "Cannot set RECOMP_WIDESCREEN", exception);
        }
    }

    @Override
    protected String[] getArguments() {
        File root = gameDirectory();
        String selected = getSharedPreferences("game", MODE_PRIVATE).getString("tree", null);
        if (selected == null) throw new IllegalStateException("Папка игры не выбрана");
        chooseAspect();
        SafAccess.configure(getContentResolver(), Uri.parse(selected));
        SafAccess.bindNative();
        return new String[] { "/saf-game", root.getAbsolutePath() };
    }
}
