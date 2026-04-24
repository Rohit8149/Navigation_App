package com.example.navigationapp2;

import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * UICacheManager (Layer 1.5)
 * Builds an offline textual map of the Settings tree dynamically as the user/assistant navigates.
 * Saves the map to a JSON file to persist across sessions!
 */
public class UICacheManager {

    private static final String TAG = "NAV_APP";

    private static final Map<String, List<String>> uiTree = new HashMap<>();
    private static String currentParent = "Settings_Root";
    private static String pendingParent = null;
    private static boolean isLoaded = false;
    private static final List<String> lastScreenLabels = new ArrayList<>();

    public static void setCurrentParent(String parentName) {
        if (parentName == null || parentName.trim().isEmpty()) return;
        pendingParent = parentName;
        Log.d(TAG, "📂 UI Cache Manager: Pending drill down -> " + parentName);
    }

    public static void recordScreen(Context context, AccessibilityNodeInfo root) {
        if (root == null) return;
        if (!isLoaded && context != null) {
            loadFromFile(context);
        }

        List<String> labels = new ArrayList<>();
        collectLabels(root, labels);

        List<String> cleanLabels = new ArrayList<>();
        for (String label : labels) {
            String trimmed = label.trim();
            if (!trimmed.isEmpty() && !cleanLabels.contains(trimmed) && !isJunkLabel(trimmed)) {
                cleanLabels.add(trimmed);
            }
        }

        if (cleanLabels.isEmpty()) return;

        // ─── TRANSITION GUARD: Are we still on the same screen? ─────────
        int overlapWithLast = 0;
        for (String label : cleanLabels) {
            if (lastScreenLabels.contains(label)) overlapWithLast++;
        }
        
        int minSize = Math.min(cleanLabels.size(), lastScreenLabels.size());
        boolean isSameScreen = false;
        if (minSize > 0) {
            double ratio = (double) overlapWithLast / minSize;
            if (ratio >= 0.5) isSameScreen = true; // >= 50% match means no true page transition occurred
        } else if (lastScreenLabels.isEmpty() && !cleanLabels.isEmpty()) {
            // First ever scan on app launch is inherently a "new" screen
            isSameScreen = false;
        } else {
            isSameScreen = true;
        }

        if (isSameScreen) {
            // Just a scroll or minor re-render. Do NOT process pending clicks or pivots.
            mergeIntoParent(cleanLabels, context);
            for (String label : cleanLabels) {
                if (!lastScreenLabels.contains(label)) lastScreenLabels.add(label);
            }
            return;
        }

        // ─── 🧠 EXCELLENT TREE RECONSTRUCTION ALGORITHM ──────────────
        
        Log.d(TAG, "🌪️ UI Cache Manager: True screen transition detected!");

        if (pendingParent != null) {
            // We specifically clicked a button to drill deeper.
            currentParent = pendingParent;
            pendingParent = null;
        } else {
            // Compute overlap with all known screens to mathematically determine where we are.
            String bestMatch = null;
            int maxOverlap = 0;
            
            for (Map.Entry<String, List<String>> entry : uiTree.entrySet()) {
                int overlap = 0;
                for (String label : cleanLabels) {
                    if (entry.getValue().contains(label)) overlap++;
                }
                // Minimum of 3 overlapping labels to assume context pivot
                if (overlap > maxOverlap && overlap >= 3) {
                    maxOverlap = overlap;
                    bestMatch = entry.getKey();
                }
            }
            
            if (bestMatch != null) {
                if (!bestMatch.equals(currentParent)) {
                    Log.d(TAG, "🔄 UI Cache Manager: Auto-detected screen flip (Back/Home). Assumed Context: [" + bestMatch + "]");
                    currentParent = bestMatch;
                }
            } else {
                // Completely unknown screen! (Overlap < 3)
                // We MUST NOT pollute currentParent. Attempt to actively extract the screen's title.
                String extractedTitle = extractScreenTitle(cleanLabels, root);
                if (extractedTitle != null) {
                    currentParent = extractedTitle;
                    Log.d(TAG, "🆕 UI Cache Manager: Discovered unknown screen natively. Extracted title: [" + extractedTitle + "]");
                } else {
                    // Cannot securely identify screen. To prevent tree corruption, abort adding it.
                    Log.d(TAG, "⚠️ UI Cache Manager: Unknown screen with no title. Aborting record to prevent cache corruption.");
                    return;
                }
            }
        }

        // ─── APPLY NEW SCREEN DATA ──────────────────────────────────────────
        
        lastScreenLabels.clear();
        lastScreenLabels.addAll(cleanLabels);
        
        mergeIntoParent(cleanLabels, context);
    }

