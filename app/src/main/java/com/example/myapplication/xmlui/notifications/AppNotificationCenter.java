package com.example.myapplication.xmlui.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.xmlui.AddTransactionActivity;
import com.example.myapplication.xmlui.BudgetActivity;
import com.example.myapplication.xmlui.UiFormatters;

public final class AppNotificationCenter {
    static final String ACTION_REMINDER_ALARM = "com.example.myapplication.action.REMINDER_ALARM";

    private static final String CHANNEL_BUDGET = "budget_alerts";
    private static final String CHANNEL_REMINDER = "transaction_reminders";
    private static final int NOTIFICATION_ID_REMINDER = 1101;
    private static final int NOTIFICATION_ID_BUDGET_BASE = 2100;
    private static final int NOTIFICATION_ID_BUDGET_WARNING_BASE = 2800;

    private AppNotificationCenter() {
    }

    static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED;
    }

    static void notifyTransactionReminder(Context context) {
        if (!canPostNotifications(context)) {
            return;
        }
        ensureChannels(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_wallet_bell)
            .setContentTitle(context.getString(R.string.notification_reminder_title))
            .setContentText(context.getString(R.string.notification_reminder_body))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_reminder_body)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(reminderPendingIntent(context));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REMINDER, builder.build());
    }

    static void notifyBudgetExceeded(Context context, String category, double spent, double limit) {
        if (!canPostNotifications(context)) {
            return;
        }
        ensureChannels(context);
        String displayCategory = category == null || category.trim().isEmpty()
            ? context.getString(R.string.overview_budget_title_default)
            : category.trim();
        String body = context.getString(
            R.string.notification_budget_exceeded_body,
            displayCategory,
            UiFormatters.money(spent),
            UiFormatters.money(limit)
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_wallet_bell)
            .setContentTitle(context.getString(R.string.notification_budget_exceeded_title))
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(budgetPendingIntent(context));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(context).notify(budgetNotificationId(displayCategory, NOTIFICATION_ID_BUDGET_BASE), builder.build());
    }

    static void notifyBudgetWarning(Context context, String budgetName, int percent, double spent, double limit, String dedupeKey) {
        if (!canPostNotifications(context)) {
            return;
        }
        ensureChannels(context);
        String title = context.getString(R.string.notification_item_budget_warning_title);
        String body = context.getString(
            R.string.notification_budget_warning_body_detail,
            budgetName,
            percent,
            UiFormatters.money(spent),
            UiFormatters.money(limit)
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_wallet_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(budgetPendingIntent(context));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(context).notify(
            budgetNotificationId("warning:" + dedupeKey, NOTIFICATION_ID_BUDGET_WARNING_BASE),
            builder.build()
        );
    }

    public static void clearAllNotifications(Context context) {
        if (context == null) {
            return;
        }
        NotificationManagerCompat.from(context.getApplicationContext()).cancelAll();
    }

    private static PendingIntent reminderPendingIntent(Context context) {
        Intent intent = new Intent(context, AddTransactionActivity.class);
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_EXPENSE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
            context,
            3201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent budgetPendingIntent(Context context) {
        Intent intent = new Intent(context, BudgetActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
            context,
            3202,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int budgetNotificationId(String key, int base) {
        return base + Math.abs(key.hashCode() % 700);
    }

    private static void ensureChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel budgetChannel = new NotificationChannel(
            CHANNEL_BUDGET,
            context.getString(R.string.notification_budget_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        );
        budgetChannel.setDescription(context.getString(R.string.notification_budget_channel_desc));
        manager.createNotificationChannel(budgetChannel);

        NotificationChannel reminderChannel = new NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.notification_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        );
        reminderChannel.setDescription(context.getString(R.string.notification_reminder_channel_desc));
        manager.createNotificationChannel(reminderChannel);
    }
}

