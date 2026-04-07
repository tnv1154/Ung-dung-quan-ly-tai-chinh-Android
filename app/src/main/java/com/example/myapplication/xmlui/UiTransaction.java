package com.example.myapplication.xmlui;

import com.google.firebase.Timestamp;

public class UiTransaction {
    private final String id;
    private final String walletName;
    private final String category;
    private final String note;
    private final String type;
    private final double amount;
    private final Timestamp createdAt;

    public UiTransaction(String id, String walletName, String category, String note, String type, double amount, Timestamp createdAt) {
        this.id = id;
        this.walletName = walletName;
        this.category = category;
        this.note = note;
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getWalletName() {
        return walletName;
    }

    public String getCategory() {
        return category;
    }

    public String getNote() {
        return note;
    }

    public String getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }
}
