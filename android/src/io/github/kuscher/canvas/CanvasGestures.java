// SPDX-License-Identifier: MIT
package io.github.kuscher.canvas;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import java.util.ArrayList;

/**
 * Pinch and pan for Patchy's canvas, which Qt for Android doesn't provide:
 *
 * - Trackpad pinch: Android reports it as MotionEvents classified
 *   CLASSIFICATION_PINCH with a per-sample scale factor. They become Patchy's
 *   native zoom gesture (the one macOS trackpads send).
 * - Trackpad two-finger scroll: Android reports it as one fake finger
 *   (CLASSIFICATION_TWO_FINGER_SWIPE) dragging from the pointer, which Qt would
 *   take for a touch and paint with. It pans the view instead, the content
 *   following the fake finger as it does in scrolling views. Swipes with three
 *   or four fingers are the system's; they never reach Qt either.
 * - Touchscreen: a first finger is held back briefly. If a second one lands in
 *   that time, both fingers pinch to zoom and move to pan; otherwise the held
 *   events go to Qt, where one finger draws as before. A second finger that
 *   lands later cancels the stroke and starts the gesture.
 *
 * Pens and mice pass straight through. The zoom and pan land in C++
 * (android_window.cpp) through the native methods below, in window pixels.
 */
final class CanvasGestures {
    interface Sink {
        void dispatchToQt(MotionEvent event);
    }

    private static final long HOLD_MS = 90;
    private static final float TAP_SLOP_PX = 12f;
    // MotionEvent's three- and four-finger trackpad swipe, hidden from the SDK.
    private static final int CLASSIFICATION_MULTI_FINGER_SWIPE = 4;

    static native void nativeZoom(float x, float y, float factor);
    static native void nativePan(float x, float y, float dx, float dy);

    private final Sink sink;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<MotionEvent> held = new ArrayList<>();
    private boolean holding;
    private boolean gesture;
    private float downX;
    private float downY;
    private float lastSpan;
    private float lastX;
    private float lastY;
    private boolean trackpadPinch;
    private float scrollAnchorX;
    private float scrollAnchorY;
    private float scrollX;
    private float scrollY;
    private double pinchTotal; // CanvasDiag
    private int pinchSamples; // CanvasDiag

    CanvasGestures(Sink sink) {
        this.sink = sink;
    }

    /** True when the event was used for a gesture (or is held back for now). */
    boolean onTouch(MotionEvent event) {
        if (isTrackpadPinch(event)) {
            return onTrackpadPinch(event);
        }
        if (isTrackpadSwipe(event)) {
            return onTrackpadSwipe(event);
        }
        if (!isFinger(event) && !holding && !gesture) {
            return false;
        }
        int action = event.getActionMasked();
        if (gesture) {
            if (event.getPointerCount() >= 2 && action == MotionEvent.ACTION_MOVE) {
                track(event, true);
            } else if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
                // Re-anchor on the fingers that remain.
                if (event.getPointerCount() - (action == MotionEvent.ACTION_POINTER_UP ? 1 : 0) >= 2) {
                    track(event, false);
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                gesture = false;
            }
            return true;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                holding = true;
                downX = event.getX();
                downY = event.getY();
                hold(event);
                handler.postDelayed(this::flush, HOLD_MS);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (holding) {
                    dropHeld();
                } else {
                    // A second finger after drawing began: cancel the stroke first.
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    sink.dispatchToQt(cancel);
                    cancel.recycle();
                }
                gesture = true;
                android.util.Log.i("CanvasDiag", "touch gesture starts");
                track(event, false);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (holding) {
                    hold(event);
                    if (Math.hypot(event.getX() - downX, event.getY() - downY) > TAP_SLOP_PX) {
                        flush();
                    }
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (holding) {
                    hold(event);
                    flush();
                    return true;
                }
                return false;
            default:
                if (holding) {
                    hold(event);
                    return true;
                }
                return false;
        }
    }

    private static boolean isFinger(MotionEvent event) {
        return (event.getSource() & android.view.InputDevice.SOURCE_TOUCHSCREEN) == android.view.InputDevice.SOURCE_TOUCHSCREEN
                && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER;
    }

    private static boolean isTrackpadPinch(MotionEvent event) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && event.getClassification() == MotionEvent.CLASSIFICATION_PINCH;
    }

