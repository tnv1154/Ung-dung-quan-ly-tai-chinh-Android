package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE_NAV_ITEM_ID = "extra_source_nav_item_id";

    private static final ZoneId DEVICE_ZONE = ZoneId.systemDefault();

    private final ActivityResultLauncher<Intent> filterLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            Intent data = result.getData();
            String resultKey = data.getStringExtra(HistoryFilterActivity.EXTRA_RESULT_KEY);
            if (resultKey != null && !resultKey.isBlank()) {
                selectedFilterKey = resultKey;
            }
            String resultLabel = data.getStringExtra(HistoryFilterActivity.EXTRA_RESULT_LABEL);
            if (resultLabel != null && !resultLabel.isBlank()) {
                selectedFilterLabel = resultLabel;
            }
            boolean hasRange = data.getBooleanExtra(HistoryFilterActivity.EXTRA_RESULT_HAS_RANGE, false);
            if (hasRange) {
                long startEpoch = data.getLongExtra(HistoryFilterActivity.EXTRA_RESULT_START_EPOCH, 0L);
                long endEpoch = data.getLongExtra(HistoryFilterActivity.EXTRA_RESULT_END_EPOCH, 0L);
                if (endEpoch > startEpoch) {
                    filterStartEpochSecond = startEpoch;
                    filterEndEpochSecond = endEpoch;
                } else {
                    filterStartEpochSecond = null;
                    filterEndEpochSecond = null;
                }
            } else {
                filterStartEpochSecond = null;
                filterEndEpochSecond = null;
            }
            refreshFilterPickerLabel();
            applyFilters();
        });

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;

    private TransactionRowAdapter transactionAdapter;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvEmpty;
    private TextInputEditText etSearch;
    private MaterialButton btnFilterPicker;

    private String searchQuery = "";
    private String selectedFilterLabel = "";
    private String selectedFilterKey = HistoryFilterActivity.KEY_MONTH_THIS;
    private Long filterStartEpochSecond = null;
    private Long filterEndEpochSecond = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        bindViews();
        setupToolbar();
        setupBottomNavigation();
        setupFilterPicker();
        setupSearch();
        setupTransactionsList();
        setupSession();
        applyDefaultMonthFilter();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarHistory);
        toolbar.setTitle(R.string.app_title_history);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        tvIncome = findViewById(R.id.tvHistoryIncome);
        tvExpense = findViewById(R.id.tvHistoryExpense);
        tvEmpty = findViewById(R.id.tvHistoryEmpty);
        etSearch = findViewById(R.id.etHistorySearch);
        btnFilterPicker = findViewById(R.id.btnHistoryFilterPicker);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        int sourceNavItemId = getIntent().getIntExtra(EXTRA_SOURCE_NAV_ITEM_ID, R.id.nav_more);
        bottomNavigationView.setSelectedItemId(sourceNavItemId);
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

    private void setupFilterPicker() {
        btnFilterPicker.setOnClickListener(v -> openFilterPage());
        refreshFilterPickerLabel();
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                searchQuery = editable == null ? "" : editable.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }
        });
    }

    private void setupTransactionsList() {
        RecyclerView recyclerView = findViewById(R.id.rvHistoryTransactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionRowAdapter(
            this::confirmDeleteTransaction,
            this::openEditTransaction,
            true
        );
        recyclerView.setAdapter(transactionAdapter);
    }

    private void setupSession() {
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        sessionViewModel.getUiStateLiveData().observe(this, this::renderSessionState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (financeViewModel != null) {
            financeViewModel.refreshRealtimeSync();
        }
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
        latestState = state;
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        }
        applyFilters();
    }

    private void applyDefaultMonthFilter() {
        YearMonth month = YearMonth.now(DEVICE_ZONE);
        ZonedDateTime start = month.atDay(1).atStartOfDay(DEVICE_ZONE);
        selectedFilterKey = HistoryFilterActivity.KEY_MONTH_THIS;
        selectedFilterLabel = getString(R.string.history_filter_month_this);
        filterStartEpochSecond = start.toEpochSecond();
        filterEndEpochSecond = start.plusMonths(1).toEpochSecond();
        refreshFilterPickerLabel();
    }

    private void openFilterPage() {
        Intent intent = new Intent(this, HistoryFilterActivity.class);
        intent.putExtra(HistoryFilterActivity.EXTRA_SELECTED_KEY, selectedFilterKey);
        boolean hasRange = filterStartEpochSecond != null && filterEndEpochSecond != null;
        intent.putExtra(HistoryFilterActivity.EXTRA_HAS_RANGE, hasRange);
        if (hasRange) {
            intent.putExtra(HistoryFilterActivity.EXTRA_START_EPOCH, filterStartEpochSecond);
            intent.putExtra(HistoryFilterActivity.EXTRA_END_EPOCH, filterEndEpochSecond);
        }
        filterLauncher.launch(intent);
    }

    private void refreshFilterPickerLabel() {
        if (selectedFilterLabel == null || selectedFilterLabel.trim().isEmpty()) {
            selectedFilterLabel = getString(R.string.history_filter_all_time);
        }
        btnFilterPicker.setText(selectedFilterLabel);
    }

    private void applyFilters() {
        if (latestState == null) {
            return;
        }
        Map<String, Wallet> walletById = new HashMap<>();
        for (Wallet wallet : latestState.getWallets()) {
            walletById.put(wallet.getId(), wallet);
        }
        Map<String, TransactionCategory> categoryByKey = buildCategoryByKey(latestState.getCategories());

        List<UiTransaction> filtered = new ArrayList<>();
        for (FinanceTransaction tx : latestState.getTransactions()) {
            if (!matchesSelectedRange(tx)) {
                continue;
            }
            Wallet wallet = walletById.get(tx.getWalletId());
            String walletName = wallet == null ? getString(R.string.label_source_wallet) : wallet.getName();
            String walletIconKey = wallet == null ? "cash" : wallet.getIconKey();
            String walletAccountType = wallet == null ? "CASH" : wallet.getAccountType();
            Wallet destinationWallet = tx.getToWalletId() == null ? null : walletById.get(tx.getToWalletId());
            String destinationWalletName = destinationWallet == null ? "" : destinationWallet.getName();
            String destinationWalletIconKey = destinationWallet == null ? "cash" : destinationWallet.getIconKey();
            String destinationWalletAccountType = destinationWallet == null ? "CASH" : destinationWallet.getAccountType();
            TransactionCategory category = categoryByKey.get(categoryKey(tx.getType(), tx.getCategory()));
            String categoryIconKey = category == null ? "" : category.getIconKey();
            if (categoryIconKey == null || categoryIconKey.trim().isEmpty()) {
                categoryIconKey = CategoryUiHelper.inferIconKeyFromCategoryName(tx.getCategory(), tx.getType());
            }
            UiTransaction ui = new UiTransaction(
                tx.getId(),
                walletName,
                tx.getCategory(),
                tx.getNote(),
                tx.getType().name(),
                tx.getAmount(),
                tx.getCreatedAt(),
                categoryIconKey,
                walletIconKey,
                walletAccountType,
                destinationWalletName,
                destinationWalletIconKey,
                destinationWalletAccountType
            );
            if (!searchQuery.isEmpty()) {
                String haystack = (ui.getCategory() + " " + ui.getNote() + " " + ui.getWalletName() + " " + ui.getType())
                    .toLowerCase(Locale.ROOT);
                if (!haystack.contains(searchQuery)) {
                    continue;
                }
            }
            filtered.add(ui);
        }

        filtered.sort((left, right) -> Long.compare(right.getCreatedAt().getSeconds(), left.getCreatedAt().getSeconds()));

        double totalIncome = 0.0;
        double totalExpense = 0.0;
        for (UiTransaction tx : filtered) {
            if ("INCOME".equalsIgnoreCase(tx.getType())) {
                totalIncome += tx.getAmount();
            } else {
                totalExpense += tx.getAmount();
            }
        }

        tvIncome.setText(UiFormatters.money(totalIncome));
        tvExpense.setText(UiFormatters.money(totalExpense));
        transactionAdapter.submit(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Map<String, TransactionCategory> buildCategoryByKey(List<TransactionCategory> categories) {
        Map<String, TransactionCategory> map = new HashMap<>();
        for (TransactionCategory category : categories) {
            map.put(categoryKey(category.getType(), category.getName()), category);
        }
        return map;
    }

    private String categoryKey(TransactionType type, String categoryName) {
        return type.name() + "::" + CategoryUiHelper.normalize(categoryName);
    }

    private boolean matchesSelectedRange(FinanceTransaction tx) {
        long epochSeconds = Instant.ofEpochSecond(
            tx.getCreatedAt().getSeconds(),
            tx.getCreatedAt().getNanoseconds()
        ).atZone(DEVICE_ZONE).toEpochSecond();
        if (filterStartEpochSecond != null && epochSeconds < filterStartEpochSecond) {
            return false;
        }
        if (filterEndEpochSecond != null && epochSeconds >= filterEndEpochSecond) {
            return false;
        }
        return true;
    }

    private void confirmDeleteTransaction(UiTransaction transaction) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_transaction_title)
            .setMessage(R.string.dialog_delete_transaction_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                if (financeViewModel != null) {
                    financeViewModel.deleteTransaction(transaction.getId());
                    Toast.makeText(this, R.string.message_transaction_deleted, Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void openEditTransaction(UiTransaction transaction) {
        if (transaction == null || latestState == null) {
            return;
        }
        FinanceTransaction target = findTransactionById(transaction.getId());
        if (target == null) {
            Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra(AddTransactionActivity.EXTRA_EDIT_TRANSACTION_ID, target.getId());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, resolvePrefillMode(target.getType()));
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_SOURCE_WALLET_ID, target.getWalletId());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_DESTINATION_WALLET_ID, target.getToWalletId());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_AMOUNT, target.getAmount());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_NOTE, target.getNote());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_CATEGORY_NAME, target.getCategory());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_TIME_MILLIS, toEpochMillis(target));
        startActivity(intent);
    }

    private FinanceTransaction findTransactionById(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            return null;
        }
        for (FinanceTransaction transaction : latestState.getTransactions()) {
            if (transactionId.equals(transaction.getId())) {
                return transaction;
            }
        }
        return null;
    }

    private String resolvePrefillMode(TransactionType type) {
        if (type == TransactionType.INCOME) {
            return AddTransactionActivity.MODE_INCOME;
        }
        if (type == TransactionType.TRANSFER) {
            return AddTransactionActivity.MODE_TRANSFER;
        }
        return AddTransactionActivity.MODE_EXPENSE;
    }

    private long toEpochMillis(FinanceTransaction transaction) {
        if (transaction == null || transaction.getCreatedAt() == null) {
            return System.currentTimeMillis();
        }
        return transaction.getCreatedAt().getSeconds() * 1000L
            + (transaction.getCreatedAt().getNanoseconds() / 1_000_000L);
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
