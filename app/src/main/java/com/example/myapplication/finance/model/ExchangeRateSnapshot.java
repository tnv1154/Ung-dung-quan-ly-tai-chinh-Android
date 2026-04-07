package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ExchangeRateSnapshot {
    public static final String DEFAULT_BASE_CURRENCY = "USD";
    public static final String DEFAULT_PROVIDER = "frankfurter";

    private final String baseCurrency;
    private final Map<String, Double> rates;
    private final Timestamp fetchedAt;
    private final String provider;

    public ExchangeRateSnapshot() {
        this(DEFAULT_BASE_CURRENCY, new HashMap<>(), Timestamp.now(), DEFAULT_PROVIDER);
    }

    public ExchangeRateSnapshot(
        String baseCurrency,
        Map<String, Double> rates,
        Timestamp fetchedAt,
        String provider
    ) {
        this.baseCurrency = normalizeCurrency(baseCurrency, DEFAULT_BASE_CURRENCY);
        Map<String, Double> copy = new HashMap<>();
        if (rates != null) {
            for (Map.Entry<String, Double> entry : rates.entrySet()) {
                String quote = normalizeCurrency(entry.getKey(), null);
                Double value = entry.getValue();
                if (quote == null || value == null || value <= 0.0) {
                    continue;
                }
                copy.put(quote, value);
            }
        }
        copy.put(this.baseCurrency, 1.0d);
        this.rates = Collections.unmodifiableMap(copy);
        this.fetchedAt = fetchedAt == null ? Timestamp.now() : fetchedAt;
        this.provider = (provider == null || provider.isBlank()) ? DEFAULT_PROVIDER : provider.trim();
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public Map<String, Double> getRates() {
        return rates;
    }

    public Timestamp getFetchedAt() {
        return fetchedAt;
    }

    public String getProvider() {
        return provider;
    }

    public Double getRate(String quoteCurrency) {
        String quote = normalizeCurrency(quoteCurrency, null);
        if (quote == null) {
            return null;
        }
        return rates.get(quote);
    }

    public Double conversionRate(String fromCurrency, String toCurrency) {
        String from = normalizeCurrency(fromCurrency, null);
        String to = normalizeCurrency(toCurrency, null);
        if (from == null || to == null) {
            return null;
        }
        if (from.equals(to)) {
            return 1.0d;
        }
        Double fromRate = rates.get(from);
        Double toRate = rates.get(to);
        if (fromRate == null || toRate == null || fromRate <= 0.0 || toRate <= 0.0) {
            return null;
        }
        return toRate / fromRate;
    }

    private static String normalizeCurrency(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return fallback;
        }
        return value;
    }
}
