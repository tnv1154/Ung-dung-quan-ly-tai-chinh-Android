package com.example.myapplication.xmlui.currency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SupportedCurrencyCatalog {

    private SupportedCurrencyCatalog() {
    }

    public static List<String> defaultCodes() {
        Set<String> codes = new LinkedHashSet<>();
        try {
            for (Currency currency : Currency.getAvailableCurrencies()) {
                if (currency == null || currency.getCurrencyCode() == null) {
                    continue;
                }
                String normalized = normalize(currency.getCurrencyCode());
                if (!normalized.isEmpty()) {
                    codes.add(normalized);
                }
            }
        } catch (Exception ignored) {
        }
        codes.add("USD");
        codes.add("VND");
        List<String> result = new ArrayList<>(codes);
        Collections.sort(result);
        return result;
    }

    public static List<String> withSelectedCode(String selectedCode) {
        List<String> result = defaultCodes();
        String selected = normalize(selectedCode);
        if (!selected.isEmpty() && !result.contains(selected)) {
            result.add(selected);
            Collections.sort(result);
        }
        return result;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
