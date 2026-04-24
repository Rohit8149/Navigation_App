package com.example.navigationapp2;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * AccessibilityOverlay — overlay windows that appear over ALL apps including system apps.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY which is granted automatically to Accessibility Services
 * and can draw above Settings, Phone dialer, Camera — any system app.
 *
 * TYPE_APPLICATION_OVERLAY (used by OverlayService) is blocked by some OEMs
 * on system apps. This class solves that for all navigation-phase overlays:
 *
 *   guardBannerView  — "Please open Settings" persistent banner
 *   navStatusBarView — live "Scanning... / Scroll down ↓" during navigation
 *   statusView       — auto-dismiss 3-second status messages
 *
 * Must be initialised by calling init() from NavigationAccessibilityService.onServiceConnected()
 * so that the WindowManager is obtained from a valid AccessibilityService context.
 */
public class AccessibilityOverlay {

    private static final String TAG = "NAV_APP";

    /** Always post WindowManager calls here — UI thread required */
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static WindowManager wm;
    private static Context       serviceContext;   // AccessibilityService context

    private static View guardBannerView;
    private static View navStatusBarView;
    private static View statusView;

    // ─── Init ─────────────────────────────────────────────────────────────────

    /**
     * Must be called from NavigationAccessibilityService.onServiceConnected().
     * Stores the accessibility service's WindowManager — the only one that can
     * add TYPE_ACCESSIBILITY_OVERLAY windows.
     */
    public static void init(AccessibilityService service) {
        wm             = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        serviceContext = service;
        Log.d(TAG, "✅ AccessibilityOverlay initialised");
    }

    public static boolean isReady() {
        return wm != null && serviceContext != null;
    }

    // ─── Guard Banner — persistent ────────────────────────────────────────────

    /**
     * Show a full-width persistent banner at the top of the screen.
     * Visible over Settings and all other system apps.
     * Updates in-place if already showing (no flicker).
     *
     * @param message Primary message, e.g. "⚙️ Please open Settings"
     */
    public static void showGuardBanner(String message) {
        mainHandler.post(() -> {
            if (!isReady()) return;
            try {
                if (guardBannerView != null) {
                    // Update text in-place — no remove/re-add needed
                    TextView tv = guardBannerView.findViewById(R.id.guardTitle);
                    if (tv != null) tv.setText(message);
                    Log.d(TAG, "🔄 Guard banner updated: " + message);
                    return;
                }

                guardBannerView = LayoutInflater.from(serviceContext)
                        .inflate(R.layout.guard_overlay, null);

                TextView title = guardBannerView.findViewById(R.id.guardTitle);
                TextView sub   = guardBannerView.findViewById(R.id.guardSubtitle);
                if (title != null) title.setText(message);
                if (sub   != null) sub.setText("AI Navigation Assistant is waiting...");

                wm.addView(guardBannerView, topBannerParams());
                Log.d(TAG, "🟡 Guard banner shown: " + message);

            } catch (Exception e) {
                Log.e(TAG, "❌ showGuardBanner: " + e.getMessage());
            }
        });
    }

    public static void removeGuardBanner() {
        mainHandler.post(() -> {
            if (guardBannerView != null && wm != null) {
                try { wm.removeView(guardBannerView); } catch (Exception ignored) {}
                guardBannerView = null;
                Log.d(TAG, "🗑 Guard banner removed");
            }
        });
    }

    // ─── Nav Status Bar — persistent during navigation ────────────────────────

    /**
     * Show (or update) the navigation status bar.
     * Shows keywords being searched (small line) + live status message (bold line).
     *
     * Safe to call repeatedly — updates in-place.
     *
     * @param keywordsLine e.g. "wifi | wi-fi | wireless"
     * @param statusLine   e.g. "🔍 Scanning..." or "👇 Scroll down"
     */
    public static void showNavStatusBar(String keywordsLine, String statusLine) {
        showNavStatusBar(keywordsLine, statusLine, null);
    }

