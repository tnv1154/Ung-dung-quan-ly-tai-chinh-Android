package com.example.myapplication.xmlui;

public class UiWallet {
    private final String id;
    private final String name;
    private final double balance;
    private final String accountType;
    private final String iconKey;
    private final String currency;
    private final String note;
    private final boolean includeInReport;
    private final String providerName;
    private final boolean locked;
    private final Double convertedVndBalance;

    public UiWallet(
        String id,
        String name,
        double balance,
        String accountType,
        String iconKey,
        String currency,
        String note,
        boolean includeInReport,
        String providerName,
        boolean locked,
        Double convertedVndBalance
    ) {
        this.id = id;
        this.name = name;
        this.balance = balance;
        this.accountType = accountType;
        this.iconKey = iconKey;
        this.currency = currency;
        this.note = note;
        this.includeInReport = includeInReport;
        this.providerName = providerName;
        this.locked = locked;
        this.convertedVndBalance = convertedVndBalance;
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

    public boolean isIncludeInReport() {
        return includeInReport;
    }

    public String getProviderName() {
        return providerName;
    }

    public boolean isLocked() {
        return locked;
    }

    public Double getConvertedVndBalance() {
        return convertedVndBalance;
    }
}
