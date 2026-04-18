package com.example.navigationapp2;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * NavigationGuard — pure detection engine (NO UI).
 *
 * Responsibilities:
 *  - Track whether the foreground app is a known Settings screen
 *  - Fire a one-shot callback when Settings is first detected
 *  - Handle 30-second timeout (via callback to OverlayService for UI)
 *  - Support 11+ ROM package names out of the box
 *
 * ALL overlay / banner UI is owned by OverlayService so there is one
 * single WindowManager owner and zero race conditions.
 */
public class NavigationGuard {

    private static final String TAG = "NAV_APP";

    // ─── STEP 1: Multi-ROM Settings package list ──────────────────────────────
    private static final String[] SETTINGS_PACKAGES = {
            "com.android.settings",          // Stock Android / Pixel
            "com.miui.securitycenter",        // Xiaomi MIUI
            "com.miui.settings",              // Xiaomi alternate
            "com.samsung.android.settings",   // Samsung OneUI
            "com.lge.settings",               // LG
            "com.htc.settings",               // HTC
            "com.motorola.settings",          // Motorola
            "com.oneplus.settings",           // OnePlus OxygenOS
            "com.oppo.settings",              // OPPO ColorOS
            "com.realme.settings",            // Realme
            "com.vivo.settings",              // Vivo FuntouchOS
    };

    private static final long TIMEOUT_MS = 30_000L;

    // ─── STEP 2: State ────────────────────────────────────────────────────────
    private static boolean isGuardActive      = false;
    private static boolean isOnSettingsScreen = false;

    private static final Handler  mainHandler    = new Handler(Looper.getMainLooper());
    private static       Runnable timeoutRunnable;

    // ─── Callback ─────────────────────────────────────────────────────────────
    public interface OnSettingsReadyCallback {
        void onSettingsReady();
    }

    private static OnSettingsReadyCallback pendingCallback;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Arm the guard. Fires {@code callback} exactly once on the main thread
     * the first time a Settings package moves to the foreground.
     *
     * If Settings is already open when this is called, callback fires immediately.
     */
    public static void startGuard(OnSettingsReadyCallback callback) {
        isGuardActive   = true;
        pendingCallback = callback;
        Log.d(TAG, "🛡 NavigationGuard armed");

        if (isOnSettingsScreen) {
            Log.d(TAG, "✅ Already on Settings → firing immediately");
            fireCallback();
        } else {
            startTimeout();
        }
    }

    /**
     * Disarm the guard entirely (assistant stopped, or nav complete).
     */
    public static void stopGuard() {
        isGuardActive      = false;
        pendingCallback    = null;
        isOnSettingsScreen = false;
        cancelTimeout();
        Log.d(TAG, "🛑 NavigationGuard stopped");
    }

    // ─── STEP 8: Event-driven handler (called from onAccessibilityEvent) ──────

    /**
     * Drives the full guard state machine — no polling, no threads.
     *
     * @param nowOnSettings True if the service resolved the active window to be Settings
     * @param packageName Package of the app that generated the event
     */
    public static void updateGuardState(boolean nowOnSettings, String packageName) {
        // ── Entered Settings ──────────────────────────────────────────────
        if (nowOnSettings && !isOnSettingsScreen) {
            isOnSettingsScreen = true;
            Log.d(TAG, "✅ Settings detected: " + packageName);

            if (isGuardActive) {
                cancelTimeout();
                fireCallback(); // OverlayService will remove the banner
            }
        }

        // ── Left Settings ─────────────────────────────────────────────────
        if (!nowOnSettings && isOnSettingsScreen) {
            isOnSettingsScreen = false;
            Log.d(TAG, "⬅️ Left Settings → package: " + packageName);
        }
    }

    // ─── STEP 4: Settings detection ───────────────────────────────────────────

    /**
     * Checks exact match against known ROM packages, then a broad
     * "settings" substring fallback for unknown OEMs.
     */
    public static boolean isSettingsPackage(String packageName) {
        if (packageName == null) return false;
        String lower = packageName.toLowerCase();
        for (String known : SETTINGS_PACKAGES) {
            if (lower.equals(known)) return true;
        }
        return lower.contains("settings"); // OEM fallback
    }

    /** Convenience: is a Settings screen currently in the foreground? */
    public static boolean isCurrentlyOnSettings() {
        return isOnSettingsScreen;
    }

    // ─── Timeout ──────────────────────────────────────────────────────────────

    private static void startTimeout() {
        cancelTimeout();
        timeoutRunnable = () -> {
            if (!isGuardActive || isOnSettingsScreen) return;
            Log.d(TAG, "⏱ 30s timeout → hint user");
            // Delegate UI update to AccessibilityOverlay
            AccessibilityOverlay.showGuardBanner(
                    "Need help? Open the Settings ⚙️ app on your phone"
            );
        };
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    private static void cancelTimeout() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    // ─── Callback ─────────────────────────────────────────────────────────────

    private static void fireCallback() {
        if (pendingCallback != null) {
            OnSettingsReadyCallback cb = pendingCallback;
            pendingCallback = null; // nulled before firing — no double-fire
            mainHandler.post(cb::onSettingsReady);
            Log.d(TAG, "🚀 Guard callback fired");
        }
    }
}
