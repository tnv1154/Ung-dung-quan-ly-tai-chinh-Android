package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CategoryActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_TYPE = "extra_initial_type";

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private final List<TransactionCategory> categories = new ArrayList<>();
    private TransactionType selectedType = TransactionType.EXPENSE;
    private String searchQuery = "";

    private MaterialButtonToggleGroup toggleGroup;
    private MaterialButton btnExpense;
    private MaterialButton btnIncome;
    private LinearLayout listContainer;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);
        bindViews();
        applyIntent();
        setupToolbar();
        setupToggle();
        setupSearch();
        setupActions();
        setupSession();
    }

    private void bindViews() {
        toggleGroup = findViewById(R.id.toggleCategoryType);
        btnExpense = findViewById(R.id.btnCategoryExpense);
        btnIncome = findViewById(R.id.btnCategoryIncome);
        listContainer = findViewById(R.id.layoutCategoryContainer);
        tvEmpty = findViewById(R.id.tvCategoryEmpty);
    }

    private void applyIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String typeRaw = intent.getStringExtra(EXTRA_INITIAL_TYPE);
        if ("INCOME".equalsIgnoreCase(typeRaw)) {
            selectedType = TransactionType.INCOME;
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarCategory);
        toolbar.setTitle(R.string.app_title_edit_categories);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupToggle() {
        if (selectedType == TransactionType.INCOME) {
            toggleGroup.check(R.id.btnCategoryIncome);
        } else {
            toggleGroup.check(R.id.btnCategoryExpense);
        }
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            selectedType = checkedId == R.id.btnCategoryIncome ? TransactionType.INCOME : TransactionType.EXPENSE;
            styleTypeButtons();
            renderCategoryList();
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

    private void setupSearch() {
        TextInputEditText etSearch = findViewById(R.id.etCategorySearch);
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
                renderCategoryList();
            }
        });
    }

    private void setupActions() {
        FloatingActionButton fab = findViewById(R.id.fabAddCategory);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(this, CategoryFormActivity.class);
            intent.putExtra(CategoryFormActivity.EXTRA_INITIAL_TYPE, selectedType.name());
            startActivity(intent);
        });
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
        categories.clear();
        categories.addAll(CategoryFallbackMerger.mergeWithFallbacks(state.getCategories()));
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()
            && !state.getErrorMessage().contains("PERMISSION_DENIED")) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        } else if (state.getErrorMessage() != null && state.getErrorMessage().contains("PERMISSION_DENIED")) {
            financeViewModel.clearError();
        }
        renderCategoryList();
    }

    private void renderCategoryList() {
        listContainer.removeAllViews();
        List<TransactionCategory> typed = new ArrayList<>();
        for (TransactionCategory category : categories) {
            if (category.getType() == selectedType) {
                typed.add(category);
            }
        }
        typed.sort(
            Comparator.comparingInt(TransactionCategory::getSortOrder)
                .thenComparing(TransactionCategory::getName)
        );
        if (selectedType == TransactionType.EXPENSE) {
            renderExpenseCategories(typed);
        } else {
            renderIncomeCategories(typed);
        }
    }

    private void renderIncomeCategories(List<TransactionCategory> typed) {
        int count = 0;
        for (TransactionCategory category : typed) {
            if (!CategoryUiHelper.matchesSearch(category, searchQuery)) {
                continue;
            }
            addManageEntry(category, false, getString(R.string.category_mode_income));
            count++;
        }
        showEmptyState(count == 0);
    }

    private void renderExpenseCategories(List<TransactionCategory> typed) {
        List<TransactionCategory> parents = new ArrayList<>();
        List<TransactionCategory> leafOnly = new ArrayList<>();
        Map<String, List<TransactionCategory>> childrenByParent = new HashMap<>();
        for (TransactionCategory category : typed) {
            if (category.getParentName() == null || category.getParentName().trim().isEmpty()) {
                parents.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentName(), key -> new ArrayList<>()).add(category);
                leafOnly.add(category);
            }
        }
        parents.sort(
            Comparator.comparingInt(TransactionCategory::getSortOrder)
                .thenComparing(TransactionCategory::getName)
        );
        for (List<TransactionCategory> value : childrenByParent.values()) {
            value.sort(
                Comparator.comparingInt(TransactionCategory::getSortOrder)
                    .thenComparing(TransactionCategory::getName)
            );
        }

        int count = 0;
        Set<String> renderedChildIds = new HashSet<>();
        for (TransactionCategory parent : parents) {
            List<TransactionCategory> children = childrenByParent.get(parent.getName());
            boolean parentMatch = CategoryUiHelper.matchesSearch(parent, searchQuery);
            boolean hasChildMatch = false;
            if (children != null) {
                for (TransactionCategory child : children) {
                    if (CategoryUiHelper.matchesSearch(child, searchQuery)) {
                        hasChildMatch = true;
                        break;
                    }
                }
            }
            if (!parentMatch && !hasChildMatch) {
                continue;
            }
            addManageEntry(parent, false, getString(R.string.label_category_group_parent));
            count++;
            if (children != null) {
                for (TransactionCategory child : children) {
                    if (!CategoryUiHelper.matchesSearch(child, searchQuery)) {
                        continue;
                    }
                    addManageEntry(child, true, parent.getName());
                    renderedChildIds.add(child.getId());
                    count++;
                }
            }
        }

        for (TransactionCategory child : leafOnly) {
            if (renderedChildIds.contains(child.getId())) {
                continue;
            }
            if (!CategoryUiHelper.matchesSearch(child, searchQuery)) {
                continue;
            }
            addManageEntry(child, true, child.getParentName());
            count++;
        }
        showEmptyState(count == 0);
    }

    private void showEmptyState(boolean empty) {
        if (empty) {
            tvEmpty.setVisibility(View.VISIBLE);
            listContainer.addView(tvEmpty);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void addManageEntry(TransactionCategory category, boolean child, String subtitle) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_category_entry, listContainer, false);
        View layoutIcon = view.findViewById(R.id.layoutCategoryEntryIcon);
        ImageView ivIcon = view.findViewById(R.id.ivCategoryEntryIcon);
        TextView tvTitle = view.findViewById(R.id.tvCategoryEntryTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvCategoryEntrySubtitle);
        ImageButton btnAction = view.findViewById(R.id.btnCategoryEntryAction);

        layoutIcon.getBackground().setTint(ContextCompat.getColor(this, CategoryUiHelper.iconBgForCategory(category)));
        ivIcon.setImageResource(CategoryUiHelper.iconResForCategory(category));
        ivIcon.setImageTintList(ContextCompat.getColorStateList(this, CategoryUiHelper.iconTintForCategory(category)));
        tvTitle.setText(category.getName());
        tvSubtitle.setVisibility(View.VISIBLE);
        tvSubtitle.setText(subtitle == null || subtitle.trim().isEmpty()
            ? getString(R.string.label_category_group_other)
            : subtitle);
        boolean isFallback = category.getId() != null && category.getId().startsWith("fallback_");
        if (isFallback) {
            btnAction.setVisibility(View.GONE);
        } else {
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setOnClickListener(v -> confirmDelete(category));
        }

        if (child) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMarginStart(dp(16));
            view.setLayoutParams(params);
        }
        listContainer.addView(view);
    }

    private void confirmDelete(TransactionCategory category) {
        if (financeViewModel == null) {
            return;
        }
        List<String> deleteIds = new ArrayList<>();
        deleteIds.add(category.getId());
        if (selectedType == TransactionType.EXPENSE && (category.getParentName() == null || category.getParentName().trim().isEmpty())) {
            for (TransactionCategory item : categories) {
                if (item.getType() == TransactionType.EXPENSE && category.getName().equals(item.getParentName())) {
                    deleteIds.add(item.getId());
                }
            }
        }
        int affectedCount = deleteIds.size();
        String message = affectedCount > 1
            ? getString(R.string.dialog_delete_category_with_children_message, affectedCount)
            : getString(R.string.dialog_delete_category_message);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_category_title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                for (String id : deleteIds) {
                    financeViewModel.deleteCategory(id);
                }
            })
            .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
