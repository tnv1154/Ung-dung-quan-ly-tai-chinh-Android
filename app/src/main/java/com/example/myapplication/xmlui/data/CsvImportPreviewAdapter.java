package com.example.myapplication.xmlui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.finance.model.CsvImportRow;
import com.example.myapplication.finance.model.TransactionType;
import com.google.firebase.Timestamp;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvImportPreviewAdapter extends RecyclerView.Adapter<CsvImportPreviewAdapter.CsvPreviewViewHolder> {
    public interface RowClickListener {
        void onRowClick(int position, CsvImportRow row);
    }

    private final List<CsvImportRow> items = new ArrayList<>();
    private final RowClickListener rowClickListener;

    public CsvImportPreviewAdapter() {
        this(null);
    }

    public CsvImportPreviewAdapter(RowClickListener rowClickListener) {
        this.rowClickListener = rowClickListener;
    }

    public void submit(List<CsvImportRow> rows) {
        int previousSize = items.size();
        items.clear();
        if (previousSize > 0) {
            notifyItemRangeRemoved(0, previousSize);
        }
        if (rows != null) {
            items.addAll(rows);
        }
        if (!items.isEmpty()) {
            notifyItemRangeInserted(0, items.size());
        }
    }

    @NonNull
    @Override
    public CsvPreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_csv_import_preview, parent, false);
        return new CsvPreviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CsvPreviewViewHolder holder, int position) {
        holder.bind(items.get(position), rowClickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class CsvPreviewViewHolder extends RecyclerView.ViewHolder {
        private final FrameLayout statusContainer;
        private final ImageView ivStatus;
        private final TextView tvTitle;
        private final TextView tvMetaLeft;
        private final TextView tvAmount;
        private final TextView tvMetaRight;

        CsvPreviewViewHolder(@NonNull View itemView) {
            super(itemView);
            statusContainer = itemView.findViewById(R.id.layoutCsvPreviewStatus);
            ivStatus = itemView.findViewById(R.id.ivCsvPreviewStatus);
            tvTitle = itemView.findViewById(R.id.tvCsvPreviewTitle);
            tvMetaLeft = itemView.findViewById(R.id.tvCsvPreviewMetaLeft);
            tvAmount = itemView.findViewById(R.id.tvCsvPreviewAmount);
            tvMetaRight = itemView.findViewById(R.id.tvCsvPreviewMetaRight);
        }

        void bind(CsvImportRow row, RowClickListener rowClickListener) {
            if (rowClickListener == null) {
                itemView.setOnClickListener(null);
            } else {
                itemView.setOnClickListener(v -> {
                    int adapterPosition = getAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) {
                        return;
                    }
                    rowClickListener.onRowClick(adapterPosition, row);
                });
            }
            Context context = itemView.getContext();
            boolean isValid = row != null && row.isValid();
            TransactionType type = row == null ? null : row.getType();
            String note = row == null ? "" : safe(row.getNote()).trim();
            String category = row == null ? "" : safe(row.getCategory()).trim();
            String title = !note.isEmpty() ? note : (!category.isEmpty() ? category : context.getString(R.string.csv_import_preview_row_fallback_title));

            tvTitle.setText(title);
            if (isValid) {
                itemView.setBackgroundResource(R.drawable.bg_csv_preview_item_valid);
                statusContainer.setBackgroundResource(R.drawable.bg_csv_preview_status_valid);
                ivStatus.setImageResource(R.drawable.ic_action_check);
                ivStatus.setImageTintList(ContextCompat.getColorStateList(context, R.color.income_green));
                tvMetaLeft.setText(context.getString(R.string.csv_import_preview_date_format, formatDateTime(row.getTransactionCreatedAt())));
                tvMetaLeft.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            } else {
                itemView.setBackgroundResource(R.drawable.bg_csv_preview_item_invalid);
                statusContainer.setBackgroundResource(R.drawable.bg_csv_preview_status_invalid);
                ivStatus.setImageResource(R.drawable.ic_notification_warning);
                ivStatus.setImageTintList(ContextCompat.getColorStateList(context, R.color.warning_orange));
                tvMetaLeft.setText(row == null ? "" : safe(row.getValidationMessage()));
                tvMetaLeft.setTextColor(ContextCompat.getColor(context, R.color.warning_orange));
            }

            String amountText = row == null
                ? ""
                : formatAmount(row.getAmount(), row.getCurrencyCode());
            if (type == TransactionType.INCOME) {
                tvAmount.setText("+" + amountText);
                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_green));
            } else if (type == TransactionType.EXPENSE) {
                tvAmount.setText("-" + amountText);
                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_red));
            } else {
                tvAmount.setText(amountText);
                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }

            if (!isValid) {
                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.warning_orange));
            }

            String metaRight = row == null ? "" : safe(row.getCategory()).trim();
            if (metaRight.isEmpty()) {
                metaRight = row == null ? "" : safe(row.getWalletName()).trim();
            }
            tvMetaRight.setText(metaRight);
        }

        private String formatDateTime(Timestamp timestamp) {
            if (timestamp == null) {
                return "--";
            }
            Date date = new Date(timestamp.getSeconds() * 1000L);
            return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date);
        }

        private String formatAmount(double amount, String currencyCode) {
            DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(new Locale("vi", "VN"));
            decimalFormat.applyPattern("#,###.##");
            String code = safe(currencyCode).trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty() || "VND".equals(code)) {
                return UiFormatters.moneyRaw(amount);
            }
            return decimalFormat.format(amount) + " " + code;
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
