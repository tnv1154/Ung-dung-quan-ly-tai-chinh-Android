package com.example.myapplication.xmlui;

public class UiReportBudgetUsage {
    private final String name;
    private final double spent;
    private final double limit;
    private final double ratio;
    private final int iconRes;
    private final int iconBgColor;
    private final int iconTintColor;

    public UiReportBudgetUsage(
        String name,
        double spent,
        double limit,
        double ratio,
        int iconRes,
        int iconBgColor,
        int iconTintColor
    ) {
        this.name = name;
        this.spent = spent;
        this.limit = limit;
        this.ratio = ratio;
        this.iconRes = iconRes;
        this.iconBgColor = iconBgColor;
        this.iconTintColor = iconTintColor;
    }

    public String getName() {
        return name;
    }

    public double getSpent() {
        return spent;
    }

    public double getLimit() {
        return limit;
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
}
