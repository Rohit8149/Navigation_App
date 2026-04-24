package com.example.navigationapp2;

public class CommandClassifier {

    /**
     * Only blocks obvious non-navigation chat phrases.
     * Everything else is passed straight to Groq to interpret.
     * Removed the strict TARGET_KEYWORDS whitelist so commands like
     * "refresh rate", "fps", "haptics" etc. are no longer blocked.
     */
    private static final String[] INVALID_PHRASES = {
            "hello", "hi", "how are you", "what is your name",
            "joke", "story", "song", "movie", "game"
    };

    public static String getValidationError(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Please type what you want to do on your phone.";
        }

        String normalized = normalize(text);

        if (normalized.length() < 3) {
            return "Please type a bit more.";
        }

        if (containsAny(normalized, INVALID_PHRASES)) {
            return "I can only help navigate phone settings, not general chat.";
        }

        // Let Groq decide everything else — no keyword whitelist
        return null;
    }

    private static String normalize(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim();
    }

    private static boolean containsAny(String text, String[] terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}
