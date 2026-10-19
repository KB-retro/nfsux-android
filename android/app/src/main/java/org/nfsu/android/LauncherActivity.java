package org.nfsu.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Selects the user's disc folder, opens the settings and starts the native game. */
public final class LauncherActivity extends Activity {
    private static final int PICK_GAME = 1;
    private static final long AUTO_START_DELAY_MS = 1500;
    private final ExecutorService folderChecker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStart = new Runnable() {
        @Override public void run() { startGame(); }
    };
    private TextView status;
    private Button chooseButton;
    private boolean folderReady;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String tree = getSharedPreferences("game", MODE_PRIVATE).getString("tree", null);
        showMenu("");
        if (tree != null) {
            checkFolder(Uri.parse(tree), false);
        }
    }

    private void showMenu(String message) {
        handler.removeCallbacks(autoStart);
        folderReady = false;
        setContentView(R.layout.activity_launcher);
        View panel = findViewById(R.id.launcher_panel);
        int width = Math.min(getResources().getConfiguration().screenWidthDp - 48, 560);
        panel.getLayoutParams().width = (int) (width * getResources().getDisplayMetrics().density);
        TextView credits = findViewById(R.id.launcher_credits);
        credits.setText(Html.fromHtml(getString(R.string.launcher_credits, BuildConfig.BUILD_COMMIT, BuildConfig.BUILD_COMMIT_FULL), Html.FROM_HTML_MODE_LEGACY));
        credits.setMovementMethod(LinkMovementMethod.getInstance());
        status = findViewById(R.id.launcher_status);
        status.setText(message);
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        chooseButton = findViewById(R.id.choose_game);
        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, PICK_GAME);
            }
        });
        findViewById(R.id.open_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handler.removeCallbacks(autoStart);
                if (folderReady) status.setText(R.string.launcher_ready_status);
                startActivity(new Intent(LauncherActivity.this, SettingsActivity.class));
            }
        });
        findViewById(R.id.play_game).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { startGame(); }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GAME || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        checkFolder(data.getData(), true);
    }

    private void checkFolder(final Uri tree, final boolean persistSelection) {
        if (chooseButton != null) {
            chooseButton.setEnabled(false);
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.launcher_checking);
        }
        folderChecker.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                SafAccess.OtherReleaseException otherRelease = null;
                try {
                    if (persistSelection) {
                        getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    SafAccess.configure(getContentResolver(), tree);
                    SafAccess.validate();
                    if (persistSelection && !getSharedPreferences("game", MODE_PRIVATE).edit().putString("tree", tree.toString()).commit())
                        throw new IOException("Could not persist the selected folder");
                } catch (SafAccess.OtherReleaseException exception) {
                    Log.w("NFSU", "Game folder holds another release", exception);
                    otherRelease = exception;
                    error = exception.getMessage();
                } catch (IOException | RuntimeException exception) {
                    Log.w("NFSU", "Game folder validation failed", exception);
                    error = exception.getMessage();
                    if (error == null) {
                        error = exception.toString();
                    }
                }
                final String result = error;
                final SafAccess.OtherReleaseException release = otherRelease;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (release != null) {
                            showMenu(getString(R.string.launcher_other_release, regionName(release.region)));
                        } else if (result != null) {
                            showMenu(getString(R.string.launcher_unavailable));
                        } else {
                            // Never start on its own after choosing a folder; Play does.
                            showReady(!persistSelection);
                        }
                    }
                });
            }
        });
    }

    /** The saved folder is valid: offer Play and Settings, or start after a short delay. */
    private void showReady(boolean allowAutoStart) {
        folderReady = true;
        ((TextView) findViewById(R.id.launcher_heading)).setText(R.string.launcher_ready_title);
        ((TextView) findViewById(R.id.launcher_description)).setText(R.string.launcher_ready_description);
        chooseButton.setEnabled(true);
        chooseButton.setText(R.string.launcher_change_folder);
        Button play = findViewById(R.id.play_game);
        play.setVisibility(View.VISIBLE);
        play.requestFocus();
        status.setVisibility(View.VISIBLE);
        if (allowAutoStart && LauncherSettings.autoStart(this)) {
            status.setText(R.string.launcher_auto_starting);
            handler.postDelayed(autoStart, AUTO_START_DELAY_MS);
        } else {
            status.setText(R.string.launcher_ready_status);
        }
    }

    private void startGame() {
        handler.removeCallbacks(autoStart);
        if (isFinishing() || isDestroyed()) return;
        Intent game = new Intent(LauncherActivity.this, GameActivity.class);
        game.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(game);
        finish();
    }

    private String regionName(int region) {
        if ((region & 4) != 0) return getString(R.string.launcher_region_pal);
        if ((region & 2) != 0) return getString(R.string.launcher_region_japan);
        return getString(R.string.launcher_region_other);
    }

    @Override
    protected void onPause() {
        // Leaving the launcher (settings, another app) cancels a pending auto-start.
        if (handler.hasCallbacks(autoStart)) {
            handler.removeCallbacks(autoStart);
            if (status != null) status.setText(R.string.launcher_ready_status);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoStart);
        folderChecker.shutdownNow();
        super.onDestroy();
    }
}
