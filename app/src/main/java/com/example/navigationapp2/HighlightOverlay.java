package com.example.navigationapp2;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.util.Log;

/**
 * HighlightOverlay — draws a colored border + label over a UI element.
 *
 * Used by NavigationExecutor to highlight the next node for the user to tap,
 * and by SettingsNavigator to show directional arrows.
 *
 * Thread-safe: all WindowManager operations are posted to the main thread.
 * (WindowManager methods must run on the main thread — calling from the
 * accessibility or background thread causes CalledFromWrongThreadException.)
 */
public class HighlightOverlay {

    private static final String TAG = "NAV_APP";

    private static View          highlightView;
    private static TextView      textView;
    private static WindowManager windowManager;

    /** All WindowManager ops must run here */
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Show ─────────────────────────────────────────────────────────────────

    /**
     * Show a rectangular highlight at {@code bounds} with a text {@code message}.
     * Safe to call from any thread.
     *
     * @param context Any context with overlay permission (typically the AccessibilityService)
     * @param bounds  Screen-coordinate rectangle to highlight
     * @param message Label shown above the rectangle
     */
    public static void show(Context context, Rect bounds, String message, Runnable onClickCallback) {
        mainHandler.post(() -> showOnMainThread(context, bounds, message, onClickCallback));
    }

    private static void showOnMainThread(Context context, Rect bounds, String message, Runnable onClickCallback) {
        try {
            if (windowManager == null) {
                windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            }

            int width  = Math.max(1, bounds.width());
            int height = Math.max(1, bounds.height());

            WindowManager.LayoutParams boxParams = new WindowManager.LayoutParams(
                    width, height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            boxParams.gravity = Gravity.TOP | Gravity.START;
            boxParams.x = bounds.left;
            boxParams.y = bounds.top;

            WindowManager.LayoutParams textParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            textParams.gravity = Gravity.TOP | Gravity.START;
            textParams.x = bounds.left;
            textParams.y = Math.max(0, bounds.top - 80);

            if (highlightView == null || textView == null) {
                // Initialize views for the first time
                highlightView = new View(context);
                highlightView.setBackgroundResource(R.drawable.highlight_border);
                windowManager.addView(highlightView, boxParams);

                textView = new TextView(context);
                textView.setBackgroundColor(0xCC1A1A2E);
                textView.setTextColor(0xFFFFFFFF);
                textView.setTextSize(13f);
                textView.setPadding(24, 8, 24, 8);
                textView.setText(message);
                windowManager.addView(textView, textParams);
            } else {
                // Smoothly update existing views without destroying them
                textView.setText(message);
                windowManager.updateViewLayout(highlightView, boxParams);
                windowManager.updateViewLayout(textView, textParams);
            }

            // Assign the click listener to intercept the user action!
            highlightView.setOnClickListener(v -> {
                Log.d(TAG, "👆 Green box clicked directly by user!");
                if (onClickCallback != null) {
                    onClickCallback.run();
                }
            });

            Log.d(TAG, "🟢 HighlightOverlay shown/updated at " + bounds);

        } catch (Exception e) {
            Log.e(TAG, "❌ HighlightOverlay.show error: " + e.getMessage());
        }
    }

    // ─── Remove ───────────────────────────────────────────────────────────────

    /**
     * Remove the highlight overlay. Safe to call from any thread
     * (accessibility thread, background thread, main thread).
     */
    public static void remove() {
        mainHandler.post(HighlightOverlay::removeOnMainThread);
    }

    private static void removeOnMainThread() {
        try {
            if (windowManager == null) return;

            if (highlightView != null) {
                windowManager.removeView(highlightView);
                highlightView = null;
            }
            if (textView != null) {
                windowManager.removeView(textView);
                textView = null;
            }
        } catch (Exception e) {
            // View may already have been removed — safe to ignore
            Log.w(TAG, "⚠️ HighlightOverlay.remove: " + e.getMessage());
        }
    }
}