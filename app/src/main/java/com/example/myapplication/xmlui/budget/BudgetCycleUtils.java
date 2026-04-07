package com.example.myapplication.xmlui.budget;

import com.example.myapplication.finance.model.BudgetLimit;
import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public final class BudgetCycleUtils {
    private BudgetCycleUtils() {
    }

    public static final class BudgetWindow {
        private final LocalDate start;
        private final LocalDate end;
        private final boolean active;

        public BudgetWindow(LocalDate start, LocalDate end, boolean active) {
            this.start = start;
            this.end = end;
            this.active = active;
        }

        public LocalDate getStart() {
            return start;
        }

        public LocalDate getEnd() {
            return end;
        }

        public boolean isActive() {
            return active;
        }
    }

    public static BudgetWindow resolveWindow(BudgetLimit budget, LocalDate referenceDate) {
        LocalDate baseStart = LocalDate.ofEpochDay(budget.getStartDateEpochDay());
        LocalDate baseEnd = LocalDate.ofEpochDay(budget.getEndDateEpochDay());
        if (baseEnd.isBefore(baseStart)) {
            LocalDate temp = baseStart;
            baseStart = baseEnd;
            baseEnd = temp;
        }

        LocalDate start;
        LocalDate end;
        String repeatCycle = normalizeRepeatCycle(budget.getRepeatCycle());
        if (BudgetLimit.REPEAT_MONTHLY.equals(repeatCycle)) {
            int startDay = Math.min(baseStart.getDayOfMonth(), referenceDate.lengthOfMonth());
            int endDay = Math.min(baseEnd.getDayOfMonth(), referenceDate.lengthOfMonth());
            start = LocalDate.of(referenceDate.getYear(), referenceDate.getMonth(), startDay);
            end = LocalDate.of(referenceDate.getYear(), referenceDate.getMonth(), endDay);
            if (end.isBefore(start)) {
                end = end.plusMonths(1);
            }
            if (referenceDate.isAfter(end)) {
                start = start.plusMonths(1);
                end = end.plusMonths(1);
            }
        } else {
            start = baseStart;
            end = baseEnd;
        }

        boolean active = !referenceDate.isBefore(start) && !referenceDate.isAfter(end);
        return new BudgetWindow(start, end, active);
    }

    public static double calculateSpent(BudgetLimit budget, List<FinanceTransaction> transactions, ZoneId zoneId, LocalDate referenceDate) {
        BudgetWindow window = resolveWindow(budget, referenceDate);
        return calculateSpentInWindow(budget, transactions, zoneId, window.getStart(), window.getEnd());
    }

    public static double calculateSpentInWindow(
        BudgetLimit budget,
        List<FinanceTransaction> transactions,
        ZoneId zoneId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        double spent = 0.0;
        for (FinanceTransaction tx : transactions) {
            if (tx.getType() != TransactionType.EXPENSE) {
                continue;
            }
            LocalDate txDate = Instant.ofEpochSecond(
                tx.getCreatedAt().getSeconds(),
                tx.getCreatedAt().getNanoseconds()
            ).atZone(zoneId).toLocalDate();
            if (txDate.isBefore(startDate) || txDate.isAfter(endDate)) {
                continue;
            }
            if (!isAllCategory(budget) && !normalizeCategory(tx.getCategory()).equals(normalizeCategory(budget.getCategory()))) {
                continue;
            }
            spent += tx.getAmount();
        }
        return spent;
    }

    public static long daysRemaining(BudgetWindow window, LocalDate referenceDate) {
        if (referenceDate.isAfter(window.getEnd())) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(referenceDate, window.getEnd()) + 1L;
    }

    public static boolean isAllCategory(BudgetLimit budget) {
        String category = budget == null ? "" : budget.getCategory();
        if (category == null) {
            return true;
        }
        String normalized = category.trim();
        return normalized.isEmpty() || BudgetLimit.CATEGORY_ALL.equalsIgnoreCase(normalized);
    }

    public static String normalizeRepeatCycle(String value) {
        String repeat = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (BudgetLimit.REPEAT_MONTHLY.equals(repeat)) {
            return BudgetLimit.REPEAT_MONTHLY;
        }
        return BudgetLimit.REPEAT_NONE;
    }

    public static String normalizeCategory(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
