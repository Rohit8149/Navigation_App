package com.example.navigationapp2;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class UITreeActivity extends AppCompatActivity {

    private TextView textTree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ui_tree);

        textTree = findViewById(R.id.textTree);
        textTree.setText("No settings explored yet. Start the assistant and navigate through Settings to build the map.");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("UI Tree", textTree.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        // Set listener to receive UI tree updates
        UITreeManager.getInstance().setListener(tree -> {
            // Must update UI on main thread
            runOnUiThread(() -> textTree.setText(tree));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove listener to prevent memory leaks
        UITreeManager.getInstance().setListener(null);
    }
}
