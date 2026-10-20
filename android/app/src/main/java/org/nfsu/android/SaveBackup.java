package org.nfsu.android;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zips the game saves and options to a document, and restores them.
 *
 * Only save/UserData (profiles, career) and save/TitleData hold saves. The
 * save directory also has the emulated hard disk's Partition1..5.img: sparse
 * files that report about 5 GB, which the first version read in full on the
 * UI thread (a long black screen) and whose backups import then refused.
 */
final class SaveBackup {
    private static final String SAVE = "save";
    private static final String[] SAVE_DIRS = {"UserData", "TitleData"};
    private static final long MAX_BYTES = 256L << 20;

    private SaveBackup() { }

    private static File root(Context context) {
        File external = context.getExternalFilesDir(null);
        return external != null ? external : context.getFilesDir();
    }

    /** Returns the number of files written. */
    static int export(Context context, Uri target) throws IOException {
        File root = root(context);
        int[] count = {0};
        try (OutputStream output = context.getContentResolver().openOutputStream(target, "wt")) {
            if (output == null) throw new IOException("Cannot write " + target);
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (String dir : SAVE_DIRS)
                    add(zip, new File(new File(root, SAVE), dir), SAVE + "/" + dir, count);
                File options = new File(root, LauncherSettings.OPTIONS_FILE);
                if (options.isFile()) add(zip, options, LauncherSettings.OPTIONS_FILE, count);
            }
        }
        return count[0];
    }

    private static void add(ZipOutputStream zip, File file, String name, int[] count) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) add(zip, child, name + "/" + child.getName(), count);
            return;
        }
        if (!file.isFile()) return;
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream input = new FileInputStream(file)) {
            copy(input, zip, Long.MAX_VALUE);
        }
        zip.closeEntry();
        count[0]++;
    }

    /** Whether a backup entry is restored: UserData, TitleData or the options file. */
    private static boolean restorable(String name) {
        if (name.equals(LauncherSettings.OPTIONS_FILE)) return true;
        for (String dir : SAVE_DIRS)
            if (name.startsWith(SAVE + "/" + dir + "/")) return true;
        return false;
    }

    /** Restores a backup over the current saves. Older backups also carry the disk images: skipped. */
    static int restore(Context context, Uri source) throws IOException {
        File root = root(context).getCanonicalFile();
        int count = 0;
        long total = 0;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IOException("Cannot open " + source);
            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory()) continue;
                    if (!restorable(name)) continue;
                    File target = new File(root, name).getCanonicalFile();
                    // Reject entries that escape the app directory (zip slip).
                    if (!target.getPath().startsWith(root.getPath() + File.separator))
                        throw new IOException("Invalid entry " + name);
                    File parent = target.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                        throw new IOException("Cannot create " + parent);
                    try (OutputStream output = new FileOutputStream(target)) {
                        total += copy(zip, output, MAX_BYTES - total);
                    }
                    count++;
                }
            }
        }
        if (count == 0) throw new IOException("No saves in the backup");
        return count;
    }

    private static long copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) > 0) {
            copied += read;
            if (copied > limit) throw new IOException("Backup too large");
            output.write(buffer, 0, read);
        }
        return copied;
    }
}
