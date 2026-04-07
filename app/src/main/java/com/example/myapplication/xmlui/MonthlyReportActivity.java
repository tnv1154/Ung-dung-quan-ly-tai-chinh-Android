package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
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
import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.FinanceTransaction;
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
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.example.myapplication.xmlui.views.ReportGroupedBarChartView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonthlyReportActivity extends AppCompatActivity {

    private enum Scope { MONTH, QUARTER }

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;
    private ExchangeRateSnapshot latestRateSnapshot;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();

    private Scope selectedScope = Scope.MONTH;

    private TextView tvReportOverviewBalance;
    private TextView tvReportOverviewIncomeLabel;
    private TextView tvReportOverviewExpenseLabel;
    private TextView tvReportOverviewIncome;
    private TextView tvReportOverviewExpense;
    private TextView tvReportBudgetTitle;
    private TextView tvReportBudgetEmpty;
    private MaterialButton btnReportOverviewMonth;
    private MaterialButton btnReportOverviewQuarter;
    private ReportGroupedBarChartView chartReportOverview;
    private ReportBudgetUsageAdapter budgetAdapter;

    @Override
    protected void onDestroy() {
        rateExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_report);
        bindViews();
        setupTopBar();
        setupScopeActions();
        setupBudgetList();
        setupBottomNavigation();
        setupSession();
        refreshScopeButtons();
    }

    private void bindViews() {
        tvReportOverviewBalance = findViewById(R.id.tvReportOverviewBalance);
        tvReportOverviewIncomeLabel = findViewById(R.id.tvReportOverviewIncomeLabel);
        tvReportOverviewExpenseLabel = findViewById(R.id.tvReportOverviewExpenseLabel);
        tvReportOverviewIncome = findViewById(R.id.tvReportOverviewIncome);
        tvReportOverviewExpense = findViewById(R.id.tvReportOverviewExpense);
        tvReportBudgetTitle = findViewById(R.id.tvReportBudgetTitle);
        tvReportBudgetEmpty = findViewById(R.id.tvReportBudgetEmpty);
        btnReportOverviewMonth = findViewById(R.id.btnReportOverviewMonth);
        btnReportOverviewQuarter = findViewById(R.id.btnReportOverviewQuarter);
        chartReportOverview = findViewById(R.id.chartReportOverview);
    }

    private void setupTopBar() {
        findViewById(R.id.btnMonthlyBack).setOnClickListener(v -> finish());
    }

    private void setupScopeActions() {
        btnReportOverviewMonth.setOnClickListener(v -> {
            if (selectedScope == Scope.MONTH) {
                return;
            }
            selectedScope = Scope.MONTH;
            refreshScopeButtons();
            if (latestState != null) {
                renderFinanceState(latestState);
            }
        });
        btnReportOverviewQuarter.setOnClickListener(v -> {
            if (selectedScope == Scope.QUARTER) {
                return;
            }
            selectedScope = Scope.QUARTER;
            refreshScopeButtons();
            if (latestState != null) {
                renderFinanceState(latestState);
            }
        });
    }

    private void setupBudgetList() {
        RecyclerView recyclerView = findViewById(R.id.rvReportBudgetUsage);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(false);
        budgetAdapter = new ReportBudgetUsageAdapter();
        recyclerView.setAdapter(budgetAdapter);
    }

    private void refreshScopeButtons() {
        styleScopeButton(btnReportOverviewMonth, selectedScope == Scope.MONTH);
        styleScopeButton(btnReportOverviewQuarter, selectedScope == Scope.QUARTER);
    }

    private void styleScopeButton(MaterialButton button, boolean selected) {
        int fill = selected ? R.color.card_bg : android.R.color.transparent;
        int text = selected ? R.color.text_primary : R.color.text_secondary;
        int stroke = R.color.divider;
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(fill)));
        button.setStrokeColor(ColorStateList.valueOf(getColor(stroke)));
        button.setTextColor(getColor(text));
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_report);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_report) {
                startActivity(new Intent(this, ReportActivity.class));
                finish();
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
        ReminderScheduler.syncFromSettings(this, state.getSettings());
        BudgetAlertNotifier.maybeNotifyExceeded(this, state);
        loadLatestRateSnapshot();

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime rangeStart = selectedScope == Scope.MONTH
            ? startOfMonth(now)
            : startOfQuarter(now);
        ZonedDateTime rangeEnd = selectedScope == Scope.MONTH
            ? rangeStart.plusMonths(1)
            : rangeStart.plusMonths(3);
        Map<String, String> walletCurrencyMap = buildWalletCurrencyMap(state.getWallets());

        double totalBalance = 0.0;
        for (Wallet wallet : state.getWallets()) {
            String walletCurrency = CurrencyRateUtils.normalizeCurrency(wallet.getCurrency());
            Double converted = CurrencyRateUtils.convert(wallet.getBalance(), walletCurrency, "VND", latestRateSnapshot);
            totalBalance += converted == null ? 0.0 : converted;
        }
        double income = sumInRange(state.getTransactions(), rangeStart, rangeEnd, TransactionType.INCOME, walletCurrencyMap);
        double expense = sumInRange(state.getTransactions(), rangeStart, rangeEnd, TransactionType.EXPENSE, walletCurrencyMap)
            + sumInRange(state.getTransactions(), rangeStart, rangeEnd, TransactionType.TRANSFER, walletCurrencyMap);

        tvReportOverviewBalance.setText(UiFormatters.money(totalBalance));
        tvReportOverviewIncome.setText(formatCompactMoney(income));
        tvReportOverviewExpense.setText(formatCompactMoney(expense));
        tvReportOverviewIncomeLabel.setText(
            selectedScope == Scope.MONTH
                ? R.string.report_income_scope_month
                : R.string.report_income_scope_quarter
        );
        tvReportOverviewExpenseLabel.setText(
            selectedScope == Scope.MONTH
                ? R.string.report_expense_scope_month
                : R.string.report_expense_scope_quarter
        );

        updateBarChart(state.getTransactions(), walletCurrencyMap, now);
        updateBudgetSection(state, walletCurrencyMap, rangeStart, rangeEnd);
    }

    private void updateBarChart(List<FinanceTransaction> transactions, Map<String, String> walletCurrencyMap, ZonedDateTime now) {
        List<ReportGroupedBarChartView.Entry> entries = new ArrayList<>();
        if (selectedScope == Scope.MONTH) {
            ZonedDateTime currentMonth = startOfMonth(now);
            for (int i = 4; i >= 0; i--) {
                ZonedDateTime monthStart = currentMonth.minusMonths(i);
                ZonedDateTime monthEnd = monthStart.plusMonths(1);
                double income = sumInRange(transactions, monthStart, monthEnd, TransactionType.INCOME, walletCurrencyMap);
                double expense = sumInRange(transactions, monthStart, monthEnd, TransactionType.EXPENSE, walletCurrencyMap)
                    + sumInRange(transactions, monthStart, monthEnd, TransactionType.TRANSFER, walletCurrencyMap);
                String label = getString(R.string.report_month_short, monthStart.getMonthValue());
                entries.add(new ReportGroupedBarChartView.Entry(label, income, expense, i == 0));
            }
            chartReportOverview.setEntries(entries);
            return;
        }

        ZonedDateTime quarterStart = startOfQuarter(now);
        for (int i = 0; i < 3; i++) {
            ZonedDateTime monthStart = quarterStart.plusMonths(i);
            ZonedDateTime monthEnd = monthStart.plusMonths(1);
            double income = sumInRange(transactions, monthStart, monthEnd, TransactionType.INCOME, walletCurrencyMap);
            double expense = sumInRange(transactions, monthStart, monthEnd, TransactionType.EXPENSE, walletCurrencyMap)
                + sumInRange(transactions, monthStart, monthEnd, TransactionType.TRANSFER, walletCurrencyMap);
            String label = getString(R.string.report_month_short, monthStart.getMonthValue());
            boolean highlighted = monthStart.getMonthValue() == now.getMonthValue();
            entries.add(new ReportGroupedBarChartView.Entry(label, income, expense, highlighted));
        }
        chartReportOverview.setEntries(entries);
    }

    private void updateBudgetSection(
        FinanceUiState state,
        Map<String, String> walletCurrencyMap,
        ZonedDateTime rangeStart,
        ZonedDateTime rangeEnd
    ) {
        if (selectedScope == Scope.MONTH) {
            tvReportBudgetTitle.setText(getString(R.string.report_budget_title_month_value, rangeStart.getMonthValue()));
        } else {
            int quarter = ((rangeStart.getMonthValue() - 1) / 3) + 1;
            tvReportBudgetTitle.setText(getString(R.string.report_budget_title_quarter_value, quarter));
        }

        List<UiReportBudgetUsage> budgetUsages = new ArrayList<>();
        for (BudgetLimit budget : state.getBudgetLimits()) {
            double limit = Math.max(0.0, budget.getLimitAmount());
            if (limit <= 0.0) {
                continue;
            }
            String categoryName = budget.getCategory() == null ? "" : budget.getCategory().trim();
            if (categoryName.isEmpty()) {
                categoryName = getString(R.string.default_category_other);
            }
            double spent = sumExpenseByCategoryInRange(state.getTransactions(), rangeStart, rangeEnd, categoryName, walletCurrencyMap);
            double ratio = spent / limit;
            TransactionCategory category = findExpenseCategory(state.getCategories(), categoryName);

            int iconRes = CategoryUiHelper.iconResForCategory(category);
            int iconBgColor = getColor(CategoryUiHelper.iconBgForCategory(category));
            int iconTintColor = getColor(CategoryUiHelper.iconTintForCategory(category));
            budgetUsages.add(new UiReportBudgetUsage(categoryName, spent, limit, ratio, iconRes, iconBgColor, iconTintColor));
        }

        budgetUsages.sort(Comparator.comparingDouble(UiReportBudgetUsage::getRatio).reversed());
        if (budgetUsages.size() > 5) {
            budgetUsages = new ArrayList<>(budgetUsages.subList(0, 5));
        }
        budgetAdapter.submit(budgetUsages);
        tvReportBudgetEmpty.setVisibility(budgetUsages.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private double sumInRange(
        List<FinanceTransaction> transactions,
        ZonedDateTime start,
        ZonedDateTime end,
        TransactionType type,
        Map<String, String> walletCurrencyMap
    ) {
        double total = 0.0;
        for (FinanceTransaction transaction : transactions) {
            if (transaction.getType() != type) {
                continue;
            }
            ZonedDateTime time = toZonedDateTime(transaction);
            if (time.isBefore(start) || !time.isBefore(end)) {
                continue;
            }
            total += amountInVnd(transaction, walletCurrencyMap);
        }
        return total;
    }

    private double sumExpenseByCategoryInRange(
        List<FinanceTransaction> transactions,
        ZonedDateTime start,
        ZonedDateTime end,
        String categoryName,
        Map<String, String> walletCurrencyMap
    ) {
        double total = 0.0;
        String normalized = normalize(categoryName);
        for (FinanceTransaction transaction : transactions) {
            if (transaction.getType() != TransactionType.EXPENSE) {
                continue;
            }
            ZonedDateTime time = toZonedDateTime(transaction);
            if (time.isBefore(start) || !time.isBefore(end)) {
                continue;
            }
            if (!normalize(transaction.getCategory()).equals(normalized)) {
                continue;
            }
            total += amountInVnd(transaction, walletCurrencyMap);
        }
        return total;
    }

    private Map<String, String> buildWalletCurrencyMap(List<Wallet> wallets) {
        Map<String, String> walletCurrencyMap = new HashMap<>();
        for (Wallet wallet : wallets) {
            walletCurrencyMap.put(wallet.getId(), CurrencyRateUtils.normalizeCurrency(wallet.getCurrency()));
        }
        return walletCurrencyMap;
    }

    private double amountInVnd(FinanceTransaction transaction, Map<String, String> walletCurrencyMap) {
        String currency = resolveTransactionCurrency(transaction, walletCurrencyMap);
        Double converted = CurrencyRateUtils.convert(transaction.getAmount(), currency, "VND", latestRateSnapshot);
        return converted == null ? 0.0 : converted;
    }

    private String resolveTransactionCurrency(FinanceTransaction transaction, Map<String, String> walletCurrencyMap) {
        String rawSourceCurrency = transaction.getSourceCurrency();
        if (rawSourceCurrency != null && !rawSourceCurrency.trim().isEmpty()) {
            return CurrencyRateUtils.normalizeCurrency(rawSourceCurrency);
        }
        String walletCurrency = walletCurrencyMap.get(transaction.getWalletId());
        return walletCurrency == null || walletCurrency.isBlank() ? "VND" : walletCurrency;
    }

    private void loadLatestRateSnapshot() {
        if (latestRateSnapshot != null) {
            return;
        }
        String userId = observedUserId;
        if (userId == null || userId.isBlank()) {
            return;
        }
        rateExecutor.submit(() -> {
            ExchangeRateSnapshot snapshot = null;
            try {
                snapshot = ExchangeRateSnapshotLoader.loadWithFallback(new FirestoreFinanceRepository(), userId);
            } catch (Exception ignored) {
            }
            ExchangeRateSnapshot finalSnapshot = snapshot;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalSnapshot == null) {
                    return;
                }
                latestRateSnapshot = finalSnapshot;
                if (latestState != null) {
                    renderFinanceState(latestState);
                }
            });
        });
    }

    private TransactionCategory findExpenseCategory(List<TransactionCategory> categories, String categoryName) {
        String normalized = normalize(categoryName);
        for (TransactionCategory category : categories) {
            if (category.getType() != TransactionType.EXPENSE) {
                continue;
            }
            if (normalize(category.getName()).equals(normalized)) {
                return category;
            }
        }
        return null;
    }

    private ZonedDateTime toZonedDateTime(FinanceTransaction transaction) {
        return Instant.ofEpochSecond(transaction.getCreatedAt().getSeconds(), transaction.getCreatedAt().getNanoseconds())
            .atZone(ZoneId.systemDefault());
    }

    private ZonedDateTime startOfMonth(ZonedDateTime value) {
        return value.withDayOfMonth(1).toLocalDate().atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfQuarter(ZonedDateTime value) {
        int quarterStartMonth = ((value.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate date = LocalDate.of(value.getYear(), quarterStartMonth, 1);
        return date.atStartOfDay(value.getZone());
    }

    private String formatCompactMoney(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000d) {
            return String.format(Locale.US, "%.1fB", value / 1_000_000_000d);
        }
        if (abs >= 1_000_000d) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000d);
        }
        if (abs >= 1_000d) {
            return String.format(Locale.US, "%.1fK", value / 1_000d);
        }
        return UiFormatters.moneyRaw(value);
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
