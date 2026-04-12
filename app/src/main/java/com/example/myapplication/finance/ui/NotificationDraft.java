package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.TransactionType;

public class NotificationDraft {
    private final TransactionType type;
    private final double amount;
    private final String category;
    private final String note;
    private final String currency;
    private final String sourceName;
    private final String walletHint;
    private final long transactionTimestampMillis;

    public NotificationDraft(TransactionType type, double amount, String category, String note) {
        this(type, amount, category, note, "VND", "", "", 0L);
    }

    public NotificationDraft(
        TransactionType type,
        double amount,
        String category,
        String note,
        String currency,
        String sourceName,
        String walletHint,
        long transactionTimestampMillis
    ) {
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.amount = amount;
        this.category = category == null ? "" : category;
        this.note = note == null ? "" : note;
        this.currency = currency == null || currency.trim().isEmpty() ? "VND" : currency.trim().toUpperCase(java.util.Locale.ROOT);
        this.sourceName = sourceName == null ? "" : sourceName;
        this.walletHint = walletHint == null ? "" : walletHint;
        this.transactionTimestampMillis = Math.max(0L, transactionTimestampMillis);
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

    public String getCurrency() {
        return currency;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getWalletHint() {
        return walletHint;
    }

    public long getTransactionTimestampMillis() {
        return transactionTimestampMillis;
    }
}

