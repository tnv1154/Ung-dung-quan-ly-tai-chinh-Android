package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

public class BudgetLimit {
    private final String id;
    private final String category;
    private final double limitAmount;
    private final Timestamp updatedAt;

    public BudgetLimit() {
        this("", "", 0.0, Timestamp.now());
    }

    public BudgetLimit(String id, String category, double limitAmount, Timestamp updatedAt) {
        this.id = id == null ? "" : id;
        this.category = category == null ? "" : category;
        this.limitAmount = limitAmount;
        this.updatedAt = updatedAt == null ? Timestamp.now() : updatedAt;
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

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
}

