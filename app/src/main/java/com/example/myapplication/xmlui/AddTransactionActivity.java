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
import com.example.myapplication.xmlui.currency.CurrencyRateUtils;
import com.example.myapplication.xmlui.currency.ExchangeRateSnapshotLoader;
import com.google.firebase.Timestamp;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private static final double BALANCE_EPSILON = 0.000001d;

    public static final String EXTRA_PREFILL_MODE = "extra_prefill_mode";
    public static final String EXTRA_PREFILL_SOURCE_WALLET_ID = "extra_prefill_source_wallet_id";
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
            selectedCategoryId = data.getStringExtra(CategoryPickerActivity.EXTRA_SELECTED_CATEGORY_ID);
            selectedCategoryName = data.getStringExtra(CategoryPickerActivity.EXTRA_SELECTED_CATEGORY_NAME);
            updateSelectionButtons();
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
    private long selectedDateTimeMillis;

    private MaterialButton btnModeSelector;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);
        bindViews();
        applyIntentPrefill();
        selectedDateTimeMillis = System.currentTimeMillis();
        setupActions();
        setupBottomNavigation();
        setupSession();
        refreshModeUi();
        updateDateLabel();
    }

    private void bindViews() {
        btnModeSelector = findViewById(R.id.btnModeSelector);
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
    }

    private void setupActions() {
        ImageButton btnHistory = findViewById(R.id.btnAddBack);
        ImageButton btnTopSave = findViewById(R.id.btnTopSaveTransaction);
        View rowCategorySelector = findViewById(R.id.rowCategorySelector);
        View rowSourceWallet = findViewById(R.id.rowSourceWallet);
        View rowTransferSource = findViewById(R.id.rowTransferSource);
        View rowTransferDestination = findViewById(R.id.rowTransferDestination);
        ImageButton btnSwapTransferWallets = findViewById(R.id.btnSwapTransferWallets);
        View rowDateTime = findViewById(R.id.rowTransactionDateTime);

        btnHistory.setOnClickListener(v -> {
            Intent historyIntent = new Intent(this, HistoryActivity.class);
            historyIntent.putExtra(HistoryActivity.EXTRA_SOURCE_NAV_ITEM_ID, R.id.nav_add);
            startActivity(historyIntent);
        });
        btnTopSave.setOnClickListener(v -> submitTransaction());
        btnSave.setOnClickListener(v -> submitTransaction());
        btnModeSelector.setOnClickListener(v -> showModeSelector());
        MoneyInputFormatter.attach(etAmount);
        MoneyInputFormatter.attach(etActualBalance);
        rowCategorySelector.setOnClickListener(v -> openCategoryPicker());
        tvCategoryAll.setOnClickListener(v -> openCategoryPicker());
        btnManageCategories.setOnClickListener(v -> openCategoryEditor());
        rowSourceWallet.setOnClickListener(v -> chooseSourceWallet());
        rowTransferSource.setOnClickListener(v -> chooseSourceWallet());
        rowTransferDestination.setOnClickListener(v -> chooseDestinationWallet());
        btnSwapTransferWallets.setOnClickListener(v -> swapTransferWallets());
        rowDateTime.setOnClickListener(v -> openDateTimePicker());
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

        if (selectedCategoryId != null) {
            TransactionCategory category = findCategoryById(selectedCategoryId);
            if (category != null) {
                selectedCategoryName = category.getName();
            } else {
                selectedCategoryId = null;
                selectedCategoryName = null;
            }
        }
        ensureDefaultCategorySelection();
        renderQuickCategories();
        updateSelectionButtons();

        String errorMessage = state.getErrorMessage();
        if (errorMessage != null && isPermissionDenied(errorMessage)) {
            financeViewModel.clearError();
        }
    }

    private void applyIntentPrefill() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        prefillSourceWalletId = intent.getStringExtra(EXTRA_PREFILL_SOURCE_WALLET_ID);
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
        ensureDefaultCategorySelection();
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

    private void ensureDefaultCategorySelection() {
        if (selectedMode != Mode.EXPENSE && selectedMode != Mode.INCOME) {
            return;
        }
        if (selectedCategoryName != null && !selectedCategoryName.trim().isEmpty()) {
            return;
        }
        TransactionType type = selectedMode == Mode.INCOME ? TransactionType.INCOME : TransactionType.EXPENSE;
        TransactionCategory fallback = null;
        for (TransactionCategory category : categories) {
            if (category.getType() != type) {
                continue;
            }
            if (type == TransactionType.EXPENSE && (category.getParentName() == null || category.getParentName().trim().isEmpty())) {
                continue;
            }
            fallback = category;
            break;
        }
        if (fallback == null) {
            for (TransactionCategory category : categories) {
                if (category.getType() == type) {
                    fallback = category;
                    break;
                }
            }
        }
        if (fallback != null) {
            selectedCategoryId = fallback.getId();
            selectedCategoryName = fallback.getName();
        }
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
            ivIcon.setImageResource(CategoryUiHelper.iconResForCategory(category));
            ivIcon.setImageTintList(ContextCompat.getColorStateList(this, CategoryUiHelper.iconTintForCategory(category)));
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
        ivCategorySelectedIcon.setImageResource(CategoryUiHelper.iconResForCategory(selectedCategory));
        ivCategorySelectedIcon.setImageTintList(
            ContextCompat.getColorStateList(this, CategoryUiHelper.iconTintForCategory(selectedCategory))
        );
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
            Double actual = parseDouble(etActualBalance.getText() == null ? "" : etActualBalance.getText().toString());
            if (actual == null || actual < 0.0) {
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

        Double amount = parseDouble(etAmount.getText() == null ? "" : etAmount.getText().toString());
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
        if ((type == TransactionType.EXPENSE || type == TransactionType.TRANSFER)
            && isAmountGreaterThanBalance(amount, selectedSourceWallet.getBalance())) {
            showError(getString(R.string.error_insufficient_balance));
            return;
        }

        pendingSubmit = true;
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

    private Double parseDouble(String raw) {
        String cleaned = MoneyInputFormatter.normalizeAmount(raw);
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isAmountGreaterThanBalance(double amount, double balance) {
        return amount - balance > BALANCE_EPSILON;
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
            Toast.makeText(this, R.string.message_transaction_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
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
