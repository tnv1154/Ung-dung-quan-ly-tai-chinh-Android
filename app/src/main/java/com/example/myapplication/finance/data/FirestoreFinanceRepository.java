package com.example.myapplication.finance.data;

import com.example.myapplication.finance.model.BudgetLimit;
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
import com.google.firebase.firestore.FieldValue;
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
    private static final String USERS_COLLECTION = "users";
    private static final String WALLETS_COLLECTION = "wallets";
    private static final String TRANSACTIONS_COLLECTION = "transactions";
    private static final String BUDGETS_COLLECTION = "budgets";
    private static final String CATEGORIES_COLLECTION = "categories";

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
        budgets.sort(Comparator.comparing(BudgetLimit::getCategory));
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
        Tasks.await(
            firestore.collection(USERS_COLLECTION)
                .document(userId)
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
        DocumentSnapshot walletSnapshot = getDocumentSnapshotPreferCache(walletRef);
        Double oldBalanceRaw = walletSnapshot.getDouble("balance");
        double oldBalance = oldBalanceRaw == null ? 0.0 : oldBalanceRaw;
        double difference = newBalance - oldBalance;

        WriteBatch batch = firestore.batch();
        Map<String, Object> walletUpdate = new HashMap<>();
        walletUpdate.put("balance", newBalance);
        walletUpdate.put("updatedAt", now);
        batch.update(walletRef, walletUpdate);

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
            batch.set(txRef, FirestoreFinanceMappersKt.toFirestoreMap(adjustmentTx));
        }

        Tasks.await(batch.commit());
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
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentReference txRef = userRef.collection(TRANSACTIONS_COLLECTION).document();
        DocumentReference sourceWalletRef = userRef.collection(WALLETS_COLLECTION).document(walletId);
        DocumentReference targetWalletRef = toWalletId == null ? null : userRef.collection(WALLETS_COLLECTION).document(toWalletId);
        Timestamp now = Timestamp.now();
        Timestamp txCreatedAt = transactionCreatedAt == null ? now : transactionCreatedAt;
        WriteBatch batch = firestore.batch();

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

        Map<String, Object> sourceUpdate = new HashMap<>();
        sourceUpdate.put("balance", FieldValue.increment(sourceDelta));
        sourceUpdate.put("updatedAt", now);
        batch.update(sourceWalletRef, sourceUpdate);

        if (type == TransactionType.TRANSFER) {
            if (targetWalletRef == null) {
                throw new IllegalArgumentException("toWalletId is required for transfer");
            }
            Map<String, Object> targetUpdate = new HashMap<>();
            targetUpdate.put("balance", FieldValue.increment(amount));
            targetUpdate.put("updatedAt", now);
            batch.update(targetWalletRef, targetUpdate);
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
        batch.set(txRef, FirestoreFinanceMappersKt.toFirestoreMap(txModel));
        Tasks.await(batch.commit());
    }

    public void deleteTransaction(String userId, String transactionId) throws Exception {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
        DocumentReference txRef = userRef.collection(TRANSACTIONS_COLLECTION).document(transactionId);
        DocumentSnapshot txSnap = getDocumentSnapshotPreferCache(txRef);
        if (!txSnap.exists()) {
            return;
        }

        FinanceTransaction tx = FirestoreFinanceMappersKt.toFinanceTransactionModel(txSnap);
        DocumentReference sourceWalletRef = userRef.collection(WALLETS_COLLECTION).document(tx.getWalletId());
        Timestamp now = Timestamp.now();
        WriteBatch batch = firestore.batch();

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

        Map<String, Object> sourceUpdate = new HashMap<>();
        sourceUpdate.put("balance", FieldValue.increment(sourceDelta));
        sourceUpdate.put("updatedAt", now);
        batch.update(sourceWalletRef, sourceUpdate);

        if (tx.getType() == TransactionType.TRANSFER) {
            String targetId = tx.getToWalletId();
            if (targetId == null || targetId.isBlank()) {
                throw new IllegalStateException("transfer transaction missing toWalletId");
            }
            DocumentReference targetRef = userRef.collection(WALLETS_COLLECTION).document(targetId);
            Map<String, Object> targetUpdate = new HashMap<>();
            targetUpdate.put("balance", FieldValue.increment(-tx.getAmount()));
            targetUpdate.put("updatedAt", now);
            batch.update(targetRef, targetUpdate);
        }

        batch.delete(txRef);
        Tasks.await(batch.commit());
    }

    public void addBudgetLimit(String userId, String category, double limitAmount) throws Exception {
        DocumentReference budgetRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(BUDGETS_COLLECTION)
            .document();

        BudgetLimit budget = new BudgetLimit(
            budgetRef.getId(),
            category,
            limitAmount,
            Timestamp.now()
        );

        Tasks.await(budgetRef.set(FirestoreFinanceMappersKt.toFirestoreMap(budget)));
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

    public void seedDefaultCategories(String userId) throws Exception {
        CollectionReference categoriesRef = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(CATEGORIES_COLLECTION);

        List<DocumentSnapshot> existingDocs = Tasks.await(categoriesRef.get()).getDocuments();
        Set<String> existingKeys = new HashSet<>();
        for (DocumentSnapshot doc : existingDocs) {
            TransactionType type;
            try {
                type = TransactionType.valueOf(safe(doc.getString("type")));
            } catch (IllegalArgumentException ex) {
                type = TransactionType.EXPENSE;
            }
            existingKeys.add(
                FirestoreDefaultCategoriesKt.categoryIdentity(
                    type,
                    safe(doc.getString("parentName")),
                    safe(doc.getString("name"))
                )
            );
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

        if (missing.isEmpty()) {
            return;
        }

        WriteBatch batch = firestore.batch();
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

    private static DocumentSnapshot getDocumentSnapshotPreferCache(DocumentReference documentRef) throws Exception {
        try {
            DocumentSnapshot cached = Tasks.await(documentRef.get(Source.CACHE));
            if (cached != null && cached.exists()) {
                return cached;
            }
        } catch (Exception ignored) {
        }
        return Tasks.await(documentRef.get());
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}

