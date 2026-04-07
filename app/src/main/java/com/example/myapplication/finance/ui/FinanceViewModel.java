package com.example.myapplication.finance.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.CsvImportRow;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class FinanceViewModel extends ViewModel {
    private final FirestoreFinanceRepository repository;
    private final String userId;
    private final MutableLiveData<FinanceUiState> uiStateLiveData = new MutableLiveData<>(new FinanceUiState());
    private final AtomicReference<FinanceUiState> stateRef = new AtomicReference<>(new FinanceUiState());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Object loadLock = new Object();
    private volatile boolean requestedDefaultCategories = false;
    private Future<?> loadJob;

    public FinanceViewModel(FirestoreFinanceRepository repository, String userId) {
        this.repository = repository;
        this.userId = userId;
        FinanceUiState cached = FinanceUiStateMemoryCache.get(userId);
        if (cached != null) {
            FinanceUiState restored = new FinanceUiState(
                false,
                cached.getWallets(),
                cached.getTransactions(),
                cached.getBudgetLimits(),
                cached.getCategories(),
                cached.getSettings(),
                cached.getMonthlySummary(),
                null
            );
            stateRef.set(restored);
            uiStateLiveData.setValue(restored);
        }
        loadData(false);
    }

    public LiveData<FinanceUiState> getUiStateLiveData() {
        return uiStateLiveData;
    }

    public void refreshRealtimeSync() {
        loadData(true);
    }

    private void loadData(boolean forceReload) {
        synchronized (loadLock) {
            if (!forceReload && loadJob != null && !loadJob.isDone()) {
                return;
            }
            if (forceReload && loadJob != null && !loadJob.isDone()) {
                loadJob.cancel(true);
            }
            loadJob = ioExecutor.submit(() -> {
                FinanceUiState cacheState = buildSnapshot(Source.CACHE);
                publishState(cacheState);
                try {
                    FinanceUiState serverState = buildSnapshot(Source.SERVER);
                    publishState(serverState);
                } catch (Exception error) {
                    if (!hasAnyData(currentState())) {
                        postError("Không thể đồng bộ dữ liệu tài chính", error);
                    }
                }
            });
        }
    }

    private FinanceUiState buildSnapshot(Source source) {
        FinanceUiState current = currentState();
        List<Wallet> wallets = safeFetch(() -> repository.getWallets(userId, source), current.getWallets());
        List<FinanceTransaction> transactions = safeFetch(() -> repository.getTransactions(userId, source), current.getTransactions());
        List<com.example.myapplication.finance.model.BudgetLimit> budgetLimits =
            safeFetch(() -> repository.getBudgetLimits(userId, source), current.getBudgetLimits());
        List<com.example.myapplication.finance.model.TransactionCategory> categories =
            safeFetch(() -> repository.getCategories(userId, source), current.getCategories());
        com.example.myapplication.finance.model.UserSettings settings =
            safeFetch(() -> repository.getUserSettings(userId, source), current.getSettings());
        FinanceSummary summary = FinanceUiCalculatorsKt.calculateCurrentMonthSummary(transactions);

        return new FinanceUiState(
            false,
            wallets,
            transactions,
            budgetLimits,
            categories,
            settings,
            summary,
            null
        );
    }

    private void publishState(FinanceUiState state) {
        postUiState(state, true);
    }

    private boolean hasAnyData(FinanceUiState state) {
        return !state.getWallets().isEmpty()
            || !state.getTransactions().isEmpty()
            || !state.getBudgetLimits().isEmpty()
            || !state.getCategories().isEmpty();
    }

    private void refreshFromCacheOnly() {
        ioExecutor.submit(() -> {
            try {
                publishState(buildSnapshot(Source.CACHE));
            } catch (Exception ignored) {
            }
        });
    }

    public void addWallet(
        String name,
        double openingBalance,
        String accountType,
        String iconKey,
        String currency,
        String note,
        boolean includeInReport,
        String providerName,
        boolean isLocked
    ) {
        ioExecutor.submit(() -> {
            try {
                Wallet wallet = repository.addWallet(
                    userId,
                    name,
                    openingBalance,
                    accountType,
                    iconKey,
                    currency,
                    note,
                    includeInReport,
                    providerName,
                    isLocked
                );
                FinanceUiState state = currentState();
                List<Wallet> mergedWallets = new ArrayList<>(state.getWallets());
                boolean exists = false;
                for (Wallet item : mergedWallets) {
                    if (item.getId().equals(wallet.getId())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    mergedWallets.add(wallet);
                }

                FinanceUiState nextState = new FinanceUiState(
                    false,
                    mergedWallets,
                    state.getTransactions(),
                    state.getBudgetLimits(),
                    state.getCategories(),
                    state.getSettings(),
                    state.getMonthlySummary(),
                    null
                );
                postUiState(nextState, true);
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể tạo ví", error);
            }
        });
    }

    public void deleteWallet(String walletId) {
        ioExecutor.submit(() -> {
            try {
                repository.deleteWallet(userId, walletId);
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể xóa ví", error);
            }
        });
    }

    public void updateWalletName(String walletId, String newName) {
        ioExecutor.submit(() -> {
            try {
                repository.updateWalletName(userId, walletId, newName);
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể cập nhật tên ví", error);
            }
        });
    }

    public void updateWallet(
        String walletId,
        String name,
        String accountType,
        String iconKey,
        String currency,
        String note,
        boolean includeInReport,
        String providerName,
        boolean isLocked
    ) {
        ioExecutor.submit(() -> {
            try {
                repository.updateWallet(
                    userId,
                    walletId,
                    name,
                    accountType,
                    iconKey,
                    currency,
                    note,
                    includeInReport,
                    providerName,
                    isLocked
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể cập nhật ví", error);
            }
        });
    }

    public void adjustWalletBalance(String walletId, double newBalance, String note) {
        adjustWalletBalance(walletId, newBalance, note, null);
    }

    public void adjustWalletBalance(String walletId, double newBalance, String note, Timestamp transactionCreatedAt) {
        ioExecutor.submit(() -> {
            try {
                repository.adjustWalletBalance(userId, walletId, newBalance, note, transactionCreatedAt);
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể điều chỉnh số dư", error);
            }
        });
    }

    public void addTransaction(
        String walletId,
        TransactionType type,
        double amount,
        String category,
        String note,
        String toWalletId
    ) {
        addTransaction(walletId, type, amount, category, note, toWalletId, null);
    }

    public void addTransaction(
        String walletId,
        TransactionType type,
        double amount,
        String category,
        String note,
        String toWalletId,
        Timestamp transactionCreatedAt
    ) {
        ioExecutor.submit(() -> {
            try {
                repository.addTransaction(
                    userId,
                    walletId,
                    type,
                    amount,
                    category,
                    note,
                    toWalletId,
                    transactionCreatedAt
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể thêm giao dịch", error);
            }
        });
    }

    public void deleteTransaction(String transactionId) {
        ioExecutor.submit(() -> {
            try {
                repository.deleteTransaction(userId, transactionId);
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể xóa giao dịch", error);
            }
        });
    }

    public void importCsvRows(List<CsvImportRow> rows) {
        ioExecutor.submit(() -> {
            FinanceUiState state = currentState();
            Map<String, Wallet> walletsByName = new HashMap<>();
            for (Wallet wallet : state.getWallets()) {
                walletsByName.put(wallet.getName().trim().toLowerCase(Locale.ROOT), wallet);
            }

            String fallbackWalletId = state.getWallets().isEmpty() ? null : state.getWallets().get(0).getId();
            if (fallbackWalletId == null) {
                FinanceUiState next = new FinanceUiState(
                    state.isLoading(),
                    state.getWallets(),
                    state.getTransactions(),
                    state.getBudgetLimits(),
                    state.getCategories(),
                    state.getSettings(),
                    state.getMonthlySummary(),
                    "Chưa có ví để nhập dữ liệu. Hãy tạo ví trước."
                );
                postUiState(next, false);
                return;
            }

            int successCount = 0;
            int skippedCount = 0;

            for (CsvImportRow row : rows) {
                String sourceWalletId = fallbackWalletId;
                if (row.getWalletName() != null) {
                    Wallet sourceWallet = walletsByName.get(row.getWalletName().trim().toLowerCase(Locale.ROOT));
                    if (sourceWallet != null) {
                        sourceWalletId = sourceWallet.getId();
                    }
                }

                String targetWalletId = null;
                if (row.getToWalletName() != null) {
                    Wallet targetWallet = walletsByName.get(row.getToWalletName().trim().toLowerCase(Locale.ROOT));
                    if (targetWallet != null) {
                        targetWalletId = targetWallet.getId();
                    }
                }

                if (row.getType() == TransactionType.TRANSFER && targetWalletId == null) {
                    skippedCount++;
                    continue;
                }

                try {
                    repository.addTransaction(
                        userId,
                        sourceWalletId,
                        row.getType(),
                        row.getAmount(),
                        row.getCategory(),
                        row.getNote(),
                        targetWalletId
                    );
                    successCount++;
                } catch (Exception ex) {
                    skippedCount++;
                }
            }

            FinanceUiState latest = currentState();
            FinanceUiState next = new FinanceUiState(
                latest.isLoading(),
                latest.getWallets(),
                latest.getTransactions(),
                latest.getBudgetLimits(),
                latest.getCategories(),
                latest.getSettings(),
                latest.getMonthlySummary(),
                "Nhập CSV xong: " + successCount + " thành công, " + skippedCount + " bỏ qua."
            );
            postUiState(next, false);
            refreshFromCacheOnly();
        });
    }

    public void addBudgetLimit(String category, double limitAmount) {
        ioExecutor.submit(() -> {
            try {
                repository.addBudgetLimit(userId, category, limitAmount);
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể thêm hạn mức", error);
            }
        });
    }

    public void deleteBudgetLimit(String budgetId) {
        ioExecutor.submit(() -> {
            try {
                repository.deleteBudgetLimit(userId, budgetId);
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể xóa hạn mức", error);
            }
        });
    }

    public void addCategory(String name, TransactionType type) {
        addCategory(name, type, "", "dot", 0);
    }

    public void addCategory(String name, TransactionType type, String parentName, String iconKey) {
        addCategory(name, type, parentName, iconKey, 0);
    }

    public void addCategory(
        String name,
        TransactionType type,
        String parentName,
        String iconKey,
        int sortOrder
    ) {
        ioExecutor.submit(() -> {
            try {
                repository.addCategory(
                    userId,
                    name,
                    type,
                    parentName,
                    iconKey,
                    sortOrder
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể thêm hạng mục", error);
            }
        });
    }

    public void ensureDefaultCategories() {
        if (requestedDefaultCategories) {
            return;
        }
        requestedDefaultCategories = true;
        ioExecutor.submit(() -> {
            try {
                repository.seedDefaultCategories(userId);
            } catch (Exception error) {
                requestedDefaultCategories = false;
                postError("Không thể tạo hạng mục mặc định", error);
            }
        });
    }

    public void deleteCategory(String categoryId) {
        ioExecutor.submit(() -> {
            try {
                repository.deleteCategory(userId, categoryId);
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể xóa hạng mục", error);
            }
        });
    }

    public void clearError() {
        FinanceUiState state = currentState();
        FinanceUiState next = new FinanceUiState(
            state.isLoading(),
            state.getWallets(),
            state.getTransactions(),
            state.getBudgetLimits(),
            state.getCategories(),
            state.getSettings(),
            state.getMonthlySummary(),
            null
        );
        postUiState(next, false);
    }

    public void updateSettings(
        String currency,
        boolean showBudgetWarnings,
        boolean compactNumberFormat,
        boolean reminderEnabled,
        String reminderFrequency,
        int reminderHour,
        int reminderMinute,
        List<Integer> reminderWeekdays
    ) {
        ioExecutor.submit(() -> {
            try {
                repository.updateUserSettings(
                    userId,
                    currency,
                    showBudgetWarnings,
                    compactNumberFormat,
                    reminderEnabled,
                    reminderFrequency,
                    reminderHour,
                    reminderMinute,
                    reminderWeekdays
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                postError("Không thể lưu cài đặt", error);
            }
        });
    }

    public String buildCsvExport(ExportPeriod period, Set<String> selectedWalletIds) {
        FinanceUiState state = currentState();
        Map<String, Wallet> walletMap = new HashMap<>();
        for (Wallet wallet : state.getWallets()) {
            walletMap.put(wallet.getId(), wallet);
        }

        Set<String> walletIds = selectedWalletIds == null ? new LinkedHashSet<>() : selectedWalletIds;
        List<FinanceTransaction> filtered = FinanceUiCalculatorsKt.filterTransactionsForExport(state.getTransactions(), period);
        List<FinanceTransaction> exportItems = new ArrayList<>();
        for (FinanceTransaction tx : filtered) {
            if (walletIds.isEmpty() || walletIds.contains(tx.getWalletId())) {
                exportItems.add(tx);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("id,type,amount,category,note,wallet,toWallet,createdAt");
        for (FinanceTransaction tx : exportItems) {
            String source = walletMap.containsKey(tx.getWalletId()) ? walletMap.get(tx.getWalletId()).getName() : "";
            String target = "";
            if (tx.getToWalletId() != null && walletMap.containsKey(tx.getToWalletId())) {
                target = walletMap.get(tx.getToWalletId()).getName();
            }
            String note = tx.getNote().replace(",", " ");
            String category = tx.getCategory().replace(",", " ");
            lines.add(
                tx.getId()
                    + ","
                    + tx.getType().name()
                    + ","
                    + tx.getAmount()
                    + ","
                    + category
                    + ","
                    + note
                    + ","
                    + source
                    + ","
                    + target
                    + ","
                    + tx.getCreatedAt().getSeconds()
            );
        }
        return String.join("\n", lines);
    }

    @Override
    protected void onCleared() {
        synchronized (loadLock) {
            if (loadJob != null && !loadJob.isDone()) {
                loadJob.cancel(true);
            }
        }
        ioExecutor.shutdownNow();
        super.onCleared();
    }

    private void postError(String fallbackMessage, Exception error) {
        FinanceUiState state = currentState();
        FinanceUiState next = new FinanceUiState(
            false,
            state.getWallets(),
            state.getTransactions(),
            state.getBudgetLimits(),
            state.getCategories(),
            state.getSettings(),
            state.getMonthlySummary(),
            messageOrDefault(error, fallbackMessage)
        );
        postUiState(next, false);
    }

    private void postUiState(FinanceUiState state, boolean updateCache) {
        stateRef.set(state);
        if (updateCache) {
            FinanceUiStateMemoryCache.put(userId, state);
        }
        uiStateLiveData.postValue(state);
    }

    private FinanceUiState currentState() {
        return stateRef.get();
    }

    private static String messageOrDefault(Exception error, String fallback) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return fallback;
        }
        return error.getMessage();
    }

    private <T> T safeFetch(ThrowingSupplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

