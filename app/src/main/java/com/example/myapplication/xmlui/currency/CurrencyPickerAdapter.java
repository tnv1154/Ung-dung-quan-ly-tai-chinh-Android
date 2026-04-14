package com.example.myapplication.xmlui.currency;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.myapplication.R;

import java.util.List;

public class CurrencyPickerAdapter extends BaseAdapter {
    private final Context context;
    private final List<String> currencies;
    private final LayoutInflater inflater;
    private final String selectedCode;

    public CurrencyPickerAdapter(Context context, List<String> currencies, String selectedCode) {
        this.context = context;
        this.currencies = currencies;
        this.selectedCode = selectedCode == null ? "" : selectedCode;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return currencies == null ? 0 : currencies.size();
    }

    @Override
    public Object getItem(int position) {
        return currencies.get(position);
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
            row = inflater.inflate(R.layout.item_currency_picker_row, parent, false);
            holder = new ViewHolder(row);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        String code = currencies.get(position);
        holder.tvName.setText(CurrencyLogoUtils.displayNameForCode(code));
        holder.tvCode.setText(code);
        holder.tvSymbol.setText(CurrencyLogoUtils.displaySymbolForCode(code));

        boolean isSelected = selectedCode.equalsIgnoreCase(code);
        holder.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        holder.tvSymbol.setVisibility(isSelected ? View.GONE : View.VISIBLE);
        holder.tvName.setTextColor(context.getColor(R.color.text_primary));
        holder.tvCode.setTextColor(context.getColor(R.color.text_secondary));
        holder.itemView.setBackgroundColor(context.getColor(isSelected ? R.color.group_bank_bg : android.R.color.transparent));

        CurrencyLogoUtils.bindLogo(holder.ivFlag, code, R.drawable.ic_wallet_currency);

        return row;
    }

    private static class ViewHolder {
        final ImageView ivFlag;
        final TextView tvCode;
        final TextView tvName;
        final TextView tvSymbol;
        final ImageView ivSelected;
        final View itemView;

        ViewHolder(View view) {
            itemView = view;
            ivFlag = view.findViewById(R.id.ivCurrencyOptionFlag);
            tvCode = view.findViewById(R.id.tvCurrencyOptionCode);
            tvName = view.findViewById(R.id.tvCurrencyOptionName);
            tvSymbol = view.findViewById(R.id.tvCurrencyOptionSymbol);
            ivSelected = view.findViewById(R.id.ivCurrencyOptionSelected);
        }
    }
}
