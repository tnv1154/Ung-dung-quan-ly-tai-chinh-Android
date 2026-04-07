package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

public class FinanceTransaction {
    private final String id;
    private final String walletId;
    private final String toWalletId;
    private final TransactionType type;
    private final double amount;
    private final String category;
    private final String note;
    private final Timestamp createdAt;

    public FinanceTransaction() {
        this("", "", null, TransactionType.EXPENSE, 0.0, "", "", Timestamp.now());
    }

    public FinanceTransaction(
        String id,
        String walletId,
        String toWalletId,
        TransactionType type,
        double amount,
        String category,
        String note,
        Timestamp createdAt
    ) {
        this.id = safe(id);
        this.walletId = safe(walletId);
        this.toWalletId = toWalletId;
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.amount = amount;
        this.category = safe(category);
        this.note = safe(note);
        this.createdAt = createdAt == null ? Timestamp.now() : createdAt;
    }

    public String getId() {
        return id;
    }

    public String getWalletId() {
        return walletId;
    }

    public String getToWalletId() {
        return toWalletId;
    }

    public TransactionType getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public String getNote() {
        return note;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

