package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.budget.BudgetCycleUtils;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class BudgetActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private BudgetRowAdapter adapter;
    private TextView tvError;
    private TextView tvEmpty;
    private TextView tvSummary;
    private TextView tvSummaryHint;
    private ProgressBar pbSummary;
    private FinanceUiState latestState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);
        bindViews();
        setupToolbar();
        setupList();
        setupActions();
        setupSession();
    }

    private void bindViews() {
        tvError = findViewById(R.id.tvBudgetError);
        tvEmpty = findViewById(R.id.tvBudgetEmpty);
        tvSummary = findViewById(R.id.tvBudgetSummary);
        tvSummaryHint = findViewById(R.id.tvBudgetSummaryHint);
        pbSummary = findViewById(R.id.pbBudgetSummary);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarBudget);
        toolbar.setTitle(R.string.app_title_budgets);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupList() {
        RecyclerView recyclerView = findViewById(R.id.rvBudgetList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BudgetRowAdapter(new BudgetRowAdapter.BudgetActionListener() {
            @Override
            public void onOpen(UiBudget budget) {
                openEditor(budget);
            }

            @Override
            public void onDelete(UiBudget budget) {
                confirmDeleteBudget(budget);
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupActions() {
        MaterialButton btnAdd = findViewById(R.id.btnAddBudget);
        btnAdd.setOnClickListener(v -> openEditor(null));
    }

    private void setupSession() {
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        sessionViewModel.getUiStateLiveData().observe(this, this::renderSessionState);
    }

    private void renderSessionState(@NonNull SessionUiState state) {
        if (state.getCurrentUser() == null) {
            finish();
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
        BudgetAlertNotifier.maybeNotifyNearLimit(this, state);

        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        }

        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();

        List<UiBudget> rows = new ArrayList<>();
        double totalActiveLimit = 0.0;
        double totalActiveSpent = 0.0;
        int activeCount = 0;
        for (BudgetLimit budget : state.getBudgetLimits()) {
            BudgetCycleUtils.BudgetWindow window = BudgetCycleUtils.resolveWindow(budget, today);
            double spent = BudgetCycleUtils.calculateSpentInWindow(
                budget,
                state.getTransactions(),
                CategoryFallbackMerger.mergeWithFallbacks(state.getCategories()),
                zoneId,
                window.getStart(),
                window.getEnd()
            );
            double limitAmount = budget.getLimitAmount();
            double ratio = limitAmount <= 0.0 ? 0.0 : spent / limitAmount;
            double remaining = limitAmount - spent;
            long daysRemaining = BudgetCycleUtils.daysRemaining(window, today);
            String budgetName = resolveBudgetName(budget);
            rows.add(new UiBudget(
                budget.getId(),
                budgetName,
                budget.getCategory(),
                limitAmount,
                spent,
                ratio,
                remaining,
                BudgetCycleUtils.normalizeRepeatCycle(budget.getRepeatCycle()),
                window.getStart().toEpochDay(),
                window.getEnd().toEpochDay(),
                daysRemaining,
                window.isActive()
            ));
            if (window.isActive()) {
                totalActiveLimit += limitAmount;
                totalActiveSpent += spent;
                activeCount += 1;
            }
        }
        adapter.submit(rows);
        tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        tvError.setVisibility(View.GONE);
        updateSummary(totalActiveSpent, totalActiveLimit, activeCount);
    }

    private void updateSummary(double spent, double limit, int activeCount) {
        tvSummary.setText(getString(R.string.budget_summary_format, UiFormatters.money(spent), UiFormatters.money(limit)));
        if (activeCount <= 0) {
            tvSummaryHint.setText(R.string.budget_summary_no_active);
            pbSummary.setProgress(0);
            pbSummary.setProgressTintList(ContextCompat.getColorStateList(this, R.color.blue_primary));
            return;
        }
        tvSummaryHint.setText(getString(R.string.budget_summary_tracking_count, activeCount));
        double ratio = limit <= 0.0 ? 0.0 : spent / limit;
        int progress = (int) Math.min(100.0, Math.max(0.0, ratio * 100.0));
        pbSummary.setProgress(progress);
        if (ratio >= 1.0) {
            pbSummary.setProgressTintList(ContextCompat.getColorStateList(this, R.color.error_red));
        } else if (ratio >= 0.8) {
            pbSummary.setProgressTintList(ContextCompat.getColorStateList(this, R.color.warning_orange));
        } else {
            pbSummary.setProgressTintList(ContextCompat.getColorStateList(this, R.color.blue_primary));
        }
    }

    private String resolveBudgetName(BudgetLimit budget) {
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

    private void openEditor(UiBudget budget) {
        Intent intent = new Intent(this, BudgetEditorActivity.class);
        if (budget != null) {
            BudgetLimit sourceBudget = findBudgetById(budget.getId());
            intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_ID, budget.getId());
            if (sourceBudget != null) {
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_NAME, sourceBudget.getName());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_CATEGORY, sourceBudget.getCategory());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_AMOUNT, sourceBudget.getLimitAmount());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_REPEAT, sourceBudget.getRepeatCycle());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_START_EPOCH_DAY, sourceBudget.getStartDateEpochDay());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_END_EPOCH_DAY, sourceBudget.getEndDateEpochDay());
            } else {
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_NAME, budget.getName());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_CATEGORY, budget.getCategory());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_AMOUNT, budget.getLimitAmount());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_REPEAT, budget.getRepeatCycle());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_START_EPOCH_DAY, budget.getStartDateEpochDay());
                intent.putExtra(BudgetEditorActivity.EXTRA_BUDGET_END_EPOCH_DAY, budget.getEndDateEpochDay());
            }
        }
        startActivity(intent);
    }

    private BudgetLimit findBudgetById(String budgetId) {
        if (latestState == null || budgetId == null) {
            return null;
        }
        for (BudgetLimit budget : latestState.getBudgetLimits()) {
            if (budgetId.equals(budget.getId())) {
                return budget;
            }
        }
        return null;
    }

    private void confirmDeleteBudget(UiBudget budget) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_budget_title)
            .setMessage(R.string.dialog_delete_budget_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                if (financeViewModel != null) {
                    financeViewModel.deleteBudgetLimit(budget.getId());
                }
            })
            .show();
    }
}
