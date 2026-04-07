package com.example.myapplication.xmlui;

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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        items.clear();
        if (wallets != null) {
            items.addAll(wallets);
        }
        notifyDataSetChanged();
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
        holder.tvBalance.setText(formatMoney(wallet.getBalance()));
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
        holder.vDivider.setVisibility(position == items.size() - 1 ? View.GONE : View.VISIBLE);
        holder.itemView.setOnClickListener(actionListener == null ? null : v -> actionListener.onWalletClick(wallet));
        holder.ivMore.setOnClickListener(actionListener == null ? null : v -> actionListener.onWalletClick(wallet));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatMoney(double value) {
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(new Locale("vi", "VN"));
        formatter.applyPattern("#,###");
        return formatter.format(value) + " ₫";
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
            tvLockedBadge = itemView.findViewById(R.id.tvWalletLockedBadge);
            vDivider = itemView.findViewById(R.id.vWalletDivider);
        }
    }
}
