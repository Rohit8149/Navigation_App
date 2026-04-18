package com.example.navigationapp2;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/**
 * DebugSaver — saves the current accessibility UI tree to a JSON file on disk.
 *
 * Uses {@link UITreeExporter} to convert the tree (no duplicated logic).
 * Files are saved in: getExternalFilesDir("NavigationDebug")/<mode>/<uuid>.json
 *
 * Enable debug saving by calling DebugSaver.saveScreen(service, "tag")
 * from NavigationExecutor or the accessibility service.
 */
public class DebugSaver {

    private static final String TAG = "NAV_DEBUG";

    /**
     * Captures the current accessibility window hierarchy and saves it to disk.
     *
     * @param service The active AccessibilityService instance
     * @param mode    Subfolder label, e.g. "settings_scan", "scroll_debug"
     */
    public static void saveScreen(AccessibilityService service, String mode) {
        try {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "⚠️ Root node is null, skipping save");
                return;
            }

            // Delegate tree → JSON conversion to UITreeExporter (single source of truth)
            JSONObject tree = UITreeExporter.export(root);

            // Resolve save directory
            File baseDir = service.getExternalFilesDir("NavigationDebug");
            if (baseDir == null) baseDir = service.getFilesDir();

            File folder = new File(baseDir, mode);
            if (!folder.exists()) {
                boolean created = folder.mkdirs();
                Log.d(TAG, "📁 Folder created: " + created + " → " + folder.getAbsolutePath());
            }

            File jsonFile = new File(folder, "screen_" + UUID.randomUUID() + ".json");

            try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
                fos.write(tree.toString(2).getBytes());
            }

            Log.d(TAG, "✅ Screen saved: " + jsonFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "❌ DebugSaver error: " + e.getMessage(), e);
        }
    }
}