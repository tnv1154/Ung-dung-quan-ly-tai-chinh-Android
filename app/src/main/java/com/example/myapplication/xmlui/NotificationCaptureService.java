package com.example.myapplication.xmlui;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationCaptureService extends NotificationListenerService {

    private static final String PREF_NAME = "notification_capture_pref";
    private static final String KEY_LATEST_TEXT = "latest_text";
    private static final String KEY_LATEST_TIME = "latest_time";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        Notification notification = sbn.getNotification();
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        StringBuilder content = new StringBuilder();
        if (title != null && title.length() > 0) {
            content.append(title).append(": ");
        }
        if (bigText != null && bigText.length() > 0) {
            content.append(bigText);
        } else if (text != null && text.length() > 0) {
            content.append(text);
        }
        String captured = content.toString().trim();
        if (captured.isEmpty()) {
            return;
        }
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LATEST_TEXT, captured)
            .putLong(KEY_LATEST_TIME, System.currentTimeMillis())
            .apply();
    }

    public static String getLatestNotificationText(android.content.Context context) {
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            .getString(KEY_LATEST_TEXT, null);
    }

    public static void clearLatestNotification(android.content.Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
}
