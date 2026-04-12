package com.example.myapplication.xmlui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class BudgetEditorActivity extends AppCompatActivity {
    public static final String EXTRA_BUDGET_ID = "extra_budget_id";
    public static final String EXTRA_BUDGET_NAME = "extra_budget_name";
    public static final String EXTRA_BUDGET_CATEGORY = "extra_budget_category";
    public static final String EXTRA_BUDGET_AMOUNT = "extra_budget_amount";
    public static final String EXTRA_BUDGET_REPEAT = "extra_budget_repeat";
    public static final String EXTRA_BUDGET_START_EPOCH_DAY = "extra_budget_start_epoch_day";
    public static final String EXTRA_BUDGET_END_EPOCH_DAY = "extra_budget_end_epoch_day";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private TextInputEditText etName;
    private TextInputEditText etAmount;
    private TextView tvError;
    private MaterialButton btnCategory;
    private MaterialButton btnRepeatValue;
    private MaterialButton btnStartDate;
    private MaterialButton btnEndDate;
    private MaterialButton btnDelete;

    private String editingBudgetId;
    private String selectedCategory = BudgetLimit.CATEGORY_ALL;
    private String selectedRepeatCycle = BudgetLimit.REPEAT_NONE;
    private LocalDate selectedStartDate = LocalDate.now().withDayOfMonth(1);
    private LocalDate selectedEndDate = LocalDate.now();
    private boolean submitPending;
    private final ActivityResultLauncher<Intent> categoryPickerLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            String category = result.getData().getStringExtra(BudgetCategoryPickerActivity.EXTRA_RESULT_CATEGORY);
            if (category == null || category.trim().isEmpty()) {
                return;
            }
            selectedCategory = category.trim();
            refreshUiState();
            tvError.setVisibility(View.GONE);
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_editor);
        bindViews();
        readIntent();
        setupToolbar();
        setupActions();
        refreshUiState();
        setupSession();
    }

    private void bindViews() {
        etName = findViewById(R.id.etBudgetEditorName);
        etAmount = findViewById(R.id.etBudgetEditorAmount);
        tvError = findViewById(R.id.tvBudgetEditorError);
        btnCategory = findViewById(R.id.btnBudgetEditorCategory);
        btnRepeatValue = findViewById(R.id.btnBudgetRepeatValue);
        btnStartDate = findViewById(R.id.btnBudgetStartDate);
        btnEndDate = findViewById(R.id.btnBudgetEndDate);
        btnDelete = findViewById(R.id.btnBudgetEditorDelete);
    }

    private void readIntent() {
        editingBudgetId = getIntent().getStringExtra(EXTRA_BUDGET_ID);
        String budgetName = getIntent().getStringExtra(EXTRA_BUDGET_NAME);
        String budgetCategory = getIntent().getStringExtra(EXTRA_BUDGET_CATEGORY);
        double budgetAmount = getIntent().getDoubleExtra(EXTRA_BUDGET_AMOUNT, 0.0);
        String repeat = getIntent().getStringExtra(EXTRA_BUDGET_REPEAT);
        long startEpoch = getIntent().getLongExtra(EXTRA_BUDGET_START_EPOCH_DAY, selectedStartDate.toEpochDay());
        long endEpoch = getIntent().getLongExtra(EXTRA_BUDGET_END_EPOCH_DAY, selectedStartDate.plusMonths(1).minusDays(1).toEpochDay());

        if (budgetName != null) {
            etName.setText(budgetName);
        }
        if (budgetCategory != null && !budgetCategory.trim().isEmpty()) {
            selectedCategory = budgetCategory.trim();
        }
        if (budgetAmount > 0.0) {
            etAmount.setText(formatAmountInput(budgetAmount));
        }
        if (BudgetLimit.REPEAT_MONTHLY.equalsIgnoreCase(repeat)) {
            selectedRepeatCycle = BudgetLimit.REPEAT_MONTHLY;
        }
        selectedStartDate = LocalDate.ofEpochDay(startEpoch);
        selectedEndDate = LocalDate.ofEpochDay(endEpoch);
        if (selectedEndDate.isBefore(selectedStartDate)) {
            selectedEndDate = selectedStartDate;
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarBudgetEditor);
        toolbar.setTitle(editingBudgetId == null ? R.string.budget_editor_title_add : R.string.budget_editor_title_edit);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupActions() {
        btnCategory.setOnClickListener(v -> openBudgetCategoryPicker());
        btnRepeatValue.setOnClickListener(v -> chooseRepeatCycle());
        btnStartDate.setOnClickListener(v -> pickDate(true));
        btnEndDate.setOnClickListener(v -> pickDate(false));
        findViewById(R.id.btnBudgetEditorSave).setOnClickListener(v -> saveBudget());
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private void refreshUiState() {
        String categoryText = BudgetLimit.CATEGORY_ALL.equalsIgnoreCase(selectedCategory)
            ? getString(R.string.budget_category_all)
            : selectedCategory;
        btnCategory.setText(categoryText);
        btnRepeatValue.setText(
            BudgetLimit.REPEAT_MONTHLY.equals(selectedRepeatCycle)
                ? getString(R.string.budget_repeat_monthly)
                : getString(R.string.budget_repeat_none)
        );
        btnStartDate.setText(DATE_FORMAT.format(selectedStartDate));
        btnEndDate.setText(DATE_FORMAT.format(selectedEndDate));
        btnDelete.setVisibility(editingBudgetId == null ? View.GONE : View.VISIBLE);
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
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            if (!submitPending) {
                Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            }
            financeViewModel.clearError();
        }
    }

    private void openBudgetCategoryPicker() {
        Intent intent = new Intent(this, BudgetCategoryPickerActivity.class);
        intent.putExtra(BudgetCategoryPickerActivity.EXTRA_SELECTED_CATEGORY, selectedCategory);
        categoryPickerLauncher.launch(intent);
    }

    private void chooseRepeatCycle() {
        String[] options = new String[] {
            getString(R.string.budget_repeat_none),
            getString(R.string.budget_repeat_monthly)
        };
        int checkedIndex = BudgetLimit.REPEAT_MONTHLY.equals(selectedRepeatCycle) ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.budget_editor_label_repeat)
            .setSingleChoiceItems(options, checkedIndex, (dialog, which) -> {
                selectedRepeatCycle = which == 1 ? BudgetLimit.REPEAT_MONTHLY : BudgetLimit.REPEAT_NONE;
                refreshUiState();
                tvError.setVisibility(View.GONE);
                dialog.dismiss();
            })
            .show();
    }

    private void pickDate(boolean isStart) {
        LocalDate initial = isStart ? selectedStartDate : selectedEndDate;
        DatePickerDialog dialog = new DatePickerDialog(
            this,
            (view, year, monthOfYear, dayOfMonth) -> {
                LocalDate picked = LocalDate.of(year, monthOfYear + 1, dayOfMonth);
                if (isStart) {
                    selectedStartDate = picked;
                    if (selectedEndDate.isBefore(selectedStartDate)) {
                        selectedEndDate = selectedStartDate;
                    }
                } else {
                    selectedEndDate = picked;
                    if (selectedEndDate.isBefore(selectedStartDate)) {
                        selectedStartDate = selectedEndDate;
                    }
                }
                refreshUiState();
                tvError.setVisibility(View.GONE);
            },
            initial.getYear(),
            initial.getMonthValue() - 1,
            initial.getDayOfMonth()
        );
        dialog.show();
    }

    private void saveBudget() {
        if (submitPending) {
            return;
        }
        tvError.setVisibility(View.GONE);
        if (financeViewModel == null) {
            showError(getString(R.string.error_unknown));
            return;
        }
        String rawName = etName.getText() == null ? "" : etName.getText().toString().trim();
        String rawAmount = etAmount.getText() == null ? "" : etAmount.getText().toString().replace(",", "").trim();
        if (TextUtils.isEmpty(rawAmount)) {
            showError(getString(R.string.error_invalid_amount));
            return;
        }
        Double amount;
        try {
            amount = Double.parseDouble(rawAmount);
        } catch (NumberFormatException ex) {
            amount = null;
        }
        if (amount == null || amount <= 0.0) {
            showError(getString(R.string.error_invalid_amount));
            return;
        }
        if (selectedEndDate.isBefore(selectedStartDate)) {
            showError(getString(R.string.budget_editor_error_date_range));
            return;
        }
        String fallbackName = BudgetLimit.CATEGORY_ALL.equalsIgnoreCase(selectedCategory)
            ? getString(R.string.budget_category_all)
            : selectedCategory;
        String budgetName = rawName.isEmpty() ? fallbackName : rawName;

        if (editingBudgetId == null) {
            submitPending = true;
            financeViewModel.addBudgetLimit(
                budgetName,
                selectedCategory,
                amount,
                selectedRepeatCycle,
                selectedStartDate.toEpochDay(),
                selectedEndDate.toEpochDay(),
                this::onBudgetSaveCompleted
            );
        } else {
            submitPending = true;
            financeViewModel.updateBudgetLimit(
                editingBudgetId,
                budgetName,
                selectedCategory,
                amount,
                selectedRepeatCycle,
                selectedStartDate.toEpochDay(),
                selectedEndDate.toEpochDay(),
                this::onBudgetSaveCompleted
            );
        }
    }

    private void confirmDelete() {
        if (submitPending || editingBudgetId == null || financeViewModel == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_budget_title)
            .setMessage(R.string.dialog_delete_budget_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                submitPending = true;
                financeViewModel.deleteBudgetLimit(editingBudgetId, this::onBudgetDeleteCompleted);
            })
            .show();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private String formatAmountInput(double amount) {
        if (amount == Math.rint(amount)) {
            return String.format(Locale.US, "%.0f", amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private void onBudgetSaveCompleted(String errorMessage) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            submitPending = false;
            if (errorMessage != null && !errorMessage.isBlank()) {
                showError(errorMessage);
                if (financeViewModel != null) {
                    financeViewModel.clearError();
                }
                return;
            }
            Toast.makeText(
                this,
                editingBudgetId == null ? R.string.message_budget_added : R.string.message_budget_updated,
                Toast.LENGTH_SHORT
            ).show();
            finish();
        });
    }

    private void onBudgetDeleteCompleted(String errorMessage) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            submitPending = false;
            if (errorMessage != null && !errorMessage.isBlank()) {
                showError(errorMessage);
                if (financeViewModel != null) {
                    financeViewModel.clearError();
                }
                return;
            }
            Toast.makeText(this, R.string.message_budget_deleted, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
