package com.example.myapplication.xmlui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ReportBudgetUsageAdapter extends RecyclerView.Adapter<ReportBudgetUsageAdapter.BudgetViewHolder> {

    private final List<UiReportBudgetUsage> items = new ArrayList<>();

    public void submit(List<UiReportBudgetUsage> values) {
        List<UiReportBudgetUsage> newItems = values == null ? Collections.emptyList() : new ArrayList<>(values);
        List<UiReportBudgetUsage> oldItems = new ArrayList<>(items);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(
                    oldItems.get(oldItemPosition).getName(),
                    newItems.get(newItemPosition).getName()
                );
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                UiReportBudgetUsage oldItem = oldItems.get(oldItemPosition);
                UiReportBudgetUsage newItem = newItems.get(newItemPosition);
                return Double.compare(oldItem.getSpent(), newItem.getSpent()) == 0
                    && Double.compare(oldItem.getLimit(), newItem.getLimit()) == 0
                    && Double.compare(oldItem.getRatio(), newItem.getRatio()) == 0
                    && oldItem.getIconRes() == newItem.getIconRes()
                    && oldItem.getIconBgColor() == newItem.getIconBgColor()
                    && oldItem.getIconTintColor() == newItem.getIconTintColor();
            }
        });
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report_budget_usage, parent, false);
        return new BudgetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        UiReportBudgetUsage item = items.get(position);
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(item.getIconBgColor()));
        holder.icon.setImageResource(item.getIconRes());
        holder.icon.setImageTintList(ColorStateList.valueOf(item.getIconTintColor()));
        holder.name.setText(item.getName());
        holder.percent.setText(holder.itemView.getContext().getString(R.string.format_percent_int, (int) Math.round(item.getRatio() * 100.0)));
        holder.amount.setText(holder.itemView.getContext().getString(
            R.string.report_budget_usage_spent_of_limit,
            UiFormatters.moneyRaw(item.getSpent()),
            UiFormatters.moneyRaw(item.getLimit())
        ));
        holder.progress.setProgress((int) Math.round(Math.min(100.0, item.getRatio() * 100.0)));
        holder.progress.setProgressTintList(ColorStateList.valueOf(
            holder.itemView.getContext().getColor(item.getRatio() > 1.0 ? R.color.error_red : R.color.overview_progress_fill)
        ));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BudgetViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout iconContainer;
        final ImageView icon;
        final TextView name;
        final TextView amount;
        final TextView percent;
        final ProgressBar progress;

        BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.boxReportBudgetIcon);
            icon = itemView.findViewById(R.id.ivReportBudgetIcon);
            name = itemView.findViewById(R.id.tvReportBudgetName);
            amount = itemView.findViewById(R.id.tvReportBudgetAmount);
            percent = itemView.findViewById(R.id.tvReportBudgetPercent);
            progress = itemView.findViewById(R.id.progressReportBudget);
        }
    }
}