    private static boolean isTrackpadSwipe(MotionEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }
        int classification = event.getClassification();
        return classification == MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE
                || classification == CLASSIFICATION_MULTI_FINGER_SWIPE;
    }

    private boolean onTrackpadSwipe(MotionEvent event) {
        if (event.getClassification() != MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // The fake finger starts at the pointer: pan whatever is under it.
                scrollAnchorX = scrollX = event.getX();
                scrollAnchorY = scrollY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                float y = event.getY();
                if (x != scrollX || y != scrollY) {
                    nativePan(scrollAnchorX, scrollAnchorY, x - scrollX, y - scrollY);
                }
                scrollX = x;
                scrollY = y;
                break;
            }
            default:
                break;
        }
        return true;
    }

    private boolean onTrackpadPinch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = 0;
        float y = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            x += event.getX(i);
            y += event.getY(i);
        }
        x /= event.getPointerCount();
        y /= event.getPointerCount();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (!trackpadPinch) {
                pinchTotal = 1.0;
                pinchSamples = 0;
            }
            trackpadPinch = true;
            lastX = x;
            lastY = y;
        } else if (action == MotionEvent.ACTION_MOVE && trackpadPinch) {
            // Samples that arrived within one frame are batched into one event:
            // each carries its own step, so take them all.
            double factor = 1.0;
            for (int h = 0; h <= event.getHistorySize(); h++) {
                float step = h < event.getHistorySize()
                        ? event.getHistoricalAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR, h)
                        : event.getAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR);
                if (step > 0f) {
                    factor *= step;
                    pinchSamples++;
                }
            }
            if (Math.abs(factor - 1.0) > 0.0005) {
                pinchTotal *= factor;
                nativeZoom(lastX, lastY, (float) factor);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (trackpadPinch) {
                android.util.Log.i("CanvasDiag", "trackpad pinch: x" + (float) pinchTotal + " over " + pinchSamples
                        + " samples");
            }
            trackpadPinch = false;
        }
        return true;
    }

    private void track(MotionEvent event, boolean apply) {
        int skip = event.getActionMasked() == MotionEvent.ACTION_POINTER_UP ? event.getActionIndex() : -1;
        float sx = 0;
        float sy = 0;
        int n = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skip) {
                continue;
            }
            sx += event.getX(i);
            sy += event.getY(i);
            n++;
        }
        if (n < 2) {
            return;
        }
        float cx = sx / n;
        float cy = sy / n;
        float span = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i != skip) {
                span += (float) Math.hypot(event.getX(i) - cx, event.getY(i) - cy);
            }
        }
        span /= n;
        if (apply) {
            if (lastSpan > 0f && span > 0f && Math.abs(span / lastSpan - 1f) > 0.002f) {
                nativeZoom(cx, cy, span / lastSpan);
            }
            float dx = cx - lastX;
            float dy = cy - lastY;
            if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                nativePan(cx, cy, dx, dy);
            }
        }
        lastSpan = span;
        lastX = cx;
        lastY = cy;
    }

    private void hold(MotionEvent event) {
        held.add(MotionEvent.obtain(event));
    }

    private void dropHeld() {
        handler.removeCallbacksAndMessages(null);
        for (MotionEvent e : held) {
            e.recycle();
        }
        held.clear();
        holding = false;
    }

    /** The finger is drawing after all: give Qt what was held back. */
    private void flush() {
        if (!holding) {
            return;
        }
        handler.removeCallbacksAndMessages(null);
        holding = false;
        for (MotionEvent e : held) {
            sink.dispatchToQt(e);
            e.recycle();
        }
        held.clear();
    }
}
