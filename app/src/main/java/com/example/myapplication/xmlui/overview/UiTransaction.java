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
    private final String categoryIconKey;
    private final String walletIconKey;
    private final String walletAccountType;
    private final String destinationWalletName;
    private final String destinationWalletIconKey;
    private final String destinationWalletAccountType;

    public UiTransaction(String id, String walletName, String category, String note, String type, double amount, Timestamp createdAt) {
        this(
            id,
            walletName,
            category,
            note,
            type,
            amount,
            createdAt,
            "",
            "cash",
            "CASH",
            "",
            "cash",
            "CASH"
        );
    }

    public UiTransaction(
        String id,
        String walletName,
        String category,
        String note,
        String type,
        double amount,
        Timestamp createdAt,
        String categoryIconKey,
        String walletIconKey,
        String walletAccountType
    ) {
        this(
            id,
            walletName,
            category,
            note,
            type,
            amount,
            createdAt,
            categoryIconKey,
            walletIconKey,
            walletAccountType,
            "",
            "cash",
            "CASH"
        );
    }

    public UiTransaction(
        String id,
        String walletName,
        String category,
        String note,
        String type,
        double amount,
        Timestamp createdAt,
        String categoryIconKey,
        String walletIconKey,
        String walletAccountType,
        String destinationWalletName,
        String destinationWalletIconKey,
        String destinationWalletAccountType
    ) {
        this.id = id;
        this.walletName = walletName;
        this.category = category;
        this.note = note;
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
        this.categoryIconKey = categoryIconKey == null ? "" : categoryIconKey;
        this.walletIconKey = walletIconKey == null ? "cash" : walletIconKey;
        this.walletAccountType = walletAccountType == null ? "CASH" : walletAccountType;
        this.destinationWalletName = destinationWalletName == null ? "" : destinationWalletName;
        this.destinationWalletIconKey = destinationWalletIconKey == null ? "cash" : destinationWalletIconKey;
        this.destinationWalletAccountType = destinationWalletAccountType == null ? "CASH" : destinationWalletAccountType;
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

    public String getCategoryIconKey() {
        return categoryIconKey;
    }

    public String getWalletIconKey() {
        return walletIconKey;
    }

    public String getWalletAccountType() {
        return walletAccountType;
    }

    public String getDestinationWalletName() {
        return destinationWalletName;
    }

    public String getDestinationWalletIconKey() {
        return destinationWalletIconKey;
    }

    public String getDestinationWalletAccountType() {
        return destinationWalletAccountType;
    }
}
