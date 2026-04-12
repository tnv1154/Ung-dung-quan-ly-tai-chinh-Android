package com.example.myapplication.finance.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.CsvImportRow;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.xmlui.currency.ExchangeRateSnapshotLoader;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
        refreshRealtimeSync(null);
    }

    public void refreshRealtimeSync(Consumer<String> onComplete) {
        loadData(true, onComplete);
    }

    public void pullFromServer() {
        pullFromServer(null);
    }

    public void pullFromServer(Consumer<String> onComplete) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                publishState(buildSnapshot(Source.SERVER));
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể lấy dữ liệu từ máy chủ");
                postError("Không thể lấy dữ liệu từ máy chủ", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
            }
        });
    }

    public void clearInMemoryCache() {
        FinanceUiStateMemoryCache.remove(userId);
    }

    private void loadData(boolean forceReload) {
        loadData(forceReload, null);
    }

    private void loadData(boolean forceReload, Consumer<String> onComplete) {
        synchronized (loadLock) {
            if (!forceReload && loadJob != null && !loadJob.isDone()) {
                return;
            }
            if (forceReload && loadJob != null && !loadJob.isDone()) {
                loadJob.cancel(true);
            }
            loadJob = ioExecutor.submit(() -> {
                String errorMessage = null;
                FinanceUiState cacheState = buildSnapshot(Source.CACHE);
                publishState(cacheState);
                try {
                    FinanceUiState serverState = buildSnapshot(Source.SERVER);
                    publishState(serverState);
                } catch (Exception error) {
                    errorMessage = messageOrDefault(error, "Không thể đồng bộ dữ liệu tài chính");
                    if (!hasAnyData(currentState())) {
                        postError("Không thể đồng bộ dữ liệu tài chính", error);
                    }
                }
                if (onComplete != null) {
                    onComplete.accept(errorMessage);
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
            boolean published = false;
            try {
                publishState(buildSnapshot(Source.CACHE));
                published = true;
            } catch (Exception ignored) {
            }
            try {
                publishState(buildSnapshot(Source.SERVER));
                published = true;
            } catch (Exception serverError) {
                if (!published) {
                    postError("Không thể làm mới dữ liệu", serverError);
                }
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
        deleteWallet(walletId, null);
    }

    public void deleteWallet(String walletId, Consumer<String> onComplete) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                repository.deleteWallet(userId, walletId);
                refreshFromCacheOnly();
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể xóa ví");
                postError("Không thể xóa ví", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
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
        updateWallet(
            walletId,
            name,
            accountType,
            iconKey,
            currency,
            note,
            includeInReport,
            providerName,
            isLocked,
            null
        );
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
        boolean isLocked,
        Consumer<String> onComplete
    ) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
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
                errorMessage = messageOrDefault(error, "Không thể cập nhật ví");
                postError("Không thể cập nhật ví", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
            }
        });
    }

    public void adjustWalletBalance(String walletId, double newBalance, String note) {
        adjustWalletBalance(walletId, newBalance, note, null, null);
    }

    public void adjustWalletBalance(String walletId, double newBalance, String note, Timestamp transactionCreatedAt) {
        adjustWalletBalance(walletId, newBalance, note, transactionCreatedAt, null);
    }

    public void adjustWalletBalance(
        String walletId,
        double newBalance,
        String note,
        Timestamp transactionCreatedAt,
        Consumer<String> onComplete
    ) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                repository.adjustWalletBalance(userId, walletId, newBalance, note, transactionCreatedAt);
                refreshFromCacheOnly();
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể điều chỉnh số dư");
                postError("Không thể điều chỉnh số dư", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
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
        addTransaction(walletId, type, amount, category, note, toWalletId, null, null);
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
        addTransaction(walletId, type, amount, category, note, toWalletId, transactionCreatedAt, null);
    }

    public void addTransaction(
        String walletId,
        TransactionType type,
        double amount,
        String category,
        String note,
        String toWalletId,
        Timestamp transactionCreatedAt,
        Consumer<String> onComplete
    ) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
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
                errorMessage = messageOrDefault(error, "Không thể thêm giao dịch");
                postError("Không thể thêm giao dịch", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
            }
        });
    }

    public void addTransferTransactionWithConversion(
        String sourceWalletId,
        String destinationWalletId,
        double sourceAmount,
        String category,
        String note,
        String sourceCurrency,
        String destinationCurrency,
        Timestamp transactionCreatedAt
    ) {
        addTransferTransactionWithConversion(
            sourceWalletId,
            destinationWalletId,
            sourceAmount,
            category,
            note,
            sourceCurrency,
            destinationCurrency,
            transactionCreatedAt,
            null
        );
    }

    public void addTransferTransactionWithConversion(
        String sourceWalletId,
        String destinationWalletId,
        double sourceAmount,
        String category,
        String note,
        String sourceCurrency,
        String destinationCurrency,
        Timestamp transactionCreatedAt,
        Consumer<String> onComplete
    ) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                String sourceCode = normalizeCurrencyCode(sourceCurrency);
                String destinationCode = normalizeCurrencyCode(destinationCurrency);
                double destinationAmount = sourceAmount;
                Double exchangeRate = null;
                Timestamp exchangeRateFetchedAt = null;

                if (!sourceCode.equals(destinationCode)) {
                    ExchangeRateSnapshot snapshot = ExchangeRateSnapshotLoader.loadWithFallback(repository, userId);
                    if (snapshot == null) {
                        throw new IllegalStateException("Chưa có dữ liệu tỷ giá. Vui lòng chờ đồng bộ.");
                    }
                    exchangeRate = snapshot.conversionRate(sourceCode, destinationCode);
                    if (exchangeRate == null || exchangeRate <= 0.0) {
                        throw new IllegalStateException("Chưa có tỷ giá cho cặp " + sourceCode + "/" + destinationCode);
                    }
                    destinationAmount = roundCurrencyAmount(sourceAmount * exchangeRate);
                    if (destinationAmount <= 0.0) {
                        throw new IllegalStateException("Không thể quy đổi số tiền chuyển khoản.");
                    }
                    exchangeRateFetchedAt = snapshot.getFetchedAt();
                }

                repository.addTransferTransaction(
                    userId,
                    sourceWalletId,
                    destinationWalletId,
                    sourceAmount,
                    destinationAmount,
                    category,
                    note,
                    sourceCode,
                    destinationCode,
                    exchangeRate,
                    exchangeRateFetchedAt,
                    transactionCreatedAt
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể thêm giao dịch");
                postError("Không thể thêm giao dịch", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
            }
        });
    }

    public void updateTransaction(
        String transactionId,
        String walletId,
        TransactionType type,
        double amount,
        String category,
        String note,
        String toWalletId,
        Timestamp transactionCreatedAt,
        Consumer<String> onComplete
    ) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                repository.updateTransaction(
                    userId,
                    transactionId,
                    walletId,
                    toWalletId,
                    type,
                    amount,
                    amount,
                    "",
                    "",
                    null,
                    null,
                    category,
                    note,
                    transactionCreatedAt
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể cập nhật giao dịch");
                postError("Không thể cập nhật giao dịch", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
            }
        });
    }

    public void updateTransferTransactionWithConversion(
        String transactionId,
        String sourceWalletId,
        String destinationWalletId,
        double sourceAmount,
        String category,
        String note,
        String sourceCurrency,
        String destinationCurrency,
        Timestamp transactionCreatedAt,
        Consumer<String> onComplete
    ) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                String sourceCode = normalizeCurrencyCode(sourceCurrency);
                String destinationCode = normalizeCurrencyCode(destinationCurrency);
                double destinationAmount = sourceAmount;
                Double exchangeRate = null;
                Timestamp exchangeRateFetchedAt = null;

                if (!sourceCode.equals(destinationCode)) {
                    ExchangeRateSnapshot snapshot = ExchangeRateSnapshotLoader.loadWithFallback(repository, userId);
                    if (snapshot == null) {
                        throw new IllegalStateException("Chưa có dữ liệu tỷ giá. Vui lòng chờ đồng bộ.");
                    }
                    exchangeRate = snapshot.conversionRate(sourceCode, destinationCode);
                    if (exchangeRate == null || exchangeRate <= 0.0) {
                        throw new IllegalStateException("Chưa có tỷ giá cho cặp " + sourceCode + "/" + destinationCode);
                    }
                    destinationAmount = roundCurrencyAmount(sourceAmount * exchangeRate);
                    if (destinationAmount <= 0.0) {
                        throw new IllegalStateException("Không thể quy đổi số tiền chuyển khoản.");
                    }
                    exchangeRateFetchedAt = snapshot.getFetchedAt();
                }

                repository.updateTransaction(
                    userId,
                    transactionId,
                    sourceWalletId,
                    destinationWalletId,
                    TransactionType.TRANSFER,
                    sourceAmount,
                    destinationAmount,
                    sourceCode,
                    destinationCode,
                    exchangeRate,
                    exchangeRateFetchedAt,
                    category,
                    note,
                    transactionCreatedAt
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể cập nhật giao dịch");
                postError("Không thể cập nhật giao dịch", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
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
        importCsvRows(rows, null);
    }

    public void importCsvRows(List<CsvImportRow> rows, Consumer<CsvImportSummary> onComplete) {
        ioExecutor.submit(() -> {
            if (rows == null || rows.isEmpty()) {
                CsvImportSummary summary = new CsvImportSummary(
                    0,
                    0,
                    0.0,
                    0.0,
                    "Không có dữ liệu hợp lệ để nhập."
                );
                if (onComplete != null) {
                    onComplete.accept(summary);
                }
                return;
            }

            FinanceUiState state = currentState();

            int successCount = 0;
            int skippedCount = 0;
            double totalIncome = 0.0;
            double totalExpense = 0.0;
            String importError = null;

            for (CsvImportRow row : rows) {
                if (row == null || !row.isValid()) {
                    skippedCount++;
                    continue;
                }
                if (row.getType() == null || row.getAmount() <= 0.0) {
                    skippedCount++;
                    continue;
                }

                String sourceWalletId = row.getWalletId();
                if (sourceWalletId == null || sourceWalletId.isBlank()) {
                    Wallet sourceWallet = FinanceParsersKt.findWalletByName(row.getWalletName(), state.getWallets());
                    sourceWalletId = sourceWallet == null ? null : sourceWallet.getId();
                }
                if (sourceWalletId == null || sourceWalletId.isBlank()) {
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
                        null,
                        row.getTransactionCreatedAt()
                    );
                    successCount++;
                    if (row.getType() == TransactionType.INCOME) {
                        totalIncome += row.getAmount();
                    } else if (row.getType() == TransactionType.EXPENSE) {
                        totalExpense += row.getAmount();
                    }
                } catch (Exception ex) {
                    if (importError == null || importError.trim().isEmpty()) {
                        importError = messageOrDefault(ex, "Có lỗi trong quá trình nhập dữ liệu.");
                    }
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

            CsvImportSummary summary = new CsvImportSummary(
                successCount,
                skippedCount,
                totalIncome,
                totalExpense,
                successCount > 0 ? "" : (importError == null ? "Không có giao dịch nào được nhập." : importError)
            );
            if (onComplete != null) {
                onComplete.accept(summary);
            }
        });
    }

    public void addBudgetLimit(String category, double limitAmount) {
        addBudgetLimit(
            category,
            category,
            limitAmount,
            com.example.myapplication.finance.model.BudgetLimit.REPEAT_MONTHLY,
            java.time.LocalDate.now().withDayOfMonth(1).toEpochDay(),
            java.time.LocalDate.now().withDayOfMonth(java.time.LocalDate.now().lengthOfMonth()).toEpochDay()
        );
    }

    public void addBudgetLimit(
        String name,
        String category,
        double limitAmount,
        String repeatCycle,
        long startDateEpochDay,
        long endDateEpochDay
    ) {
        addBudgetLimit(
            name,
            category,
            limitAmount,
            repeatCycle,
            startDateEpochDay,
            endDateEpochDay,
            null
        );
    }

    public void addBudgetLimit(
        String name,
        String category,
        double limitAmount,
        String repeatCycle,
        long startDateEpochDay,
        long endDateEpochDay,
        Consumer<String> onComplete
    ) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                repository.addBudgetLimit(
                    userId,
                    name,
                    category,
                    limitAmount,
                    repeatCycle,
                    startDateEpochDay,
                    endDateEpochDay
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể thêm hạn mức");
                postError("Không thể thêm hạn mức", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
            }
        });
    }

    public void updateBudgetLimit(
        String budgetId,
        String name,
        String category,
        double limitAmount,
        String repeatCycle,
        long startDateEpochDay,
        long endDateEpochDay
    ) {
        updateBudgetLimit(
            budgetId,
            name,
            category,
            limitAmount,
            repeatCycle,
            startDateEpochDay,
            endDateEpochDay,
            null
        );
    }

    public void updateBudgetLimit(
        String budgetId,
        String name,
        String category,
        double limitAmount,
        String repeatCycle,
        long startDateEpochDay,
        long endDateEpochDay,
        Consumer<String> onComplete
    ) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                repository.updateBudgetLimit(
                    userId,
                    budgetId,
                    name,
                    category,
                    limitAmount,
                    repeatCycle,
                    startDateEpochDay,
                    endDateEpochDay
                );
                refreshFromCacheOnly();
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể cập nhật hạn mức");
                postError("Không thể cập nhật hạn mức", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
            }
        });
    }

    public void deleteBudgetLimit(String budgetId) {
        deleteBudgetLimit(budgetId, null);
    }

    public void deleteBudgetLimit(String budgetId, Consumer<String> onComplete) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                repository.deleteBudgetLimit(userId, budgetId);
                refreshFromCacheOnly();
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể xóa hạn mức");
                postError("Không thể xóa hạn mức", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
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

    public void clearUserCloudData(Consumer<String> onComplete) {
        ioExecutor.submit(() -> {
            String errorMessage = null;
            try {
                repository.clearUserCloudData(userId);
                requestedDefaultCategories = false;
                clearInMemoryCache();
                publishState(buildSnapshot(Source.SERVER));
            } catch (Exception error) {
                errorMessage = messageOrDefault(error, "Không thể xóa dữ liệu trên máy chủ");
                postError("Không thể xóa dữ liệu trên máy chủ", error);
            }
            if (onComplete != null) {
                onComplete.accept(errorMessage);
            }
        });
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

    private static String normalizeCurrencyCode(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "VND";
        }
        return value;
    }

    private static double roundCurrencyAmount(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
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

