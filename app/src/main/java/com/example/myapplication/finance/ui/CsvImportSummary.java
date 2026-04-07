package com.example.myapplication.finance.ui;

public class CsvImportSummary {
    private final int successCount;
    private final int skippedCount;
    private final double totalIncome;
    private final double totalExpense;
    private final String errorMessage;

    public CsvImportSummary(
        int successCount,
        int skippedCount,
        double totalIncome,
        double totalExpense,
        String errorMessage
    ) {
        this.successCount = Math.max(successCount, 0);
        this.skippedCount = Math.max(skippedCount, 0);
        this.totalIncome = Math.max(totalIncome, 0.0);
        this.totalExpense = Math.max(totalExpense, 0.0);
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public double getTotalIncome() {
        return totalIncome;
    }

    public double getTotalExpense() {
        return totalExpense;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasError() {
        return !errorMessage.trim().isEmpty();
    }
}
