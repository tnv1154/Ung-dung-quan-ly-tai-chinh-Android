package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
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
import com.example.myapplication.xmlui.views.ReportDonutChartView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CategoryAnalysisActivity extends AppCompatActivity {

    private enum CategoryScope { WEEK, MONTH, YEAR }

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;
    private ExchangeRateSnapshot latestRateSnapshot;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();

    private CategoryScope selectedScope = CategoryScope.MONTH;
    private ZonedDateTime selectedStart = startOfMonth(ZonedDateTime.now());
    private ReportFilterState reportFilter = ReportFilterState.all();
    private ZonedDateTime currentRangeStart;
    private ZonedDateTime currentRangeEnd;

    private MaterialButton btnCategoryScopeWeek;
    private MaterialButton btnCategoryScopeMonth;
    private MaterialButton btnCategoryScopeYear;
    private MaterialButton btnCategoryMonthPicker;
    private MaterialButton btnCategoryFilter;
    private TextView tvCategoryAnalysisTotal;
    private TextView tvCategoryCenterPercent;
    private TextView tvCategoryCenterName;
    private TextView tvCategoryAnalysisEmpty;
    private LinearLayout layoutCategoryLegend;
    private ReportDonutChartView chartCategoryDonut;
    private ReportCategoryAdapter categoryAdapter;
    private final List<String> donutCategories = new ArrayList<>();
    private final Map<String, Double> amountCache = new HashMap<>();
    private String amountCacheSeed = "";

    @Override
    protected void onDestroy() {
        rateExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_analysis);
        bindViews();
        setupTopBar();
        setupScopeActions();
        setupPeriodPicker();
        setupCategoryList();
        setupBottomNavigation();
        setupSession();
        refreshScopeButtons();
        refreshPeriodLabel();
        refreshFilterButton();
        setupChartInteractions();
    }

    private void bindViews() {
        btnCategoryScopeWeek = findViewById(R.id.btnCategoryScopeWeek);
        btnCategoryScopeMonth = findViewById(R.id.btnCategoryScopeMonth);
        btnCategoryScopeYear = findViewById(R.id.btnCategoryScopeYear);
        btnCategoryMonthPicker = findViewById(R.id.btnCategoryMonthPicker);
        btnCategoryFilter = findViewById(R.id.btnCategoryFilter);
        tvCategoryAnalysisTotal = findViewById(R.id.tvCategoryAnalysisTotal);
        tvCategoryCenterPercent = findViewById(R.id.tvCategoryCenterPercent);
        tvCategoryCenterName = findViewById(R.id.tvCategoryCenterName);
        tvCategoryAnalysisEmpty = findViewById(R.id.tvCategoryAnalysisEmpty);
        layoutCategoryLegend = findViewById(R.id.layoutCategoryLegend);
        chartCategoryDonut = findViewById(R.id.chartCategoryDonut);
    }

    private void setupTopBar() {
        findViewById(R.id.btnCategoryBack).setOnClickListener(v -> finish());
    }

    private void setupScopeActions() {
        btnCategoryScopeWeek.setOnClickListener(v -> selectScope(CategoryScope.WEEK));
        btnCategoryScopeMonth.setOnClickListener(v -> selectScope(CategoryScope.MONTH));
        btnCategoryScopeYear.setOnClickListener(v -> selectScope(CategoryScope.YEAR));
    }

    private void setupPeriodPicker() {
        btnCategoryMonthPicker.setOnClickListener(v -> {
            List<PeriodOption> options = buildPeriodOptions();
            String[] labels = new String[options.size()];
            int checked = 0;
            for (int i = 0; i < options.size(); i++) {
                PeriodOption option = options.get(i);
                labels[i] = option.label;
                if (samePeriodStart(option.start, selectedStart)) {
                    checked = i;
                }
            }
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.report_choose_period_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    selectedStart = options.get(which).start;
                    refreshPeriodLabel();
                    if (latestState != null) {
                        renderFinanceState(latestState);
                    }
                    dialog.dismiss();
                })
                .show();
        });
        btnCategoryFilter.setOnClickListener(v -> {
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

    private void setupChartInteractions() {
        chartCategoryDonut.setOnSegmentClickListener((index, value) -> {
            if (index < 0 || index >= donutCategories.size() || currentRangeStart == null || currentRangeEnd == null) {
                return;
            }
            Set<TransactionType> types = new LinkedHashSet<>();
            types.add(TransactionType.EXPENSE);
            Intent intent = ReportDrilldownActivity.createIntent(
                this,
                getString(R.string.report_drilldown_title_with_label, donutCategories.get(index)),
                currentRangeStart,
                currentRangeEnd,
                donutCategories.get(index),
                reportFilter.getWalletIds(),
                types
            );
            startActivity(intent);
        });
    }

    private List<PeriodOption> buildPeriodOptions() {
        List<PeriodOption> options = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        if (selectedScope == CategoryScope.WEEK) {
            for (int i = 0; i < 24; i++) {
                ZonedDateTime start = startOfWeek(now.minusWeeks(i));
                options.add(new PeriodOption(start, formatWeekLabel(start)));
            }
            return options;
        }
        if (selectedScope == CategoryScope.MONTH) {
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

    private void selectScope(CategoryScope scope) {
        if (selectedScope == scope) {
            return;
        }
        selectedScope = scope;
        selectedStart = startForScope(scope, ZonedDateTime.now());
        refreshScopeButtons();
        refreshPeriodLabel();
        if (latestState != null) {
            renderFinanceState(latestState);
        }
    }

    private void refreshScopeButtons() {
        styleScopeButton(btnCategoryScopeWeek, selectedScope == CategoryScope.WEEK);
        styleScopeButton(btnCategoryScopeMonth, selectedScope == CategoryScope.MONTH);
        styleScopeButton(btnCategoryScopeYear, selectedScope == CategoryScope.YEAR);
    }

    private void styleScopeButton(MaterialButton button, boolean selected) {
        int fill = selected ? R.color.card_bg : android.R.color.transparent;
        int text = selected ? R.color.blue_primary : R.color.text_secondary;
        int stroke = R.color.divider;
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(fill)));
        button.setStrokeColor(ColorStateList.valueOf(getColor(stroke)));
        button.setTextColor(getColor(text));
    }

    private void refreshPeriodLabel() {
        if (selectedScope == CategoryScope.WEEK) {
            btnCategoryMonthPicker.setText(formatWeekLabel(selectedStart));
        } else if (selectedScope == CategoryScope.MONTH) {
            btnCategoryMonthPicker.setText(formatMonthLabel(selectedStart));
        } else {
            btnCategoryMonthPicker.setText(formatYearLabel(selectedStart));
        }
    }

    private boolean samePeriodStart(ZonedDateTime first, ZonedDateTime second) {
        return first.toEpochSecond() == second.toEpochSecond();
    }

    private void refreshFilterButton() {
        if (reportFilter.isAll()) {
            btnCategoryFilter.setText(R.string.action_filter);
            return;
        }
        int activeGroups = (reportFilter.hasWalletFilter() ? 1 : 0) + (reportFilter.hasTypeFilter() ? 1 : 0);
        btnCategoryFilter.setText(getString(R.string.report_filter_with_count, activeGroups));
    }

    private void setupCategoryList() {
        RecyclerView recyclerView = findViewById(R.id.rvCategoryAnalysisDetails);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(false);
        categoryAdapter = new ReportCategoryAdapter();
        recyclerView.setAdapter(categoryAdapter);
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
        updateAmountCacheSeed(state, walletCurrencyMap);
        ZonedDateTime rangeStart = selectedStart;
        ZonedDateTime rangeEnd = endForScope(selectedScope, selectedStart);
        currentRangeStart = rangeStart;
        currentRangeEnd = rangeEnd;

        Map<String, CategoryBucket> grouped = new HashMap<>();
        double totalExpense = 0.0;
        for (FinanceTransaction transaction : state.getTransactions()) {
            if (transaction.getType() != TransactionType.EXPENSE) {
                continue;
            }
            if (!reportFilter.includesType(TransactionType.EXPENSE)) {
                continue;
            }
            if (!reportFilter.includesWallet(transaction.getWalletId())) {
                continue;
            }
            ZonedDateTime time = Instant.ofEpochSecond(transaction.getCreatedAt().getSeconds(), transaction.getCreatedAt().getNanoseconds())
                .atZone(rangeStart.getZone());
            if (time.isBefore(rangeStart) || !time.isBefore(rangeEnd)) {
                continue;
            }
            String categoryName = safeCategoryName(transaction.getCategory());
            String key = normalize(categoryName);
            CategoryBucket bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new CategoryBucket(categoryName, findExpenseCategory(state.getCategories(), categoryName));
                grouped.put(key, bucket);
            }
            double amount = amountInVnd(transaction, walletCurrencyMap);
            bucket.amount += amount;
            totalExpense += amount;
        }

        List<CategoryBucket> sorted = new ArrayList<>(grouped.values());
        sorted.sort(Comparator.comparingDouble((CategoryBucket item) -> item.amount).reversed());

        List<Integer> palette = buildPalette();
        List<Float> donutValues = new ArrayList<>();
        List<Integer> donutColors = new ArrayList<>();
        List<UiReportCategory> uiItems = new ArrayList<>();
        donutCategories.clear();

        for (int i = 0; i < sorted.size(); i++) {
            CategoryBucket bucket = sorted.get(i);
            if (bucket.amount <= 0.0) {
                continue;
            }
            double ratio = totalExpense <= 0.0 ? 0.0 : bucket.amount / totalExpense;
            int chartColor = palette.get(i % palette.size());
            int iconRes = CategoryUiHelper.iconResForCategory(bucket.category);
            int iconBgColor = getColor(CategoryUiHelper.iconBgForCategory(bucket.category));
            int iconTintColor = getColor(CategoryUiHelper.iconTintForCategory(bucket.category));
            uiItems.add(new UiReportCategory(
                bucket.name,
                bucket.amount,
                ratio,
                iconRes,
                iconBgColor,
                iconTintColor,
                chartColor
            ));
            donutValues.add((float) bucket.amount);
            donutColors.add(chartColor);
            donutCategories.add(bucket.name);
        }

        tvCategoryAnalysisTotal.setText(formatCompactMoney(totalExpense));
        if (uiItems.isEmpty()) {
            tvCategoryCenterPercent.setText(R.string.default_percent_zero);
            tvCategoryCenterName.setText(R.string.label_no_category_data);
        } else {
            UiReportCategory first = uiItems.get(0);
            tvCategoryCenterPercent.setText(UiFormatters.percent(first.getRatio()));
            tvCategoryCenterName.setText(first.getName());
        }
        chartCategoryDonut.setSegments(donutValues, donutColors);
        renderLegend(uiItems);

        categoryAdapter.submit(uiItems);
        tvCategoryAnalysisEmpty.setVisibility(uiItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderLegend(List<UiReportCategory> categories) {
        layoutCategoryLegend.removeAllViews();
        int count = Math.min(categories.size(), 4);
        for (int i = 0; i < count; i++) {
            UiReportCategory category = categories.get(i);
            TextView label = new TextView(this);
            label.setTextColor(getColor(R.color.text_secondary));
            label.setTextSize(15f);
            label.setText(category.getName());
            label.setMaxLines(1);

            View dot = new View(this);
            int dotSize = dp(10);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
            dotParams.setMarginStart(dp(6));
            dotParams.setMarginEnd(dp(4));
            dot.setLayoutParams(dotParams);
            dot.setBackground(AppCompatResources.getDrawable(this, R.drawable.bg_report_legend_dot_income));
            dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(category.getChartColor()));

            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.HORIZONTAL);
            wrap.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                wrapParams.setMarginStart(dp(10));
            }
            wrap.setLayoutParams(wrapParams);
            wrap.addView(dot);
            wrap.addView(label);
            layoutCategoryLegend.addView(wrap);
        }
    }

    private List<Integer> buildPalette() {
        List<Integer> colors = new ArrayList<>();
        colors.add(getColor(R.color.blue_primary));
        colors.add(getColor(R.color.group_cash_tint));
        colors.add(getColor(R.color.warning_orange));
        colors.add(getColor(R.color.expense_red));
        colors.add(getColor(R.color.group_ewallet_tint));
        colors.add(getColor(R.color.chip_text));
        return colors;
    }

    private TransactionCategory findExpenseCategory(List<TransactionCategory> categories, String categoryName) {
        String needle = normalize(categoryName);
        for (TransactionCategory category : categories) {
            if (category.getType() != TransactionType.EXPENSE) {
                continue;
            }
            if (normalize(category.getName()).equals(needle)) {
                return category;
            }
        }
        return null;
    }

    private String safeCategoryName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return getString(R.string.default_category_other);
        }
        return raw.trim();
    }

    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private ZonedDateTime startForScope(CategoryScope scope, ZonedDateTime reference) {
        if (scope == CategoryScope.WEEK) {
            return startOfWeek(reference);
        }
        if (scope == CategoryScope.MONTH) {
            return startOfMonth(reference);
        }
        return startOfYear(reference);
    }

    private ZonedDateTime endForScope(CategoryScope scope, ZonedDateTime start) {
        if (scope == CategoryScope.WEEK) {
            return start.plusWeeks(1);
        }
        if (scope == CategoryScope.MONTH) {
            return start.plusMonths(1);
        }
        return start.plusYears(1);
    }

    private ZonedDateTime startOfWeek(ZonedDateTime value) {
        int day = value.getDayOfWeek().getValue();
        return value.minusDays(day - 1L).toLocalDate().atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfMonth(ZonedDateTime value) {
        YearMonth month = YearMonth.of(value.getYear(), value.getMonthValue());
        return month.atDay(1).atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfYear(ZonedDateTime value) {
        return value.withDayOfYear(1).toLocalDate().atStartOfDay(value.getZone());
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

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
        String key = transaction.getId() + "|" + currency;
        Double cached = amountCache.get(key);
        if (cached != null) {
            return cached;
        }
        Double converted = CurrencyRateUtils.convert(transaction.getAmount(), currency, "VND", latestRateSnapshot);
        double value = converted == null ? 0.0 : converted;
        amountCache.put(key, value);
        return value;
    }

    private String resolveTransactionCurrency(FinanceTransaction transaction, Map<String, String> walletCurrencyMap) {
        String rawSourceCurrency = transaction.getSourceCurrency();
        if (rawSourceCurrency != null && !rawSourceCurrency.trim().isEmpty()) {
            return CurrencyRateUtils.normalizeCurrency(rawSourceCurrency);
        }
        String walletCurrency = walletCurrencyMap.get(transaction.getWalletId());
        return walletCurrency == null || walletCurrency.isBlank() ? "VND" : walletCurrency;
    }

    private void updateAmountCacheSeed(FinanceUiState state, Map<String, String> walletCurrencyMap) {
        String seed =
            transactionsDigest(state.getTransactions())
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
        if (!seed.equals(amountCacheSeed)) {
            amountCacheSeed = seed;
            amountCache.clear();
        }
    }

    private long transactionsDigest(List<FinanceTransaction> transactions) {
        long digest = 17L;
        for (FinanceTransaction transaction : transactions) {
            if (transaction == null) {
                continue;
            }
            digest = digest * 31L + hashString(transaction.getId());
            digest = digest * 31L + (transaction.getType() == null ? 0 : transaction.getType().ordinal() + 1);
            digest = digest * 31L + Double.hashCode(transaction.getAmount());
            digest = digest * 31L + hashString(transaction.getCategory());
            digest = digest * 31L + hashString(transaction.getWalletId());
            digest = digest * 31L + hashString(transaction.getSourceCurrency());
            if (transaction.getCreatedAt() != null) {
                digest = digest * 31L + Long.hashCode(transaction.getCreatedAt().getSeconds());
                digest = digest * 31L + transaction.getCreatedAt().getNanoseconds();
            }
        }
        return digest;
    }

    private int hashString(String value) {
        return value == null ? 0 : value.hashCode();
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

    private static final class CategoryBucket {
        private final String name;
        private final TransactionCategory category;
        private double amount;

        private CategoryBucket(String name, TransactionCategory category) {
            this.name = name;
            this.category = category;
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
