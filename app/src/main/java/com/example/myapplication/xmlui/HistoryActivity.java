package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {

    private enum Period {
        ALL, DAY, WEEK, MONTH, QUARTER
    }

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;

    private TransactionRowAdapter transactionAdapter;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvEmpty;
    private TextInputEditText etSearch;
    private MaterialButton btnAll;
    private MaterialButton btnDay;
    private MaterialButton btnWeek;
    private MaterialButton btnMonth;
    private MaterialButton btnQuarter;

    private Period selectedPeriod = Period.ALL;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        bindViews();
        setupBottomNavigation();
        setupFilterButtons();
        setupSearch();
        setupTransactionsList();
        setupSession();
        refreshFilterButtonUi();
    }

    private void bindViews() {
        tvIncome = findViewById(R.id.tvHistoryIncome);
        tvExpense = findViewById(R.id.tvHistoryExpense);
        tvEmpty = findViewById(R.id.tvHistoryEmpty);
        etSearch = findViewById(R.id.etHistorySearch);
        btnAll = findViewById(R.id.btnHistoryAll);
        btnDay = findViewById(R.id.btnHistoryDay);
        btnWeek = findViewById(R.id.btnHistoryWeek);
        btnMonth = findViewById(R.id.btnHistoryMonth);
        btnQuarter = findViewById(R.id.btnHistoryQuarter);
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

    private void setupFilterButtons() {
        btnAll.setOnClickListener(v -> onPeriodChanged(Period.ALL));
        btnDay.setOnClickListener(v -> onPeriodChanged(Period.DAY));
        btnWeek.setOnClickListener(v -> onPeriodChanged(Period.WEEK));
        btnMonth.setOnClickListener(v -> onPeriodChanged(Period.MONTH));
        btnQuarter.setOnClickListener(v -> onPeriodChanged(Period.QUARTER));
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
        transactionAdapter = new TransactionRowAdapter(this::confirmDeleteTransaction);
        recyclerView.setAdapter(transactionAdapter);
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
        latestState = state;
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        }
        applyFilters();
    }

    private void onPeriodChanged(Period period) {
        selectedPeriod = period;
        refreshFilterButtonUi();
        applyFilters();
    }

    private void refreshFilterButtonUi() {
        styleFilterButton(btnAll, selectedPeriod == Period.ALL);
        styleFilterButton(btnDay, selectedPeriod == Period.DAY);
        styleFilterButton(btnWeek, selectedPeriod == Period.WEEK);
        styleFilterButton(btnMonth, selectedPeriod == Period.MONTH);
        styleFilterButton(btnQuarter, selectedPeriod == Period.QUARTER);
    }

    private void styleFilterButton(MaterialButton button, boolean selected) {
        int bg = selected ? R.color.blue_primary : R.color.card_bg;
        int fg = selected ? android.R.color.white : R.color.text_primary;
        button.setBackgroundColor(getColor(bg));
        button.setTextColor(getColor(fg));
    }

    private void applyFilters() {
        if (latestState == null) {
            return;
        }
        Map<String, String> walletNameById = new HashMap<>();
        for (Wallet wallet : latestState.getWallets()) {
            walletNameById.put(wallet.getId(), wallet.getName());
        }

        List<UiTransaction> filtered = new ArrayList<>();
        for (FinanceTransaction tx : latestState.getTransactions()) {
            if (!matchesPeriod(tx, selectedPeriod)) {
                continue;
            }
            String walletName = walletNameById.getOrDefault(tx.getWalletId(), getString(R.string.label_source_wallet));
            UiTransaction ui = new UiTransaction(
                tx.getId(),
                walletName,
                tx.getCategory(),
                tx.getNote(),
                tx.getType().name(),
                tx.getAmount(),
                tx.getCreatedAt()
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

    private boolean matchesPeriod(FinanceTransaction tx, Period period) {
        if (period == Period.ALL) {
            return true;
        }
        ZonedDateTime date = Instant.ofEpochSecond(tx.getCreatedAt().getSeconds(), tx.getCreatedAt().getNanoseconds())
            .atZone(ZoneId.systemDefault());
        ZonedDateTime now = ZonedDateTime.now();
        switch (period) {
            case DAY:
                return date.toLocalDate().equals(now.toLocalDate());
            case WEEK: {
                ZonedDateTime weekStart = now.minusDays(now.getDayOfWeek().getValue() - 1L).toLocalDate().atStartOfDay(now.getZone());
                ZonedDateTime weekEnd = weekStart.plusDays(7);
                return !date.isBefore(weekStart) && date.isBefore(weekEnd);
            }
            case QUARTER: {
                int quarterDate = ((date.getMonthValue() - 1) / 3) + 1;
                int quarterNow = ((now.getMonthValue() - 1) / 3) + 1;
                return date.getYear() == now.getYear() && quarterDate == quarterNow;
            }
            case MONTH:
            default:
                return date.getYear() == now.getYear() && date.getMonth() == now.getMonth();
        }
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

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
