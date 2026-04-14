package com.example.myapplication.xmlui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.myapplication.R;

import java.util.List;

public class WalletProviderPickerAdapter extends BaseAdapter {
    private final Context context;
    private final List<WalletProviderOption> options;
    private final LayoutInflater inflater;
    private final String selectedProvider;
    private final String accountType;

    public WalletProviderPickerAdapter(
        Context context,
        List<WalletProviderOption> options,
        String selectedProvider,
        String accountType
    ) {
        this.context = context;
        this.options = options;
        this.selectedProvider = selectedProvider == null ? "" : selectedProvider;
        this.accountType = accountType == null ? WalletProviderLogoUtils.ACCOUNT_TYPE_BANK : accountType;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return options == null ? 0 : options.size();
    }

    @Override
    public Object getItem(int position) {
        return options.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(R.layout.item_wallet_provider_row, parent, false);
            holder = new ViewHolder(row);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        WalletProviderOption option = options.get(position);
        holder.tvShortName.setText(option.getShortName());
        holder.tvDisplayName.setText(option.getDisplayName());
        boolean showSubtitle = !WalletProviderLogoUtils.ACCOUNT_TYPE_EWALLET.equalsIgnoreCase(accountType)
            && !option.getDisplayName().equalsIgnoreCase(option.getShortName());
        holder.tvDisplayName.setVisibility(showSubtitle ? View.VISIBLE : View.GONE);

        boolean isSelected = WalletProviderLogoUtils.normalizeShortName(selectedProvider)
            .equals(WalletProviderLogoUtils.normalizeShortName(option.getShortName()));
        holder.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        holder.tvShortName.setTextColor(context.getColor(isSelected ? R.color.blue_primary : R.color.text_primary));
        holder.tvDisplayName.setTextColor(context.getColor(isSelected ? R.color.blue_primary : R.color.text_secondary));

        int fallbackRes = WalletProviderLogoUtils.ACCOUNT_TYPE_EWALLET.equalsIgnoreCase(accountType)
            ? R.drawable.ic_wallet_wallet
            : R.drawable.ic_wallet_card;
        WalletProviderLogoUtils.bindLogo(holder.ivLogo, accountType, option.getShortName(), fallbackRes);

        return row;
    }

    private static class ViewHolder {
        final ImageView ivLogo;
        final TextView tvShortName;
        final TextView tvDisplayName;
        final ImageView ivSelected;

        ViewHolder(View view) {
            ivLogo = view.findViewById(R.id.ivWalletProviderOptionLogo);
            tvShortName = view.findViewById(R.id.tvWalletProviderOptionShortName);
            tvDisplayName = view.findViewById(R.id.tvWalletProviderOptionDisplayName);
            ivSelected = view.findViewById(R.id.ivWalletProviderOptionSelected);
        }
    }
}
