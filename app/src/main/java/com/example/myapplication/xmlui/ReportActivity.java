package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
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
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportActivity extends AppCompatActivity {

    private enum Period { DAY, WEEK, MONTH, QUARTER }

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private Period selectedPeriod = Period.MONTH;
    private MaterialButton btnDay;
    private MaterialButton btnWeek;
    private MaterialButton btnMonth;
    private MaterialButton btnQuarter;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvNet;
    private TextView tvNoCategory;
    private TextView tvTrendSummary;
    private ReportCategoryAdapter reportCategoryAdapter;
    private FinanceUiState lastStableState;
    private long lastStableRealtimeMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        bindViews();
        setupBottomNavigation();
        setupPeriodActions();
        setupCategoryList();
        setupSession();
        refreshPeriodButtonUi();
    }

    private void bindViews() {
        btnDay = findViewById(R.id.btnReportDay);
        btnWeek = findViewById(R.id.btnReportWeek);
        btnMonth = findViewById(R.id.btnReportMonth);
        btnQuarter = findViewById(R.id.btnReportQuarter);
        tvIncome = findViewById(R.id.tvReportIncome);
        tvExpense = findViewById(R.id.tvReportExpense);
        tvNet = findViewById(R.id.tvReportNet);
        tvNoCategory = findViewById(R.id.tvNoCategoryData);
        tvTrendSummary = findViewById(R.id.tvTrendSummary);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_report);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_report) {
                return true;
            }
            if (id == R.id.nav_accounts) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_overview) {
                startActivity(new Intent(this, OverviewActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_add) {
                Intent addIntent = new Intent(this, AddTransactionActivity.class);
                addIntent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_EXPENSE);
                startActivity(addIntent);
                return false;
            }
            if (id == R.id.nav_more) {
                startActivity(new Intent(this, MoreActivity.class));
                finish();
                return true;
            }
            Toast.makeText(this, R.string.message_feature_in_progress, Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    private void setupPeriodActions() {
        btnDay.setOnClickListener(v -> onPeriodChanged(Period.DAY));
        btnWeek.setOnClickListener(v -> onPeriodChanged(Period.WEEK));
        btnMonth.setOnClickListener(v -> onPeriodChanged(Period.MONTH));
        btnQuarter.setOnClickListener(v -> onPeriodChanged(Period.QUARTER));
    }

    private void setupCategoryList() {
        RecyclerView recyclerView = findViewById(R.id.rvCategoryBreakdown);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        reportCategoryAdapter = new ReportCategoryAdapter();
        recyclerView.setAdapter(reportCategoryAdapter);
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

    private void onPeriodChanged(Period period) {
        selectedPeriod = period;
        refreshPeriodButtonUi();
        if (financeViewModel != null && financeViewModel.getUiStateLiveData().getValue() != null) {
            renderFinanceState(financeViewModel.getUiStateLiveData().getValue());
        }
    }

    private void refreshPeriodButtonUi() {
        stylePeriodButton(btnDay, selectedPeriod == Period.DAY);
        stylePeriodButton(btnWeek, selectedPeriod == Period.WEEK);
        stylePeriodButton(btnMonth, selectedPeriod == Period.MONTH);
        stylePeriodButton(btnQuarter, selectedPeriod == Period.QUARTER);
    }

    private void stylePeriodButton(MaterialButton button, boolean selected) {
        int bg = selected ? R.color.blue_primary : R.color.card_bg;
        int fg = selected ? android.R.color.white : R.color.text_primary;
        button.setBackgroundColor(getColor(bg));
        button.setTextColor(getColor(fg));
    }

    private void renderFinanceState(@NonNull FinanceUiState state) {
        FinanceUiState displayState = state;
        if (shouldKeepStableState(state)) {
            displayState = lastStableState;
        } else if (hasDisplayData(state)) {
            lastStableState = state;
            lastStableRealtimeMs = SystemClock.elapsedRealtime();
        }

        List<FinanceTransaction> current = filterByPeriod(displayState.getTransactions(), selectedPeriod, 0);
        List<FinanceTransaction> previous = filterByPeriod(displayState.getTransactions(), selectedPeriod, 1);

        double income = sum(current, TransactionType.INCOME);
        double expense = sum(current, TransactionType.EXPENSE) + sum(current, TransactionType.TRANSFER);
        double net = income - expense;

        tvIncome.setText(UiFormatters.money(income));
        tvExpense.setText(UiFormatters.money(expense));
        tvNet.setText(UiFormatters.money(net));
        tvNet.setTextColor(getColor(net >= 0 ? R.color.blue_primary : R.color.error_red));

        updateCategoryBreakdown(current);
        updateTrend(current, previous);
    }

    private boolean hasDisplayData(FinanceUiState state) {
        return !state.getTransactions().isEmpty()
            || !state.getWallets().isEmpty()
            || !state.getBudgetLimits().isEmpty();
    }

    private boolean shouldKeepStableState(FinanceUiState incoming) {
        if (lastStableState == null || !hasDisplayData(lastStableState)) {
            return false;
        }
        boolean incomingLooksEmpty = incoming.getTransactions().isEmpty()
            && incoming.getWallets().isEmpty()
            && incoming.getBudgetLimits().isEmpty();
        if (!incomingLooksEmpty) {
            return false;
        }
        long ageMs = SystemClock.elapsedRealtime() - lastStableRealtimeMs;
        return ageMs <= 2500L;
    }

    private void updateCategoryBreakdown(List<FinanceTransaction> transactions) {
        double totalExpense = 0.0;
        Map<String, Double> byCategory = new HashMap<>();
        for (FinanceTransaction tx : transactions) {
            if (tx.getType() != TransactionType.EXPENSE) {
                continue;
            }
            totalExpense += tx.getAmount();
            String category = tx.getCategory() == null || tx.getCategory().isEmpty()
                ? getString(R.string.default_category_other)
                : tx.getCategory();
            byCategory.put(category, byCategory.getOrDefault(category, 0.0) + tx.getAmount());
        }

        List<UiReportCategory> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : byCategory.entrySet()) {
            double ratio = totalExpense <= 0.0 ? 0.0 : entry.getValue() / totalExpense;
            result.add(new UiReportCategory(entry.getKey(), entry.getValue(), ratio));
        }
        result.sort(Comparator.comparingDouble(UiReportCategory::getAmount).reversed());
        reportCategoryAdapter.submit(result);
        tvNoCategory.setVisibility(result.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void updateTrend(List<FinanceTransaction> current, List<FinanceTransaction> previous) {
        double currentNet = sum(current, TransactionType.INCOME) - (sum(current, TransactionType.EXPENSE) + sum(current, TransactionType.TRANSFER));
        double previousNet = sum(previous, TransactionType.INCOME) - (sum(previous, TransactionType.EXPENSE) + sum(previous, TransactionType.TRANSFER));
        if (previous.isEmpty() || Math.abs(previousNet) < 0.0001) {
            tvTrendSummary.setText(R.string.report_trend_no_previous);
            return;
        }
        double delta = currentNet - previousNet;
        double ratio = Math.abs(delta / previousNet);
        String percent = String.format(Locale.US, "%.1f%%", ratio * 100.0);
        if (Math.abs(delta) < 0.0001) {
            tvTrendSummary.setText(R.string.report_trend_flat);
        } else if (delta > 0) {
            tvTrendSummary.setText(getString(R.string.report_trend_up, percent));
        } else {
            tvTrendSummary.setText(getString(R.string.report_trend_down, percent));
        }
    }

    private double sum(List<FinanceTransaction> transactions, TransactionType type) {
        double total = 0.0;
        for (FinanceTransaction tx : transactions) {
            if (tx.getType() == type) {
                total += tx.getAmount();
            }
        }
        return total;
    }

    private List<FinanceTransaction> filterByPeriod(List<FinanceTransaction> transactions, Period period, int offset) {
        ZonedDateTime now = ZonedDateTime.now();
        if (period == Period.DAY) {
            now = now.minusDays(offset);
        } else if (period == Period.WEEK) {
            now = now.minusWeeks(offset);
        } else if (period == Period.MONTH) {
            now = now.minusMonths(offset);
        } else {
            now = now.minusMonths(offset * 3L);
        }
        List<FinanceTransaction> out = new ArrayList<>();
        for (FinanceTransaction tx : transactions) {
            ZonedDateTime date = Instant.ofEpochSecond(tx.getCreatedAt().getSeconds(), tx.getCreatedAt().getNanoseconds())
                .atZone(ZoneId.systemDefault());
            if (matchesPeriod(date, now, period)) {
                out.add(tx);
            }
        }
        return out;
    }

    private boolean matchesPeriod(ZonedDateTime date, ZonedDateTime pivot, Period period) {
        switch (period) {
            case DAY:
                return date.toLocalDate().equals(pivot.toLocalDate());
            case WEEK:
                ZonedDateTime weekStart = pivot.minusDays(pivot.getDayOfWeek().getValue() - 1L).toLocalDate().atStartOfDay(pivot.getZone());
                ZonedDateTime weekEnd = weekStart.plusDays(7);
                return !date.isBefore(weekStart) && date.isBefore(weekEnd);
            case QUARTER:
                int q1 = ((date.getMonthValue() - 1) / 3) + 1;
                int q2 = ((pivot.getMonthValue() - 1) / 3) + 1;
                return date.getYear() == pivot.getYear() && q1 == q2;
            case MONTH:
            default:
                return date.getYear() == pivot.getYear() && date.getMonth() == pivot.getMonth();
        }
    }

    private void showSignOutDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_sign_out_title)
            .setMessage(R.string.dialog_sign_out_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_sign_out, (dialog, which) -> sessionViewModel.signOut())
            .show();
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
