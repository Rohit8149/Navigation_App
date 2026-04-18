package com.example.navigationapp2;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class NodeMatcher {

    private static final String TAG = "NAV_APP";

    // 🔥 RESULT CLASS (node + path)
    public static class MatchResult {
        public AccessibilityNodeInfo node;
        public List<Integer> path;

        public MatchResult(AccessibilityNodeInfo node, List<Integer> path) {
            this.node = node;
            this.path = path;
        }
    }

    // 🔍 Find the best node matching the keywords and return its path
    public static MatchResult findBestMatchWithPath(
            AccessibilityNodeInfo root,
            List<String> keywords
    ) {
        if (root == null) {
            Log.w(TAG, "⚠️ Root node is null, cannot find match");
            return null;
        }

        Log.d(TAG, "🔎 Searching for keywords: " + keywords);

        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);

        Log.d(TAG, "ℹ️ Total nodes collected: " + nodes.size());

        for (AccessibilityNodeInfo node : nodes) {

            if (node == null) continue;

            String text = safe(node.getText());
            String desc = safe(node.getContentDescription());

            for (String key : keywords) {

                if (text.contains(key) || desc.contains(key)) {

                    Log.d(TAG, "✅ Keyword match found: \"" + key + "\" in node: text=" + text + ", desc=" + desc);

                    if (!node.isVisibleToUser()) {
                        Log.d(TAG, "⚠️ Node not visible to user, skipping");
                        continue;
                    }

                    if (!node.isEnabled()) {
                        Log.d(TAG, "⚠️ Node not enabled, skipping");
                        continue;
                    }

                    AccessibilityNodeInfo clickable = getClickable(node);

                    if (clickable != null) {

                        List<Integer> path = NodePathUtils.getNodePath(clickable);
                        Log.d(TAG, "🟢 Clickable node found. Path: " + path);

                        return new MatchResult(clickable, path);
                    } else {
                        Log.d(TAG, "⚠️ No clickable parent found for node");
                    }
                }
            }
        }

        Log.w(TAG, "❌ No matching node found for keywords: " + keywords);
        return null;
    }

    // 🔁 Recursive traversal to collect all nodes
    private static void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) {

        if (node == null) return;

        list.add(node);

        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), list);
        }
    }

    // 🔹 Safe text conversion to lowercase
    private static String safe(CharSequence cs) {
        return cs == null ? "" : cs.toString().toLowerCase();
    }

    // 🔹 Find nearest clickable parent
    private static AccessibilityNodeInfo getClickable(AccessibilityNodeInfo node) {

        while (node != null) {
            if (node.isClickable()) {
                Log.d(TAG, "🟢 Node is clickable: " + safe(node.getText()));
                return node;
            }
            node = node.getParent();
        }

        Log.d(TAG, "⚠️ Reached top of tree, no clickable node found");
        return null;
    }
}