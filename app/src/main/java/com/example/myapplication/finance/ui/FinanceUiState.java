package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.UserSettings;
import com.example.myapplication.finance.model.Wallet;

import java.util.Collections;
import java.util.List;

public class FinanceUiState {
    private final boolean isLoading;
    private final List<Wallet> wallets;
    private final List<FinanceTransaction> transactions;
    private final List<BudgetLimit> budgetLimits;
    private final List<TransactionCategory> categories;
    private final UserSettings settings;
    private final FinanceSummary monthlySummary;
    private final String errorMessage;

    public FinanceUiState() {
        this(
            true,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            new UserSettings(),
            new FinanceSummary(),
            null
        );
    }

    public FinanceUiState(
        boolean isLoading,
        List<Wallet> wallets,
        List<FinanceTransaction> transactions,
        List<BudgetLimit> budgetLimits,
        List<TransactionCategory> categories,
        UserSettings settings,
        FinanceSummary monthlySummary,
        String errorMessage
    ) {
        this.isLoading = isLoading;
        this.wallets = wallets == null ? Collections.emptyList() : wallets;
        this.transactions = transactions == null ? Collections.emptyList() : transactions;
        this.budgetLimits = budgetLimits == null ? Collections.emptyList() : budgetLimits;
        this.categories = categories == null ? Collections.emptyList() : categories;
        this.settings = settings == null ? new UserSettings() : settings;
        this.monthlySummary = monthlySummary == null ? new FinanceSummary() : monthlySummary;
        this.errorMessage = errorMessage;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public List<Wallet> getWallets() {
        return wallets;
    }

    public List<FinanceTransaction> getTransactions() {
        return transactions;
    }

    public List<BudgetLimit> getBudgetLimits() {
        return budgetLimits;
    }

    public List<TransactionCategory> getCategories() {
        return categories;
    }

    public UserSettings getSettings() {
        return settings;
    }

    public FinanceSummary getMonthlySummary() {
        return monthlySummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

