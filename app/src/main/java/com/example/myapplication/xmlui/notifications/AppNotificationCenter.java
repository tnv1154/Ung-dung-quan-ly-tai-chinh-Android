package com.example.myapplication.xmlui.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.xmlui.AddTransactionActivity;
import com.example.myapplication.xmlui.BudgetActivity;
import com.example.myapplication.xmlui.MonthlyReportActivity;
import com.example.myapplication.xmlui.UiFormatters;

public final class AppNotificationCenter {
    static final String ACTION_REMINDER_ALARM = "com.example.myapplication.action.REMINDER_ALARM";

    private static final String PREFS_NAME = "finance_notifications";
    private static final String CHANNEL_BUDGET = "budget_alerts";
    private static final String CHANNEL_REMINDER = "transaction_reminders";
    private static final String CHANNEL_REPORT = "monthly_reports";
    private static final int NOTIFICATION_ID_REMINDER = 1101;
    private static final int NOTIFICATION_ID_BUDGET_BASE = 2100;
    private static final int NOTIFICATION_ID_BUDGET_WARNING_BASE = 2800;
    private static final int NOTIFICATION_ID_MONTHLY_REPORT_BASE = 3600;
    private static final String KEY_MONTHLY_REPORT_ENABLED = "monthly_report_enabled";
    private static final String TYPE_BUDGET_EXCEEDED = "budget_exceeded";
    private static final String TYPE_BUDGET_WARNING = "budget_warning";
    private static final String TYPE_REMINDER = "reminder";
    private static final String TYPE_MONTHLY_REPORT = "monthly_report";
    private static final String KEY_IN_APP_TITLE_PREFIX = "in_app_title_";
    private static final String KEY_IN_APP_BODY_PREFIX = "in_app_body_";
    private static final String KEY_IN_APP_TIME_PREFIX = "in_app_time_";

    private AppNotificationCenter() {
    }

    public static final class InAppNotificationEntry {
        private final String title;
        private final String body;
        private final long triggeredAtMillis;

        private InAppNotificationEntry(String title, String body, long triggeredAtMillis) {
            this.title = title;
            this.body = body;
            this.triggeredAtMillis = triggeredAtMillis;
        }

        public String getTitle() {
            return title;
        }

        public String getBody() {
            return body;
        }

        public long getTriggeredAtMillis() {
            return triggeredAtMillis;
        }
    }

    static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isMonthlyReportEnabled(Context context) {
        if (context == null) {
            return true;
        }
        return prefs(context).getBoolean(KEY_MONTHLY_REPORT_ENABLED, true);
    }

