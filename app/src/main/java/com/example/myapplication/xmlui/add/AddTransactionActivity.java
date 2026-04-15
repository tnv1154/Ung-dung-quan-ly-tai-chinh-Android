//noinspection PackageDirectoryMismatch,SpellCheckingInspection
package com.example.myapplication.xmlui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.AuthActivity;
import com.example.myapplication.xmlui.CategoryActivity;
import com.example.myapplication.xmlui.CategoryAssetIconLoader;
import com.example.myapplication.xmlui.CategoryFallbackMerger;
import com.example.myapplication.xmlui.CategoryPickerActivity;
import com.example.myapplication.xmlui.CategoryUiHelper;
import com.example.myapplication.xmlui.HistoryActivity;
import com.example.myapplication.xmlui.MoneyInputFormatter;
import com.example.myapplication.xmlui.MoreActivity;
import com.example.myapplication.xmlui.OverviewActivity;
import com.example.myapplication.xmlui.ReportActivity;
import com.example.myapplication.xmlui.SearchTextUtils;
import com.example.myapplication.xmlui.UiFormatters;
import com.example.myapplication.xmlui.WalletUiMapper;
import com.example.myapplication.xmlui.receipt.ReceiptImportActivity;
import com.example.myapplication.xmlui.voice.VoiceImportActivity;
import com.example.myapplication.xmlui.currency.CurrencyRateUtils;
import com.example.myapplication.xmlui.currency.ExchangeRateSnapshotLoader;
import com.google.firebase.Timestamp;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddTransactionActivity extends AppCompatActivity {
    public static final String EXTRA_PREFILL_MODE = "extra_prefill_mode";
    public static final String EXTRA_PREFILL_SOURCE_WALLET_ID = "extra_prefill_source_wallet_id";
    public static final String EXTRA_PREFILL_DESTINATION_WALLET_ID = "extra_prefill_destination_wallet_id";
    public static final String EXTRA_PREFILL_AMOUNT = "extra_prefill_amount";
    public static final String EXTRA_PREFILL_NOTE = "extra_prefill_note";
    public static final String EXTRA_PREFILL_CATEGORY_NAME = "extra_prefill_category_name";
    public static final String EXTRA_PREFILL_TIME_MILLIS = "extra_prefill_time_millis";
    public static final String EXTRA_PREFILL_PAYMENT_METHOD = "extra_prefill_payment_method";
    public static final String EXTRA_EDIT_TRANSACTION_ID = "extra_edit_transaction_id";
    public static final String EXTRA_PREVIEW_EDIT_ONLY = "extra_preview_edit_only";
    public static final String EXTRA_RESULT_MODE = "extra_result_mode";
    public static final String EXTRA_RESULT_SOURCE_WALLET_ID = "extra_result_source_wallet_id";
    public static final String EXTRA_RESULT_DESTINATION_WALLET_ID = "extra_result_destination_wallet_id";
    public static final String EXTRA_RESULT_AMOUNT = "extra_result_amount";
    public static final String EXTRA_RESULT_NOTE = "extra_result_note";
    public static final String EXTRA_RESULT_CATEGORY_NAME = "extra_result_category_name";
    public static final String EXTRA_RESULT_TIME_MILLIS = "extra_result_time_millis";
    public static final String MODE_EXPENSE = "EXPENSE";
    public static final String MODE_INCOME = "INCOME";
    public static final String MODE_TRANSFER = "TRANSFER";
    public static final String MODE_ADJUST = "ADJUST";

    private enum Mode { EXPENSE, INCOME, TRANSFER, ADJUST }

    private final ActivityResultLauncher<Intent> categoryPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            Intent data = result.getData();
            syncModeWithSelectedCategoryType(data.getStringExtra(CategoryPickerActivity.EXTRA_SELECTED_CATEGORY_TYPE));
            selectedCategoryId = data.getStringExtra(CategoryPickerActivity.EXTRA_SELECTED_CATEGORY_ID);
            selectedCategoryName = data.getStringExtra(CategoryPickerActivity.EXTRA_SELECTED_CATEGORY_NAME);
            updateSelectionButtons();
        });
    private final ActivityResultLauncher<Intent> receiptImportLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            applyReceiptPrefill(result.getData());
        });
    private final ActivityResultLauncher<Intent> voiceImportLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            handleVoiceImportResult(result.getData());
        });

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private Mode selectedMode = Mode.EXPENSE;
    private boolean pendingSubmit;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();
    private ExchangeRateSnapshot latestRateSnapshot;
    private boolean loadingRateSnapshot;

    private final List<Wallet> wallets = new ArrayList<>();
    private final List<TransactionCategory> categories = new ArrayList<>();
    private Wallet selectedSourceWallet;
    private Wallet selectedDestinationWallet;
    private String selectedCategoryId;
    private String selectedCategoryName;
    private String prefillSourceWalletId;
    private String prefillDestinationWalletId;
    private Double prefillAmount;
    private String prefillNote;
    private String prefillCategoryName;
    private long prefillTimeMillis;
    private String prefillPaymentMethod;
    private boolean preferPositiveBalanceWallet;
    private long selectedDateTimeMillis;
    private String editingTransactionId;
    private boolean previewEditOnly;

    private ImageButton btnBack;
    private MaterialButton btnModeSelector;
    private MaterialButton btnDelete;
    private MaterialButton btnSave;
    private LinearLayout layoutCategorySection;
    private LinearLayout layoutQuickCategorySection;
    private LinearLayout layoutTransferCard;
    private LinearLayout layoutSourceWalletRow;
    private LinearLayout layoutActualBalance;
    private LinearLayout layoutAmountCard;
    private GridLayout gridQuickCategories;
    private TextInputEditText etAmount;
    private TextInputEditText etActualBalance;
    private TextInputEditText etNote;
    private TextView tvSourceWalletValue;
    private TextView tvTransferSourceValue;
    private TextView tvTransferDestinationValue;
    private TextView tvCategoryValue;
    private ImageView ivCategorySelectedIcon;
    private TextView tvCategoryAll;
    private TextView btnManageCategories;
    private TextView tvDate;
    private TextView tvTime;
    private TextView tvError;
    private View modeOverlayScrim;
    private View modeOverlayCard;
    private View rowModeExpense;
    private View rowModeIncome;
    private View rowModeTransfer;
    private FloatingActionButton fabVoiceEntry;
    private FloatingActionButton fabReceiptScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);
        bindViews();
        selectedDateTimeMillis = System.currentTimeMillis();
        applyIntentPrefill();
        setupActions();
        applyInputPrefill();
        applyEditModeUi();
        setupBottomNavigation();
        setupSession();
        refreshModeUi();
        updateDateLabel();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnAddBack);
        btnModeSelector = findViewById(R.id.btnModeSelector);
        btnDelete = findViewById(R.id.btnDeleteTransaction);
        btnSave = findViewById(R.id.btnSaveTransaction);
        layoutCategorySection = findViewById(R.id.layoutCategorySection);
        layoutQuickCategorySection = findViewById(R.id.layoutQuickCategorySection);
        layoutTransferCard = findViewById(R.id.layoutTransferCard);
        layoutSourceWalletRow = findViewById(R.id.rowSourceWallet);
        layoutActualBalance = findViewById(R.id.layoutActualBalance);
        layoutAmountCard = findViewById(R.id.layoutAmountCard);
        gridQuickCategories = findViewById(R.id.gridQuickCategories);
        etAmount = findViewById(R.id.etAmount);
        etActualBalance = findViewById(R.id.etActualBalance);
        etNote = findViewById(R.id.etNote);
        tvSourceWalletValue = findViewById(R.id.tvSourceWalletValue);
        tvTransferSourceValue = findViewById(R.id.tvTransferSourceValue);
        tvTransferDestinationValue = findViewById(R.id.tvTransferDestinationValue);
        tvCategoryValue = findViewById(R.id.btnCategory);
        ivCategorySelectedIcon = findViewById(R.id.ivCategorySelectedIcon);
        tvCategoryAll = findViewById(R.id.tvCategoryAll);
        btnManageCategories = findViewById(R.id.btnManageCategories);
        tvDate = findViewById(R.id.tvTransactionDate);
        tvTime = findViewById(R.id.tvTransactionTime);
        tvError = findViewById(R.id.tvAddTransactionError);
        modeOverlayScrim = findViewById(R.id.viewModeOverlayScrim);
        modeOverlayCard = findViewById(R.id.cardModeOverlay);
        rowModeExpense = findViewById(R.id.rowModeExpense);
        rowModeIncome = findViewById(R.id.rowModeIncome);
        rowModeTransfer = findViewById(R.id.rowModeTransfer);
        fabVoiceEntry = findViewById(R.id.fabVoiceEntry);
        fabReceiptScan = findViewById(R.id.fabReceiptScan);
    }

    private void setupActions() {
        ImageButton btnTopSave = findViewById(R.id.btnTopSaveTransaction);
        View rowCategorySelector = findViewById(R.id.rowCategorySelector);
        View rowSourceWallet = findViewById(R.id.rowSourceWallet);
        View rowTransferSource = findViewById(R.id.rowTransferSource);
        View rowTransferDestination = findViewById(R.id.rowTransferDestination);
        ImageButton btnSwapTransferWallets = findViewById(R.id.btnSwapTransferWallets);
        View rowDateTime = findViewById(R.id.rowTransactionDateTime);

        btnBack.setOnClickListener(v -> {
            if (isEditMode() || previewEditOnly) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
            Intent historyIntent = new Intent(this, HistoryActivity.class);
            historyIntent.putExtra(HistoryActivity.EXTRA_SOURCE_NAV_ITEM_ID, R.id.nav_add);
            startActivity(historyIntent);
        });
        btnTopSave.setOnClickListener(v -> submitTransaction());
        btnDelete.setOnClickListener(v -> confirmDeleteTransaction());
        btnSave.setOnClickListener(v -> submitTransaction());
        btnModeSelector.setOnClickListener(v -> showModeSelector());
        MoneyInputFormatter.attach(etAmount);
        MoneyInputFormatter.attachSigned(etActualBalance);
        rowCategorySelector.setOnClickListener(v -> openCategoryPicker());
        tvCategoryAll.setOnClickListener(v -> openCategoryPicker());
        btnManageCategories.setOnClickListener(v -> openCategoryEditor());
        rowSourceWallet.setOnClickListener(v -> chooseSourceWallet());
        rowTransferSource.setOnClickListener(v -> chooseSourceWallet());
        rowTransferDestination.setOnClickListener(v -> chooseDestinationWallet());
        btnSwapTransferWallets.setOnClickListener(v -> swapTransferWallets());
        rowDateTime.setOnClickListener(v -> openDateTimePicker());
        fabVoiceEntry.setOnClickListener(
            v -> voiceImportLauncher.launch(new Intent(this, VoiceImportActivity.class))
        );
        fabReceiptScan.setOnClickListener(
            v -> receiptImportLauncher.launch(new Intent(this, ReceiptImportActivity.class))
        );
        modeOverlayScrim.setOnClickListener(v -> hideModeOverlay());
        rowModeExpense.setOnClickListener(v -> {
            applyModeSelection(Mode.EXPENSE);
            hideModeOverlay();
        });
        rowModeIncome.setOnClickListener(v -> {
            applyModeSelection(Mode.INCOME);
            hideModeOverlay();
        });
        rowModeTransfer.setOnClickListener(v -> {
            applyModeSelection(Mode.TRANSFER);
            hideModeOverlay();
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_add);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_add) {
                return true;
            }
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
        financeViewModel.ensureDefaultCategories();
    }

    private void renderFinanceState(@NonNull FinanceUiState state) {
        wallets.clear();
        for (Wallet wallet : state.getWallets()) {
            if (!wallet.isLocked()) {
                wallets.add(wallet);
            }
        }
        categories.clear();
        categories.addAll(CategoryFallbackMerger.mergeWithFallbacks(state.getCategories()));
        loadLatestRateSnapshot();

        selectedSourceWallet = findWalletById(selectedSourceWallet == null ? null : selectedSourceWallet.getId());
        selectedDestinationWallet = findWalletById(selectedDestinationWallet == null ? null : selectedDestinationWallet.getId());

        if (selectedSourceWallet == null && prefillSourceWalletId != null) {
            selectedSourceWallet = findWalletById(prefillSourceWalletId);
            if (selectedSourceWallet != null) {
                prefillSourceWalletId = null;
            }
        }
        if (selectedDestinationWallet == null && prefillDestinationWalletId != null) {
            selectedDestinationWallet = findWalletById(prefillDestinationWalletId);
            if (selectedDestinationWallet != null) {
                prefillDestinationWalletId = null;
            }
        }
        if (selectedSourceWallet == null && !wallets.isEmpty()) {
            selectedSourceWallet = wallets.get(0);
        }
        if (selectedDestinationWallet == null && selectedMode == Mode.TRANSFER) {
            for (Wallet wallet : wallets) {
                if (selectedSourceWallet == null || !wallet.getId().equals(selectedSourceWallet.getId())) {
                    selectedDestinationWallet = wallet;
                    break;
                }
            }
        }
        applyPendingReceiptWalletSelection();

        if (selectedCategoryId != null) {
            TransactionCategory category = findCategoryById(selectedCategoryId);
            if (category != null) {
                selectedCategoryName = category.getName();
            } else {
                selectedCategoryId = null;
                selectedCategoryName = null;
            }
        }
        renderQuickCategories();
        updateSelectionButtons();

        String errorMessage = state.getErrorMessage();
        if (isPermissionDenied(errorMessage)) {
            financeViewModel.clearError();
        }
    }

    private void applyIntentPrefill() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        prefillSourceWalletId = intent.getStringExtra(EXTRA_PREFILL_SOURCE_WALLET_ID);
        prefillDestinationWalletId = intent.getStringExtra(EXTRA_PREFILL_DESTINATION_WALLET_ID);
        String editId = intent.getStringExtra(EXTRA_EDIT_TRANSACTION_ID);
        if (editId != null && !editId.trim().isEmpty()) {
            editingTransactionId = editId.trim();
        }
        previewEditOnly = intent.getBooleanExtra(EXTRA_PREVIEW_EDIT_ONLY, false);
        if (intent.hasExtra(EXTRA_PREFILL_AMOUNT)) {
            double amount = intent.getDoubleExtra(EXTRA_PREFILL_AMOUNT, 0.0);
            if (amount > 0.0) {
                prefillAmount = amount;
            }
        }
        String note = intent.getStringExtra(EXTRA_PREFILL_NOTE);
        if (note != null && !note.trim().isEmpty()) {
            prefillNote = note.trim();
        }
        String category = intent.getStringExtra(EXTRA_PREFILL_CATEGORY_NAME);
        if (category != null && !category.trim().isEmpty()) {
            prefillCategoryName = category.trim();
            selectedCategoryName = prefillCategoryName;
        }
        long prefTime = intent.getLongExtra(EXTRA_PREFILL_TIME_MILLIS, 0L);
        if (prefTime > 0L) {
            prefillTimeMillis = prefTime;
            selectedDateTimeMillis = prefTime;
        }
        String paymentMethod = intent.getStringExtra(EXTRA_PREFILL_PAYMENT_METHOD);
        if (paymentMethod != null && !paymentMethod.trim().isEmpty()) {
            prefillPaymentMethod = paymentMethod.trim();
        }
        String prefillMode = intent.getStringExtra(EXTRA_PREFILL_MODE);
        if (MODE_INCOME.equalsIgnoreCase(prefillMode)) {
            selectedMode = Mode.INCOME;
        } else if (MODE_TRANSFER.equalsIgnoreCase(prefillMode)) {
            selectedMode = Mode.TRANSFER;
        } else if (MODE_ADJUST.equalsIgnoreCase(prefillMode)) {
            selectedMode = Mode.ADJUST;
        } else {
            selectedMode = Mode.EXPENSE;
        }
    }

    private void applyInputPrefill() {
        if (prefillAmount != null && prefillAmount > 0.0) {
            etAmount.setText(formatAmountForInput(prefillAmount));
        }
        if (prefillNote != null && !prefillNote.isEmpty()) {
            etNote.setText(prefillNote);
        }
        if (prefillCategoryName != null && !prefillCategoryName.isEmpty()) {
            selectedCategoryName = prefillCategoryName;
        }
        if (prefillTimeMillis > 0L) {
            selectedDateTimeMillis = prefillTimeMillis;
        }
    }

    private String formatAmountForInput(double value) {
        return Math.floor(value) == value
            ? String.format(Locale.ROOT, "%.0f", value)
            : String.format(Locale.ROOT, "%.2f", value);
    }

    private void applyReceiptPrefill(@NonNull Intent data) {
        String prefillMode = data.getStringExtra(EXTRA_PREFILL_MODE);
        if (MODE_INCOME.equalsIgnoreCase(prefillMode)) {
            applyModeSelection(Mode.INCOME);
        } else if (MODE_TRANSFER.equalsIgnoreCase(prefillMode)) {
            applyModeSelection(Mode.TRANSFER);
        } else {
            applyModeSelection(Mode.EXPENSE);
        }

        if (data.hasExtra(EXTRA_PREFILL_AMOUNT)) {
            double amount = data.getDoubleExtra(EXTRA_PREFILL_AMOUNT, 0.0);
            if (amount > 0.0) {
                etAmount.setText(formatAmountForInput(amount));
            }
        }

        String note = data.getStringExtra(EXTRA_PREFILL_NOTE);
        etNote.setText(note == null ? "" : note.trim());

        String category = data.getStringExtra(EXTRA_PREFILL_CATEGORY_NAME);
        selectedCategoryId = null;
        selectedCategoryName = (category != null && !category.trim().isEmpty()) ? category.trim() : null;

        String paymentMethod = data.getStringExtra(EXTRA_PREFILL_PAYMENT_METHOD);
        prefillPaymentMethod = paymentMethod == null || paymentMethod.trim().isEmpty() ? null : paymentMethod.trim();
        preferPositiveBalanceWallet = prefillPaymentMethod == null;
        applyPendingReceiptWalletSelection();

        updateSelectionButtons();

        long timeMillis = data.getLongExtra(EXTRA_PREFILL_TIME_MILLIS, System.currentTimeMillis());
        if (timeMillis <= 0L) {
            timeMillis = System.currentTimeMillis();
        }
        selectedDateTimeMillis = timeMillis;
        updateDateLabel();
        tvError.setVisibility(View.GONE);
    }

    private void handleVoiceImportResult(@NonNull Intent data) {
        if (data.getBooleanExtra(VoiceImportActivity.EXTRA_DIRECT_SAVED, false)) {
            Toast.makeText(this, R.string.message_transaction_saved, Toast.LENGTH_SHORT).show();
            resetFormToDefaultState();
            if (financeViewModel != null) {
                financeViewModel.refreshRealtimeSync();
            }
            return;
        }
        applyReceiptPrefill(data);
    }

    private void applyPendingReceiptWalletSelection() {
        if (wallets.isEmpty()) {
            return;
        }
        if (prefillPaymentMethod == null || prefillPaymentMethod.trim().isEmpty()) {
            if (preferPositiveBalanceWallet) {
                preferPositiveBalanceWallet = false;
                applyPositiveBalanceWalletFallback();
            }
            return;
        }
        String targetType = resolveWalletTypeFromPaymentMethod(prefillPaymentMethod);
        prefillPaymentMethod = null;
        if (targetType.isEmpty()) {
            applyPositiveBalanceWalletFallback();
            return;
        }
        if (selectedSourceWallet != null
            && targetType.equals(WalletUiMapper.normalizeAccountType(selectedSourceWallet.getAccountType()))) {
            return;
        }
        for (Wallet wallet : wallets) {
            if (wallet == null) {
                continue;
            }
            String walletType = WalletUiMapper.normalizeAccountType(wallet.getAccountType());
            if (!targetType.equals(walletType)) {
                continue;
            }
            selectedSourceWallet = wallet;
            prefillSourceWalletId = wallet.getId();
            if (selectedDestinationWallet != null && selectedDestinationWallet.getId().equals(wallet.getId())) {
                selectedDestinationWallet = null;
            }
            break;
        }
        if (selectedSourceWallet == null
            || !targetType.equals(WalletUiMapper.normalizeAccountType(selectedSourceWallet.getAccountType()))) {
            applyPositiveBalanceWalletFallback();
        }
    }

    private void applyPositiveBalanceWalletFallback() {
        if (wallets.isEmpty()) {
            return;
        }
        if (selectedSourceWallet != null && selectedSourceWallet.getBalance() > 0.0d) {
            return;
        }
        Wallet positiveBalanceWallet = null;
        for (Wallet wallet : wallets) {
            if (wallet != null && wallet.getBalance() > 0.0d) {
                positiveBalanceWallet = wallet;
                break;
            }
        }
        if (positiveBalanceWallet == null) {
            return;
        }
        selectedSourceWallet = positiveBalanceWallet;
        prefillSourceWalletId = positiveBalanceWallet.getId();
        if (selectedDestinationWallet != null && selectedDestinationWallet.getId().equals(positiveBalanceWallet.getId())) {
            selectedDestinationWallet = null;
        }
    }

    private String resolveWalletTypeFromPaymentMethod(String paymentMethod) {
        String normalized = SearchTextUtils.normalize(paymentMethod);
        if (normalized.contains("vi dien tu") || normalized.contains("ewallet")) {
            return "EWALLET";
        }
        if (normalized.contains("ngan hang") || normalized.contains("bank")) {
            return "BANK";
        }
        if (normalized.contains("tien mat") || normalized.contains("cash")) {
            return "CASH";
        }
        return "";
    }

    private void applyEditModeUi() {
        if (!isEditMode() && !previewEditOnly) {
            btnDelete.setVisibility(View.GONE);
            fabVoiceEntry.setVisibility(View.VISIBLE);
            fabReceiptScan.setVisibility(View.VISIBLE);
            return;
        }
        btnBack.setImageResource(R.drawable.ic_wallet_back);
        btnBack.setContentDescription(getString(R.string.action_back));
        btnDelete.setVisibility(isEditMode() ? View.VISIBLE : View.GONE);
        fabVoiceEntry.setVisibility(View.GONE);
        fabReceiptScan.setVisibility(View.GONE);
        btnSave.setText(R.string.action_save_changes);
        if (previewEditOnly && rowModeTransfer != null) {
            rowModeTransfer.setVisibility(View.GONE);
            if (selectedMode == Mode.TRANSFER || selectedMode == Mode.ADJUST) {
                selectedMode = Mode.EXPENSE;
            }
        }
    }

    private boolean isEditMode() {
        return editingTransactionId != null && !editingTransactionId.isBlank();
    }

    private void refreshModeUi() {
        btnModeSelector.setText(modeLabel(selectedMode));
        tvError.setVisibility(View.GONE);
        hideModeOverlay(false);

        boolean expense = selectedMode == Mode.EXPENSE;
        boolean income = selectedMode == Mode.INCOME;
        boolean transfer = selectedMode == Mode.TRANSFER;
        boolean adjust = selectedMode == Mode.ADJUST;

        layoutAmountCard.setVisibility(adjust ? View.GONE : View.VISIBLE);
        layoutCategorySection.setVisibility((expense || income) ? View.VISIBLE : View.GONE);
        layoutQuickCategorySection.setVisibility(expense ? View.VISIBLE : View.GONE);
        layoutTransferCard.setVisibility(transfer ? View.VISIBLE : View.GONE);
        layoutSourceWalletRow.setVisibility((expense || income || adjust) ? View.VISIBLE : View.GONE);
        layoutActualBalance.setVisibility(adjust ? View.VISIBLE : View.GONE);

        int amountColor = expense
            ? R.color.add_amount_expense
            : (income ? R.color.add_amount_income : R.color.add_amount_transfer);
        etAmount.setTextColor(getColor(amountColor));
        etAmount.setHintTextColor(getColor(amountColor));

        styleModeSelector(transfer);
        renderQuickCategories();
        updateSelectionButtons();
        updateCurrencyHints();
    }

    private void styleModeSelector(boolean filled) {
        btnModeSelector.setCornerRadius(dp(24));
        btnModeSelector.setStrokeColor(ColorStateList.valueOf(getColor(R.color.add_mode_blue)));
        btnModeSelector.setStrokeWidth(dp(filled ? 1 : 2));
        btnModeSelector.setBackgroundTintList(
            ColorStateList.valueOf(getColor(filled ? R.color.group_bank_bg : R.color.card_bg))
        );
        btnModeSelector.setMinWidth(dp(selectedMode == Mode.TRANSFER ? 146 : 118));
        btnModeSelector.setTextColor(getColor(R.color.add_mode_blue));
        btnModeSelector.setIconTint(ColorStateList.valueOf(getColor(R.color.add_mode_blue)));
    }

    private String modeLabel(Mode mode) {
        if (mode == Mode.INCOME) {
            return getString(R.string.app_title_add_income);
        }
        if (mode == Mode.TRANSFER) {
            return getString(R.string.app_title_add_transfer);
        }
        if (mode == Mode.ADJUST) {
            return getString(R.string.app_title_adjust_balance);
        }
        return getString(R.string.app_title_add_expense);
    }

    private void showModeSelector() {
        if (modeOverlayScrim.getVisibility() == View.VISIBLE) {
            hideModeOverlay();
        } else {
            showModeOverlay();
        }
    }

    private void applyModeSelection(Mode nextMode) {
        if ((nextMode == Mode.EXPENSE || nextMode == Mode.INCOME) && nextMode != selectedMode) {
            selectedCategoryId = null;
            selectedCategoryName = null;
        }
        selectedMode = nextMode;
        if (selectedMode != Mode.EXPENSE && selectedMode != Mode.INCOME) {
            selectedCategoryId = null;
            selectedCategoryName = null;
        }
        refreshModeUi();
    }

    private void showModeOverlay() {
        modeOverlayScrim.setVisibility(View.VISIBLE);
        modeOverlayCard.setVisibility(View.VISIBLE);
        modeOverlayScrim.setAlpha(0f);
        modeOverlayCard.setAlpha(0f);
        modeOverlayCard.setTranslationY(-dp(8));
        modeOverlayScrim.animate()
            .alpha(1f)
            .setDuration(120L)
            .start();
        modeOverlayCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(160L)
            .start();
    }

    private void hideModeOverlay() {
        hideModeOverlay(true);
    }

    private void hideModeOverlay(boolean animate) {
        if (modeOverlayScrim.getVisibility() != View.VISIBLE) {
            return;
        }
        if (!animate) {
            modeOverlayScrim.setVisibility(View.GONE);
            modeOverlayCard.setVisibility(View.GONE);
            modeOverlayScrim.setAlpha(1f);
            modeOverlayCard.setAlpha(1f);
            modeOverlayCard.setTranslationY(0f);
            return;
        }
        modeOverlayScrim.animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction(() -> modeOverlayScrim.setVisibility(View.GONE))
            .start();
        modeOverlayCard.animate()
            .alpha(0f)
            .translationY(-dp(8))
            .setDuration(120L)
            .withEndAction(() -> {
                modeOverlayCard.setVisibility(View.GONE);
                modeOverlayCard.setTranslationY(0f);
            })
            .start();
    }

    private void renderQuickCategories() {
        gridQuickCategories.removeAllViews();
        if (selectedMode != Mode.EXPENSE) {
            return;
        }
        List<TransactionCategory> quick = new ArrayList<>();
        for (TransactionCategory category : categories) {
            if (category.getType() != TransactionType.EXPENSE) {
                continue;
            }
            if (category.getParentName() == null || category.getParentName().trim().isEmpty()) {
                continue;
            }
            quick.add(category);
        }
        quick.sort(
            Comparator.comparingInt(TransactionCategory::getSortOrder)
                .thenComparing(TransactionCategory::getName)
        );
        int limit = Math.min(7, quick.size());
        for (int i = 0; i < limit; i++) {
            TransactionCategory category = quick.get(i);
            View item = createQuickCategoryView(category, false);
            item.setOnClickListener(v -> {
                selectedCategoryId = category.getId();
                selectedCategoryName = category.getName();
                updateSelectionButtons();
            });
            gridQuickCategories.addView(item);
        }
        View editItem = createQuickCategoryView(null, true);
        editItem.setOnClickListener(v -> openCategoryEditor());
        gridQuickCategories.addView(editItem);
    }

    private View createQuickCategoryView(TransactionCategory category, boolean isEditTile) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_add_quick_category, gridQuickCategories, false);
        View layoutIcon = item.findViewById(R.id.layoutQuickCategoryIcon);
        ImageView ivIcon = item.findViewById(R.id.ivQuickCategoryIcon);
        TextView tvLabel = item.findViewById(R.id.tvQuickCategoryLabel);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f)
        );
        params.width = 0;
        params.setMargins(dp(2), dp(0), dp(2), dp(6));
        item.setLayoutParams(params);

        if (isEditTile) {
            tvLabel.setText(getString(R.string.action_edit_categories));
            layoutIcon.getBackground().setTint(ContextCompat.getColor(this, R.color.card_bg));
            ivIcon.setImageResource(R.drawable.ic_action_edit);
            ivIcon.setImageTintList(ContextCompat.getColorStateList(this, R.color.add_mode_blue));
            tvLabel.setTextColor(getColor(R.color.add_mode_blue));
            return item;
        }

        if (category != null) {
            tvLabel.setText(category.getName());
            layoutIcon.getBackground().setTint(ContextCompat.getColor(this, CategoryUiHelper.iconBgForCategory(category)));
            boolean loadedFromAssets = CategoryAssetIconLoader.applyCategoryIcon(
                ivIcon,
                category,
                CategoryUiHelper.iconResForCategory(category)
            );
            ivIcon.setImageTintList(loadedFromAssets
                ? null
                : ContextCompat.getColorStateList(this, CategoryUiHelper.iconTintForCategory(category)));
        }
        return item;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openCategoryPicker() {
        if (selectedMode != Mode.EXPENSE && selectedMode != Mode.INCOME) {
            return;
        }
        Intent intent = new Intent(this, CategoryPickerActivity.class);
        intent.putExtra(
            CategoryPickerActivity.EXTRA_TYPE,
            selectedMode == Mode.INCOME ? MODE_INCOME : MODE_EXPENSE
        );
        intent.putExtra(CategoryPickerActivity.EXTRA_SELECTED_CATEGORY_ID, selectedCategoryId);
        categoryPickerLauncher.launch(intent);
    }

    private void syncModeWithSelectedCategoryType(String categoryTypeRaw) {
        if (categoryTypeRaw == null || categoryTypeRaw.trim().isEmpty()) {
            return;
        }
        if ("INCOME".equalsIgnoreCase(categoryTypeRaw)) {
            applyModeSelection(Mode.INCOME);
            return;
        }
        if ("EXPENSE".equalsIgnoreCase(categoryTypeRaw)) {
            applyModeSelection(Mode.EXPENSE);
        }
    }

    private void openCategoryEditor() {
        Intent intent = new Intent(this, CategoryActivity.class);
        intent.putExtra(
            CategoryActivity.EXTRA_INITIAL_TYPE,
            selectedMode == Mode.INCOME ? MODE_INCOME : MODE_EXPENSE
        );
        startActivity(intent);
    }

    private void updateSelectionButtons() {
        String sourceText = selectedSourceWallet == null
            ? getString(R.string.action_choose_wallet)
            : selectedSourceWallet.getName();
        tvSourceWalletValue.setText(sourceText);
        tvTransferSourceValue.setText(sourceText);

        String destinationText = selectedDestinationWallet == null
            ? getString(R.string.action_choose_wallet)
            : selectedDestinationWallet.getName();
        tvTransferDestinationValue.setText(destinationText);

        if (selectedMode == Mode.EXPENSE || selectedMode == Mode.INCOME) {
            tvCategoryValue.setText(
                selectedCategoryName == null || selectedCategoryName.trim().isEmpty()
                    ? getString(R.string.action_choose_category)
                    : selectedCategoryName
            );
        }
        updateCategorySelectionIcon();
        updateCurrencyHints();
    }

    private void updateCategorySelectionIcon() {
        if (selectedMode != Mode.EXPENSE && selectedMode != Mode.INCOME) {
            applyDefaultCategoryIcon();
            return;
        }
        TransactionCategory selectedCategory = findCategoryById(selectedCategoryId);
        if (selectedCategory == null && selectedCategoryName != null && !selectedCategoryName.trim().isEmpty()) {
            for (TransactionCategory category : categories) {
                if (category.getType() == selectedTypeForMode()
                    && category.getName().equalsIgnoreCase(selectedCategoryName.trim())) {
                    selectedCategory = category;
                    break;
                }
            }
        }
        if (selectedCategory == null) {
            applyDefaultCategoryIcon();
            return;
        }
        ivCategorySelectedIcon.setBackgroundResource(R.drawable.bg_add_quick_icon);
        if (ivCategorySelectedIcon.getBackground() != null) {
            ivCategorySelectedIcon.getBackground().setTint(
                ContextCompat.getColor(this, CategoryUiHelper.iconBgForCategory(selectedCategory))
            );
        }
        boolean loadedFromAssets = CategoryAssetIconLoader.applyCategoryIcon(
            ivCategorySelectedIcon,
            selectedCategory,
            CategoryUiHelper.iconResForCategory(selectedCategory)
        );
        ivCategorySelectedIcon.setImageTintList(loadedFromAssets
            ? null
            : ContextCompat.getColorStateList(this, CategoryUiHelper.iconTintForCategory(selectedCategory)));
    }

    private void applyDefaultCategoryIcon() {
        ivCategorySelectedIcon.setBackgroundResource(R.drawable.bg_add_dashed_circle);
        ivCategorySelectedIcon.setImageResource(R.drawable.ic_add_mode_plus);
        ivCategorySelectedIcon.setImageTintList(ContextCompat.getColorStateList(this, R.color.text_secondary));
    }

    private TransactionType selectedTypeForMode() {
        return selectedMode == Mode.INCOME ? TransactionType.INCOME : TransactionType.EXPENSE;
    }

    private void updateCurrencyHints() {
        String unit = currencyUnitForSelectedWallet();
        String hint = getString(R.string.hint_amount_with_unit, unit);
        etAmount.setHint(hint);
        etActualBalance.setHint(hint);
    }

    private String currencyUnitForSelectedWallet() {
        String currency = normalizeCurrency(selectedSourceWallet == null ? null : selectedSourceWallet.getCurrency());
        if ("USD".equals(currency)) {
            return getString(R.string.currency_unit_usd);
        }
        if ("VND".equals(currency)) {
            return getString(R.string.currency_unit_vnd);
        }
        return currency;
    }

    private String normalizeCurrency(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "VND";
        }
        return value;
    }

    private void updateDateLabel() {
        Date selected = new Date(selectedDateTimeMillis);
        String datePart = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selected);
        if (isSameDay(selectedDateTimeMillis, System.currentTimeMillis())) {
            tvDate.setText(getString(R.string.label_transaction_date_today, datePart));
        } else {
            tvDate.setText(datePart);
        }
        tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(selected));
    }

    private void openDateTimePicker() {
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(selectedDateTimeMillis);
        DatePickerDialog picker = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                Calendar next = Calendar.getInstance();
                next.setTimeInMillis(selectedDateTimeMillis);
                next.set(Calendar.YEAR, year);
                next.set(Calendar.MONTH, month);
                next.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                selectedDateTimeMillis = next.getTimeInMillis();
                openTimePicker();
            },
            selected.get(Calendar.YEAR),
            selected.get(Calendar.MONTH),
            selected.get(Calendar.DAY_OF_MONTH)
        );
        picker.show();
    }

    private void openTimePicker() {
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(selectedDateTimeMillis);
        TimePickerDialog picker = new TimePickerDialog(
            this,
            (view, hourOfDay, minute) -> {
                Calendar next = Calendar.getInstance();
                next.setTimeInMillis(selectedDateTimeMillis);
                next.set(Calendar.HOUR_OF_DAY, hourOfDay);
                next.set(Calendar.MINUTE, minute);
                next.set(Calendar.SECOND, 0);
                next.set(Calendar.MILLISECOND, 0);
                selectedDateTimeMillis = next.getTimeInMillis();
                updateDateLabel();
            },
            selected.get(Calendar.HOUR_OF_DAY),
            selected.get(Calendar.MINUTE),
            true
        );
        picker.show();
    }

    private boolean isSameDay(long aMillis, long bMillis) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(aMillis);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(bMillis);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void chooseSourceWallet() {
        if (wallets.isEmpty()) {
            showError(getString(R.string.error_wallet_required));
            return;
        }
        String[] names = new String[wallets.size()];
        for (int i = 0; i < wallets.size(); i++) {
            names[i] = buildWalletOptionLabel(wallets.get(i));
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_source_wallet)
            .setItems(names, (dialog, which) -> {
                selectedSourceWallet = wallets.get(which);
                if (selectedDestinationWallet != null && selectedDestinationWallet.getId().equals(selectedSourceWallet.getId())) {
                    selectedDestinationWallet = null;
                }
                updateSelectionButtons();
            })
            .show();
    }

    private void chooseDestinationWallet() {
        if (wallets.size() <= 1) {
            showError(getString(R.string.error_destination_required));
            return;
        }
        List<Wallet> options = new ArrayList<>();
        for (Wallet wallet : wallets) {
            if (selectedSourceWallet == null || !wallet.getId().equals(selectedSourceWallet.getId())) {
                options.add(wallet);
            }
        }
        String[] names = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            names[i] = buildWalletOptionLabel(options.get(i));
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_destination_wallet)
            .setItems(names, (dialog, which) -> {
                selectedDestinationWallet = options.get(which);
                updateSelectionButtons();
            })
            .show();
    }

    private void swapTransferWallets() {
        if (selectedSourceWallet == null && selectedDestinationWallet == null) {
            return;
        }
        Wallet temp = selectedSourceWallet;
        selectedSourceWallet = selectedDestinationWallet;
        selectedDestinationWallet = temp;
        if (selectedSourceWallet == null && !wallets.isEmpty()) {
            selectedSourceWallet = wallets.get(0);
        }
        if (selectedDestinationWallet == null && selectedMode == Mode.TRANSFER) {
            for (Wallet wallet : wallets) {
                if (selectedSourceWallet == null || !wallet.getId().equals(selectedSourceWallet.getId())) {
                    selectedDestinationWallet = wallet;
                    break;
                }
            }
        }
        updateSelectionButtons();
    }

    private void submitTransaction() {
        if (pendingSubmit) {
            return;
        }
        tvError.setVisibility(View.GONE);
        if (financeViewModel == null) {
            showError(getString(R.string.error_unknown));
            return;
        }
        if (selectedSourceWallet == null) {
            showError(getString(R.string.error_wallet_required));
            return;
        }
        if (selectedMode == Mode.TRANSFER) {
            if (selectedDestinationWallet == null) {
                showError(getString(R.string.error_destination_required));
                return;
            }
            if (selectedDestinationWallet.getId().equals(selectedSourceWallet.getId())) {
                showError(getString(R.string.error_destination_same_wallet));
                return;
            }
        }

        String note = etNote.getText() == null ? "" : etNote.getText().toString().trim();
        Timestamp selectedTimestamp = new Timestamp(new Date(selectedDateTimeMillis));
        if (selectedMode == Mode.ADJUST) {
            if (previewEditOnly) {
                showError(getString(R.string.csv_import_preview_edit_type_not_supported));
                return;
            }
            Double actual = parseDouble(
                etActualBalance.getText() == null ? "" : etActualBalance.getText().toString(),
                true
            );
            if (actual == null) {
                showError(getString(R.string.error_invalid_amount));
                return;
            }
            pendingSubmit = true;
            financeViewModel.adjustWalletBalance(
                selectedSourceWallet.getId(),
                actual,
                note,
                selectedTimestamp,
                this::onSubmitCompleted
            );
            return;
        }

        Double amount = parseDouble(
            etAmount.getText() == null ? "" : etAmount.getText().toString(),
            false
        );
        if (amount == null || amount <= 0.0) {
            showError(getString(R.string.error_invalid_amount));
            return;
        }

        TransactionType type;
        String category;
        String destinationWalletId = null;
        if (selectedMode == Mode.INCOME) {
            type = TransactionType.INCOME;
            category = selectedCategoryName == null || selectedCategoryName.trim().isEmpty()
                ? getString(R.string.default_category_other)
                : selectedCategoryName;
        } else if (selectedMode == Mode.TRANSFER) {
            type = TransactionType.TRANSFER;
            category = getString(R.string.transaction_type_transfer);
            destinationWalletId = selectedDestinationWallet.getId();
        } else {
            type = TransactionType.EXPENSE;
            category = selectedCategoryName == null || selectedCategoryName.trim().isEmpty()
                ? getString(R.string.default_category_other)
                : selectedCategoryName;
        }
        pendingSubmit = true;
        if (previewEditOnly) {
            completePreviewEdit(
                amount,
                category,
                note,
                selectedTimestamp,
                selectedSourceWallet.getId(),
                destinationWalletId
            );
            return;
        }
        if (isEditMode()) {
            if (type == TransactionType.TRANSFER) {
                financeViewModel.updateTransferTransactionWithConversion(
                    editingTransactionId,
                    selectedSourceWallet.getId(),
                    destinationWalletId,
                    amount,
                    category,
                    note,
                    selectedSourceWallet.getCurrency(),
                    selectedDestinationWallet == null ? null : selectedDestinationWallet.getCurrency(),
                    selectedTimestamp,
                    this::onSubmitCompleted
                );
            } else {
                financeViewModel.updateTransaction(
                    editingTransactionId,
                    selectedSourceWallet.getId(),
                    type,
                    amount,
                    category,
                    note,
                    destinationWalletId,
                    selectedTimestamp,
                    this::onSubmitCompleted
                );
            }
            return;
        }
        if (type == TransactionType.TRANSFER) {
            financeViewModel.addTransferTransactionWithConversion(
                selectedSourceWallet.getId(),
                destinationWalletId,
                amount,
                category,
                note,
                selectedSourceWallet.getCurrency(),
                selectedDestinationWallet == null ? null : selectedDestinationWallet.getCurrency(),
                selectedTimestamp,
                this::onSubmitCompleted
            );
        } else {
            financeViewModel.addTransaction(
                selectedSourceWallet.getId(),
                type,
                amount,
                category,
                note,
                destinationWalletId,
                selectedTimestamp,
                this::onSubmitCompleted
            );
        }
    }

    private void completePreviewEdit(
        double amount,
        String category,
        String note,
        Timestamp createdAt,
        String sourceWalletId,
        String destinationWalletId
    ) {
        pendingSubmit = false;
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_MODE, modeValue(selectedMode));
        result.putExtra(EXTRA_RESULT_SOURCE_WALLET_ID, sourceWalletId);
        result.putExtra(EXTRA_RESULT_DESTINATION_WALLET_ID, destinationWalletId);
        result.putExtra(EXTRA_RESULT_AMOUNT, amount);
        result.putExtra(EXTRA_RESULT_NOTE, note);
        result.putExtra(EXTRA_RESULT_CATEGORY_NAME, category);
        result.putExtra(
            EXTRA_RESULT_TIME_MILLIS,
            createdAt == null ? selectedDateTimeMillis : createdAt.getSeconds() * 1000L + (createdAt.getNanoseconds() / 1_000_000L)
        );
        setResult(RESULT_OK, result);
        finish();
    }

    private String modeValue(Mode mode) {
        if (mode == Mode.INCOME) {
            return MODE_INCOME;
        }
        if (mode == Mode.TRANSFER) {
            return MODE_TRANSFER;
        }
        if (mode == Mode.ADJUST) {
            return MODE_ADJUST;
        }
        return MODE_EXPENSE;
    }

    private Double parseDouble(String raw, boolean allowSigned) {
        String cleaned = allowSigned
            ? MoneyInputFormatter.normalizeSignedAmount(raw)
            : MoneyInputFormatter.normalizeAmount(raw);
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildWalletOptionLabel(Wallet wallet) {
        String currencyCode = normalizeCurrency(wallet.getCurrency());
        String original = formatMoneyByCurrency(wallet.getBalance(), currencyCode);
        Double convertedVnd = CurrencyRateUtils.convert(
            wallet.getBalance(),
            currencyCode,
            "VND",
            latestRateSnapshot
        );
        if (convertedVnd != null && !"VND".equals(currencyCode)) {
            return wallet.getName() + " • " + original + " (≈ " + UiFormatters.money(convertedVnd) + ")";
        }
        return wallet.getName() + " • " + original;
    }

    private String formatMoneyByCurrency(double value, String currencyCode) {
        String currency = normalizeCurrency(currencyCode);
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(new Locale("vi", "VN"));
        if ("VND".equals(currency)) {
            formatter.applyPattern("#,###");
            return formatter.format(value) + " ₫";
        }
        if ("USD".equals(currency)) {
            formatter.applyPattern("#,##0.00");
            return "$" + formatter.format(value);
        }
        formatter.applyPattern("#,##0.00");
        return formatter.format(value) + " " + currency;
    }

    private void loadLatestRateSnapshot() {
        if (loadingRateSnapshot || latestRateSnapshot != null) {
            return;
        }
        String userId = observedUserId;
        if (userId == null || userId.isBlank()) {
            return;
        }
        loadingRateSnapshot = true;
        rateExecutor.submit(() -> {
            ExchangeRateSnapshot snapshot = null;
            try {
                snapshot = ExchangeRateSnapshotLoader.loadWithFallback(new FirestoreFinanceRepository(), userId);
            } catch (Exception ignored) {
            }
            ExchangeRateSnapshot finalSnapshot = snapshot;
            runOnUiThread(() -> {
                loadingRateSnapshot = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalSnapshot != null) {
                    latestRateSnapshot = finalSnapshot;
                }
            });
        });
    }

    private Wallet findWalletById(String walletId) {
        if (walletId == null) {
            return null;
        }
        for (Wallet wallet : wallets) {
            if (walletId.equals(wallet.getId())) {
                return wallet;
            }
        }
        return null;
    }

    private TransactionCategory findCategoryById(String categoryId) {
        if (categoryId == null) {
            return null;
        }
        for (TransactionCategory category : categories) {
            if (categoryId.equals(category.getId())) {
                return category;
            }
        }
        return null;
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void confirmDeleteTransaction() {
        if (!isEditMode() || financeViewModel == null || pendingSubmit) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_transaction_title)
            .setMessage(R.string.dialog_delete_transaction_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                financeViewModel.deleteTransaction(editingTransactionId);
                Toast.makeText(this, R.string.message_transaction_deleted, Toast.LENGTH_SHORT).show();
                finish();
            })
            .show();
    }

    private void onSubmitCompleted(String errorMessage) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            pendingSubmit = false;
            if (errorMessage != null && !errorMessage.isBlank()) {
                showError(errorMessage);
                if (financeViewModel != null) {
                    financeViewModel.clearError();
                }
                return;
            }
            if (isEditMode()) {
                Toast.makeText(this, R.string.message_transaction_updated, Toast.LENGTH_SHORT).show();
                if (financeViewModel != null) {
                    financeViewModel.refreshRealtimeSync();
                }
                finish();
                return;
            }
            Toast.makeText(this, R.string.message_transaction_saved, Toast.LENGTH_SHORT).show();
            resetFormToDefaultState();
            if (financeViewModel != null) {
                financeViewModel.refreshRealtimeSync();
            }
        });
    }

    private void resetFormToDefaultState() {
        selectedMode = Mode.EXPENSE;
        selectedDateTimeMillis = System.currentTimeMillis();
        selectedCategoryId = null;
        selectedCategoryName = null;
        selectedDestinationWallet = null;
        prefillSourceWalletId = null;
        prefillDestinationWalletId = null;
        selectedSourceWallet = wallets.isEmpty() ? null : wallets.get(0);

        etAmount.setText("");
        etActualBalance.setText("");
        etNote.setText("");
        tvError.setVisibility(View.GONE);

        refreshModeUi();
        updateDateLabel();
        etAmount.requestFocus();
    }

    private boolean isPermissionDenied(String message) {
        return message != null && message.contains("PERMISSION_DENIED");
    }

    @Override
    protected void onDestroy() {
        rateExecutor.shutdownNow();
        super.onDestroy();
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
