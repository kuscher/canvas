// SPDX-License-Identifier: MIT
package io.github.kuscher.canvas;

import android.app.ActivityManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsetsController;

import org.qtproject.qt.android.bindings.QtActivity;

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
        super.onCreate(savedInstanceState);
        current = this;
        applyCaption();
    }

    @Override
    public void onDestroy() {
        if (current == this) {
            current = null;
        }
        super.onDestroy();
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
