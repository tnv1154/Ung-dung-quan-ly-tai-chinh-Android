package com.example.myapplication.xmlui.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

public final class MonthlyReportNotifier {
    private static final String PREFS_NAME = "finance_notifications";
    private static final String KEY_MONTHLY_REPORT_PERIOD = "monthly_report_period_key";

    private MonthlyReportNotifier() {
    }

    public static void maybeNotifyMonthlyReport(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (!AppNotificationCenter.isMonthlyReportEnabled(appContext)) {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        int month = calendar.get(Calendar.MONTH) + 1;
        int year = calendar.get(Calendar.YEAR);
        String periodKey = String.format(Locale.ROOT, "%04d-%02d", year, month);
        SharedPreferences preferences = prefs(appContext);
        if (periodKey.equals(preferences.getString(KEY_MONTHLY_REPORT_PERIOD, ""))) {
            return;
        }
        AppNotificationCenter.notifyMonthlyReport(appContext, month, year);
        preferences.edit().putString(KEY_MONTHLY_REPORT_PERIOD, periodKey).apply();
    }

    public static void clearLocalState(Context context) {
        if (context == null) {
            return;
        }
        prefs(context.getApplicationContext()).edit().remove(KEY_MONTHLY_REPORT_PERIOD).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

