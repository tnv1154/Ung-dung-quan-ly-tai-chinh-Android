package com.example.myapplication.xmlui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.currency.CurrencyRateUtils;
import com.example.myapplication.xmlui.currency.ExchangeRateSnapshotLoader;
import com.google.android.material.appbar.MaterialToolbar;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportDrilldownActivity extends AppCompatActivity {

    private static final String EXTRA_TITLE = "extra_title";
    private static final String EXTRA_START_EPOCH_SECONDS = "extra_start_epoch_seconds";
    private static final String EXTRA_END_EPOCH_SECONDS = "extra_end_epoch_seconds";
    private static final String EXTRA_CATEGORY = "extra_category";
    private static final String EXTRA_WALLET_IDS = "extra_wallet_ids";
    private static final String EXTRA_TRANSACTION_TYPES = "extra_transaction_types";
    private static final String EXTRA_TYPE_FILTER_PROVIDED = "extra_type_filter_provided";

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState;
    private ExchangeRateSnapshot latestRateSnapshot;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();

    private MaterialToolbar toolbar;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvEmpty;
    private TransactionRowAdapter transactionAdapter;

    private long startEpochSeconds;
    private long endEpochSeconds;
    private String categoryFilter;
    private Set<String> walletFilters = Collections.emptySet();
    private Set<TransactionType> typeFilters = Collections.emptySet();
    private boolean hasTypeFilter;

    public static Intent createIntent(
        @NonNull Context context,
        @NonNull String title,
        @NonNull ZonedDateTime start,
        @NonNull ZonedDateTime end,
        String category,
        @NonNull Set<String> walletIds,
        @NonNull Set<TransactionType> types
    ) {
        Intent intent = new Intent(context, ReportDrilldownActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_START_EPOCH_SECONDS, start.toEpochSecond());
        intent.putExtra(EXTRA_END_EPOCH_SECONDS, end.toEpochSecond());
        intent.putExtra(EXTRA_CATEGORY, category);
        intent.putStringArrayListExtra(EXTRA_WALLET_IDS, new ArrayList<>(walletIds));

        ArrayList<String> typeNames = new ArrayList<>();
        for (TransactionType type : types) {
            typeNames.add(type.name());
        }
        intent.putStringArrayListExtra(EXTRA_TRANSACTION_TYPES, typeNames);
        intent.putExtra(EXTRA_TYPE_FILTER_PROVIDED, true);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_drilldown);
        bindViews();
        readExtras();
        setupToolbar();
        setupList();
        setupSession();
    }

    @Override
    protected void onDestroy() {
        rateExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarReportDrilldown);
        tvIncome = findViewById(R.id.tvDrilldownIncome);
        tvExpense = findViewById(R.id.tvDrilldownExpense);
        tvEmpty = findViewById(R.id.tvReportDrilldownEmpty);
    }

    private void readExtras() {
        Intent intent = getIntent();
        startEpochSeconds = intent.getLongExtra(EXTRA_START_EPOCH_SECONDS, 0L);
        endEpochSeconds = intent.getLongExtra(EXTRA_END_EPOCH_SECONDS, Long.MAX_VALUE);
        categoryFilter = normalize(intent.getStringExtra(EXTRA_CATEGORY));

        ArrayList<String> walletIds = intent.getStringArrayListExtra(EXTRA_WALLET_IDS);
        if (walletIds != null && !walletIds.isEmpty()) {
            walletFilters = new LinkedHashSet<>(walletIds);
        }

        ArrayList<String> typeNames = intent.getStringArrayListExtra(EXTRA_TRANSACTION_TYPES);
        hasTypeFilter = intent.getBooleanExtra(EXTRA_TYPE_FILTER_PROVIDED, false);
        if (hasTypeFilter && typeNames != null && !typeNames.isEmpty()) {
            Set<TransactionType> parsedTypes = new LinkedHashSet<>();
            for (String raw : typeNames) {
                try {
                    parsedTypes.add(TransactionType.valueOf(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
            typeFilters = parsedTypes;
        }
    }

    private void setupToolbar() {
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.trim().isEmpty()) {
            title = getString(R.string.report_drilldown_default_title);
        }
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupList() {
        RecyclerView recyclerView = findViewById(R.id.rvReportDrilldownTransactions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionRowAdapter();
        recyclerView.setAdapter(transactionAdapter);
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
        latestState = state;
        loadLatestRateSnapshot();
        Map<String, String> walletNameById = new HashMap<>();
        Map<String, String> walletCurrencyById = new HashMap<>();
        Map<String, String> walletIconById = new HashMap<>();
        Map<String, String> walletTypeById = new HashMap<>();
        for (Wallet wallet : state.getWallets()) {
            walletNameById.put(wallet.getId(), wallet.getName());
            walletCurrencyById.put(wallet.getId(), CurrencyRateUtils.normalizeCurrency(wallet.getCurrency()));
            walletIconById.put(wallet.getId(), wallet.getIconKey());
            walletTypeById.put(wallet.getId(), wallet.getAccountType());
        }
        Map<String, TransactionCategory> categoryByKey = new HashMap<>();
        for (TransactionCategory category : state.getCategories()) {
            categoryByKey.put(categoryKey(category.getType(), category.getName()), category);
        }

        List<UiTransaction> uiTransactions = new ArrayList<>();
        double totalIncome = 0.0;
        double totalExpense = 0.0;
        for (FinanceTransaction tx : state.getTransactions()) {
            if (!matchesRange(tx) || !matchesCategory(tx) || !matchesWallet(tx) || !matchesType(tx)) {
                continue;
            }
            String walletName = walletNameById.getOrDefault(tx.getWalletId(), getString(R.string.label_source_wallet));
            String walletIcon = walletIconById.getOrDefault(tx.getWalletId(), "cash");
            String walletType = walletTypeById.getOrDefault(tx.getWalletId(), "CASH");
            TransactionCategory category = categoryByKey.get(categoryKey(tx.getType(), tx.getCategory()));
            String categoryIcon = category == null ? "" : category.getIconKey();
            double amountVnd = amountInVnd(tx, walletCurrencyById);
            uiTransactions.add(new UiTransaction(
                tx.getId(),
                walletName,
                tx.getCategory(),
                tx.getNote(),
                tx.getType().name(),
                amountVnd,
                tx.getCreatedAt(),
                categoryIcon,
                walletIcon,
                walletType
            ));
            if (tx.getType() == TransactionType.INCOME) {
                totalIncome += amountVnd;
            } else {
                totalExpense += amountVnd;
            }
        }

        uiTransactions.sort((left, right) ->
            Long.compare(right.getCreatedAt().getSeconds(), left.getCreatedAt().getSeconds())
        );
        transactionAdapter.submit(uiTransactions);
        tvIncome.setText(UiFormatters.money(totalIncome));
        tvExpense.setText(UiFormatters.money(totalExpense));
        tvEmpty.setVisibility(uiTransactions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesRange(FinanceTransaction tx) {
        ZonedDateTime date = Instant.ofEpochSecond(tx.getCreatedAt().getSeconds(), tx.getCreatedAt().getNanoseconds())
            .atZone(ZoneId.systemDefault());
        long epochSeconds = date.toEpochSecond();
        return epochSeconds >= startEpochSeconds && epochSeconds < endEpochSeconds;
    }

    private boolean matchesCategory(FinanceTransaction tx) {
        if (categoryFilter.isEmpty()) {
            return true;
        }
        return normalize(tx.getCategory()).equals(categoryFilter);
    }

    private boolean matchesWallet(FinanceTransaction tx) {
        return walletFilters.isEmpty() || walletFilters.contains(tx.getWalletId());
    }

    private boolean matchesType(FinanceTransaction tx) {
        if (!hasTypeFilter) {
            return true;
        }
        return typeFilters.contains(tx.getType());
    }

    private double amountInVnd(FinanceTransaction tx, Map<String, String> walletCurrencyById) {
        String sourceCurrency = tx.getSourceCurrency();
        String currency;
        if (sourceCurrency != null && !sourceCurrency.trim().isEmpty()) {
            currency = CurrencyRateUtils.normalizeCurrency(sourceCurrency);
        } else {
            currency = walletCurrencyById.get(tx.getWalletId());
            if (currency == null || currency.isBlank()) {
                currency = "VND";
            }
        }
        Double converted = CurrencyRateUtils.convert(tx.getAmount(), currency, "VND", latestRateSnapshot);
        return converted == null ? 0.0 : converted;
    }

    private void loadLatestRateSnapshot() {
        if (latestRateSnapshot != null) {
            return;
        }
        String userId = observedUserId;
        if (userId == null || userId.isBlank()) {
            return;
        }
        rateExecutor.submit(() -> {
            ExchangeRateSnapshot snapshot = null;
            try {
                snapshot = ExchangeRateSnapshotLoader.loadWithFallback(new FirestoreFinanceRepository(), userId);
            } catch (Exception ignored) {
            }
            ExchangeRateSnapshot finalSnapshot = snapshot;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalSnapshot == null) {
                    return;
                }
                latestRateSnapshot = finalSnapshot;
                if (latestState != null) {
                    renderFinanceState(latestState);
                }
            });
        });
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String categoryKey(TransactionType type, String categoryName) {
        return type.name() + "::" + CategoryUiHelper.normalize(categoryName);
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
