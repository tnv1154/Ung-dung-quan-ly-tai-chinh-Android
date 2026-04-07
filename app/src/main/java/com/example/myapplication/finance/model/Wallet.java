package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

public class Wallet {
    private final String id;
    private final String name;
    private final double balance;
    private final String accountType;
    private final String iconKey;
    private final String currency;
    private final String note;
    private final boolean includeInReport;
    private final String providerName;
    private final boolean isLocked;
    private final Timestamp updatedAt;

    public Wallet() {
        this("", "", 0.0, "CASH", "cash", "VND", "", true, "", false, Timestamp.now());
    }

    public Wallet(
        String id,
        String name,
        double balance,
        String accountType,
        String iconKey,
        String currency,
        String note,
        boolean includeInReport,
        String providerName,
        boolean isLocked,
        Timestamp updatedAt
    ) {
        this.id = safe(id);
        this.name = safe(name);
        this.balance = balance;
        this.accountType = safeDefault(accountType, "CASH");
        this.iconKey = safeDefault(iconKey, "cash");
        this.currency = safeDefault(currency, "VND");
        this.note = safe(note);
        this.includeInReport = includeInReport;
        this.providerName = safe(providerName);
        this.isLocked = isLocked;
        this.updatedAt = updatedAt == null ? Timestamp.now() : updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getBalance() {
        return balance;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getIconKey() {
        return iconKey;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNote() {
        return note;
    }

    public boolean getIncludeInReport() {
        return includeInReport;
    }

    public String getProviderName() {
        return providerName;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}

