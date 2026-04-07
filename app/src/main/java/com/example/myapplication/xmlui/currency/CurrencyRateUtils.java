package com.example.myapplication.xmlui.currency;

import com.example.myapplication.finance.model.ExchangeRateSnapshot;

import java.util.Locale;

public final class CurrencyRateUtils {
    private CurrencyRateUtils() {
    }

    public static String normalizeCurrency(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "VND";
        }
        return value;
    }

    public static Double convert(
        double amount,
        String fromCurrency,
        String toCurrency,
        ExchangeRateSnapshot snapshot
    ) {
        String from = normalizeCurrency(fromCurrency);
        String to = normalizeCurrency(toCurrency);
        if (from.equals(to)) {
            return amount;
        }
        if (snapshot == null) {
            return null;
        }
        Double rate = snapshot.conversionRate(from, to);
        if (rate == null || rate <= 0.0) {
            return null;
        }
        return roundAmount(amount * rate);
    }

    public static double roundAmount(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
