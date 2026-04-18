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
        Log.d(TAG, "🟡 GroqApiClient → sending: " + userInput);

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

    // ─── Prompt ───────────────────────────────────────────────────────────────

    private static String buildPrompt(String userInput) {
        return "You are a strict JSON generator.\n"
                + "DO NOT return markdown.\n"
                + "Return ONLY valid JSON.\n\n"
                + "The Android Settings app is already open.\n"
                + "Convert the user command into navigation path steps inside Settings only.\n"
                + "Each step must be an array of alternative text labels that could appear on different phones.\n"
                + "Provide as many common label variations as possible for items.\n"
                + "CRITICAL RULE: Provide EXACTLY the minimum sequence of steps needed to fulfill the command. "
                + "Do NOT add extra deeper menus if the user only asked for a top-level category. "
                + "For example, if the user says 'open location', your output should be EXACTLY ONE step: [[\"Location\", \"Location services\"]]. "
                + "Do NOT append extra steps like [\"Mode\", \"Location mode\"] unless the user specifically asked to change the mode.\n"
                + "If a Settings item can appear under multiple names, include those alternatives "
                + "in the same step array.\n"
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
                + "If the request is not a valid Android Settings navigation command, return:\n"
                + "{ \"intent\": \"unknown\", \"slots\": {}, \"missing_slots\": [\"command\"], "
                + "\"navigation_paths\": [] }\n\n"
                + "Command: " + userInput + "\n";
    }
}
