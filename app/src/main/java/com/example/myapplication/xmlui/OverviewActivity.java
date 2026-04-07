package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class OverviewActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private TextView tvTotalBalance;
    private TextView tvGreetingSubtitle;
    private TextView tvGreetingTitle;
    private TextView tvCurrencyChip;
    private TextView tvTrend;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvBudgetTitle;
    private TextView tvBudgetRemaining;
    private TextView tvBudgetUsed;
    private TextView tvBudgetPercent;
    private TextView tvBudgetStatus;
    private ProgressBar pbBudget;
    private View cardBudget;
    private View cardRecent;
    private TextView tvRecentName;
    private TextView tvRecentTime;
    private TextView tvRecentAmount;
    private TextView tvNoTransactions;
    private TextView tvViewAll;
    private FinanceUiState lastStableState;
    private long lastStableRealtimeMs;
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
        setupToolbar();
        setupBottomNavigation();
        setupSession();
    }

    private void bindViews() {
        tvTotalBalance = findViewById(R.id.tvOverviewTotalBalance);
        tvGreetingSubtitle = findViewById(R.id.tvOverviewGreetingSubtitle);
        tvGreetingTitle = findViewById(R.id.tvOverviewGreetingTitle);
        tvCurrencyChip = findViewById(R.id.tvOverviewCurrencyChip);
        tvTrend = findViewById(R.id.tvOverviewTrend);
        tvIncome = findViewById(R.id.tvOverviewIncome);
        tvExpense = findViewById(R.id.tvOverviewExpense);
        tvBudgetTitle = findViewById(R.id.tvOverviewBudgetTitle);
        tvBudgetRemaining = findViewById(R.id.tvOverviewBudgetRemaining);
        tvBudgetUsed = findViewById(R.id.tvOverviewBudgetUsed);
        tvBudgetPercent = findViewById(R.id.tvOverviewBudgetPercent);
        tvBudgetStatus = findViewById(R.id.tvOverviewBudgetStatus);
        pbBudget = findViewById(R.id.pbOverviewBudget);
        cardBudget = findViewById(R.id.cardOverviewBudget);
        cardRecent = findViewById(R.id.cardOverviewRecent);
        tvRecentName = findViewById(R.id.tvOverviewRecentName);
        tvRecentTime = findViewById(R.id.tvOverviewRecentTime);
        tvRecentAmount = findViewById(R.id.tvOverviewRecentAmount);
        tvNoTransactions = findViewById(R.id.tvOverviewNoTransactions);
        tvViewAll = findViewById(R.id.tvOverviewViewAll);
    }

    private void setupToolbar() {
        tvViewAll.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
            finish();
        });
        View notificationButton = findViewById(R.id.ivOverviewBell);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            intent.putExtra(NotificationsActivity.EXTRA_SOURCE_NAV_ITEM_ID, R.id.nav_overview);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreetingSubtitle();
        greetingHandler.removeCallbacks(greetingTicker);
        greetingHandler.post(greetingTicker);
    }

    @Override
    protected void onPause() {
        greetingHandler.removeCallbacks(greetingTicker);
        super.onPause();
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
        FinanceUiState displayState = state;
        if (shouldKeepStableState(state)) {
            displayState = lastStableState;
        } else if (hasDisplayData(state)) {
            lastStableState = state;
            lastStableRealtimeMs = SystemClock.elapsedRealtime();
        }
        ReminderScheduler.syncFromSettings(this, displayState.getSettings());
        BudgetAlertNotifier.maybeNotifyExceeded(this, displayState);
        updateGreetingSubtitle();

        double totalBalance = 0.0;
        for (Wallet wallet : displayState.getWallets()) {
            totalBalance += wallet.getBalance();
        }
        String displayName = "bạn";
        if (sessionViewModel != null && sessionViewModel.getUiStateLiveData().getValue() != null
            && sessionViewModel.getUiStateLiveData().getValue().getCurrentUser() != null) {
            String raw = sessionViewModel.getUiStateLiveData().getValue().getCurrentUser().getDisplayName();
            if (raw != null && !raw.trim().isEmpty()) {
                displayName = raw.trim();
            }
        }
        tvGreetingTitle.setText(getString(R.string.overview_greeting_title, normalizeDisplayName(displayName)));
        tvTotalBalance.setText(UiFormatters.money(totalBalance));
        String currency = displayState.getSettings().getCurrency();
        if (currency == null || currency.trim().isEmpty()) {
            currency = getString(R.string.overview_currency_chip);
        }
        tvCurrencyChip.setText(currency.trim().toUpperCase(Locale.ROOT));
        tvIncome.setText(UiFormatters.money(displayState.getMonthlySummary().getTotalIncome()));
        tvExpense.setText(UiFormatters.money(displayState.getMonthlySummary().getTotalExpense()));
        updateTrend(displayState);

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
            cardBudget.setVisibility(View.GONE);
            tvBudgetStatus.setText(R.string.label_no_budget);
            tvBudgetStatus.setTextColor(getColor(R.color.text_secondary));
            tvBudgetPercent.setText(R.string.default_percent_zero);
            pbBudget.setProgress(0);
            return;
        }
        cardBudget.setVisibility(View.VISIBLE);

        Map<String, Double> expenseByCategory = new HashMap<>();
        for (FinanceTransaction tx : state.getTransactions()) {
            if (tx.getType() != com.example.myapplication.finance.model.TransactionType.EXPENSE) {
                continue;
            }
            String key = normalizedCategory(tx.getCategory());
            expenseByCategory.put(key, expenseByCategory.getOrDefault(key, 0.0) + tx.getAmount());
        }

        com.example.myapplication.finance.model.BudgetLimit topBudget = state.getBudgetLimits().get(0);
        double topUsed = expenseByCategory.getOrDefault(normalizedCategory(topBudget.getCategory()), 0.0);
        for (com.example.myapplication.finance.model.BudgetLimit limit : state.getBudgetLimits()) {
            double used = expenseByCategory.getOrDefault(normalizedCategory(limit.getCategory()), 0.0);
            if (used > topUsed) {
                topUsed = used;
                topBudget = limit;
            }
        }

        double limitAmount = topBudget.getLimitAmount();
        if (limitAmount <= 0.0) {
            String budgetTitle = normalizedCategory(topBudget.getCategory());
            tvBudgetTitle.setText(budgetTitle.isEmpty()
                ? getString(R.string.overview_budget_title_default)
                : budgetTitle);
            tvBudgetRemaining.setText(getString(R.string.overview_budget_remaining, UiFormatters.money(0)));
            tvBudgetUsed.setText(getString(R.string.overview_budget_used, UiFormatters.money(0)));
            tvBudgetPercent.setText("0%");
            pbBudget.setProgress(0);
            tvBudgetStatus.setVisibility(View.GONE);
            return;
        }

        double used = expenseByCategory.getOrDefault(normalizedCategory(topBudget.getCategory()), 0.0);
        double ratio = Math.max(0.0, used / limitAmount);
        int progress = (int) Math.min(100, Math.round(ratio * 100));
        double remaining = Math.max(0.0, limitAmount - used);

        String budgetTitle = normalizedCategory(topBudget.getCategory());
        tvBudgetTitle.setText(budgetTitle.isEmpty()
            ? getString(R.string.overview_budget_title_default)
            : budgetTitle);
        tvBudgetRemaining.setText(getString(R.string.overview_budget_remaining, UiFormatters.money(remaining)));
        tvBudgetUsed.setText(getString(R.string.overview_budget_used, UiFormatters.money(used)));
        tvBudgetPercent.setText(getString(R.string.format_percent_int, progress));
        pbBudget.setProgress(progress);

        if (ratio >= 0.8 && ratio < 1.0) {
            tvBudgetStatus.setVisibility(View.VISIBLE);
            tvBudgetStatus.setText(R.string.overview_budget_warning);
        } else if (ratio >= 1.0) {
            tvBudgetStatus.setVisibility(View.VISIBLE);
            tvBudgetStatus.setText(getString(R.string.budget_warning_exceeded, UiFormatters.percent(ratio)));
        } else {
            tvBudgetStatus.setVisibility(View.GONE);
        }
    }

    private void updateTrend(FinanceUiState state) {
        List<FinanceTransaction> txs = state.getTransactions();
        if (txs.isEmpty()) {
            tvTrend.setText(getString(R.string.overview_balance_trend, "0%"));
            return;
        }
        long now = System.currentTimeMillis() / 1000L;
        long lastMonthStart = now - 30L * 24 * 3600;
        long prevMonthStart = now - 60L * 24 * 3600;
        double current = 0.0;
        double previous = 0.0;
        for (FinanceTransaction tx : txs) {
            if (tx.getType() != com.example.myapplication.finance.model.TransactionType.INCOME) {
                continue;
            }
            long second = tx.getCreatedAt().getSeconds();
            if (second >= lastMonthStart) {
                current += tx.getAmount();
            } else if (second >= prevMonthStart && second < lastMonthStart) {
                previous += tx.getAmount();
            }
        }
        double changePercent;
        if (previous <= 0.0) {
            changePercent = current > 0.0 ? 100.0 : 0.0;
        } else {
            changePercent = ((current - previous) / previous) * 100.0;
        }
        String signed = String.format(Locale.US, "%+.0f%%", changePercent);
        tvTrend.setText(getString(R.string.overview_balance_trend, signed));
    }

    private void updateRecentTransactions(FinanceUiState state) {
        if (state.getTransactions().isEmpty()) {
            cardRecent.setVisibility(View.GONE);
            tvNoTransactions.setVisibility(View.VISIBLE);
            return;
        }
        FinanceTransaction tx = null;
        for (FinanceTransaction item : state.getTransactions()) {
            if (tx == null || item.getCreatedAt().getSeconds() > tx.getCreatedAt().getSeconds()) {
                tx = item;
            }
        }
        if (tx == null) {
            cardRecent.setVisibility(View.GONE);
            tvNoTransactions.setVisibility(View.VISIBLE);
            return;
        }
        cardRecent.setVisibility(View.VISIBLE);
        tvNoTransactions.setVisibility(View.GONE);
        String note = tx.getNote() == null ? "" : tx.getNote().trim();
        String category = normalizedCategory(tx.getCategory());
        String recentName = !note.isEmpty()
            ? note
            : (!category.isEmpty() ? category : getString(R.string.overview_recent_store_default));
        tvRecentName.setText(recentName);
        long now = System.currentTimeMillis() / 1000L;
        long txSec = tx.getCreatedAt().getSeconds();
        long delta = Math.max(0L, now - txSec);
        String timePart = UiFormatters.timeOnly(tx.getCreatedAt());
        if (delta < 24L * 3600L) {
            tvRecentTime.setText(getString(R.string.overview_recent_time_today, timePart));
        } else {
            tvRecentTime.setText(getString(
                R.string.overview_recent_time_full,
                UiFormatters.dateOnly(tx.getCreatedAt()),
                timePart
            ));
        }
        String amount = UiFormatters.moneyRaw(tx.getAmount());
        boolean isIncome = tx.getType() == com.example.myapplication.finance.model.TransactionType.INCOME;
        tvRecentAmount.setTextColor(getColor(isIncome ? R.color.income_green : R.color.expense_red));
        String prefix = isIncome ? "+ " : "- ";
        tvRecentAmount.setText(getString(R.string.format_signed_amount, prefix, amount));
    }

    private String normalizedCategory(String value) {
        return value == null ? "" : value.trim();
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

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
