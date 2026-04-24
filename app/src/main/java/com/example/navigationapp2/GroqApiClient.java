package com.example.navigationapp2;

import com.example.navigationapp2.BuildConfig;
import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

/**
 * GroqApiClient — pure Groq API client. Zero business logic.
 *
 * Sends the user command to Groq's LLaMA model and returns
 * structured JSON: { intent, slots, missing_slots, navigation_paths }
 *
 * Previously named ChatGPTService (misnomer — this uses Groq, not OpenAI).
 */
public class GroqApiClient {

    private static final String TAG = "NAV_APP";
    private static final String API_KEY = BuildConfig.GROQ_API_KEY;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    = "llama-3.3-70b-versatile";

    public interface Callback {
        void onSuccess(JSONObject data);
        void onError(String error);
    }

    /**
     * Sends {@code userInput} to Groq and returns structured navigation JSON.
     * Always calls back on the OkHttp background thread — callers must post
     * to the main thread if they need to update UI.
     */
    public static void processCommand(String userInput, Callback callback) {
        String deviceBrand = android.os.Build.MANUFACTURER;
        String deviceModel = android.os.Build.MODEL;
        
        Log.d(TAG, "🟡 GroqApiClient → sending: " + userInput);
        Log.d(TAG, "📱 Device Identity Sent: " + deviceBrand + " " + deviceModel);

        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("temperature", 0.0);
            body.put("max_tokens", 400);

            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", buildPrompt(userInput));
            body.put("messages", new org.json.JSONArray().put(message));

            Request request = new Request.Builder()
                    .url(GROQ_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(),
                            MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "🔴 Groq network failure: " + e.getMessage());
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String raw = response.body().string();
                        Log.d(TAG, "🟢 Groq raw response:\n" + raw);

                        JSONObject json = new JSONObject(raw);

                        if (json.has("error")) {
                            String err = json.getJSONObject("error").getString("message");
                            Log.e(TAG, "❌ Groq API error: " + err);
                            callback.onError(err);
                            return;
                        }

                        String content = json.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        // Strip markdown code fences if present
                        content = content.replace("```json", "")
                                .replace("```", "")
                                .trim();

                        Log.d(TAG, "🟢 Groq parsed content:\n" + content);

                        JSONObject result = new JSONObject(content);
                        callback.onSuccess(result);

                    } catch (Exception e) {
                        Log.e(TAG, "🔴 Groq parse error: " + e.getMessage());
                        callback.onError(e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "🔴 Groq request build error: " + e.getMessage());
            callback.onError(e.getMessage());
        }
    }

    public static void processRerouteCommand(String originalGoal, String expectedStep, java.util.List<String> visibleOptions, String settingsKnowledgeMap, Callback callback) {
        String deviceBrand = android.os.Build.MANUFACTURER;
        String deviceModel = android.os.Build.MODEL;
        
        Log.d(TAG, "🟡 GroqApiClient → sending REROUTE for: " + originalGoal);

        OkHttpClient client = new OkHttpClient();

        try {
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("temperature", 0.0);
            body.put("max_tokens", 600);

            JSONObject message = new JSONObject();
            message.put("role", "user");
            
            String prompt = "You are a strict JSON generator. Return ONLY valid JSON.\n\n"
                + "Device: " + deviceBrand + " " + deviceModel + "\n"
                + "The user's original goal was: '" + originalGoal + "'.\n"
                + "We expected to click: '" + expectedStep + "' but it is NOT present on the current screen.\n"
                + "Here are the ONLY text labels visible on the current screen right now:\n"
                + "[" + String.join(", ", visibleOptions) + "]\n\n"
                + "We have a knowledge map of Android Settings screens explored so far on this device.\n"
                + "This map contains screen names as keys and their known menu items as values:\n"
                + settingsKnowledgeMap + "\n\n"
                + "INSTRUCTIONS:\n"
                + "1. Check the knowledge map. If the expected step '" + expectedStep + "' does NOT appear inside any screen's list in the map, it means that path is WRONG on this device. DO NOT suggest navigating through a screen that does not contain the target.\n"
                + "2. Based ONLY on the text labels currently VISIBLE on screen, pick the most likely category to tap to reach the goal.\n"
                + "3. Return the FULL remaining sequence of forward steps from this screen to the goal.\n"
                + "4. NEVER output a BACK step. Only move forward from the current screen using items VISIBLE right now.\n"
                + "5. Output exactly one JSON object with fields: intent, slots, missing_slots, navigation_paths.\n\n"
                + "Example (Goal is 'Developer options', screen shows 'System', 'Display', 'Apps'):\n"
                + "{\n"
                + "  \"intent\": \"reroute\",\n"
                + "  \"slots\": {},\n"
                + "  \"missing_slots\": [],\n"
                + "  \"navigation_paths\": [\n"
                + "    [[\"System\", \"System & updates\"], [\"Developer options\", \"Developer mode\"]]\n"
                + "  ]\n"
                + "}\n";

            message.put("content", prompt);
            body.put("messages", new org.json.JSONArray().put(message));

            Request request = new Request.Builder()
                    .url(GROQ_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "🔴 Groq (Reroute) network failure: " + e.getMessage());
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String raw = response.body().string();
                        JSONObject json = new JSONObject(raw);
                        if (json.has("error")) {
                            String err = json.getJSONObject("error").getString("message");
                            callback.onError(err);
                            return;
                        }

                        String content = json.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content");
                        content = content.replace("```json", "").replace("```", "").trim();
                        Log.d(TAG, "🟢 Groq Reroute parsed content:\n" + content);

                        JSONObject result = new JSONObject(content);
                        callback.onSuccess(result);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    // ─── Prompt ───────────────────────────────────────────────────────────────

    private static String buildPrompt(String userInput) {
        String deviceBrand = android.os.Build.MANUFACTURER;
        String deviceModel = android.os.Build.MODEL;

        return "You are a strict JSON generator.\n"
                + "DO NOT return markdown.\n"
                + "Return ONLY valid JSON.\n\n"
                + "Device: " + deviceBrand + " " + deviceModel + "\n"
                + "The Android Settings app is already open.\n"
                + "Convert the user command into navigation path steps inside Android Settings.\n"
                + "Assume the user is starting from the absolute root Main Menu of the Settings app. The very first step in your path MUST ALWAYS be an option visible on the top-level Main Settings screen (e.g. 'Network', 'Display', 'System', 'Apps'). Do NOT output the path backwards. Do NOT skip the parent categories.\n"
                + "Each step must be an array of alternative text labels that ALL refer to the EXACT SAME setting — just named differently across phone brands.\n"
                + "ALIAS RULE: Only include labels that are genuinely the same setting with different wording. Do NOT group unrelated settings together even if they are nearby.\n"
                + "BAD EXAMPLE (wrong): [\"Font size\", \"Display size\"] — these are TWO different settings. Grouping them will cause the wrong item to be highlighted.\n"
                + "GOOD EXAMPLE (correct): [\"Font size\", \"Font size and style\", \"Text size\"] — all refer to the same thing.\n"
                + "CRITICAL RULE: Provide EXACTLY the minimum correct sequence of steps needed to fulfill the command starting from the top-level root menu. "
                + "Do NOT add extra deeper menus if the user only asked for a top-level category. "
                + "For example, if the user says 'open location', your output should be EXACTLY ONE step: [[\"Location\", \"Location services\"]]. "
                + "Do NOT append extra steps like [\"Mode\", \"Location mode\"] unless the user specifically asked to change the mode.\n"
                + "Think broadly — Settings contains many items like Refresh Rate, Developer Options, Haptics, Touch sensitivity, etc. "
                + "If a Settings item can appear under multiple names ON DIFFERENT PHONES (same function, different label), include those alternatives in the same step array.\n"
                + "Output exactly one JSON object with fields: intent, slots, missing_slots, navigation_paths.\n"
                + "navigation_paths must be an array of alternative valid step sequences to accommodate different UI brandings.\n\n"
                + "Example command: 'open about phone'\n"
                + "Example output:\n"
                + "{\n"
                + "  \"intent\": \"about_phone\",\n"
                + "  \"slots\": {},\n"
                + "  \"missing_slots\": [],\n"
                + "  \"navigation_paths\": [\n"
                + "    [[\"About phone\",\"About device\",\"About tablet\"]]\n"
                + "  ]\n"
                + "}\n\n"
                + "If you truly cannot find ANY relevant path in Android Settings, return navigation_paths as an empty array [].\n\n"
                + "Command: " + userInput + "\n";
    }
}
