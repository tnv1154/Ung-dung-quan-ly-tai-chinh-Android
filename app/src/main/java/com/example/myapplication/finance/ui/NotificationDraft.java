package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.TransactionType;

public class NotificationDraft {
    private final TransactionType type;
    private final double amount;
    private final String category;
    private final String note;

    public NotificationDraft(TransactionType type, double amount, String category, String note) {
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.amount = amount;
        this.category = category == null ? "" : category;
        this.note = note == null ? "" : note;
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
}

