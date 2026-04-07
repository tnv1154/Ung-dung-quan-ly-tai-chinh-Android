package com.example.myapplication.xmlui.currency;

import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.google.firebase.Timestamp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FrankfurterRateClient {
    private static final String FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/v2/rates?base=";
    private static final String FRANKFURTER_CURRENCIES_URL = "https://api.frankfurter.dev/v2/currencies";
    private static final String OPEN_ER_BASE_URL = "https://open.er-api.com/v6/latest/";

    private FrankfurterRateClient() {
    }

    public static ExchangeRateSnapshot fetchLatestSnapshot(String baseCurrency) throws Exception {
        String normalizedBase = normalizeCurrency(baseCurrency, ExchangeRateSnapshot.DEFAULT_BASE_CURRENCY);
        try {
            return fetchLatestSnapshotFromFrankfurter(normalizedBase);
        } catch (Exception primaryError) {
            return fetchLatestSnapshotFromOpenErApi(normalizedBase);
        }
    }

    public static List<String> fetchSupportedCurrencyCodes() throws Exception {
        try {
            return fetchSupportedCurrencyCodesFromFrankfurter();
        } catch (Exception ignored) {
            ExchangeRateSnapshot fallbackSnapshot = fetchLatestSnapshot(ExchangeRateSnapshot.DEFAULT_BASE_CURRENCY);
            List<String> fallbackCodes = new ArrayList<>(fallbackSnapshot.getRates().keySet());
            Collections.sort(fallbackCodes);
            return fallbackCodes;
        }
    }

    private static List<String> fetchSupportedCurrencyCodesFromFrankfurter() throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(FRANKFURTER_CURRENCIES_URL);
            connection = openConnection(url);

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Frankfurter currencies HTTP " + code);
            }
            String body = readFully(new BufferedInputStream(connection.getInputStream()));
            JSONArray rows = new JSONArray(body);
            Set<String> currencyCodes = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) {
                    continue;
                }
                String codeValue = normalizeCurrency(row.optString("iso_code", ""), "");
                if (!codeValue.isEmpty()) {
                    currencyCodes.add(codeValue);
                }
            }
            currencyCodes.add("VND");
            currencyCodes.add("USD");
            List<String> result = new ArrayList<>(currencyCodes);
            Collections.sort(result);
            return result;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ExchangeRateSnapshot fetchLatestSnapshotFromFrankfurter(String normalizedBase) throws Exception {
        HttpURLConnection connection = null;
        try {
            String encodedBase = URLEncoder.encode(normalizedBase, StandardCharsets.UTF_8.name());
            URL url = new URL(FRANKFURTER_BASE_URL + encodedBase);
            connection = openConnection(url);

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Frankfurter HTTP " + code);
            }
            String body = readFully(new BufferedInputStream(connection.getInputStream()));
            JSONArray rows = new JSONArray(body);
            if (rows.length() == 0) {
                throw new IOException("Frankfurter response is empty");
            }
            Map<String, Double> rates = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) {
                    continue;
                }
                String quote = normalizeCurrency(row.optString("quote", ""), "");
                double value = row.optDouble("rate", Double.NaN);
                if (quote.isEmpty() || Double.isNaN(value) || value <= 0.0) {
                    continue;
                }
                rates.put(quote, value);
            }
            if (rates.isEmpty()) {
                throw new IOException("Frankfurter rates map is empty");
            }
            return new ExchangeRateSnapshot(
                normalizedBase,
                rates,
                Timestamp.now(),
                ExchangeRateSnapshot.DEFAULT_PROVIDER
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ExchangeRateSnapshot fetchLatestSnapshotFromOpenErApi(String normalizedBase) throws Exception {
        HttpURLConnection connection = null;
        try {
            String encodedBase = URLEncoder.encode(normalizedBase, StandardCharsets.UTF_8.name());
            URL url = new URL(OPEN_ER_BASE_URL + encodedBase);
            connection = openConnection(url);

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("open.er-api HTTP " + code);
            }
            String body = readFully(new BufferedInputStream(connection.getInputStream()));
            JSONObject payload = new JSONObject(body);
            JSONObject ratesObject = payload.optJSONObject("rates");
            if (ratesObject == null || ratesObject.length() == 0) {
                throw new IOException("open.er-api rates map is empty");
            }
            Map<String, Double> rates = new HashMap<>();
            JSONArray names = ratesObject.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String quote = normalizeCurrency(names.optString(i, ""), "");
                    if (quote.isEmpty()) {
                        continue;
                    }
                    double value = ratesObject.optDouble(quote, Double.NaN);
                    if (Double.isNaN(value) || value <= 0.0) {
                        continue;
                    }
                    rates.put(quote, value);
                }
            }
            if (rates.isEmpty()) {
                throw new IOException("open.er-api parsed rates map is empty");
            }
            return new ExchangeRateSnapshot(
                normalizedBase,
                rates,
                Timestamp.now(),
                "open.er-api"
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection openConnection(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "FinanceApp/1.0");
        return connection;
    }

    private static String readFully(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, count);
            }
        }
        return builder.toString();
    }

    private static String normalizeCurrency(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        return value;
    }
}
