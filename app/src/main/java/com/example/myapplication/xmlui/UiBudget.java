package com.example.myapplication.xmlui;

public class UiBudget {
    private final String id;
    private final String category;
    private final double limitAmount;
    private final double spent;
    private final double ratio;

    public UiBudget(String id, String category, double limitAmount, double spent, double ratio) {
        this.id = id;
        this.category = category;
        this.limitAmount = limitAmount;
        this.spent = spent;
        this.ratio = ratio;
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public double getLimitAmount() {
        return limitAmount;
    }

    public double getSpent() {
        return spent;
    }

    public double getRatio() {
        return ratio;
    }
}
