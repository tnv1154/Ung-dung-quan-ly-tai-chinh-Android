package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
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
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CategoryPickerActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_SELECTED_CATEGORY_ID = "extra_selected_category_id";
    public static final String EXTRA_SELECTED_CATEGORY_NAME = "extra_selected_category_name";
    public static final String EXTRA_SELECTED_CATEGORY_TYPE = "extra_selected_category_type";

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private final List<TransactionCategory> categories = new ArrayList<>();
    private TransactionType selectedType = TransactionType.EXPENSE;
    private String selectedCategoryId;
    private String searchQuery = "";

    private MaterialButtonToggleGroup toggleGroup;
    private MaterialButton btnExpense;
    private MaterialButton btnIncome;
    private LinearLayout listContainer;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_picker);
        bindViews();
        applyIntent();
        setupToolbar();
        setupToggle();
        setupSearch();
        setupSession();
    }

    private void bindViews() {
        toggleGroup = findViewById(R.id.toggleCategoryPickerType);
        btnExpense = findViewById(R.id.btnCategoryPickerExpense);
        btnIncome = findViewById(R.id.btnCategoryPickerIncome);
        listContainer = findViewById(R.id.layoutCategoryPickerContainer);
        tvEmpty = findViewById(R.id.tvCategoryPickerEmpty);
    }

    private void applyIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String typeRaw = intent.getStringExtra(EXTRA_TYPE);
        if ("INCOME".equalsIgnoreCase(typeRaw)) {
            selectedType = TransactionType.INCOME;
        } else {
            selectedType = TransactionType.EXPENSE;
        }
        selectedCategoryId = intent.getStringExtra(EXTRA_SELECTED_CATEGORY_ID);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarCategoryPicker);
        toolbar.setTitle(R.string.app_title_pick_category);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(this::onToolbarMenuClick);
    }

    private boolean onToolbarMenuClick(MenuItem item) {
        if (item.getItemId() == R.id.actionEditCategories) {
            openCategoryEditor();
            return true;
        }
        return false;
    }

    private void openCategoryEditor() {
        Intent intent = new Intent(this, CategoryActivity.class);
        intent.putExtra(CategoryActivity.EXTRA_INITIAL_TYPE, selectedType.name());
        startActivity(intent);
    }

    private void setupToggle() {
        if (selectedType == TransactionType.INCOME) {
            toggleGroup.check(R.id.btnCategoryPickerIncome);
        } else {
            toggleGroup.check(R.id.btnCategoryPickerExpense);
        }
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            selectedType = checkedId == R.id.btnCategoryPickerIncome ? TransactionType.INCOME : TransactionType.EXPENSE;
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
        TextInputEditText etSearch = findViewById(R.id.etCategoryPickerSearch);
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
            addCategoryEntry(category, false, category.getName());
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
            addCategoryEntry(parent, false, getString(R.string.label_category_group_parent));
            count++;
            if (children != null) {
                for (TransactionCategory child : children) {
                    if (!CategoryUiHelper.matchesSearch(child, searchQuery)) {
                        continue;
                    }
                    addCategoryEntry(child, true, parent.getName());
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
            String subtitle = child.getParentName() == null || child.getParentName().trim().isEmpty()
                ? getString(R.string.label_category_group_other)
                : child.getParentName();
            addCategoryEntry(child, true, subtitle);
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

    private void addCategoryEntry(TransactionCategory category, boolean child, String subtitle) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_category_entry, listContainer, false);
        View layoutIcon = view.findViewById(R.id.layoutCategoryEntryIcon);
        ImageView ivIcon = view.findViewById(R.id.ivCategoryEntryIcon);
        TextView tvTitle = view.findViewById(R.id.tvCategoryEntryTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvCategoryEntrySubtitle);
        ImageButton btnAction = view.findViewById(R.id.btnCategoryEntryAction);

        layoutIcon.getBackground().setTint(ContextCompat.getColor(this, CategoryUiHelper.iconBgForCategory(category)));
        boolean loadedFromAssets = CategoryAssetIconLoader.applyCategoryIcon(
            ivIcon,
            category,
            CategoryUiHelper.iconResForCategory(category)
        );
        ivIcon.setImageTintList(loadedFromAssets
            ? null
            : ContextCompat.getColorStateList(this, CategoryUiHelper.iconTintForCategory(category)));
        tvTitle.setText(category.getName());
        tvSubtitle.setVisibility(View.VISIBLE);
        tvSubtitle.setText(subtitle);
        btnAction.setVisibility(View.GONE);

        if (child) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMarginStart(dp(16));
            view.setLayoutParams(params);
        }
        if (category.getId().equals(selectedCategoryId)) {
            view.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.chip_bg));
        }
        view.setOnClickListener(v -> finishWithCategory(category));
        listContainer.addView(view);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void finishWithCategory(TransactionCategory category) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_SELECTED_CATEGORY_ID, category.getId());
        intent.putExtra(EXTRA_SELECTED_CATEGORY_NAME, category.getName());
        intent.putExtra(EXTRA_SELECTED_CATEGORY_TYPE, category.getType().name());
        setResult(RESULT_OK, intent);
        finish();
    }
}
