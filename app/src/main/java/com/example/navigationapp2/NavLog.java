package com.example.navigationapp2;

import java.util.ArrayList;
import java.util.List;

public class NavLog {
    private final String timestamp;
    private final String query;
    private final String initialPath;
    private final List<String> reroutes;

    public NavLog(String timestamp, String query, String initialPath) {
        this.timestamp = timestamp;
        this.query = query;
        this.initialPath = initialPath;
        this.reroutes = new ArrayList<>();
    }

    public void addReroute(String reroutePath) {
        this.reroutes.add(reroutePath);
    }

    public String getTimestamp() { return timestamp; }
    public String getQuery()     { return query; }
    public String getInitialPath() { return initialPath; }
    public List<String> getReroutes() { return reroutes; }
}
