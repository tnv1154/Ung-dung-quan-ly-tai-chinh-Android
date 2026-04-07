package com.example.myapplication.xmlui;

import com.google.firebase.Timestamp;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class UiFormatters {
    private UiFormatters() {
    }

    private static final Locale VI = new Locale("vi", "VN");

    private static DecimalFormat moneyFormatter() {
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(VI);
        formatter.applyPattern("#,###");
        return formatter;
    }

    public static String money(double value) {
        return moneyFormatter().format(value) + " ₫";
    }

    public static String moneyRaw(double value) {
        return moneyFormatter().format(value) + " đ";
    }

    public static String percent(double ratio) {
        return String.format(Locale.US, "%.0f%%", ratio * 100.0);
    }

    public static String dateTime(Timestamp timestamp) {
        Date date = new Date(timestamp.getSeconds() * 1000L);
        return new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(date);
    }

    public static String dateOnly(Timestamp timestamp) {
        Date date = new Date(timestamp.getSeconds() * 1000L);
        return new SimpleDateFormat("dd/MM", Locale.US).format(date);
    }

    public static String timeOnly(Timestamp timestamp) {
        Date date = new Date(timestamp.getSeconds() * 1000L);
        return new SimpleDateFormat("HH:mm", Locale.US).format(date);
    }
}
