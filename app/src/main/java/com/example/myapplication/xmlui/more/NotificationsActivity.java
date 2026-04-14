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
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.notifications.AppNotificationCenter;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE_NAV_ITEM_ID = "extra_source_nav_item_id";

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private int sourceNavItemId = R.id.nav_overview;

    private View cardExceeded;
    private View cardWarning;
    private View cardReminder;
    private View cardMonthlyReport;
    private TextView tvYesterdayLabel;
    private TextView tvLatestEmpty;
    private TextView tvExceededBody;
    private TextView tvWarningBody;
    private TextView tvReminderBody;
    private TextView tvExceededTime;
    private TextView tvWarningTime;
    private TextView tvReminderTime;
    private TextView tvReportBody;
    private TextView tvReportTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_list);
        applyHeaderStatusBarStyle();
        sourceNavItemId = resolveSourceNavItemId(getIntent());
        bindViews();
        setupTopBar();
        setupBottomNavigation();
        setupSession();
    }

    private void bindViews() {
        cardExceeded = findViewById(R.id.cardNotificationExceeded);
        cardWarning = findViewById(R.id.cardNotificationWarning);
        cardReminder = findViewById(R.id.cardNotificationReminder);
        cardMonthlyReport = findViewById(R.id.cardNotificationMonthlyReport);
        tvYesterdayLabel = findViewById(R.id.tvNotificationYesterdayLabel);
        tvLatestEmpty = findViewById(R.id.tvNotificationLatestEmpty);
        tvExceededBody = findViewById(R.id.tvNotificationExceededBody);
        tvWarningBody = findViewById(R.id.tvNotificationWarningBody);
        tvReminderBody = findViewById(R.id.tvNotificationReminderBody);
        tvExceededTime = findViewById(R.id.tvNotificationExceededTime);
        tvWarningTime = findViewById(R.id.tvNotificationWarningTime);
        tvReminderTime = findViewById(R.id.tvNotificationReminderTime);
        tvReportBody = findViewById(R.id.tvNotificationReportBody);
        tvReportTime = findViewById(R.id.tvNotificationReportTime);
    }

    private void setupTopBar() {
        findViewById(R.id.btnNotificationsBack).setOnClickListener(v -> finish());
        cardMonthlyReport.setOnClickListener(v -> startActivity(new Intent(this, MonthlyReportActivity.class)));
        cardExceeded.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        cardWarning.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        cardReminder.setOnClickListener(v -> {
            Intent addIntent = new Intent(this, AddTransactionActivity.class);
            addIntent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_EXPENSE);
            startActivity(addIntent);
        });
    }

    private void applyHeaderStatusBarStyle() {
        getWindow().setStatusBarColor(getColor(R.color.card_bg));
        View decorView = getWindow().getDecorView();
        int flags = decorView.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decorView.setSystemUiVisibility(flags);
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
        BudgetAlertNotifier.maybeNotifyNearLimit(this, state);

        AppNotificationCenter.InAppNotificationEntry exceededEntry =
            AppNotificationCenter.getLatestBudgetExceededEntry(this);
        AppNotificationCenter.InAppNotificationEntry warningEntry =
            AppNotificationCenter.getLatestBudgetWarningEntry(this);
        AppNotificationCenter.InAppNotificationEntry reminderEntry =
            AppNotificationCenter.getLatestReminderEntry(this);
        AppNotificationCenter.InAppNotificationEntry monthlyReportEntry =
            AppNotificationCenter.getLatestMonthlyReportEntry(this);

        boolean hasExceeded = bindNotificationCard(cardExceeded, tvExceededBody, tvExceededTime, exceededEntry);
        boolean hasWarning = bindNotificationCard(cardWarning, tvWarningBody, tvWarningTime, warningEntry);
        boolean hasReminder = bindNotificationCard(cardReminder, tvReminderBody, tvReminderTime, reminderEntry);
        boolean hasMonthlyReport = bindNotificationCard(cardMonthlyReport, tvReportBody, tvReportTime, monthlyReportEntry);

        tvLatestEmpty.setVisibility(hasExceeded || hasWarning || hasReminder ? View.GONE : View.VISIBLE);
        tvYesterdayLabel.setVisibility(hasMonthlyReport ? View.VISIBLE : View.GONE);
    }

    private boolean bindNotificationCard(
        View card,
        TextView bodyView,
        TextView timeView,
        AppNotificationCenter.InAppNotificationEntry entry
    ) {
        if (entry == null) {
            card.setVisibility(View.GONE);
            return false;
        }
        card.setVisibility(View.VISIBLE);
        bodyView.setText(entry.getBody());
        timeView.setText(formatNotificationTime(entry.getTriggeredAtMillis()));
        return true;
    }

    private String formatNotificationTime(long timeMillis) {
        if (timeMillis <= 0L) {
            return "";
        }
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timeMillis);
        if (isSameDay(now, target)) {
            return new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(timeMillis));
        }
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, target)) {
            return getString(R.string.label_time_yesterday);
        }
        return new SimpleDateFormat("dd/MM HH:mm", Locale.ROOT).format(new Date(timeMillis));
    }

    private boolean isSameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
            && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

