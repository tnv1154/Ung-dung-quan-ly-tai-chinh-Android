package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BudgetCategoryPickerActivity extends AppCompatActivity {
    public static final String EXTRA_SELECTED_CATEGORY = "extra_selected_budget_category";
    public static final String EXTRA_RESULT_CATEGORY = "extra_result_budget_category";

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private final List<TransactionCategory> expenseCategories = new ArrayList<>();
    private String selectedCategory = BudgetLimit.CATEGORY_ALL;
    private String searchQuery = "";

    private LinearLayout layoutRows;
    private TextView tvCount;
    private TextView tvEmpty;
    private ImageView ivSelectAllState;
    private MaterialButton btnConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget_category_picker);
        readIntentData();
        bindViews();
        setupToolbar();
        setupSearch();
        setupActions();
        setupSession();
        renderCategoryRows();
    }

    private void readIntentData() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String category = intent.getStringExtra(EXTRA_SELECTED_CATEGORY);
        if (category != null && !category.trim().isEmpty()) {
            selectedCategory = category.trim();
        }
    }

    private void bindViews() {
        layoutRows = findViewById(R.id.layoutBudgetCategoryRows);
        tvCount = findViewById(R.id.tvBudgetCategoryCount);
        tvEmpty = findViewById(R.id.tvBudgetCategoryEmpty);
        ivSelectAllState = findViewById(R.id.ivBudgetCategorySelectAllState);
        btnConfirm = findViewById(R.id.btnBudgetCategoryConfirm);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarBudgetCategoryPicker);
        toolbar.setTitle(R.string.budget_category_picker_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSearch() {
        TextInputEditText etSearch = findViewById(R.id.etBudgetCategorySearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                searchQuery = editable == null ? "" : editable.toString().trim();
                renderCategoryRows();
            }
        });
    }

    private void setupActions() {
        findViewById(R.id.rowBudgetCategorySelectAll).setOnClickListener(v -> {
            selectedCategory = isAllCategorySelected() ? "" : BudgetLimit.CATEGORY_ALL;
            renderCategoryRows();
        });
        btnConfirm.setOnClickListener(v -> submitSelection());
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
        expenseCategories.clear();
        List<TransactionCategory> merged = CategoryFallbackMerger.mergeWithFallbacks(state.getCategories());
        for (TransactionCategory category : merged) {
            if (category.getType() == TransactionType.EXPENSE) {
                expenseCategories.add(category);
            }
        }
        if (!isAllCategorySelected() && !isNoneSelected() && findCategoryByName(selectedCategory) == null) {
            selectedCategory = BudgetLimit.CATEGORY_ALL;
        }
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()
            && !state.getErrorMessage().contains("PERMISSION_DENIED")) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        } else if (state.getErrorMessage() != null && state.getErrorMessage().contains("PERMISSION_DENIED")) {
            financeViewModel.clearError();
        }
        renderCategoryRows();
    }

    private void renderCategoryRows() {
        layoutRows.removeAllViews();
        List<DisplayCategoryRow> displayRows = buildDisplayRows(searchQuery);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (DisplayCategoryRow rowItem : displayRows) {
            View row = inflater.inflate(R.layout.item_budget_category_selection, layoutRows, false);
            View indent = row.findViewById(R.id.vBudgetCategoryIndent);
            FrameLayout iconContainer = row.findViewById(R.id.layoutBudgetCategoryIcon);
            ImageView ivIcon = row.findViewById(R.id.ivBudgetCategoryIcon);
            TextView tvName = row.findViewById(R.id.tvBudgetCategoryName);
            ImageView ivCheck = row.findViewById(R.id.ivBudgetCategoryChecked);

            ViewGroup.LayoutParams indentParams = indent.getLayoutParams();
            indentParams.width = rowItem.child ? dp(24) : 0;
            indent.setLayoutParams(indentParams);

            iconContainer.setBackgroundTintList(
                ColorStateList.valueOf(getColor(CategoryUiHelper.iconBgForCategory(rowItem.category)))
            );
            ivIcon.setImageResource(CategoryUiHelper.iconResForCategory(rowItem.category));
            ivIcon.setImageTintList(
                ColorStateList.valueOf(getColor(CategoryUiHelper.iconTintForCategory(rowItem.category)))
            );
            tvName.setText(rowItem.category.getName());
            if (rowItem.parent) {
                tvName.setTextColor(getColor(R.color.add_mode_blue));
                tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
            } else {
                tvName.setTextColor(getColor(R.color.text_primary));
                tvName.setTypeface(tvName.getTypeface(), Typeface.NORMAL);
            }

            boolean selected = isCategoryMarkedSelected(rowItem.category);
            styleCheckIcon(ivCheck, selected);
            row.setBackgroundColor(selected ? getColor(R.color.export_search_bg) : getColor(android.R.color.transparent));
            row.setOnClickListener(v -> {
                String categoryName = rowItem.category.getName();
                if (isAllCategorySelected()) {
                    selectedCategory = categoryName;
                } else if (isSelectedCategory(categoryName)) {
                    selectedCategory = "";
                } else {
                    selectedCategory = categoryName;
                }
                renderCategoryRows();
            });
            layoutRows.addView(row);
        }
        tvEmpty.setVisibility(displayRows.isEmpty() ? View.VISIBLE : View.GONE);
        tvCount.setText(getString(R.string.budget_category_count_format, computeSelectedCount()));
        styleCheckIcon(ivSelectAllState, isAllCategorySelected());
    }

    private int computeSelectedCount() {
        if (isAllCategorySelected()) {
            return buildDisplayRows("").size();
        }
        if (isNoneSelected()) {
            return 0;
        }
        TransactionCategory selected = findCategoryByName(selectedCategory);
        if (selected != null && (selected.getParentName() == null || selected.getParentName().trim().isEmpty())) {
            return 1 + countChildrenForParent(selected.getName());
        }
        return 1;
    }

    private List<DisplayCategoryRow> buildDisplayRows(String query) {
        List<TransactionCategory> sorted = new ArrayList<>(expenseCategories);
        sorted.sort(
            Comparator.comparingInt(TransactionCategory::getSortOrder)
                .thenComparing(TransactionCategory::getName)
        );

        List<TransactionCategory> parents = new ArrayList<>();
        Map<String, List<TransactionCategory>> childrenByParent = new HashMap<>();
        for (TransactionCategory category : sorted) {
            if (category.getParentName() == null || category.getParentName().trim().isEmpty()) {
                parents.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentName(), key -> new ArrayList<>()).add(category);
            }
        }

        List<DisplayCategoryRow> rows = new ArrayList<>();
        Set<String> renderedChildIds = new HashSet<>();
        for (TransactionCategory parent : parents) {
            List<TransactionCategory> children = childrenByParent.get(parent.getName());
            boolean parentMatch = CategoryUiHelper.matchesSearch(parent, query);
            boolean hasChildMatch = false;
            if (children != null) {
                for (TransactionCategory child : children) {
                    if (CategoryUiHelper.matchesSearch(child, query)) {
                        hasChildMatch = true;
                        break;
                    }
                }
            }
            if (!parentMatch && !hasChildMatch) {
                continue;
            }
            rows.add(new DisplayCategoryRow(parent, false, true));
            if (children != null) {
                for (TransactionCategory child : children) {
                    if (!CategoryUiHelper.matchesSearch(child, query)) {
                        continue;
                    }
                    rows.add(new DisplayCategoryRow(child, true, false));
                    renderedChildIds.add(child.getId());
                }
            }
        }

        for (TransactionCategory child : sorted) {
            if (child.getParentName() == null || child.getParentName().trim().isEmpty()) {
                continue;
            }
            if (renderedChildIds.contains(child.getId())) {
                continue;
            }
            if (!CategoryUiHelper.matchesSearch(child, query)) {
                continue;
            }
            if (containsParent(parents, child.getParentName())) {
                continue;
            }
            rows.add(new DisplayCategoryRow(child, false, false));
        }
        return rows;
    }

    private boolean containsParent(List<TransactionCategory> parents, String parentName) {
        String target = CategoryUiHelper.normalize(parentName);
        for (TransactionCategory parent : parents) {
            if (CategoryUiHelper.normalize(parent.getName()).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private TransactionCategory findCategoryByName(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }
        String target = categoryName.trim();
        for (TransactionCategory category : expenseCategories) {
            if (category.getName().equalsIgnoreCase(target)) {
                return category;
            }
        }
        return null;
    }

    private boolean isSelectedCategory(String categoryName) {
        if (categoryName == null) {
            return false;
        }
        if (selectedCategory == null) {
            return false;
        }
        return categoryName.trim().equalsIgnoreCase(selectedCategory.trim());
    }

    private boolean isCategoryMarkedSelected(TransactionCategory category) {
        if (category == null) {
            return false;
        }
        if (isAllCategorySelected()) {
            return true;
        }
        if (isNoneSelected()) {
            return false;
        }
        if (isSelectedCategory(category.getName())) {
            return true;
        }
        TransactionCategory selected = findCategoryByName(selectedCategory);
        if (selected == null) {
            return false;
        }
        boolean selectedIsParent = selected.getParentName() == null || selected.getParentName().trim().isEmpty();
        if (!selectedIsParent) {
            return false;
        }
        String parentName = category.getParentName();
        return parentName != null && parentName.trim().equalsIgnoreCase(selected.getName().trim());
    }

    private int countChildrenForParent(String parentName) {
        if (parentName == null || parentName.trim().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TransactionCategory category : expenseCategories) {
            String rowParent = category.getParentName();
            if (rowParent != null && rowParent.trim().equalsIgnoreCase(parentName.trim())) {
                count += 1;
            }
        }
        return count;
    }

    private boolean isAllCategorySelected() {
        return BudgetLimit.CATEGORY_ALL.equalsIgnoreCase(selectedCategory == null ? "" : selectedCategory.trim());
    }

    private boolean isNoneSelected() {
        return selectedCategory == null || selectedCategory.trim().isEmpty();
    }

    private void submitSelection() {
        if (isNoneSelected()) {
            Toast.makeText(this, R.string.budget_category_picker_error_required, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        if (isAllCategorySelected()) {
            result.putExtra(EXTRA_RESULT_CATEGORY, BudgetLimit.CATEGORY_ALL);
        } else {
            result.putExtra(EXTRA_RESULT_CATEGORY, selectedCategory.trim());
        }
        setResult(RESULT_OK, result);
        finish();
    }

    private void styleCheckIcon(ImageView view, boolean selected) {
        if (selected) {
            view.setImageResource(R.drawable.ic_action_check);
            view.setImageTintList(ColorStateList.valueOf(getColor(android.R.color.white)));
            view.setBackgroundResource(R.drawable.bg_budget_check_selected);
        } else {
            view.setImageDrawable(null);
            view.setBackgroundResource(R.drawable.bg_budget_check_unselected);
        }
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

    private static final class DisplayCategoryRow {
        final TransactionCategory category;
        final boolean child;
        final boolean parent;

        DisplayCategoryRow(TransactionCategory category, boolean child, boolean parent) {
            this.category = category;
            this.child = child;
            this.parent = parent;
        }
    }
}
