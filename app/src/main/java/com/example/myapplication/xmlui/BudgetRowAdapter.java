package com.example.myapplication.xmlui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

public class BudgetRowAdapter extends RecyclerView.Adapter<BudgetRowAdapter.BudgetViewHolder> {

    public interface BudgetActionListener {
        void onDelete(UiBudget budget);
    }

    private final List<UiBudget> items = new ArrayList<>();
    private final BudgetActionListener actionListener;

    public BudgetRowAdapter(BudgetActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submit(List<UiBudget> budgets) {
        items.clear();
        if (budgets != null) {
            items.addAll(budgets);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_budget_row, parent, false);
        return new BudgetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        UiBudget item = items.get(position);
        holder.tvCategory.setText(item.getCategory());
        holder.tvUsage.setText(
            holder.itemView.getContext().getString(
                R.string.label_budget_usage,
                UiFormatters.money(item.getSpent()),
                UiFormatters.money(item.getLimitAmount())
            )
        );
        double remaining = Math.max(0.0, item.getLimitAmount() - item.getSpent());
        holder.tvRemaining.setText(
            holder.itemView.getContext().getString(R.string.label_budget_remaining, UiFormatters.money(remaining))
        );
        int progress = (int) Math.min(100.0, Math.max(0.0, item.getRatio() * 100.0));
        holder.progressUsage.setProgress(progress);
        if (item.getRatio() >= 1.0) {
            holder.progressUsage.setProgressTintList(holder.itemView.getContext().getColorStateList(R.color.error_red));
            holder.tvRemaining.setText(holder.itemView.getContext().getString(
                R.string.label_budget_over,
                UiFormatters.money(item.getSpent() - item.getLimitAmount())
            ));
            holder.tvRemaining.setTextColor(holder.itemView.getContext().getColor(R.color.error_red));
        } else if (item.getRatio() >= 0.8) {
            holder.progressUsage.setProgressTintList(holder.itemView.getContext().getColorStateList(R.color.warning_orange));
            holder.tvRemaining.setTextColor(holder.itemView.getContext().getColor(R.color.warning_orange));
        } else {
            holder.progressUsage.setProgressTintList(holder.itemView.getContext().getColorStateList(R.color.blue_primary));
            holder.tvRemaining.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
        }
        holder.btnDelete.setOnClickListener(v -> actionListener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BudgetViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCategory;
        final TextView tvUsage;
        final TextView tvRemaining;
        final ProgressBar progressUsage;
        final ImageButton btnDelete;

        BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvBudgetCategory);
            tvUsage = itemView.findViewById(R.id.tvBudgetUsage);
            tvRemaining = itemView.findViewById(R.id.tvBudgetRemaining);
            progressUsage = itemView.findViewById(R.id.progressBudgetUsage);
            btnDelete = itemView.findViewById(R.id.btnBudgetDelete);
        }
    }
}
