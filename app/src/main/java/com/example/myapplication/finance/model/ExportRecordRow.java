package com.example.myapplication.finance.model;

public class ExportRecordRow {
    private final int stt;
    private final String date;
    private final String time;
    private final Double incomeAmount;
    private final Double expenseAmount;
    private final String currency;
    private final String walletName;
    private final String category;
    private final String note;

    public ExportRecordRow(
        int stt,
        String date,
        String time,
        Double incomeAmount,
        Double expenseAmount,
        String currency,
        String walletName,
        String category,
        String note
    ) {
        this.stt = stt;
        this.date = safe(date);
        this.time = safe(time);
        this.incomeAmount = incomeAmount;
        this.expenseAmount = expenseAmount;
        this.currency = safe(currency);
        this.walletName = safe(walletName);
        this.category = safe(category);
        this.note = safe(note);
    }

    public int getStt() {
        return stt;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public Double getIncomeAmount() {
        return incomeAmount;
    }

    public Double getExpenseAmount() {
        return expenseAmount;
    }

    public String getCurrency() {
        return currency;
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

