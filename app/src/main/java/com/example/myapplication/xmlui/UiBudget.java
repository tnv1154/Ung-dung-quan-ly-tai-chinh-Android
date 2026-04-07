package com.example.myapplication.xmlui;

public class UiBudget {
    private final String id;
    private final String name;
    private final String category;
    private final double limitAmount;
    private final double spent;
    private final double ratio;
    private final double remaining;
    private final String repeatCycle;
    private final long startDateEpochDay;
    private final long endDateEpochDay;
    private final long daysRemaining;
    private final boolean active;

    public UiBudget(
        String id,
        String name,
        String category,
        double limitAmount,
        double spent,
        double ratio,
        double remaining,
        String repeatCycle,
        long startDateEpochDay,
        long endDateEpochDay,
        long daysRemaining,
        boolean active
    ) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.limitAmount = limitAmount;
        this.spent = spent;
        this.ratio = ratio;
        this.remaining = remaining;
        this.repeatCycle = repeatCycle;
        this.startDateEpochDay = startDateEpochDay;
        this.endDateEpochDay = endDateEpochDay;
        this.daysRemaining = daysRemaining;
        this.active = active;
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

    public double getSpent() {
        return spent;
    }

    public double getRatio() {
        return ratio;
    }

    public double getRemaining() {
        return remaining;
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

    public long getDaysRemaining() {
        return daysRemaining;
    }

    public boolean isActive() {
        return active;
    }
}
