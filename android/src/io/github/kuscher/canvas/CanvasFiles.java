// SPDX-License-Identifier: MIT
package io.github.kuscher.canvas;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Document access for Patchy's path-based file pipeline (called from C++ over JNI,
 * see dialog_utils_android.cpp). Patchy reads and writes local files, so an opened
 * document is copied into a working copy in the app's storage and a save is copied
 * back to the document, the way Patchy's web build moves files in and out of the
 * browser.
 */
public final class CanvasFiles {
    private static final String TAG = "Canvas";

    private CanvasFiles() {}

    /** The document's display name ("Holiday.psd"), or null. */
    public static String displayName(Context context, String uri) {
        try (Cursor c = context.getContentResolver().query(Uri.parse(uri),
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "displayName " + uri + ": " + e);
        }
        String last = Uri.parse(uri).getLastPathSegment();
        return last == null ? null : last.substring(last.lastIndexOf('/') + 1);
    }

    /** Keeps read (and, where granted, write) access across restarts, for recent files and Save. */
    public static void persist(Context context, String uri) {
        ContentResolver resolver = context.getContentResolver();
        Uri u = Uri.parse(uri);
        int both = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        try {
            resolver.takePersistableUriPermission(u, both);
        } catch (SecurityException e) {
            try {
                resolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e2) {
                // Not a persistable grant (e.g. a one-off "Open with" from another app).
            }
        }
    }

    /** Copies the document into a local file. */
    public static boolean copyToFile(Context context, String uri, String path) {
        File target = new File(path);
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        File partial = new File(path + ".part");
        try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(uri));
             OutputStream out = new FileOutputStream(partial)) {
            if (in == null) {
                return false;
            }
            copy(in, out);
        } catch (Exception e) {
            Log.w(TAG, "copyToFile " + uri + ": " + e);
            partial.delete();
            return false;
        }
        return partial.renameTo(target);
    }

    /** Replaces the document's contents with a local file. */
    public static boolean copyFromFile(Context context, String path, String uri) {
        try (InputStream in = new FileInputStream(path);
             OutputStream out = context.getContentResolver().openOutputStream(Uri.parse(uri), "wt")) {
            if (out == null) {
                return false;
            }
            copy(in, out);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "copyFromFile " + uri + ": " + e);
            return false;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buffer = new byte[1 << 20];
        for (int n; (n = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, n);
        }
    }
}
