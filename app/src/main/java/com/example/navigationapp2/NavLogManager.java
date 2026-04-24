package com.example.navigationapp2;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NavLogManager {

    private static final NavLogManager instance = new NavLogManager();
    private final List<NavLog> history = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault());

    private NavLogManager() {}

    public static NavLogManager getInstance() {
        return instance;
    }

    public synchronized void addLog(String query, String initialPath) {
        String timestamp = timeFormat.format(new Date());
        history.add(0, new NavLog(timestamp, query, initialPath)); // Add to top
    }

    public synchronized void addRerouteToLatest(String reroutePath) {
        if (!history.isEmpty()) {
            history.get(0).addReroute(reroutePath);
        }
    }

    public synchronized List<NavLog> getLogs() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
    
    public synchronized void clear() {
        history.clear();
    }
}
