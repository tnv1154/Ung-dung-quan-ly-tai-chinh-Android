package com.example.myapplication.xmlui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.TransactionType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BudgetRowAdapter extends RecyclerView.Adapter<BudgetRowAdapter.BudgetViewHolder> {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT);

    public interface BudgetActionListener {
        void onOpen(UiBudget budget);

        void onDelete(UiBudget budget);
    }

    private final List<UiBudget> items = new ArrayList<>();
    private final BudgetActionListener actionListener;

    public BudgetRowAdapter(BudgetActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submit(List<UiBudget> budgets) {
        List<UiBudget> newItems = budgets == null ? Collections.emptyList() : new ArrayList<>(budgets);
        List<UiBudget> oldItems = new ArrayList<>(items);
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
                    oldItems.get(oldItemPosition).getId(),
                    newItems.get(newItemPosition).getId()
                );
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                UiBudget oldItem = oldItems.get(oldItemPosition);
                UiBudget newItem = newItems.get(newItemPosition);
                return Objects.equals(oldItem.getName(), newItem.getName())
                    && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                    && Double.compare(oldItem.getLimitAmount(), newItem.getLimitAmount()) == 0
                    && Double.compare(oldItem.getSpent(), newItem.getSpent()) == 0
                    && Double.compare(oldItem.getRatio(), newItem.getRatio()) == 0
                    && Double.compare(oldItem.getRemaining(), newItem.getRemaining()) == 0
                    && Objects.equals(oldItem.getRepeatCycle(), newItem.getRepeatCycle())
                    && oldItem.getStartDateEpochDay() == newItem.getStartDateEpochDay()
                    && oldItem.getEndDateEpochDay() == newItem.getEndDateEpochDay()
                    && oldItem.getDaysRemaining() == newItem.getDaysRemaining()
                    && oldItem.isActive() == newItem.isActive();
            }
        });
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
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
        Context context = holder.itemView.getContext();
        applyBudgetCategoryIcon(holder, context, item);
        holder.tvName.setText(item.getName());
        holder.tvPeriod.setText(formatPeriod(context, item));
        holder.tvLimit.setText(UiFormatters.money(item.getLimitAmount()));
        holder.tvDaysLeft.setText(formatDays(context, item));

        double remaining = item.getRemaining();
        if (remaining >= 0.0) {
            holder.tvRemaining.setText(
                context.getString(R.string.label_budget_remaining, UiFormatters.money(remaining))
            );
            holder.tvRemaining.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        } else {
            holder.tvRemaining.setText(
                context.getString(R.string.label_budget_over, UiFormatters.money(Math.abs(remaining)))
            );
            holder.tvRemaining.setTextColor(ContextCompat.getColor(context, R.color.error_red));
        }

        int progress = (int) Math.min(100.0, Math.max(0.0, item.getRatio() * 100.0));
        holder.progressUsage.setProgress(progress);
        if (item.getRatio() >= 1.0) {
            holder.progressUsage.setProgressTintList(ContextCompat.getColorStateList(context, R.color.error_red));
            holder.tvDaysLeft.setTextColor(ContextCompat.getColor(context, R.color.error_red));
        } else if (item.getRatio() >= 0.8) {
            holder.progressUsage.setProgressTintList(ContextCompat.getColorStateList(context, R.color.warning_orange));
            holder.tvDaysLeft.setTextColor(ContextCompat.getColor(context, R.color.warning_orange));
        } else {
            holder.progressUsage.setProgressTintList(ContextCompat.getColorStateList(context, R.color.blue_primary));
            holder.tvDaysLeft.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }

        holder.itemView.setOnClickListener(v -> actionListener.onOpen(item));
        holder.itemView.setOnLongClickListener(v -> {
            actionListener.onDelete(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BudgetViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName;
        final TextView tvPeriod;
        final TextView tvLimit;
        final TextView tvDaysLeft;
        final TextView tvRemaining;
        final ProgressBar progressUsage;

        BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivBudgetIcon);
            tvName = itemView.findViewById(R.id.tvBudgetName);
            tvPeriod = itemView.findViewById(R.id.tvBudgetPeriod);
            tvLimit = itemView.findViewById(R.id.tvBudgetLimit);
            tvDaysLeft = itemView.findViewById(R.id.tvBudgetDaysLeft);
            tvRemaining = itemView.findViewById(R.id.tvBudgetRemaining);
            progressUsage = itemView.findViewById(R.id.progressBudgetUsage);
        }
    }

    private void applyBudgetCategoryIcon(BudgetViewHolder holder, Context context, UiBudget item) {
        String categoryName = resolveBudgetCategoryName(context, item);
        String iconKey = CategoryUiHelper.inferIconKeyFromCategoryName(categoryName, TransactionType.EXPENSE);
        holder.ivIcon.getBackground().setTint(
            ContextCompat.getColor(context, CategoryUiHelper.iconBgForKey(iconKey, TransactionType.EXPENSE))
        );
        int fallbackRes = CategoryUiHelper.iconResForKey(iconKey, TransactionType.EXPENSE);
        boolean loadedFromAssets = CategoryAssetIconLoader.applyCategoryIcon(
            holder.ivIcon,
            TransactionType.EXPENSE,
            categoryName,
            fallbackRes
        );
        holder.ivIcon.setImageTintList(loadedFromAssets
            ? null
            : ColorStateList.valueOf(
                ContextCompat.getColor(context, CategoryUiHelper.iconTintForKey(iconKey, TransactionType.EXPENSE))
            ));
    }

    private String resolveBudgetCategoryName(Context context, UiBudget item) {
        if (BudgetLimit.CATEGORY_ALL.equalsIgnoreCase(item.getCategory())) {
            return context.getString(R.string.category_icon_money_out);
        }
        String source = item.getCategory() == null ? "" : item.getCategory().trim();
        if (source.isEmpty()) {
            source = item.getName();
        }
        return source == null ? "" : source.trim();
    }

    private String formatPeriod(Context context, UiBudget item) {
        LocalDate startDate = LocalDate.ofEpochDay(item.getStartDateEpochDay());
        LocalDate endDate = LocalDate.ofEpochDay(item.getEndDateEpochDay());
        String categoryText = BudgetLimit.CATEGORY_ALL.equalsIgnoreCase(item.getCategory())
            ? context.getString(R.string.budget_category_all)
            : item.getCategory();
        String periodText = DATE_FORMAT.format(startDate) + " - " + DATE_FORMAT.format(endDate);
        if (BudgetLimit.REPEAT_MONTHLY.equals(item.getRepeatCycle())) {
            return context.getString(R.string.budget_period_monthly, periodText, categoryText);
        }
        return context.getString(R.string.budget_period_single, periodText, categoryText);
    }

    private String formatDays(Context context, UiBudget item) {
        if (item.isActive()) {
            return context.getResources().getQuantityString(
                R.plurals.budget_days_left_quantity,
                (int) item.getDaysRemaining(),
                item.getDaysRemaining()
            );
        }
        if (item.getDaysRemaining() > 0) {
            return context.getResources().getQuantityString(
                R.plurals.budget_days_until_start_quantity,
                (int) item.getDaysRemaining(),
                item.getDaysRemaining()
            );
        }
        return context.getString(R.string.budget_inactive);
    }
}
