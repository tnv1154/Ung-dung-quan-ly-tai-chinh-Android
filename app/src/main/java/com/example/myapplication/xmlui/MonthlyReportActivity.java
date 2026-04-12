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
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonthlyReportActivity extends AppCompatActivity {

    private enum Scope { WEEK, MONTH, YEAR }

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;
    private ExchangeRateSnapshot latestRateSnapshot;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();

    private Scope selectedScope = Scope.WEEK;
    private ZonedDateTime selectedStart = startOfWeek(ZonedDateTime.now());
    private ReportFilterState reportFilter = ReportFilterState.all();

    private TextView tvReportOverviewBalance;
    private TextView tvReportOverviewIncomeLabel;
    private TextView tvReportOverviewExpenseLabel;
    private TextView tvReportOverviewIncome;
    private TextView tvReportOverviewExpense;
    private TextView tvReportOverviewIncomeDelta;
    private TextView tvReportOverviewExpenseDelta;
    private TextView tvReportBudgetTitle;
    private TextView tvReportBudgetEmpty;
    private MaterialButton btnReportOverviewWeek;
    private MaterialButton btnReportOverviewMonth;
    private MaterialButton btnReportOverviewYear;
    private MaterialButton btnReportOverviewPeriodPicker;
    private MaterialButton btnReportOverviewFilter;
    private ReportGroupedBarChartView chartReportOverview;
    private ReportBudgetUsageAdapter budgetAdapter;
    private final List<RangeBucket> barBuckets = new ArrayList<>();
    private final Map<String, Double> aggregateCache = new HashMap<>();
    private String aggregateCacheSeed = "";

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
        refreshPeriodPickerLabel();
        refreshFilterButton();
        setupBarChartInteractions();
    }

    private void bindViews() {
        tvReportOverviewBalance = findViewById(R.id.tvReportOverviewBalance);
        tvReportOverviewIncomeLabel = findViewById(R.id.tvReportOverviewIncomeLabel);
        tvReportOverviewExpenseLabel = findViewById(R.id.tvReportOverviewExpenseLabel);
        tvReportOverviewIncome = findViewById(R.id.tvReportOverviewIncome);
        tvReportOverviewExpense = findViewById(R.id.tvReportOverviewExpense);
        tvReportOverviewIncomeDelta = findViewById(R.id.tvReportOverviewIncomeDelta);
        tvReportOverviewExpenseDelta = findViewById(R.id.tvReportOverviewExpenseDelta);
        tvReportBudgetTitle = findViewById(R.id.tvReportBudgetTitle);
        tvReportBudgetEmpty = findViewById(R.id.tvReportBudgetEmpty);
        btnReportOverviewWeek = findViewById(R.id.btnReportOverviewWeek);
        btnReportOverviewMonth = findViewById(R.id.btnReportOverviewMonth);
        btnReportOverviewYear = findViewById(R.id.btnReportOverviewYear);
        btnReportOverviewPeriodPicker = findViewById(R.id.btnReportOverviewPeriodPicker);
        btnReportOverviewFilter = findViewById(R.id.btnReportOverviewFilter);
        chartReportOverview = findViewById(R.id.chartReportOverview);
    }

    private void setupTopBar() {
        findViewById(R.id.btnMonthlyBack).setOnClickListener(v -> finish());
    }

    private void setupScopeActions() {
        btnReportOverviewWeek.setOnClickListener(v -> selectScope(Scope.WEEK));
        btnReportOverviewMonth.setOnClickListener(v -> selectScope(Scope.MONTH));
        btnReportOverviewYear.setOnClickListener(v -> selectScope(Scope.YEAR));
        btnReportOverviewPeriodPicker.setOnClickListener(v -> {
            List<PeriodOption> options = buildPeriodOptions();
            String[] labels = new String[options.size()];
            int checked = 0;
            for (int i = 0; i < options.size(); i++) {
                labels[i] = options.get(i).label;
                if (samePeriodStart(options.get(i).start, selectedStart)) {
                    checked = i;
                }
            }
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.report_choose_period_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    selectedStart = options.get(which).start;
                    refreshPeriodPickerLabel();
                    if (latestState != null) {
                        renderFinanceState(latestState);
                    }
                    dialog.dismiss();
                })
                .show();
        });
        btnReportOverviewFilter.setOnClickListener(v -> {
            List<Wallet> wallets = latestState == null ? new ArrayList<>() : latestState.getWallets();
            ReportFilterDialog.show(this, wallets, reportFilter, nextFilter -> {
                reportFilter = nextFilter;
                refreshFilterButton();
                if (latestState != null) {
                    renderFinanceState(latestState);
                }
            });
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
        styleScopeButton(btnReportOverviewWeek, selectedScope == Scope.WEEK);
        styleScopeButton(btnReportOverviewMonth, selectedScope == Scope.MONTH);
        styleScopeButton(btnReportOverviewYear, selectedScope == Scope.YEAR);
    }

    private void selectScope(Scope scope) {
        if (selectedScope == scope) {
            return;
        }
        selectedScope = scope;
        selectedStart = startForScope(scope, ZonedDateTime.now());
        refreshScopeButtons();
        refreshPeriodPickerLabel();
        if (latestState != null) {
            renderFinanceState(latestState);
        }
    }

    private void refreshPeriodPickerLabel() {
        if (selectedScope == Scope.WEEK) {
            btnReportOverviewPeriodPicker.setText(formatWeekLabel(selectedStart));
        } else if (selectedScope == Scope.MONTH) {
            btnReportOverviewPeriodPicker.setText(formatMonthLabel(selectedStart));
        } else {
            btnReportOverviewPeriodPicker.setText(formatYearLabel(selectedStart));
        }
    }

    private boolean samePeriodStart(ZonedDateTime first, ZonedDateTime second) {
        return first.toEpochSecond() == second.toEpochSecond();
    }

    private void refreshFilterButton() {
        if (reportFilter.isAll()) {
            btnReportOverviewFilter.setText(R.string.action_filter);
            return;
        }
        int activeGroups = (reportFilter.hasWalletFilter() ? 1 : 0) + (reportFilter.hasTypeFilter() ? 1 : 0);
        btnReportOverviewFilter.setText(getString(R.string.report_filter_with_count, activeGroups));
    }

    private void setupBarChartInteractions() {
        chartReportOverview.setOnEntryClickListener((index, entry) -> {
            if (index < 0 || index >= barBuckets.size()) {
                return;
            }
            RangeBucket bucket = barBuckets.get(index);
            Intent intent = ReportDrilldownActivity.createIntent(
                this,
                getString(R.string.report_drilldown_title_with_label, bucket.label),
                bucket.start,
                bucket.end,
                null,
                reportFilter.getWalletIds(),
                reportFilter.getTransactionTypes()
            );
            startActivity(intent);
        });
    }

    private List<PeriodOption> buildPeriodOptions() {
        List<PeriodOption> options = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        if (selectedScope == Scope.WEEK) {
            for (int i = 0; i < 24; i++) {
                ZonedDateTime start = startOfWeek(now.minusWeeks(i));
                options.add(new PeriodOption(start, formatWeekLabel(start)));
            }
            return options;
        }
        if (selectedScope == Scope.MONTH) {
            for (int i = 0; i < 24; i++) {
                ZonedDateTime start = startOfMonth(now.minusMonths(i));
                options.add(new PeriodOption(start, formatMonthLabel(start)));
            }
            return options;
        }
        for (int i = 0; i < 10; i++) {
            ZonedDateTime start = startOfYear(now.minusYears(i));
            options.add(new PeriodOption(start, formatYearLabel(start)));
        }
        return options;
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
        ZonedDateTime rangeStart = selectedStart;
        ZonedDateTime rangeEnd = endForScope(selectedScope, rangeStart);
        Map<String, String> walletCurrencyMap = buildWalletCurrencyMap(state.getWallets());
        updateAggregateCacheSeed(state, rangeStart, walletCurrencyMap);

        double totalBalance = 0.0;
        for (Wallet wallet : state.getWallets()) {
            String walletCurrency = CurrencyRateUtils.normalizeCurrency(wallet.getCurrency());
            Double converted = CurrencyRateUtils.convert(wallet.getBalance(), walletCurrency, "VND", latestRateSnapshot);
            totalBalance += converted == null ? 0.0 : converted;
        }
        double income = sumInRange(state.getTransactions(), rangeStart, rangeEnd, TransactionType.INCOME, walletCurrencyMap);
        double expense = sumInRange(state.getTransactions(), rangeStart, rangeEnd, TransactionType.EXPENSE, walletCurrencyMap)
            + sumInRange(state.getTransactions(), rangeStart, rangeEnd, TransactionType.TRANSFER, walletCurrencyMap);
        ZonedDateTime previousStart = previousStartForScope(selectedScope, rangeStart);
        double previousIncome = sumInRange(state.getTransactions(), previousStart, rangeStart, TransactionType.INCOME, walletCurrencyMap);
        double previousExpense = sumInRange(state.getTransactions(), previousStart, rangeStart, TransactionType.EXPENSE, walletCurrencyMap)
            + sumInRange(state.getTransactions(), previousStart, rangeStart, TransactionType.TRANSFER, walletCurrencyMap);

        tvReportOverviewBalance.setText(UiFormatters.money(totalBalance));
        tvReportOverviewBalance.setTextColor(getColor(totalBalance < 0.0 ? R.color.error_red : R.color.text_primary));
        tvReportOverviewIncome.setText(formatCompactMoney(income));
        tvReportOverviewExpense.setText(formatCompactMoney(expense));
        tvReportOverviewIncomeDelta.setText(formatDeltaText(income, previousIncome));
        tvReportOverviewExpenseDelta.setText(formatDeltaText(expense, previousExpense));
        tvReportOverviewIncomeLabel.setText(
            selectedScope == Scope.WEEK
                ? R.string.report_income_scope_week
                : selectedScope == Scope.MONTH
                ? R.string.report_income_scope_month
                : R.string.report_income_scope_year
        );
        tvReportOverviewExpenseLabel.setText(
            selectedScope == Scope.WEEK
                ? R.string.report_expense_scope_week
                : selectedScope == Scope.MONTH
                ? R.string.report_expense_scope_month
                : R.string.report_expense_scope_year
        );

        updateBarChart(state.getTransactions(), walletCurrencyMap, rangeStart, now);
        updateBudgetSection(state, walletCurrencyMap, rangeStart, rangeEnd);
    }

    private void updateBarChart(
        List<FinanceTransaction> transactions,
        Map<String, String> walletCurrencyMap,
        ZonedDateTime rangeStart,
        ZonedDateTime now
    ) {
        List<ReportGroupedBarChartView.Entry> entries = new ArrayList<>();
        barBuckets.clear();
        if (selectedScope == Scope.WEEK) {
            for (int i = 0; i < 7; i++) {
                ZonedDateTime dayStart = rangeStart.plusDays(i);
                ZonedDateTime dayEnd = dayStart.plusDays(1);
                double income = sumInRange(transactions, dayStart, dayEnd, TransactionType.INCOME, walletCurrencyMap);
                double expense = sumInRange(transactions, dayStart, dayEnd, TransactionType.EXPENSE, walletCurrencyMap)
                    + sumInRange(transactions, dayStart, dayEnd, TransactionType.TRANSFER, walletCurrencyMap);
                boolean highlighted = sameDay(dayStart, now);
                String label = shortWeekday(dayStart);
                entries.add(new ReportGroupedBarChartView.Entry(label, income, expense, highlighted));
                barBuckets.add(new RangeBucket(label, dayStart, dayEnd));
            }
            chartReportOverview.setEntries(entries);
            return;
        }

        if (selectedScope == Scope.MONTH) {
            ZonedDateTime monthEnd = rangeStart.plusMonths(1);
            ZonedDateTime cursor = rangeStart;
            int weekIndex = 1;
            while (cursor.isBefore(monthEnd)) {
                ZonedDateTime bucketEnd = cursor.plusDays(7);
                if (bucketEnd.isAfter(monthEnd)) {
                    bucketEnd = monthEnd;
                }
                double income = sumInRange(transactions, cursor, bucketEnd, TransactionType.INCOME, walletCurrencyMap);
                double expense = sumInRange(transactions, cursor, bucketEnd, TransactionType.EXPENSE, walletCurrencyMap)
                    + sumInRange(transactions, cursor, bucketEnd, TransactionType.TRANSFER, walletCurrencyMap);
                boolean highlighted = !now.isBefore(cursor) && now.isBefore(bucketEnd);
                entries.add(new ReportGroupedBarChartView.Entry(
                    getString(R.string.report_week_short, weekIndex),
                    income,
                    expense,
                    highlighted
                ));
                barBuckets.add(new RangeBucket(getString(R.string.report_week_short, weekIndex), cursor, bucketEnd));
                weekIndex++;
                cursor = bucketEnd;
            }
            chartReportOverview.setEntries(entries);
            return;
        }

        for (int i = 0; i < 12; i++) {
            ZonedDateTime monthStart = rangeStart.plusMonths(i);
            ZonedDateTime monthEnd = monthStart.plusMonths(1);
            double income = sumInRange(transactions, monthStart, monthEnd, TransactionType.INCOME, walletCurrencyMap);
            double expense = sumInRange(transactions, monthStart, monthEnd, TransactionType.EXPENSE, walletCurrencyMap)
                + sumInRange(transactions, monthStart, monthEnd, TransactionType.TRANSFER, walletCurrencyMap);
            boolean highlighted = monthStart.getYear() == now.getYear() && monthStart.getMonthValue() == now.getMonthValue();
            entries.add(new ReportGroupedBarChartView.Entry(
                getString(R.string.report_month_short, monthStart.getMonthValue()),
                income,
                expense,
                highlighted
            ));
            barBuckets.add(new RangeBucket(getString(R.string.report_month_short, monthStart.getMonthValue()), monthStart, monthEnd));
        }
        chartReportOverview.setEntries(entries);
    }

    private void updateBudgetSection(
        FinanceUiState state,
        Map<String, String> walletCurrencyMap,
        ZonedDateTime rangeStart,
        ZonedDateTime rangeEnd
    ) {
        if (selectedScope == Scope.WEEK) {
            int week = rangeStart.get(WeekFields.ISO.weekOfWeekBasedYear());
            tvReportBudgetTitle.setText(getString(R.string.report_budget_title_week_value, week, rangeStart.getYear()));
        } else if (selectedScope == Scope.MONTH) {
            tvReportBudgetTitle.setText(getString(R.string.report_budget_title_month_value, rangeStart.getMonthValue()));
        } else {
            tvReportBudgetTitle.setText(getString(R.string.report_budget_title_year_value, rangeStart.getYear()));
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
        if (!reportFilter.includesType(type)) {
            return 0.0;
        }
        String key = "sum|" + type.name() + "|" + start.toEpochSecond() + "|" + end.toEpochSecond();
        Double cached = aggregateCache.get(key);
        if (cached != null) {
            return cached;
        }
        double total = 0.0;
        for (FinanceTransaction transaction : transactions) {
            if (transaction.getType() != type) {
                continue;
            }
            if (!reportFilter.includesWallet(transaction.getWalletId())) {
                continue;
            }
            ZonedDateTime time = toZonedDateTime(transaction);
            if (time.isBefore(start) || !time.isBefore(end)) {
                continue;
            }
            total += amountInVnd(transaction, walletCurrencyMap);
        }
        aggregateCache.put(key, total);
        return total;
    }

    private double sumExpenseByCategoryInRange(
        List<FinanceTransaction> transactions,
        ZonedDateTime start,
        ZonedDateTime end,
        String categoryName,
        Map<String, String> walletCurrencyMap
    ) {
        if (!reportFilter.includesType(TransactionType.EXPENSE)) {
            return 0.0;
        }
        String normalized = normalize(categoryName);
        String key = "cat|" + normalized + "|" + start.toEpochSecond() + "|" + end.toEpochSecond();
        Double cached = aggregateCache.get(key);
        if (cached != null) {
            return cached;
        }
        double total = 0.0;
        for (FinanceTransaction transaction : transactions) {
            if (transaction.getType() != TransactionType.EXPENSE) {
                continue;
            }
            if (!reportFilter.includesWallet(transaction.getWalletId())) {
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
        aggregateCache.put(key, total);
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

    private ZonedDateTime startOfWeek(ZonedDateTime value) {
        int day = value.getDayOfWeek().getValue();
        return value.minusDays(day - 1L).toLocalDate().atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfYear(ZonedDateTime value) {
        LocalDate date = LocalDate.of(value.getYear(), 1, 1);
        return date.atStartOfDay(value.getZone());
    }

    private ZonedDateTime startForScope(Scope scope, ZonedDateTime reference) {
        if (scope == Scope.WEEK) {
            return startOfWeek(reference);
        }
        if (scope == Scope.MONTH) {
            return startOfMonth(reference);
        }
        return startOfYear(reference);
    }

    private ZonedDateTime endForScope(Scope scope, ZonedDateTime start) {
        if (scope == Scope.WEEK) {
            return start.plusWeeks(1);
        }
        if (scope == Scope.MONTH) {
            return start.plusMonths(1);
        }
        return start.plusYears(1);
    }

    private ZonedDateTime previousStartForScope(Scope scope, ZonedDateTime currentStart) {
        if (scope == Scope.WEEK) {
            return currentStart.minusWeeks(1);
        }
        if (scope == Scope.MONTH) {
            return currentStart.minusMonths(1);
        }
        return currentStart.minusYears(1);
    }

    private void updateAggregateCacheSeed(
        FinanceUiState state,
        ZonedDateTime rangeStart,
        Map<String, String> walletCurrencyMap
    ) {
        String seed =
            state.getTransactions().size()
                + "|"
                + selectedScope.name()
                + "|"
                + rangeStart.toEpochSecond()
                + "|"
                + reportFilter.getWalletIds().hashCode()
                + "|"
                + reportFilter.getTransactionTypes().hashCode()
                + "|"
                + walletCurrencyMap.hashCode()
                + "|"
                + (latestRateSnapshot == null ? 0 : latestRateSnapshot.hashCode());
        if (!seed.equals(aggregateCacheSeed)) {
            aggregateCacheSeed = seed;
            aggregateCache.clear();
        }
    }

    private String formatDeltaText(double current, double previous) {
        if (Math.abs(previous) < 0.0001) {
            return getString(R.string.report_delta_no_previous);
        }
        double delta = current - previous;
        if (Math.abs(delta) < 0.0001) {
            return getString(R.string.report_delta_flat);
        }
        double ratio = Math.abs(delta / previous) * 100.0;
        String percent = String.format(Locale.US, "%.1f%%", ratio);
        return delta > 0
            ? getString(R.string.report_delta_up, percent)
            : getString(R.string.report_delta_down, percent);
    }

    private String formatWeekLabel(ZonedDateTime value) {
        int week = value.get(WeekFields.ISO.weekOfWeekBasedYear());
        return getString(R.string.report_week_picker_label, week, value.getYear());
    }

    private String formatMonthLabel(ZonedDateTime value) {
        return getString(R.string.report_month_picker_value, value.getMonthValue(), value.getYear());
    }

    private String formatYearLabel(ZonedDateTime value) {
        return getString(R.string.report_year_picker_label, value.getYear());
    }

    private boolean sameDay(ZonedDateTime first, ZonedDateTime second) {
        return first.getYear() == second.getYear()
            && first.getMonthValue() == second.getMonthValue()
            && first.getDayOfMonth() == second.getDayOfMonth();
    }

    private String shortWeekday(ZonedDateTime value) {
        switch (value.getDayOfWeek()) {
            case MONDAY:
                return getString(R.string.report_weekday_monday);
            case TUESDAY:
                return getString(R.string.report_weekday_tuesday);
            case WEDNESDAY:
                return getString(R.string.report_weekday_wednesday);
            case THURSDAY:
                return getString(R.string.report_weekday_thursday);
            case FRIDAY:
                return getString(R.string.report_weekday_friday);
            case SATURDAY:
                return getString(R.string.report_weekday_saturday);
            case SUNDAY:
            default:
                return getString(R.string.report_weekday_sunday);
        }
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

    private static final class RangeBucket {
        private final String label;
        private final ZonedDateTime start;
        private final ZonedDateTime end;

        private RangeBucket(String label, ZonedDateTime start, ZonedDateTime end) {
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }

    private static final class PeriodOption {
        private final ZonedDateTime start;
        private final String label;

        private PeriodOption(ZonedDateTime start, String label) {
            this.start = start;
            this.label = label;
        }
    }
}
