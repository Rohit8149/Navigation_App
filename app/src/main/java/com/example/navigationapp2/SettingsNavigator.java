package com.example.navigationapp2;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * SettingsNavigator — guides the user to find and open the Settings app
 * on their home screen before navigation begins.
 *
 * 📌 STATUS: Phase 2 (ready but not yet triggered automatically).
 *            Will be called by AssistantController in a future update
 *            when the guard detects the user is NOT on any Settings screen
 *            and needs step-by-step guidance to open it.
 *
 * Wired callbacks:
 *   showMessage(msg)      → OverlayService.instance.showStatus(msg)
 *   showArrow(direction)  → HighlightOverlay.show() with edge arrow
 *   onFound(node)         → HighlightOverlay.show() on the Settings icon
 *
 * To trigger manually (for testing):
 *   SettingsNavigator.start(NavigationAccessibilityService.instance, SettingsNavigator.buildDefaultCallback());
 */
public class SettingsNavigator {

    private static final String TAG = "NAV_APP";

    // ─── Step sequence ────────────────────────────────────────────────────────

    enum NavStep {
        START,
        GO_HOME,
        READ_UI,
        FIND_SETTINGS,
        SWIPE_PAGES,
        OPEN_FOLDER,
        SCROLL,
        FOUND,
        DONE
    }

    private static NavStep currentStep = NavStep.START;

    // ─── Callback interface ───────────────────────────────────────────────────

    public interface Callback {
        /** Show a descriptive message to the user (e.g. "Looking for Settings...") */
        void showMessage(String msg);
        /** Show a directional arrow hint: "LEFT", "RIGHT", "UP", "DOWN" */
        void showArrow(String direction);
        /** Settings icon was found — highlight it and tell the user to tap */
        void onFound(AccessibilityNodeInfo node);
    }

    // ─── Default wired callback (uses OverlayService + HighlightOverlay) ──────

    /**
     * Returns a callback that connects SettingsNavigator to the live overlay system.
     * Pass this to {@link #start(AccessibilityService, Callback)}.
     */
    public static Callback buildDefaultCallback(AccessibilityService service) {
        return new Callback() {

            @Override
            public void showMessage(String msg) {
                AccessibilityOverlay.showStatus(msg);
            }

            @Override
            public void showArrow(String direction) {
                // Map direction string to a screen-edge rectangle
                Rect arrowRect = getArrowRect(direction);
                String label  = directionEmoji(direction) + " Swipe " + direction.toLowerCase();
                HighlightOverlay.show(service, arrowRect, label, null);
            }

            @Override
            public void onFound(AccessibilityNodeInfo node) {
                if (node == null) return;
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                HighlightOverlay.show(service, bounds, "👆 Tap here to open Settings", null);
                Log.d(TAG, "✅ Settings icon highlighted at: " + bounds);
            }
        };
    }

    // ─── Main entry point ─────────────────────────────────────────────────────

    /**
     * Starts the step-by-step guidance to find and open Settings.
     * Runs asynchronously on the main thread with a 2-second step interval.
     *
     * @param service Active accessibility service (for UI tree reads)
     * @param cb      Callback for UI actions — use {@link #buildDefaultCallback(AccessibilityService)}
     */
    public static void start(AccessibilityService service, Callback cb) {
        currentStep = NavStep.START;
        Log.d(TAG, "🧭 SettingsNavigator started");

        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (currentStep == NavStep.DONE) return;

                AccessibilityNodeInfo root = service.getRootInActiveWindow();

                switch (currentStep) {

                    case START:
                        cb.showMessage("🧭 Looking for Settings app...");
                        currentStep = NavStep.GO_HOME;
                        break;

                    case GO_HOME:
                        cb.showMessage("🏠 Going to Home Screen");
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                        currentStep = NavStep.READ_UI;
                        break;

                    case READ_UI:
                        cb.showMessage("🔍 Reading screen...");
                        currentStep = NavStep.FIND_SETTINGS;
                        break;

                    case FIND_SETTINGS:
                        if (root != null) {
                            List<AccessibilityNodeInfo> nodes =
                                    root.findAccessibilityNodeInfosByText("Settings");
                            if (nodes != null && !nodes.isEmpty()) {
                                currentStep = NavStep.FOUND;
                                cb.onFound(nodes.get(0));
                                return; // wait for user tap, don't advance automatically
                            }
                        }
                        cb.showMessage("❌ Settings not visible — trying to scroll");
                        currentStep = NavStep.SWIPE_PAGES;
                        break;

                    case SWIPE_PAGES:
                        cb.showMessage("👉 Swipe right to find Settings");
                        cb.showArrow("RIGHT");
                        currentStep = NavStep.READ_UI;
                        break;

                    case OPEN_FOLDER:
                        cb.showMessage("📂 Open the app folder");
                        currentStep = NavStep.READ_UI;
                        break;

                    case SCROLL:
                        cb.showMessage("🔽 Scroll down to find Settings");
                        cb.showArrow("DOWN");
                        currentStep = NavStep.READ_UI;
                        break;

                    case FOUND:
                        cb.showMessage("✅ Tap Settings to open it");
                        currentStep = NavStep.DONE;
                        return;

                    case DONE:
                        return;
                }

                handler.postDelayed(this, 2_000);
            }
        });
    }

    // ─── Arrow positioning helpers ────────────────────────────────────────────

    /** Maps a direction string to a screen-edge rectangle for HighlightOverlay */
    private static Rect getArrowRect(String direction) {
        switch (direction.toUpperCase()) {
            case "RIGHT": return new Rect(900, 800, 1080, 1000);
            case "LEFT":  return new Rect(0, 800, 180, 1000);
            case "UP":    return new Rect(400, 0, 680, 100);
            case "DOWN":  return new Rect(400, 1800, 680, 1920);
            default:      return new Rect(400, 900, 680, 1000);
        }
    }

    private static String directionEmoji(String direction) {
        switch (direction.toUpperCase()) {
            case "RIGHT": return "👉";
            case "LEFT":  return "👈";
            case "UP":    return "👆";
            case "DOWN":  return "👇";
            default:      return "➡️";
        }
    }
}