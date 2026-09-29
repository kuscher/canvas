// SPDX-License-Identifier: MIT
package io.github.kuscher.canvas;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
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
        // Keep the caption bar's layout current for C++ (captionLayout()).
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        measureCaption();
                    }
                });
    }

    private volatile int[] caption = {0, 0, 0};
    private final CanvasGestures gestures = new CanvasGestures(this::dispatchToQt);

    private void dispatchToQt(MotionEvent event) {
        super.dispatchTouchEvent(event);
    }

    // Diagnostics for the "the window flashes" report: lifecycle and configuration
    // changes, logged with the tag CanvasDiag.
    @Override
    public void onConfigurationChanged(Configuration configuration) {
        Log.i("CanvasDiag", "configuration " + configuration);
        super.onConfigurationChanged(configuration);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.i("CanvasDiag", "focus " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL && event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            // The desktop caption over the menu bar can take the pointer right
            // after a mouse or trackpad button goes down on it (it may become
            // a window drag), so the menu bar sees its press cancelled. Qt for
            // Android drops mouse cancels and would keep the button held,
            // sending every later pointer move to the pressed window: end the
            // press where it began instead.
            MotionEvent up = MotionEvent.obtain(event);
            up.setAction(MotionEvent.ACTION_UP);
            boolean handled = gestures.onTouch(up) || super.dispatchTouchEvent(up);
            up.recycle();
            return handled;
        }
        return gestures.onTouch(event) || super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return gestures.onTouch(event) || super.dispatchGenericMotionEvent(event);
    }

    /**
     * Where the desktop caption bar sits over the window, in window pixels: its height,
     * and how much of its left and right the system's own controls take (the app icon
     * and menu, the window buttons). Canvas puts Patchy's menu bar in the rest of it.
     */
    private void measureCaption() {
        View decor = getWindow().getDecorView();
        WindowInsets insets = decor.getRootWindowInsets();
        if (insets == null) {
            return;
        }
        int height = insets.getInsets(WindowInsets.Type.captionBar()).top;
        int width = decor.getWidth();
        int left = 0;
        int right = 0;
        if (height > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            for (Rect r : insets.getBoundingRects(WindowInsets.Type.captionBar())) {
                if (r.centerX() < width / 2) {
                    left = Math.max(left, r.right);
                } else {
                    right = Math.max(right, width - r.left);
                }
            }
        }
        int[] next = {height, left, right};
        if (next[0] != caption[0] || next[1] != caption[1] || next[2] != caption[2]) {
            Log.i("CanvasDiag", "caption " + height + " " + left + " " + right);
        }
        caption = next;
    }

    /** Called from C++: {height, left, right} of the caption bar in window pixels. */
    public static int[] captionLayout() {
        CanvasActivity activity = current;
        return activity != null ? activity.caption : new int[] {0, 0, 0};
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
        View decor = getWindow().peekDecorView();
        if (decor == null) {
            // Not laid out yet (a relaunched activity can get here first): try again once it is.
            getWindow().getDecorView().post(this::applyCaption);
            return;
        }
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
