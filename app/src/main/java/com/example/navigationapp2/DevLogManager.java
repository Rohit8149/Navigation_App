package com.example.navigationapp2;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DevLogManager — in-memory circular log buffer for developer trace.
 *
 * Any class calls DevLogManager.log("TAG", "message") and it is
 * stored in a circular buffer (max 800 entries).
 * DevLogActivity reads it and auto-refreshes every second.
 */
public class DevLogManager {

    private static final DevLogManager instance = new DevLogManager();
    private static final int MAX_ENTRIES = 800;

    private final List<String> entries = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    // Listener for real-time updates when LogActivity is open
    private LogListener listener;

    public interface LogListener {
        void onNewLog(String fullLog);
    }

    private DevLogManager() {}

    public static DevLogManager getInstance() { return instance; }

    // ─── Write ────────────────────────────────────────────────────────────────

    public synchronized void log(String tag, String message) {
        String entry = fmt.format(new Date()) + "  [" + tag + "]  " + message;
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) entries.remove(0);
        if (listener != null) listener.onNewLog(buildFull());
    }

    // Convenience helpers
    public static void info(String tag, String msg)  { getInstance().log("ℹ " + tag, msg); }
    public static void match(String tag, String msg) { getInstance().log("🎯 " + tag, msg); }
    public static void step(String tag, String msg)  { getInstance().log("👣 " + tag, msg); }
    public static void click(String tag, String msg) { getInstance().log("👆 " + tag, msg); }
    public static void warn(String tag, String msg)  { getInstance().log("⚠️ " + tag, msg); }
    public static void error(String tag, String msg) { getInstance().log("❌ " + tag, msg); }
    public static void ok(String tag, String msg)    { getInstance().log("✅ " + tag, msg); }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public synchronized String buildFull() {
        StringBuilder sb = new StringBuilder();
        // Show newest at the top
        List<String> reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);
        for (String e : reversed) sb.append(e).append("\n");
        return sb.toString();
    }

    public synchronized void clear() { entries.clear(); }

    // ─── Listener ─────────────────────────────────────────────────────────────

    public synchronized void setListener(LogListener l) {
        this.listener = l;
        if (l != null) l.onNewLog(buildFull());
    }
}
