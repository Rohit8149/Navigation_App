package com.example.navigationapp2;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class LogActivity extends AppCompatActivity {

    private LogAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        ImageView btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(v -> {
            NavLogManager.getInstance().clear();
            refreshList();
        });

        recyclerView = findViewById(R.id.recyclerViewLogs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        refreshList();
    }

    private void refreshList() {
        adapter = new LogAdapter(NavLogManager.getInstance().getLogs());
        recyclerView.setAdapter(adapter);
    }
}
