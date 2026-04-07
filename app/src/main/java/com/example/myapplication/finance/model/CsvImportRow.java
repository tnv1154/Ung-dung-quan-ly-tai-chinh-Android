package com.example.myapplication.finance.model;

public class CsvImportRow {
    private final TransactionType type;
    private final double amount;
    private final String category;
    private final String note;
    private final String walletName;
    private final String toWalletName;

    public CsvImportRow(
        TransactionType type,
        double amount,
        String category,
        String note,
        String walletName,
        String toWalletName
    ) {
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.amount = amount;
        this.category = category == null ? "" : category;
        this.note = note == null ? "" : note;
        this.walletName = walletName;
        this.toWalletName = toWalletName;
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

    public String getWalletName() {
        return walletName;
    }

    public String getToWalletName() {
        return toWalletName;
    }
}

