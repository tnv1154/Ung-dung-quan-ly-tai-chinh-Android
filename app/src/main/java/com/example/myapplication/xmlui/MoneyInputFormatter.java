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
        attachInternal(input, false);
    }

    public static void attachSigned(TextInputEditText input) {
        attachInternal(input, true);
    }

    private static void attachInternal(TextInputEditText input, boolean allowSigned) {
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
                String normalized = allowSigned ? normalizeSignedAmount(raw) : normalizeAmount(raw);
                if (normalized.isEmpty()) {
                    return;
                }
                String formatted = allowSigned ? formatGroupedSigned(normalized) : formatGrouped(normalized);
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
        return normalizeInternal(raw, false);
    }

    public static String normalizeSignedAmount(String raw) {
        return normalizeInternal(raw, true);
    }

    private static String normalizeInternal(String raw, boolean allowSigned) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        boolean negative = false;
        StringBuilder digits = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
                continue;
            }
            if (allowSigned && c == '-' && !negative && digits.length() == 0) {
                negative = true;
            }
        }
        if (digits.length() == 0) {
            return negative ? "-" : "";
        }
        return negative ? "-" + digits : digits.toString();
    }

    public static String formatGrouped(String digits) {
        return formatGroupedInternal(digits, false);
    }

    public static String formatGroupedSigned(String value) {
        return formatGroupedInternal(value, true);
    }

    private static String formatGroupedInternal(String value, boolean allowSigned) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean negative = allowSigned && value.startsWith("-");
        String digits = negative ? value.substring(1) : value;
        if (digits.isEmpty()) {
            return negative ? "-" : "";
        }
        try {
            BigInteger number = new BigInteger(digits);
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            symbols.setGroupingSeparator('.');
            DecimalFormat decimalFormat = new DecimalFormat("#,###", symbols);
            decimalFormat.setGroupingUsed(true);
            String formatted = decimalFormat.format(number);
            return negative ? "-" + formatted : formatted;
        } catch (NumberFormatException ex) {
            return value;
        }
    }
}
