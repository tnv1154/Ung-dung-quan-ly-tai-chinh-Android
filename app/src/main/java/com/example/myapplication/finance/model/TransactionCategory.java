package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

public class TransactionCategory {
    private final String id;
    private final String name;
    private final TransactionType type;
    private final String parentName;
    private final String iconKey;
    private final int sortOrder;
    private final Timestamp updatedAt;

    public TransactionCategory() {
        this("", "", TransactionType.EXPENSE, "", "dot", 0, Timestamp.now());
    }

    public TransactionCategory(
        String id,
        String name,
        TransactionType type,
        String parentName,
        String iconKey,
        int sortOrder,
        Timestamp updatedAt
    ) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.type = type == null ? TransactionType.EXPENSE : type;
        this.parentName = parentName == null ? "" : parentName;
        this.iconKey = iconKey == null ? "dot" : iconKey;
        this.sortOrder = sortOrder;
        this.updatedAt = updatedAt == null ? Timestamp.now() : updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TransactionType getType() {
        return type;
    }

    public String getParentName() {
        return parentName;
    }

    public String getIconKey() {
        return iconKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
}

