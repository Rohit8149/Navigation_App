package com.example.navigationapp2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AssistantController — the single orchestration brain of the app.
 *
 * Owns the FULL navigation flow:
 *   user input → validate → Groq API → guard → navigate → pause/resume → complete
 *
 * Talks to:
 *   OverlayService         — for ALL UI (banners, status, input box)
 *   NavigationGuard        — Settings detection
 *   GroqApiClient          — AI navigation paths
 *   NavigationExecutor     — step-by-step execution
 *   HighlightOverlay       — hint arrows on Settings UI
 *   NavigationAccessibilityService — reads current state
 *
 * This class has ZERO UI code. It only makes decisions.
 */
public class AssistantController {

    private static final String TAG = "NAV_APP";

    /** Singleton — initialised in OverlayService.onCreate() */
    public static AssistantController instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Parsed Groq response with navigation_paths + missing_slots */
    private JSONObject pendingCommand;

    /** True while step-by-step navigation is actively running inside Settings */
    private boolean navigationActive = false;

    private AssistantController() {}

    public static void init() {
        instance = new AssistantController();
        Log.d(TAG, "✅ AssistantController initialised");
    }

    // ─── PHASE 1: User submits input ─────────────────────────────────────────

    /**
     * Entry point for user input. Called by OverlayService submit button.
     *
     * Flow: validate → network → accessibility check → Groq API
     */
    public void handleUserInput(Context context, String rawText) {
        String text = rawText.trim().toLowerCase();

        // 1. Local intent classification (fast — no network)
        String validationError = CommandClassifier.getValidationError(text);
        if (validationError != null) {
            showStatus(validationError);
            return;
        }

        // 2. Network availability
        if (!NetworkUtils.isInternetAvailable(context)) {
            showStatus("❌ No Internet connection");
            return;
        }

        // 3. Accessibility service alive?
        if (NavigationAccessibilityService.instance == null) {
            showStatus("⚠️ Enable Accessibility Service first");
            return;
        }

        showStatus("🤖 Understanding command...");

        // 4. Call Groq API (background thread)
        GroqApiClient.processCommand(text, new GroqApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                // Always post back to main thread for state changes
                mainHandler.post(() -> onApiSuccess(data));
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> showStatus("❌ API error: " + error));
            }
        });
    }

    /** Called on main thread once Groq responds successfully */
    private void onApiSuccess(JSONObject data) {
        try {
            if (!data.has("navigation_paths") || !data.has("missing_slots")) {
                showStatus("❌ Invalid AI response, try again");
                return;
            }

            // Check if intent is unknown (non-Settings command passed classification)
            if ("unknown".equals(data.optString("intent", ""))) {
                showStatus("❌ I can only navigate Android Settings");
                return;
            }

            pendingCommand   = data;
            navigationActive = false;

            NavigationAccessibilityService.currentState =
                    NavigationAccessibilityService.NavState.WAITING_FOR_SETTINGS;

            // Show PERSISTENT banner — stays until Settings is confirmed open
            showGuardBanner("⚙️ Please open Settings to continue");

            // Arm the guard — callback fires the moment Settings enters foreground
            NavigationGuard.startGuard(this::onSettingsReady);

            Log.d(TAG, "✅ Command ready, guard armed → " + data.optString("intent"));

        } catch (Exception e) {
            Log.e(TAG, "❌ onApiSuccess error: " + e.getMessage());
            showStatus("❌ Could not process command, try again");
        }
    }

    // ─── PHASE 2: Settings is open ───────────────────────────────────────────

    /**
     * Called by NavigationGuard (on main thread) the instant Settings enters foreground.
     */
    private void onSettingsReady() {
        Log.d(TAG, "✅ Settings confirmed → starting navigation");

        removeGuardBanner();  // banner disappears immediately

        if (pendingCommand == null) return;

        navigationActive = true;
        NavigationGuard.stopGuard();

        NavigationAccessibilityService.currentState =
                NavigationAccessibilityService.NavState.RUNNING;

        // Show nav status bar immediately as navigation begins
        showNavStatusBar("", "🔍 Scanning...");
        runNavigationStep();
    }

    // ─── PHASE 3: Execute steps on each window change ────────────────────────

    /**
     * Called by NavigationAccessibilityService on each TYPE_WINDOW_STATE_CHANGED
     * or TYPE_WINDOW_CONTENT_CHANGED event while state == RUNNING.
     */
    public void onWindowEvent(int eventType) {
        if (!navigationActive || pendingCommand == null) return;
        
        if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED) {
            // User tapped the screen. Ensure they progress to the next step.
            NavigationExecutor.advanceStepIfAppropriate();
        }

        // Update status to "Scanning..." immediately so user sees live feedback
        updateNavStatus("🔍 Scanning...");
        runNavigationStep();
    }

    private void runNavigationStep() {
        try {
            JSONArray missing = pendingCommand.getJSONArray("missing_slots");
            JSONArray paths   = pendingCommand.getJSONArray("navigation_paths");

            if (missing.length() > 0) {
                showNavStatusBar("", "⚠️ Missing info: " + missing.getString(0));
                return;
            }

            NavigationExecutor.StepResult result =
                    NavigationExecutor.execute(NavigationAccessibilityService.instance, paths);

            String keywords = NavigationExecutor.getCurrentStepKeywordsDisplay();

            switch (result) {

                case STEP_SHOWN:
                    // Node found — highlight is on screen, waiting for user tap
                    showNavStatusBar(keywords, "👆 Tap the highlighted item");
                    break;

                case NOT_FOUND:
                    if (NavigationExecutor.isRecentlyTapped()) {
                        // User just tapped! Screen is transitioning. Keep scanning active.
                        showNavStatusBar(keywords, "🔍 Loading next screen...");
                    } else {
                        // Node not found anywhere — default to scrolling down
                        showNavStatusBar(keywords, "👇 Not found here — scroll down");
                    }
                    break;

                case NOT_FOUND_SCROLL_UP:
                    // Node is off-screen at the top
                    showNavStatusBar(keywords, "⬆️ Scroll UP to see the item");
                    break;

                case NOT_FOUND_SCROLL_DOWN:
                    // Node is off-screen at the bottom
                    showNavStatusBar(keywords, "⬇️ Scroll DOWN to see the item");
                    break;

                case COMPLETE:
                    onNavigationComplete();
                    break;

                case SETTINGS_EXITED:
                    // NavigationAccessibilityService already called onUserLeftSettings()
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ runNavigationStep error: " + e.getMessage());
        }
    }

    private void onNavigationComplete() {
        navigationActive = false;
        pendingCommand   = null;
        NavigationAccessibilityService.currentState =
                NavigationAccessibilityService.NavState.IDLE;
        HighlightOverlay.remove();
        removeNavStatusBar();
        showStatus("✅ Done! You're on the right screen.");
        Log.d(TAG, "🏁 Navigation complete");
    }

    // ─── PHASE 4: User left Settings mid-navigation ──────────────────────────

    /**
     * Called by NavigationAccessibilityService when foreground package
     * leaves Settings while state == RUNNING.
     */
    public void onUserLeftSettings() {
        if (!navigationActive) return;

        navigationActive = false;
        NavigationAccessibilityService.currentState =
                NavigationAccessibilityService.NavState.PAUSED;

        HighlightOverlay.remove();
        removeNavStatusBar();
        showGuardBanner("⚙️ Please return to Settings to continue");
        Log.d(TAG, "⏸ Navigation paused — user left Settings");
    }

    // ─── PHASE 5: User returned to Settings while paused ─────────────────────

    /**
     * Called by NavigationAccessibilityService when foreground package
     * is Settings again while state == PAUSED.
     */
    public void onUserReturnedToSettings() {
        if (pendingCommand == null) return;

        Log.d(TAG, "🔄 User returned to Settings — re-arming guard");

        // Update banner text while guard re-arms
        showGuardBanner("📍 Settings detected — resuming...");

        NavigationAccessibilityService.currentState =
                NavigationAccessibilityService.NavState.WAITING_FOR_SETTINGS;

        NavigationGuard.startGuard(this::onSettingsReady); // reuses same logic
    }

    // ─── Full stop ────────────────────────────────────────────────────────────

    /**
     * Completely stops the assistant (user pressed "Stop").
     */
    public void stop() {
        navigationActive = false;
        pendingCommand   = null;

        NavigationGuard.stopGuard();
        NavigationExecutor.reset();
        HighlightOverlay.remove();
        removeGuardBanner();
        removeNavStatusBar();

        NavigationAccessibilityService.currentState =
                NavigationAccessibilityService.NavState.IDLE;

        Log.d(TAG, "🛑 AssistantController stopped");
    }

    // ─── UI Helpers (delegates — controller never touches WindowManager) ──────

    private void showStatus(String msg) {
        AccessibilityOverlay.showStatus(msg);
    }

    private void showGuardBanner(String msg) {
        AccessibilityOverlay.showGuardBanner(msg);
    }

    private void removeGuardBanner() {
        AccessibilityOverlay.removeGuardBanner();
    }

    private void showNavStatusBar(String keywords, String status) {
        AccessibilityOverlay.showNavStatusBar(keywords, status);
    }

    private void updateNavStatus(String status) {
        AccessibilityOverlay.updateNavStatus(status);
    }

    private void removeNavStatusBar() {
        AccessibilityOverlay.removeNavStatusBar();
    }

    private String getIntentLabel() {
        return pendingCommand != null
                ? pendingCommand.optString("intent", "target")
                : "target";
    }
}
