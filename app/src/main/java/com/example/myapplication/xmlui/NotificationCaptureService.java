package com.example.myapplication.xmlui;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceParsersKt;
import com.example.myapplication.finance.ui.NotificationDraft;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationCaptureService extends NotificationListenerService {

    private static final String PREF_NAME = "notification_capture_pref";
    private static final String PREF_AUTO_ENTRY = "auto_entry_settings";
    private static final String KEY_ENABLE_VCB = "enable_vcb";
    private static final String KEY_ENABLE_VIETIN = "enable_vietin";
    private static final String KEY_ENABLE_MBBANK = "enable_mbbank";
    private static final String KEY_ENABLE_BIDV = "enable_bidv";
    private static final String KEY_LAST_PROCESSED_CAPTURE = "last_processed_capture";
    private static final String KEY_LATEST_TEXT = "latest_text";
    private static final String KEY_LATEST_TIME = "latest_time";
    private static final String KEY_LATEST_PACKAGE = "latest_package";
    private static final String KEY_LATEST_TITLE = "latest_title";
    private static final String KEY_LATEST_BODY = "latest_body";
    private static final String KEY_LATEST_APP_NAME = "latest_app_name";
    private static final String KEY_LATEST_KEY = "latest_key";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final FirestoreFinanceRepository repository = new FirestoreFinanceRepository();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        Notification notification = sbn.getNotification();
        String sourcePackage = sbn.getPackageName();
        if (sourcePackage != null && sourcePackage.equals(getPackageName())) {
            return;
        }
        Bundle extras = notification.extras;
        if (extras == null) {
            return;
        }
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        String appName = resolveAppName(sourcePackage);

        String titleText = title == null ? "" : title.toString().trim();
        String bodyText = buildBodyText(bigText, text, textLines);

        StringBuilder fullTextBuilder = new StringBuilder();
        if (!appName.isEmpty()) {
            fullTextBuilder.append(appName).append(" ");
        }
        if (!titleText.isEmpty()) {
            fullTextBuilder.append(titleText);
        }
        if (!bodyText.isEmpty()) {
            if (fullTextBuilder.length() > 0) {
                fullTextBuilder.append(": ");
            }
            fullTextBuilder.append(bodyText);
        }
        String captured = fullTextBuilder.toString().trim();
        if (captured.isEmpty()) {
            return;
        }

        long postedAtMillis = sbn.getPostTime() > 0L ? sbn.getPostTime() : System.currentTimeMillis();
        String entryKey = buildEntryKey(sourcePackage, captured, postedAtMillis);
        sharedPreferences(this)
            .edit()
            .putString(KEY_LATEST_TEXT, captured)
            .putLong(KEY_LATEST_TIME, postedAtMillis)
            .putString(KEY_LATEST_PACKAGE, sourcePackage == null ? "" : sourcePackage)
            .putString(KEY_LATEST_TITLE, titleText)
            .putString(KEY_LATEST_BODY, bodyText)
            .putString(KEY_LATEST_APP_NAME, appName)
            .putString(KEY_LATEST_KEY, entryKey)
            .apply();

        scheduleAutoImport(entryKey, sourcePackage, appName, captured, postedAtMillis);
    }

    @Override
    public void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    public static String getLatestNotificationText(Context context) {
        if (context == null) {
            return null;
        }
        return sharedPreferences(context).getString(KEY_LATEST_TEXT, null);
    }

    public static CapturedNotification getLatestCapturedNotification(Context context) {
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = sharedPreferences(context);
        String text = prefs.getString(KEY_LATEST_TEXT, null);
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return new CapturedNotification(
            prefs.getString(KEY_LATEST_KEY, ""),
            prefs.getString(KEY_LATEST_PACKAGE, ""),
            prefs.getString(KEY_LATEST_TITLE, ""),
            prefs.getString(KEY_LATEST_BODY, ""),
            prefs.getString(KEY_LATEST_APP_NAME, ""),
            text,
            prefs.getLong(KEY_LATEST_TIME, 0L)
        );
    }

    public static String getLatestNotificationKey(Context context) {
        if (context == null) {
            return "";
        }
        return sharedPreferences(context).getString(KEY_LATEST_KEY, "");
    }

    public static void clearLatestNotification(Context context) {
        if (context == null) {
            return;
        }
        sharedPreferences(context)
            .edit()
            .clear()
            .apply();
    }

    private static SharedPreferences sharedPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String buildEntryKey(String sourcePackage, String text, long timestampMillis) {
        String pkg = sourcePackage == null ? "" : sourcePackage;
        String content = text == null ? "" : text;
        return pkg + "|" + timestampMillis + "|" + content.hashCode();
    }

    private static String buildBodyText(CharSequence bigText, CharSequence text, CharSequence[] textLines) {
        StringBuilder bodyBuilder = new StringBuilder();
        appendUnique(bodyBuilder, bigText);
        appendUnique(bodyBuilder, text);
        if (textLines != null) {
            for (CharSequence line : textLines) {
                appendUnique(bodyBuilder, line);
            }
        }
        return bodyBuilder.toString().trim();
    }

    private static void appendUnique(StringBuilder builder, CharSequence raw) {
        if (builder == null || raw == null) {
            return;
        }
        String value = raw.toString().trim();
        if (value.isEmpty()) {
            return;
        }
        String current = builder.toString();
        if (!current.isEmpty() && current.contains(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" | ");
        }
        builder.append(value);
    }

    private String resolveAppName(String sourcePackage) {
        if (sourcePackage == null || sourcePackage.trim().isEmpty()) {
            return "";
        }
        try {
            PackageManager packageManager = getPackageManager();
            CharSequence label = packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sourcePackage, 0)
            );
            return label == null ? "" : label.toString().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void scheduleAutoImport(
        String entryKey,
        String sourcePackage,
        String appName,
        String captured,
        long postedAtMillis
    ) {
        SharedPreferences autoPrefs = autoEntryPreferences(this);
        boolean vcbEnabled = autoPrefs.getBoolean(KEY_ENABLE_VCB, false);
        boolean vietinEnabled = autoPrefs.getBoolean(KEY_ENABLE_VIETIN, false);
        boolean mbEnabled = autoPrefs.getBoolean(KEY_ENABLE_MBBANK, false);
        boolean bidvEnabled = autoPrefs.getBoolean(KEY_ENABLE_BIDV, false);
        if (!vcbEnabled && !vietinEnabled && !mbEnabled && !bidvEnabled) {
            return;
        }
        String lastProcessed = autoPrefs.getString(KEY_LAST_PROCESSED_CAPTURE, "");
        if (entryKey != null && !entryKey.isEmpty() && entryKey.equals(lastProcessed)) {
            return;
        }
        ioExecutor.execute(() -> processAutoImport(entryKey, sourcePackage, appName, captured, postedAtMillis, vcbEnabled, vietinEnabled, mbEnabled, bidvEnabled));
    }

    private void processAutoImport(
        String entryKey,
        String sourcePackage,
        String appName,
        String captured,
        long postedAtMillis,
        boolean vcbEnabled,
        boolean vietinEnabled,
        boolean mbEnabled,
        boolean bidvEnabled
    ) {
        NotificationDraft draft = null;
        if (mbEnabled) {
            draft = FinanceParsersKt.parseMbBankNotificationText(captured, sourcePackage, appName);
        }
        if (draft == null && bidvEnabled) {
            draft = FinanceParsersKt.parseBidvNotificationText(captured, sourcePackage, appName);
        }
        if (draft == null && vcbEnabled) {
            draft = FinanceParsersKt.parseVietcombankNotificationText(captured, sourcePackage, appName);
        }
        if (draft == null && vietinEnabled) {
            draft = FinanceParsersKt.parseVietinBankNotificationText(captured, sourcePackage, appName);
        }
        if (draft == null) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getUid() == null || user.getUid().trim().isEmpty()) {
            return;
        }
        String userId = user.getUid();

        List<Wallet> wallets = loadWallets(userId);
        Wallet targetWallet = resolveWalletForDraft(wallets, draft);
        if (targetWallet == null) {
            return;
        }

        Timestamp createdAt = null;
        if (draft.getTransactionTimestampMillis() > 0L) {
            createdAt = new Timestamp(new Date(draft.getTransactionTimestampMillis()));
        } else if (postedAtMillis > 0L) {
            createdAt = new Timestamp(new Date(postedAtMillis));
        }

        try {
            repository.addTransaction(
                userId,
                targetWallet.getId(),
                draft.getType(),
                draft.getAmount(),
                draft.getCategory(),
                draft.getNote(),
                null,
                createdAt
            );
            if (entryKey != null && !entryKey.trim().isEmpty()) {
                autoEntryPreferences(this).edit().putString(KEY_LAST_PROCESSED_CAPTURE, entryKey).apply();
            }
        } catch (Exception ignored) {
        }
    }

    private List<Wallet> loadWallets(String userId) {
        try {
            return repository.getWallets(userId, Source.SERVER);
        } catch (Exception serverError) {
            try {
                return repository.getWallets(userId, Source.CACHE);
            } catch (Exception cacheError) {
                return new ArrayList<>();
            }
        }
    }

    private Wallet resolveWalletForDraft(List<Wallet> wallets, NotificationDraft draft) {
        if (wallets == null || wallets.isEmpty() || draft == null) {
            return null;
        }
        String sourceName = FinanceParsersKt.normalizeToken(draft.getSourceName());
        String[] bankKeywords;
        if (sourceName.contains("bidv") || sourceName.contains("smartbanking")) {
            bankKeywords = new String[] {"bidv", "smartbanking"};
        } else if (sourceName.contains("vietinbank") || sourceName.contains("vietin")) {
            bankKeywords = new String[] {"vietinbank", "vietin", "ctg"};
        } else if (sourceName.contains("vietcombank") || sourceName.contains("vcb")) {
            bankKeywords = new String[] {"vietcombank", "vcb"};
        } else {
            bankKeywords = new String[] {"mbbank", "mb"};
        }
        String hint = FinanceParsersKt.normalizeToken(draft.getWalletHint());
        Wallet fallbackBank = null;
        for (Wallet wallet : wallets) {
            if (wallet == null || wallet.isLocked()) {
                continue;
            }
            String type = wallet.getAccountType() == null ? "" : wallet.getAccountType().trim().toUpperCase(Locale.ROOT);
            if (!"BANK".equals(type) && !"NGAN_HANG".equals(type)) {
                continue;
            }
            String provider = FinanceParsersKt.normalizeToken(wallet.getProviderName());
            String name = FinanceParsersKt.normalizeToken(wallet.getName());
            String note = FinanceParsersKt.normalizeToken(wallet.getNote());
            boolean providerMatched = containsAny(provider, bankKeywords)
                || containsAny(name, bankKeywords)
                || containsAny(note, bankKeywords);
            if (!providerMatched) {
                continue;
            }
            if (!hint.isEmpty() && (name.contains(hint) || provider.contains(hint) || note.contains(hint))) {
                return wallet;
            }
            if (fallbackBank == null) {
                fallbackBank = wallet;
            }
        }
        if (fallbackBank != null) {
            return fallbackBank;
        }
        for (Wallet wallet : wallets) {
            if (wallet != null && !wallet.isLocked()) {
                return wallet;
            }
        }
        return null;
    }

    private boolean containsAny(String text, String[] keywords) {
        if (text == null || text.isEmpty() || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            String token = keyword == null ? "" : keyword.trim();
            if (!token.isEmpty() && text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static SharedPreferences autoEntryPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_AUTO_ENTRY, Context.MODE_PRIVATE);
    }

    public static final class CapturedNotification {
        private final String key;
        private final String sourcePackage;
        private final String title;
        private final String body;
        private final String appName;
        private final String fullText;
        private final long postedAtMillis;

        public CapturedNotification(
            String key,
            String sourcePackage,
            String title,
            String body,
            String appName,
            String fullText,
            long postedAtMillis
        ) {
            this.key = key == null ? "" : key;
            this.sourcePackage = sourcePackage == null ? "" : sourcePackage;
            this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
            this.appName = appName == null ? "" : appName;
            this.fullText = fullText == null ? "" : fullText;
            this.postedAtMillis = Math.max(0L, postedAtMillis);
        }

        public String getKey() {
            return key;
        }

        public String getSourcePackage() {
            return sourcePackage;
        }

        public String getTitle() {
            return title;
        }

        public String getBody() {
            return body;
        }

        public String getAppName() {
            return appName;
        }

        public String getFullText() {
            return fullText;
        }

        public long getPostedAtMillis() {
            return postedAtMillis;
        }
    }
}
