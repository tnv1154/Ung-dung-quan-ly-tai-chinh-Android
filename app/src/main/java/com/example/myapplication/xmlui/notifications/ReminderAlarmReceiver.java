package com.example.myapplication.xmlui.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!AppNotificationCenter.ACTION_REMINDER_ALARM.equals(intent.getAction())) {
            return;
        }
        ReminderScheduler.handleReminderAlarm(context.getApplicationContext());
    }
}

