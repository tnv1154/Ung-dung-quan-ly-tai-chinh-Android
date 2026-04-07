package com.example.myapplication.finance.data;

import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.UserSettings;
import com.example.myapplication.finance.model.Wallet;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FirestoreFinanceMappersKt {
    private FirestoreFinanceMappersKt() {
    }

    static Map<String, Object> toFirestoreMap(Wallet wallet) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", wallet.getId());
        map.put("name", wallet.getName());
        map.put("balance", wallet.getBalance());
        map.put("accountType", wallet.getAccountType());
        map.put("iconKey", wallet.getIconKey());
        map.put("currency", wallet.getCurrency());
        map.put("note", wallet.getNote());
        map.put("includeInReport", wallet.getIncludeInReport());
        map.put("providerName", wallet.getProviderName());
        map.put("isLocked", wallet.isLocked());
        map.put("updatedAt", wallet.getUpdatedAt());
        return map;
    }

    static Map<String, Object> toFirestoreMap(FinanceTransaction transaction) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", transaction.getId());
        map.put("walletId", transaction.getWalletId());
        map.put("toWalletId", transaction.getToWalletId());
        map.put("type", transaction.getType().name());
        map.put("amount", transaction.getAmount());
        map.put("destinationAmount", transaction.getDestinationAmount());
        map.put("sourceCurrency", transaction.getSourceCurrency());
        map.put("destinationCurrency", transaction.getDestinationCurrency());
        map.put("exchangeRate", transaction.getExchangeRate());
        map.put("exchangeRateFetchedAt", transaction.getExchangeRateFetchedAt());
        map.put("category", transaction.getCategory());
        map.put("note", transaction.getNote());
        map.put("createdAt", transaction.getCreatedAt());
        return map;
    }

    static Map<String, Object> toFirestoreMap(BudgetLimit budgetLimit) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", budgetLimit.getId());
        map.put("name", budgetLimit.getName());
        map.put("category", budgetLimit.getCategory());
        map.put("limitAmount", budgetLimit.getLimitAmount());
        map.put("repeatCycle", budgetLimit.getRepeatCycle());
        map.put("startDateEpochDay", budgetLimit.getStartDateEpochDay());
        map.put("endDateEpochDay", budgetLimit.getEndDateEpochDay());
        map.put("updatedAt", budgetLimit.getUpdatedAt());
        return map;
    }

    static Map<String, Object> toFirestoreMap(TransactionCategory category) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getName());
        map.put("type", category.getType().name());
        map.put("parentName", category.getParentName());
        map.put("iconKey", category.getIconKey());
        map.put("sortOrder", category.getSortOrder());
        map.put("updatedAt", category.getUpdatedAt());
        return map;
    }

    static Map<String, Object> toFirestoreMap(ExchangeRateSnapshot snapshot) {
        Map<String, Object> map = new HashMap<>();
        map.put("baseCurrency", snapshot.getBaseCurrency());
        map.put("rates", new HashMap<>(snapshot.getRates()));
        map.put("fetchedAt", snapshot.getFetchedAt());
        map.put("provider", snapshot.getProvider());
        map.put("updatedAt", Timestamp.now());
        return map;
    }

    static Wallet toWalletModel(DocumentSnapshot snapshot) {
        String id = safe(snapshot.getString("id"));
        if (id.isBlank()) {
            id = snapshot.getId();
        }
        Double balance = snapshot.getDouble("balance");
        Boolean includeInReport = snapshot.getBoolean("includeInReport");
        Boolean isLocked = snapshot.getBoolean("isLocked");
        Timestamp updatedAt = snapshot.getTimestamp("updatedAt");
        double safeBalance = balance == null ? 0.0 : Math.max(balance, 0.0);
        return new Wallet(
            id,
            safe(snapshot.getString("name")),
            safeBalance,
            defaultString(snapshot.getString("accountType"), "CASH"),
            defaultString(snapshot.getString("iconKey"), "cash"),
            defaultString(snapshot.getString("currency"), "VND"),
            safe(snapshot.getString("note")),
            includeInReport == null || includeInReport,
            safe(snapshot.getString("providerName")),
            isLocked != null && isLocked,
            updatedAt == null ? Timestamp.now() : updatedAt
        );
    }

    static FinanceTransaction toFinanceTransactionModel(DocumentSnapshot snapshot) {
        String id = safe(snapshot.getString("id"));
        if (id.isBlank()) {
            id = snapshot.getId();
        }
        Double amount = snapshot.getDouble("amount");
        Double destinationAmount = snapshot.getDouble("destinationAmount");
        Double exchangeRate = snapshot.getDouble("exchangeRate");
        String sourceCurrency = safe(snapshot.getString("sourceCurrency"));
        String destinationCurrency = safe(snapshot.getString("destinationCurrency"));
        Timestamp exchangeRateFetchedAt = snapshot.getTimestamp("exchangeRateFetchedAt");
        Timestamp createdAt = snapshot.getTimestamp("createdAt");
        return new FinanceTransaction(
            id,
            safe(snapshot.getString("walletId")),
            snapshot.getString("toWalletId"),
            parseTransactionType(snapshot.getString("type")),
            amount == null ? 0.0 : amount,
            destinationAmount == null
                ? (amount == null ? 0.0 : amount)
                : destinationAmount,
            sourceCurrency,
            destinationCurrency,
            exchangeRate,
            exchangeRateFetchedAt,
            safe(snapshot.getString("category")),
            safe(snapshot.getString("note")),
            createdAt == null ? Timestamp.now() : createdAt
        );
    }

    static BudgetLimit toBudgetLimitModel(DocumentSnapshot snapshot) {
        String id = safe(snapshot.getString("id"));
        if (id.isBlank()) {
            id = snapshot.getId();
        }
        Double limitAmount = snapshot.getDouble("limitAmount");
        String category = safe(snapshot.getString("category"));
        String name = safe(snapshot.getString("name"));
        String repeatCycle = defaultString(snapshot.getString("repeatCycle"), BudgetLimit.REPEAT_NONE);
        long defaultStart = LocalDate.now().withDayOfMonth(1).toEpochDay();
        long defaultEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toEpochDay();
        Long startDateEpochDay = snapshot.getLong("startDateEpochDay");
        Long endDateEpochDay = snapshot.getLong("endDateEpochDay");
        Timestamp updatedAt = snapshot.getTimestamp("updatedAt");
        if (name.isBlank()) {
            name = category;
        }
        return new BudgetLimit(
            id,
            name,
            category,
            limitAmount == null ? 0.0 : limitAmount,
            repeatCycle,
            startDateEpochDay == null ? defaultStart : startDateEpochDay,
            endDateEpochDay == null ? defaultEnd : endDateEpochDay,
            updatedAt == null ? Timestamp.now() : updatedAt
        );
    }

    static TransactionCategory toTransactionCategoryModel(DocumentSnapshot snapshot) {
        String id = safe(snapshot.getString("id"));
        if (id.isBlank()) {
            id = snapshot.getId();
        }
        Long sortOrder = snapshot.getLong("sortOrder");
        Timestamp updatedAt = snapshot.getTimestamp("updatedAt");
        return new TransactionCategory(
            id,
            safe(snapshot.getString("name")),
            parseTransactionType(snapshot.getString("type")),
            safe(snapshot.getString("parentName")),
            defaultString(snapshot.getString("iconKey"), "dot"),
            sortOrder == null ? 0 : sortOrder.intValue(),
            updatedAt == null ? Timestamp.now() : updatedAt
        );
    }

    static UserSettings toUserSettingsModel(DocumentSnapshot snapshot) {
        Object raw = snapshot.get("settings");
        if (!(raw instanceof Map)) {
            return new UserSettings();
        }
        Map<?, ?> settings = (Map<?, ?>) raw;
        String currency = settings.get("currency") instanceof String ? (String) settings.get("currency") : "VND";
        boolean showBudgetWarnings = settings.get("showBudgetWarnings") instanceof Boolean
            ? (Boolean) settings.get("showBudgetWarnings")
            : true;
        boolean compactNumberFormat = settings.get("compactNumberFormat") instanceof Boolean
            ? (Boolean) settings.get("compactNumberFormat")
            : false;
        boolean reminderEnabled = settings.get("reminderEnabled") instanceof Boolean
            ? (Boolean) settings.get("reminderEnabled")
            : false;
        String reminderFrequency = settings.get("reminderFrequency") instanceof String
            ? (String) settings.get("reminderFrequency")
            : UserSettings.REMINDER_FREQUENCY_DAILY;
        int reminderHour = settings.get("reminderHour") instanceof Number
            ? ((Number) settings.get("reminderHour")).intValue()
            : 20;
        int reminderMinute = settings.get("reminderMinute") instanceof Number
            ? ((Number) settings.get("reminderMinute")).intValue()
            : 0;
        List<Integer> reminderWeekdays = toReminderWeekdays(settings.get("reminderWeekdays"));
        Timestamp updatedAt = settings.get("updatedAt") instanceof Timestamp
            ? (Timestamp) settings.get("updatedAt")
            : Timestamp.now();
        return new UserSettings(
            currency,
            showBudgetWarnings,
            compactNumberFormat,
            reminderEnabled,
            reminderFrequency,
            reminderHour,
            reminderMinute,
            reminderWeekdays,
            updatedAt
        );
    }

    private static List<Integer> toReminderWeekdays(Object value) {
        if (!(value instanceof Iterable)) {
            return new ArrayList<>();
        }
        List<Integer> days = new ArrayList<>();
        for (Object item : (Iterable<?>) value) {
            if (item instanceof Number) {
                days.add(((Number) item).intValue());
            }
        }
        return days;
    }

    static ExchangeRateSnapshot toExchangeRateSnapshotModel(DocumentSnapshot snapshot) {
        String baseCurrency = defaultString(
            snapshot.getString("baseCurrency"),
            ExchangeRateSnapshot.DEFAULT_BASE_CURRENCY
        );
        Timestamp fetchedAt = snapshot.getTimestamp("fetchedAt");
        String provider = defaultString(
            snapshot.getString("provider"),
            ExchangeRateSnapshot.DEFAULT_PROVIDER
        );
        Map<String, Double> rates = toRateMap(snapshot.get("rates"));
        return new ExchangeRateSnapshot(
            baseCurrency,
            rates,
            fetchedAt == null ? Timestamp.now() : fetchedAt,
            provider
        );
    }

    private static Map<String, Double> toRateMap(Object value) {
        Map<String, Double> rates = new HashMap<>();
        if (!(value instanceof Map<?, ?>)) {
            return rates;
        }
        Map<?, ?> rawMap = (Map<?, ?>) value;
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            Object key = entry.getKey();
            Object rawRate = entry.getValue();
            if (!(key instanceof String) || !(rawRate instanceof Number)) {
                continue;
            }
            rates.put((String) key, ((Number) rawRate).doubleValue());
        }
        return rates;
    }

    private static TransactionType parseTransactionType(String raw) {
        if (raw == null || raw.isBlank()) {
            return TransactionType.EXPENSE;
        }
        try {
            return TransactionType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return TransactionType.EXPENSE;
        }
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }
}