    public static void showNavStatusBar(String keywordsLine, String statusLine, Runnable onHelpAction) {
        mainHandler.post(() -> {
            if (!isReady()) return;
            try {
                if (navStatusBarView != null) {
                    TextView kw = navStatusBarView.findViewById(R.id.navKeywordsText);
                    TextView st = navStatusBarView.findViewById(R.id.navStatusText);
                    View helpBtn = navStatusBarView.findViewById(R.id.navHelpButton);
                    
                    if (kw != null) kw.setText("Searching: " + keywordsLine);
                    if (st != null) st.setText(statusLine);
                    
                    if (helpBtn != null) {
                        if (onHelpAction != null) {
                            helpBtn.setVisibility(View.VISIBLE);
                            helpBtn.setOnClickListener(v -> onHelpAction.run());
                        } else {
                            helpBtn.setVisibility(View.GONE);
                            helpBtn.setOnClickListener(null);
                        }
                    }
                    Log.d(TAG, "🔄 NavStatusBar updated: " + statusLine);
                    return;
                }

                navStatusBarView = LayoutInflater.from(serviceContext)
                        .inflate(R.layout.nav_status_bar, null);

                TextView kw = navStatusBarView.findViewById(R.id.navKeywordsText);
                TextView st = navStatusBarView.findViewById(R.id.navStatusText);
                View spinner = navStatusBarView.findViewById(R.id.navStatusProgress);
                View helpBtn = navStatusBarView.findViewById(R.id.navHelpButton);
                
                if (kw != null) kw.setText("Searching: " + keywordsLine);
                if (st != null) st.setText(statusLine);
                if (spinner != null) spinner.setVisibility(statusLine.contains("Scanning") ? View.VISIBLE : View.GONE);
                
                if (helpBtn != null) {
                    if (onHelpAction != null) {
                        helpBtn.setVisibility(View.VISIBLE);
                        helpBtn.setOnClickListener(v -> onHelpAction.run());
                    } else {
                        helpBtn.setVisibility(View.GONE);
                        helpBtn.setOnClickListener(null);
                    }
                }

                wm.addView(navStatusBarView, topBannerParams());
                Log.d(TAG, "🟣 NavStatusBar shown: " + statusLine);

            } catch (Exception e) {
                Log.e(TAG, "❌ showNavStatusBar: " + e.getMessage());
            }
        });
    }

    public static void updateNavStatus(String statusLine) {
        updateNavStatus(statusLine, null);
    }

    public static void updateNavStatus(String statusLine, Runnable onHelpAction) {
        mainHandler.post(() -> {
            if (navStatusBarView == null) return;
            TextView st = navStatusBarView.findViewById(R.id.navStatusText);
            View spinner = navStatusBarView.findViewById(R.id.navStatusProgress);
            View helpBtn = navStatusBarView.findViewById(R.id.navHelpButton);
            
            if (st != null) {
                st.setText(statusLine);
                Log.d(TAG, "🔄 NavStatus: " + statusLine);
            }
            if (spinner != null) {
                spinner.setVisibility(statusLine.contains("Scanning") ? View.VISIBLE : View.GONE);
            }
            if (helpBtn != null) {
                if (onHelpAction != null) {
                    helpBtn.setVisibility(View.VISIBLE);
                    helpBtn.setOnClickListener(v -> onHelpAction.run());
                } else {
                    helpBtn.setVisibility(View.GONE);
                    helpBtn.setOnClickListener(null);
                }
            }
        });
    }

    public static void removeNavStatusBar() {
        mainHandler.post(() -> {
            if (navStatusBarView != null && wm != null) {
                try { wm.removeView(navStatusBarView); } catch (Exception ignored) {}
                navStatusBarView = null;
                Log.d(TAG, "🗑 NavStatusBar removed");
            }
        });
    }

    // ─── Status Toast — auto-dismiss 3 s ──────────────────────────────────────

    /**
     * Shows a brief status message (e.g. "✅ Done!", "❌ No Internet") for 3 seconds.
     * Visible over Settings.
     */
    public static void showStatus(String text) {
        mainHandler.post(() -> {
            if (!isReady()) return;
            try {
                removeStatusInternal();

                statusView = LayoutInflater.from(serviceContext)
                        .inflate(R.layout.status_layout, null);

                TextView tv = statusView.findViewById(R.id.statusText);
                if (tv != null) tv.setText(text);

                WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                );
                // Sit below nav status bar or guard banner
                p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                p.y = navStatusBarView != null ? 160 : (guardBannerView != null ? 160 : 80);

                wm.addView(statusView, p);
                Log.d(TAG, "🟢 Status shown: " + text);

                mainHandler.postDelayed(AccessibilityOverlay::removeStatusInternal, 3_000);

            } catch (Exception e) {
                Log.e(TAG, "❌ showStatus: " + e.getMessage());
            }
        });
    }

    private static void removeStatusInternal() {
        if (statusView != null && wm != null) {
            try { wm.removeView(statusView); } catch (Exception ignored) {}
            statusView = null;
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    public static void removeAll() {
        removeGuardBanner();
        removeNavStatusBar();
        mainHandler.post(AccessibilityOverlay::removeStatusInternal);
    }

    // ─── Window params helper ─────────────────────────────────────────────────

    /**
     * Standard params for a full-width top-pinned accessibility overlay.
     * FLAG_NOT_FOCUSABLE ensures touches pass through to the app underneath.
     */
    private static WindowManager.LayoutParams topBannerParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = 0;
        return p;
    }
}
