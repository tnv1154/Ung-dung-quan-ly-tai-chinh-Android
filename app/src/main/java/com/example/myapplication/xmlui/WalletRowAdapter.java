package com.example.myapplication.xmlui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class WalletRowAdapter extends RecyclerView.Adapter<WalletRowAdapter.WalletViewHolder> {

    public interface WalletActionListener {
        void onWalletClick(UiWallet wallet);
    }

    private final List<UiWallet> items = new ArrayList<>();
    private final WalletActionListener actionListener;

    public WalletRowAdapter() {
        this.actionListener = null;
    }

    public WalletRowAdapter(WalletActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void submit(List<UiWallet> wallets) {
        List<UiWallet> newItems = wallets == null ? Collections.emptyList() : new ArrayList<>(wallets);
        List<UiWallet> oldItems = new ArrayList<>(items);
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
                UiWallet oldItem = oldItems.get(oldItemPosition);
                UiWallet newItem = newItems.get(newItemPosition);
                return Objects.equals(oldItem.getName(), newItem.getName())
                    && Double.compare(oldItem.getBalance(), newItem.getBalance()) == 0
                    && Objects.equals(oldItem.getAccountType(), newItem.getAccountType())
                    && Objects.equals(oldItem.getIconKey(), newItem.getIconKey())
                    && Objects.equals(oldItem.getCurrency(), newItem.getCurrency())
                    && Objects.equals(oldItem.getNote(), newItem.getNote())
                    && oldItem.isIncludeInReport() == newItem.isIncludeInReport()
                    && Objects.equals(oldItem.getProviderName(), newItem.getProviderName())
                    && oldItem.isLocked() == newItem.isLocked()
                    && Objects.equals(oldItem.getConvertedVndBalance(), newItem.getConvertedVndBalance());
            }
        });
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public WalletViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallet_row, parent, false);
        return new WalletViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WalletViewHolder holder, int position) {
        UiWallet wallet = items.get(position);
        holder.tvName.setText(wallet.getName());
        CharSequence providerLine = buildProviderLine(wallet, holder.itemView);
        if (providerLine == null) {
            holder.tvProvider.setVisibility(View.GONE);
        } else {
            holder.tvProvider.setText(providerLine);
            holder.tvProvider.setVisibility(View.VISIBLE);
        }
        String originalBalance = formatMoney(wallet.getBalance(), wallet.getCurrency());
        Double convertedVnd = wallet.getConvertedVndBalance();
        boolean showConverted = convertedVnd != null && !"VND".equals(normalizeCurrency(wallet.getCurrency()));
        if (showConverted) {
            String convertedText = holder.itemView.getContext().getString(
                R.string.wallet_balance_converted_vnd_inline,
                formatVndMoney(convertedVnd)
            );
            holder.tvBalance.setText(
                holder.itemView.getContext().getString(
                    R.string.wallet_balance_original_and_converted,
                    originalBalance,
                    convertedText
                )
            );
        } else {
            holder.tvBalance.setText(originalBalance);
        }
        int balanceColor = wallet.getBalance() < 0.0 ? R.color.error_red : R.color.text_secondary;
        holder.tvBalance.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), balanceColor));
        holder.tvBalanceConverted.setVisibility(View.GONE);
        holder.iconContainer.setBackgroundTintList(ContextCompat.getColorStateList(
            holder.itemView.getContext(),
            WalletUiMapper.iconBackgroundColor(wallet.getAccountType())
        ));
        holder.ivIcon.setImageResource(WalletUiMapper.iconResForKey(wallet.getIconKey(), wallet.getAccountType()));
        holder.ivIcon.setImageTintList(ContextCompat.getColorStateList(
            holder.itemView.getContext(),
            WalletUiMapper.iconTintColor(wallet.getAccountType())
        ));
        holder.tvLockedBadge.setVisibility(wallet.isLocked() ? View.VISIBLE : View.GONE);
        holder.vDivider.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(actionListener == null ? null : v -> actionListener.onWalletClick(wallet));
        holder.ivMore.setOnClickListener(actionListener == null ? null : v -> actionListener.onWalletClick(wallet));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatMoney(double value, String currencyCode) {
        String currency = normalizeCurrency(currencyCode);
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(new Locale("vi", "VN"));
        if ("VND".equals(currency)) {
            formatter.applyPattern("#,###");
            return formatter.format(value) + " ₫";
        }
        if ("USD".equals(currency)) {
            formatter.applyPattern("#,##0.00");
            return "$" + formatter.format(value);
        }
        formatter.applyPattern("#,##0.00");
        return formatter.format(value) + " " + currency;
    }

    private String formatVndMoney(double value) {
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(new Locale("vi", "VN"));
        formatter.applyPattern("#,###");
        return formatter.format(value) + " ₫";
    }

    private String normalizeCurrency(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "VND";
        }
        return value;
    }

    private CharSequence buildProviderLine(UiWallet wallet, View itemView) {
        String normalizedType = WalletUiMapper.normalizeAccountType(wallet.getAccountType());
        if (!"BANK".equals(normalizedType) && !"EWALLET".equals(normalizedType)) {
            return null;
        }
        String provider = wallet.getProviderName() == null ? "" : wallet.getProviderName().trim();
        if (provider.isEmpty()) {
            provider = wallet.getNote() == null ? "" : wallet.getNote().trim();
        }
        if (provider.isEmpty()) {
            provider = itemView.getContext().getString(R.string.wallet_provider_not_set);
        }
        int formatRes = "BANK".equals(normalizedType)
            ? R.string.wallet_provider_bank_format
            : R.string.wallet_provider_ewallet_format;
        return itemView.getContext().getString(formatRes, provider);
    }

    static class WalletViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout iconContainer;
        final ImageView ivIcon;
        final ImageView ivMore;
        final TextView tvName;
        final TextView tvProvider;
        final TextView tvBalance;
        final TextView tvBalanceConverted;
        final TextView tvLockedBadge;
        final View vDivider;

        WalletViewHolder(@NonNull View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.iconContainerWallet);
            ivIcon = itemView.findViewById(R.id.ivWalletIcon);
            ivMore = itemView.findViewById(R.id.ivWalletMore);
            tvName = itemView.findViewById(R.id.tvWalletName);
            tvProvider = itemView.findViewById(R.id.tvWalletProvider);
            tvBalance = itemView.findViewById(R.id.tvWalletBalance);
            tvBalanceConverted = itemView.findViewById(R.id.tvWalletBalanceConverted);
            tvLockedBadge = itemView.findViewById(R.id.tvWalletLockedBadge);
            vDivider = itemView.findViewById(R.id.vWalletDivider);
        }
    }
}
