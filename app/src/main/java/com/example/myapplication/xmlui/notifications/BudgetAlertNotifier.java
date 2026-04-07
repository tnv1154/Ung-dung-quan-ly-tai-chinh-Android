package com.example.myapplication.xmlui.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.ui.FinanceUiState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BudgetAlertNotifier {
    private static final String PREFS_NAME = "finance_notifications";
    private static final String KEY_MONTH = "budget_alert_month";
    private static final String KEY_CATEGORIES = "budget_alert_categories";

    private BudgetAlertNotifier() {
    }

    public static void maybeNotifyExceeded(Context context, FinanceUiState state) {
        if (context == null || state == null || !state.getSettings().getShowBudgetWarnings()) {
            return;
        }
        String monthKey = monthKeyNow();
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedMonth = preferences.getString(KEY_MONTH, "");
        List<String> notifiedCategories = monthKey.equals(savedMonth)
            ? parseCsv(preferences.getString(KEY_CATEGORIES, ""))
            : new ArrayList<>();

        Map<String, Double> expenseByCategory = currentMonthExpenseByCategory(state.getTransactions());
        boolean updated = false;
        for (BudgetLimit budget : state.getBudgetLimits()) {
            if (budget.getLimitAmount() <= 0.0) {
                continue;
            }
            String normalizedCategory = normalizeCategoryKey(budget.getCategory());
            if (normalizedCategory.isEmpty()) {
                continue;
            }
            double spent = expenseByCategory.getOrDefault(normalizedCategory, 0.0);
            if (spent >= budget.getLimitAmount() && !notifiedCategories.contains(normalizedCategory)) {
                AppNotificationCenter.notifyBudgetExceeded(
                    context.getApplicationContext(),
                    budget.getCategory(),
                    spent,
                    budget.getLimitAmount()
                );
                notifiedCategories.add(normalizedCategory);
                updated = true;
            }
        }

        if (!monthKey.equals(savedMonth) || updated) {
            preferences.edit()
                .putString(KEY_MONTH, monthKey)
                .putString(KEY_CATEGORIES, String.join(",", notifiedCategories))
                .apply();
        }
    }

    private static Map<String, Double> currentMonthExpenseByCategory(List<FinanceTransaction> transactions) {
        Map<String, Double> expenseByCategory = new HashMap<>();
        ZonedDateTime now = ZonedDateTime.now();
        for (FinanceTransaction tx : transactions) {
            if (tx.getType() != TransactionType.EXPENSE) {
                continue;
            }
            ZonedDateTime txTime = Instant.ofEpochSecond(
                tx.getCreatedAt().getSeconds(),
                tx.getCreatedAt().getNanoseconds()
            ).atZone(ZoneId.systemDefault());
            if (txTime.getYear() != now.getYear() || txTime.getMonthValue() != now.getMonthValue()) {
                continue;
            }
            String key = normalizeCategoryKey(tx.getCategory());
            if (key.isEmpty()) {
                continue;
            }
            expenseByCategory.put(key, expenseByCategory.getOrDefault(key, 0.0) + tx.getAmount());
        }
        return expenseByCategory;
    }

    private static String monthKeyNow() {
        ZonedDateTime now = ZonedDateTime.now();
        return now.getYear() + "-" + now.getMonthValue();
    }

    private static String normalizeCategoryKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> parseCsv(String raw) {
        List<String> values = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return values;
        }
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            String value = token.trim();
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }
}

