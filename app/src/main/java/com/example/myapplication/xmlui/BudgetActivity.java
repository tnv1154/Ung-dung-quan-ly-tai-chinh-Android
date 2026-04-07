package com.example.myapplication.xmlui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class BudgetActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;

    private MaterialButton btnCategory;
    private TextInputEditText etAmount;
    private TextView tvError;
    private TextView tvEmpty;
    private BudgetRowAdapter adapter;
    private TransactionCategory selectedCategory;

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
        btnCategory = findViewById(R.id.btnBudgetCategory);
        etAmount = findViewById(R.id.etBudgetAmount);
        tvError = findViewById(R.id.tvBudgetError);
        tvEmpty = findViewById(R.id.tvBudgetEmpty);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarBudget);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupList() {
        RecyclerView recyclerView = findViewById(R.id.rvBudgetList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BudgetRowAdapter(this::confirmDeleteBudget);
        recyclerView.setAdapter(adapter);
    }

    private void setupActions() {
        btnCategory.setOnClickListener(v -> chooseCategory());
        MaterialButton btnAdd = findViewById(R.id.btnAddBudget);
        btnAdd.setOnClickListener(v -> addBudget());
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
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        }
        if (selectedCategory != null) {
            selectedCategory = findCategoryById(state.getCategories(), selectedCategory.getId());
            btnCategory.setText(selectedCategory == null ? getString(R.string.action_choose_category) : selectedCategory.getName());
        }
        List<UiBudget> rows = new ArrayList<>();
        for (BudgetLimit budget : state.getBudgetLimits()) {
            double spent = calculateCurrentMonthExpenseForCategory(state.getTransactions(), budget.getCategory());
            double ratio = budget.getLimitAmount() <= 0.0 ? 0.0 : spent / budget.getLimitAmount();
            rows.add(new UiBudget(
                budget.getId(),
                budget.getCategory(),
                budget.getLimitAmount(),
                spent,
                ratio
            ));
        }
        adapter.submit(rows);
        tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void chooseCategory() {
        if (latestState == null) {
            return;
        }
        List<TransactionCategory> expenseCategories = new ArrayList<>();
        for (TransactionCategory item : latestState.getCategories()) {
            if (item.getType() == TransactionType.EXPENSE) {
                expenseCategories.add(item);
            }
        }
        if (expenseCategories.isEmpty()) {
            showError(getString(R.string.label_category_empty));
            return;
        }
        String[] options = new String[expenseCategories.size()];
        for (int i = 0; i < expenseCategories.size(); i++) {
            options[i] = expenseCategories.get(i).getName();
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_choose_budget_category)
            .setItems(options, (dialog, which) -> {
                selectedCategory = expenseCategories.get(which);
                btnCategory.setText(selectedCategory.getName());
                tvError.setVisibility(View.GONE);
            })
            .show();
    }

    private void addBudget() {
        tvError.setVisibility(View.GONE);
        if (financeViewModel == null) {
            return;
        }
        if (selectedCategory == null) {
            showError(getString(R.string.error_budget_category_required));
            return;
        }
        String amountRaw = etAmount.getText() == null ? "" : etAmount.getText().toString().replace(",", "").trim();
        if (TextUtils.isEmpty(amountRaw)) {
            showError(getString(R.string.error_invalid_amount));
            return;
        }
        Double amount;
        try {
            amount = Double.parseDouble(amountRaw);
        } catch (NumberFormatException ex) {
            amount = null;
        }
        if (amount == null || amount <= 0.0) {
            showError(getString(R.string.error_invalid_amount));
            return;
        }
        financeViewModel.addBudgetLimit(selectedCategory.getName(), amount);
        etAmount.setText("");
        Toast.makeText(this, R.string.message_budget_added, Toast.LENGTH_SHORT).show();
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

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private TransactionCategory findCategoryById(List<TransactionCategory> categories, String id) {
        if (id == null) {
            return null;
        }
        for (TransactionCategory item : categories) {
            if (id.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    private double calculateCurrentMonthExpenseForCategory(
        List<FinanceTransaction> transactions,
        String category
    ) {
        ZonedDateTime now = ZonedDateTime.now();
        double total = 0.0;
        for (FinanceTransaction tx : transactions) {
            ZonedDateTime date = Instant.ofEpochSecond(tx.getCreatedAt().getSeconds(), tx.getCreatedAt().getNanoseconds())
                .atZone(ZoneId.systemDefault());
            boolean sameMonth = date.getYear() == now.getYear() && date.getMonth() == now.getMonth();
            if (sameMonth && tx.getType() == TransactionType.EXPENSE && category.equalsIgnoreCase(tx.getCategory())) {
                total += tx.getAmount();
            }
        }
        return total;
    }
}
