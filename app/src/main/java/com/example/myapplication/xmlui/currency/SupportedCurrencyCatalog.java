package com.example.myapplication.xmlui.currency;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SupportedCurrencyCatalog {

    private SupportedCurrencyCatalog() {
    }

    public static List<String> defaultCodes(Context context) {
        Set<String> logoCodes = CurrencyLogoUtils.availableLogoCodes(context);
        Set<String> codes = new LinkedHashSet<>();
        if (logoCodes.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            for (Currency currency : Currency.getAvailableCurrencies()) {
                if (currency == null || currency.getCurrencyCode() == null) {
                    continue;
                }
                String normalized = normalize(currency.getCurrencyCode());
                if (!normalized.isEmpty() && logoCodes.contains(normalized)) {
                    codes.add(normalized);
                }
            }
        } catch (Exception ignored) {
        }
        if (logoCodes.contains("USD")) {
            codes.add("USD");
        }
        if (logoCodes.contains("VND")) {
            codes.add("VND");
        }
        List<String> result = new ArrayList<>(codes);
        Collections.sort(result);
        return result;
    }

    public static List<String> withSelectedCode(Context context, String selectedCode) {
        List<String> result = defaultCodes(context);
        String selected = normalize(selectedCode);
        if (!selected.isEmpty() && CurrencyLogoUtils.hasLocalLogo(context, selected) && !result.contains(selected)) {
            result.add(selected);
            Collections.sort(result);
        }
        return result;
    }

    public static List<String> filterCodesWithLogos(Context context, Collection<String> rawCodes) {
        Set<String> logoCodes = CurrencyLogoUtils.availableLogoCodes(context);
        Set<String> filtered = new LinkedHashSet<>();
        if (rawCodes != null && !rawCodes.isEmpty() && !logoCodes.isEmpty()) {
            for (String rawCode : rawCodes) {
                String normalized = normalize(rawCode);
                if (!normalized.isEmpty() && logoCodes.contains(normalized)) {
                    filtered.add(normalized);
                }
            }
        }
        List<String> result = new ArrayList<>(filtered);
        Collections.sort(result);
        return result;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
