package com.example.navigationapp2;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * OverlayService — pure window overlay UI manager.
 *
 * Responsibility: add and remove views to/from WindowManager.
 * It does NOT validate input, call APIs, or manage navigation state.
 * All business logic lives in {@link AssistantController}.
 *
 * Views owned:
 *   floatingIconView — draggable launcher button
 *   inputView        — centered text input dialog
 *   guardBannerView  — persistent top banner (stays until Settings opens)
 *   statusView       — short status toast (auto-dismiss 3 s)
 */
public class OverlayService extends Service {

    private static final String TAG = "NAV_APP";
    public  static OverlayService instance;

    private WindowManager windowManager;

    private View floatingIconView;
    private View inputView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Android Service lifecycle ────────────────────────────────────────────

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance      = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Initialise the orchestration brain here so it's ready before any UI event
        AssistantController.init();

        Log.d(TAG, "🔥 OverlayService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "START_ASSISTANT":
                    showFloatingIcon();
                    break;
                case "STOP_ASSISTANT":
                    if (AssistantController.instance != null)
                        AssistantController.instance.stop();
                    cleanupAllViews();
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (AssistantController.instance != null)
            AssistantController.instance.stop();
        cleanupAllViews();
        Log.d(TAG, "🗑 OverlayService destroyed");
    }

    // ─── Floating Icon ────────────────────────────────────────────────────────

    private void showFloatingIcon() {
        if (floatingIconView != null) return;
        try {
            ContextThemeWrapper ctx =
                    new ContextThemeWrapper(this, R.style.Theme_NavigationApp2);
            floatingIconView = LayoutInflater.from(ctx)
                    .inflate(R.layout.floating_icon, null);

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            p.gravity = Gravity.TOP | Gravity.START;
            p.x = 24; p.y = 80;
            windowManager.addView(floatingIconView, p);

            // Click → toggle input box
            ImageView icon = floatingIconView.findViewById(R.id.icon);
            icon.setOnClickListener(v -> {
                if (inputView != null) removeInputBox();
                else showInputBox();
            });

            // Drag
            floatingIconView.setOnTouchListener(new View.OnTouchListener() {
                int ix, iy; float tx, ty;
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            ix = p.x; iy = p.y;
                            tx = e.getRawX(); ty = e.getRawY();
                            return false;
                        case MotionEvent.ACTION_MOVE:
                            p.x = ix + (int)(e.getRawX() - tx);
                            p.y = iy + (int)(e.getRawY() - ty);
                            windowManager.updateViewLayout(floatingIconView, p);
                            return true;
                    }
                    return false;
                }
            });

            Log.d(TAG, "🟢 Floating icon shown");
        } catch (Exception e) {
            Log.e(TAG, "❌ showFloatingIcon: " + e.getMessage());
        }
    }

    // ─── Input Box ────────────────────────────────────────────────────────────

    public void showInputBox() {
        if (inputView != null) return;
        try {
            ContextThemeWrapper ctx =
                    new ContextThemeWrapper(this, R.style.Theme_NavigationApp2);
            inputView = LayoutInflater.from(ctx)
                    .inflate(R.layout.input_layout, null);

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            p.gravity = Gravity.CENTER;
            windowManager.addView(inputView, p);

            // Tap outside card → dismiss
            inputView.setOnTouchListener((v, e) -> {
                View card = ((android.view.ViewGroup) v).getChildAt(0);
                int[] loc = new int[2];
                card.getLocationOnScreen(loc);
                float x = e.getRawX(), y = e.getRawY();
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    if (x < loc[0] || x > loc[0] + card.getWidth()
                            || y < loc[1] || y > loc[1] + card.getHeight()) {
                        removeInputBox();
                        return true;
                    }
                }
                return false;
            });

            EditText editText = inputView.findViewById(R.id.editText);
            editText.requestFocus();
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);

            // Submit → hand off to AssistantController (no business logic here)
            inputView.findViewById(R.id.submitBtn).setOnClickListener(v -> {
                String text = editText.getText().toString().trim();
                if (text.length() < 3) {
                    editText.setHint("Please type a command");
                    editText.setHintTextColor(0xFFFF5555);
                    return;
                }
                removeInputBox(); // close dialog immediately for snappy UX
                if (AssistantController.instance != null) {
                    AssistantController.instance.handleUserInput(this, text);
                }
            });

            Log.d(TAG, "🟢 Input box shown");
        } catch (Exception e) {
            Log.e(TAG, "❌ showInputBox: " + e.getMessage());
        }
    }

    public void removeInputBox() {
        if (inputView != null) {
            try { windowManager.removeView(inputView); } catch (Exception ignored) {}
            inputView = null;
        }
    }


    // ─── Cleanup ──────────────────────────────────────────────────────────────

    private void cleanupAllViews() {
        if (floatingIconView != null) {
            try { windowManager.removeView(floatingIconView); } catch (Exception ignored) {}
            floatingIconView = null;
        }
        removeInputBox();
        AccessibilityOverlay.removeAll();
    }
}