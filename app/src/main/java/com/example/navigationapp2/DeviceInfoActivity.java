package com.example.navigationapp2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class DeviceInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        TextView tvGroqIdentity = findViewById(R.id.tvGroqIdentity);
        TextView tvBuildInfo    = findViewById(R.id.tvBuildInfo);
        TextView tvAppInfo      = findViewById(R.id.tvAppInfo);

        // ── What the app sends to Groq ──────────────────────────────────────
        String groqLine = "Device: " + Build.MANUFACTURER + " " + Build.MODEL;
        tvGroqIdentity.setText(groqLine);

        // ── Full Build Info ──────────────────────────────────────────────────
        StringBuilder build = new StringBuilder();
        append(build, "Manufacturer",   Build.MANUFACTURER);
        append(build, "Brand",          Build.BRAND);
        append(build, "Model",          Build.MODEL);
        append(build, "Product",        Build.PRODUCT);
        append(build, "Device",         Build.DEVICE);
        append(build, "Board",          Build.BOARD);
        append(build, "Hardware",       Build.HARDWARE);
        append(build, "Android",        Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        append(build, "Security Patch", Build.VERSION.SECURITY_PATCH);
        append(build, "Build ID",       Build.ID);
        append(build, "Fingerprint",    Build.FINGERPRINT);
        append(build, "Display",        Build.DISPLAY);
        append(build, "Host",           Build.HOST);
        append(build, "Tags",           Build.TAGS);
        append(build, "Type",           Build.TYPE);
        append(build, "User",           Build.USER);
        tvBuildInfo.setText(build.toString().trim());

        // ── App Info ─────────────────────────────────────────────────────────
        StringBuilder app = new StringBuilder();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            append(app, "Package",        pi.packageName);
            append(app, "Version Name",   pi.versionName);
            append(app, "Version Code",   String.valueOf(pi.getLongVersionCode()));
        } catch (Exception e) {
            app.append("Unable to read app info");
        }
        tvAppInfo.setText(app.toString().trim());

        // ── Copy All ─────────────────────────────────────────────────────────
        String fullText = groqLine + "\n\n" + build.toString().trim() + "\n\n" + app.toString().trim();
        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Device Info", fullText));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });
    }

    private void append(StringBuilder sb, String key, String value) {
        sb.append(key).append(": ").append(value != null ? value : "N/A").append("\n");
    }
}
