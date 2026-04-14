package com.example.myapplication.xmlui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.finance.model.TransactionType;
import com.google.firebase.Timestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TransactionRowAdapter extends RecyclerView.Adapter<TransactionRowAdapter.TransactionViewHolder> {
    private static final ZoneId DEVICE_ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_HEADER_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    public interface TransactionActionListener {
        void onDelete(UiTransaction transaction);
    }

    public interface TransactionClickListener {
        void onClick(UiTransaction transaction);
    }

    private final List<UiTransaction> items = new ArrayList<>();
    private final TransactionActionListener actionListener;
    private final TransactionClickListener clickListener;
    private final boolean showDateHeaders;

    public TransactionRowAdapter() {
        this(null, null, false);
    }

    public TransactionRowAdapter(TransactionActionListener actionListener) {
        this(actionListener, null, false);
    }

    public TransactionRowAdapter(
        TransactionActionListener actionListener,
        TransactionClickListener clickListener
    ) {
        this(actionListener, clickListener, false);
    }

    public TransactionRowAdapter(
        TransactionActionListener actionListener,
        TransactionClickListener clickListener,
        boolean showDateHeaders
    ) {
        this.actionListener = actionListener;
        this.clickListener = clickListener;
        this.showDateHeaders = showDateHeaders;
    }

    public void submit(List<UiTransaction> transactions) {
        List<UiTransaction> newItems = transactions == null ? Collections.emptyList() : new ArrayList<>(transactions);
        List<UiTransaction> oldItems = new ArrayList<>(items);
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
                UiTransaction oldItem = oldItems.get(oldItemPosition);
                UiTransaction newItem = newItems.get(newItemPosition);
                return Objects.equals(oldItem.getWalletName(), newItem.getWalletName())
                    && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                    && Objects.equals(oldItem.getNote(), newItem.getNote())
                    && Objects.equals(oldItem.getType(), newItem.getType())
                    && Objects.equals(oldItem.getCategoryIconKey(), newItem.getCategoryIconKey())
                    && Objects.equals(oldItem.getWalletIconKey(), newItem.getWalletIconKey())
                    && Objects.equals(oldItem.getWalletAccountType(), newItem.getWalletAccountType())
                    && Objects.equals(oldItem.getDestinationWalletName(), newItem.getDestinationWalletName())
                    && Objects.equals(oldItem.getDestinationWalletIconKey(), newItem.getDestinationWalletIconKey())
                    && Objects.equals(oldItem.getDestinationWalletAccountType(), newItem.getDestinationWalletAccountType())
                    && Double.compare(oldItem.getAmount(), newItem.getAmount()) == 0
                    && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt());
            }
        });
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
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
        if (clickListener == null) {
            holder.itemView.setOnClickListener(null);
        } else {
            holder.itemView.setOnClickListener(v -> clickListener.onClick(tx));
        }
        String categoryText = tx.getCategory() == null || tx.getCategory().trim().isEmpty()
            ? holder.tvCategory.getContext().getString(R.string.default_category_other)
            : tx.getCategory().trim();
        holder.tvCategory.setText(categoryText);
        TransactionType txType = parseTransactionType(tx.getType());
        String categoryIconKey = tx.getCategoryIconKey();
        if (categoryIconKey == null || categoryIconKey.trim().isEmpty()) {
            categoryIconKey = CategoryUiHelper.inferIconKeyFromCategoryName(tx.getCategory(), txType);
        }
        holder.ivCategoryIcon.setImageResource(CategoryUiHelper.iconResForKey(categoryIconKey, txType));
        holder.ivCategoryIcon.setImageTintList(ColorStateList.valueOf(
            holder.ivCategoryIcon.getContext().getColor(CategoryUiHelper.iconTintForKey(categoryIconKey, txType))
        ));
        holder.layoutCategoryIcon.setBackgroundTintList(ColorStateList.valueOf(
            holder.layoutCategoryIcon.getContext().getColor(CategoryUiHelper.iconBgForKey(categoryIconKey, txType))
        ));

        String note = tx.getNote() == null ? "" : tx.getNote().trim();
        if (note.isEmpty()) {
            holder.tvNote.setText("");
            holder.tvNote.setVisibility(View.GONE);
        } else {
            holder.tvNote.setText(note);
            holder.tvNote.setVisibility(View.VISIBLE);
        }
        if ("TRANSFER".equalsIgnoreCase(tx.getType())
            && tx.getDestinationWalletName() != null
            && !tx.getDestinationWalletName().trim().isEmpty()) {
            holder.rowDestination.setVisibility(View.VISIBLE);
            holder.tvDestinationWallet.setText(
                holder.tvDestinationWallet.getContext().getString(
                    R.string.history_transfer_destination_format,
                    tx.getDestinationWalletName().trim()
                )
            );
            holder.ivDestinationIcon.setImageResource(
                WalletUiMapper.iconResForKey(
                    tx.getDestinationWalletIconKey(),
                    tx.getDestinationWalletAccountType()
                )
            );
            holder.ivDestinationIcon.setImageTintList(ColorStateList.valueOf(
                holder.ivDestinationIcon.getContext().getColor(
                    WalletUiMapper.iconTintColor(tx.getDestinationWalletAccountType())
                )
            ));
        } else {
            holder.rowDestination.setVisibility(View.GONE);
            holder.tvDestinationWallet.setText("");
        }
        holder.tvWallet.setText(tx.getWalletName());
        holder.ivWalletIcon.setImageResource(WalletUiMapper.iconResForKey(tx.getWalletIconKey(), tx.getWalletAccountType()));
        holder.ivWalletIcon.setImageTintList(ColorStateList.valueOf(
            holder.ivWalletIcon.getContext().getColor(WalletUiMapper.iconTintColor(tx.getWalletAccountType()))
        ));
        holder.rowWallet.setVisibility(
            tx.getWalletName() == null || tx.getWalletName().trim().isEmpty() ? View.GONE : View.VISIBLE
        );
        if (showDateHeaders) {
            holder.tvDate.setVisibility(View.GONE);
        } else {
            holder.tvDate.setVisibility(View.VISIBLE);
            holder.tvDate.setText(UiFormatters.dateTime(tx.getCreatedAt()));
        }
        bindDateHeader(holder, position, tx);
        holder.tvAmount.setText(UiFormatters.money(tx.getAmount()));
        holder.tvAmount.setTextColor(holder.tvAmount.getContext().getColor(amountColorRes(tx.getType())));
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

    private void bindDateHeader(TransactionViewHolder holder, int position, UiTransaction current) {
        if (!showDateHeaders) {
            holder.layoutDateHeader.setVisibility(View.GONE);
            return;
        }
        LocalDate currentDate = toLocalDate(current.getCreatedAt());
        LocalDate previousDate = position > 0
            ? toLocalDate(items.get(position - 1).getCreatedAt())
            : null;
        boolean showHeader = position == 0 || previousDate == null || !currentDate.equals(previousDate);
        if (!showHeader) {
            holder.layoutDateHeader.setVisibility(View.GONE);
            return;
        }
        holder.layoutDateHeader.setVisibility(View.VISIBLE);
        holder.tvDateHeader.setText(DATE_HEADER_FORMAT.format(currentDate));
        String relativeLabel = relativeDateLabel(currentDate, holder.itemView.getContext());
        if (relativeLabel.isEmpty()) {
            holder.tvDateRelative.setVisibility(View.GONE);
            holder.tvDateRelative.setText("");
        } else {
            holder.tvDateRelative.setVisibility(View.VISIBLE);
            holder.tvDateRelative.setText(relativeLabel);
        }
    }

    private String relativeDateLabel(LocalDate date, Context context) {
        LocalDate today = LocalDate.now(DEVICE_ZONE);
        if (date.equals(today)) {
            return context.getString(R.string.history_filter_day_today);
        }
        if (date.equals(today.minusDays(1))) {
            return context.getString(R.string.history_filter_day_yesterday);
        }
        return "";
    }

    private LocalDate toLocalDate(Timestamp timestamp) {
        if (timestamp == null) {
            return LocalDate.now(DEVICE_ZONE);
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanoseconds())
            .atZone(DEVICE_ZONE)
            .toLocalDate();
    }

    private int amountColorRes(String rawType) {
        if ("INCOME".equalsIgnoreCase(rawType)) {
            return R.color.income_green;
        }
        if ("TRANSFER".equalsIgnoreCase(rawType)) {
            return R.color.text_primary;
        }
        return R.color.expense_red;
    }

    private TransactionType parseTransactionType(String rawType) {
        if ("INCOME".equalsIgnoreCase(rawType)) {
            return TransactionType.INCOME;
        }
        if ("TRANSFER".equalsIgnoreCase(rawType)) {
            return TransactionType.TRANSFER;
        }
        return TransactionType.EXPENSE;
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        final View layoutDateHeader;
        final TextView tvDateHeader;
        final TextView tvDateRelative;
        final View layoutCategoryIcon;
        final TextView tvAmount;
        final TextView tvCategory;
        final ImageView ivCategoryIcon;
        final TextView tvNote;
        final View rowDestination;
        final ImageView ivDestinationIcon;
        final TextView tvDestinationWallet;
        final View rowWallet;
        final ImageView ivWalletIcon;
        final TextView tvWallet;
        final TextView tvDate;
        final View btnDelete;

        TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutDateHeader = itemView.findViewById(R.id.layoutTransactionDateHeader);
            tvDateHeader = itemView.findViewById(R.id.tvTransactionDateHeader);
            tvDateRelative = itemView.findViewById(R.id.tvTransactionDateRelative);
            layoutCategoryIcon = itemView.findViewById(R.id.layoutTransactionCategoryIcon);
            tvAmount = itemView.findViewById(R.id.tvTransactionAmount);
            tvCategory = itemView.findViewById(R.id.tvTransactionCategory);
            ivCategoryIcon = itemView.findViewById(R.id.ivTransactionCategoryIcon);
            tvNote = itemView.findViewById(R.id.tvTransactionNote);
            rowDestination = itemView.findViewById(R.id.rowTransactionDestination);
            ivDestinationIcon = itemView.findViewById(R.id.ivTransactionDestinationIcon);
            tvDestinationWallet = itemView.findViewById(R.id.tvTransactionDestinationWallet);
            rowWallet = itemView.findViewById(R.id.rowTransactionWallet);
            ivWalletIcon = itemView.findViewById(R.id.ivTransactionWalletIcon);
            tvWallet = itemView.findViewById(R.id.tvTransactionWallet);
            tvDate = itemView.findViewById(R.id.tvTransactionDate);
            btnDelete = itemView.findViewById(R.id.btnTransactionDelete);
        }
    }
}
