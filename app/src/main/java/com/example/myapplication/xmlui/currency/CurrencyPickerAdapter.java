package com.example.myapplication.xmlui.currency;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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
        holder.tvCode.setText(code);
        boolean isSelected = selectedCode.equalsIgnoreCase(code);
        holder.ivSelected.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        holder.tvCode.setTextColor(context.getColor(isSelected ? R.color.blue_primary : R.color.text_primary));

        holder.ivFlag.setText(CurrencyFlagUtils.flagEmojiForCurrency(code));

        return row;
    }

    private static class ViewHolder {
        final TextView ivFlag;
        final TextView tvCode;
        final android.widget.ImageView ivSelected;

        ViewHolder(View view) {
            ivFlag = view.findViewById(R.id.ivCurrencyOptionFlag);
            tvCode = view.findViewById(R.id.tvCurrencyOptionCode);
            ivSelected = view.findViewById(R.id.ivCurrencyOptionSelected);
        }
    }
}
