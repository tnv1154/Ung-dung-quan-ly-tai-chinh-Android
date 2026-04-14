package com.example.myapplication.xmlui;

public class UiReportCategory {
    private final String name;
    private final double amount;
    private final double ratio;
    private final int iconRes;
    private final int iconBgColor;
    private final int iconTintColor;
    private final int chartColor;

    public UiReportCategory(
        String name,
        double amount,
        double ratio,
        int iconRes,
        int iconBgColor,
        int iconTintColor,
        int chartColor
    ) {
        this.name = name;
        this.amount = amount;
        this.ratio = ratio;
        this.iconRes = iconRes;
        this.iconBgColor = iconBgColor;
        this.iconTintColor = iconTintColor;
        this.chartColor = chartColor;
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

    public int getIconRes() {
        return iconRes;
    }

    public int getIconBgColor() {
        return iconBgColor;
    }

    public int getIconTintColor() {
        return iconTintColor;
    }

    public int getChartColor() {
        return chartColor;
    }
}
