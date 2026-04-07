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

public class TransactionRowAdapter extends RecyclerView.Adapter<TransactionRowAdapter.TransactionViewHolder> {

    public interface TransactionActionListener {
        void onDelete(UiTransaction transaction);
    }

    private final List<UiTransaction> items = new ArrayList<>();
    private final TransactionActionListener actionListener;

    public TransactionRowAdapter() {
        this.actionListener = null;
    }

    public TransactionRowAdapter(TransactionActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submit(List<UiTransaction> transactions) {
        items.clear();
        if (transactions != null) {
            items.addAll(transactions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_row, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        UiTransaction tx = items.get(position);
        holder.tvCategory.setText(tx.getCategory().isEmpty() ? holder.tvCategory.getContext().getString(R.string.default_category_other) : tx.getCategory());
        holder.tvNote.setText(tx.getNote().isEmpty()
            ? holder.tvNote.getContext().getString(R.string.placeholder_dash)
            : tx.getNote());
        holder.tvWallet.setText(tx.getWalletName());
        holder.tvDate.setText(UiFormatters.dateTime(tx.getCreatedAt()));
        holder.tvType.setText(typeLabel(holder, tx.getType()));
        String sign = isExpenseLike(tx.getType()) ? "- " : "+ ";
        holder.tvAmount.setText(holder.tvAmount.getContext()
            .getString(R.string.format_signed_amount, sign, UiFormatters.money(tx.getAmount())));
        holder.tvAmount.setTextColor(holder.tvAmount.getContext().getColor(isExpenseLike(tx.getType()) ? R.color.expense_red : R.color.income_green));
        if (actionListener == null) {
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDelete.setOnClickListener(null);
        } else {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> actionListener.onDelete(tx));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String typeLabel(TransactionViewHolder holder, String rawType) {
        if ("TRANSFER".equalsIgnoreCase(rawType)) {
            return holder.tvType.getContext().getString(R.string.transaction_type_transfer);
        }
        if ("INCOME".equalsIgnoreCase(rawType)) {
            return holder.tvType.getContext().getString(R.string.transaction_type_income);
        }
        return holder.tvType.getContext().getString(R.string.transaction_type_expense);
    }

    private boolean isExpenseLike(String rawType) {
        return !"INCOME".equalsIgnoreCase(rawType);
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvType;
        final TextView tvAmount;
        final TextView tvCategory;
        final TextView tvNote;
        final TextView tvWallet;
        final TextView tvDate;
        final View btnDelete;

        TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvTransactionType);
            tvAmount = itemView.findViewById(R.id.tvTransactionAmount);
            tvCategory = itemView.findViewById(R.id.tvTransactionCategory);
            tvNote = itemView.findViewById(R.id.tvTransactionNote);
            tvWallet = itemView.findViewById(R.id.tvTransactionWallet);
            tvDate = itemView.findViewById(R.id.tvTransactionDate);
            btnDelete = itemView.findViewById(R.id.btnTransactionDelete);
        }
    }
}
