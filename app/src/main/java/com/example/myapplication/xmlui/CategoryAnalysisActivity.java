package com.example.myapplication.xmlui;

import android.content.Intent;
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

public class CategoryAnalysisActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;
    private ExchangeRateSnapshot latestRateSnapshot;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();

    private YearMonth selectedMonth = YearMonth.now();

    private MaterialButton btnCategoryMonthPicker;
    private TextView tvCategoryAnalysisTotal;
    private TextView tvCategoryCenterPercent;
    private TextView tvCategoryCenterName;
    private TextView tvCategoryAnalysisEmpty;
    private LinearLayout layoutCategoryLegend;
    private ReportDonutChartView chartCategoryDonut;
    private ReportCategoryAdapter categoryAdapter;

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
        setupMonthPicker();
        setupCategoryList();
        setupBottomNavigation();
        setupSession();
        refreshMonthLabel();
    }

    private void bindViews() {
        btnCategoryMonthPicker = findViewById(R.id.btnCategoryMonthPicker);
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

    private void setupMonthPicker() {
        btnCategoryMonthPicker.setOnClickListener(v -> {
            List<YearMonth> options = buildMonthOptions();
            String[] labels = new String[options.size()];
            int checked = 0;
            for (int i = 0; i < options.size(); i++) {
                YearMonth option = options.get(i);
                labels[i] = getString(R.string.report_month_picker_label, option.getMonthValue(), option.getYear());
                if (option.equals(selectedMonth)) {
                    checked = i;
                }
            }
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.report_choose_month_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    selectedMonth = options.get(which);
                    refreshMonthLabel();
                    if (latestState != null) {
                        renderFinanceState(latestState);
                    }
                    dialog.dismiss();
                })
                .show();
        });
    }

    private List<YearMonth> buildMonthOptions() {
        List<YearMonth> values = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 0; i < 24; i++) {
            values.add(now.minusMonths(i));
        }
        return values;
    }

    private void refreshMonthLabel() {
        btnCategoryMonthPicker.setText(
            getString(R.string.report_month_picker_value, selectedMonth.getMonthValue(), selectedMonth.getYear())
        );
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

        Map<String, CategoryBucket> grouped = new HashMap<>();
        double totalExpense = 0.0;
        for (FinanceTransaction transaction : state.getTransactions()) {
            if (transaction.getType() != TransactionType.EXPENSE) {
                continue;
            }
            ZonedDateTime time = Instant.ofEpochSecond(transaction.getCreatedAt().getSeconds(), transaction.getCreatedAt().getNanoseconds())
                .atZone(ZoneId.systemDefault());
            YearMonth txMonth = YearMonth.of(time.getYear(), time.getMonthValue());
            if (!txMonth.equals(selectedMonth)) {
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

        for (int i = 0; i < sorted.size(); i++) {
            CategoryBucket bucket = sorted.get(i);
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
        }

        tvCategoryAnalysisTotal.setText(UiFormatters.moneyRaw(totalExpense));
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

    private static final class CategoryBucket {
        private final String name;
        private final TransactionCategory category;
        private double amount;

        private CategoryBucket(String name, TransactionCategory category) {
            this.name = name;
            this.category = category;
        }
    }
}
