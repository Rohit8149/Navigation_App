# 🧭 NavigationApp — AI-Powered Android Settings Navigator

> An intelligent, accessibility-powered assistant that lets users navigate Android Settings hands-free using natural language commands, powered by the **Groq LLaMA API**.

---

## 📱 What It Does

NavigationApp is an Android application that listens to voice or text commands like *"Open Wi-Fi settings"* or *"Change font size"* and autonomously navigates the Android Settings app on the user's behalf using Android's **Accessibility Services**. It uses an AI model (Groq / LLaMA) to figure out the correct navigation path through the Settings menu hierarchy and then executes each step automatically.

---

## ✅ Current Features (v1.0 — Implemented)

### 🤖 AI-Powered Navigation
- Sends the user's command to **Groq LLaMA API** which returns a step-by-step navigation path through Android Settings.
- The AI is given the current live **UI tree** of the device screen so it can make contextual decisions.
- Supports re-routing: if a navigation step is missing, the AI is called again with updated context.

### ♿ Accessibility Service Integration
- `NavigationAccessibilityService` captures the full live UI tree of the screen.
- Detects when the Settings app is open/closed and activates/deactivates the assistant accordingly.
- Prevents false-positive exits caused by transient system UI overlays.

### ⚙️ Smart Node Matching (`NodeMatcher`)
- Uses a **word-boundary-aware fuzzy matching** algorithm to find the correct UI element to tap.
- Implements **Levenshtein distance** scoring with strict thresholds to avoid false matches (e.g., "Font size" vs "Display size").
- Falls back through multiple matching strategies: exact → word-boundary → fuzzy → content-description.

### 🗺️ Agentic Navigation Executor (`NavigationExecutor`)
- Maintains a full **exploration history** (visited path stack) to avoid re-visiting nodes and prevent navigation loops.
- Enforces root-to-leaf navigation order: always navigates from the top of the Settings menu downward.
- Performs automatic scrolling to find off-screen elements before declaring a step as "not found".

### 🧠 Assistant Controller (`AssistantController`)
- Orchestrates the full navigation lifecycle: command → AI call → path parsing → node execution.
- Manages retry logic and communicates status back to the UI overlay.

### 🪟 Floating Overlay UI (`OverlayService` / `AccessibilityOverlay`)
- A persistent floating icon lives on top of all apps when the assistant is active.
- Tapping the icon opens a **centered input dialog** where the user types a navigation command.
- A **status bar pill overlay** shows real-time navigation progress (e.g., "Navigating step 2/4...").
- A **Help button** allows manual re-trigger of AI rerouting when stuck.
- Glass-morphism dark theme with animated transitions.

### 🔍 UI Tree Viewer (`UITreeActivity` / `UITreeManager`)
- A built-in screen that dumps and displays the **live accessibility UI tree** of the current foreground app.
- Useful for debugging: see exactly what nodes the AI and navigator are working with.
- UI tree is cached via `UICacheManager` for quick repeated access.

### 📋 Developer Logging (`DevLogManager` / `NavLogManager`)
- `DevLogManager`: Captures real-time navigation decisions, match scores, API responses, and search outcomes.
- `NavLogManager` + `NavLog`: Structured log entries per navigation step with timestamps.
- `LogActivity` + `LogAdapter`: In-app log viewer screen to inspect all developer logs without needing ADB.

### 📟 Device Info Screen (`DeviceInfoActivity`)
- Dedicated screen showing device information (model, Android version, accessibility service state, etc.).
- Useful for diagnostics and bug reports.

### 🔐 Navigation Guard (`NavigationGuard`)
- Detects when the user has left the Settings app and pauses the navigation session.
- Distinguishes real app exits from temporary system UI events (e.g., notification shade, volume popup).

### 💬 Command Classifier (`CommandClassifier`)
- Classifies raw user input to determine whether it is a navigation command or a general query.
- Routes the command to the appropriate handler.

