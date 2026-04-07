package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class WalletDetailActivity extends AppCompatActivity {

    private static final String[] ACCOUNT_TYPES = new String[] {"CASH", "BANK", "EWALLET", "OTHER"};
    private static final String[] BANK_OPTIONS = new String[] {
        "MBBank", "Vietcombank", "BIDV", "Techcombank", "ACB", "VPBank"
    };
    private static final String[] EWALLET_OPTIONS = new String[] {
        "Momo", "ZaloPay", "ShopeePay", "Viettel Money"
    };

    private MaterialToolbar toolbar;
    private TextView tvTopAction;
    private TextInputEditText etHeaderBalance;
    private TextView tvTypeLabel;
    private TextView tvProviderLabel;
    private MaterialButton btnTypePicker;
    private MaterialButton btnProviderPicker;
    private MaterialButton btnSaveWallet;
    private MaterialButton btnDeleteWallet;
    private View spacerWalletBottom;
    private TextInputEditText etWalletName;
    private TextInputEditText etWalletNote;
    private View inputProvider;
    private RadioGroup rgCurrency;
    private TextView tvError;

    private String selectedType = "CASH";
    private String selectedIconKey = "cash";
    private String selectedCurrency = "VND";
    private String selectedProvider = "";
    private boolean isLocked = false;

    private FinanceViewModel financeViewModel;
    private boolean submitPending = false;
    private boolean waitingCreateWalletResult = false;
    private String waitingCreateWalletName = "";
    private String waitingCreateWalletType = "CASH";
    private double waitingCreateWalletBalance = 0.0;
    private int waitingCreateWalletCount = 0;
    private boolean isEditMode = false;
    private String editWalletId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_detail);

        resolveModeAndPrefill();
        setupViewModel();
        bindViews();
        setupActions();
        populateInitialValues();
        applyModeUi();
        updateProviderUi();
        updateHeaderBalance();
    }

    private void setupViewModel() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        FinanceViewModelFactory factory = new FinanceViewModelFactory(new FirestoreFinanceRepository(), user.getUid());
        financeViewModel = new ViewModelProvider(this, factory).get(FinanceViewModel.class);
        financeViewModel.getUiStateLiveData().observe(this, state -> {
            if (state.getErrorMessage() != null && submitPending) {
                submitPending = false;
                waitingCreateWalletResult = false;
                showTextError(state.getErrorMessage());
                financeViewModel.clearError();
            }
            if (waitingCreateWalletResult && walletCreateCompleted(state)) {
                waitingCreateWalletResult = false;
                submitPending = false;
                startActivity(new Intent(this, WalletSuccessActivity.class));
                finish();
            }
        });
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarWalletDetail);
        tvTopAction = findViewById(R.id.tvWalletTopAction);
        etHeaderBalance = findViewById(R.id.etWalletHeaderBalance);
        tvTypeLabel = findViewById(R.id.tvWalletTypeValue);
        tvProviderLabel = findViewById(R.id.tvProviderLabel);
        btnTypePicker = findViewById(R.id.btnTypePicker);
        btnProviderPicker = findViewById(R.id.btnProviderPicker);
        btnSaveWallet = findViewById(R.id.btnSaveWallet);
        btnDeleteWallet = findViewById(R.id.btnDeleteWallet);
        spacerWalletBottom = findViewById(R.id.spacerWalletBottom);
        etWalletName = findViewById(R.id.etWalletName);
        etWalletNote = findViewById(R.id.etWalletNote);
        inputProvider = findViewById(R.id.inputWalletProvider);
        rgCurrency = findViewById(R.id.rgWalletCurrency);
        tvError = findViewById(R.id.tvWalletDetailError);
    }

    private void setupActions() {
        toolbar.setNavigationOnClickListener(v -> finish());
        tvTopAction.setOnClickListener(v -> saveWallet());
        btnSaveWallet.setOnClickListener(v -> saveWallet());
        btnDeleteWallet.setOnClickListener(v -> confirmDeleteFromEdit());

        btnTypePicker.setOnClickListener(v -> showTypeSelector());
        btnProviderPicker.setOnClickListener(v -> showProviderSelector());

        rgCurrency.setOnCheckedChangeListener((group, checkedId) ->
            selectedCurrency = checkedId == R.id.rbCurrencyUsd ? "USD" : "VND"
        );
        MoneyInputFormatter.attach(etHeaderBalance);

        etHeaderBalance.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateHeaderBalance();
            }
        });
    }

    private void applyModeUi() {
        if (isEditMode) {
            toolbar.setTitle(R.string.wallet_edit_screen_title);
            tvTopAction.setText(R.string.wallet_save_top_action);
            btnSaveWallet.setText(R.string.action_save_wallet);
            btnDeleteWallet.setVisibility(View.VISIBLE);
            spacerWalletBottom.setVisibility(View.VISIBLE);
            etHeaderBalance.setEnabled(false);
            etHeaderBalance.setFocusable(false);
            etHeaderBalance.setFocusableInTouchMode(false);
            etHeaderBalance.setLongClickable(false);
        } else {
            toolbar.setTitle(R.string.wallet_add_title);
            tvTopAction.setText(R.string.wallet_add_top_action);
            btnSaveWallet.setText(R.string.action_save_again);
            btnDeleteWallet.setVisibility(View.GONE);
            spacerWalletBottom.setVisibility(View.GONE);
            etHeaderBalance.setEnabled(true);
            etHeaderBalance.setFocusable(true);
            etHeaderBalance.setFocusableInTouchMode(true);
            etHeaderBalance.setLongClickable(true);
        }
    }

    private void resolveModeAndPrefill() {
        Intent intent = getIntent();
        isEditMode = intent != null && intent.getBooleanExtra(MainActivity.EXTRA_WALLET_EDIT_MODE, false);
        if (!isEditMode || intent == null) {
            selectedType = "CASH";
            selectedIconKey = WalletUiMapper.iconKeyForType(selectedType);
            return;
        }
        editWalletId = intent.getStringExtra(MainActivity.EXTRA_WALLET_ID);
        selectedType = WalletUiMapper.normalizeAccountType(intent.getStringExtra(MainActivity.EXTRA_WALLET_TYPE));
        String iconFromIntent = intent.getStringExtra(MainActivity.EXTRA_WALLET_ICON_KEY);
        selectedIconKey = iconFromIntent == null || iconFromIntent.trim().isEmpty()
            ? WalletUiMapper.iconKeyForType(selectedType)
            : iconFromIntent.trim().toLowerCase(Locale.ROOT);
        selectedProvider = safe(intent.getStringExtra(MainActivity.EXTRA_WALLET_PROVIDER_NAME));
        isLocked = intent.getBooleanExtra(MainActivity.EXTRA_WALLET_LOCKED, false);
    }

    private void populateInitialValues() {
        Intent intent = getIntent();
        if (isEditMode && intent != null) {
            etWalletName.setText(safe(intent.getStringExtra(MainActivity.EXTRA_WALLET_NAME)));
            etHeaderBalance.setText(String.valueOf(intent.getDoubleExtra(MainActivity.EXTRA_WALLET_BALANCE, 0.0)));
            etWalletNote.setText(safe(intent.getStringExtra(MainActivity.EXTRA_WALLET_NOTE)));
            selectedCurrency = safe(intent.getStringExtra(MainActivity.EXTRA_WALLET_CURRENCY)).toUpperCase(Locale.ROOT);
            if (!"USD".equals(selectedCurrency)) {
                selectedCurrency = "VND";
            }
        } else {
            etHeaderBalance.setText("0");
            selectedCurrency = "VND";
        }
        rgCurrency.check("USD".equals(selectedCurrency) ? R.id.rbCurrencyUsd : R.id.rbCurrencyVnd);
        tvTypeLabel.setText(WalletUiMapper.displayType(selectedType));
    }

    private void showTypeSelector() {
        String[] labels = new String[ACCOUNT_TYPES.length];
        int selectedIndex = 0;
        for (int i = 0; i < ACCOUNT_TYPES.length; i++) {
            labels[i] = WalletUiMapper.displayType(ACCOUNT_TYPES[i]);
            if (ACCOUNT_TYPES[i].equals(selectedType)) {
                selectedIndex = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.wallet_label_account_type)
            .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                selectedType = ACCOUNT_TYPES[which];
                selectedIconKey = WalletUiMapper.iconKeyForType(selectedType);
                if (!requiresProvider(selectedType)) {
                    selectedProvider = "";
                }
                tvTypeLabel.setText(WalletUiMapper.displayType(selectedType));
                updateProviderUi();
                dialog.dismiss();
            })
            .show();
    }

    private void showProviderSelector() {
        if (!requiresProvider(selectedType)) {
            return;
        }
        String[] options = "BANK".equals(selectedType) ? BANK_OPTIONS : EWALLET_OPTIONS;
        int selectedIndex = -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(selectedProvider)) {
                selectedIndex = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle("BANK".equals(selectedType) ? R.string.wallet_label_provider_bank : R.string.wallet_label_provider_ewallet)
            .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                selectedProvider = options[which];
                btnProviderPicker.setText(selectedProvider);
                dialog.dismiss();
            })
            .show();
    }

    private void updateProviderUi() {
        if (requiresProvider(selectedType)) {
            inputProvider.setVisibility(View.VISIBLE);
            tvProviderLabel.setVisibility(View.VISIBLE);
            tvProviderLabel.setText("BANK".equals(selectedType) ? R.string.wallet_label_provider_bank : R.string.wallet_label_provider_ewallet);
            if (selectedProvider.isEmpty()) {
                btnProviderPicker.setText("BANK".equals(selectedType) ? R.string.wallet_bank_placeholder : R.string.wallet_ewallet_placeholder);
            } else {
                btnProviderPicker.setText(selectedProvider);
            }
        } else {
            inputProvider.setVisibility(View.GONE);
            tvProviderLabel.setVisibility(View.GONE);
        }
    }

    private boolean requiresProvider(String accountType) {
        return "BANK".equals(accountType) || "EWALLET".equals(accountType);
    }

    private void updateHeaderBalance() {
        String raw = normalizedAmountText(etHeaderBalance);
        if (raw.isEmpty()) {
            etHeaderBalance.setText("0");
        }
    }

    private void saveWallet() {
        tvError.setVisibility(View.GONE);
        String walletName = safe(etWalletName.getText() == null ? "" : etWalletName.getText().toString());
        String note = safe(etWalletNote.getText() == null ? "" : etWalletNote.getText().toString());
        String balanceText = normalizedAmountText(etHeaderBalance);

        if (walletName.isEmpty()) {
            showTextError(getString(R.string.error_wallet_name_required));
            return;
        }
        if (requiresProvider(selectedType) && safe(selectedProvider).isEmpty()) {
            showTextError(getString("BANK".equals(selectedType) ? R.string.wallet_bank_placeholder : R.string.wallet_ewallet_placeholder));
            return;
        }
        if (financeViewModel == null) {
            showTextError(getString(R.string.error_unknown));
            return;
        }

        if (isEditMode) {
            if (safe(editWalletId).isEmpty()) {
                showTextError(getString(R.string.error_unknown));
                return;
            }
            submitPending = true;
            financeViewModel.updateWallet(
                editWalletId,
                walletName,
                selectedType,
                selectedIconKey,
                selectedCurrency,
                note,
                true,
                safe(selectedProvider),
                isLocked
            );
            Toast.makeText(this, R.string.message_wallet_updated, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        double openingBalance;
        try {
            openingBalance = balanceText.isEmpty() ? 0.0 : Double.parseDouble(balanceText);
        } catch (NumberFormatException ex) {
            showTextError(getString(R.string.error_invalid_opening_balance));
            return;
        }
        if (openingBalance < 0.0) {
            showTextError(getString(R.string.error_invalid_opening_balance));
            return;
        }

        submitPending = true;
        waitingCreateWalletResult = true;
        waitingCreateWalletName = walletName;
        waitingCreateWalletType = selectedType;
        waitingCreateWalletBalance = openingBalance;
        waitingCreateWalletCount = currentWalletCount();
        financeViewModel.addWallet(
            walletName,
            openingBalance,
            selectedType,
            selectedIconKey,
            selectedCurrency,
            note,
            true,
            safe(selectedProvider),
            false
        );
    }

    private void confirmDeleteFromEdit() {
        if (!isEditMode || financeViewModel == null || safe(editWalletId).isEmpty()) {
            return;
        }
        if (isLocked) {
            showTextError(getString(R.string.wallet_locked_message));
            return;
        }
        String walletName = safe(etWalletName.getText() == null ? "" : etWalletName.getText().toString());
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_wallet_title)
            .setMessage(getString(R.string.dialog_delete_wallet_message, walletName.isEmpty() ? getString(R.string.wallet_label_name) : walletName))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                financeViewModel.deleteWallet(editWalletId);
                finish();
            })
            .show();
    }

    private void showTextError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private String safe(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private String normalizedAmountText(TextInputEditText input) {
        return MoneyInputFormatter.normalizeAmount(
            safe(input.getText() == null ? "" : input.getText().toString())
        );
    }

    private int currentWalletCount() {
        if (financeViewModel == null || financeViewModel.getUiStateLiveData().getValue() == null) {
            return 0;
        }
        return financeViewModel.getUiStateLiveData().getValue().getWallets().size();
    }

    private boolean walletCreateCompleted(com.example.myapplication.finance.ui.FinanceUiState state) {
        if (state == null || state.getWallets().size() <= waitingCreateWalletCount) {
            return false;
        }
        for (Wallet wallet : state.getWallets()) {
            if (!waitingCreateWalletName.equalsIgnoreCase(wallet.getName())) {
                continue;
            }
            if (!waitingCreateWalletType.equals(WalletUiMapper.normalizeAccountType(wallet.getAccountType()))) {
                continue;
            }
            if (Math.abs(wallet.getBalance() - waitingCreateWalletBalance) > 0.0001) {
                continue;
            }
            return true;
        }
        return false;
    }
}
