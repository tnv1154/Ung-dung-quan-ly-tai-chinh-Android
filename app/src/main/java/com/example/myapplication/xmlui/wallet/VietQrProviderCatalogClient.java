package com.example.myapplication.xmlui;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VietQrProviderCatalogClient {
    private static final String VIETQR_BANKS_URL = "https://api.vietqr.io/v2/banks";

    private VietQrProviderCatalogClient() {
    }

    public static ProviderCatalog fetchCatalog(Context context) throws Exception {
        if (context == null) {
            return localFallbackCatalog();
        }
        Map<String, WalletProviderOption> banks = new LinkedHashMap<>();
        Map<String, WalletProviderOption> ewallets = new LinkedHashMap<>();

        HttpURLConnection connection = null;
        try {
            URL url = new URL(VIETQR_BANKS_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "FinanceApp/1.0");

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("VietQR banks HTTP " + code);
            }

            String body = readFully(new BufferedInputStream(connection.getInputStream()));
            JSONObject payload = new JSONObject(body);
            JSONArray rows = payload.optJSONArray("data");
            if (rows != null) {
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) {
                        continue;
                    }
                    String shortName = firstNonEmpty(
                        row.optString("shortName", ""),
                        row.optString("short_name", "")
                    );
                    if (shortName.isEmpty()) {
                        continue;
                    }
                    String displayName = firstNonEmpty(row.optString("name", ""), shortName);

                    String bankShortName = WalletProviderLogoUtils.resolveShortName(
                        context,
                        WalletProviderLogoUtils.ACCOUNT_TYPE_BANK,
                        shortName
                    );
                    if (!bankShortName.isEmpty()) {
                        putIfAbsent(banks, bankShortName, displayName);
                        continue;
                    }

                    String ewalletShortName = WalletProviderLogoUtils.resolveShortName(
                        context,
                        WalletProviderLogoUtils.ACCOUNT_TYPE_EWALLET,
                        shortName
                    );
                    if (!ewalletShortName.isEmpty()) {
                        putIfAbsent(ewallets, ewalletShortName, displayName);
                    }
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        appendLocalFallback(context, WalletProviderLogoUtils.ACCOUNT_TYPE_BANK, banks);
        appendLocalFallback(context, WalletProviderLogoUtils.ACCOUNT_TYPE_EWALLET, ewallets);

        return new ProviderCatalog(toSortedList(banks), toSortedList(ewallets));
    }

    public static ProviderCatalog localFallbackCatalog(Context context) {
        if (context == null) {
            return localFallbackCatalog();
        }
        Map<String, WalletProviderOption> banks = new LinkedHashMap<>();
        Map<String, WalletProviderOption> ewallets = new LinkedHashMap<>();
        appendLocalFallback(context, WalletProviderLogoUtils.ACCOUNT_TYPE_BANK, banks);
        appendLocalFallback(context, WalletProviderLogoUtils.ACCOUNT_TYPE_EWALLET, ewallets);
        return new ProviderCatalog(toSortedList(banks), toSortedList(ewallets));
    }

    private static ProviderCatalog localFallbackCatalog() {
        return new ProviderCatalog(Collections.emptyList(), Collections.emptyList());
    }

    private static void appendLocalFallback(
        Context context,
        String accountType,
        Map<String, WalletProviderOption> target
    ) {
        for (String shortName : WalletProviderLogoUtils.localShortNames(context, accountType)) {
            putIfAbsent(target, shortName, shortName);
        }
    }

    private static void putIfAbsent(
        Map<String, WalletProviderOption> target,
        String shortName,
        String displayName
    ) {
        String normalized = WalletProviderLogoUtils.normalizeShortName(shortName);
        if (normalized.isEmpty() || target.containsKey(normalized)) {
            return;
        }
        String safeShortName = firstNonEmpty(shortName, "");
        if (safeShortName.isEmpty()) {
            return;
        }
        target.put(normalized, new WalletProviderOption(safeShortName, firstNonEmpty(displayName, safeShortName)));
    }

    private static List<WalletProviderOption> toSortedList(Map<String, WalletProviderOption> source) {
        List<WalletProviderOption> options = new ArrayList<>(source.values());
        Collections.sort(options, (left, right) -> left.getShortName().compareToIgnoreCase(right.getShortName()));
        return options;
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

    private static String firstNonEmpty(String primary, String fallback) {
        String value = primary == null ? "" : primary.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return fallback == null ? "" : fallback.trim();
    }

    public static final class ProviderCatalog {
        private final List<WalletProviderOption> banks;
        private final List<WalletProviderOption> ewallets;

        public ProviderCatalog(List<WalletProviderOption> banks, List<WalletProviderOption> ewallets) {
            this.banks = banks == null ? Collections.emptyList() : new ArrayList<>(banks);
            this.ewallets = ewallets == null ? Collections.emptyList() : new ArrayList<>(ewallets);
        }

        public List<WalletProviderOption> getBanks() {
            return new ArrayList<>(banks);
        }

        public List<WalletProviderOption> getEwallets() {
            return new ArrayList<>(ewallets);
        }
    }
}
