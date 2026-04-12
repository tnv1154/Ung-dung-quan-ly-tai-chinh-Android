package com.example.myapplication.finance.ui;

import android.content.Context;

import com.example.myapplication.R;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ExportPeriodUtils {
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter FILE_DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT);

    private ExportPeriodUtils() {
    }

    public static ExportPeriod parseOrDefault(String raw, ExportPeriod fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return ExportPeriod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static DateRange resolveRange(ExportPeriod period, LocalDate customStartDate, LocalDate customEndDate) {
        ExportPeriod resolvedPeriod = period == null ? ExportPeriod.THIS_MONTH : period;
        LocalDate now = LocalDate.now();
        switch (resolvedPeriod) {
            case TODAY:
                return new DateRange(now, now);
            case THIS_WEEK:
                LocalDate weekStart = now.minusDays(now.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
                return new DateRange(weekStart, weekStart.plusDays(6));
            case THIS_MONTH:
                return new DateRange(now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth()));
            case LAST_MONTH:
                LocalDate previousMonth = now.minusMonths(1);
                return new DateRange(
                    previousMonth.withDayOfMonth(1),
                    previousMonth.withDayOfMonth(previousMonth.lengthOfMonth())
                );
            case THIS_QUARTER:
                LocalDate quarterStart = LocalDate.of(now.getYear(), ((now.getMonthValue() - 1) / 3) * 3 + 1, 1);
                return new DateRange(quarterStart, quarterStart.plusMonths(3).minusDays(1));
            case LAST_QUARTER:
                LocalDate thisQuarterStart = LocalDate.of(now.getYear(), ((now.getMonthValue() - 1) / 3) * 3 + 1, 1);
                LocalDate lastQuarterStart = thisQuarterStart.minusMonths(3);
                return new DateRange(lastQuarterStart, thisQuarterStart.minusDays(1));
            case THIS_YEAR:
                return new DateRange(LocalDate.of(now.getYear(), 1, 1), LocalDate.of(now.getYear(), 12, 31));
            case LAST_YEAR:
                int lastYear = now.getYear() - 1;
                return new DateRange(LocalDate.of(lastYear, 1, 1), LocalDate.of(lastYear, 12, 31));
            case CUSTOM:
                LocalDate start = customStartDate == null ? now : customStartDate;
                LocalDate end = customEndDate == null ? start : customEndDate;
                if (end.isBefore(start)) {
                    LocalDate swap = start;
                    start = end;
                    end = swap;
                }
                return new DateRange(start, end);
            case ALL:
            default:
                return new DateRange(null, null);
        }
    }

    public static String formatDisplayDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return DISPLAY_DATE_FORMAT.format(date);
    }

    public static String periodDisplayLabel(
        Context context,
        ExportPeriod period,
        LocalDate customStartDate,
        LocalDate customEndDate
    ) {
        ExportPeriod resolvedPeriod = period == null ? ExportPeriod.THIS_MONTH : period;
        switch (resolvedPeriod) {
            case THIS_MONTH:
                return context.getString(R.string.export_period_this_month);
            case LAST_MONTH:
                return context.getString(R.string.export_period_last_month);
            case THIS_QUARTER:
                return context.getString(R.string.export_period_this_quarter);
            case LAST_QUARTER:
                return context.getString(R.string.export_period_last_quarter);
            case THIS_YEAR:
                return context.getString(R.string.export_period_this_year);
            case LAST_YEAR:
                return context.getString(R.string.export_period_last_year);
            case CUSTOM:
                DateRange customRange = resolveRange(resolvedPeriod, customStartDate, customEndDate);
                return context.getString(
                    R.string.export_period_custom_range_display,
                    formatDisplayDate(customRange.getStartDate()),
                    formatDisplayDate(customRange.getEndDate())
                );
            case TODAY:
                return context.getString(R.string.label_period_day);
            case THIS_WEEK:
                return context.getString(R.string.label_period_week);
            case ALL:
            default:
                return context.getString(R.string.label_period_all);
        }
    }

    public static String periodFileNameSuffix(ExportPeriod period, LocalDate customStartDate, LocalDate customEndDate) {
        ExportPeriod resolvedPeriod = period == null ? ExportPeriod.THIS_MONTH : period;
        switch (resolvedPeriod) {
            case THIS_MONTH:
                return "Thang_nay";
            case LAST_MONTH:
                return "Thang_truoc";
            case THIS_QUARTER:
                return "Quy_nay";
            case LAST_QUARTER:
                return "Quy_truoc";
            case THIS_YEAR:
                return "Nam_nay";
            case LAST_YEAR:
                return "Nam_truoc";
            case CUSTOM:
                DateRange customRange = resolveRange(resolvedPeriod, customStartDate, customEndDate);
                return FILE_DATE_FORMAT.format(customRange.getStartDate()) + "_den_" + FILE_DATE_FORMAT.format(customRange.getEndDate());
            case TODAY:
                return "Hom_nay";
            case THIS_WEEK:
                return "Tuan_nay";
            case ALL:
            default:
                return "Tat_ca_thoi_gian";
        }
    }

    public static final class DateRange {
        private final LocalDate startDate;
        private final LocalDate endDate;

        public DateRange(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }
    }
}

