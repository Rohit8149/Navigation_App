package com.example.navigationapp2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<NavLog> logs;

    public LogAdapter(List<NavLog> logs) {
        this.logs = logs;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nav_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        NavLog log = logs.get(position);
        
        holder.tvQuery.setText(log.getQuery());
        holder.tvTimestamp.setText(log.getTimestamp());
        holder.tvInitialPath.setText(log.getInitialPath());
        
        List<String> reroutes = log.getReroutes();
        if (reroutes != null && !reroutes.isEmpty()) {
            holder.layoutReroutes.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (String r : reroutes) {
                sb.append(r).append("\n\n");
            }
            holder.tvReroutes.setText(sb.toString().trim());
        } else {
            holder.layoutReroutes.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView tvQuery, tvTimestamp, tvInitialPath, tvReroutes;
        LinearLayout layoutReroutes;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvQuery = itemView.findViewById(R.id.tvQuery);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvInitialPath = itemView.findViewById(R.id.tvInitialPath);
            tvReroutes = itemView.findViewById(R.id.tvReroutes);
            layoutReroutes = itemView.findViewById(R.id.layoutReroutes);
        }
    }
}
