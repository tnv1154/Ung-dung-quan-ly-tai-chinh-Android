package com.example.myapplication.xmlui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceParsersKt;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.NotificationDraft;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AutoEntryActivity extends AppCompatActivity {
    private static final String PREF_AUTO_ENTRY = "auto_entry_settings";
    private static final String KEY_ENABLE_VCB = "enable_vcb";
    private static final String KEY_ENABLE_VIETIN = "enable_vietin";
    private static final String KEY_ENABLE_MBBANK = "enable_mbbank";
    private static final String KEY_ENABLE_BIDV = "enable_bidv";
    private static final String KEY_ENABLE_MOMO = "enable_momo";
    private static final String KEY_ENABLE_ZALOPAY = "enable_zalopay";
    private static final String KEY_SWITCH_DEFAULTS_INITIALIZED = "switch_defaults_initialized";
    private static final String KEY_LAST_PROCESSED_CAPTURE = "last_processed_capture";
    private static final long POLL_INTERVAL_MILLIS = 1800L;

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private FinanceUiState latestFinanceState;
    private String observedUserId;

    private SharedPreferences preferences;
    private TextView tvPermissionStatus;
    private TextView tvHint;
    private SwitchCompat switchVcb;
    private SwitchCompat switchVietin;
    private SwitchCompat switchMb;
    private SwitchCompat switchBidv;
    private SwitchCompat switchMomo;
    private SwitchCompat switchZaloPay;

    private AlertDialog activeDetectedDialog;
    private String lastSeenCaptureKey = "";
    private boolean suppressSwitchCallbacks = false;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                maybeHandleLatestNotification();
                pollHandler.postDelayed(this, POLL_INTERVAL_MILLIS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_entry);
        preferences = getSharedPreferences(PREF_AUTO_ENTRY, MODE_PRIVATE);
        bindViews();
        setupTopBar();
        setupBottomNavigation();
        setupSwitches();
        setupSession();
        updateNotificationAccessStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        pollHandler.post(pollRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationAccessStatus();
        maybeHandleLatestNotification();
    }

    @Override
    protected void onStop() {
        super.onStop();
        pollHandler.removeCallbacks(pollRunnable);
        if (activeDetectedDialog != null && activeDetectedDialog.isShowing()) {
            activeDetectedDialog.dismiss();
        }
    }

    private void bindViews() {
        tvPermissionStatus = findViewById(R.id.tvAutoEntryPermissionStatus);
        tvHint = findViewById(R.id.tvAutoEntryHint);
        switchVcb = findViewById(R.id.switchAutoEntryVcb);
        switchVietin = findViewById(R.id.switchAutoEntryVietin);
        switchMb = findViewById(R.id.switchAutoEntryMb);
        switchBidv = findViewById(R.id.switchAutoEntryBidv);
        switchMomo = findViewById(R.id.switchAutoEntryMomo);
        switchZaloPay = findViewById(R.id.switchAutoEntryZaloPay);
    }

    private void setupTopBar() {
        ImageButton btnBack = findViewById(R.id.btnAutoEntryBack);
        MaterialButton btnGrantPermission = findViewById(R.id.btnAutoEntryGrantPermission);
        btnBack.setOnClickListener(v -> finish());
        btnGrantPermission.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_more);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_overview) {
                startActivity(new Intent(this, OverviewActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_accounts) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_add) {
                Intent addIntent = new Intent(this, AddTransactionActivity.class);
                addIntent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_EXPENSE);
                startActivity(addIntent);
                return false;
            }
            if (id == R.id.nav_report) {
                startActivity(new Intent(this, ReportActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_more) {
                startActivity(new Intent(this, MoreActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupSwitches() {
        if (!preferences.getBoolean(KEY_SWITCH_DEFAULTS_INITIALIZED, false)) {
            preferences.edit()
                .putBoolean(KEY_ENABLE_VCB, false)
                .putBoolean(KEY_ENABLE_VIETIN, false)
                .putBoolean(KEY_ENABLE_MBBANK, false)
                .putBoolean(KEY_ENABLE_BIDV, false)
                .putBoolean(KEY_ENABLE_MOMO, false)
                .putBoolean(KEY_ENABLE_ZALOPAY, false)
                .putBoolean(KEY_SWITCH_DEFAULTS_INITIALIZED, true)
                .apply();
        }
        preferences.edit()
            .putBoolean(KEY_ENABLE_MOMO, false)
            .putBoolean(KEY_ENABLE_ZALOPAY, false)
            .apply();

        suppressSwitchCallbacks = true;
        switchVcb.setChecked(preferences.getBoolean(KEY_ENABLE_VCB, false));
        switchVietin.setChecked(preferences.getBoolean(KEY_ENABLE_VIETIN, false));
        switchMb.setChecked(preferences.getBoolean(KEY_ENABLE_MBBANK, false));
        switchBidv.setChecked(preferences.getBoolean(KEY_ENABLE_BIDV, false));
        switchMomo.setChecked(false);
        switchZaloPay.setChecked(false);
        suppressSwitchCallbacks = false;

        switchMomo.setEnabled(false);
        switchZaloPay.setEnabled(false);
        switchMomo.setClickable(false);
        switchZaloPay.setClickable(false);
        switchMomo.setAlpha(0.55f);
        switchZaloPay.setAlpha(0.55f);

        switchVcb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallbacks) {
                return;
            }
            preferences.edit().putBoolean(KEY_ENABLE_VCB, isChecked).apply();
        });
        switchVietin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallbacks) {
                return;
            }
            preferences.edit().putBoolean(KEY_ENABLE_VIETIN, isChecked).apply();
        });
        switchMb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallbacks) {
                return;
            }
            preferences.edit().putBoolean(KEY_ENABLE_MBBANK, isChecked).apply();
        });
        switchBidv.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallbacks) {
                return;
            }
            preferences.edit().putBoolean(KEY_ENABLE_BIDV, isChecked).apply();
        });
    }

    private void setupSession() {
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        sessionViewModel.getUiStateLiveData().observe(this, this::renderSessionState);
    }

    private void renderSessionState(@NonNull SessionUiState state) {
        if (state.getCurrentUser() == null) {
            goToAuth();
            return;
        }
        String userId = state.getCurrentUser().getUid();
        if (observedUserId != null && observedUserId.equals(userId)) {
            return;
        }
        observedUserId = userId;
        FinanceViewModelFactory factory = new FinanceViewModelFactory(new FirestoreFinanceRepository(), userId);
        financeViewModel = new ViewModelProvider(this, factory).get(FinanceViewModel.class);
        financeViewModel.getUiStateLiveData().observe(this, this::renderFinanceState);
    }

    private void renderFinanceState(@NonNull FinanceUiState state) {
        latestFinanceState = state;
        String error = state.getErrorMessage();
        if (error != null && !error.trim().isEmpty()) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            if (financeViewModel != null) {
                financeViewModel.clearError();
            }
        }
    }

    private void updateNotificationAccessStatus() {
        boolean enabled = isNotificationListenerEnabled();
        tvPermissionStatus.setText(enabled ? R.string.notification_access_enabled : R.string.notification_access_disabled);
        tvPermissionStatus.setTextColor(getColor(enabled ? R.color.income_green : R.color.warning_orange));
    }

    private boolean isNotificationListenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        String packageName = getPackageName();
        String[] components = enabled.split(":");
        for (String flat : components) {
            ComponentName componentName = ComponentName.unflattenFromString(flat);
            if (componentName != null && packageName.equals(componentName.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private void maybeHandleLatestNotification() {
        if (!isNotificationListenerEnabled()) {
            return;
        }
        boolean vcbEnabled = preferences.getBoolean(KEY_ENABLE_VCB, false);
        boolean vietinEnabled = preferences.getBoolean(KEY_ENABLE_VIETIN, false);
        boolean mbEnabled = preferences.getBoolean(KEY_ENABLE_MBBANK, false);
        boolean bidvEnabled = preferences.getBoolean(KEY_ENABLE_BIDV, false);
        if (!vcbEnabled && !vietinEnabled && !mbEnabled && !bidvEnabled) {
            return;
        }
        NotificationCaptureService.CapturedNotification capture =
            NotificationCaptureService.getLatestCapturedNotification(this);
        if (capture == null) {
            return;
        }
        updateCaptureHint(capture);

        String captureKey = capture.getKey();
        if (captureKey == null || captureKey.trim().isEmpty()) {
            return;
        }
        if (captureKey.equals(lastSeenCaptureKey)) {
            return;
        }
        String lastProcessed = preferences.getString(KEY_LAST_PROCESSED_CAPTURE, "");
        if (captureKey.equals(lastProcessed)) {
            lastSeenCaptureKey = captureKey;
            return;
        }

        NotificationDraft draft = null;
        if (mbEnabled) {
            draft = FinanceParsersKt.parseMbBankNotificationText(
                capture.getFullText(),
                capture.getSourcePackage(),
                capture.getAppName()
            );
        }
        if (draft == null && bidvEnabled) {
            draft = FinanceParsersKt.parseBidvNotificationText(
                capture.getFullText(),
                capture.getSourcePackage(),
                capture.getAppName()
            );
        }
        if (draft == null && vcbEnabled) {
            draft = FinanceParsersKt.parseVietcombankNotificationText(
                capture.getFullText(),
                capture.getSourcePackage(),
                capture.getAppName()
            );
        }
        if (draft == null && vietinEnabled) {
            draft = FinanceParsersKt.parseVietinBankNotificationText(
                capture.getFullText(),
                capture.getSourcePackage(),
                capture.getAppName()
            );
        }
        if (draft == null) {
            lastSeenCaptureKey = captureKey;
            tvHint.setText(getString(R.string.auto_entry_mb_parse_failed_hint));
            return;
        }
        if (activeDetectedDialog != null && activeDetectedDialog.isShowing()) {
            return;
        }
        lastSeenCaptureKey = captureKey;
        showDetectedTransactionDialog(captureKey, draft);
    }

    private void updateCaptureHint(NotificationCaptureService.CapturedNotification capture) {
        if (capture == null) {
            return;
        }
        String source = capture.getAppName();
        if (source == null || source.trim().isEmpty()) {
            source = capture.getSourcePackage();
        }
        if (source == null || source.trim().isEmpty()) {
            source = getString(R.string.label_more_auto_entry);
        }
        String time = formatDetectedTime(capture.getPostedAtMillis());
        tvHint.setText(getString(R.string.auto_entry_last_capture_hint, source, time));
    }

    private void showDetectedTransactionDialog(String captureKey, NotificationDraft draft) {
        Wallet resolvedWallet = resolveWalletForDraft(draft);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_auto_entry_detected, null, false);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvAutoDetectedSubtitle);
        TextView tvAmount = dialogView.findViewById(R.id.tvAutoDetectedAmount);
        TextView tvSourceWallet = dialogView.findViewById(R.id.tvAutoDetectedSourceWallet);
        TextView tvCategory = dialogView.findViewById(R.id.tvAutoDetectedCategory);
        TextView tvTime = dialogView.findViewById(R.id.tvAutoDetectedTime);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnAutoDetectedConfirm);
        MaterialButton btnEdit = dialogView.findViewById(R.id.btnAutoDetectedEdit);

        tvSubtitle.setText(getString(R.string.auto_entry_detected_subtitle, draft.getSourceName()));
        String amountPrefix = draft.getType() == TransactionType.EXPENSE ? "-" : "+";
        tvAmount.setText(amountPrefix + UiFormatters.money(draft.getAmount()));
        tvAmount.setTextColor(getColor(draft.getType() == TransactionType.EXPENSE ? R.color.error_red : R.color.income_green));
        tvSourceWallet.setText(resolvedWallet == null
            ? getString(R.string.auto_entry_wallet_unresolved)
            : resolvedWallet.getName());
        tvCategory.setText(draft.getCategory());
        tvTime.setText(formatDetectedTime(draft.getTransactionTimestampMillis()));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> {
            if (activeDetectedDialog == dialog) {
                activeDetectedDialog = null;
            }
        });

        btnConfirm.setOnClickListener(v -> {
            Wallet wallet = resolvedWallet == null ? fallbackWalletForAutoEntry() : resolvedWallet;
            if (wallet == null) {
                Toast.makeText(this, R.string.auto_entry_wallet_required, Toast.LENGTH_SHORT).show();
                return;
            }
            markProcessedCapture(captureKey);
            saveDraftTransaction(draft, wallet);
            dialog.dismiss();
        });

        btnEdit.setOnClickListener(v -> {
            openDraftInAddScreen(draft, resolvedWallet);
            markProcessedCapture(captureKey);
            dialog.dismiss();
        });

        activeDetectedDialog = dialog;
        dialog.show();
    }

    private String formatDetectedTime(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return getString(R.string.auto_entry_time_just_now);
        }
        long delta = Math.abs(System.currentTimeMillis() - timestampMillis);
        if (delta < 120_000L) {
            return getString(R.string.auto_entry_time_just_now);
        }
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(timestampMillis));
    }

    private void saveDraftTransaction(NotificationDraft draft, Wallet wallet) {
        if (financeViewModel == null) {
            Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT).show();
            return;
        }
        Timestamp createdAt = null;
        if (draft.getTransactionTimestampMillis() > 0L) {
            createdAt = new Timestamp(new Date(draft.getTransactionTimestampMillis()));
        }
        financeViewModel.addTransaction(
            wallet.getId(),
            draft.getType(),
            draft.getAmount(),
            draft.getCategory(),
            draft.getNote(),
            null,
            createdAt,
            errorMessage -> runOnUiThread(() -> {
                if (errorMessage == null || errorMessage.trim().isEmpty()) {
                    Toast.makeText(this, R.string.auto_entry_saved_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                }
            })
        );
    }

    private void openDraftInAddScreen(NotificationDraft draft, Wallet wallet) {
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra(
            AddTransactionActivity.EXTRA_PREFILL_MODE,
            draft.getType() == TransactionType.INCOME
                ? AddTransactionActivity.MODE_INCOME
                : AddTransactionActivity.MODE_EXPENSE
        );
        if (wallet != null) {
            intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_SOURCE_WALLET_ID, wallet.getId());
        }
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_AMOUNT, draft.getAmount());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_NOTE, draft.getNote());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_CATEGORY_NAME, draft.getCategory());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_TIME_MILLIS, draft.getTransactionTimestampMillis());
        startActivity(intent);
    }

    private void markProcessedCapture(String captureKey) {
        if (captureKey == null || captureKey.trim().isEmpty()) {
            return;
        }
        preferences.edit().putString(KEY_LAST_PROCESSED_CAPTURE, captureKey).apply();
    }

    private Wallet resolveWalletForDraft(NotificationDraft draft) {
        if (latestFinanceState == null) {
            return null;
        }
        List<Wallet> candidates = unlockedWallets(latestFinanceState.getWallets());
        if (candidates.isEmpty()) {
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
        for (Wallet wallet : candidates) {
            String type = WalletUiMapper.normalizeAccountType(wallet.getAccountType());
            if (!"BANK".equals(type)) {
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
        for (Wallet wallet : candidates) {
            if ("BANK".equals(WalletUiMapper.normalizeAccountType(wallet.getAccountType()))) {
                return wallet;
            }
        }
        return candidates.get(0);
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

    private Wallet fallbackWalletForAutoEntry() {
        if (latestFinanceState == null) {
            return null;
        }
        List<Wallet> wallets = unlockedWallets(latestFinanceState.getWallets());
        if (wallets.isEmpty()) {
            return null;
        }
        return wallets.get(0);
    }

    private List<Wallet> unlockedWallets(List<Wallet> source) {
        List<Wallet> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Wallet wallet : source) {
            if (wallet != null && !wallet.isLocked()) {
                result.add(wallet);
            }
        }
        return result;
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
