package com.example.navigationapp2;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
import android.widget.Toast;

/**
 * NavigationAccessibilityService — event router only.
 *
 * Responsibility: receive Android accessibility events and route them
 * to the appropriate component. Makes zero decisions itself.
 *
 * Routes:
 *   ALL events       → NavigationGuard.onAppEvent()   (Settings detection)
 *   Window events    → AssistantController.onWindowEvent()  (step execution)
 *   Package changes  → AssistantController.onUserLeftSettings()
 *                    → AssistantController.onUserReturnedToSettings()
 */
public class NavigationAccessibilityService extends AccessibilityService {

    public static NavigationAccessibilityService instance;
    private static final String TAG = "NAV_APP";

    // ─── Navigation state machine ─────────────────────────────────────────────
    public enum NavState {
        IDLE,
        WAITING_FOR_SETTINGS,
        RUNNING,
        PAUSED
    }

    public static NavState currentState = NavState.IDLE;

    // Dedup: skip repeated events from the same package within 300 ms
    private String lastPackage   = "";
    private long   lastEventTime = 0L;

    // ─── Service lifecycle ────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityOverlay.init(this);
        Log.d(TAG, "✅ Accessibility Service connected");
        Toast.makeText(this, "Accessibility Service Connected", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "⚠️ Accessibility Service interrupted");
    }

    // ─── Main event handler ───────────────────────────────────────────────────

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String packageName = event.getPackageName().toString();
        int    eventType   = event.getEventType();

        // ── Throttle fast-firing noise, but NEVER throttle clicks or window changes ──
        long now = System.currentTimeMillis();
        boolean samePackage = packageName.equals(lastPackage);
        
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Throttle removed entirely for maximum performance in demo.
            // Box stays stuck perfectly to scrolling UI elements (0ms intentional delay).
        }
        lastEventTime = now;

        boolean packageChanged = !packageName.equals(lastPackage);
        lastPackage = packageName;

        // Ignore noisy system UI and keyboard events for package tracking
        if (packageName.equals("com.android.systemui") || packageName.contains("inputmethod")) {
            // Just drive the window event, but skip package tracking logic
            if (currentState == NavState.RUNNING &&
                    (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                     eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                     eventType == AccessibilityEvent.TYPE_VIEW_CLICKED)) {
                AssistantController.instance.onWindowEvent(eventType);
            }
            return;
        }

        // ── Step 1: Robust settings detection ─────────────────────────────
        boolean onSettings = NavigationGuard.isSettingsPackage(packageName);

        // If the event says we aren't in settings, verify with the active window
        // because popups/dialogs sometimes have different package names.
        if (!onSettings) {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                onSettings = NavigationGuard.isSettingsPackage(root.getPackageName().toString());
            }
        }

        // Always feed NavigationGuard (detection engine)
        NavigationGuard.updateGuardState(onSettings, packageName);

        // ── Step 2: RUNNING state — execute navigation steps ──────────────
        if (currentState == NavState.RUNNING) {

            if (!onSettings) {
                // User left Settings mid-navigation
                Log.d(TAG, "⛔ Left Settings during RUNNING → pausing");
                AssistantController.instance.onUserLeftSettings();
                return;
            }

            // Drive step execution on meaningful UI changes
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {

                AssistantController.instance.onWindowEvent(eventType);
            }
        }

        // ── Step 3: PAUSED state — watch for return to Settings ───────────
        if (currentState == NavState.PAUSED && onSettings && packageChanged) {
            Log.d(TAG, "▶️ Returned to Settings while PAUSED → resuming");
            AssistantController.instance.onUserReturnedToSettings();
        }
    }
}