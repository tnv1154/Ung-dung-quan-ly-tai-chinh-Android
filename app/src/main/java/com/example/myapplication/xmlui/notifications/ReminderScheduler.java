package com.example.myapplication.xmlui.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.example.myapplication.finance.model.UserSettings;
import com.example.myapplication.xmlui.currency.ExchangeRateSyncScheduler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ReminderScheduler {
    private static final String PREFS_NAME = "finance_notifications";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String KEY_REMINDER_FREQUENCY = "reminder_frequency";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_REMINDER_WEEKDAYS = "reminder_weekdays";

    private ReminderScheduler() {
    }

    public static void syncFromSettings(Context context, UserSettings settings) {
        if (settings == null) {
            return;
        }
        ExchangeRateSyncScheduler.syncFromSettings(context, settings);
        applyConfig(
            context,
            settings.getReminderEnabled(),
            settings.getReminderFrequency(),
            settings.getReminderHour(),
            settings.getReminderMinute(),
            settings.getReminderWeekdays()
        );
    }

    public static void applyConfig(
        Context context,
        boolean enabled,
        String frequency,
        int hour,
        int minute,
        List<Integer> weekdays
    ) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putBoolean(KEY_REMINDER_ENABLED, enabled);
        editor.putString(KEY_REMINDER_FREQUENCY, normalizeFrequency(frequency));
        editor.putInt(KEY_REMINDER_HOUR, clamp(hour, 0, 23, 20));
        editor.putInt(KEY_REMINDER_MINUTE, clamp(minute, 0, 59, 0));
        editor.putString(KEY_REMINDER_WEEKDAYS, serializeWeekdays(weekdays));
        editor.apply();
        if (enabled) {
            scheduleNextReminder(context);
        } else {
            cancelReminder(context);
        }
    }

    public static void rescheduleFromStoredConfig(Context context) {
        if (prefs(context).getBoolean(KEY_REMINDER_ENABLED, false)) {
            scheduleNextReminder(context);
        } else {
            cancelReminder(context);
        }
    }

    public static void clearLocalState(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        cancelReminder(appContext);
        prefs(appContext).edit().clear().apply();
    }

    static void handleReminderAlarm(Context context) {
        if (!prefs(context).getBoolean(KEY_REMINDER_ENABLED, false)) {
            cancelReminder(context);
            return;
        }
        if (shouldNotifyToday(context)) {
            AppNotificationCenter.notifyTransactionReminder(context);
        }
        scheduleNextReminder(context);
    }

    private static boolean shouldNotifyToday(Context context) {
        SharedPreferences preferences = prefs(context);
        String frequency = normalizeFrequency(
            preferences.getString(KEY_REMINDER_FREQUENCY, UserSettings.REMINDER_FREQUENCY_DAILY)
        );
        if (UserSettings.REMINDER_FREQUENCY_DAILY.equals(frequency)) {
            return true;
        }
        List<Integer> selectedWeekdays = parseWeekdays(preferences.getString(KEY_REMINDER_WEEKDAYS, ""));
        int today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return selectedWeekdays.contains(today);
    }

    private static void scheduleNextReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        int hour = clamp(preferences.getInt(KEY_REMINDER_HOUR, 20), 0, 23, 20);
        int minute = clamp(preferences.getInt(KEY_REMINDER_MINUTE, 0), 0, 59, 0);

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        PendingIntent alarmIntent = reminderPendingIntent(context);
        alarmManager.cancel(alarmIntent);
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), alarmIntent);
    }

    private static void cancelReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(reminderPendingIntent(context));
    }

    private static PendingIntent reminderPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderAlarmReceiver.class);
        intent.setAction(AppNotificationCenter.ACTION_REMINDER_ALARM);
        return PendingIntent.getBroadcast(
            context,
            3101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String normalizeFrequency(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (UserSettings.REMINDER_FREQUENCY_WEEKLY.equals(value)) {
            return UserSettings.REMINDER_FREQUENCY_WEEKLY;
        }
        return UserSettings.REMINDER_FREQUENCY_DAILY;
    }

    private static String serializeWeekdays(List<Integer> weekdays) {
        List<Integer> normalized = parseWeekdaysList(weekdays);
        if (normalized.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Integer item : normalized) {
            parts.add(String.valueOf(item));
        }
        return String.join(",", parts);
    }

    private static List<Integer> parseWeekdays(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] tokens = raw.split(",");
        List<Integer> values = new ArrayList<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int day = Integer.parseInt(trimmed);
                if (day >= Calendar.SUNDAY && day <= Calendar.SATURDAY && !values.contains(day)) {
                    values.add(day);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private static List<Integer> parseWeekdaysList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> normalized = new ArrayList<>();
        for (Integer value : values) {
            if (value == null) {
                continue;
            }
            if (value >= Calendar.SUNDAY && value <= Calendar.SATURDAY && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}

