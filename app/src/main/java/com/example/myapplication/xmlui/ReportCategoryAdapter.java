package com.example.myapplication.xmlui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

public class ReportCategoryAdapter extends RecyclerView.Adapter<ReportCategoryAdapter.ReportCategoryViewHolder> {

    private final List<UiReportCategory> items = new ArrayList<>();

    public void submit(List<UiReportCategory> categories) {
        items.clear();
        if (categories != null) {
            items.addAll(categories);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportCategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report_category_row, parent, false);
        return new ReportCategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportCategoryViewHolder holder, int position) {
        UiReportCategory item = items.get(position);
        holder.tvName.setText(item.getName());
        holder.tvAmount.setText(UiFormatters.money(item.getAmount()));
        holder.tvPercent.setText(UiFormatters.percent(item.getRatio()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ReportCategoryViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvPercent;
        final TextView tvAmount;

        ReportCategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCategoryName);
            tvPercent = itemView.findViewById(R.id.tvCategoryPercent);
            tvAmount = itemView.findViewById(R.id.tvCategoryAmount);
        }
    }
}
