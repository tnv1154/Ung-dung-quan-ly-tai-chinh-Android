package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.ExportRecordRow;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.google.firebase.Timestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FinanceExportBuilder {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private FinanceExportBuilder() {
    }

    public static List<ExportRecordRow> buildRows(
        FinanceUiState state,
        ExportPeriod period,
        LocalDate customStartDate,
        LocalDate customEndDate,
        Set<String> selectedWalletIds
    ) {
        FinanceUiState sourceState = state == null ? new FinanceUiState() : state;
        Map<String, Wallet> walletMap = new HashMap<>();
        for (Wallet wallet : sourceState.getWallets()) {
            walletMap.put(wallet.getId(), wallet);
        }

        Set<String> walletIds = selectedWalletIds == null ? new LinkedHashSet<>() : selectedWalletIds;
        List<FinanceTransaction> filteredByPeriod = FinanceUiCalculatorsKt.filterTransactionsForExport(
            sourceState.getTransactions(),
            period,
            customStartDate,
            customEndDate
        );
        filteredByPeriod.sort(
            Comparator.comparingLong(FinanceExportBuilder::timestampSeconds)
                .thenComparingLong(FinanceExportBuilder::timestampNanos)
        );

        List<ExportRecordRow> rows = new ArrayList<>();
        int nextIndex = 1;
        for (FinanceTransaction tx : filteredByPeriod) {
            if (!walletIds.isEmpty() && !walletIds.contains(tx.getWalletId())) {
                continue;
            }
            Wallet sourceWallet = walletMap.get(tx.getWalletId());
            String currency = normalizeCurrencyCode(firstNonBlank(
                tx.getSourceCurrency(),
                sourceWallet == null ? "" : sourceWallet.getCurrency(),
                "VND"
            ));
            double incomeAmount = tx.getType() == TransactionType.INCOME ? tx.getAmount() : 0.0;
            double expenseAmount = tx.getType() == TransactionType.INCOME ? 0.0 : tx.getAmount();

            ZonedDateTime time = toDateTime(tx.getCreatedAt());
            rows.add(
                new ExportRecordRow(
                    nextIndex++,
                    DATE_FORMATTER.format(time),
                    TIME_FORMATTER.format(time),
                    incomeAmount > 0.0 ? incomeAmount : null,
                    expenseAmount > 0.0 ? expenseAmount : null,
                    currency,
                    sourceWallet == null ? "" : sourceWallet.getName(),
                    buildCategory(tx),
                    safe(tx.getNote())
                )
            );
        }
        return rows;
    }

    private static String buildCategory(FinanceTransaction tx) {
        String category = tx == null ? "" : safe(tx.getCategory());
        if (!category.isBlank()) {
            return category;
        }
        return FinanceParsersKt.defaultCategoryForType(tx == null ? TransactionType.EXPENSE : tx.getType());
    }

    private static ZonedDateTime toDateTime(Timestamp timestamp) {
        Timestamp source = timestamp == null ? Timestamp.now() : timestamp;
        return Instant.ofEpochSecond(source.getSeconds(), source.getNanoseconds())
            .atZone(ZoneId.systemDefault());
    }

    private static long timestampSeconds(FinanceTransaction transaction) {
        if (transaction == null || transaction.getCreatedAt() == null) {
            return 0L;
        }
        return transaction.getCreatedAt().getSeconds();
    }

    private static long timestampNanos(FinanceTransaction transaction) {
        if (transaction == null || transaction.getCreatedAt() == null) {
            return 0L;
        }
        return transaction.getCreatedAt().getNanoseconds();
    }

    private static String normalizeCurrencyCode(String raw) {
        String value = safe(raw).trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "VND";
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