    public static void setMonthlyReportEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putBoolean(KEY_MONTHLY_REPORT_ENABLED, enabled).apply();
    }

    public static InAppNotificationEntry getLatestBudgetExceededEntry(Context context) {
        return readInAppEntry(context, TYPE_BUDGET_EXCEEDED);
    }

    public static InAppNotificationEntry getLatestBudgetWarningEntry(Context context) {
        return readInAppEntry(context, TYPE_BUDGET_WARNING);
    }

    public static InAppNotificationEntry getLatestReminderEntry(Context context) {
        return readInAppEntry(context, TYPE_REMINDER);
    }

    public static InAppNotificationEntry getLatestMonthlyReportEntry(Context context) {
        return readInAppEntry(context, TYPE_MONTHLY_REPORT);
    }

    static void notifyTransactionReminder(Context context) {
        Context appContext = context.getApplicationContext();
        String title = appContext.getString(R.string.notification_reminder_title);
        String body = appContext.getString(R.string.notification_reminder_body);
        saveInAppEntry(appContext, TYPE_REMINDER, title, body);
        if (!canPostNotifications(appContext)) {
            return;
        }
        ensureChannels(appContext);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_wallet_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(reminderPendingIntent(appContext));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID_REMINDER, builder.build());
    }

    static void notifyBudgetExceeded(Context context, String category, double spent, double limit) {
        Context appContext = context.getApplicationContext();
        String displayCategory = category == null || category.trim().isEmpty()
            ? appContext.getString(R.string.overview_budget_title_default)
            : category.trim();
        String title = appContext.getString(R.string.notification_budget_exceeded_title);
        String body = appContext.getString(
            R.string.notification_budget_exceeded_body,
            displayCategory,
            UiFormatters.money(spent),
            UiFormatters.money(limit)
        );
        saveInAppEntry(appContext, TYPE_BUDGET_EXCEEDED, title, body);
        if (!canPostNotifications(appContext)) {
            return;
        }
        ensureChannels(appContext);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_wallet_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(budgetPendingIntent(appContext));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(appContext).notify(
            budgetNotificationId(displayCategory, NOTIFICATION_ID_BUDGET_BASE),
            builder.build()
        );
    }

    static void notifyBudgetWarning(Context context, String budgetName, int percent, double spent, double limit, String dedupeKey) {
        Context appContext = context.getApplicationContext();
        String title = appContext.getString(R.string.notification_item_budget_warning_title);
        String body = appContext.getString(
            R.string.notification_budget_warning_body_detail,
            budgetName,
            percent,
            UiFormatters.money(spent),
            UiFormatters.money(limit)
        );
        saveInAppEntry(appContext, TYPE_BUDGET_WARNING, title, body);
        if (!canPostNotifications(appContext)) {
            return;
        }
        ensureChannels(appContext);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_wallet_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(budgetPendingIntent(appContext));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(appContext).notify(
            budgetNotificationId("warning:" + dedupeKey, NOTIFICATION_ID_BUDGET_WARNING_BASE),
            builder.build()
        );
    }

    static void notifyMonthlyReport(Context context, int month, int year) {
        Context appContext = context.getApplicationContext();
        String title = appContext.getString(R.string.notification_monthly_report_title);
        String body = appContext.getString(R.string.notification_monthly_report_body, month, year);
        saveInAppEntry(appContext, TYPE_MONTHLY_REPORT, title, body);
        if (!canPostNotifications(appContext)) {
            return;
        }
        ensureChannels(appContext);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_REPORT)
            .setSmallIcon(R.drawable.ic_notification_report)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(monthlyReportPendingIntent(appContext));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(appContext).notify(
            budgetNotificationId("report:" + year + "-" + month, NOTIFICATION_ID_MONTHLY_REPORT_BASE),
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

    private static PendingIntent monthlyReportPendingIntent(Context context) {
        Intent intent = new Intent(context, MonthlyReportActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
            context,
            3203,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int budgetNotificationId(String key, int base) {
        return base + Math.abs(key.hashCode() % 700);
    }

    private static void saveInAppEntry(Context context, String type, String title, String body) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(KEY_IN_APP_TITLE_PREFIX + type, title);
        editor.putString(KEY_IN_APP_BODY_PREFIX + type, body);
        editor.putLong(KEY_IN_APP_TIME_PREFIX + type, System.currentTimeMillis());
        editor.apply();
    }

    private static InAppNotificationEntry readInAppEntry(Context context, String type) {
        if (context == null) {
            return null;
        }
        SharedPreferences preferences = prefs(context);
        String title = preferences.getString(KEY_IN_APP_TITLE_PREFIX + type, "");
        String body = preferences.getString(KEY_IN_APP_BODY_PREFIX + type, "");
        long time = preferences.getLong(KEY_IN_APP_TIME_PREFIX + type, 0L);
        if (title == null || title.trim().isEmpty() || body == null || body.trim().isEmpty() || time <= 0L) {
            return null;
        }
        return new InAppNotificationEntry(title, body, time);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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

        NotificationChannel reportChannel = new NotificationChannel(
            CHANNEL_REPORT,
            context.getString(R.string.notification_report_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        );
        reportChannel.setDescription(context.getString(R.string.notification_report_channel_desc));
        manager.createNotificationChannel(reportChannel);
    }
}

