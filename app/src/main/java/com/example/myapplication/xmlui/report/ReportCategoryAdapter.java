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
import com.example.myapplication.finance.model.TransactionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ReportCategoryAdapter extends RecyclerView.Adapter<ReportCategoryAdapter.ReportCategoryViewHolder> {

    private final List<UiReportCategory> items = new ArrayList<>();

    public void submit(List<UiReportCategory> categories) {
        List<UiReportCategory> newItems = categories == null ? Collections.emptyList() : new ArrayList<>(categories);
        List<UiReportCategory> oldItems = new ArrayList<>(items);
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
                UiReportCategory oldItem = oldItems.get(oldItemPosition);
                UiReportCategory newItem = newItems.get(newItemPosition);
                return Double.compare(oldItem.getAmount(), newItem.getAmount()) == 0
                    && Double.compare(oldItem.getRatio(), newItem.getRatio()) == 0
                    && oldItem.getIconRes() == newItem.getIconRes()
                    && oldItem.getIconBgColor() == newItem.getIconBgColor()
                    && oldItem.getIconTintColor() == newItem.getIconTintColor()
                    && oldItem.getChartColor() == newItem.getChartColor();
            }
        });
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
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
        holder.iconBox.setBackgroundTintList(ColorStateList.valueOf(item.getIconBgColor()));
        boolean loadedFromAssets = CategoryAssetIconLoader.applyCategoryIcon(
            holder.icon,
            TransactionType.EXPENSE,
            item.getName(),
            item.getIconRes()
        );
        holder.icon.setImageTintList(loadedFromAssets ? null : ColorStateList.valueOf(item.getIconTintColor()));
        holder.tvName.setText(item.getName());
        holder.tvAmount.setText(UiFormatters.money(item.getAmount()));
        holder.tvPercent.setText(UiFormatters.percent(item.getRatio()));
        holder.progress.setProgress((int) Math.round(Math.min(100.0, item.getRatio() * 100.0)));
        holder.progress.setProgressTintList(ColorStateList.valueOf(item.getChartColor()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ReportCategoryViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout iconBox;
        final ImageView icon;
        final TextView tvName;
        final TextView tvPercent;
        final TextView tvAmount;
        final ProgressBar progress;

        ReportCategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            iconBox = itemView.findViewById(R.id.boxCategoryIcon);
            icon = itemView.findViewById(R.id.ivCategoryIcon);
            tvName = itemView.findViewById(R.id.tvCategoryName);
            tvPercent = itemView.findViewById(R.id.tvCategoryPercent);
            tvAmount = itemView.findViewById(R.id.tvCategoryAmount);
            progress = itemView.findViewById(R.id.progressCategoryRatio);
        }
    }
}
