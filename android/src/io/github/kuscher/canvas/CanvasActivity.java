// SPDX-License-Identifier: MIT
package io.github.kuscher.canvas;

import android.app.ActivityManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;

import org.qtproject.qt.android.bindings.QtActivity;

import java.io.File;

/**
 * Canvas's window: Qt's activity plus the desktop details Patchy can't reach from C++.
 *
 * In a desktop window the system draws the caption bar in colours taken from the
 * wallpaper, unrelated to the app, unless the app asks for a transparent one. Canvas
 * asks for that and puts its menu bar's colour (Patchy's title_bar_bg) behind it, so
 * the caption reads as part of the app. C++ calls setCaptionColor whenever Patchy's
 * light or dark scheme changes (android_window.cpp).
 */
public class CanvasActivity extends QtActivity {
    private static volatile CanvasActivity current;
    private int captionColor = 0xFF4F4F4F;
    private boolean captionDark = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // The manifest turns Qt's extraction of Android's widget styling off, but Qt
        // still loads a style.json an earlier build extracted; remove it first.
        deleteTree(new File(getApplicationInfo().dataDir, "qt-reserved-files/android-style"));
        super.onCreate(savedInstanceState);
        current = this;
        applyCaption();
        // Qt for Android aborts when an accessibility query holds its lock while the app
        // opens a menu (see main.cpp). Keep services out of the window's content so no
        // query can race a menu; TalkBack can't read Canvas until Qt fixes this.
        getWindow().getDecorView().setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    @Override
    public void onDestroy() {
        if (current == this) {
            current = null;
        }
        super.onDestroy();
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
    }

    /** The live activity, for the other Java helpers. */
    static CanvasActivity currentActivity() {
        return current;
    }

    /** Called from C++ on the Qt thread. */
    public static void setCaptionColor(final int argb, final boolean dark) {
        final CanvasActivity activity = current;
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            activity.captionColor = argb;
            activity.captionDark = dark;
            activity.applyCaption();
        });
    }

    private void applyCaption() {
        getWindow().setBackgroundDrawable(new ColorDrawable(captionColor));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(new ActivityManager.TaskDescription.Builder()
                    .setPrimaryColor(captionColor)
                    .setBackgroundColor(captionColor)
                    .build());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowInsetsController insets = getWindow().getInsetsController();
            if (insets != null) {
                int transparent = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND;
                int light = WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS;
                insets.setSystemBarsAppearance(transparent | (captionDark ? 0 : light), transparent | light);
            }
        }
    }
}
