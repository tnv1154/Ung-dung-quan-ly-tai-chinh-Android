package com.example.myapplication.xmlui.currency;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.UserSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.Locale;

public final class ExchangeRateSyncScheduler {
    public static final String ACTION_EXCHANGE_RATE_SYNC_ALARM =
        "com.example.myapplication.action.EXCHANGE_RATE_SYNC_ALARM";

    private static final String PREFS_NAME = "finance_exchange_rate_sync";
    private static final String KEY_SLOT_DAY = "slot_day";
    private static final String KEY_SLOT_MASK = "slot_mask";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_BASE_CURRENCY = "base_currency";
    private static final String EXTRA_SLOT_INDEX = "extra_slot_index";
    private static final String EXTRA_SLOT_DAY = "extra_slot_day";
    private static final int REQUEST_CODE = 4102;
    private static final int[] SLOT_HOURS = {8, 14, 20};
    private static final int SLOT_MINUTE = 0;

    private ExchangeRateSyncScheduler() {
    }

    public static void syncFromSettings(Context context, UserSettings settings) {
        if (context == null || settings == null) {
            return;
        }
        prefs(context).edit()
            .putString(KEY_BASE_CURRENCY, normalizeBaseCurrency(settings.getCurrency()))
            .apply();
        scheduleNextSync(context.getApplicationContext());
    }

    public static void rescheduleFromStoredConfig(Context context) {
        if (context == null) {
            return;
        }
        scheduleNextSync(context.getApplicationContext());
    }

    static void handleAlarm(Context context, Intent intent) {
        if (context == null) {
            return;
        }
        String userId = currentUserId();
        if (userId == null) {
            cancelSync(context.getApplicationContext());
            return;
        }
        int slotIndex = clampSlot(intent == null ? -1 : intent.getIntExtra(EXTRA_SLOT_INDEX, -1));
        int slotDay = intent == null ? -1 : intent.getIntExtra(EXTRA_SLOT_DAY, -1);
        if (slotIndex < 0) {
            SlotSpec current = resolveCurrentSlot();
            slotIndex = current.slotIndex;
            slotDay = current.dayToken;
        }
        if (!markSlotRequested(context, slotDay, slotIndex)) {
            scheduleNextSync(context.getApplicationContext());
            return;
        }
        try {
            String baseCurrency = prefs(context).getString(
                KEY_BASE_CURRENCY,
                ExchangeRateSnapshot.DEFAULT_BASE_CURRENCY
            );
            ExchangeRateSnapshot snapshot = FrankfurterRateClient.fetchLatestSnapshot(baseCurrency);
            FirestoreFinanceRepository repository = new FirestoreFinanceRepository();
            repository.upsertExchangeRateSnapshot(userId, snapshot);
            prefs(context).edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
        }
        scheduleNextSync(context.getApplicationContext());
    }

    public static long getLastSyncAtMillis(Context context) {
        if (context == null) {
            return 0L;
        }
        return prefs(context).getLong(KEY_LAST_SYNC_AT, 0L);
    }

    public static void clearLocalState(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        cancelSync(appContext);
        prefs(appContext).edit().clear().apply();
    }

    private static void scheduleNextSync(Context context) {
        String userId = currentUserId();
        if (userId == null) {
            cancelSync(context);
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        SlotSpec nextSlot = resolveNextSlot();
        Intent alarm = new Intent(context, ExchangeRateAlarmReceiver.class);
        alarm.setAction(ACTION_EXCHANGE_RATE_SYNC_ALARM);
        alarm.putExtra(EXTRA_SLOT_INDEX, nextSlot.slotIndex);
        alarm.putExtra(EXTRA_SLOT_DAY, nextSlot.dayToken);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            alarm,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextSlot.triggerAtMillis,
            pendingIntent
        );
    }

    private static void cancelSync(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Intent alarm = new Intent(context, ExchangeRateAlarmReceiver.class);
        alarm.setAction(ACTION_EXCHANGE_RATE_SYNC_ALARM);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            alarm,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
    }

    private static boolean markSlotRequested(Context context, int dayToken, int slotIndex) {
        if (dayToken <= 0 || slotIndex < 0 || slotIndex >= SLOT_HOURS.length) {
            return false;
        }
        SharedPreferences preferences = prefs(context);
        int storedDay = preferences.getInt(KEY_SLOT_DAY, -1);
        int mask = storedDay == dayToken ? preferences.getInt(KEY_SLOT_MASK, 0) : 0;
        int slotBit = 1 << slotIndex;
        if ((mask & slotBit) != 0) {
            return false;
        }
        mask = mask | slotBit;
        preferences.edit()
            .putInt(KEY_SLOT_DAY, dayToken)
            .putInt(KEY_SLOT_MASK, mask)
            .apply();
        return true;
    }

    private static SlotSpec resolveNextSlot() {
        Calendar now = Calendar.getInstance();
        long nowMillis = now.getTimeInMillis();
        for (int i = 0; i < SLOT_HOURS.length; i++) {
            Calendar slot = (Calendar) now.clone();
            slot.set(Calendar.HOUR_OF_DAY, SLOT_HOURS[i]);
            slot.set(Calendar.MINUTE, SLOT_MINUTE);
            slot.set(Calendar.SECOND, 0);
            slot.set(Calendar.MILLISECOND, 0);
            if (slot.getTimeInMillis() > nowMillis) {
                return new SlotSpec(slot.getTimeInMillis(), i, dayToken(slot));
            }
        }
        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        tomorrow.set(Calendar.HOUR_OF_DAY, SLOT_HOURS[0]);
        tomorrow.set(Calendar.MINUTE, SLOT_MINUTE);
        tomorrow.set(Calendar.SECOND, 0);
        tomorrow.set(Calendar.MILLISECOND, 0);
        return new SlotSpec(tomorrow.getTimeInMillis(), 0, dayToken(tomorrow));
    }

    private static SlotSpec resolveCurrentSlot() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int slotIndex = SLOT_HOURS.length - 1;
        for (int i = SLOT_HOURS.length - 1; i >= 0; i--) {
            if (hour >= SLOT_HOURS[i]) {
                slotIndex = i;
                break;
            }
        }
        return new SlotSpec(now.getTimeInMillis(), slotIndex, dayToken(now));
    }

    private static int clampSlot(int value) {
        if (value < 0 || value >= SLOT_HOURS.length) {
            return -1;
        }
        return value;
    }

    private static int dayToken(Calendar calendar) {
        return calendar.get(Calendar.YEAR) * 10000
            + (calendar.get(Calendar.MONTH) + 1) * 100
            + calendar.get(Calendar.DAY_OF_MONTH);
    }

    private static String currentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getUid() == null || user.getUid().isBlank()) {
            return null;
        }
        return user.getUid();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String normalizeBaseCurrency(String rawCurrency) {
        String value = rawCurrency == null ? "" : rawCurrency.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return ExchangeRateSnapshot.DEFAULT_BASE_CURRENCY;
        }
        return value;
    }

    private static final class SlotSpec {
        final long triggerAtMillis;
        final int slotIndex;
        final int dayToken;

        SlotSpec(long triggerAtMillis, int slotIndex, int dayToken) {
            this.triggerAtMillis = triggerAtMillis;
            this.slotIndex = slotIndex;
            this.dayToken = dayToken;
        }
    }
}
