package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.currency.CurrencyRateUtils;
import com.example.myapplication.xmlui.currency.ExchangeRateSnapshotLoader;
import com.example.myapplication.xmlui.AddTransactionActivity;
import com.example.myapplication.xmlui.AuthActivity;
import com.example.myapplication.xmlui.MoreActivity;
import com.example.myapplication.xmlui.OverviewActivity;
import com.example.myapplication.xmlui.ReportActivity;
import com.example.myapplication.xmlui.UiWallet;
import com.example.myapplication.xmlui.WalletRowAdapter;
import com.example.myapplication.xmlui.WalletDetailActivity;
import com.example.myapplication.xmlui.WalletUiMapper;
import com.example.myapplication.xmlui.notifications.BudgetAlertNotifier;
import com.example.myapplication.xmlui.notifications.ReminderScheduler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_WALLET_ID = "extra_wallet_id";
    public static final String EXTRA_WALLET_NAME = "extra_wallet_name";
    public static final String EXTRA_WALLET_BALANCE = "extra_wallet_balance";
    public static final String EXTRA_WALLET_TYPE = "extra_wallet_type";
    public static final String EXTRA_WALLET_ICON_KEY = "extra_wallet_icon_key";
    public static final String EXTRA_WALLET_CURRENCY = "extra_wallet_currency";
    public static final String EXTRA_WALLET_NOTE = "extra_wallet_note";
    public static final String EXTRA_WALLET_INCLUDE_IN_REPORT = "extra_wallet_include_in_report";
    public static final String EXTRA_WALLET_PROVIDER_NAME = "extra_wallet_provider_name";
    public static final String EXTRA_WALLET_LOCKED = "extra_wallet_locked";
    public static final String EXTRA_WALLET_EDIT_MODE = "extra_wallet_edit_mode";

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;

    private TextView tvTotalAssets;
    private TextView tvEmptyState;
    private TextView tvCashHeader;
    private TextView tvBankHeader;
    private TextView tvEwalletHeader;
    private TextView tvOtherHeader;
    private View cardCashGroup;
    private View cardBankGroup;
    private View cardEwalletGroup;
    private View cardOtherGroup;
    private RecyclerView rvCash;
    private RecyclerView rvBank;
    private RecyclerView rvEwallet;
    private RecyclerView rvOther;
    private WalletRowAdapter cashAdapter;
    private WalletRowAdapter bankAdapter;
    private WalletRowAdapter ewalletAdapter;
    private WalletRowAdapter otherAdapter;
    private BottomNavigationView bottomNavigationView;
    private ImageButton btnAddWalletFloating;
    private FinanceUiState latestState;
    private ExchangeRateSnapshot latestRateSnapshot;

    private String observedUserId;
    private final ExecutorService rateExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupToolbar();
        setupWalletLists();
        setupBottomNavigation();
        setupSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (financeViewModel != null) {
            financeViewModel.refreshRealtimeSync();
        }
    }

    private void bindViews() {
        tvTotalAssets = findViewById(R.id.tvTotalAssets);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvCashHeader = findViewById(R.id.tvCashHeader);
        tvBankHeader = findViewById(R.id.tvBankHeader);
        tvEwalletHeader = findViewById(R.id.tvEwalletHeader);
        tvOtherHeader = findViewById(R.id.tvOtherHeader);
        cardCashGroup = findViewById(R.id.cardCashGroup);
        cardBankGroup = findViewById(R.id.cardBankGroup);
        cardEwalletGroup = findViewById(R.id.cardEwalletGroup);
        cardOtherGroup = findViewById(R.id.cardOtherGroup);
        rvCash = findViewById(R.id.rvCashWallets);
        rvBank = findViewById(R.id.rvBankWallets);
        rvEwallet = findViewById(R.id.rvEwalletWallets);
        rvOther = findViewById(R.id.rvOtherWallets);
        bottomNavigationView = findViewById(R.id.bottomNavigation);
        btnAddWalletFloating = findViewById(R.id.btnAddWalletFloating);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitleMarginStart(0);
        toolbar.setTitle(R.string.app_title_accounts);
        toolbar.setTitleTextColor(getColor(R.color.text_primary));
    }

    private void setupWalletLists() {
        rvCash.setLayoutManager(new LinearLayoutManager(this));
        rvBank.setLayoutManager(new LinearLayoutManager(this));
        rvEwallet.setLayoutManager(new LinearLayoutManager(this));
        rvOther.setLayoutManager(new LinearLayoutManager(this));

        cashAdapter = new WalletRowAdapter(this::showWalletOptions);
        bankAdapter = new WalletRowAdapter(this::showWalletOptions);
        ewalletAdapter = new WalletRowAdapter(this::showWalletOptions);
        otherAdapter = new WalletRowAdapter(this::showWalletOptions);

        rvCash.setAdapter(cashAdapter);
        rvBank.setAdapter(bankAdapter);
        rvEwallet.setAdapter(ewalletAdapter);
        rvOther.setAdapter(otherAdapter);
        rvCash.setItemAnimator(null);
        rvBank.setItemAnimator(null);
        rvEwallet.setItemAnimator(null);
        rvOther.setItemAnimator(null);
        btnAddWalletFloating.setOnClickListener(v -> startActivity(new Intent(this, WalletDetailActivity.class)));
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setSelectedItemId(R.id.nav_accounts);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_accounts) {
                return true;
            }
            if (id == R.id.nav_overview) {
                startActivity(new Intent(this, OverviewActivity.class));
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
            bottomNavigationView.setSelectedItemId(R.id.nav_accounts);
            return false;
        });
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
        ReminderScheduler.syncFromSettings(this, state.getSettings());
        BudgetAlertNotifier.maybeNotifyExceeded(this, state);
        renderWalletGroups(state, latestRateSnapshot);
        loadLatestRateSnapshot();
    }

    private void loadLatestRateSnapshot() {
        String userId = observedUserId;
        if (userId == null || userId.isBlank()) {
            return;
        }
        rateExecutor.submit(() -> {
            ExchangeRateSnapshot snapshot = null;
            FirestoreFinanceRepository repository = new FirestoreFinanceRepository();
            try {
                snapshot = ExchangeRateSnapshotLoader.loadWithFallback(repository, userId);
            } catch (Exception ignored) {
            }
            ExchangeRateSnapshot finalSnapshot = snapshot;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalSnapshot != null) {
                    latestRateSnapshot = finalSnapshot;
                    if (latestState != null) {
                        renderWalletGroups(latestState, latestRateSnapshot);
                    }
                }
            });
        });
    }

    private void renderWalletGroups(FinanceUiState state, ExchangeRateSnapshot rateSnapshot) {
        List<UiWallet> cash = new ArrayList<>();
        List<UiWallet> bank = new ArrayList<>();
        List<UiWallet> ewallet = new ArrayList<>();
        List<UiWallet> other = new ArrayList<>();
        double totalAssets = 0.0;

        for (Wallet wallet : state.getWallets()) {
            String accountType = WalletUiMapper.normalizeAccountType(wallet.getAccountType());
            String walletCurrency = CurrencyRateUtils.normalizeCurrency(wallet.getCurrency());
            Double convertedVnd = CurrencyRateUtils.convert(
                wallet.getBalance(),
                walletCurrency,
                "VND",
                rateSnapshot
            );
            double totalValue = convertedVnd == null ? 0.0 : convertedVnd;
            UiWallet uiWallet = new UiWallet(
                wallet.getId(),
                wallet.getName(),
                wallet.getBalance(),
                accountType,
                wallet.getIconKey(),
                walletCurrency,
                wallet.getNote(),
                wallet.getIncludeInReport(),
                wallet.getProviderName(),
                wallet.isLocked(),
                convertedVnd
            );
            totalAssets += totalValue;
            switch (accountType) {
                case "BANK":
                    bank.add(uiWallet);
                    break;
                case "EWALLET":
                    ewallet.add(uiWallet);
                    break;
                case "OTHER":
                    other.add(uiWallet);
                    break;
                default:
                    cash.add(uiWallet);
                    break;
            }
        }

        cashAdapter.submit(cash);
        bankAdapter.submit(bank);
        ewalletAdapter.submit(ewallet);
        otherAdapter.submit(other);

        toggleGroupVisibility(cash, tvCashHeader, cardCashGroup);
        toggleGroupVisibility(bank, tvBankHeader, cardBankGroup);
        toggleGroupVisibility(ewallet, tvEwalletHeader, cardEwalletGroup);
        toggleGroupVisibility(other, tvOtherHeader, cardOtherGroup);

        boolean noWallets = state.getWallets().isEmpty();
        tvEmptyState.setVisibility(noWallets ? View.VISIBLE : View.GONE);
        tvTotalAssets.setText(formatMoney(totalAssets));
        tvTotalAssets.setTextColor(getColor(totalAssets < 0.0 ? R.color.error_red : R.color.blue_primary));
    }

    @Override
    protected void onDestroy() {
        rateExecutor.shutdownNow();
        super.onDestroy();
    }

    private void toggleGroupVisibility(List<UiWallet> wallets, View header, View listView) {
        int visibility = wallets.isEmpty() ? View.GONE : View.VISIBLE;
        header.setVisibility(visibility);
        listView.setVisibility(visibility);
    }

    private void showSignOutDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_sign_out_title)
            .setMessage(R.string.dialog_sign_out_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_sign_out, (dialog, which) -> sessionViewModel.signOut())
            .show();
    }

    private void showWalletOptions(UiWallet wallet) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        ViewGroup root = findViewById(android.R.id.content);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_wallet_options, root, false);
        dialog.setContentView(sheetView);

        TextView tvWalletName = sheetView.findViewById(R.id.tvWalletOptionsWalletName);
        View btnTransfer = sheetView.findViewById(R.id.btnWalletOptionTransfer);
        View btnEdit = sheetView.findViewById(R.id.btnWalletOptionEdit);
        View btnLock = sheetView.findViewById(R.id.btnWalletOptionLock);
        View btnDelete = sheetView.findViewById(R.id.btnWalletOptionDelete);
        TextView tvLockTitle = sheetView.findViewById(R.id.tvWalletOptionLockTitle);
        TextView tvLockedHint = sheetView.findViewById(R.id.tvWalletOptionLockedHint);
        ImageView ivLock = sheetView.findViewById(R.id.ivWalletOptionLock);

        tvWalletName.setText(wallet.getName());
        tvLockTitle.setText(wallet.isLocked() ? R.string.wallet_option_toggle_unlock : R.string.wallet_option_toggle_lock);
        ivLock.setImageResource(wallet.isLocked() ? R.drawable.ic_wallet_unlock : R.drawable.ic_wallet_lock);
        tvLockedHint.setVisibility(wallet.isLocked() ? View.VISIBLE : View.GONE);
        btnTransfer.setEnabled(!wallet.isLocked());
        btnTransfer.setAlpha(wallet.isLocked() ? 0.45f : 1f);
        btnTransfer.setOnClickListener(v -> {
            if (wallet.isLocked()) {
                Toast.makeText(this, R.string.wallet_locked_message, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            startTransferFlow(wallet);
        });
        btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            startEditWalletFlow(wallet);
        });
        btnLock.setOnClickListener(v -> {
            dialog.dismiss();
            toggleWalletLock(wallet);
        });
        btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteWallet(wallet);
        });
        dialog.show();
    }

    private void startTransferFlow(UiWallet wallet) {
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_TRANSFER);
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_SOURCE_WALLET_ID, wallet.getId());
        startActivity(intent);
    }

    private void startEditWalletFlow(UiWallet wallet) {
        Intent intent = new Intent(this, WalletDetailActivity.class);
        intent.putExtra(EXTRA_WALLET_EDIT_MODE, true);
        intent.putExtra(EXTRA_WALLET_ID, wallet.getId());
        intent.putExtra(EXTRA_WALLET_NAME, wallet.getName());
        intent.putExtra(EXTRA_WALLET_BALANCE, wallet.getBalance());
        intent.putExtra(EXTRA_WALLET_TYPE, wallet.getAccountType());
        intent.putExtra(EXTRA_WALLET_ICON_KEY, wallet.getIconKey());
        intent.putExtra(EXTRA_WALLET_CURRENCY, wallet.getCurrency());
        intent.putExtra(EXTRA_WALLET_NOTE, wallet.getNote());
        intent.putExtra(EXTRA_WALLET_INCLUDE_IN_REPORT, wallet.isIncludeInReport());
        intent.putExtra(EXTRA_WALLET_PROVIDER_NAME, wallet.getProviderName());
        intent.putExtra(EXTRA_WALLET_LOCKED, wallet.isLocked());
        startActivity(intent);
    }

    private void confirmDeleteWallet(UiWallet wallet) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_wallet_title)
            .setMessage(getString(R.string.dialog_delete_wallet_message, wallet.getName()))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                if (financeViewModel == null) {
                    return;
                }
                if (latestState != null) {
                    boolean hasTx = false;
                    for (com.example.myapplication.finance.model.FinanceTransaction tx : latestState.getTransactions()) {
                        if (wallet.getId().equals(tx.getWalletId()) || wallet.getId().equals(tx.getToWalletId())) {
                            hasTx = true;
                            break;
                        }
                    }
                    if (hasTx) {
                        Toast.makeText(this, R.string.error_wallet_has_transactions, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                financeViewModel.deleteWallet(wallet.getId());
            })
            .show();
    }

    private void toggleWalletLock(UiWallet wallet) {
        if (financeViewModel == null) {
            return;
        }
        financeViewModel.updateWallet(
            wallet.getId(),
            wallet.getName(),
            wallet.getAccountType(),
            wallet.getIconKey(),
            wallet.getCurrency(),
            wallet.getNote(),
            wallet.isIncludeInReport(),
            wallet.getProviderName(),
            !wallet.isLocked()
        );
        Toast.makeText(
            this,
            wallet.isLocked() ? R.string.wallet_unlocked_message : R.string.wallet_locked_message,
            Toast.LENGTH_SHORT
        ).show();
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String formatMoney(double value) {
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(new Locale("vi", "VN"));
        formatter.applyPattern("#,###");
        return formatter.format(value) + " ₫";
    }
}
