package com.example.myapplication.xmlui.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.R;
import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.xmlui.CategoryFallbackMerger;
import com.example.myapplication.xmlui.budget.BudgetCycleUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BudgetAlertNotifier {
    private static final String PREFS_NAME = "finance_notifications";
    private static final String KEY_EXCEEDED = "budget_exceeded_keys";
    private static final String KEY_WARNING = "budget_warning_keys";
    private static final double WARNING_THRESHOLD = 0.8;

    private BudgetAlertNotifier() {
    }

    public static void maybeNotifyExceeded(Context context, FinanceUiState state) {
        if (context == null || state == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        MonthlyReportNotifier.maybeNotifyMonthlyReport(appContext);
        if (!state.getSettings().getShowBudgetWarnings()) {
            return;
        }
        SharedPreferences preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> notifiedKeys = parseCsv(preferences.getString(KEY_EXCEEDED, ""));
        boolean updated = false;
        for (BudgetUsage usage : collectActiveBudgetUsage(appContext, state)) {
            if (usage.ratio < 1.0) {
                continue;
            }
            String key = usage.key;
            if (notifiedKeys.contains(key)) {
                continue;
            }
            AppNotificationCenter.notifyBudgetExceeded(
                appContext,
                usage.displayName,
                usage.spent,
                usage.limit
            );
            notifiedKeys.add(key);
            updated = true;
        }
        if (updated) {
            preferences.edit().putString(KEY_EXCEEDED, String.join(",", notifiedKeys)).apply();
        }
        maybeNotifyNearLimitInternal(appContext, state, preferences);
    }

    public static void maybeNotifyNearLimit(Context context, FinanceUiState state) {
        if (context == null || state == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        MonthlyReportNotifier.maybeNotifyMonthlyReport(appContext);
        if (!state.getSettings().getShowBudgetWarnings()) {
            return;
        }
        SharedPreferences preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        maybeNotifyNearLimitInternal(appContext, state, preferences);
    }

    private static void maybeNotifyNearLimitInternal(
        Context context,
        FinanceUiState state,
        SharedPreferences preferences
    ) {
        Set<String> notifiedKeys = parseCsv(preferences.getString(KEY_WARNING, ""));
        boolean updated = false;
        for (BudgetUsage usage : collectActiveBudgetUsage(context, state)) {
            if (usage.ratio < WARNING_THRESHOLD || usage.ratio >= 1.0) {
                continue;
            }
            String key = usage.key;
            if (notifiedKeys.contains(key)) {
                continue;
            }
            int percent = (int) Math.round(usage.ratio * 100.0);
            AppNotificationCenter.notifyBudgetWarning(
                context.getApplicationContext(),
                usage.displayName,
                percent,
                usage.spent,
                usage.limit,
                key
            );
            notifiedKeys.add(key);
            updated = true;
        }
        if (updated) {
            preferences.edit().putString(KEY_WARNING, String.join(",", notifiedKeys)).apply();
        }
    }

    public static void clearLocalState(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_EXCEEDED)
            .remove(KEY_WARNING)
            .apply();
    }

    private static List<BudgetUsage> collectActiveBudgetUsage(Context context, FinanceUiState state) {
        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();
        List<BudgetUsage> usages = new ArrayList<>();
        for (BudgetLimit budget : state.getBudgetLimits()) {
            if (budget.getLimitAmount() <= 0.0) {
                continue;
            }
            BudgetCycleUtils.BudgetWindow window = BudgetCycleUtils.resolveWindow(budget, today);
            if (!window.isActive()) {
                continue;
            }
            double spent = BudgetCycleUtils.calculateSpentInWindow(
                budget,
                state.getTransactions(),
                CategoryFallbackMerger.mergeWithFallbacks(state.getCategories()),
                zoneId,
                window.getStart(),
                window.getEnd()
            );
            double ratio = spent / budget.getLimitAmount();
            usages.add(new BudgetUsage(
                buildUsageKey(budget, window),
                resolveDisplayName(context, budget),
                budget.getLimitAmount(),
                spent,
                ratio
            ));
        }
        return usages;
    }

    private static String buildUsageKey(BudgetLimit budget, BudgetCycleUtils.BudgetWindow window) {
        String budgetId = budget.getId() == null ? "budget" : budget.getId();
        return budgetId + ":" + window.getStart().toEpochDay() + ":" + window.getEnd().toEpochDay();
    }

    private static String resolveDisplayName(Context context, BudgetLimit budget) {
        String name = budget.getName() == null ? "" : budget.getName().trim();
        if (!name.isEmpty()) {
            return name;
        }
        if (BudgetCycleUtils.isAllCategory(budget)) {
            return context.getString(R.string.budget_category_all);
        }
        String category = budget.getCategory() == null ? "" : budget.getCategory().trim();
        return category.isEmpty() ? context.getString(R.string.overview_budget_title_default) : category;
    }

    private static Set<String> parseCsv(String raw) {
        Set<String> values = new HashSet<>();
        if (raw == null || raw.trim().isEmpty()) {
            return values;
        }
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            String value = token.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static final class BudgetUsage {
        private final String key;
        private final String displayName;
        private final double limit;
        private final double spent;
        private final double ratio;

        private BudgetUsage(String key, String displayName, double limit, double spent, double ratio) {
            this.key = key;
            this.displayName = displayName;
            this.limit = limit;
            this.spent = spent;
            this.ratio = ratio;
        }
    }
}
