package com.example.navigationapp2;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NAV_MAIN";

    Button btnOverlay, btnAccessibility, btnToggleAssistant;
    LinearLayout permissionLayout, controlLayout;
    
    private boolean isAssistantRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "🟢 MainActivity started");

        setContentView(R.layout.activity_main);

        btnOverlay = findViewById(R.id.btnOverlay);
        btnAccessibility = findViewById(R.id.btnAccessibility);
        btnToggleAssistant = findViewById(R.id.btnToggleAssistant);
        permissionLayout = findViewById(R.id.permissionLayout);
        controlLayout = findViewById(R.id.controlLayout);

        checkPermissions();

        // Overlay settings click
        btnOverlay.setOnClickListener(v -> {
            Log.d(TAG, "🟡 User clicked Overlay Settings");
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        });

        // Accessibility settings click
        btnAccessibility.setOnClickListener(v -> {
            Log.d(TAG, "🟡 User clicked Accessibility Settings");
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        
        // Toggle Assistant
        btnToggleAssistant.setOnClickListener(v -> {
            if (!isAssistantRunning) {
                // Start
                Intent intent = new Intent(this, OverlayService.class);
                intent.setAction("START_ASSISTANT");
                startService(intent);
                
                isAssistantRunning = true;
                btnToggleAssistant.setText("Stop Assistant");
                btnToggleAssistant.setTextColor(getResources().getColor(R.color.text_secondary, null));
            } else {
                // Stop
                Intent intent = new Intent(this, OverlayService.class);
                intent.setAction("STOP_ASSISTANT");
                startService(intent);
                
                isAssistantRunning = false;
                btnToggleAssistant.setText("Start Assistant");
                btnToggleAssistant.setTextColor(getResources().getColor(R.color.accent, null));
            }
        });
    }

    private void checkPermissions() {
        Log.d(TAG, "🔍 Checking permissions...");

        permissionLayout.setVisibility(View.VISIBLE);
        controlLayout.setVisibility(View.GONE);

        TextView textOverlay = findViewById(R.id.textOverlay);
        TextView textAccessibility = findViewById(R.id.textAccessibility);

        boolean overlayGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayGranted = Settings.canDrawOverlays(this);
        }

        boolean accessibilityGranted = isAccessibilityEnabled();

        int stepNumber = 1;

        // Overlay permission
        if (!overlayGranted) {
            Log.w(TAG, "⚠️ Overlay permission not granted");
            textOverlay.setVisibility(View.VISIBLE);
            btnOverlay.setVisibility(View.VISIBLE);
            textOverlay.setText(stepNumber + ". Enable 'Display over other apps'");
            stepNumber++;
        } else {
            textOverlay.setVisibility(View.GONE);
            btnOverlay.setVisibility(View.GONE);
        }

        // Accessibility permission
        if (!accessibilityGranted) {
            Log.w(TAG, "⚠️ Accessibility permission not granted");
            textAccessibility.setVisibility(View.VISIBLE);
            btnAccessibility.setVisibility(View.VISIBLE);
            textAccessibility.setText(stepNumber + ". Enable Accessibility Service");
            stepNumber++;
        } else {
            textAccessibility.setVisibility(View.GONE);
            btnAccessibility.setVisibility(View.GONE);
        }

        // ✅ Show Control Layout if all permissions granted
        if (overlayGranted && accessibilityGranted) {
            Log.d(TAG, "🟢 All permissions granted, showing controls");
            permissionLayout.setVisibility(View.GONE);
            controlLayout.setVisibility(View.VISIBLE);
        }
    }

    private boolean isAccessibilityEnabled() {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "❌ Accessibility setting not found", e);
            return false;
        }

        if (accessibilityEnabled == 1) {
            String enabledServices = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );

            if (enabledServices != null) {
                String service = getPackageName() + "/" + NavigationAccessibilityService.class.getName();
                boolean enabled = enabledServices.contains(service);
                Log.d(TAG, "🟢 Accessibility service enabled: " + enabled);
                return enabled;
            }
        }

        Log.d(TAG, "⚠️ Accessibility service not enabled");
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🔄 MainActivity resumed, re-checking permissions");
        checkPermissions();
    }
}