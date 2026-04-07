package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MonthlyReportActivity extends AppCompatActivity {
    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private TextView tvMonthlyTitle;
    private TextView tvMonthlyTotalExpense;
    private TextView tvMonthlyTrend;
    private TextView tvMonthlyRemaining;
    private TextView tvMonthlyBudgetAmount;
    private TextView tvMonthlyActualAmount;
    private ProgressBar progressMonthlyBudget;
    private ProgressBar progressMonthlyActual;

    private View[] categoryRows;
    private FrameLayout[] categoryIconBoxes;
    private ImageView[] categoryIcons;
    private TextView[] categoryNames;
    private TextView[] categoryAmounts;
    private ProgressBar[] categoryProgressBars;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_report);
        bindViews();
        setupTopBar();
        setupBottomNavigation();
        setupSession();
    }

    private void bindViews() {
        tvMonthlyTitle = findViewById(R.id.tvMonthlyTitle);
        tvMonthlyTotalExpense = findViewById(R.id.tvMonthlyTotalExpense);
        tvMonthlyTrend = findViewById(R.id.tvMonthlyTrend);
        tvMonthlyRemaining = findViewById(R.id.tvMonthlyRemaining);
        tvMonthlyBudgetAmount = findViewById(R.id.tvMonthlyBudgetAmount);
        tvMonthlyActualAmount = findViewById(R.id.tvMonthlyActualAmount);
        progressMonthlyBudget = findViewById(R.id.progressMonthlyBudget);
        progressMonthlyActual = findViewById(R.id.progressMonthlyActual);

        categoryRows = new View[] {
            findViewById(R.id.rowMonthlyCategory1),
            findViewById(R.id.rowMonthlyCategory2),
            findViewById(R.id.rowMonthlyCategory3),
            findViewById(R.id.rowMonthlyCategory4)
        };
        categoryIconBoxes = new FrameLayout[] {
            findViewById(R.id.boxMonthlyCategory1),
            findViewById(R.id.boxMonthlyCategory2),
            findViewById(R.id.boxMonthlyCategory3),
            findViewById(R.id.boxMonthlyCategory4)
        };
        categoryIcons = new ImageView[] {
            findViewById(R.id.ivMonthlyCategory1),
            findViewById(R.id.ivMonthlyCategory2),
            findViewById(R.id.ivMonthlyCategory3),
            findViewById(R.id.ivMonthlyCategory4)
        };
        categoryNames = new TextView[] {
            findViewById(R.id.tvMonthlyCategoryName1),
            findViewById(R.id.tvMonthlyCategoryName2),
            findViewById(R.id.tvMonthlyCategoryName3),
            findViewById(R.id.tvMonthlyCategoryName4)
        };
        categoryAmounts = new TextView[] {
            findViewById(R.id.tvMonthlyCategoryAmount1),
            findViewById(R.id.tvMonthlyCategoryAmount2),
            findViewById(R.id.tvMonthlyCategoryAmount3),
            findViewById(R.id.tvMonthlyCategoryAmount4)
        };
        categoryProgressBars = new ProgressBar[] {
            findViewById(R.id.progressMonthlyCategory1),
            findViewById(R.id.progressMonthlyCategory2),
            findViewById(R.id.progressMonthlyCategory3),
            findViewById(R.id.progressMonthlyCategory4)
        };
    }

    private void setupTopBar() {
        findViewById(R.id.btnMonthlyBack).setOnClickListener(v -> finish());
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
        ReminderScheduler.syncFromSettings(this, state.getSettings());
        BudgetAlertNotifier.maybeNotifyExceeded(this, state);

        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        tvMonthlyTitle.setText(getString(R.string.app_title_monthly_report, month));

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime previous = now.minusMonths(1);
        double currentExpense = 0.0;
        double previousExpense = 0.0;

        Map<String, CategorySpend> expenseByCategory = new HashMap<>();
        for (FinanceTransaction tx : state.getTransactions()) {
            if (tx.getType() != TransactionType.EXPENSE) {
                continue;
            }
            ZonedDateTime txTime = Instant.ofEpochSecond(tx.getCreatedAt().getSeconds(), tx.getCreatedAt().getNanoseconds())
                .atZone(ZoneId.systemDefault());
            if (sameMonth(txTime, now)) {
                currentExpense += tx.getAmount();
                String categoryName = safeCategoryName(tx.getCategory());
                String key = normalizeCategory(categoryName);
                CategorySpend spend = expenseByCategory.get(key);
                if (spend == null) {
                    spend = new CategorySpend(categoryName);
                    expenseByCategory.put(key, spend);
                }
                spend.amount += tx.getAmount();
            } else if (sameMonth(txTime, previous)) {
                previousExpense += tx.getAmount();
            }
        }

        tvMonthlyTotalExpense.setText(UiFormatters.money(currentExpense));
        applyTrendUi(currentExpense, previousExpense);

        double totalBudget = 0.0;
        for (BudgetLimit budget : state.getBudgetLimits()) {
            totalBudget += Math.max(0.0, budget.getLimitAmount());
        }
        tvMonthlyBudgetAmount.setText(UiFormatters.money(totalBudget));
        tvMonthlyActualAmount.setText(UiFormatters.money(currentExpense));
        applyBudgetBalanceUi(totalBudget, currentExpense);

        double progressMax = Math.max(1.0, Math.max(totalBudget, currentExpense));
        progressMonthlyBudget.setProgress((int) Math.round(Math.min(100.0, (totalBudget / progressMax) * 100.0)));
        progressMonthlyActual.setProgress((int) Math.round(Math.min(100.0, (currentExpense / progressMax) * 100.0)));

        List<CategorySpend> sorted = new ArrayList<>(expenseByCategory.values());
        sorted.sort(Comparator.comparingDouble((CategorySpend item) -> item.amount).reversed());
        renderCategoryRows(sorted, state.getCategories());
    }

    private void applyTrendUi(double currentExpense, double previousExpense) {
        if (previousExpense <= 0.0) {
            tvMonthlyTrend.setText(R.string.label_monthly_trend_no_previous);
            tvMonthlyTrend.setBackgroundResource(R.drawable.bg_monthly_trend_neutral);
            tvMonthlyTrend.setTextColor(getColor(R.color.group_other_tint));
            return;
        }
        if (currentExpense <= previousExpense) {
            int percent = (int) Math.round(((previousExpense - currentExpense) / previousExpense) * 100.0);
            tvMonthlyTrend.setText(getString(R.string.label_monthly_trend_lower, Math.max(0, percent)));
            tvMonthlyTrend.setBackgroundResource(R.drawable.bg_monthly_trend_positive);
            tvMonthlyTrend.setTextColor(getColor(R.color.group_cash_tint));
            return;
        }
        int percent = (int) Math.round(((currentExpense - previousExpense) / previousExpense) * 100.0);
        tvMonthlyTrend.setText(getString(R.string.label_monthly_trend_higher, Math.max(0, percent)));
        tvMonthlyTrend.setBackgroundResource(R.drawable.bg_monthly_trend_negative);
        tvMonthlyTrend.setTextColor(getColor(R.color.overview_warning_text));
    }

    private void applyBudgetBalanceUi(double totalBudget, double currentExpense) {
        if (totalBudget <= 0.0) {
            tvMonthlyRemaining.setText(R.string.label_monthly_budget_missing);
            tvMonthlyRemaining.setBackgroundResource(R.drawable.bg_monthly_trend_neutral);
            tvMonthlyRemaining.setTextColor(getColor(R.color.group_other_tint));
            return;
        }
        if (currentExpense <= totalBudget) {
            tvMonthlyRemaining.setText(getString(R.string.label_monthly_remaining, UiFormatters.money(totalBudget - currentExpense)));
            tvMonthlyRemaining.setBackgroundResource(R.drawable.bg_monthly_trend_positive);
            tvMonthlyRemaining.setTextColor(getColor(R.color.group_cash_tint));
            return;
        }
        tvMonthlyRemaining.setText(getString(R.string.label_monthly_overrun, UiFormatters.money(currentExpense - totalBudget)));
        tvMonthlyRemaining.setBackgroundResource(R.drawable.bg_monthly_trend_negative);
        tvMonthlyRemaining.setTextColor(getColor(R.color.overview_warning_text));
    }

    private void renderCategoryRows(List<CategorySpend> sorted, List<TransactionCategory> categories) {
        double maxAmount = sorted.isEmpty() ? 1.0 : sorted.get(0).amount;
        for (int i = 0; i < categoryRows.length; i++) {
            if (i >= sorted.size()) {
                categoryRows[i].setVisibility(View.GONE);
                continue;
            }
            CategorySpend spend = sorted.get(i);
            categoryRows[i].setVisibility(View.VISIBLE);
            categoryNames[i].setText(spend.name);
            categoryAmounts[i].setText(UiFormatters.money(spend.amount));
            int progress = (int) Math.round(Math.min(100.0, (spend.amount / maxAmount) * 100.0));
            categoryProgressBars[i].setProgress(progress);

            TransactionCategory category = findExpenseCategory(categories, spend.name);
            if (category == null) {
                categoryIconBoxes[i].setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.group_other_bg)));
                categoryIcons[i].setImageResource(R.drawable.ic_category_other);
                categoryIcons[i].setImageTintList(ColorStateList.valueOf(getColor(R.color.group_other_tint)));
                continue;
            }
            categoryIconBoxes[i].setBackgroundTintList(
                ColorStateList.valueOf(getColor(CategoryUiHelper.iconBgForCategory(category)))
            );
            categoryIcons[i].setImageResource(CategoryUiHelper.iconResForCategory(category));
            categoryIcons[i].setImageTintList(
                ColorStateList.valueOf(getColor(CategoryUiHelper.iconTintForCategory(category)))
            );
        }
    }

    private TransactionCategory findExpenseCategory(List<TransactionCategory> categories, String name) {
        String needle = normalizeCategory(name);
        for (TransactionCategory category : categories) {
            if (category.getType() != TransactionType.EXPENSE) {
                continue;
            }
            if (normalizeCategory(category.getName()).equals(needle)) {
                return category;
            }
        }
        return null;
    }

    private boolean sameMonth(ZonedDateTime first, ZonedDateTime second) {
        return first.getYear() == second.getYear() && first.getMonthValue() == second.getMonthValue();
    }

    private String safeCategoryName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return getString(R.string.default_category_other);
        }
        return raw.trim();
    }

    private String normalizeCategory(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static final class CategorySpend {
        private final String name;
        private double amount;

        private CategorySpend(String name) {
            this.name = name;
        }
    }
}