### 🎨 Premium Dark UI
- Full dark-mode glassmorphism design throughout the app.
- Custom drawables for cards, overlays, and icons.
- Smooth animations and micro-interactions on all interactive elements.
- Splash screen with animated branding.

---

## 🏗️ Architecture Overview

```
User Input (Text/Voice)
        │
        ▼
CommandClassifier
        │
        ▼
AssistantController
    │           │
    ▼           ▼
GroqApiClient  UICacheManager
(LLaMA AI)    (Live UI Tree)
    │
    ▼
NavigationExecutor
    │
    ▼
NodeMatcher ──► AccessibilityService ──► Tap/Scroll on Screen
    │
    ▼
DevLogManager / NavLogManager (Logging)
    │
    ▼
OverlayService (Status Feedback to User)
```

---

## 🚀 Future Updates Roadmap

The following features are planned for future releases. Each one can be picked up and implemented independently.

---

### 🔵 Level 2 — Core Improvements (Short-term)

#### [ ] FU-01: Voice Input Support
- Replace the text input dialog with a **speech-to-text** interface using Android `SpeechRecognizer`.
- Allow hands-free activation via a wake word (e.g., *"Hey Navigator"*).
- Show a waveform animation while listening.

#### [ ] FU-02: Multi-Step Command Queueing
- Allow users to input a **sequence of commands** at once (e.g., "Open Wi-Fi, then toggle it off, then go back").
- Parse commands into a queue and execute them one by one with status updates between steps.

#### [ ] FU-03: Settings Value Reading & Reporting
- After navigating to a setting, **read and speak/display the current value** (e.g., "Font size is currently Large").
- Use the accessibility node's `getText()` and `getStateDescription()` to extract values.

#### [ ] FU-04: Toggle/Change Setting Values
- After navigating to a setting, **actually change it** (e.g., toggle a switch, select a value from a slider/list).
- Implement a `SettingsMutator` class that detects the type of control (switch, seekbar, radio button, dropdown) and interacts accordingly.

#### [ ] FU-05: Persistent Session History
- Save the last N navigation sessions to local storage (Room database or SharedPreferences).
- Show a "Recent Commands" list in the main UI so users can quickly repeat past commands.

---

### 🟡 Level 3 — Intelligence & Reliability (Mid-term)

#### [ ] FU-06: On-Device AI Fallback (Offline Mode)
- Integrate a small on-device model (e.g., **Gemini Nano** via `MediaPipe LLM Inference API`) as a fallback when the internet is unavailable.
- The on-device model handles common, simple navigation paths; Groq is used for complex ones.

#### [ ] FU-07: Adaptive Path Learning
- After a successful navigation, **cache the resolved path** for that command locally.
- On subsequent identical/similar commands, skip the API call and use the cached path directly.
- Implement a `PathCache` with an LRU eviction policy.

#### [ ] FU-08: App-Agnostic Navigation
- Extend support beyond Android Settings to **any installed app** (e.g., "Open YouTube and go to Subscriptions").
- The AI would need the package name + initial UI tree to construct a valid navigation path.
- Add an app-picker UI to select the target application before giving a command.

#### [ ] FU-09: Confidence Score Display
- Show the **AI's confidence score** for each navigation step in the status bar overlay.
- If confidence is below a threshold, prompt the user for confirmation before proceeding.

#### [ ] FU-10: Improved Scroll Strategy
- Implement **bi-directional scrolling** (scroll up before accepting "not found").
- Add **nested scroll container** detection so the executor scrolls the right container, not just the root.

---

### 🟠 Level 4 — Platform & UX Expansion (Long-term)

#### [ ] FU-11: TalkBack / Accessibility-Friendly Mode
- Ensure all overlays and dialogs are **fully compatible with TalkBack** screen reader.
- Add content descriptions to all custom views and drawables.
- Audit touch target sizes (minimum 48dp) across the entire app.

#### [ ] FU-12: Shortcuts & Quick Actions Widget
- Add a **home screen widget** with a microphone button to instantly trigger navigation without opening the app.
- Support Android **App Shortcuts** (long-press icon) for the most-used commands.

