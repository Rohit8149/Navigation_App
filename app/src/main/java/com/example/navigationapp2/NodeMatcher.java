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
            DevLogManager.warn("NodeMatcher", "Root node is null — cannot search");
            return null;
        }

        DevLogManager.step("NodeMatcher", "Searching for: " + keywords);

        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);

        MatchResult bestResult  = null;
        int         highestScore = -1;
        String      bestMatchText = null;
        String      bestMatchKey  = null;

        for (AccessibilityNodeInfo node : nodes) {
            if (node == null) continue;

            String text = safe(node.getText());
            String desc = safe(node.getContentDescription());
            if (text.isEmpty() && desc.isEmpty()) continue; // skip invisible/empty nodes

            for (String key : keywords) {
                int score = calculateMatchScore(text, desc, key);

                if (score > 0) {
                    // Log every hit with context
                    String nodeLabel = !text.isEmpty() ? "\"" + text + "\"" : "(desc) \"" + desc + "\"";
                    DevLogManager.match("NodeMatcher",
                        "  keyword=\"" + key + "\" → node=" + nodeLabel + "  score=" + score);

                    if (!node.isVisibleToUser()) {
                        DevLogManager.warn("NodeMatcher", "    ↳ SKIP — not visible");
                        continue;
                    }
                    if (!node.isEnabled()) {
                        DevLogManager.warn("NodeMatcher", "    ↳ SKIP — not enabled");
                        continue;
                    }

                    AccessibilityNodeInfo clickable = getClickable(node);
                    if (clickable != null) {
                        if (score > highestScore) {
                            highestScore  = score;
                            bestMatchText = nodeLabel;
                            bestMatchKey  = key;
                            List<Integer> path = NodePathUtils.getNodePath(clickable);
                            bestResult = new MatchResult(clickable, path);
                        }
                    } else {
                        DevLogManager.warn("NodeMatcher", "    ↳ SKIP — no clickable parent");
                    }
                }
            }
        }

        if (bestResult != null) {
            DevLogManager.ok("NodeMatcher",
                "BEST MATCH: keyword=\"" + bestMatchKey + "\" matched node=" + bestMatchText + " (score=" + highestScore + ")");
            Log.d(TAG, "🟢 BEST clickable node found! (Score: " + highestScore + ")");
            return bestResult;
        }

        DevLogManager.warn("NodeMatcher", "NO MATCH found for keywords: " + keywords);
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

    // 🔹 Tiered Match Scoring Logic — strict word-aware matching
    private static int calculateMatchScore(String text, String desc, String key) {
        if (key == null || key.isEmpty()) return 0;
        key = key.trim().toLowerCase();

        int bestScore = 0;

        // 1. Check Primary Text
        if (text != null && !text.isEmpty()) {
            text = text.trim().toLowerCase();
            String cleanText = text.replaceAll("[^a-z0-9]", "");
            String cleanKey  = key.replaceAll("[^a-z0-9]", "");

            // Tier 1 – perfect exact match (100)
            if (text.equals(key) || cleanText.equals(cleanKey)) {
                return 100;
            }

            // Tier 2 – node text starts with the keyword (80)
            // e.g. key="font size" matches "Font size and style"
            if (text.startsWith(key)) {
                bestScore = Math.max(bestScore, 80);
            }

            // Tier 3 – the node text CONTAINS the keyword as a whole phrase (65)
            // e.g. key="font size" inside "Change font size"
            if (text.contains(key)) {
                bestScore = Math.max(bestScore, 65);
            }

            // Tier 4 – word-level overlap: every word in the keyword must appear
            //   as a whole word inside the node text.
            //   "font size" → words ["font","size"] must both be whole words in text.
            //   "display size" will NOT match because "font" is missing.
            if (bestScore == 0 && key.contains(" ")) {
                String[] keyWords  = key.split("\\s+");
                String[] textWords = text.split("[^a-z0-9]+");
                int matched = 0;
                for (String kw : keyWords) {
                    for (String tw : textWords) {
                        if (tw.equals(kw)) { matched++; break; }
                    }
                }
                // All keyword words must match a whole word in the text
                if (matched == keyWords.length) {
                    bestScore = Math.max(bestScore, 70);
                }
                // Majority (≥ 2/3) match — but only for 3-word+ keywords
                else if (keyWords.length >= 3 && matched >= (keyWords.length * 2 / 3 + 1)) {
                    bestScore = Math.max(bestScore, 50);
                }
            }

            // Tier 5 – single-word key: cleanText must START WITH cleanKey (not just contain)
            //   Prevents "font" matching "default" or "fontsize" matching "displaysize"
            if (bestScore == 0 && !key.contains(" ")) {
                if (cleanText.startsWith(cleanKey)) {
                    bestScore = Math.max(bestScore, 60);
                } else {
                    // Levenshtein only for very short strings that are nearly identical
                    int maxLen = Math.max(cleanText.length(), cleanKey.length());
                    if (maxLen > 0 && cleanKey.length() >= 4) {
                        double similarity = 1.0 - ((double) levenshteinDistance(cleanText, cleanKey) / maxLen);
                        // Raised threshold to 0.92 — only near-identical strings qualify
                        if (similarity >= 0.92) bestScore = Math.max(bestScore, 40);
                    }
                }
            }
        }

        // 2. Check Description Text (lower priority, no fuzzy)
        if (desc != null && !desc.isEmpty()) {
            desc = desc.trim().toLowerCase();
            if (desc.equals(key)) {
                bestScore = Math.max(bestScore, 40);
            } else if (desc.contains(key)) {
                bestScore = Math.max(bestScore, 20);
            }
        }

        return bestScore;
    }


    private static int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1))
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
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