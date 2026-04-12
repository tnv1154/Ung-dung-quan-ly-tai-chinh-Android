package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionType;
import com.google.firebase.Timestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

final class FinanceUiCalculatorsKt {
    private FinanceUiCalculatorsKt() {
    }

    static FinanceSummary calculateCurrentMonthSummary(List<FinanceTransaction> transactions) {
        ZonedDateTime now = ZonedDateTime.now();
        double income = 0.0;
        double expense = 0.0;
        for (FinanceTransaction tx : transactions) {
            ZonedDateTime date = toZonedDateTime(tx.getCreatedAt());
            if (date.getYear() != now.getYear() || date.getMonth() != now.getMonth()) {
                continue;
            }
            if (tx.getType() == TransactionType.INCOME) {
                income += tx.getAmount();
            } else if (tx.getType() == TransactionType.EXPENSE || tx.getType() == TransactionType.TRANSFER) {
                expense += tx.getAmount();
            }
        }
        return new FinanceSummary(income, expense);
    }

    static List<FinanceTransaction> filterTransactionsForExport(
        List<FinanceTransaction> transactions,
        ExportPeriod period
    ) {
        return filterTransactionsForExport(transactions, period, null, null);
    }

    static List<FinanceTransaction> filterTransactionsForExport(
        List<FinanceTransaction> transactions,
        ExportPeriod period,
        LocalDate customStartDate,
        LocalDate customEndDate
    ) {
        ZonedDateTime now = ZonedDateTime.now();
        List<FinanceTransaction> filtered = new ArrayList<>();
        LocalDate thisQuarterStart = LocalDate.of(
            now.getYear(),
            ((now.getMonthValue() - 1) / 3) * 3 + 1,
            1
        );
        LocalDate lastQuarterStart = thisQuarterStart.minusMonths(3);
        LocalDate lastQuarterEnd = thisQuarterStart.minusDays(1);
        LocalDate customStart = customStartDate;
        LocalDate customEnd = customEndDate;
        if (customStart != null && customEnd != null && customEnd.isBefore(customStart)) {
            LocalDate swap = customStart;
            customStart = customEnd;
            customEnd = swap;
        }

        for (FinanceTransaction tx : transactions) {
            ZonedDateTime date = toZonedDateTime(tx.getCreatedAt());
            boolean include;
            switch (period) {
                case TODAY:
                    include = date.toLocalDate().equals(now.toLocalDate());
                    break;
                case THIS_WEEK:
                    LocalDate start = now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1L);
                    LocalDate end = start.plusDays(6);
                    LocalDate day = date.toLocalDate();
                    include = !day.isBefore(start) && !day.isAfter(end);
                    break;
                case THIS_MONTH:
                    include = date.getYear() == now.getYear() && date.getMonth() == now.getMonth();
                    break;
                case LAST_MONTH:
                    ZonedDateTime lastMonth = now.minusMonths(1);
                    include = date.getYear() == lastMonth.getYear()
                        && date.getMonthValue() == lastMonth.getMonthValue();
                    break;
                case THIS_QUARTER:
                    int nowQuarter = ((now.getMonthValue() - 1) / 3) + 1;
                    int txQuarter = ((date.getMonthValue() - 1) / 3) + 1;
                    include = date.getYear() == now.getYear() && nowQuarter == txQuarter;
                    break;
                case LAST_QUARTER:
                    LocalDate txDate = date.toLocalDate();
                    include = !txDate.isBefore(lastQuarterStart) && !txDate.isAfter(lastQuarterEnd);
                    break;
                case THIS_YEAR:
                    include = date.getYear() == now.getYear();
                    break;
                case LAST_YEAR:
                    include = date.getYear() == (now.getYear() - 1);
                    break;
                case CUSTOM:
                    LocalDate customDay = date.toLocalDate();
                    if (customStart == null || customEnd == null) {
                        include = true;
                    } else {
                        include = !customDay.isBefore(customStart) && !customDay.isAfter(customEnd);
                    }
                    break;
                case ALL:
                default:
                    include = true;
                    break;
            }
            if (include) {
                filtered.add(tx);
            }
        }
        return filtered;
    }

    private static ZonedDateTime toZonedDateTime(Timestamp timestamp) {
        Timestamp source = timestamp == null ? Timestamp.now() : timestamp;
        return Instant.ofEpochSecond(source.getSeconds(), source.getNanoseconds())
            .atZone(ZoneId.systemDefault());
    }
}

