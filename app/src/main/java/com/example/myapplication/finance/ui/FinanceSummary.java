package com.example.myapplication.finance.ui;

public class FinanceSummary {
    private final double totalIncome;
    private final double totalExpense;

    public FinanceSummary() {
        this(0.0, 0.0);
    }

    public FinanceSummary(double totalIncome, double totalExpense) {
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
    }

    public double getTotalIncome() {
        return totalIncome;
    }

    public double getTotalExpense() {
        return totalExpense;
    }

    public double getNetBalance() {
        return totalIncome - totalExpense;
    }
}

