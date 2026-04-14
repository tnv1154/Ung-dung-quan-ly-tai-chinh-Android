package com.example.myapplication.xmlui;

import androidx.annotation.NonNull;

import com.example.myapplication.finance.model.TransactionType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ReportFilterState {

    private static final EnumSet<TransactionType> SUPPORTED_TYPES =
        EnumSet.of(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER);

    private final Set<String> walletIds;
    private final EnumSet<TransactionType> transactionTypes;

    public ReportFilterState(Set<String> walletIds, Set<TransactionType> transactionTypes) {
        Set<String> wallets = walletIds == null ? Collections.emptySet() : walletIds;
        this.walletIds = Collections.unmodifiableSet(new LinkedHashSet<>(wallets));

        EnumSet<TransactionType> types = EnumSet.noneOf(TransactionType.class);
        if (transactionTypes != null) {
            types.addAll(transactionTypes);
        }
        types.retainAll(SUPPORTED_TYPES);
        this.transactionTypes = types;
    }

    public static ReportFilterState all() {
        return new ReportFilterState(Collections.emptySet(), Collections.emptySet());
    }

    @NonNull
    public Set<String> getWalletIds() {
        return walletIds;
    }

    @NonNull
    public Set<TransactionType> getTransactionTypes() {
        return Collections.unmodifiableSet(transactionTypes);
    }

    public boolean hasWalletFilter() {
        return !walletIds.isEmpty();
    }

    public boolean hasTypeFilter() {
        return !transactionTypes.isEmpty();
    }

    public boolean isAll() {
        return !hasWalletFilter() && !hasTypeFilter();
    }

    public boolean includesWallet(String walletId) {
        return walletIds.isEmpty() || walletIds.contains(walletId);
    }

    public boolean includesType(TransactionType type) {
        return transactionTypes.isEmpty() || transactionTypes.contains(type);
    }
}
