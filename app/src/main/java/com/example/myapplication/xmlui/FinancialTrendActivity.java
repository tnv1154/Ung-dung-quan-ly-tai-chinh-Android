package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.FinanceTransaction;
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
import com.example.myapplication.xmlui.views.ReportTrendChartView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FinancialTrendActivity extends AppCompatActivity {

    private enum TrendScope { DAY, WEEK, MONTH, YEAR }

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;
    private ExchangeRateSnapshot latestRateSnapshot;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();

    private TrendScope selectedScope = TrendScope.DAY;
    private ZonedDateTime selectedStart = startOfDay(ZonedDateTime.now());
    private ReportFilterState reportFilter = ReportFilterState.all();

    private MaterialButton btnTrendDay;
    private MaterialButton btnTrendWeek;
    private MaterialButton btnTrendMonth;
    private MaterialButton btnTrendYear;
    private MaterialButton btnTrendPeriodPicker;
    private MaterialButton btnTrendFilter;
    private TextView tvTrendIncome;
    private TextView tvTrendExpense;
    private TextView tvTrendNet;
    private TextView tvTrendNetDelta;
    private ReportTrendChartView chartTrendFlow;
    private final List<RangeBucket> trendBuckets = new ArrayList<>();
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
        setContentView(R.layout.activity_financial_trend);
        bindViews();
        setupTopBar();
        setupScopeActions();
        setupPeriodPicker();
        setupBottomNavigation();
        setupSession();
        refreshScopeButtons();
        refreshPeriodPickerLabel();
        refreshFilterButton();
        setupTrendChartInteractions();
    }

    private void bindViews() {
        btnTrendDay = findViewById(R.id.btnTrendDay);
        btnTrendWeek = findViewById(R.id.btnTrendWeek);
        btnTrendMonth = findViewById(R.id.btnTrendMonth);
        btnTrendYear = findViewById(R.id.btnTrendYear);
        btnTrendPeriodPicker = findViewById(R.id.btnTrendPeriodPicker);
        btnTrendFilter = findViewById(R.id.btnTrendFilter);
        tvTrendIncome = findViewById(R.id.tvTrendIncome);
        tvTrendExpense = findViewById(R.id.tvTrendExpense);
        tvTrendNet = findViewById(R.id.tvTrendNet);
        tvTrendNetDelta = findViewById(R.id.tvTrendNetDelta);
        chartTrendFlow = findViewById(R.id.chartTrendFlow);
    }

    private void setupTopBar() {
        findViewById(R.id.btnTrendBack).setOnClickListener(v -> finish());
    }

    private void setupScopeActions() {
        btnTrendDay.setOnClickListener(v -> selectScope(TrendScope.DAY));
        btnTrendWeek.setOnClickListener(v -> selectScope(TrendScope.WEEK));
        btnTrendMonth.setOnClickListener(v -> selectScope(TrendScope.MONTH));
        btnTrendYear.setOnClickListener(v -> selectScope(TrendScope.YEAR));
    }

    private void selectScope(TrendScope scope) {
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

    private void setupPeriodPicker() {
        btnTrendPeriodPicker.setOnClickListener(v -> {
            List<PeriodOption> options = buildPeriodOptions();
            String[] labels = new String[options.size()];
            int checked = 0;
            for (int i = 0; i < options.size(); i++) {
                labels[i] = options.get(i).label;
                if (samePeriodStart(options.get(i).start, selectedStart)) {
                    checked = i;
                }
            }
            new MaterialAlertDialogBuilder(this)
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
        btnTrendFilter.setOnClickListener(v -> {
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

    private boolean samePeriodStart(ZonedDateTime first, ZonedDateTime second) {
        return first.toEpochSecond() == second.toEpochSecond();
    }

    private void setupTrendChartInteractions() {
        chartTrendFlow.setOnEntryClickListener((index, entry) -> {
            if (index < 0 || index >= trendBuckets.size()) {
                return;
            }
            RangeBucket bucket = trendBuckets.get(index);
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

    private void refreshFilterButton() {
        if (reportFilter.isAll()) {
            btnTrendFilter.setText(R.string.action_filter);
            return;
        }
        int activeGroups = (reportFilter.hasWalletFilter() ? 1 : 0) + (reportFilter.hasTypeFilter() ? 1 : 0);
        btnTrendFilter.setText(getString(R.string.report_filter_with_count, activeGroups));
    }

    private List<PeriodOption> buildPeriodOptions() {
        List<PeriodOption> options = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        if (selectedScope == TrendScope.DAY) {
            for (int i = 0; i < 14; i++) {
                ZonedDateTime start = startOfDay(now.minusDays(i));
                options.add(new PeriodOption(start, formatDayLabel(start)));
            }
            return options;
        }
        if (selectedScope == TrendScope.WEEK) {
            for (int i = 0; i < 12; i++) {
                ZonedDateTime start = startOfWeek(now.minusWeeks(i));
                options.add(new PeriodOption(start, formatWeekLabel(start)));
            }
            return options;
        }
        if (selectedScope == TrendScope.MONTH) {
            for (int i = 0; i < 12; i++) {
                ZonedDateTime start = startOfMonth(now.minusMonths(i));
                options.add(new PeriodOption(start, formatMonthLabel(start)));
            }
            return options;
        }
        for (int i = 0; i < 8; i++) {
            ZonedDateTime start = startOfYear(now.minusYears(i));
            options.add(new PeriodOption(start, formatYearLabel(start)));
        }
        return options;
    }

    private void refreshScopeButtons() {
        styleScopeButton(btnTrendDay, selectedScope == TrendScope.DAY);
        styleScopeButton(btnTrendWeek, selectedScope == TrendScope.WEEK);
        styleScopeButton(btnTrendMonth, selectedScope == TrendScope.MONTH);
        styleScopeButton(btnTrendYear, selectedScope == TrendScope.YEAR);
    }

    private void styleScopeButton(MaterialButton button, boolean selected) {
        int fill = selected ? R.color.card_bg : android.R.color.transparent;
        int text = selected ? R.color.blue_primary : R.color.text_secondary;
        int stroke = R.color.divider;
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(fill)));
        button.setStrokeColor(ColorStateList.valueOf(getColor(stroke)));
        button.setTextColor(getColor(text));
    }

    private void refreshPeriodPickerLabel() {
        if (selectedScope == TrendScope.DAY) {
            btnTrendPeriodPicker.setText(formatDayLabel(selectedStart));
        } else if (selectedScope == TrendScope.WEEK) {
            btnTrendPeriodPicker.setText(formatWeekLabel(selectedStart));
        } else if (selectedScope == TrendScope.MONTH) {
            btnTrendPeriodPicker.setText(formatMonthLabel(selectedStart));
        } else {
            btnTrendPeriodPicker.setText(formatYearLabel(selectedStart));
        }
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
        Map<String, String> walletCurrencyMap = buildWalletCurrencyMap(state.getWallets());
        updateAggregateCacheSeed(state, walletCurrencyMap);

        ZonedDateTime currentStart = selectedStart;
        ZonedDateTime currentEnd = endForScope(selectedScope, currentStart);
        ZonedDateTime previousStart = previousStart(selectedScope, currentStart);
        ZonedDateTime previousEnd = currentStart;

        double income = sumInRange(state.getTransactions(), currentStart, currentEnd, TransactionType.INCOME, walletCurrencyMap);
        double expense = sumInRange(state.getTransactions(), currentStart, currentEnd, TransactionType.EXPENSE, walletCurrencyMap)
            + sumInRange(state.getTransactions(), currentStart, currentEnd, TransactionType.TRANSFER, walletCurrencyMap);
        double net = income - expense;

        double previousIncome = sumInRange(state.getTransactions(), previousStart, previousEnd, TransactionType.INCOME, walletCurrencyMap);
        double previousExpense = sumInRange(state.getTransactions(), previousStart, previousEnd, TransactionType.EXPENSE, walletCurrencyMap)
            + sumInRange(state.getTransactions(), previousStart, previousEnd, TransactionType.TRANSFER, walletCurrencyMap);
        double previousNet = previousIncome - previousExpense;

        tvTrendIncome.setText(UiFormatters.moneyRaw(income));
        tvTrendExpense.setText(UiFormatters.moneyRaw(expense));
        tvTrendNet.setText(UiFormatters.moneyRaw(net));
        tvTrendNet.setTextColor(getColor(net < 0.0 ? R.color.expense_red : android.R.color.white));

        if (Math.abs(previousNet) < 0.0001) {
            tvTrendNetDelta.setText(R.string.report_trend_no_previous);
        } else {
            double delta = net - previousNet;
            double ratio = Math.abs(delta / previousNet);
            String percent = String.format(Locale.US, "%.1f%%", ratio * 100.0);
            if (Math.abs(delta) < 0.0001) {
                tvTrendNetDelta.setText(R.string.report_trend_flat);
            } else if (delta > 0) {
                tvTrendNetDelta.setText(getString(R.string.report_trend_up, percent));
            } else {
                tvTrendNetDelta.setText(getString(R.string.report_trend_down, percent));
            }
        }

        chartTrendFlow.setEntries(buildChartEntries(state.getTransactions(), walletCurrencyMap, currentStart));
    }

    private List<ReportTrendChartView.Entry> buildChartEntries(
        List<FinanceTransaction> transactions,
        Map<String, String> walletCurrencyMap,
        ZonedDateTime periodStart
    ) {
        List<ReportTrendChartView.Entry> entries = new ArrayList<>();
        trendBuckets.clear();
        if (selectedScope == TrendScope.DAY) {
            for (int i = 0; i < 6; i++) {
                ZonedDateTime start = periodStart.plusHours(i * 4L);
                ZonedDateTime end = start.plusHours(4);
                double income = sumInRange(transactions, start, end, TransactionType.INCOME, walletCurrencyMap);
                double expense = sumInRange(transactions, start, end, TransactionType.EXPENSE, walletCurrencyMap)
                    + sumInRange(transactions, start, end, TransactionType.TRANSFER, walletCurrencyMap);
                String label = i * 4 + "h";
                entries.add(new ReportTrendChartView.Entry(label, income, expense));
                trendBuckets.add(new RangeBucket(label, start, end));
            }
            return entries;
        }

        if (selectedScope == TrendScope.WEEK) {
            for (int i = 0; i < 7; i++) {
                ZonedDateTime start = periodStart.plusDays(i);
                ZonedDateTime end = start.plusDays(1);
                double income = sumInRange(transactions, start, end, TransactionType.INCOME, walletCurrencyMap);
                double expense = sumInRange(transactions, start, end, TransactionType.EXPENSE, walletCurrencyMap)
                    + sumInRange(transactions, start, end, TransactionType.TRANSFER, walletCurrencyMap);
                String label = shortWeekday(start);
                entries.add(new ReportTrendChartView.Entry(label, income, expense));
                trendBuckets.add(new RangeBucket(label, start, end));
            }
            return entries;
        }

        if (selectedScope == TrendScope.MONTH) {
            ZonedDateTime monthEnd = periodStart.plusMonths(1);
            for (int i = 0; i < 4; i++) {
                ZonedDateTime start = periodStart.plusDays(i * 7L);
                ZonedDateTime end = start.plusDays(7);
                if (end.isAfter(monthEnd)) {
                    end = monthEnd;
                }
                double income = sumInRange(transactions, start, end, TransactionType.INCOME, walletCurrencyMap);
                double expense = sumInRange(transactions, start, end, TransactionType.EXPENSE, walletCurrencyMap)
                    + sumInRange(transactions, start, end, TransactionType.TRANSFER, walletCurrencyMap);
                String label = getString(R.string.report_week_short, i + 1);
                entries.add(new ReportTrendChartView.Entry(label, income, expense));
                trendBuckets.add(new RangeBucket(label, start, end));
            }
            return entries;
        }

        for (int i = 0; i < 12; i++) {
            ZonedDateTime start = periodStart.plusMonths(i);
            ZonedDateTime end = start.plusMonths(1);
            double income = sumInRange(transactions, start, end, TransactionType.INCOME, walletCurrencyMap);
            double expense = sumInRange(transactions, start, end, TransactionType.EXPENSE, walletCurrencyMap)
                + sumInRange(transactions, start, end, TransactionType.TRANSFER, walletCurrencyMap);
            String label = getString(R.string.report_month_short, start.getMonthValue());
            entries.add(new ReportTrendChartView.Entry(label, income, expense));
            trendBuckets.add(new RangeBucket(label, start, end));
        }
        return entries;
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
            ZonedDateTime time = Instant.ofEpochSecond(transaction.getCreatedAt().getSeconds(), transaction.getCreatedAt().getNanoseconds())
                .atZone(start.getZone());
            if (time.isBefore(start) || !time.isBefore(end)) {
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

    private ZonedDateTime startForScope(TrendScope scope, ZonedDateTime reference) {
        if (scope == TrendScope.DAY) {
            return startOfDay(reference);
        }
        if (scope == TrendScope.WEEK) {
            return startOfWeek(reference);
        }
        if (scope == TrendScope.MONTH) {
            return startOfMonth(reference);
        }
        return startOfYear(reference);
    }

    private ZonedDateTime previousStart(TrendScope scope, ZonedDateTime currentStart) {
        if (scope == TrendScope.DAY) {
            return currentStart.minusDays(1);
        }
        if (scope == TrendScope.WEEK) {
            return currentStart.minusWeeks(1);
        }
        if (scope == TrendScope.MONTH) {
            return currentStart.minusMonths(1);
        }
        return currentStart.minusYears(1);
    }

    private ZonedDateTime endForScope(TrendScope scope, ZonedDateTime start) {
        if (scope == TrendScope.DAY) {
            return start.plusDays(1);
        }
        if (scope == TrendScope.WEEK) {
            return start.plusWeeks(1);
        }
        if (scope == TrendScope.MONTH) {
            return start.plusMonths(1);
        }
        return start.plusYears(1);
    }

    private void updateAggregateCacheSeed(FinanceUiState state, Map<String, String> walletCurrencyMap) {
        String seed =
            state.getTransactions().size()
                + "|"
                + selectedScope.name()
                + "|"
                + selectedStart.toEpochSecond()
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

    private String formatDayLabel(ZonedDateTime value) {
        return getString(R.string.report_day_picker_label, value.getDayOfMonth(), value.getMonthValue(), value.getYear());
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

    private ZonedDateTime startOfDay(ZonedDateTime value) {
        return value.toLocalDate().atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfWeek(ZonedDateTime value) {
        int day = value.getDayOfWeek().getValue();
        return value.minusDays(day - 1L).toLocalDate().atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfMonth(ZonedDateTime value) {
        LocalDate date = LocalDate.of(value.getYear(), value.getMonthValue(), 1);
        return date.atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfYear(ZonedDateTime value) {
        LocalDate date = LocalDate.of(value.getYear(), 1, 1);
        return date.atStartOfDay(value.getZone());
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

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static final class PeriodOption {
        private final ZonedDateTime start;
        private final String label;

        private PeriodOption(ZonedDateTime start, String label) {
            this.start = start;
            this.label = label;
        }
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
}
