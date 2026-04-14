package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

import java.util.Locale;

public class CsvImportRow {
    private final int rowNumber;
    private final TransactionType type;
    private final double amount;
    private final Timestamp transactionCreatedAt;
    private final String currencyCode;
    private final String category;
    private final String note;
    private final String walletName;
    private final String walletId;
    private final boolean valid;
    private final String validationMessage;

    public CsvImportRow(
        int rowNumber,
        TransactionType type,
        double amount,
        Timestamp transactionCreatedAt,
        String currencyCode,
        String category,
        String note,
        String walletName,
        String walletId,
        boolean valid,
        String validationMessage
    ) {
        this.rowNumber = rowNumber;
        this.type = type;
        this.amount = amount;
        this.transactionCreatedAt = transactionCreatedAt;
        this.currencyCode = currencyCode == null ? "" : currencyCode.trim().toUpperCase(Locale.ROOT);
        this.category = category == null ? "" : category;
        this.note = note == null ? "" : note;
        this.walletName = walletName == null ? "" : walletName;
        this.walletId = walletId;
        this.valid = valid;
        this.validationMessage = validationMessage == null ? "" : validationMessage;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public TransactionType getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public Timestamp getTransactionCreatedAt() {
        return transactionCreatedAt;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getCategory() {
        return category;
    }

    public String getNote() {
        return note;
    }

    public String getWalletName() {
        return walletName;
    }

    public String getWalletId() {
        return walletId;
    }

    public boolean isValid() {
        return valid;
    }

    public String getValidationMessage() {
        return validationMessage;
    }
}

