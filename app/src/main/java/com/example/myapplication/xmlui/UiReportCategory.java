package com.example.myapplication.xmlui;

public class UiReportCategory {
    private final String name;
    private final double amount;
    private final double ratio;

    public UiReportCategory(String name, double amount, double ratio) {
        this.name = name;
        this.amount = amount;
        this.ratio = ratio;
    }

    public String getName() {
        return name;
    }

    public double getAmount() {
        return amount;
    }

    public double getRatio() {
        return ratio;
    }
}
