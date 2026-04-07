package com.example.myapplication.finance.model;

import com.google.firebase.Timestamp;

import java.time.LocalDate;
import java.util.Locale;

public class BudgetLimit {
    public static final String CATEGORY_ALL = "__ALL__";
    public static final String REPEAT_NONE = "NONE";
    public static final String REPEAT_MONTHLY = "MONTHLY";

    private final String id;
    private final String name;
    private final String category;
    private final double limitAmount;
    private final String repeatCycle;
    private final long startDateEpochDay;
    private final long endDateEpochDay;
    private final Timestamp updatedAt;

    public BudgetLimit() {
        this(
            "",
            "",
            CATEGORY_ALL,
            0.0,
            REPEAT_NONE,
            LocalDate.now().toEpochDay(),
            LocalDate.now().toEpochDay(),
            Timestamp.now()
        );
    }

    public BudgetLimit(String id, String category, double limitAmount, Timestamp updatedAt) {
        this(
            id,
            category,
            category,
            limitAmount,
            REPEAT_MONTHLY,
            LocalDate.now().withDayOfMonth(1).toEpochDay(),
            LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toEpochDay(),
            updatedAt
        );
    }

    public BudgetLimit(
        String id,
        String name,
        String category,
        double limitAmount,
        String repeatCycle,
        long startDateEpochDay,
        long endDateEpochDay,
        Timestamp updatedAt
    ) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.category = category == null ? "" : category;
        this.limitAmount = limitAmount;
        this.repeatCycle = normalizeRepeatCycle(repeatCycle);
        this.startDateEpochDay = startDateEpochDay;
        this.endDateEpochDay = endDateEpochDay;
        this.updatedAt = updatedAt == null ? Timestamp.now() : updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public double getLimitAmount() {
        return limitAmount;
    }

    public String getRepeatCycle() {
        return repeatCycle;
    }

    public long getStartDateEpochDay() {
        return startDateEpochDay;
    }

    public long getEndDateEpochDay() {
        return endDateEpochDay;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    private String normalizeRepeatCycle(String value) {
        String cycle = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (REPEAT_MONTHLY.equals(cycle)) {
            return REPEAT_MONTHLY;
        }
        return REPEAT_NONE;
    }
}

