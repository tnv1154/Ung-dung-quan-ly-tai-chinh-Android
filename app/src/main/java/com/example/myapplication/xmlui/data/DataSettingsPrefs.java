package com.example.myapplication.xmlui;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DataSettingsPrefs {
    private static final String PREF_NAME = "finance_data_settings_prefs";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_WIFI_ONLY = "wifi_only";

    private DataSettingsPrefs() {
    }

    static long getLastSyncAt(Context context) {
        if (context == null) {
            return 0L;
        }
        return context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_AT, 0L);
    }

    static void markSyncedNow(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .apply();
    }

    static boolean isWifiOnlyEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WIFI_ONLY, false);
    }

    static void setWifiOnlyEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WIFI_ONLY, enabled)
            .apply();
    }

    static String formatLastSync(long millis) {
        if (millis <= 0L) {
            return "";
        }
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    static void clear(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
}