#### [ ] FU-13: Settings Profiles
- Allow users to create **"Settings Profiles"** (e.g., "Office Mode", "Gaming Mode") that are a collection of settings to apply all at once.
- One tap applies all settings in a profile sequentially using the navigation engine.

#### [ ] FU-14: Multi-Language Support (i18n)
- Add full internationalization support with string resources for major languages.
- Adapt the AI prompt to handle commands in the user's language.
- Detect device locale and set default language automatically.

#### [ ] FU-15: Cloud Sync & Backup
- Allow users to back up their command history, profiles, and preferences to **Firebase Firestore** or **Google Drive**.
- Sync across multiple devices using the same Google account.

---

### 🔴 Level 5 — Advanced AI & System Integration (Future Vision)

#### [ ] FU-16: Visual Grounding (Screen Understanding)
- Capture a **screenshot** of the current screen and send it alongside the UI tree to a **vision-capable model** (e.g., Gemini 1.5 Pro with Vision).
- The model can identify UI elements by visual appearance, not just accessibility labels — handling apps with poor accessibility support.

#### [ ] FU-17: Proactive Suggestions
- Analyze usage patterns and **proactively suggest** settings adjustments (e.g., "Your Battery is at 15% — want me to enable Battery Saver?").
- Use Android `UsageStatsManager` for context-aware recommendations.

#### [ ] FU-18: Macro Recorder
- Allow users to **record a sequence of manual taps/actions** and save it as a replayable macro.
- Combine with the AI: describe what you want in words and the app will record + replay the path.

#### [ ] FU-19: Wearable Companion (Wear OS)
- Build a **Wear OS companion app** that lets users trigger navigation commands from their smartwatch.
- The watch sends commands to the phone over the Wearable Data Layer API.

#### [ ] FU-20: Enterprise MDM Integration
- Allow IT administrators to **pre-configure allowed settings** and deploy navigation command whitelists via an MDM profile.
- Useful for organizations that want employees to navigate only approved settings.

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java (Android) |
| Min SDK | API 26 (Android 8.0) |
| AI / LLM | Groq Cloud API (LLaMA 3) |
| Accessibility | Android AccessibilityService API |
| UI | Custom Views, XML Layouts, Dark Glass Theme |
| Networking | OkHttp3 |
| Logging | Custom DevLogManager + NavLogManager |
| Build | Gradle (Kotlin DSL) |

---

## 🔧 Setup & Configuration

1. Clone this repository.
2. Open in **Android Studio**.
3. Add your **Groq API key** in `GroqApiClient.java`:
   ```java
   private static final String API_KEY = "your_groq_api_key_here";
   ```
4. Build and install on a physical Android device (API 26+).
5. Grant **Accessibility Service** permission in Android Settings → Accessibility → NavigationApp.
6. Launch the app and tap **Start Assistant**.

---

## 📂 Key Classes Reference

| Class | Purpose |
|---|---|
| `AssistantController` | Orchestrates the full navigation session |
| `GroqApiClient` | Sends prompts to Groq LLaMA and parses responses |
| `NavigationExecutor` | Executes AI-generated paths step by step |
| `NodeMatcher` | Fuzzy-matches text commands to real UI nodes |
| `NavigationAccessibilityService` | Hooks into Android Accessibility to read UI trees |
| `UICacheManager` | Caches and exports the live UI accessibility tree |
| `OverlayService` | Manages the floating icon and status overlay |
| `AccessibilityOverlay` | The floating command input dialog |
| `NavigationGuard` | Detects accidental exits from the target app |
| `DevLogManager` | Developer logging for navigation decisions |
| `NavLogManager` | Per-step structured navigation logs |
| `DeviceInfoActivity` | Device diagnostics screen |
| `UITreeActivity` | Live UI tree viewer for debugging |
| `LogActivity` | In-app developer log viewer |

---

## 📄 License

This project is for personal and educational use. All rights reserved © 2026.
