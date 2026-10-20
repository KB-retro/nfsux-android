package org.nfsu.android;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings edited from the launcher before the game starts.
 *
 * Game options share nfsu1x_options.txt with the in-game Options -> Camera
 * rows (recomp_manual.c), so unknown keys such as sensor_mode are kept.
 * Runtime switches go to nfsu1x_launcher.txt, loaded by android_host.c
 * before nfsu1x_env.txt, which still overrides them.
 */
final class LauncherSettings {
    static final String OPTIONS_FILE = "nfsu1x_options.txt";
    static final String ENV_FILE = "nfsu1x_launcher.txt";

    /** XC_LANGUAGE values offered in the launcher, see xbox_language(). */
    static final int[] LANGUAGES = {1, 5, 4, 3, 6};
    static final int[] SCALES = {100, 150, 200, 250};
    static final int[] ANISO = {1, 2, 4, 8, 16};

    final Map<String, String> options = new LinkedHashMap<>();
    final Map<String, String> env = new LinkedHashMap<>();
    private final File directory;

    LauncherSettings(Context context) {
        File external = context.getExternalFilesDir(null);
        directory = external != null ? external : context.getFilesDir();
    }

    static boolean autoStart(Context context) {
        return context.getSharedPreferences("game", Context.MODE_PRIVATE).getBoolean("auto_start", false);
    }

    static void setAutoStart(Context context, boolean value) {
        context.getSharedPreferences("game", Context.MODE_PRIVATE).edit().putBoolean("auto_start", value).commit();
    }

    static boolean showFps(Context context) {
        return context.getSharedPreferences("game", Context.MODE_PRIVATE).getBoolean("show_fps", false);
    }

    static void setShowFps(Context context, boolean value) {
        context.getSharedPreferences("game", Context.MODE_PRIVATE).edit().putBoolean("show_fps", value).commit();
    }

    LauncherSettings load() throws IOException {
        read(new File(directory, OPTIONS_FILE), options);
        read(new File(directory, ENV_FILE), env);
        return this;
    }

    void save() throws IOException {
        write(new File(directory, OPTIONS_FILE), options, null);
        write(new File(directory, ENV_FILE), env,
            "# Written by the launcher settings. nfsu1x_env.txt overrides these values.\n");
    }

    /** Only nfsu1x_launcher.txt: the game process saves its options file itself. */
    void saveEnv() throws IOException {
        write(new File(directory, ENV_FILE), env,
            "# Written by the launcher settings. nfsu1x_env.txt overrides these values.\n");
    }

    int option(String key, int fallback) {
        return parse(options.get(key), fallback);
    }

    int envValue(String key, int fallback) {
        return parse(env.get(key), fallback);
    }

    private static int parse(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static int indexOf(int[] values, int value, int fallback) {
        for (int i = 0; i < values.length; i++)
            if (values[i] == value) return i;
        return fallback;
    }

    private static void read(File file, Map<String, String> into) throws IOException {
        into.clear();
        if (!file.isFile()) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                int equal = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || equal <= 0) continue;
                into.put(trimmed.substring(0, equal).trim(), trimmed.substring(equal + 1).trim());
            }
        }
    }

    private static void write(File file, Map<String, String> values, String header) throws IOException {
        File temporary = new File(file.getPath() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8)) {
            if (header != null) writer.write(header);
            for (Map.Entry<String, String> entry : values.entrySet())
                writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IOException("Cannot replace " + file);
        }
    }
}
