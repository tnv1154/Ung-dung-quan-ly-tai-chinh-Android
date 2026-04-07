package com.example.myapplication.finance.ui;

import java.util.HashMap;
import java.util.Map;

final class FinanceUiStateMemoryCache {
    private static final Map<String, FinanceUiState> CACHE = new HashMap<>();

    private FinanceUiStateMemoryCache() {
    }

    static FinanceUiState get(String userId) {
        synchronized (CACHE) {
            return CACHE.get(userId);
        }
    }

    static void put(String userId, FinanceUiState state) {
        FinanceUiState value = new FinanceUiState(
            false,
            state.getWallets(),
            state.getTransactions(),
            state.getBudgetLimits(),
            state.getCategories(),
            state.getSettings(),
            state.getMonthlySummary(),
            null
        );
        synchronized (CACHE) {
            CACHE.put(userId, value);
        }
    }
}

