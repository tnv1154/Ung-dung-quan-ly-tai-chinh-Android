package com.example.myapplication.xmlui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
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
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceParsersKt;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ExportWalletPickerActivity extends AppCompatActivity {
    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private final List<Wallet> wallets = new ArrayList<>();
    private final Set<String> selectedWalletIds = new LinkedHashSet<>();
    private String searchQuery = "";

    private LinearLayout containerWalletRows;
    private TextView tvSelectedCount;
    private MaterialButton btnConfirm;
    private ImageView ivSelectAllState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_wallet_picker);
        readIntentData();
        bindViews();
        setupToolbar();
        setupSearch();
        setupActions();
        setupSession();
    }

    private void readIntentData() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        ArrayList<String> selected = intent.getStringArrayListExtra(ExportIntentKeys.EXTRA_EXPORT_SELECTED_WALLET_IDS);
        if (selected != null) {
            selectedWalletIds.addAll(selected);
        }
    }

    private void bindViews() {
        containerWalletRows = findViewById(R.id.layoutExportWalletRows);
        tvSelectedCount = findViewById(R.id.tvExportWalletSelectedCount);
        btnConfirm = findViewById(R.id.btnExportWalletConfirm);
        ivSelectAllState = findViewById(R.id.ivExportWalletSelectAllState);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarExportWallet);
        toolbar.setTitle(R.string.export_wallet_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSearch() {
        TextInputEditText etSearch = findViewById(R.id.etExportWalletSearch);
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
                renderWalletRows();
            }
        });
    }

    private void setupActions() {
        findViewById(R.id.rowExportWalletSelectAll).setOnClickListener(v -> toggleSelectAll());
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
        wallets.clear();
        wallets.addAll(state.getWallets());
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        }
        Set<String> validIds = new LinkedHashSet<>();
        for (Wallet wallet : wallets) {
            validIds.add(wallet.getId());
        }
        selectedWalletIds.retainAll(validIds);
        if (selectedWalletIds.isEmpty()) {
            selectedWalletIds.addAll(validIds);
        }
        renderWalletRows();
    }

    private void renderWalletRows() {
        containerWalletRows.removeAllViews();
        List<Wallet> filtered = filterWallets();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Wallet wallet : filtered) {
            View row = inflater.inflate(R.layout.item_export_wallet_selection, containerWalletRows, false);
            FrameLayout iconContainer = row.findViewById(R.id.layoutExportWalletIcon);
            ImageView ivIcon = row.findViewById(R.id.ivExportWalletIcon);
            TextView tvName = row.findViewById(R.id.tvExportWalletName);
            TextView tvBalance = row.findViewById(R.id.tvExportWalletBalance);
            ImageView ivCheck = row.findViewById(R.id.ivExportWalletChecked);

            iconContainer.setBackgroundTintList(
                ColorStateList.valueOf(getColor(WalletUiMapper.iconBackgroundColor(wallet.getAccountType())))
            );
            ivIcon.setImageResource(WalletUiMapper.iconResForKey(wallet.getIconKey(), wallet.getAccountType()));
            ivIcon.setImageTintList(
                ColorStateList.valueOf(getColor(WalletUiMapper.iconTintColor(wallet.getAccountType())))
            );
            tvName.setText(wallet.getName());
            tvBalance.setText(formatBalance(wallet));
            tvBalance.setTextColor(getColor(wallet.getBalance() < 0.0 ? R.color.error_red : R.color.text_secondary));

            boolean selected = selectedWalletIds.contains(wallet.getId());
            styleCheckIcon(ivCheck, selected);
            row.setOnClickListener(v -> {
                toggleWallet(wallet.getId());
                styleCheckIcon(ivCheck, selectedWalletIds.contains(wallet.getId()));
                updateSelectionSummary();
            });

            containerWalletRows.addView(row);
        }
        updateSelectionSummary();
    }

    private List<Wallet> filterWallets() {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return new ArrayList<>(wallets);
        }
        String keyword = FinanceParsersKt.normalizeToken(searchQuery);
        List<Wallet> filtered = new ArrayList<>();
        for (Wallet wallet : wallets) {
            String nameToken = FinanceParsersKt.normalizeToken(wallet.getName());
            if (nameToken.contains(keyword)) {
                filtered.add(wallet);
            }
        }
        return filtered;
    }

    private void toggleWallet(String walletId) {
        if (walletId == null || walletId.trim().isEmpty()) {
            return;
        }
        if (selectedWalletIds.contains(walletId)) {
            selectedWalletIds.remove(walletId);
        } else {
            selectedWalletIds.add(walletId);
        }
    }

    private void toggleSelectAll() {
        if (wallets.isEmpty()) {
            return;
        }
        if (selectedWalletIds.size() >= wallets.size()) {
            selectedWalletIds.clear();
        } else {
            selectedWalletIds.clear();
            for (Wallet wallet : wallets) {
                selectedWalletIds.add(wallet.getId());
            }
        }
        renderWalletRows();
    }

    private void updateSelectionSummary() {
        int selectedCount = selectedWalletIds.size();
        tvSelectedCount.setText(getString(R.string.export_wallet_selected_count, selectedCount));
        btnConfirm.setText(getString(R.string.export_wallet_confirm_with_count, selectedCount));
        boolean allSelected = !wallets.isEmpty() && selectedCount >= wallets.size();
        styleCheckIcon(ivSelectAllState, allSelected);
    }

    private void submitSelection() {
        if (selectedWalletIds.isEmpty()) {
            Toast.makeText(this, R.string.error_export_wallet_required, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putStringArrayListExtra(
            ExportIntentKeys.EXTRA_EXPORT_SELECTED_WALLET_IDS,
            new ArrayList<>(selectedWalletIds)
        );
        setResult(RESULT_OK, result);
        finish();
    }

    private void styleCheckIcon(ImageView view, boolean selected) {
        if (selected) {
            view.setImageResource(R.drawable.ic_action_check);
            view.setImageTintList(ColorStateList.valueOf(getColor(android.R.color.white)));
            view.setBackgroundResource(R.drawable.bg_export_check_selected);
        } else {
            view.setImageDrawable(null);
            view.setBackgroundResource(R.drawable.bg_export_check_unselected);
        }
    }

    private String formatBalance(Wallet wallet) {
        String currencyCode = normalizeCurrency(wallet.getCurrency());
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(new Locale("vi", "VN"));
        if ("VND".equals(currencyCode)) {
            formatter.applyPattern("#,###");
            return formatter.format(wallet.getBalance()) + " đ";
        }
        if ("USD".equals(currencyCode)) {
            formatter.applyPattern("#,##0.00");
            return "$" + formatter.format(wallet.getBalance());
        }
        formatter.applyPattern("#,##0.00");
        return formatter.format(wallet.getBalance()) + " " + currencyCode;
    }

    private String normalizeCurrency(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "VND";
        }
        return value;
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

