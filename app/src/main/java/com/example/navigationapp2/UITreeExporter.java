package com.example.navigationapp2;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;

public class UITreeExporter {

    private static final String TAG = "NAV_APP";

    /**
     * Exports an AccessibilityNodeInfo tree into a JSONObject.
     *
     * @param node root node to export
     * @return JSONObject representation of the UI tree
     */
    public static JSONObject export(AccessibilityNodeInfo node) {

        JSONObject obj = new JSONObject();

        try {
            if (node == null) {
                Log.w(TAG, "⚠️ export called with null node");
                return obj;
            }

            // 🔹 Node properties
            obj.put("text", node.getText());
            obj.put("desc", node.getContentDescription());
            obj.put("class", node.getClassName());
            obj.put("clickable", node.isClickable());

            // 🔹 Recursively export children
            JSONArray children = new JSONArray();
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    children.put(export(child));
                } else {
                    Log.w(TAG, "⚠️ Null child at index " + i + " for node " + node.getClassName());
                }
            }

            obj.put("children", children);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error exporting node: " + node, e);
        }

        return obj;
    }
}