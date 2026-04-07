package com.example.myapplication.xmlui.currency;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExchangeRateAlarmReceiver extends BroadcastReceiver {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        if (!ExchangeRateSyncScheduler.ACTION_EXCHANGE_RATE_SYNC_ALARM.equals(intent.getAction())) {
            return;
        }
        BroadcastReceiver.PendingResult pendingResult = goAsync();
        EXECUTOR.execute(() -> {
            try {
                ExchangeRateSyncScheduler.handleAlarm(context.getApplicationContext(), intent);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
