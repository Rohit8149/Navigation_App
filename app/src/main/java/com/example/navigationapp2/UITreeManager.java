package com.example.navigationapp2;

import android.view.accessibility.AccessibilityNodeInfo;

public class UITreeManager {
    private static final UITreeManager instance = new UITreeManager();
    private String lastTreeString = "Listening for UI changes. Open Settings or System Apps to capture tree...";
    
    private TreeUpdateListener listener;

    public interface TreeUpdateListener {
        void onTreeUpdated(String tree);
    }
    
    private UITreeManager() {}

    public static UITreeManager getInstance() {
        return instance;
    }

    public synchronized void setListener(TreeUpdateListener listener) {
        this.listener = listener;
        if (listener != null) {
            listener.onTreeUpdated(lastTreeString);
        }
    }

    public void updateJSONTree() {
        new Thread(() -> {
            String result = UICacheManager.getBeautifulJsonString();
            
            synchronized(this) {
                lastTreeString = result;
            }
            
            if (listener != null) {
                listener.onTreeUpdated(result);
            }
        }).start();
    }
}
