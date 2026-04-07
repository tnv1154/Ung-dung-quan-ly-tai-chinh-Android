package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationsActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE_NAV_ITEM_ID = "extra_source_nav_item_id";

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private int sourceNavItemId = R.id.nav_overview;

    private View cardExceeded;
    private View cardWarning;
    private View cardMonthlyReport;
    private TextView tvLatestEmpty;
    private TextView tvExceededBody;
    private TextView tvWarningBody;
    private TextView tvExceededTime;
    private TextView tvWarningTime;
    private TextView tvReportBody;
    private TextView tvReportTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_list);
        sourceNavItemId = resolveSourceNavItemId(getIntent());
        bindViews();
        setupTopBar();
        setupBottomNavigation();
        setupSession();
    }

    private void bindViews() {
        cardExceeded = findViewById(R.id.cardNotificationExceeded);
        cardWarning = findViewById(R.id.cardNotificationWarning);
        cardMonthlyReport = findViewById(R.id.cardNotificationMonthlyReport);
        tvLatestEmpty = findViewById(R.id.tvNotificationLatestEmpty);
        tvExceededBody = findViewById(R.id.tvNotificationExceededBody);
        tvWarningBody = findViewById(R.id.tvNotificationWarningBody);
        tvExceededTime = findViewById(R.id.tvNotificationExceededTime);
        tvWarningTime = findViewById(R.id.tvNotificationWarningTime);
        tvReportBody = findViewById(R.id.tvNotificationReportBody);
        tvReportTime = findViewById(R.id.tvNotificationReportTime);
    }

    private void setupTopBar() {
        findViewById(R.id.btnNotificationsBack).setOnClickListener(v -> finish());
        cardMonthlyReport.setOnClickListener(v -> startActivity(new Intent(this, MonthlyReportActivity.class)));
        cardExceeded.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        cardWarning.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(sourceNavItemId);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == sourceNavItemId) {
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

    private int resolveSourceNavItemId(Intent intent) {
        if (intent == null) {
            return R.id.nav_overview;
        }
        int source = intent.getIntExtra(EXTRA_SOURCE_NAV_ITEM_ID, R.id.nav_overview);
        if (source == R.id.nav_overview
            || source == R.id.nav_accounts
            || source == R.id.nav_add
            || source == R.id.nav_report
            || source == R.id.nav_more) {
            return source;
        }
        return R.id.nav_overview;
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

        Map<String, Double> expenseByCategory = currentMonthExpenseByCategory(state.getTransactions());
        BudgetSignal exceeded = null;
        BudgetSignal warning = null;
        for (BudgetLimit budget : state.getBudgetLimits()) {
            if (budget.getLimitAmount() <= 0.0) {
                continue;
            }
            String categoryKey = normalizeCategory(budget.getCategory());
            if (categoryKey.isEmpty()) {
                continue;
            }
            double used = expenseByCategory.getOrDefault(categoryKey, 0.0);
            double ratio = used / budget.getLimitAmount();
            if (ratio >= 1.0) {
                if (exceeded == null || ratio > exceeded.ratio) {
                    exceeded = new BudgetSignal(budget.getCategory(), ratio);
                }
            } else if (ratio >= 0.8) {
                if (warning == null || ratio > warning.ratio) {
                    warning = new BudgetSignal(budget.getCategory(), ratio);
                }
            }
        }

        if (exceeded != null) {
            cardExceeded.setVisibility(View.VISIBLE);
            tvExceededBody.setText(
                getString(
                    R.string.notification_item_budget_exceeded_body_with_category,
                    safeCategoryName(exceeded.category)
                )
            );
            tvExceededTime.setText(currentTimeLabel());
        } else {
            cardExceeded.setVisibility(View.GONE);
        }

        if (warning != null) {
            cardWarning.setVisibility(View.VISIBLE);
            int ratioPercent = (int) Math.round(warning.ratio * 100.0);
            tvWarningBody.setText(getString(R.string.notification_item_budget_warning_body, ratioPercent));
            tvWarningTime.setText(currentTimeLabel());
        } else {
            cardWarning.setVisibility(View.GONE);
        }

        tvLatestEmpty.setVisibility(
            cardExceeded.getVisibility() == View.VISIBLE || cardWarning.getVisibility() == View.VISIBLE
                ? View.GONE
                : View.VISIBLE
        );

        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        tvReportBody.setText(getString(R.string.notification_item_monthly_report_body, month));
        tvReportTime.setText(getString(R.string.label_time_yesterday));
    }

    private Map<String, Double> currentMonthExpenseByCategory(List<FinanceTransaction> transactions) {
        Map<String, Double> expenses = new HashMap<>();
        ZonedDateTime now = ZonedDateTime.now();
        for (FinanceTransaction tx : transactions) {
            if (tx.getType() != TransactionType.EXPENSE) {
                continue;
            }
            ZonedDateTime txTime = Instant.ofEpochSecond(tx.getCreatedAt().getSeconds(), tx.getCreatedAt().getNanoseconds())
                .atZone(ZoneId.systemDefault());
            if (txTime.getYear() != now.getYear() || txTime.getMonthValue() != now.getMonthValue()) {
                continue;
            }
            String key = normalizeCategory(tx.getCategory());
            if (key.isEmpty()) {
                continue;
            }
            expenses.put(key, expenses.getOrDefault(key, 0.0) + tx.getAmount());
        }
        return expenses;
    }

    private String normalizeCategory(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeCategoryName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return getString(R.string.default_category_other);
        }
        return value.trim();
    }

    private String currentTimeLabel() {
        return new SimpleDateFormat("hh:mm a", Locale.US).format(new Date());
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static final class BudgetSignal {
        private final String category;
        private final double ratio;

        private BudgetSignal(String category, double ratio) {
            this.category = category;
            this.ratio = ratio;
        }
    }
}