    private static void mergeIntoParent(List<String> cleanLabels, Context context) {
        boolean changed = false;
        List<String> existing = uiTree.get(currentParent);
        if (existing == null) {
            existing = new ArrayList<>();
            uiTree.put(currentParent, existing);
            changed = true;
        }

        for (String label : cleanLabels) {
            if (!existing.contains(label)) {
                existing.add(label);
                changed = true;
            }
        }

        if (changed) {
            Log.d(TAG, "💾 UI Cache Manager: Merged data into [" + currentParent + "], total items known: " + existing.size());
            if (context != null) {
                saveToFile(context);
                // Notify the in-app viewer so it refreshes instantly
                UITreeManager.getInstance().updateJSONTree();
            }
        }
    }

    /**
     * Filters out Android system-UI noise that is not a real Settings item:
     * back buttons, search bars, profile pictures, status bar labels, etc.
     */
    private static boolean isJunkLabel(String label) {
        if (label.length() > 120) return true; // Very long strings are accessibility descriptions, not titles
        String lower = label.toLowerCase();
        // System navigation buttons
        if (lower.equals("navigate up") || lower.equals("back") || lower.equals("navigate up button")) return true;
        // Search bars
        if (lower.startsWith("search")) return true;
        // Status bar / portrait / phone call noise
        if (lower.contains("profile picture") || lower.contains("double tap")) return true;
        // Generic one-char or number-only labels
        if (label.length() <= 1) return true;
        return false;
    }

    private static void collectLabels(AccessibilityNodeInfo node, List<String> list) {
        if (node == null) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        if (text != null && text.length() > 0) {
            list.add(text.toString());
        } else if (desc != null && desc.length() > 0) {
            list.add(desc.toString());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectLabels(node.getChild(i), list);
        }
    }

    /**
     * Extracts all unique text labels from the provided window root.
     * Used exclusively for Agentic Rerouting to feed live options back to Groq.
     */
    public static List<String> getCleanLabelsForCurrentScreen(AccessibilityNodeInfo root) {
        List<String> labels = new ArrayList<>();
        collectLabels(root, labels);
        
        List<String> cleanLabels = new ArrayList<>();
        for (String label : labels) {
            String trimmed = label.trim();
            if (!trimmed.isEmpty() && !cleanLabels.contains(trimmed)) {
                cleanLabels.add(trimmed);
            }
        }
        return cleanLabels;
    }

    /**
     * Extracts currently visible labels, but ALSO merges them with any offline 
     * cached labels we know belong to the current page (e.g. items that are scrolled off-screen).
     */
    public static List<String> getKnownLabelsForCurrentScreen(AccessibilityNodeInfo root) {
        List<String> cleanLabels = getCleanLabelsForCurrentScreen(root);
        
        if (currentParent != null) {
            List<String> historicLabels = uiTree.get(currentParent);
            if (historicLabels != null) {
                for (String label : historicLabels) {
                    if (!cleanLabels.contains(label)) {
                        cleanLabels.add(label);
                    }
                }
            }
        }
        
        return cleanLabels;
    }

