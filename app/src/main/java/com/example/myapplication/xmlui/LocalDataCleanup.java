package com.example.myapplication.xmlui;

import android.content.Context;

import com.example.myapplication.xmlui.currency.ExchangeRateSyncScheduler;
import com.example.myapplication.xmlui.notifications.AppNotificationCenter;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;

final class LocalDataCleanup {

    private LocalDataCleanup() {
    }

    static void clear(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        DataSettingsPrefs.clear(appContext);
        NotificationCaptureService.clearLatestNotification(appContext);
        BudgetAlertNotifier.clearLocalState(appContext);
        ReminderScheduler.clearLocalState(appContext);
        ExchangeRateSyncScheduler.clearLocalState(appContext);
        AppNotificationCenter.clearAllNotifications(appContext);
    }
}
