package com.example.navigationapp2;

public class CommandClassifier {

    private static final String[] COMMAND_KEYWORDS = {
            "open", "go to", "show", "navigate", "change", "set", "turn on", "turn off",
            "enable", "disable", "find", "search", "check", "view", "help"
    };

    private static final String[] TARGET_KEYWORDS = {
            "settings", "wifi", "hotspot", "about phone", "about", "phone", "system",
            "accessibility", "display", "bluetooth", "mobile data", "data", "network",
            "sound", "volume", "battery", "apps", "app", "security", "privacy",
            "notifications", "storage", "display", "brightness", "connection",
            "password", "lock", "location", "camera", "ringtone", "vpn", "update",
            "language", "keyboard", "date", "time", "accounts", "developer", "reset"
    };

    private static final String[] INVALID_PHRASES = {
            "hello", "hi", "how are you", "what is", "who is", "tell me",
            "joke", "story", "song", "movie", "game", "translate", "meaning"
    };

    public static boolean isNavigationCommand(String text) {
        if (text == null) return false;
        String normalized = normalize(text);

        if (normalized.length() < 3) {
            return false;
        }

        if (containsAny(normalized, INVALID_PHRASES)) {
            return false;
        }

        if (!containsAny(normalized, TARGET_KEYWORDS)) {
            return false;
        }

        return containsAny(normalized, COMMAND_KEYWORDS) || normalized.contains("settings") || normalized.contains("hotspot") || normalized.contains("wifi");
    }

    public static String getValidationError(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Please type what you want to do on your phone.";
        }

        String normalized = normalize(text);

        if (normalized.length() < 3) {
            return "Please type a command with at least a few words.";
        }

        if (containsAny(normalized, INVALID_PHRASES)) {
            return "I can only guide phone settings and device actions, not general chat.";
        }

        if (!containsAny(normalized, TARGET_KEYWORDS)) {
            return "Please ask for something like opening Settings, Wi-Fi, Hotspot, About Phone, or another phone setting.";
        }

        return null;
    }

    private static String normalize(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim();
    }

    private static boolean containsAny(String text, String[] terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
