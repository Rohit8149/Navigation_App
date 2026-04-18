package com.example.navigationapp2;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * NavigationExecutor — executes one step of a navigation path per call.
 *
 * Returns a {@link StepResult} so the caller (AssistantController) can
 * show the right status message to the user:
 *
 *   STEP_SHOWN       → node found, highlight displayed, waiting for user tap
 *   NOT_FOUND        → node not on screen, tell user to scroll down
 *   COMPLETE         → all steps finished successfully
 *   SETTINGS_EXITED  → user left Settings mid-step, stop immediately
 *
 * IMPORTANT: This executor never auto-scrolls. When a node is not found,
 * it returns NOT_FOUND and lets the user scroll manually. The next
 * accessibility event (TYPE_WINDOW_CONTENT_CHANGED from the scroll) will
 * trigger another call to execute(), which will re-scan automatically.
 */
public class NavigationExecutor {

    private static final String TAG = "NAV_APP";

    // ─── Result enum ──────────────────────────────────────────────────────────

    public enum StepResult {
        STEP_SHOWN,        // node found → highlight shown → waiting for user tap
        NOT_FOUND,         // node not on this screen → tell user to scroll
        NOT_FOUND_SCROLL_UP,   // node logic exists, but is drawn above screen -> scroll up
        NOT_FOUND_SCROLL_DOWN, // node logic exists, but is drawn completely below screen -> scroll down
        COMPLETE,          // all steps done
        SETTINGS_EXITED    // user left Settings — stop immediately
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private static int          currentStepIndex          = 0;
    private static String       currentPathKey            = null;
    private static List<Integer> lastNodePath             = null;

    /** Keywords the current step is searching for — shown in the nav status bar */
    private static String currentStepKeywordsDisplay = "";
    
    private static boolean isWaitingForTap = false;
    private static long lastTapTime = 0;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Execute the next pending step.
     *
     * @param service Active accessibility service
     * @param paths   JSON array of navigation paths from Groq
     * @return {@link StepResult} indicating what happened
     */
    public static StepResult execute(AccessibilityService service, JSONArray paths) {
        try {
            for (int i = 0; i < paths.length(); i++) {

                JSONArray path    = paths.getJSONArray(i);
                String    pathKey = path.toString();

                // Reset step index when a new path is presented
                if (currentPathKey == null || !currentPathKey.equals(pathKey)) {
                    currentPathKey   = pathKey;
                    currentStepIndex = 0;
                    lastNodePath     = null;
                    Log.d(TAG, "🔄 New path → reset step index");
                    
                    Log.d(TAG, "📋 Navigation Plan Details:");
                    for (int stepIdx = 0; stepIdx < path.length(); stepIdx++) {
                        Log.d(TAG, "   " + (stepIdx + 1) + ". going to search for these elements: " + path.getJSONArray(stepIdx).toString());
                    }
                }

                return executeSinglePath(service, path);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ execute error: " + e.getMessage(), e);
        }

        return StepResult.NOT_FOUND;
    }

    /**
     * Keywords the executor is currently looking for (formatted for display).
     * Example: "wifi | wi-fi | wireless"
     */
    public static String getCurrentStepKeywordsDisplay() {
        return currentStepKeywordsDisplay;
    }

    /**
     * Reset all execution state.
     * Called by AssistantController.stop() between commands.
     */
    public static void reset() {
        currentStepIndex          = 0;
        currentPathKey            = null;
        lastNodePath              = null;
        currentStepKeywordsDisplay = "";
        isWaitingForTap           = false;
        lastTapTime               = 0;
        Log.d(TAG, "🔄 NavigationExecutor reset");
    }

    public static void advanceStepIfAppropriate() {
        if (isWaitingForTap) {
            currentStepIndex++;
            isWaitingForTap = false;
            lastTapTime = System.currentTimeMillis();
            Log.d(TAG, "👆 User tapped! Advancing to step " + currentStepIndex);
        }
    }

    public static boolean isRecentlyTapped() {
        return (System.currentTimeMillis() - lastTapTime) < 1500;
    }

    // ─── Single path execution ────────────────────────────────────────────────

    private static StepResult executeSinglePath(AccessibilityService service, JSONArray path) {
        try {
            // ── Guard: bail if user left Settings ────────────────────────────
            if (!isStillOnSettings(service)) {
                Log.w(TAG, "⛔ Not on Settings — aborting step");
                return StepResult.SETTINGS_EXITED;
            }

            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "⚠️ Root window null");
                return StepResult.NOT_FOUND;
            }

            // Save UI snapshot for debug
            DebugSaver.saveScreen(service, "settings_scan");

            // ── All steps done? ───────────────────────────────────────────────
            if (currentStepIndex >= path.length()) {
                Log.d(TAG, "✅ All steps completed");
                reset();
                return StepResult.COMPLETE;
            }

            AccessibilityNodeInfo node = null;
            int foundTargetIndex = -1;
            List<String> foundKeywords = new ArrayList<>();

            // ── Robust Reverse-Priority Multi-Step Search ─────────────────
            // 1. Gather matches for all valid upcoming steps.
            NodeMatcher.MatchResult[] matches = new NodeMatcher.MatchResult[path.length()];
            List<List<String>> allKeywords = new ArrayList<>();
            for (int i = 0; i < path.length(); i++) {
                JSONArray stepArrayRaw = path.getJSONArray(i);
                List<String> stepKeywords = new ArrayList<>();
                for (int k = 0; k < stepArrayRaw.length(); k++) {
                    stepKeywords.add(stepArrayRaw.getString(k).toLowerCase());
                }
                allKeywords.add(stepKeywords);
                
                if (i >= currentStepIndex) {
                    matches[i] = NodeMatcher.findBestMatchWithPath(root, stepKeywords);
                }
            }
            
            // 2. We prefer to jump to the furthest step possible (Reverse Priority).
            // BUT if a future step matched the EXACT SAME node as our current step 
            // (e.g. "VPN" is just a subtitle in the "Network" cell), we treat it as the current step.
            for (int i = path.length() - 1; i >= currentStepIndex; i--) {
                if (matches[i] != null) {
                    // We found a deep match! Check if lower steps match the same node.
                    int trueTargetIndex = i;
                    for (int j = currentStepIndex; j < i; j++) {
                        if (matches[j] != null) {
                            Rect b1 = new Rect();
                            matches[j].node.getBoundsInScreen(b1);
                            Rect b2 = new Rect();
                            matches[i].node.getBoundsInScreen(b2);
                            
                            if (b1.equals(b2)) {
                                trueTargetIndex = j;
                                break; // prioritize the lower index so we don't accidentally skip!
                            }
                        }
                    }
                    
                    node = matches[trueTargetIndex].node;
                    lastNodePath = matches[trueTargetIndex].path;
                    foundTargetIndex = trueTargetIndex;
                    foundKeywords = allKeywords.get(trueTargetIndex);
                    Log.d(TAG, "🟢 Node found! Deepest match was step " + i + " but resolved to step " + trueTargetIndex);
                    break;
                }
            }

            if (node == null) {
                // Keep the current step keywords for display in case we didn't find anything
                // (so the user knows what we are looking for and can scroll)
                JSONArray currentStepArray = path.getJSONArray(currentStepIndex);
                List<String> currentKeywords = new ArrayList<>();
                for (int k = 0; k < currentStepArray.length(); k++) {
                    currentKeywords.add(currentStepArray.getString(k).toLowerCase());
                }
                currentStepKeywordsDisplay = String.join(" | ", currentKeywords);
                Log.d(TAG, "🔍 Node not found. Waiting for user to scroll to find step " + currentStepIndex + ": " + currentStepKeywordsDisplay);
            } else {
                if (foundTargetIndex > currentStepIndex) {
                    Log.d(TAG, "🚀 Smart skip/Auto-advance! Jumped from step " + currentStepIndex + " to step " + foundTargetIndex);
                    currentStepIndex = foundTargetIndex;
                    // We found a new element further down the path, wait for user to tap this new element
                    isWaitingForTap = false;
                    lastTapTime = System.currentTimeMillis();
                }
                currentStepKeywordsDisplay = String.join(" | ", foundKeywords);
            }

            // ── Node not found — DO NOT scroll. Tell user to scroll. ─────────
            if (node == null) {
                Log.d(TAG, "❌ Node not found for: " + currentStepKeywordsDisplay + " — telling user to scroll");
                HighlightOverlay.remove(); // Remove any leftover box from an item that scrolled off-screen
                // No tryScroll() here. User must scroll manually.
                // Next TYPE_WINDOW_CONTENT_CHANGED event will re-call execute() automatically.
                return StepResult.NOT_FOUND;
            }

            // ── Node found — show highlight ──────────────────────────────────
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);

