package com.example.myapplication.xmlui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.CsvImportRow;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.UserSettings;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.CsvParseResult;
import com.example.myapplication.finance.ui.ExportPeriod;
import com.example.myapplication.finance.ui.FinanceParsersKt;
import com.example.myapplication.finance.ui.FinanceTimeAndIoKt;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.NotificationDraft;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class MoreActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;

    private TextView tvDisplayName;
    private TextView tvAvatar;
    private TextView tvEmail;
    private TextView tvError;
    private TextView tvAutoPreview;
    private TextView tvNotificationStatus;
    private TextInputEditText etAutoText;
    private MaterialButton btnAutoWallet;
    private Wallet selectedAutoWallet;
    private NotificationDraft autoDraft;

    private static final int REQUEST_PICK_IMPORT = 1001;
    private static final int REQUEST_PICK_EXPORT = 1002;
    private static final int REQUEST_POST_NOTIFICATIONS = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more);
        bindViews();
        setupBottomNavigation();
        setupActions();
        setupSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationAccessStatus();
    }

    private void bindViews() {
        tvDisplayName = findViewById(R.id.tvMoreDisplayName);
        tvAvatar = findViewById(R.id.tvMoreAvatar);
        tvEmail = findViewById(R.id.tvMoreEmail);
        tvError = findViewById(R.id.tvMoreError);
        tvAutoPreview = findViewById(R.id.tvAutoEntryPreview);
        tvNotificationStatus = findViewById(R.id.tvNotificationAccessStatus);
        etAutoText = findViewById(R.id.etAutoEntryText);
        btnAutoWallet = findViewById(R.id.btnAutoEntryWallet);
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
            return id == R.id.nav_more;
        });
    }

    private void setupActions() {
        View btnNotify = findViewById(R.id.btnMoreNotifications);
        View btnCategories = findViewById(R.id.btnMoreCategories);
        View btnBudgets = findViewById(R.id.btnMoreBudgets);
        View btnSettings = findViewById(R.id.btnMoreSettings);
        View btnDataSettings = findViewById(R.id.btnMoreDataSettings);
        View btnImport = findViewById(R.id.btnMoreImport);
        View btnExport = findViewById(R.id.btnMoreExport);
        View btnCurrencyConverter = findViewById(R.id.btnMoreCurrencyConverter);
        View btnAutoEntry = findViewById(R.id.btnMoreAutoEntry);
        View autoEntryPanel = findViewById(R.id.layoutAutoEntryPanel);
        MaterialButton btnOpenNotificationAccess = findViewById(R.id.btnOpenNotificationAccess);
        MaterialButton btnLoadLatestNotification = findViewById(R.id.btnLoadLatestNotification);
        MaterialButton btnAutoParse = findViewById(R.id.btnAutoEntryParse);
        MaterialButton btnAutoSave = findViewById(R.id.btnAutoEntrySave);
        MaterialButton btnSignOut = findViewById(R.id.btnMoreSignOut);

        btnNotify.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            intent.putExtra(NotificationsActivity.EXTRA_SOURCE_NAV_ITEM_ID, R.id.nav_more);
            startActivity(intent);
        });
        btnCategories.setOnClickListener(v -> startActivity(new Intent(this, CategoryActivity.class)));
        btnBudgets.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        btnDataSettings.setOnClickListener(v -> startActivity(new Intent(this, DataSettingsActivity.class)));
        btnImport.setOnClickListener(v -> startActivity(new Intent(this, CsvImportActivity.class)));
        btnExport.setOnClickListener(v -> showExportOptionsDialog());
        btnCurrencyConverter.setOnClickListener(v -> startActivity(new Intent(this, CurrencyConverterActivity.class)));
        btnAutoEntry.setOnClickListener(v -> {
            if (autoEntryPanel.getVisibility() == View.VISIBLE) {
                autoEntryPanel.setVisibility(View.GONE);
            } else {
                autoEntryPanel.setVisibility(View.VISIBLE);
            }
        });
        btnOpenNotificationAccess.setOnClickListener(v -> openNotificationAccessSettings());
        btnLoadLatestNotification.setOnClickListener(v -> loadLatestCapturedNotification());
        btnAutoWallet.setOnClickListener(v -> chooseAutoWallet());
        btnAutoParse.setOnClickListener(v -> parseAutoEntry());
        btnAutoSave.setOnClickListener(v -> saveAutoEntry());
        btnSignOut.setOnClickListener(v -> showSignOutDialog());
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
        String email = state.getCurrentUser().getEmail() == null ? "" : state.getCurrentUser().getEmail();
        String displayName = state.getCurrentUser().getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = email.isEmpty()
                ? getString(R.string.label_more_user)
                : email.substring(0, email.indexOf("@") > 0 ? email.indexOf("@") : email.length());
        }
        tvDisplayName.setText(displayName);
        tvEmail.setText(email);
        tvAvatar.setText(displayName.substring(0, 1).toUpperCase(Locale.ROOT));

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
        latestState = state;
        ReminderScheduler.syncFromSettings(this, state.getSettings());
        BudgetAlertNotifier.maybeNotifyExceeded(this, state);
        if (selectedAutoWallet == null && !state.getWallets().isEmpty()) {
            selectedAutoWallet = state.getWallets().get(0);
        } else if (selectedAutoWallet != null) {
            selectedAutoWallet = findWalletById(state.getWallets(), selectedAutoWallet.getId());
        }
        btnAutoWallet.setText(selectedAutoWallet == null
            ? getString(R.string.action_choose_wallet_short)
            : selectedAutoWallet.getName());

        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            showError(state.getErrorMessage());
            financeViewModel.clearError();
        }
    }

    private void showEditProfileDialog() {
        if (sessionViewModel == null) {
            return;
        }
        EditText input = new EditText(this);
        input.setHint(R.string.hint_display_name);
        input.setText(tvDisplayName.getText());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_edit_profile_title)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, (dialog, which) -> {
                String newName = input.getText() == null ? "" : input.getText().toString().trim();
                sessionViewModel.updateDisplayName(newName, error -> {
                    runOnUiThread(() -> {
                        if (error == null) {
                            Toast.makeText(this, R.string.message_profile_updated, Toast.LENGTH_SHORT).show();
                        } else {
                            showError(error);
                        }
                    });
                });
            })
            .show();
    }

    private void showChangePasswordDialog() {
        if (sessionViewModel == null) {
            return;
        }
        EditText passwordInput = new EditText(this);
        passwordInput.setHint(R.string.hint_new_password);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirmInput = new EditText(this);
        confirmInput.setHint(R.string.hint_confirm_password);
        confirmInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);
        container.addView(passwordInput);
        container.addView(confirmInput);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_change_password_title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, (dialog, which) -> {
                String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString();
                String confirm = confirmInput.getText() == null ? "" : confirmInput.getText().toString();
                if (!password.equals(confirm)) {
                    showError(getString(R.string.error_password_mismatch));
                    return;
                }
                sessionViewModel.updatePassword(password, error -> {
                    runOnUiThread(() -> {
                        if (error == null) {
                            Toast.makeText(this, R.string.message_password_updated, Toast.LENGTH_SHORT).show();
                        } else {
                            showError(error);
                        }
                    });
                });
            })
            .show();
    }

    private void showSettingsDialog() {
        if (financeViewModel == null || latestState == null) {
            return;
        }
        EditText currencyInput = new EditText(this);
        currencyInput.setHint(R.string.hint_currency);
        currencyInput.setText(latestState.getSettings().getCurrency());

        android.widget.CheckBox warningCheckbox = new android.widget.CheckBox(this);
        warningCheckbox.setText(R.string.label_show_budget_warning);
        warningCheckbox.setChecked(latestState.getSettings().getShowBudgetWarnings());

        android.widget.CheckBox compactCheckbox = new android.widget.CheckBox(this);
        compactCheckbox.setText(R.string.label_compact_number);
        compactCheckbox.setChecked(latestState.getSettings().getCompactNumberFormat());

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);
        container.addView(currencyInput);
        container.addView(warningCheckbox);
        container.addView(compactCheckbox);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_settings_title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.action_notification_settings, (dialog, which) -> showNotificationSettingsDialog())
            .setPositiveButton(R.string.action_save, (dialog, which) -> {
                String currency = currencyInput.getText() == null ? "" : currencyInput.getText().toString().trim();
                if (currency.isEmpty()) {
                    currency = "VND";
                }
                UserSettings current = latestState.getSettings();
                financeViewModel.updateSettings(
                    currency,
                    warningCheckbox.isChecked(),
                    compactCheckbox.isChecked(),
                    current.getReminderEnabled(),
                    current.getReminderFrequency(),
                    current.getReminderHour(),
                    current.getReminderMinute(),
                    current.getReminderWeekdays()
                );
                Toast.makeText(this, R.string.message_settings_updated, Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showNotificationSettingsDialog() {
        if (financeViewModel == null || latestState == null) {
            showError(getString(R.string.error_unknown));
            return;
        }
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_notification_settings, null, false);
        CheckBox cbBudget = dialogView.findViewById(R.id.cbNotificationBudgetExceeded);
        CheckBox cbReminder = dialogView.findViewById(R.id.cbNotificationReminderEnabled);
        RadioGroup rgReminderMode = dialogView.findViewById(R.id.rgNotificationReminderMode);
        RadioButton rbDaily = dialogView.findViewById(R.id.rbNotificationReminderDaily);
        RadioButton rbWeekly = dialogView.findViewById(R.id.rbNotificationReminderWeekly);
        MaterialButton btnReminderTime = dialogView.findViewById(R.id.btnNotificationReminderTime);
        MaterialButton btnReminderDays = dialogView.findViewById(R.id.btnNotificationReminderDays);
        TextView tvReminderHint = dialogView.findViewById(R.id.tvNotificationReminderHint);

        UserSettings settings = latestState.getSettings();
        final int[] reminderHour = {settings.getReminderHour()};
        final int[] reminderMinute = {settings.getReminderMinute()};
        final List<Integer> reminderDays = new ArrayList<>(settings.getReminderWeekdays());
        Collections.sort(reminderDays);

        cbBudget.setChecked(settings.getShowBudgetWarnings());
        cbReminder.setChecked(settings.getReminderEnabled());
        if (UserSettings.REMINDER_FREQUENCY_WEEKLY.equals(settings.getReminderFrequency())) {
            rbWeekly.setChecked(true);
        } else {
            rbDaily.setChecked(true);
        }
        btnReminderTime.setText(formatReminderTime(reminderHour[0], reminderMinute[0]));
        btnReminderDays.setText(formatReminderWeekdays(reminderDays));
        updateReminderUiState(cbReminder, rgReminderMode, rbWeekly, btnReminderTime, btnReminderDays, tvReminderHint);

        cbReminder.setOnCheckedChangeListener((buttonView, isChecked) ->
            updateReminderUiState(cbReminder, rgReminderMode, rbWeekly, btnReminderTime, btnReminderDays, tvReminderHint)
        );
        rgReminderMode.setOnCheckedChangeListener((group, checkedId) ->
            updateReminderUiState(cbReminder, rgReminderMode, rbWeekly, btnReminderTime, btnReminderDays, tvReminderHint)
        );
        btnReminderTime.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    reminderHour[0] = hourOfDay;
                    reminderMinute[0] = minute;
                    btnReminderTime.setText(formatReminderTime(hourOfDay, minute));
                },
                reminderHour[0],
                reminderMinute[0],
                true
            );
            picker.show();
        });
        btnReminderDays.setOnClickListener(v -> openReminderWeekdayPicker(reminderDays, btnReminderDays));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_notification_settings_title)
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create();
        dialog.setOnShowListener(dialogInterface -> {
            View saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                boolean reminderEnabled = cbReminder.isChecked();
                boolean weekly = rbWeekly.isChecked();
                if (reminderEnabled && weekly && reminderDays.isEmpty()) {
                    showError(getString(R.string.error_reminder_days_required));
                    return;
                }
                requestNotificationPermissionIfNeeded();
                String reminderFrequency = weekly
                    ? UserSettings.REMINDER_FREQUENCY_WEEKLY
                    : UserSettings.REMINDER_FREQUENCY_DAILY;
                List<Integer> daysToSave = new ArrayList<>(reminderDays);
                Collections.sort(daysToSave);
                UserSettings current = latestState.getSettings();
                financeViewModel.updateSettings(
                    current.getCurrency(),
                    cbBudget.isChecked(),
                    current.getCompactNumberFormat(),
                    reminderEnabled,
                    reminderFrequency,
                    reminderHour[0],
                    reminderMinute[0],
                    daysToSave
                );
                ReminderScheduler.applyConfig(
                    this,
                    reminderEnabled,
                    reminderFrequency,
                    reminderHour[0],
                    reminderMinute[0],
                    daysToSave
                );
                if (cbBudget.isChecked()) {
                    BudgetAlertNotifier.maybeNotifyExceeded(this, latestState);
                }
                tvError.setVisibility(View.GONE);
                Toast.makeText(this, R.string.message_settings_updated, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void updateReminderUiState(
        CheckBox cbReminder,
        RadioGroup rgReminderMode,
        RadioButton rbWeekly,
        MaterialButton btnReminderTime,
        MaterialButton btnReminderDays,
        TextView tvReminderHint
    ) {
        boolean reminderEnabled = cbReminder.isChecked();
        for (int i = 0; i < rgReminderMode.getChildCount(); i++) {
            rgReminderMode.getChildAt(i).setEnabled(reminderEnabled);
        }
        rgReminderMode.setVisibility(reminderEnabled ? View.VISIBLE : View.GONE);
        btnReminderTime.setEnabled(reminderEnabled);
        btnReminderTime.setVisibility(reminderEnabled ? View.VISIBLE : View.GONE);
        btnReminderDays.setEnabled(reminderEnabled && rbWeekly.isChecked());
        btnReminderDays.setVisibility(reminderEnabled && rbWeekly.isChecked() ? View.VISIBLE : View.GONE);
        tvReminderHint.setVisibility(reminderEnabled ? View.VISIBLE : View.GONE);
    }

    private void openReminderWeekdayPicker(List<Integer> selectedWeekdays, MaterialButton button) {
        int[] weekdayValues = reminderWeekdayValues();
        String[] labels = reminderWeekdayLabels();
        boolean[] checked = new boolean[weekdayValues.length];
        for (int i = 0; i < weekdayValues.length; i++) {
            checked[i] = selectedWeekdays.contains(weekdayValues[i]);
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_reminder_days_title)
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, (dialog, which) -> {
                selectedWeekdays.clear();
                for (int i = 0; i < weekdayValues.length; i++) {
                    if (checked[i]) {
                        selectedWeekdays.add(weekdayValues[i]);
                    }
                }
                Collections.sort(selectedWeekdays);
                button.setText(formatReminderWeekdays(selectedWeekdays));
            })
            .show();
    }

    private int[] reminderWeekdayValues() {
        return new int[] {
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        };
    }

    private String[] reminderWeekdayLabels() {
        return new String[] {
            getString(R.string.weekday_short_mon),
            getString(R.string.weekday_short_tue),
            getString(R.string.weekday_short_wed),
            getString(R.string.weekday_short_thu),
            getString(R.string.weekday_short_fri),
            getString(R.string.weekday_short_sat),
            getString(R.string.weekday_short_sun)
        };
    }

    private String formatReminderTime(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private String formatReminderWeekdays(List<Integer> selectedWeekdays) {
        if (selectedWeekdays == null || selectedWeekdays.isEmpty()) {
            return getString(R.string.label_reminder_days_none);
        }
        List<String> labels = new ArrayList<>();
        int[] order = reminderWeekdayValues();
        for (int day : order) {
            if (!selectedWeekdays.contains(day)) {
                continue;
            }
            switch (day) {
                case Calendar.MONDAY:
                    labels.add(getString(R.string.weekday_short_mon));
                    break;
                case Calendar.TUESDAY:
                    labels.add(getString(R.string.weekday_short_tue));
                    break;
                case Calendar.WEDNESDAY:
                    labels.add(getString(R.string.weekday_short_wed));
                    break;
                case Calendar.THURSDAY:
                    labels.add(getString(R.string.weekday_short_thu));
                    break;
                case Calendar.FRIDAY:
                    labels.add(getString(R.string.weekday_short_fri));
                    break;
                case Calendar.SATURDAY:
                    labels.add(getString(R.string.weekday_short_sat));
                    break;
                case Calendar.SUNDAY:
                    labels.add(getString(R.string.weekday_short_sun));
                    break;
                default:
                    break;
            }
        }
        if (labels.isEmpty()) {
            return getString(R.string.label_reminder_days_none);
        }
        return String.join(", ", labels);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
    }

    private void parseAutoEntry() {
        tvError.setVisibility(android.view.View.GONE);
        String raw = etAutoText.getText() == null ? "" : etAutoText.getText().toString().trim();
        if (raw.isEmpty()) {
            tvAutoPreview.setText(R.string.label_auto_entry_empty);
            return;
        }
        autoDraft = FinanceParsersKt.parseNotificationText(raw);
        if (autoDraft == null) {
            tvAutoPreview.setText(R.string.message_auto_entry_failed);
            return;
        }
        String typeLabel = autoDraft.getType() == TransactionType.INCOME
            ? getString(R.string.transaction_type_income)
            : getString(R.string.transaction_type_expense);
        tvAutoPreview.setText(
            getString(
                R.string.label_auto_entry_preview_value,
                getString(R.string.label_auto_entry_preview),
                typeLabel,
                UiFormatters.money(autoDraft.getAmount()),
                autoDraft.getCategory()
            )
        );
    }

    private void saveAutoEntry() {
        if (financeViewModel == null) {
            return;
        }
        if (selectedAutoWallet == null) {
            showError(getString(R.string.error_wallet_unavailable));
            return;
        }
        if (autoDraft == null) {
            parseAutoEntry();
            if (autoDraft == null) {
                return;
            }
        }

        String category = autoDraft.getCategory();
        if (latestState != null) {
            List<TransactionCategory> available = latestState.getCategories();
            String normalized = category.trim().toLowerCase(Locale.ROOT);
            for (TransactionCategory item : available) {
                if (item.getType() == autoDraft.getType()
                    && item.getName().trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                    category = item.getName();
                    break;
                }
            }
        }
        financeViewModel.addTransaction(
            selectedAutoWallet.getId(),
            autoDraft.getType(),
            autoDraft.getAmount(),
            category,
            autoDraft.getNote(),
            null
        );
        Toast.makeText(this, R.string.message_transaction_saved, Toast.LENGTH_SHORT).show();
    }

    private void chooseAutoWallet() {
        if (latestState == null || latestState.getWallets().isEmpty()) {
            showError(getString(R.string.error_wallet_unavailable));
            return;
        }
        List<Wallet> wallets = new ArrayList<>();
        for (Wallet wallet : latestState.getWallets()) {
            if (!wallet.isLocked()) {
                wallets.add(wallet);
            }
        }
        if (wallets.isEmpty()) {
            showError(getString(R.string.error_wallet_unavailable));
            return;
        }
        String[] names = new String[wallets.size()];
        for (int i = 0; i < wallets.size(); i++) {
            names[i] = wallets.get(i).getName() + " • " + UiFormatters.money(wallets.get(i).getBalance());
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_source_wallet)
            .setItems(names, (dialog, which) -> {
                selectedAutoWallet = wallets.get(which);
                btnAutoWallet.setText(selectedAutoWallet.getName());
                tvError.setVisibility(android.view.View.GONE);
            })
            .show();
    }

    private void openNotificationAccessSettings() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void loadLatestCapturedNotification() {
        String latest = NotificationCaptureService.getLatestNotificationText(this);
        if (latest == null || latest.trim().isEmpty()) {
            showError(getString(R.string.error_no_notification_captured));
            return;
        }
        etAutoText.setText(latest);
        parseAutoEntry();
    }

    private void updateNotificationAccessStatus() {
        boolean enabled = isNotificationListenerEnabled();
        tvNotificationStatus.setText(enabled
            ? R.string.notification_access_enabled
            : R.string.notification_access_disabled);
        tvNotificationStatus.setTextColor(getColor(enabled ? R.color.income_green : R.color.warning_orange));
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

    private void pickCsvForImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_PICK_IMPORT);
    }

    private void showExportOptionsDialog() {
        if (latestState == null || latestState.getWallets().isEmpty()) {
            showError(getString(R.string.error_wallet_unavailable));
            return;
        }

        ExportPeriod[] periods = new ExportPeriod[] {
            ExportPeriod.TODAY,
            ExportPeriod.THIS_WEEK,
            ExportPeriod.THIS_MONTH,
            ExportPeriod.THIS_QUARTER,
            ExportPeriod.ALL
        };
        String[] labels = new String[] {
            getString(R.string.label_period_day),
            getString(R.string.label_period_week),
            getString(R.string.label_period_month),
            getString(R.string.label_period_quarter),
            getString(R.string.label_period_all)
        };
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_export_period_title)
            .setItems(labels, (dialog, which) -> showWalletSelectionDialog(periods[which]))
            .show();
    }

    private void showWalletSelectionDialog(ExportPeriod period) {
        List<Wallet> wallets = latestState.getWallets();
        String[] walletNames = new String[wallets.size()];
        boolean[] checked = new boolean[wallets.size()];
        for (int i = 0; i < wallets.size(); i++) {
            walletNames[i] = wallets.get(i).getName();
            checked[i] = true;
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_export_wallet_title)
            .setMultiChoiceItems(walletNames, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_export, (dialog, which) -> {
                Set<String> selected = new LinkedHashSet<>();
                for (int i = 0; i < checked.length; i++) {
                    if (checked[i]) {
                        selected.add(wallets.get(i).getId());
                    }
                }
                if (selected.isEmpty()) {
                    showError(getString(R.string.error_export_wallet_required));
                    return;
                }
                requestExport(period, selected);
            })
            .show();
    }

    private void requestExport(ExportPeriod period, Set<String> walletIds) {
        pendingExportPeriod = period;
        pendingExportWalletIds = new LinkedHashSet<>(walletIds);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "finance-export.csv");
        startActivityForResult(intent, REQUEST_PICK_EXPORT);
    }

    private ExportPeriod pendingExportPeriod = ExportPeriod.ALL;
    private Set<String> pendingExportWalletIds = new LinkedHashSet<>();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_PICK_IMPORT) {
            importCsv(uri);
        } else if (requestCode == REQUEST_PICK_EXPORT) {
            exportCsv(uri);
        }
    }

    private void importCsv(Uri uri) {
        if (financeViewModel == null) {
            return;
        }
        String raw = FinanceTimeAndIoKt.readTextFromUri(this, uri);
        if (raw == null || raw.trim().isEmpty()) {
            showError(getString(R.string.error_unknown));
            return;
        }
        CsvParseResult parsed = FinanceParsersKt.parseCsvImportRows(raw);
        List<CsvImportRow> rows = parsed.getRows();
        if (rows.isEmpty()) {
            showError(getString(R.string.message_csv_import_ready, 0, parsed.getSkippedRows()));
            return;
        }
        financeViewModel.importCsvRows(rows);
        Toast.makeText(
            this,
            getString(R.string.message_csv_import_ready, rows.size(), parsed.getSkippedRows()),
            Toast.LENGTH_LONG
        ).show();
    }

    private void exportCsv(Uri uri) {
        if (financeViewModel == null) {
            return;
        }
        Set<String> walletIds = pendingExportWalletIds.isEmpty()
            ? buildAllWalletIds()
            : pendingExportWalletIds;
        String csv = financeViewModel.buildCsvExport(pendingExportPeriod, walletIds);
        boolean success = FinanceTimeAndIoKt.writeTextToUri(this, uri, csv);
        if (success) {
            Toast.makeText(this, R.string.message_csv_export_success, Toast.LENGTH_SHORT).show();
        } else {
            showError(getString(R.string.message_csv_export_failed));
        }
    }

    private Set<String> buildAllWalletIds() {
        Set<String> walletIds = new LinkedHashSet<>();
        if (latestState != null) {
            for (Wallet wallet : latestState.getWallets()) {
                walletIds.add(wallet.getId());
            }
        }
        return walletIds;
    }

    private void showSignOutDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_sign_out_title)
            .setMessage(R.string.dialog_sign_out_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_sign_out, (dialog, which) -> sessionViewModel.signOut())
            .show();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(android.view.View.VISIBLE);
    }

    private Wallet findWalletById(List<Wallet> wallets, String id) {
        if (id == null) {
            return null;
        }
        for (Wallet wallet : wallets) {
            if (id.equals(wallet.getId())) {
                return wallet;
            }
        }
        return null;
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
