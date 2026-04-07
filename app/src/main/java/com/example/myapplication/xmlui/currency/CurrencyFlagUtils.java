package com.example.myapplication.xmlui.currency;

import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class CurrencyFlagUtils {
    private static final String FLAG_CDN_TEMPLATE = "https://flagcdn.com/w80/%s.png";
    private static final Map<String, String> CURRENCY_TO_COUNTRY = buildCurrencyCountryMap();

    private CurrencyFlagUtils() {
    }

    public static String countryCodeForCurrency(String currencyCode) {
        String code = normalizeCurrency(currencyCode);
        if (code.isEmpty()) {
            return "";
        }
        String country = CURRENCY_TO_COUNTRY.get(code);
        return country == null ? "" : country;
    }

    public static String flagCdnUrl(String currencyCode) {
        String country = countryCodeForCurrency(currencyCode);
        if (country.isEmpty()) {
            return null;
        }
        return String.format(Locale.ROOT, FLAG_CDN_TEMPLATE, country);
    }

    public static String flagEmojiForCurrency(String currencyCode) {
        String country = countryCodeForCurrency(currencyCode);
        if (country.length() != 2) {
            return "\uD83D\uDCB1";
        }
        String upper = country.toUpperCase(Locale.ROOT);
        char first = upper.charAt(0);
        char second = upper.charAt(1);
        if (first < 'A' || first > 'Z' || second < 'A' || second > 'Z') {
            return "\uD83D\uDCB1";
        }
        int firstCodePoint = 0x1F1E6 + (first - 'A');
        int secondCodePoint = 0x1F1E6 + (second - 'A');
        return new String(Character.toChars(firstCodePoint)) + new String(Character.toChars(secondCodePoint));
    }

    private static Map<String, String> buildCurrencyCountryMap() {
        Map<String, String> map = new HashMap<>();
        for (Locale locale : Locale.getAvailableLocales()) {
            String country = locale.getCountry();
            if (country == null || country.isBlank()) {
                continue;
            }
            try {
                Currency currency = Currency.getInstance(locale);
                if (currency == null) {
                    continue;
                }
                String code = normalizeCurrency(currency.getCurrencyCode());
                if (!code.isEmpty()) {
                    map.putIfAbsent(code, country.toLowerCase(Locale.ROOT));
                }
            } catch (Exception ignored) {
            }
        }

        // Widely used/global currencies and special cases.
        map.put("USD", "us");
        map.put("EUR", "eu");
        map.put("GBP", "gb");
        map.put("JPY", "jp");
        map.put("VND", "vn");
        map.put("AUD", "au");
        map.put("CAD", "ca");
        map.put("CHF", "ch");
        map.put("CNY", "cn");
        map.put("HKD", "hk");
        map.put("INR", "in");
        map.put("KRW", "kr");
        map.put("SGD", "sg");
        map.put("THB", "th");
        map.put("TWD", "tw");
        map.put("IDR", "id");
        map.put("MYR", "my");
        map.put("PHP", "ph");
        map.put("NZD", "nz");
        map.put("ZAR", "za");
        map.put("BRL", "br");
        map.put("MXN", "mx");
        map.put("ARS", "ar");
        map.put("CLP", "cl");
        map.put("COP", "co");
        map.put("PEN", "pe");
        map.put("SAR", "sa");
        map.put("AED", "ae");
        map.put("TRY", "tr");
        map.put("RUB", "ru");
        map.put("PLN", "pl");
        map.put("CZK", "cz");
        map.put("HUF", "hu");
        map.put("RON", "ro");
        map.put("SEK", "se");
        map.put("NOK", "no");
        map.put("DKK", "dk");
        map.put("ILS", "il");
        map.put("EGP", "eg");
        map.put("PKR", "pk");
        map.put("BDT", "bd");
        map.put("NPR", "np");
        map.put("LKR", "lk");
        map.put("XAF", "cm");
        map.put("XOF", "sn");
        map.put("XPF", "pf");
        map.put("ANG", "cw");
        map.put("BAM", "ba");

        return Collections.unmodifiableMap(map);
    }

    private static String normalizeCurrency(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
