package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.example.myapplication.xmlui.budget.BudgetCycleUtils;
import com.example.myapplication.xmlui.currency.CurrencyRateUtils;
import com.example.myapplication.xmlui.currency.ExchangeRateSnapshotLoader;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverviewActivity extends AppCompatActivity {
    private static final String TAG = "OverviewActivity";
    private static final int RECENT_LIMIT = 5;

    private enum Scope { WEEK, MONTH, QUARTER }

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private TextView tvTotalBalance;
    private TextView tvGreetingSubtitle;
    private TextView tvGreetingTitle;
    private TextView tvCurrencyChip;
    private TextView tvTrend;
    private TextView tvIncomeLabel;
    private TextView tvExpenseLabel;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvBudgetTitle;
    private TextView tvBudgetSummary;
    private TextView tvBudgetUsed;
    private TextView tvBudgetPercent;
    private TextView tvBudgetStatus;
    private TextView tvBudgetViewAll;
    private TextView tvBudgetEmpty;
    private LinearLayout budgetItemsLayout;
    private ProgressBar pbBudget;
    private View cardBudget;
    private RecyclerView rvRecentTransactions;
    private TextView tvNoTransactions;
    private TextView tvViewAll;
    private MaterialButton btnScopeWeek;
    private MaterialButton btnScopeMonth;
    private MaterialButton btnScopeQuarter;
    private FinanceUiState latestState;
    private ExchangeRateSnapshot latestRateSnapshot;
    private FinanceUiState lastStableState;
    private long lastStableRealtimeMs;
    private Scope selectedScope = Scope.MONTH;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();
    private TransactionRowAdapter recentAdapter;
    private final Handler greetingHandler = new Handler(Looper.getMainLooper());
    private final Runnable greetingTicker = new Runnable() {
        @Override
        public void run() {
            updateGreetingSubtitle();
            greetingHandler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overview);
        bindViews();
        setupRecentList();
        setupToolbar();
        setupScopeActions();
        setupBottomNavigation();
        setupSession();
        refreshScopeButtons();
    }

    private void bindViews() {
        tvTotalBalance = findViewById(R.id.tvOverviewTotalBalance);
        tvGreetingSubtitle = findViewById(R.id.tvOverviewGreetingSubtitle);
        tvGreetingTitle = findViewById(R.id.tvOverviewGreetingTitle);
        tvCurrencyChip = findViewById(R.id.tvOverviewCurrencyChip);
        tvTrend = findViewById(R.id.tvOverviewTrend);
        tvIncomeLabel = findViewById(R.id.tvOverviewIncomeLabel);
        tvExpenseLabel = findViewById(R.id.tvOverviewExpenseLabel);
        tvIncome = findViewById(R.id.tvOverviewIncome);
        tvExpense = findViewById(R.id.tvOverviewExpense);
        tvBudgetTitle = findViewById(R.id.tvOverviewBudgetTitle);
        tvBudgetSummary = findViewById(R.id.tvOverviewBudgetSummary);
        tvBudgetUsed = findViewById(R.id.tvOverviewBudgetUsed);
        tvBudgetPercent = findViewById(R.id.tvOverviewBudgetPercent);
        tvBudgetStatus = findViewById(R.id.tvOverviewBudgetStatus);
        tvBudgetViewAll = findViewById(R.id.tvOverviewBudgetViewAll);
        tvBudgetEmpty = findViewById(R.id.tvOverviewBudgetEmpty);
        budgetItemsLayout = findViewById(R.id.layoutOverviewBudgetItems);
        pbBudget = findViewById(R.id.pbOverviewBudget);
        cardBudget = findViewById(R.id.cardOverviewBudget);
        rvRecentTransactions = findViewById(R.id.rvOverviewRecentTransactions);
        tvNoTransactions = findViewById(R.id.tvOverviewNoTransactions);
        tvViewAll = findViewById(R.id.tvOverviewViewAll);
        btnScopeWeek = findViewById(R.id.btnOverviewScopeWeek);
        btnScopeMonth = findViewById(R.id.btnOverviewScopeMonth);
        btnScopeQuarter = findViewById(R.id.btnOverviewScopeQuarter);
    }

    private void setupRecentList() {
        rvRecentTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvRecentTransactions.setNestedScrollingEnabled(false);
        recentAdapter = new TransactionRowAdapter(
            this::confirmDeleteTransaction,
            this::openEditTransaction
        );
        rvRecentTransactions.setAdapter(recentAdapter);
        rvRecentTransactions.setItemAnimator(null);
    }

    private void setupToolbar() {
        tvViewAll.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
            finish();
        });
        View.OnClickListener openBudget = v -> startActivity(new Intent(this, BudgetActivity.class));
        cardBudget.setOnClickListener(openBudget);
        tvBudgetViewAll.setOnClickListener(openBudget);
        View notificationButton = findViewById(R.id.ivOverviewBell);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            intent.putExtra(NotificationsActivity.EXTRA_SOURCE_NAV_ITEM_ID, R.id.nav_overview);
            startActivity(intent);
        });
    }

    private void setupScopeActions() {
        btnScopeWeek.setOnClickListener(v -> selectScope(Scope.WEEK));
        btnScopeMonth.setOnClickListener(v -> selectScope(Scope.MONTH));
        btnScopeQuarter.setOnClickListener(v -> selectScope(Scope.QUARTER));
    }

    private void selectScope(Scope scope) {
        if (selectedScope == scope) {
            return;
        }
        selectedScope = scope;
        refreshScopeButtons();
        if (latestState != null) {
            renderFinanceState(latestState);
        }
    }

    private void refreshScopeButtons() {
        styleScopeButton(btnScopeWeek, selectedScope == Scope.WEEK);
        styleScopeButton(btnScopeMonth, selectedScope == Scope.MONTH);
        styleScopeButton(btnScopeQuarter, selectedScope == Scope.QUARTER);
    }

    private void styleScopeButton(MaterialButton button, boolean selected) {
        int fill = selected ? R.color.card_bg : android.R.color.transparent;
        int text = selected ? R.color.text_primary : R.color.text_secondary;
        int stroke = R.color.divider;
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(fill)));
        button.setStrokeColor(ColorStateList.valueOf(getColor(stroke)));
        button.setTextColor(getColor(text));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (financeViewModel != null) {
            financeViewModel.refreshRealtimeSync();
        }
        updateGreetingSubtitle();
        greetingHandler.removeCallbacks(greetingTicker);
        greetingHandler.post(greetingTicker);
    }

    @Override
    protected void onPause() {
        greetingHandler.removeCallbacks(greetingTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        greetingHandler.removeCallbacks(greetingTicker);
        rateExecutor.shutdownNow();
        super.onDestroy();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_overview);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_overview) {
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
        FinanceUiState displayState = state;
        if (shouldKeepStableState(state)) {
            displayState = lastStableState;
        } else if (hasDisplayData(state)) {
            lastStableState = state;
            lastStableRealtimeMs = SystemClock.elapsedRealtime();
        }
        ReminderScheduler.syncFromSettings(this, displayState.getSettings());
        BudgetAlertNotifier.maybeNotifyExceeded(this, displayState);
        BudgetAlertNotifier.maybeNotifyNearLimit(this, displayState);
        updateGreetingSubtitle();
        loadLatestRateSnapshot();

        Map<String, String> walletCurrencyMap = buildWalletCurrencyMap(displayState.getWallets());

        double totalBalance = 0.0;
        for (Wallet wallet : displayState.getWallets()) {
            totalBalance += walletBalanceInVnd(wallet);
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime currentRangeStart = startForScope(selectedScope, now);
        ZonedDateTime currentRangeEnd = endForScope(selectedScope, currentRangeStart);
        ZonedDateTime previousRangeStart = previousStartForScope(selectedScope, currentRangeStart);
        MonthAggregate currentRange = aggregateInRange(
            displayState.getTransactions(),
            currentRangeStart,
            currentRangeEnd,
            walletCurrencyMap
        );
        MonthAggregate previousRange = aggregateInRange(
            displayState.getTransactions(),
            previousRangeStart,
            currentRangeStart,
            walletCurrencyMap
        );
        String displayName = "bạn";
        if (sessionViewModel != null && sessionViewModel.getUiStateLiveData().getValue() != null
            && sessionViewModel.getUiStateLiveData().getValue().getCurrentUser() != null) {
            String raw = sessionViewModel.getUiStateLiveData().getValue().getCurrentUser().getDisplayName();
            if (raw != null && !raw.trim().isEmpty()) {
                displayName = raw.trim();
            }
        }
        tvGreetingTitle.setText(getString(R.string.overview_greeting_title, normalizeDisplayName(displayName)));
        tvTotalBalance.setText(formatCompactMoney(totalBalance));
        tvTotalBalance.setTextColor(getColor(totalBalance < 0.0 ? R.color.expense_red : android.R.color.white));
        tvCurrencyChip.setText(getString(R.string.overview_currency_chip));
        tvIncomeLabel.setText(labelIncomeForScope(selectedScope));
        tvExpenseLabel.setText(labelExpenseForScope(selectedScope));
        tvIncome.setText(formatCompactMoney(currentRange.income));
        tvExpense.setText(formatCompactMoney(currentRange.expense));
        updateTrend(currentRange.net(), previousRange.net());

        updateBudgetStatus(displayState);
        updateRecentTransactions(displayState);
    }

    private boolean hasDisplayData(FinanceUiState state) {
        return !state.getWallets().isEmpty()
            || !state.getTransactions().isEmpty()
            || !state.getBudgetLimits().isEmpty();
    }

    private boolean shouldKeepStableState(FinanceUiState incoming) {
        if (lastStableState == null || !hasDisplayData(lastStableState)) {
            return false;
        }
        boolean incomingLooksEmpty = incoming.getWallets().isEmpty()
            && incoming.getTransactions().isEmpty()
            && incoming.getBudgetLimits().isEmpty();
        if (!incomingLooksEmpty) {
            return false;
        }
        long ageMs = SystemClock.elapsedRealtime() - lastStableRealtimeMs;
        return ageMs <= 2500L;
    }

    private void updateBudgetStatus(FinanceUiState state) {
        if (state.getBudgetLimits().isEmpty()) {
            cardBudget.setVisibility(View.VISIBLE);
            tvBudgetTitle.setText(R.string.app_title_budgets);
            tvBudgetSummary.setText(R.string.budget_summary_no_active);
            tvBudgetUsed.setText(getString(R.string.overview_budget_used, formatCompactMoney(0)));
            tvBudgetPercent.setText(R.string.default_percent_zero);
            pbBudget.setProgress(0);
            tvBudgetStatus.setVisibility(View.GONE);
            budgetItemsLayout.removeAllViews();
            tvBudgetEmpty.setVisibility(View.VISIBLE);
            return;
        }
        cardBudget.setVisibility(View.VISIBLE);

        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();
        List<UiBudget> activeBudgets = new java.util.ArrayList<>();
        double totalSpent = 0.0;
        double totalLimit = 0.0;
        int warningCount = 0;
        for (BudgetLimit budget : state.getBudgetLimits()) {
            BudgetCycleUtils.BudgetWindow window = BudgetCycleUtils.resolveWindow(budget, today);
            if (!window.isActive()) {
                continue;
            }
            double spent = BudgetCycleUtils.calculateSpentInWindow(
                budget,
                state.getTransactions(),
                CategoryFallbackMerger.mergeWithFallbacks(state.getCategories()),
                zoneId,
                window.getStart(),
                window.getEnd()
            );
            double limit = budget.getLimitAmount();
            double ratio = limit <= 0.0 ? 0.0 : spent / limit;
            if (ratio >= 0.8) {
                warningCount += 1;
            }
            totalSpent += spent;
            totalLimit += limit;
            activeBudgets.add(new UiBudget(
                budget.getId(),
                resolveBudgetTitle(budget),
                budget.getCategory(),
                limit,
                spent,
                ratio,
                limit - spent,
                BudgetCycleUtils.normalizeRepeatCycle(budget.getRepeatCycle()),
                window.getStart().toEpochDay(),
                window.getEnd().toEpochDay(),
                BudgetCycleUtils.daysRemaining(window, today),
                true
            ));
        }

        if (activeBudgets.isEmpty()) {
            tvBudgetTitle.setText(R.string.app_title_budgets);
            tvBudgetSummary.setText(R.string.budget_summary_no_active);
            tvBudgetUsed.setText(getString(R.string.overview_budget_used, formatCompactMoney(0)));
            tvBudgetPercent.setText(R.string.default_percent_zero);
            pbBudget.setProgress(0);
            tvBudgetStatus.setVisibility(View.GONE);
            budgetItemsLayout.removeAllViews();
            tvBudgetEmpty.setVisibility(View.VISIBLE);
            return;
        }

        activeBudgets.sort((left, right) -> Double.compare(right.getRatio(), left.getRatio()));
        tvBudgetTitle.setText(getString(R.string.budget_overview_title_count, activeBudgets.size()));
        tvBudgetSummary.setText(getString(
            R.string.overview_budget_summary_usage,
            formatCompactMoney(totalSpent),
            formatCompactMoney(totalLimit)
        ));
        tvBudgetUsed.setText(getString(R.string.overview_budget_used, formatCompactMoney(totalSpent)));
        double totalRatio = totalLimit <= 0.0 ? 0.0 : totalSpent / totalLimit;
        double totalRatioPercent = totalRatio * 100.0;
        int progress = (int) Math.min(100, Math.max(0, Math.round(totalRatioPercent)));
        tvBudgetPercent.setText(formatUnsignedPercent(totalRatioPercent));
        pbBudget.setProgress(progress);

        if (totalRatio >= 1.0) {
            tvBudgetStatus.setVisibility(View.VISIBLE);
            tvBudgetStatus.setText(getString(R.string.budget_warning_exceeded, UiFormatters.percent(totalRatio)));
        } else if (warningCount > 0) {
            tvBudgetStatus.setVisibility(View.VISIBLE);
            tvBudgetStatus.setText(getString(R.string.overview_budget_warning_count, warningCount));
        } else {
            tvBudgetStatus.setVisibility(View.GONE);
        }

        budgetItemsLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int previewCount = Math.min(3, activeBudgets.size());
        for (int i = 0; i < previewCount; i++) {
            UiBudget preview = activeBudgets.get(i);
            View row = inflater.inflate(R.layout.item_overview_budget_mini, budgetItemsLayout, false);
            TextView nameView = row.findViewById(R.id.tvOverviewBudgetMiniName);
            TextView metaView = row.findViewById(R.id.tvOverviewBudgetMiniMeta);
            nameView.setText(preview.getName());
            metaView.setText(getString(
                R.string.overview_budget_item_meta,
                formatCompactMoney(preview.getSpent()),
                formatCompactMoney(preview.getLimitAmount())
            ));
            if (preview.getRatio() >= 1.0) {
                metaView.setTextColor(getColor(R.color.error_red));
            } else if (preview.getRatio() >= 0.8) {
                metaView.setTextColor(getColor(R.color.warning_orange));
            } else {
                metaView.setTextColor(getColor(R.color.text_secondary));
            }
            budgetItemsLayout.addView(row);
        }
        tvBudgetEmpty.setVisibility(View.GONE);
    }

    private String resolveBudgetTitle(BudgetLimit budget) {
        String name = budget.getName() == null ? "" : budget.getName().trim();
        if (!name.isEmpty()) {
            return name;
        }
        if (BudgetCycleUtils.isAllCategory(budget)) {
            return getString(R.string.budget_category_all);
        }
        String category = budget.getCategory() == null ? "" : budget.getCategory().trim();
        return category.isEmpty() ? getString(R.string.overview_budget_title_default) : category;
    }

    private void updateTrend(double currentNet, double previousNet) {
        double changePercent;
        if (Math.abs(previousNet) < 0.0001) {
            if (Math.abs(currentNet) < 0.0001) {
                changePercent = 0.0;
            } else {
                changePercent = currentNet > 0.0 ? 100.0 : -100.0;
            }
        } else {
            changePercent = ((currentNet - previousNet) / Math.abs(previousNet)) * 100.0;
        }
        tvTrend.setText(getString(trendTextForScope(selectedScope), formatSignedPercent(changePercent)));
    }

    private int trendTextForScope(Scope scope) {
        if (scope == Scope.WEEK) {
            return R.string.overview_balance_trend_week;
        }
        if (scope == Scope.QUARTER) {
            return R.string.overview_balance_trend_quarter;
        }
        return R.string.overview_balance_trend_month;
    }

    private int labelIncomeForScope(Scope scope) {
        if (scope == Scope.WEEK) {
            return R.string.overview_income_scope_week;
        }
        if (scope == Scope.QUARTER) {
            return R.string.overview_income_scope_quarter;
        }
        return R.string.overview_income_scope_month;
    }

    private int labelExpenseForScope(Scope scope) {
        if (scope == Scope.WEEK) {
            return R.string.overview_expense_scope_week;
        }
        if (scope == Scope.QUARTER) {
            return R.string.overview_expense_scope_quarter;
        }
        return R.string.overview_expense_scope_month;
    }

    private void updateRecentTransactions(FinanceUiState state) {
        List<UiTransaction> recent = buildRecentTransactions(state);
        recentAdapter.submit(recent);
        boolean empty = recent.isEmpty();
        rvRecentTransactions.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvNoTransactions.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private List<UiTransaction> buildRecentTransactions(FinanceUiState state) {
        Map<String, Wallet> walletById = new HashMap<>();
        for (Wallet wallet : state.getWallets()) {
            walletById.put(wallet.getId(), wallet);
        }
        Map<String, TransactionCategory> categoryByKey = buildCategoryByKey(state.getCategories());
        List<UiTransaction> recent = new ArrayList<>();
        for (FinanceTransaction tx : state.getTransactions()) {
            if (tx == null || tx.getType() == null || tx.getCreatedAt() == null) {
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
            recent.add(new UiTransaction(
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
            ));
        }
        recent.sort((left, right) -> Long.compare(
            right.getCreatedAt().getSeconds(),
            left.getCreatedAt().getSeconds()
        ));
        if (recent.size() > RECENT_LIMIT) {
            return new ArrayList<>(recent.subList(0, RECENT_LIMIT));
        }
        return recent;
    }

    private Map<String, TransactionCategory> buildCategoryByKey(List<TransactionCategory> categories) {
        Map<String, TransactionCategory> map = new HashMap<>();
        for (TransactionCategory category : categories) {
            if (category == null || category.getType() == null) {
                continue;
            }
            map.put(categoryKey(category.getType(), category.getName()), category);
        }
        return map;
    }

    private String categoryKey(TransactionType type, String categoryName) {
        return type.name() + "::" + CategoryUiHelper.normalize(categoryName);
    }

    private void confirmDeleteTransaction(UiTransaction transaction) {
        if (transaction == null) {
            return;
        }
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
        if (latestState == null || transactionId == null || transactionId.trim().isEmpty()) {
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

    private String normalizeDisplayName(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) {
            return "bạn";
        }
        String[] words = raw.split("\\s+");
        if (words.length <= 2) {
            return raw;
        }
        return words[0] + " " + words[words.length - 1];
    }

    private void updateGreetingSubtitle() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int greetingRes = hour < 12
            ? R.string.overview_greeting_subtitle_morning
            : (hour < 18 ? R.string.overview_greeting_subtitle_afternoon : R.string.overview_greeting_subtitle_evening);
        tvGreetingSubtitle.setText(greetingRes);
    }

    private Map<String, String> buildWalletCurrencyMap(List<Wallet> wallets) {
        Map<String, String> walletCurrencyMap = new HashMap<>();
        for (Wallet wallet : wallets) {
            walletCurrencyMap.put(wallet.getId(), CurrencyRateUtils.normalizeCurrency(wallet.getCurrency()));
        }
        return walletCurrencyMap;
    }

    private double walletBalanceInVnd(Wallet wallet) {
        String walletCurrency = CurrencyRateUtils.normalizeCurrency(wallet.getCurrency());
        Double converted = CurrencyRateUtils.convert(wallet.getBalance(), walletCurrency, "VND", latestRateSnapshot);
        return converted == null ? 0.0 : converted;
    }

    private MonthAggregate aggregateInRange(
        List<FinanceTransaction> transactions,
        ZonedDateTime start,
        ZonedDateTime end,
        Map<String, String> walletCurrencyMap
    ) {
        double income = 0.0;
        double expense = 0.0;
        for (FinanceTransaction transaction : transactions) {
            if (transaction == null || transaction.getCreatedAt() == null || transaction.getType() == null) {
                continue;
            }
            ZonedDateTime txTime = Instant.ofEpochSecond(
                transaction.getCreatedAt().getSeconds(),
                transaction.getCreatedAt().getNanoseconds()
            ).atZone(start.getZone());
            if (txTime.isBefore(start) || !txTime.isBefore(end)) {
                continue;
            }
            double amountVnd = amountInVnd(transaction, walletCurrencyMap);
            if (transaction.getType() == TransactionType.INCOME) {
                income += amountVnd;
            } else if (transaction.getType() == TransactionType.EXPENSE) {
                expense += amountVnd;
            }
        }
        return new MonthAggregate(income, expense);
    }

    private double amountInVnd(FinanceTransaction transaction, Map<String, String> walletCurrencyMap) {
        String currency = resolveTransactionCurrency(transaction, walletCurrencyMap);
        Double converted = CurrencyRateUtils.convert(transaction.getAmount(), currency, "VND", latestRateSnapshot);
        return converted == null ? 0.0 : converted;
    }

    private String resolveTransactionCurrency(FinanceTransaction transaction, Map<String, String> walletCurrencyMap) {
        String sourceCurrency = transaction.getSourceCurrency();
        if (sourceCurrency != null && !sourceCurrency.trim().isEmpty()) {
            return CurrencyRateUtils.normalizeCurrency(sourceCurrency);
        }
        String walletCurrency = walletCurrencyMap.get(transaction.getWalletId());
        return walletCurrency == null || walletCurrency.isBlank() ? "VND" : walletCurrency;
    }

    private ZonedDateTime startOfMonth(ZonedDateTime value) {
        return value.withDayOfMonth(1).toLocalDate().atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfWeek(ZonedDateTime value) {
        int day = value.getDayOfWeek().getValue();
        return value.minusDays(day - 1L).toLocalDate().atStartOfDay(value.getZone());
    }

    private ZonedDateTime startOfQuarter(ZonedDateTime value) {
        int quarterStartMonth = ((value.getMonthValue() - 1) / 3) * 3 + 1;
        return value.withMonth(quarterStartMonth).withDayOfMonth(1).toLocalDate().atStartOfDay(value.getZone());
    }

    private ZonedDateTime startForScope(Scope scope, ZonedDateTime reference) {
        if (scope == Scope.WEEK) {
            return startOfWeek(reference);
        }
        if (scope == Scope.QUARTER) {
            return startOfQuarter(reference);
        }
        return startOfMonth(reference);
    }

    private ZonedDateTime endForScope(Scope scope, ZonedDateTime start) {
        if (scope == Scope.WEEK) {
            return start.plusWeeks(1);
        }
        if (scope == Scope.QUARTER) {
            return start.plusMonths(3);
        }
        return start.plusMonths(1);
    }

    private ZonedDateTime previousStartForScope(Scope scope, ZonedDateTime currentStart) {
        if (scope == Scope.WEEK) {
            return currentStart.minusWeeks(1);
        }
        if (scope == Scope.QUARTER) {
            return currentStart.minusMonths(3);
        }
        return currentStart.minusMonths(1);
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

    private String formatUnsignedPercent(double value) {
        double abs = Math.max(0.0, value);
        if (abs >= 1_000_000d) {
            return String.format(Locale.US, "%.1fM%%", abs / 1_000_000d);
        }
        if (abs >= 1_000d) {
            return String.format(Locale.US, "%.1fK%%", abs / 1_000d);
        }
        return String.format(Locale.US, "%.0f%%", abs);
    }

    private String formatSignedPercent(double value) {
        double abs = Math.abs(value);
        if (abs < 0.05) {
            return "0%";
        }
        String prefix = value > 0.0 ? "+" : "-";
        if (abs >= 1_000_000d) {
            return String.format(Locale.US, "%s%.1fM%%", prefix, abs / 1_000_000d);
        }
        if (abs >= 1_000d) {
            return String.format(Locale.US, "%s%.1fK%%", prefix, abs / 1_000d);
        }
        return String.format(Locale.US, "%s%.0f%%", prefix, abs);
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
            } catch (Exception exception) {
                Log.w(TAG, "Cannot load exchange rates for overview", exception);
            }
            ExchangeRateSnapshot finalSnapshot = snapshot;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalSnapshot == null || latestRateSnapshot != null) {
                    return;
                }
                latestRateSnapshot = finalSnapshot;
                if (latestState != null) {
                    renderFinanceState(latestState);
                }
            });
        });
    }

    private static final class MonthAggregate {
        private final double income;
        private final double expense;

        private MonthAggregate(double income, double expense) {
            this.income = income;
            this.expense = expense;
        }

        private double net() {
            return income - expense;
        }
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