    private static String extractScreenTitle(List<String> cleanLabels, AccessibilityNodeInfo root) {
        if (root == null) return null;
        
        // 0. Explicit Hardcoded Guard for Settings Root
        if (cleanLabels.contains("Network & internet") && cleanLabels.contains("Apps") && cleanLabels.contains("Battery")) {
            return "Settings_Root";
        }
        
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collectAllNodes(root, nodes);
        
        // 1. Look for explicit title IDs
        for (AccessibilityNodeInfo node : nodes) {
            String resName = node.getViewIdResourceName();
            if (resName != null && resName.toLowerCase().contains("title")) {
                if (node.getText() != null && node.getText().length() > 0) return node.getText().toString();
            }
        }
        
        // 2. Android standard heuristic: The title is usually the first text right after the "Navigate up" button
        boolean foundNavigateUp = false;
        for (AccessibilityNodeInfo node : nodes) {
            CharSequence text = node.getText() != null ? node.getText() : node.getContentDescription();
            if (text != null && text.length() > 0) {
                String t = text.toString().trim().toLowerCase();
                if (foundNavigateUp) {
                    return text.toString();
                }
                // Only trigger on EXACT string matches to avoid 'backup' triggering 'back'
                if (t.equals("navigate up") || t.equals("back")) {
                    foundNavigateUp = true;
                }
            }
        }
        
        return null; // Could not find a safe title
    }

    private static void collectAllNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) {
        if (node == null) return;
        list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAllNodes(node.getChild(i), list);
        }
    }

    public static List<String> findPathToKeyword(String targetKeyword) {
        if (targetKeyword == null) return null;
        String targetLower = targetKeyword.toLowerCase();

        for (Map.Entry<String, List<String>> entry : uiTree.entrySet()) {
            for (String childLabel : entry.getValue()) {
                if (childLabel.toLowerCase().contains(targetLower)) {
                    List<String> path = new ArrayList<>();
                    if (!entry.getKey().equals("Settings_Root")) {
                        path.add(entry.getKey());
                    }
                    path.add(childLabel);
                    return path;
                }
            }
        }
        return null;
    }

    public static String getBeautifulJsonString() {
        try {
            JSONObject rootJson = new JSONObject();
            for (Map.Entry<String, List<String>> entry : uiTree.entrySet()) {
                JSONArray arr = new JSONArray();
                for (String s : entry.getValue()) {
                    if (s != null && !s.trim().isEmpty()) {
                        arr.put(s.trim());
                    }
                }
                rootJson.put(entry.getKey(), arr);
            }
            return rootJson.toString(4);
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to generate JSON string: " + e.getMessage());
            return "{}";
        }
    }

    private static void saveToFile(Context context) {
        try {
            String beautifulJsonStr = getBeautifulJsonString();
            
            File file = new File(context.getExternalFilesDir(null), "settings_ui_tree.json");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(beautifulJsonStr.getBytes());
            fos.close();
            
            Log.d(TAG, "✅ UI Cache Manager: Persisted beautifully formatted tree to " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to save UI Cache file: " + e.getMessage());
        }
    }

private static final boolean CLEAR_CACHE_ON_STARTUP = false; // Set to true only to wipe cache for testing

    private static void loadFromFile(Context context) {
        try {
            File file = new File(context.getExternalFilesDir(null), "settings_ui_tree.json");
            
            if (CLEAR_CACHE_ON_STARTUP && file.exists()) {
                boolean deleted = file.delete();
                Log.w(TAG, "🧹 DEV MODE: Wiped previous ui_tree cache on startup! (Deleted: " + deleted + ")");
                uiTree.clear();
                isLoaded = true;
                return;
            }

            if (!file.exists()) {
                isLoaded = true;
                return;
            }

            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String jsonStr = new String(data);
            JSONObject rootJson = new JSONObject(jsonStr);

            uiTree.clear();
            Iterator<String> keys = rootJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray arr = rootJson.getJSONArray(key);
                List<String> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getString(i));
                }
                uiTree.put(key, list);
            }
            
            isLoaded = true;
            Log.d(TAG, "✅ UI Cache Manager: Loaded previous JSON tree from disk! (" + uiTree.size() + " parent nodes)");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load UI Cache file: " + e.getMessage());
            isLoaded = true; // Still mark as loaded to avoid endless loop
        }
    }
}
