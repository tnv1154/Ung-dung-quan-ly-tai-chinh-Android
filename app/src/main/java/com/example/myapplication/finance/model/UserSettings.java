package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UserSettings {
    public static final String REMINDER_FREQUENCY_DAILY = "DAILY";
    public static final String REMINDER_FREQUENCY_WEEKLY = "WEEKLY";

    private final String currency;
    private final boolean showBudgetWarnings;
    private final boolean compactNumberFormat;
    private final boolean reminderEnabled;
    private final String reminderFrequency;
    private final int reminderHour;
    private final int reminderMinute;
    private final List<Integer> reminderWeekdays;
    private final Timestamp updatedAt;

    public UserSettings() {
        this("VND", true, false, false, REMINDER_FREQUENCY_DAILY, 20, 0, defaultReminderWeekdays(), Timestamp.now());
    }

    public UserSettings(String currency, boolean showBudgetWarnings, boolean compactNumberFormat, Timestamp updatedAt) {
        this(currency, showBudgetWarnings, compactNumberFormat, false, REMINDER_FREQUENCY_DAILY, 20, 0, defaultReminderWeekdays(), updatedAt);
    }

    public UserSettings(
        String currency,
        boolean showBudgetWarnings,
        boolean compactNumberFormat,
        boolean reminderEnabled,
        String reminderFrequency,
        int reminderHour,
        int reminderMinute,
        List<Integer> reminderWeekdays,
        Timestamp updatedAt
    ) {
        this.currency = currency == null ? "VND" : currency;
        this.showBudgetWarnings = showBudgetWarnings;
        this.compactNumberFormat = compactNumberFormat;
        this.reminderEnabled = reminderEnabled;
        this.reminderFrequency = normalizeFrequency(reminderFrequency);
        this.reminderHour = clamp(reminderHour, 0, 23, 20);
        this.reminderMinute = clamp(reminderMinute, 0, 59, 0);
        this.reminderWeekdays = sanitizeWeekdays(reminderWeekdays);
        this.updatedAt = updatedAt == null ? Timestamp.now() : updatedAt;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean getShowBudgetWarnings() {
        return showBudgetWarnings;
    }

    public boolean getCompactNumberFormat() {
        return compactNumberFormat;
    }

    public boolean getReminderEnabled() {
        return reminderEnabled;
    }

    public String getReminderFrequency() {
        return reminderFrequency;
    }

    public int getReminderHour() {
        return reminderHour;
    }

    public int getReminderMinute() {
        return reminderMinute;
    }

    public List<Integer> getReminderWeekdays() {
        return reminderWeekdays;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    private static List<Integer> defaultReminderWeekdays() {
        List<Integer> defaults = new ArrayList<>();
        defaults.add(Calendar.MONDAY);
        defaults.add(Calendar.TUESDAY);
        defaults.add(Calendar.WEDNESDAY);
        defaults.add(Calendar.THURSDAY);
        defaults.add(Calendar.FRIDAY);
        return Collections.unmodifiableList(defaults);
    }

    private static List<Integer> sanitizeWeekdays(List<Integer> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) {
            return defaultReminderWeekdays();
        }
        List<Integer> sanitized = new ArrayList<>();
        for (Integer day : weekdays) {
            if (day == null) {
                continue;
            }
            if (day >= Calendar.SUNDAY && day <= Calendar.SATURDAY && !sanitized.contains(day)) {
                sanitized.add(day);
            }
        }
        if (sanitized.isEmpty()) {
            return defaultReminderWeekdays();
        }
        return Collections.unmodifiableList(sanitized);
    }

    private static String normalizeFrequency(String frequency) {
        String raw = frequency == null ? "" : frequency.trim().toUpperCase(Locale.ROOT);
        if (REMINDER_FREQUENCY_WEEKLY.equals(raw)) {
            return REMINDER_FREQUENCY_WEEKLY;
        }
        return REMINDER_FREQUENCY_DAILY;
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}

