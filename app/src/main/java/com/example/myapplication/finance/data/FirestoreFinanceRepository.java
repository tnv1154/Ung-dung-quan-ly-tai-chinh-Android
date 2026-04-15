package com.example.myapplication.finance.data;

import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.UserSettings;
import com.example.myapplication.finance.model.Wallet;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FirestoreFinanceRepository {
    private static final double BALANCE_EPSILON = 0.000001d;
    private static final String ERROR_WALLET_HAS_TRANSACTIONS = "Không thể xóa vì tài khoản đã có giao dịch liên quan.";
    private static final String USERS_COLLECTION = "users";
    private static final String WALLETS_COLLECTION = "wallets";
    private static final String TRANSACTIONS_COLLECTION = "transactions";
    private static final String BUDGETS_COLLECTION = "budgets";
    private static final String CATEGORIES_COLLECTION = "categories";
    private static final String EXCHANGE_RATES_COLLECTION = "exchange_rates";
    private static final String EXCHANGE_RATES_LATEST_DOC = "latest";
    private static final String NOTIFICATION_LOGS_COLLECTION = "notification_logs";
    private static final String IMPORT_EXPORT_LOGS_COLLECTION = "data_import_export_logs";

    private final FirebaseFirestore firestore;

    public FirestoreFinanceRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public FirestoreFinanceRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    public UserSettings getUserSettings(String userId) throws Exception {
        return getUserSettings(userId, Source.CACHE);
    }

    public UserSettings getUserSettings(String userId, Source source) throws Exception {
        DocumentSnapshot snapshot = Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get(source)
        );
        try {
            return FirestoreFinanceMappersKt.toUserSettingsModel(snapshot);
        } catch (Exception ex) {
            return new UserSettings();
        }
    }

    public List<Wallet> getWallets(String userId) throws Exception {
        return getWallets(userId, Source.CACHE);
    }

    public List<Wallet> getWallets(String userId, Source source) throws Exception {
        List<Wallet> wallets = new ArrayList<>();
        List<DocumentSnapshot> documents = Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(WALLETS_COLLECTION)
                .get(source)
        ).getDocuments();
        for (DocumentSnapshot doc : documents) {
            try {
                wallets.add(FirestoreFinanceMappersKt.toWalletModel(doc));
            } catch (Exception ignored) {
            }
        }
        wallets.sort(Comparator.comparing(Wallet::getName));
        return wallets;
    }

    public List<FinanceTransaction> getTransactions(String userId) throws Exception {
        return getTransactions(userId, Source.CACHE);
    }

    public List<FinanceTransaction> getTransactions(String userId, Source source) throws Exception {
        List<FinanceTransaction> transactions = new ArrayList<>();
        List<DocumentSnapshot> documents = Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(TRANSACTIONS_COLLECTION)
                .get(source)
        ).getDocuments();
        for (DocumentSnapshot doc : documents) {
            try {
                transactions.add(FirestoreFinanceMappersKt.toFinanceTransactionModel(doc));
            } catch (Exception ignored) {
            }
        }
        transactions.sort((first, second) -> Long.compare(
            second.getCreatedAt().getSeconds(),
            first.getCreatedAt().getSeconds()
        ));
        return transactions;
    }

    public List<BudgetLimit> getBudgetLimits(String userId) throws Exception {
        return getBudgetLimits(userId, Source.CACHE);
    }

    public List<BudgetLimit> getBudgetLimits(String userId, Source source) throws Exception {
        List<BudgetLimit> budgets = new ArrayList<>();
        List<DocumentSnapshot> documents = Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(BUDGETS_COLLECTION)
                .get(source)
        ).getDocuments();
        for (DocumentSnapshot doc : documents) {
            try {
                budgets.add(FirestoreFinanceMappersKt.toBudgetLimitModel(doc));
            } catch (Exception ignored) {
            }
        }
        budgets.sort(
            Comparator.comparingLong(BudgetLimit::getStartDateEpochDay)
                .thenComparing(BudgetLimit::getName)
                .thenComparing(BudgetLimit::getCategory)
        );
        return budgets;
    }

    public List<TransactionCategory> getCategories(String userId) throws Exception {
        return getCategories(userId, Source.CACHE);
    }

    public List<TransactionCategory> getCategories(String userId, Source source) throws Exception {
        List<TransactionCategory> categories = new ArrayList<>();
        List<DocumentSnapshot> documents = Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(CATEGORIES_COLLECTION)
                .get(source)
        ).getDocuments();
        for (DocumentSnapshot doc : documents) {
            try {
                categories.add(FirestoreFinanceMappersKt.toTransactionCategoryModel(doc));
            } catch (Exception ignored) {
            }
        }
        categories.sort(
            Comparator.comparingInt((TransactionCategory item) -> item.getType().ordinal())
                .thenComparingInt(TransactionCategory::getSortOrder)
                .thenComparing(TransactionCategory::getName)
        );
        return categories;
    }

    public ExchangeRateSnapshot getExchangeRateSnapshot(String userId) throws Exception {
        return getExchangeRateSnapshot(userId, Source.CACHE);
    }

    public ExchangeRateSnapshot getExchangeRateSnapshot(String userId, Source source) throws Exception {
        DocumentSnapshot snapshot = Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(EXCHANGE_RATES_COLLECTION)
                .document(EXCHANGE_RATES_LATEST_DOC)
                .get(source)
        );
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }
        return FirestoreFinanceMappersKt.toExchangeRateSnapshotModel(snapshot);
    }

    public void upsertExchangeRateSnapshot(String userId, ExchangeRateSnapshot snapshot) throws Exception {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot is required");
        }
        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(EXCHANGE_RATES_COLLECTION)
                .document(EXCHANGE_RATES_LATEST_DOC)
                .set(FirestoreFinanceMappersKt.toFirestoreMap(snapshot), SetOptions.merge())
        );
    }

    public Double getConversionRate(String userId, String fromCurrency, String toCurrency, Source source) throws Exception {
        String from = normalizeCurrency(fromCurrency);
        String to = normalizeCurrency(toCurrency);
        if (from.equals(to)) {
            return 1.0d;
        }
        ExchangeRateSnapshot snapshot = getExchangeRateSnapshot(userId, source);
        if (snapshot == null) {
            return null;
        }
        return snapshot.conversionRate(from, to);
    }

    public Wallet addWallet(String userId, String name, double openingBalance) throws Exception {
        return addWallet(userId, name, openingBalance, "CASH", "cash", "VND", "", true, "", false);
    }

    public Wallet addWallet(
        String userId,
        String name,
        double openingBalance,
        String accountType,
        String iconKey,
        String currency,
        String note,
        boolean includeInReport,
        String providerName,
        boolean isLocked
    ) throws Exception {
        DocumentReference walletRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(WALLETS_COLLECTION)
            .document();

        Wallet wallet = new Wallet(
            walletRef.getId(),
            name,
            openingBalance,
            accountType,
            iconKey,
            currency,
            note,
            includeInReport,
            providerName,
            isLocked,
            Timestamp.now()
        );

        Tasks.await(walletRef.set(FirestoreFinanceMappersKt.toFirestoreMap(wallet)));
        return wallet;
    }

    public void deleteWallet(String userId, String walletId) throws Exception {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        CollectionReference transactionsRef = userRef.collection(TRANSACTIONS_COLLECTION);
        boolean hasSourceLinks = !Tasks.await(
            transactionsRef
                .whereEqualTo("walletId", walletId)
                .limit(1)
                .get(Source.SERVER)
        ).getDocuments().isEmpty();
        if (hasSourceLinks) {
            throw new IllegalStateException(ERROR_WALLET_HAS_TRANSACTIONS);
        }
        boolean hasTargetLinks = !Tasks.await(
            transactionsRef
                .whereEqualTo("toWalletId", walletId)
                .limit(1)
                .get(Source.SERVER)
        ).getDocuments().isEmpty();
        if (hasTargetLinks) {
            throw new IllegalStateException(ERROR_WALLET_HAS_TRANSACTIONS);
        }
        Tasks.await(
            userRef
                .collection(WALLETS_COLLECTION)
                .document(walletId)
                .delete()
        );
    }

    public void updateWalletName(String userId, String walletId, String newName) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);
        updates.put("updatedAt", Timestamp.now());
        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(WALLETS_COLLECTION)
                .document(walletId)
                .update(updates)
        );
    }

    public void updateWallet(
        String userId,
        String walletId,
        String name,
        String accountType,
        String iconKey,
        String currency,
        String note,
        boolean includeInReport,
        String providerName,
        boolean isLocked
    ) throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("accountType", accountType);
        updates.put("iconKey", iconKey);
        updates.put("currency", currency);
        updates.put("note", note);
        updates.put("includeInReport", includeInReport);
        updates.put("providerName", providerName);
        updates.put("isLocked", isLocked);
        updates.put("updatedAt", Timestamp.now());
        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(WALLETS_COLLECTION)
                .document(walletId)
                .update(updates)
        );
    }

    public void adjustWalletBalance(String userId, String walletId, double newBalance, String note) throws Exception {
        adjustWalletBalance(userId, walletId, newBalance, note, null);
    }

    public void adjustWalletBalance(
        String userId,
        String walletId,
        double newBalance,
        String note,
        Timestamp transactionCreatedAt
    ) throws Exception {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentReference walletRef = userRef.collection(WALLETS_COLLECTION).document(walletId);
        DocumentReference txRef = userRef.collection(TRANSACTIONS_COLLECTION).document();
        Timestamp now = Timestamp.now();
        Timestamp txCreatedAt = transactionCreatedAt == null ? now : transactionCreatedAt;
        Tasks.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot walletSnapshot = transaction.get(walletRef);
            if (!walletSnapshot.exists()) {
                throw new IllegalStateException("wallet not found");
            }

            Double oldBalanceRaw = walletSnapshot.getDouble("balance");
            double oldBalance = oldBalanceRaw == null ? 0.0 : oldBalanceRaw;
            double difference = newBalance - oldBalance;

            Map<String, Object> walletUpdate = new HashMap<>();
            walletUpdate.put("balance", newBalance);
            walletUpdate.put("updatedAt", now);
            transaction.update(walletRef, walletUpdate);

            if (difference != 0.0) {
                TransactionType adjustmentType = difference > 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
                FinanceTransaction adjustmentTx = new FinanceTransaction(
                    txRef.getId(),
                    walletId,
                    null,
                    adjustmentType,
                    Math.abs(difference),
                    "Điều chỉnh số dư",
                    note,
                    txCreatedAt
                );
                transaction.set(txRef, FirestoreFinanceMappersKt.toFirestoreMap(adjustmentTx));
            }
            return null;
        }));
    }

    public void addTransaction(
        String userId,
        String walletId,
        TransactionType type,
        double amount,
        String category,
        String note
    ) throws Exception {
        addTransaction(userId, walletId, type, amount, category, note, null, null);
    }

    public void addTransaction(
        String userId,
        String walletId,
        TransactionType type,
        double amount,
        String category,
        String note,
        String toWalletId
    ) throws Exception {
        addTransaction(userId, walletId, type, amount, category, note, toWalletId, null);
    }

    public void addTransaction(
        String userId,
        String walletId,
        TransactionType type,
        double amount,
        String category,
        String note,
        String toWalletId,
        Timestamp transactionCreatedAt
    ) throws Exception {
        if (walletId == null || walletId.isBlank()) {
            throw new IllegalArgumentException("walletId is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("transaction type is required");
        }
        if (amount <= 0.0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentReference txRef = userRef.collection(TRANSACTIONS_COLLECTION).document();
        DocumentReference sourceWalletRef = userRef.collection(WALLETS_COLLECTION).document(walletId);
        DocumentReference targetWalletRef = toWalletId == null ? null : userRef.collection(WALLETS_COLLECTION).document(toWalletId);
        Timestamp now = Timestamp.now();
        Timestamp txCreatedAt = transactionCreatedAt == null ? now : transactionCreatedAt;

        double sourceDelta;
        switch (type) {
            case INCOME:
                sourceDelta = amount;
                break;
            case EXPENSE:
            case TRANSFER:
            default:
                sourceDelta = -amount;
                break;
        }

        Tasks.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot sourceWalletSnapshot = transaction.get(sourceWalletRef);
            if (!sourceWalletSnapshot.exists()) {
                throw new IllegalStateException("wallet not found");
            }
            double sourceBalance = readWalletBalance(sourceWalletSnapshot);
            double nextSourceBalance = sourceBalance + sourceDelta;

            Map<String, Object> sourceUpdate = new HashMap<>();
            sourceUpdate.put("balance", clampBalance(nextSourceBalance));
            sourceUpdate.put("updatedAt", now);
            transaction.update(sourceWalletRef, sourceUpdate);

            if (type == TransactionType.TRANSFER) {
                if (targetWalletRef == null) {
                    throw new IllegalArgumentException("toWalletId is required for transfer");
                }
                if (walletId.equals(toWalletId)) {
                    throw new IllegalArgumentException("source and destination wallet must be different");
                }
                DocumentSnapshot targetWalletSnapshot = transaction.get(targetWalletRef);
                if (!targetWalletSnapshot.exists()) {
                    throw new IllegalStateException("destination wallet not found");
                }
                double targetBalance = readWalletBalance(targetWalletSnapshot);
                Map<String, Object> targetUpdate = new HashMap<>();
                targetUpdate.put("balance", clampBalance(targetBalance + amount));
                targetUpdate.put("updatedAt", now);
                transaction.update(targetWalletRef, targetUpdate);
            }

            FinanceTransaction txModel = new FinanceTransaction(
                txRef.getId(),
                walletId,
                toWalletId,
                type,
                amount,
                category,
                note,
                txCreatedAt
            );
            transaction.set(txRef, FirestoreFinanceMappersKt.toFirestoreMap(txModel));
            return null;
        }));
    }

    public void addTransferTransaction(
        String userId,
        String sourceWalletId,
        String destinationWalletId,
        double sourceAmount,
        double destinationAmount,
        String category,
        String note,
        String sourceCurrency,
        String destinationCurrency,
        Double exchangeRate,
        Timestamp exchangeRateFetchedAt,
        Timestamp transactionCreatedAt
    ) throws Exception {
        if (sourceWalletId == null || sourceWalletId.isBlank()) {
            throw new IllegalArgumentException("sourceWalletId is required");
        }
        if (destinationWalletId == null || destinationWalletId.isBlank()) {
            throw new IllegalArgumentException("destinationWalletId is required");
        }
        if (sourceWalletId.equals(destinationWalletId)) {
            throw new IllegalArgumentException("source and destination wallet must be different");
        }
        if (sourceAmount <= 0.0 || destinationAmount <= 0.0) {
            throw new IllegalArgumentException("transfer amounts must be positive");
        }

        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentReference txRef = userRef.collection(TRANSACTIONS_COLLECTION).document();
        DocumentReference sourceWalletRef = userRef.collection(WALLETS_COLLECTION).document(sourceWalletId);
        DocumentReference targetWalletRef = userRef.collection(WALLETS_COLLECTION).document(destinationWalletId);
        Timestamp now = Timestamp.now();
        Timestamp txCreatedAt = transactionCreatedAt == null ? now : transactionCreatedAt;
        Tasks.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot sourceWalletSnapshot = transaction.get(sourceWalletRef);
            if (!sourceWalletSnapshot.exists()) {
                throw new IllegalStateException("source wallet not found");
            }
            DocumentSnapshot targetWalletSnapshot = transaction.get(targetWalletRef);
            if (!targetWalletSnapshot.exists()) {
                throw new IllegalStateException("destination wallet not found");
            }

            double sourceBalance = readWalletBalance(sourceWalletSnapshot);
            double nextSourceBalance = sourceBalance - sourceAmount;
            double targetBalance = readWalletBalance(targetWalletSnapshot);

            Map<String, Object> sourceUpdate = new HashMap<>();
            sourceUpdate.put("balance", clampBalance(nextSourceBalance));
            sourceUpdate.put("updatedAt", now);
            transaction.update(sourceWalletRef, sourceUpdate);

            Map<String, Object> targetUpdate = new HashMap<>();
            targetUpdate.put("balance", clampBalance(targetBalance + destinationAmount));
            targetUpdate.put("updatedAt", now);
            transaction.update(targetWalletRef, targetUpdate);

            FinanceTransaction txModel = new FinanceTransaction(
                txRef.getId(),
                sourceWalletId,
                destinationWalletId,
                TransactionType.TRANSFER,
                sourceAmount,
                destinationAmount,
                sourceCurrency,
                destinationCurrency,
                exchangeRate,
                exchangeRateFetchedAt,
                category,
                note,
                txCreatedAt
            );
            transaction.set(txRef, FirestoreFinanceMappersKt.toFirestoreMap(txModel));
            return null;
        }));
    }

    public void updateTransaction(
        String userId,
        String transactionId,
        String walletId,
        String toWalletId,
        TransactionType type,
        double amount,
        double destinationAmount,
        String sourceCurrency,
        String destinationCurrency,
        Double exchangeRate,
        Timestamp exchangeRateFetchedAt,
        String category,
        String note,
        Timestamp transactionCreatedAt
    ) throws Exception {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
        if (walletId == null || walletId.isBlank()) {
            throw new IllegalArgumentException("walletId is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("transaction type is required");
        }
        if (amount <= 0.0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        final String effectiveToWalletId;
        final double effectiveDestinationAmount;
        final String effectiveSourceCurrency;
        final String effectiveDestinationCurrency;
        if (type == TransactionType.TRANSFER) {
            if (toWalletId == null || toWalletId.isBlank()) {
                throw new IllegalArgumentException("toWalletId is required for transfer");
            }
            if (walletId.equals(toWalletId)) {
                throw new IllegalArgumentException("source and destination wallet must be different");
            }
            if (destinationAmount <= 0.0) {
                throw new IllegalArgumentException("destinationAmount must be positive");
            }
            effectiveToWalletId = toWalletId;
            effectiveDestinationAmount = destinationAmount;
            effectiveSourceCurrency = normalizeCurrency(sourceCurrency);
            effectiveDestinationCurrency = normalizeCurrency(destinationCurrency);
        } else {
            effectiveToWalletId = null;
            effectiveDestinationAmount = amount;
            effectiveSourceCurrency = "";
            effectiveDestinationCurrency = "";
            exchangeRate = null;
            exchangeRateFetchedAt = null;
        }

        final Double finalExchangeRate = exchangeRate;
        final Timestamp finalExchangeRateFetchedAt = exchangeRateFetchedAt;
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentReference txRef = userRef.collection(TRANSACTIONS_COLLECTION).document(transactionId);
        Timestamp now = Timestamp.now();
        Timestamp txCreatedAt = transactionCreatedAt == null ? now : transactionCreatedAt;
        Tasks.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot txSnapshot = transaction.get(txRef);
            if (!txSnapshot.exists()) {
                throw new IllegalStateException("transaction not found");
            }
            FinanceTransaction previous = FirestoreFinanceMappersKt.toFinanceTransactionModel(txSnapshot);
            Map<String, Double> walletDeltas = new HashMap<>();
            applyRevertTransactionDeltas(previous, walletDeltas);
            applyNewTransactionDeltas(
                type,
                walletId,
                effectiveToWalletId,
                amount,
                effectiveDestinationAmount,
                walletDeltas
            );

            for (Map.Entry<String, Double> entry : walletDeltas.entrySet()) {
                double delta = entry.getValue() == null ? 0.0 : entry.getValue();
                if (Math.abs(delta) <= BALANCE_EPSILON) {
                    continue;
                }
                String changedWalletId = entry.getKey();
                if (changedWalletId == null || changedWalletId.isBlank()) {
                    throw new IllegalStateException("wallet id missing while updating transaction");
                }
                DocumentReference walletRef = userRef.collection(WALLETS_COLLECTION).document(changedWalletId);
                DocumentSnapshot walletSnapshot = transaction.get(walletRef);
                if (!walletSnapshot.exists()) {
                    throw new IllegalStateException("wallet not found");
                }
                double currentBalance = readWalletBalance(walletSnapshot);
                Map<String, Object> updates = new HashMap<>();
                updates.put("balance", clampBalance(currentBalance + delta));
                updates.put("updatedAt", now);
                transaction.update(walletRef, updates);
            }

            FinanceTransaction updatedTx;
            if (type == TransactionType.TRANSFER) {
                updatedTx = new FinanceTransaction(
                    txRef.getId(),
                    walletId,
                    effectiveToWalletId,
                    TransactionType.TRANSFER,
                    amount,
                    effectiveDestinationAmount,
                    effectiveSourceCurrency,
                    effectiveDestinationCurrency,
                    finalExchangeRate,
                    finalExchangeRateFetchedAt,
                    category,
                    note,
                    txCreatedAt
                );
            } else {
                updatedTx = new FinanceTransaction(
                    txRef.getId(),
                    walletId,
                    null,
                    type,
                    amount,
                    category,
                    note,
                    txCreatedAt
                );
            }
            transaction.set(txRef, FirestoreFinanceMappersKt.toFirestoreMap(updatedTx));
            return null;
        }));
    }

    public void deleteTransaction(String userId, String transactionId) throws Exception {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentReference txRef = userRef.collection(TRANSACTIONS_COLLECTION).document(transactionId);
        Timestamp now = Timestamp.now();
        Tasks.await(firestore.runTransaction(transaction -> {
            DocumentSnapshot txSnap = transaction.get(txRef);
            if (!txSnap.exists()) {
                return null;
            }

            FinanceTransaction tx = FirestoreFinanceMappersKt.toFinanceTransactionModel(txSnap);
            DocumentReference sourceWalletRef = userRef.collection(WALLETS_COLLECTION).document(tx.getWalletId());
            DocumentSnapshot sourceWalletSnapshot = transaction.get(sourceWalletRef);
            if (!sourceWalletSnapshot.exists()) {
                throw new IllegalStateException("wallet not found");
            }
            double sourceBalance = readWalletBalance(sourceWalletSnapshot);

            double sourceDelta;
            switch (tx.getType()) {
                case INCOME:
                    sourceDelta = -tx.getAmount();
                    break;
                case EXPENSE:
                case TRANSFER:
                default:
                    sourceDelta = tx.getAmount();
                    break;
            }
            double nextSourceBalance = sourceBalance + sourceDelta;

            Map<String, Object> sourceUpdate = new HashMap<>();
            sourceUpdate.put("balance", clampBalance(nextSourceBalance));
            sourceUpdate.put("updatedAt", now);
            transaction.update(sourceWalletRef, sourceUpdate);

            if (tx.getType() == TransactionType.TRANSFER) {
                String targetId = tx.getToWalletId();
                if (targetId == null || targetId.isBlank()) {
                    throw new IllegalStateException("transfer transaction missing toWalletId");
                }
                DocumentReference targetRef = userRef.collection(WALLETS_COLLECTION).document(targetId);
                DocumentSnapshot targetWalletSnapshot = transaction.get(targetRef);
                if (!targetWalletSnapshot.exists()) {
                    throw new IllegalStateException("destination wallet not found");
                }
                double targetBalance = readWalletBalance(targetWalletSnapshot);
                double targetAmount = tx.getDestinationAmount() > 0.0 ? tx.getDestinationAmount() : tx.getAmount();
                double nextTargetBalance = targetBalance - targetAmount;
                Map<String, Object> targetUpdate = new HashMap<>();
                targetUpdate.put("balance", clampBalance(nextTargetBalance));
                targetUpdate.put("updatedAt", now);
                transaction.update(targetRef, targetUpdate);
            }

            transaction.delete(txRef);
            return null;
        }));
    }

    public void addBudgetLimit(String userId, String category, double limitAmount) throws Exception {
        addBudgetLimit(
            userId,
            category,
            category,
            limitAmount,
            BudgetLimit.REPEAT_MONTHLY,
            java.time.LocalDate.now().withDayOfMonth(1).toEpochDay(),
            java.time.LocalDate.now().withDayOfMonth(java.time.LocalDate.now().lengthOfMonth()).toEpochDay()
        );
    }

    public void addBudgetLimit(
        String userId,
        String name,
        String category,
        double limitAmount,
        String repeatCycle,
        long startDateEpochDay,
        long endDateEpochDay
    ) throws Exception {
        if (limitAmount <= 0.0) {
            throw new IllegalArgumentException("Hạn mức phải lớn hơn 0.");
        }
        if (endDateEpochDay < startDateEpochDay) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        String normalizedRepeatCycle = BudgetLimit.REPEAT_MONTHLY.equalsIgnoreCase(repeatCycle)
            ? BudgetLimit.REPEAT_MONTHLY
            : BudgetLimit.REPEAT_NONE;
        String normalizedCategory = category == null || category.trim().isEmpty()
            ? BudgetLimit.CATEGORY_ALL
            : category.trim();
        String normalizedName = name == null || name.trim().isEmpty()
            ? normalizedCategory
            : name.trim();
        DocumentReference budgetRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(BUDGETS_COLLECTION)
            .document();

        BudgetLimit budget = new BudgetLimit(
            budgetRef.getId(),
            normalizedName,
            normalizedCategory,
            limitAmount,
            normalizedRepeatCycle,
            startDateEpochDay,
            endDateEpochDay,
            Timestamp.now()
        );

        Tasks.await(budgetRef.set(FirestoreFinanceMappersKt.toFirestoreMap(budget)));
    }

    public void updateBudgetLimit(
        String userId,
        String budgetId,
        String name,
        String category,
        double limitAmount,
        String repeatCycle,
        long startDateEpochDay,
        long endDateEpochDay
    ) throws Exception {
        if (limitAmount <= 0.0) {
            throw new IllegalArgumentException("Hạn mức phải lớn hơn 0.");
        }
        if (endDateEpochDay < startDateEpochDay) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        String normalizedRepeatCycle = BudgetLimit.REPEAT_MONTHLY.equalsIgnoreCase(repeatCycle)
            ? BudgetLimit.REPEAT_MONTHLY
            : BudgetLimit.REPEAT_NONE;
        String normalizedCategory = category == null || category.trim().isEmpty()
            ? BudgetLimit.CATEGORY_ALL
            : category.trim();
        String normalizedName = name == null || name.trim().isEmpty()
            ? normalizedCategory
            : name.trim();
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", normalizedName);
        updates.put("category", normalizedCategory);
        updates.put("limitAmount", limitAmount);
        updates.put("repeatCycle", normalizedRepeatCycle);
        updates.put("startDateEpochDay", startDateEpochDay);
        updates.put("endDateEpochDay", endDateEpochDay);
        updates.put("updatedAt", Timestamp.now());
        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(BUDGETS_COLLECTION)
                .document(budgetId)
                .update(updates)
        );
    }

    public void deleteBudgetLimit(String userId, String budgetId) throws Exception {
        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(BUDGETS_COLLECTION)
                .document(budgetId)
                .delete()
        );
    }

    public void addCategory(String userId, String name, TransactionType type) throws Exception {
        addCategory(userId, name, type, "", "dot", 0);
    }

    public void addCategory(
        String userId,
        String name,
        TransactionType type,
        String parentName,
        String iconKey
    ) throws Exception {
        addCategory(userId, name, type, parentName, iconKey, 0);
    }

    public void addCategory(
        String userId,
        String name,
        TransactionType type,
        String parentName,
        String iconKey,
        int sortOrder
    ) throws Exception {
        DocumentReference ref = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CATEGORIES_COLLECTION)
            .document();

        TransactionCategory category = new TransactionCategory(
            ref.getId(),
            name,
            type,
            parentName == null ? "" : parentName,
            iconKey == null ? "dot" : iconKey,
            sortOrder,
            Timestamp.now()
        );

        Tasks.await(ref.set(FirestoreFinanceMappersKt.toFirestoreMap(category)));
    }

    public void updateCategory(
        String userId,
        String categoryId,
        String name,
        TransactionType type,
        String parentName,
        String iconKey,
        int sortOrder
    ) throws Exception {
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name.trim());
        updates.put("type", type.name());
        updates.put("parentName", parentName == null ? "" : parentName.trim());
        updates.put("iconKey", iconKey == null || iconKey.isBlank() ? "dot" : iconKey.trim());
        updates.put("sortOrder", Math.max(0, sortOrder));
        updates.put("updatedAt", Timestamp.now());

        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(CATEGORIES_COLLECTION)
                .document(categoryId)
                .update(updates)
        );
    }

    public void seedDefaultCategories(String userId) throws Exception {
        CollectionReference categoriesRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CATEGORIES_COLLECTION);

        List<DocumentSnapshot> existingDocs = Tasks.await(categoriesRef.get()).getDocuments();
        Set<String> retiredIdentities = new HashSet<>();
        retiredIdentities.add(
            FirestoreDefaultCategoriesKt.categoryIdentity(
                TransactionType.INCOME,
                "",
                "Lãi tiết kiệm"
            )
        );
        retiredIdentities.add(
            FirestoreDefaultCategoriesKt.categoryIdentity(
                TransactionType.EXPENSE,
                "Tiền ra",
                "Đầu tư"
            )
        );
        retiredIdentities.add(
            FirestoreDefaultCategoriesKt.categoryIdentity(
                TransactionType.EXPENSE,
                "Tiền ra",
                "Khác"
            )
        );
        Set<String> existingKeys = new HashSet<>();
        List<DocumentReference> docsToDelete = new ArrayList<>();
        for (DocumentSnapshot doc : existingDocs) {
            TransactionType type;
            try {
                type = TransactionType.valueOf(safe(doc.getString("type")));
            } catch (IllegalArgumentException ex) {
                type = TransactionType.EXPENSE;
            }
            String identity = FirestoreDefaultCategoriesKt.categoryIdentity(
                type,
                safe(doc.getString("parentName")),
                safe(doc.getString("name"))
            );
            if (retiredIdentities.contains(identity)) {
                docsToDelete.add(doc.getReference());
                continue;
            }
            existingKeys.add(identity);
        }

        Timestamp now = Timestamp.now();
        List<TransactionCategory> defaults = FirestoreDefaultCategoriesKt.defaultTransactionCategories(now);
        List<TransactionCategory> missing = new ArrayList<>();
        for (TransactionCategory category : defaults) {
            String identity = FirestoreDefaultCategoriesKt.categoryIdentity(
                category.getType(),
                category.getParentName(),
                category.getName()
            );
            if (!existingKeys.contains(identity)) {
                missing.add(category);
            }
        }

        if (missing.isEmpty() && docsToDelete.isEmpty()) {
            return;
        }

        WriteBatch batch = firestore.batch();
        for (DocumentReference docRef : docsToDelete) {
            batch.delete(docRef);
        }
        for (TransactionCategory category : missing) {
            String docId = category.getId();
            if (docId == null || docId.isBlank()) {
                String parentToken = category.getParentName().isBlank()
                    ? "root"
                    : FirestoreDefaultCategoriesKt.slugify(category.getParentName());
                docId = category.getType().name().toLowerCase(Locale.ROOT)
                    + "_"
                    + parentToken
                    + "_"
                    + FirestoreDefaultCategoriesKt.slugify(category.getName());
            }
            TransactionCategory withId = new TransactionCategory(
                docId,
                category.getName(),
                category.getType(),
                category.getParentName(),
                category.getIconKey(),
                category.getSortOrder(),
                category.getUpdatedAt()
            );
            batch.set(categoriesRef.document(docId), FirestoreFinanceMappersKt.toFirestoreMap(withId));
        }
        Tasks.await(batch.commit());
    }

    public void deleteCategory(String userId, String categoryId) throws Exception {
        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(CATEGORIES_COLLECTION)
                .document(categoryId)
                .delete()
        );
    }

    public void updateUserSettings(
        String userId,
        String currency,
        boolean showBudgetWarnings,
        boolean compactNumberFormat,
        boolean reminderEnabled,
        String reminderFrequency,
        int reminderHour,
        int reminderMinute,
        List<Integer> reminderWeekdays
    ) throws Exception {
        Map<String, Object> settings = new HashMap<>();
        settings.put("currency", currency);
        settings.put("showBudgetWarnings", showBudgetWarnings);
        settings.put("compactNumberFormat", compactNumberFormat);
        settings.put("reminderEnabled", reminderEnabled);
        settings.put("reminderFrequency", reminderFrequency);
        settings.put("reminderHour", reminderHour);
        settings.put("reminderMinute", reminderMinute);
        settings.put("reminderWeekdays", reminderWeekdays == null ? new ArrayList<>() : new ArrayList<>(reminderWeekdays));
        settings.put("updatedAt", Timestamp.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("settings", settings);

        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .set(payload, SetOptions.merge())
        );
    }

    public void clearUserCloudData(String userId) throws Exception {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        deleteCollection(userRef.collection(TRANSACTIONS_COLLECTION));
        deleteCollection(userRef.collection(BUDGETS_COLLECTION));
        deleteCollection(userRef.collection(CATEGORIES_COLLECTION));
        deleteCollection(userRef.collection(EXCHANGE_RATES_COLLECTION));
        deleteCollection(userRef.collection(NOTIFICATION_LOGS_COLLECTION));
        deleteCollection(userRef.collection(IMPORT_EXPORT_LOGS_COLLECTION));
        deleteCollection(userRef.collection(WALLETS_COLLECTION));
        Tasks.await(userRef.delete());
    }

    private void deleteCollection(CollectionReference collectionRef) throws Exception {
        final int batchSize = 400;
        while (true) {
            List<DocumentSnapshot> documents = Tasks.await(
                collectionRef.limit(batchSize).get(Source.SERVER)
            ).getDocuments();
            if (documents.isEmpty()) {
                return;
            }
            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : documents) {
                batch.delete(doc.getReference());
            }
            Tasks.await(batch.commit());
            if (documents.size() < batchSize) {
                return;
            }
        }
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String normalizeCurrency(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "VND";
        }
        return value;
    }

    private static double readWalletBalance(DocumentSnapshot walletSnapshot) {
        Double rawBalance = walletSnapshot.getDouble("balance");
        if (rawBalance == null) {
            return 0.0;
        }
        return rawBalance;
    }

    private static void applyRevertTransactionDeltas(FinanceTransaction tx, Map<String, Double> walletDeltas) {
        switch (tx.getType()) {
            case INCOME:
                addWalletDelta(walletDeltas, tx.getWalletId(), -tx.getAmount());
                break;
            case EXPENSE:
                addWalletDelta(walletDeltas, tx.getWalletId(), tx.getAmount());
                break;
            case TRANSFER:
                addWalletDelta(walletDeltas, tx.getWalletId(), tx.getAmount());
                String oldTargetWalletId = tx.getToWalletId();
                if (oldTargetWalletId == null || oldTargetWalletId.isBlank()) {
                    throw new IllegalStateException("transfer transaction missing toWalletId");
                }
                double oldTargetAmount = tx.getDestinationAmount() > 0.0 ? tx.getDestinationAmount() : tx.getAmount();
                addWalletDelta(walletDeltas, oldTargetWalletId, -oldTargetAmount);
                break;
            default:
                addWalletDelta(walletDeltas, tx.getWalletId(), tx.getAmount());
                break;
        }
    }

    private static void applyNewTransactionDeltas(
        TransactionType type,
        String walletId,
        String toWalletId,
        double amount,
        double destinationAmount,
        Map<String, Double> walletDeltas
    ) {
        switch (type) {
            case INCOME:
                addWalletDelta(walletDeltas, walletId, amount);
                break;
            case EXPENSE:
                addWalletDelta(walletDeltas, walletId, -amount);
                break;
            case TRANSFER:
                if (toWalletId == null || toWalletId.isBlank()) {
                    throw new IllegalArgumentException("toWalletId is required for transfer");
                }
                if (walletId.equals(toWalletId)) {
                    throw new IllegalArgumentException("source and destination wallet must be different");
                }
                addWalletDelta(walletDeltas, walletId, -amount);
                addWalletDelta(walletDeltas, toWalletId, destinationAmount);
                break;
            default:
                throw new IllegalArgumentException("Unsupported transaction type: " + type);
        }
    }

    private static void addWalletDelta(Map<String, Double> walletDeltas, String walletId, double delta) {
        if (walletId == null || walletId.isBlank()) {
            throw new IllegalArgumentException("walletId is required");
        }
        double next = walletDeltas.getOrDefault(walletId, 0.0) + delta;
        if (Math.abs(next) <= BALANCE_EPSILON) {
            walletDeltas.remove(walletId);
        } else {
            walletDeltas.put(walletId, next);
        }
    }

    private static double clampBalance(double value) {
        if (value < 0.0 && Math.abs(value) <= BALANCE_EPSILON) {
            return 0.0;
        }
        return value;
    }
}