            int screenHeight = service.getResources().getDisplayMetrics().heightPixels;

            // If the element has moved completely above the visible screen
            if (bounds.bottom <= 100) { // 100px buffer to account for top action bars
                Log.d(TAG, "❌ Node bounds off-screen top: " + bounds);
                HighlightOverlay.remove();
                return StepResult.NOT_FOUND_SCROLL_UP;
            } 
            // If the element has moved completely below the visible screen
            else if (bounds.top >= screenHeight - 100) { // 100px buffer for bottom nav bars
                Log.d(TAG, "❌ Node bounds off-screen bottom: " + bounds);
                HighlightOverlay.remove();
                return StepResult.NOT_FOUND_SCROLL_DOWN;
            }

            // Need effectively final variable for the lambda
            final AccessibilityNodeInfo finalNode = node;

            HighlightOverlay.show(service, bounds, "👆 Tap here", () -> {
                // 1. Force the physical system click!
                if (finalNode != null) {
                    finalNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                
                // 2. Directly advance our logic!
                isWaitingForTap = true; // Ensure it passes the internal check
                advanceStepIfAppropriate();
            });
            Log.d(TAG, "🟢 Highlight at: " + bounds);

            // Wait for user to actually tap this highlighted node before progressing
            isWaitingForTap = true;

            return StepResult.STEP_SHOWN;

        } catch (Exception e) {
            Log.e(TAG, "❌ executeSinglePath error: " + e.getMessage(), e);
            return StepResult.NOT_FOUND;
        }
    }

    // ─── Settings guard ───────────────────────────────────────────────────────

    private static boolean isStillOnSettings(AccessibilityService service) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return true; // Transient state, don't abort loop
        CharSequence pkg = root.getPackageName();
        return pkg != null && NavigationGuard.isSettingsPackage(pkg.toString());
    }

    // ─── UI tree snapshot (used for scroll-change detection) ─────────────────

    public static String getTreeSnapshot(AccessibilityNodeInfo root) {
        if (root == null) return "";
        StringBuilder builder = new StringBuilder();
        buildTreeString(root, builder);
        return builder.toString();
    }

    private static void buildTreeString(AccessibilityNodeInfo node, StringBuilder builder) {
        if (node == null) return;
        builder.append(node.getClassName()).append("|");
        if (node.getText() != null) builder.append(node.getText().toString());
        builder.append("\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) buildTreeString(child, builder);
        }
    }
}