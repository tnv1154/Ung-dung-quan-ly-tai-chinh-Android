package com.example.myapplication.xmlui;

import android.text.Editable;
import android.text.TextWatcher;

import com.google.android.material.textfield.TextInputEditText;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyInputFormatter {

    private MoneyInputFormatter() {
    }

    public static void attach(TextInputEditText input) {
        if (input == null) {
            return;
        }
        input.addTextChangedListener(new TextWatcher() {
            private boolean selfChange;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (selfChange) {
                    return;
                }
                String raw = s == null ? "" : s.toString();
                String normalized = normalizeAmount(raw);
                if (normalized.isEmpty()) {
                    return;
                }
                String formatted = formatGrouped(normalized);
                if (formatted.equals(raw)) {
                    return;
                }
                selfChange = true;
                input.setText(formatted);
                input.setSelection(formatted.length());
                selfChange = false;
            }
        });
    }

    public static String normalizeAmount(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        StringBuilder digits = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }

    public static String formatGrouped(String digits) {
        if (digits == null || digits.isEmpty()) {
            return "";
        }
        try {
            BigInteger number = new BigInteger(digits);
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            symbols.setGroupingSeparator('.');
            DecimalFormat decimalFormat = new DecimalFormat("#,###", symbols);
            decimalFormat.setGroupingUsed(true);
            return decimalFormat.format(number);
        } catch (NumberFormatException ex) {
            return digits;
        }
    }
}
