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

    /** Stores raw text to pass back into Groq for Agentic Rerouting */
    private String lastUserInput = "";

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
        this.lastUserInput = text;

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

            // If navigation_paths is empty, nothing to navigate — inform user
            if (data.getJSONArray("navigation_paths").length() == 0) {
                showStatus("❌ Couldn't figure out how to navigate there. Try rephrasing.");
                return;
            }

            pendingCommand   = data;
            navigationActive = false;

            // -- LOGGING INITIAL PATH --
            try {
                String pathsJsonString = data.getJSONArray("navigation_paths").toString(2);
                NavLogManager.getInstance().addLog(lastUserInput, pathsJsonString);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log initial path: " + e.getMessage());
            }

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

    public void onWindowEvent(int eventType) {
        if (!navigationActive || pendingCommand == null) return;
        
        // Removed global TYPE_VIEW_CLICKED advancement. 
        // We only advance when the user actually taps the target's HighlightOverlay.

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
                        // Node not found anywhere — default to scrolling down. Provide Reroute Help Button!
                        showNavStatusBar(keywords, "👇 Not found here — scroll down", this::triggerAgenticReroute);
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
        
        // Delay completion message slightly so it appears AFTER the page transition
        mainHandler.postDelayed(() -> showStatus("✅ Done! You're on the right screen."), 800);
        
        Log.d(TAG, "🏁 Navigation complete");
    }

    // ─── Phase 3.5: Agentic Rerouting ─────────────────────────────────────────

    private void triggerAgenticReroute() {
        if (!navigationActive || pendingCommand == null) return;

        Log.d(TAG, "🚨 User requested Agentic Reroute. Pausing execution.");
        
        navigationActive = false;
        NavigationAccessibilityService.currentState = NavigationAccessibilityService.NavState.PAUSED;
        
        String failedStep = NavigationExecutor.getCurrentStepKeywordsDisplay();
        showNavStatusBar(failedStep, "🤖 Rethinking route...");
        
        java.util.List<String> visibleOptions = UICacheManager.getKnownLabelsForCurrentScreen(
                NavigationAccessibilityService.instance.getRootInActiveWindow()
        );
        
        int currentStepIdx = NavigationExecutor.getCurrentStepIndex();
        
        // ── Call Groq for Reroute ──
        String uiTreeStr = "Visible Options: [" + String.join(", ", visibleOptions) + "]";
        String settingsKnowledgeMap = UICacheManager.getBeautifulJsonString();
        
        GroqApiClient.processRerouteCommand(lastUserInput, failedStep, visibleOptions, settingsKnowledgeMap, new GroqApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                mainHandler.post(() -> onRerouteSuccess(data, currentStepIdx, uiTreeStr));
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    showNavStatusBar(failedStep, "❌ Reroute failed: " + error);
                    navigationActive = true;
                    // Provide a slight pause before resuming the normal search 
                    // so the user can read the error
                    mainHandler.postDelayed(() -> {
                        NavigationAccessibilityService.currentState = NavigationAccessibilityService.NavState.RUNNING;
                        showNavStatusBar(failedStep, "👇 Not found here — scroll down", AssistantController.this::triggerAgenticReroute);
                    }, 2000);
                });
            }
        });
    }

    private void onRerouteSuccess(JSONObject data, int currentStepIdx, String uiTreeStr) {
        try {
            if (!data.has("navigation_paths")) {
                showStatus("❌ Invalid AI response");
                return;
            }
            
            JSONArray newPaths = data.getJSONArray("navigation_paths");
            if (newPaths.length() == 0) {
                showStatus("❌ AI could not find a valid alternative route.");
                return;
            }
            
            // Get the entire new sequence from Groq
            JSONArray newSequence = newPaths.getJSONArray(0);
            
            JSONArray oldPaths = pendingCommand.getJSONArray("navigation_paths");
            JSONArray currentSequence = oldPaths.getJSONArray(0);
            
            // -- LOGGING REROUTE --
            try {
                String rerouteJsonString = newPaths.toString(2);
                String fullLog = uiTreeStr + "\n\nAgentic Reroute Sequence injected at step " + currentStepIdx + ":\n" + rerouteJsonString;
                NavLogManager.getInstance().addRerouteToLatest(fullLog);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log reroute path: " + e.getMessage());
            }

            // Truncate the original sequence from the failed step onwards
            while (currentSequence.length() > currentStepIdx) {
                currentSequence.remove(currentStepIdx);
            }
            
            // Append the entirety of the new sequence
            for (int i = 0; i < newSequence.length(); i++) {
                currentSequence.put(newSequence.getJSONArray(i));
            }
            
            // Inform executor so it doesn't reset currentStepIndex back to 0
            NavigationExecutor.informPathModified(oldPaths);
            
            Log.d(TAG, "✨ Reroute Success! Injected new sequence into step " + currentStepIdx + ": " + newSequence.toString());
            
            // Resume Navigation
            navigationActive = true;
            NavigationAccessibilityService.currentState = NavigationAccessibilityService.NavState.RUNNING;
            
            showNavStatusBar("", "🔍 Scanning...");
            runNavigationStep();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ onRerouteSuccess error: " + e.getMessage());
            showStatus("❌ Failed to process new route");
        }
    }

    // ─── PHASE 4: User left Settings mid-navigation ──────────────────────────

    public void onUserLeftSettings() {
        if (!navigationActive) return;

        // Update status so the overlay doesn't show "Scanning..." when user has left Settings.
        // Navigation stays armed — some sub-settings open different packages (e.g. Home Screen).
        String keywords = NavigationExecutor.getCurrentStepKeywordsDisplay();
        showNavStatusBar(keywords, "↩️ Return to Settings to continue");
        HighlightOverlay.remove();
        Log.d(TAG, "🔓 Left Settings — showing return prompt, navigation still armed");
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

    private void showNavStatusBar(String keywords, String status, Runnable onHelpAction) {
        AccessibilityOverlay.showNavStatusBar(keywords, status, onHelpAction);
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
