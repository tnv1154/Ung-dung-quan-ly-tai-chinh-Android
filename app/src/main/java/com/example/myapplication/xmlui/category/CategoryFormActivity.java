package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class CategoryFormActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_TYPE = "extra_initial_type";
    public static final String EXTRA_EDIT_CATEGORY_ID = "extra_edit_category_id";
    public static final String EXTRA_EDIT_CATEGORY_NAME = "extra_edit_category_name";
    public static final String EXTRA_EDIT_CATEGORY_TYPE = "extra_edit_category_type";
    public static final String EXTRA_EDIT_CATEGORY_PARENT_NAME = "extra_edit_category_parent_name";
    public static final String EXTRA_EDIT_CATEGORY_ICON_KEY = "extra_edit_category_icon_key";
    public static final String EXTRA_EDIT_CATEGORY_SORT_ORDER = "extra_edit_category_sort_order";

    private static final String[] ICON_KEYS = new String[] {
        "dot", "food", "transport", "utility", "money_in", "money_out", "gift", "health", "home", "other"
    };
    private static final int[] ICON_LABEL_RES = new int[] {
        R.string.category_icon_dot,
        R.string.category_icon_food,
        R.string.category_icon_transport,
        R.string.category_icon_utility,
        R.string.category_icon_money_in,
        R.string.category_icon_money_out,
        R.string.category_icon_gift,
        R.string.category_icon_health,
        R.string.category_icon_home,
        R.string.category_icon_other
    };

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private final List<TransactionCategory> categories = new ArrayList<>();
    private TransactionType selectedType = TransactionType.EXPENSE;
    private String selectedParentName = "";
    private String selectedIconKey = ICON_KEYS[0];
    private String editingCategoryId;
    private TransactionType originalEditType = TransactionType.EXPENSE;
    private String originalEditParentName = "";
    private int editingSortOrder = 0;

    private MaterialButtonToggleGroup toggleGroup;
    private MaterialButton btnExpense;
    private MaterialButton btnIncome;
    private MaterialButton btnPickParent;
    private MaterialButton btnPickIcon;
    private TextInputEditText etName;
    private View layoutCategoryIcon;
    private ImageView ivCategoryIcon;
    private TextView tvIconName;
    private TextView tvError;
    private View layoutParent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_form);
        bindViews();
        applyIntent();
        setupToolbar();
        setupToggle();
        setupActions();
        setupSession();
        refreshUiState();
    }

    private void bindViews() {
        toggleGroup = findViewById(R.id.toggleCategoryFormType);
        btnExpense = findViewById(R.id.btnCategoryFormExpense);
        btnIncome = findViewById(R.id.btnCategoryFormIncome);
        btnPickParent = findViewById(R.id.btnPickParentCategory);
        btnPickIcon = findViewById(R.id.btnPickCategoryIcon);
        etName = findViewById(R.id.etCategoryFormName);
        layoutCategoryIcon = findViewById(R.id.layoutCategoryFormIcon);
        ivCategoryIcon = findViewById(R.id.ivCategoryFormIcon);
        tvIconName = findViewById(R.id.tvCategoryFormIconName);
        tvError = findViewById(R.id.tvCategoryFormError);
        layoutParent = findViewById(R.id.layoutCategoryParentSelector);
    }

    private void applyIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        editingCategoryId = safe(intent.getStringExtra(EXTRA_EDIT_CATEGORY_ID));
        if (!editingCategoryId.isEmpty()) {
            selectedType = parseType(intent.getStringExtra(EXTRA_EDIT_CATEGORY_TYPE), selectedType);
            originalEditType = selectedType;
            selectedParentName = safe(intent.getStringExtra(EXTRA_EDIT_CATEGORY_PARENT_NAME));
            originalEditParentName = selectedParentName;
            String iconFromEdit = safe(intent.getStringExtra(EXTRA_EDIT_CATEGORY_ICON_KEY));
            if (!iconFromEdit.isEmpty()) {
                selectedIconKey = iconFromEdit;
            }
            editingSortOrder = Math.max(0, intent.getIntExtra(EXTRA_EDIT_CATEGORY_SORT_ORDER, 0));
            String editName = safe(intent.getStringExtra(EXTRA_EDIT_CATEGORY_NAME));
            if (!editName.isEmpty()) {
                etName.setText(editName);
            }
            return;
        }
        String typeRaw = intent.getStringExtra(EXTRA_INITIAL_TYPE);
        selectedType = parseType(typeRaw, selectedType);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarCategoryForm);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupToggle() {
        if (selectedType == TransactionType.INCOME) {
            toggleGroup.check(R.id.btnCategoryFormIncome);
        } else {
            toggleGroup.check(R.id.btnCategoryFormExpense);
        }
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            selectedType = checkedId == R.id.btnCategoryFormIncome ? TransactionType.INCOME : TransactionType.EXPENSE;
            if (selectedType == TransactionType.INCOME) {
                selectedParentName = "";
            }
            styleTypeButtons();
            refreshUiState();
        });
        styleTypeButtons();
    }

    private void styleTypeButtons() {
        styleTypeButton(btnExpense, selectedType == TransactionType.EXPENSE);
        styleTypeButton(btnIncome, selectedType == TransactionType.INCOME);
    }

    private void styleTypeButton(MaterialButton button, boolean selected) {
        int fill = selected ? R.color.blue_primary : R.color.card_bg;
        int text = selected ? android.R.color.white : R.color.text_primary;
        int stroke = selected ? R.color.blue_primary : R.color.divider;
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(fill)));
        button.setStrokeColor(ColorStateList.valueOf(getColor(stroke)));
        button.setTextColor(getColor(text));
    }

    private void setupActions() {
        btnPickParent.setOnClickListener(v -> chooseParentCategory());
        btnPickIcon.setOnClickListener(v -> chooseIcon());
        findViewById(R.id.btnSaveCategory).setOnClickListener(v -> saveCategory());
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
        financeViewModel.ensureDefaultCategories();
    }

    private void renderFinanceState(@NonNull FinanceUiState state) {
        categories.clear();
        categories.addAll(state.getCategories());
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()
            && !state.getErrorMessage().contains("PERMISSION_DENIED")) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        } else if (state.getErrorMessage() != null && state.getErrorMessage().contains("PERMISSION_DENIED")) {
            financeViewModel.clearError();
        }
        refreshUiState();
    }

    private void refreshUiState() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarCategoryForm);
        int titleRes;
        if (isEditMode()) {
            titleRes = R.string.action_edit_categories;
        } else {
            titleRes = selectedType == TransactionType.INCOME
                ? R.string.app_title_add_category_income
                : R.string.app_title_add_category_expense;
        }
        toolbar.setTitle(titleRes);

        layoutParent.setVisibility(selectedType == TransactionType.EXPENSE ? View.VISIBLE : View.GONE);
        if (selectedType == TransactionType.INCOME) {
            selectedParentName = "";
        }
        btnPickParent.setText(
            TextUtils.isEmpty(selectedParentName) ? getString(R.string.action_parent_none) : selectedParentName
        );
        layoutCategoryIcon.getBackground().setTint(
            ContextCompat.getColor(this, CategoryUiHelper.iconBgForKey(selectedIconKey, selectedType))
        );
        ivCategoryIcon.setImageResource(CategoryUiHelper.iconResForKey(selectedIconKey, selectedType));
        ivCategoryIcon.setImageTintList(
            ContextCompat.getColorStateList(this, CategoryUiHelper.iconTintForKey(selectedIconKey, selectedType))
        );
        tvIconName.setText(getIconLabel(selectedIconKey));
    }

    private void chooseParentCategory() {
        if (selectedType != TransactionType.EXPENSE) {
            return;
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.action_parent_none));
        for (TransactionCategory category : categories) {
            if (category.getType() == TransactionType.EXPENSE
                && (category.getParentName() == null || category.getParentName().trim().isEmpty())) {
                options.add(category.getName());
            }
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_parent_category)
            .setItems(options.toArray(new String[0]), (dialog, which) -> {
                selectedParentName = which == 0 ? "" : options.get(which);
                refreshUiState();
            })
            .show();
    }

    private void chooseIcon() {
        String[] labels = new String[ICON_KEYS.length];
        int checked = 0;
        for (int i = 0; i < ICON_KEYS.length; i++) {
            labels[i] = getString(ICON_LABEL_RES[i]);
            if (ICON_KEYS[i].equals(selectedIconKey)) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_category_icon)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                selectedIconKey = ICON_KEYS[which];
                refreshUiState();
            })
            .setPositiveButton(R.string.action_done, null)
            .show();
    }

    private void saveCategory() {
        tvError.setVisibility(View.GONE);
        if (financeViewModel == null) {
            return;
        }
        String name = etName.getText() == null ? "" : etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            tvError.setText(R.string.error_category_name_required);
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        for (TransactionCategory item : categories) {
            if (isEditMode() && item.getId().equals(editingCategoryId)) {
                continue;
            }
            boolean sameType = item.getType() == selectedType;
            boolean sameName = item.getName().equalsIgnoreCase(name);
            boolean sameParent = safe(item.getParentName()).equalsIgnoreCase(safe(selectedParentName));
            if (sameType && sameName && sameParent) {
                tvError.setText(R.string.error_category_name_duplicate);
                tvError.setVisibility(View.VISIBLE);
                return;
            }
        }

        int nextOrder = 1;
        for (TransactionCategory item : categories) {
            if (item.getType() != selectedType) {
                continue;
            }
            if (!safe(item.getParentName()).equalsIgnoreCase(safe(selectedParentName))) {
                continue;
            }
            nextOrder = Math.max(nextOrder, item.getSortOrder() + 1);
        }

        if (isEditMode()) {
            int resolvedSortOrder = editingSortOrder > 0 ? editingSortOrder : nextOrder;
            boolean movedGroup = selectedType != originalEditType
                || !safe(selectedParentName).equalsIgnoreCase(safe(originalEditParentName));
            if (movedGroup) {
                resolvedSortOrder = nextOrder;
            }
            financeViewModel.updateCategory(
                editingCategoryId,
                name,
                selectedType,
                selectedParentName,
                selectedIconKey,
                resolvedSortOrder
            );
            Toast.makeText(this, R.string.message_category_updated, Toast.LENGTH_SHORT).show();
        } else {
            financeViewModel.addCategory(name, selectedType, selectedParentName, selectedIconKey, nextOrder);
            Toast.makeText(this, R.string.message_category_added, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private boolean isEditMode() {
        return editingCategoryId != null && !editingCategoryId.isEmpty();
    }

    private TransactionType parseType(String rawType, TransactionType fallback) {
        if ("INCOME".equalsIgnoreCase(safe(rawType))) {
            return TransactionType.INCOME;
        }
        if ("EXPENSE".equalsIgnoreCase(safe(rawType))) {
            return TransactionType.EXPENSE;
        }
        return fallback == null ? TransactionType.EXPENSE : fallback;
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private String getIconLabel(String iconKey) {
        for (int i = 0; i < ICON_KEYS.length; i++) {
            if (ICON_KEYS[i].equals(iconKey)) {
                return getString(ICON_LABEL_RES[i]);
            }
        }
        return getString(R.string.category_icon_dot);
    }
}
