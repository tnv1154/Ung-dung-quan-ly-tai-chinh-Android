package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

public class FinanceTransaction {
    private final String id;
    private final String walletId;
    private final String toWalletId;
    private final TransactionType type;
    private final double amount;
    private final double destinationAmount;
    private final String sourceCurrency;
    private final String destinationCurrency;
    private final Double exchangeRate;
    private final Timestamp exchangeRateFetchedAt;
    private final String category;
    private final String note;
    private final Timestamp createdAt;

    public FinanceTransaction() {
        this("", "", null, TransactionType.EXPENSE, 0.0, 0.0, "", "", null, null, "", "", Timestamp.now());
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
        this(
            id,
            walletId,
            toWalletId,
            type,
            amount,
            amount,
            "",
            "",
            null,
            null,
            category,
            note,
            createdAt
        );
    }

    public FinanceTransaction(
        String id,
        String walletId,
        String toWalletId,
        TransactionType type,
        double amount,
        double destinationAmount,
        String sourceCurrency,
        String destinationCurrency,
        Double exchangeRate,
        Timestamp exchangeRateFetchedAt,
        String category,
        String note,
        Timestamp createdAt
    ) {
        this.id = safe(id);
        this.walletId = safe(walletId);
        this.toWalletId = toWalletId;
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.amount = amount;
        this.destinationAmount = destinationAmount;
        this.sourceCurrency = safe(sourceCurrency);
        this.destinationCurrency = safe(destinationCurrency);
        this.exchangeRate = exchangeRate;
        this.exchangeRateFetchedAt = exchangeRateFetchedAt;
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

    public double getDestinationAmount() {
        return destinationAmount;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public String getDestinationCurrency() {
        return destinationCurrency;
    }

    public Double getExchangeRate() {
        return exchangeRate;
    }

    public Timestamp getExchangeRateFetchedAt() {
        return exchangeRateFetchedAt;
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

