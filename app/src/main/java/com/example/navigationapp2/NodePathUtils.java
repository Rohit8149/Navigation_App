package com.example.navigationapp2;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NodePathUtils {

    private static final String TAG = "NAV_APP";

    // =========================
    // 🔥 GET NODE PATH (from root → target node)
    // =========================
    public static List<Integer> getNodePath(AccessibilityNodeInfo node) {

        List<Integer> path = new ArrayList<>();

        if (node == null) {
            Log.w(TAG, "⚠️ getNodePath called with null node");
            return path;
        }

        while (node != null) {

            AccessibilityNodeInfo parent = node.getParent();

            if (parent == null) break;

            boolean found = false;

            // 🔍 Find index of node inside parent
            for (int i = 0; i < parent.getChildCount(); i++) {

                AccessibilityNodeInfo child = parent.getChild(i);

                if (child != null && child.equals(node)) {
                    path.add(i);
                    found = true;
                    break;
                }
            }

            if (!found) {
                Log.w(TAG, "⚠️ Node not found in parent's children, breaking path collection");
                break;
            }

            node = parent;
        }

        // reverse to get root → child order
        Collections.reverse(path);

        Log.d(TAG, "🟢 Node path calculated: " + path);
        return path;
    }

    // =========================
    // 🔥 FIND NODE USING PATH
    // =========================
    public static AccessibilityNodeInfo findNodeByPath(
            AccessibilityNodeInfo root,
            List<Integer> path
    ) {

        if (root == null) {
            Log.w(TAG, "⚠️ findNodeByPath called with null root");
            return null;
        }

        if (path == null || path.isEmpty()) {
            Log.w(TAG, "⚠️ findNodeByPath called with null/empty path");
            return null;
        }

        AccessibilityNodeInfo current = root;

        for (int index : path) {

            if (current == null) {
                Log.w(TAG, "⚠️ Current node is null while traversing path: " + path);
                return null;
            }

            if (current.getChildCount() <= index) {
                Log.w(TAG, "⚠️ Path index out of bounds: " + index + " in path " + path);
                return null;
            }

            current = current.getChild(index);
        }

        Log.d(TAG, "🟢 Node found by path: " + path);
        return current;
    }
}