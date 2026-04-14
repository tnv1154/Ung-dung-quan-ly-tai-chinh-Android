package com.example.myapplication.xmlui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HistoryFilterActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_KEY = "extra_history_filter_selected_key";
    public static final String EXTRA_HAS_RANGE = "extra_history_filter_has_range";
    public static final String EXTRA_START_EPOCH = "extra_history_filter_start_epoch";
    public static final String EXTRA_END_EPOCH = "extra_history_filter_end_epoch";

    public static final String EXTRA_RESULT_KEY = "extra_history_filter_result_key";
    public static final String EXTRA_RESULT_LABEL = "extra_history_filter_result_label";
    public static final String EXTRA_RESULT_HAS_RANGE = "extra_history_filter_result_has_range";
    public static final String EXTRA_RESULT_START_EPOCH = "extra_history_filter_result_start_epoch";
    public static final String EXTRA_RESULT_END_EPOCH = "extra_history_filter_result_end_epoch";

    public static final String KEY_DAY_TODAY = "day_today";
    public static final String KEY_DAY_YESTERDAY = "day_yesterday";
    public static final String KEY_DAY_OTHER = "day_other";
    public static final String KEY_WEEK_THIS = "week_this";
    public static final String KEY_WEEK_LAST = "week_last";
    public static final String KEY_MONTH_THIS = "month_this";
    public static final String KEY_MONTH_LAST = "month_last";
    public static final String KEY_MONTH_OTHER = "month_other";
    public static final String KEY_QUARTER_1 = "quarter_1";
    public static final String KEY_QUARTER_2 = "quarter_2";
    public static final String KEY_QUARTER_3 = "quarter_3";
    public static final String KEY_QUARTER_4 = "quarter_4";
    public static final String KEY_CUSTOM_ALL = "custom_all";
    public static final String KEY_CUSTOM_RANGE = "custom_range";

    private enum TabType {
        DAY,
        WEEK,
        MONTH,
        QUARTER,
        CUSTOM
    }

    private interface DateCallback {
        void onPicked(LocalDate date);
    }

    private static final ZoneId DEVICE_ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TabLayout tabLayout;

    private View rowAllTime;
    private View dividerAllTime;
    private View rowOption1;
    private View rowOption2;
    private View rowOption3;
    private View rowOption4;
    private View dividerOption1;
    private View dividerOption2;
    private View dividerOption3;
    private View dividerOption4;
    private View layoutRange;
    private View rowFrom;
    private View rowTo;

    private TextView tvAllTime;
    private TextView tvOption1;
    private TextView tvOption2;
    private TextView tvOption3;
    private TextView tvOption4;
    private TextView tvFromValue;
    private TextView tvToValue;

    private ImageView ivCheckAllTime;
    private ImageView ivCheckOption1;
    private ImageView ivCheckOption2;
    private ImageView ivCheckOption3;
    private ImageView ivCheckOption4;

    private String selectedKey = KEY_MONTH_THIS;
    private TabType currentTab = TabType.MONTH;
    private LocalDate otherDay;
    private YearMonth otherMonth;
    private LocalDate customFrom;
    private LocalDate customTo;
    private int selectedRowColor;
    private int normalRowColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_filter);
        initDefaults();
        readIntentData();
        selectedRowColor = getColor(R.color.export_search_bg);
        normalRowColor = getColor(android.R.color.white);
        bindViews();
        setupToolbar();
        setupTabs();
        setupActions();
        render();
    }

    @Override
    public void finish() {
        setResult(RESULT_OK, buildResultIntent());
        super.finish();
    }

    private void initDefaults() {
        LocalDate now = LocalDate.now(DEVICE_ZONE);
        otherDay = now;
        otherMonth = YearMonth.now(DEVICE_ZONE);
        customFrom = now.withDayOfMonth(1);
        customTo = now.withDayOfMonth(now.lengthOfMonth());
    }

    private void readIntentData() {
        Intent intent = getIntent();
        if (intent == null) {
            currentTab = tabForKey(selectedKey);
            return;
        }
        String incomingKey = intent.getStringExtra(EXTRA_SELECTED_KEY);
        if (incomingKey != null && !incomingKey.isBlank()) {
            selectedKey = incomingKey;
        }
        boolean hasRange = intent.getBooleanExtra(EXTRA_HAS_RANGE, false);
        if (hasRange) {
            long start = intent.getLongExtra(EXTRA_START_EPOCH, 0L);
            long end = intent.getLongExtra(EXTRA_END_EPOCH, 0L);
            if (end > start) {
                LocalDate startDate = Instant.ofEpochSecond(start).atZone(DEVICE_ZONE).toLocalDate();
                LocalDate endDateInclusive = Instant.ofEpochSecond(end - 1L).atZone(DEVICE_ZONE).toLocalDate();
                customFrom = startDate;
                customTo = endDateInclusive;
                if (KEY_DAY_OTHER.equals(selectedKey)) {
                    otherDay = startDate;
                }
                if (KEY_MONTH_OTHER.equals(selectedKey)) {
                    otherMonth = YearMonth.from(startDate);
                }
            }
        }
        currentTab = tabForKey(selectedKey);
    }

    private void bindViews() {
        tabLayout = findViewById(R.id.tabHistoryFilterType);

        rowAllTime = findViewById(R.id.rowHistoryFilterAllTime);
        dividerAllTime = findViewById(R.id.dividerHistoryFilterAllTime);
        rowOption1 = findViewById(R.id.rowHistoryFilterOption1);
        rowOption2 = findViewById(R.id.rowHistoryFilterOption2);
        rowOption3 = findViewById(R.id.rowHistoryFilterOption3);
        rowOption4 = findViewById(R.id.rowHistoryFilterOption4);
        dividerOption1 = findViewById(R.id.dividerHistoryFilterOption1);
        dividerOption2 = findViewById(R.id.dividerHistoryFilterOption2);
        dividerOption3 = findViewById(R.id.dividerHistoryFilterOption3);
        dividerOption4 = findViewById(R.id.dividerHistoryFilterOption4);
        layoutRange = findViewById(R.id.layoutHistoryFilterRange);
        rowFrom = findViewById(R.id.rowHistoryFilterFrom);
        rowTo = findViewById(R.id.rowHistoryFilterTo);

        tvAllTime = findViewById(R.id.tvHistoryFilterAllTime);
        tvOption1 = findViewById(R.id.tvHistoryFilterOption1);
        tvOption2 = findViewById(R.id.tvHistoryFilterOption2);
        tvOption3 = findViewById(R.id.tvHistoryFilterOption3);
        tvOption4 = findViewById(R.id.tvHistoryFilterOption4);
        tvFromValue = findViewById(R.id.tvHistoryFilterFromValue);
        tvToValue = findViewById(R.id.tvHistoryFilterToValue);

        ivCheckAllTime = findViewById(R.id.ivHistoryFilterCheckAllTime);
        ivCheckOption1 = findViewById(R.id.ivHistoryFilterCheckOption1);
        ivCheckOption2 = findViewById(R.id.ivHistoryFilterCheckOption2);
        ivCheckOption3 = findViewById(R.id.ivHistoryFilterCheckOption3);
        ivCheckOption4 = findViewById(R.id.ivHistoryFilterCheckOption4);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarHistoryFilter);
        toolbar.setTitle(R.string.history_filter_page_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.label_period_day));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.label_period_week));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.label_period_month));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.label_period_quarter));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.history_filter_custom));

        TabLayout.Tab initial = tabLayout.getTabAt(indexForTab(currentTab));
        if (initial != null) {
            initial.select();
        }
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tabAt(tab.getPosition());
                ensureSelectionFitsTab();
                render();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void setupActions() {
        rowAllTime.setOnClickListener(v -> selectAndFinish(KEY_CUSTOM_ALL));
        rowOption1.setOnClickListener(v -> onOptionClicked(1));
        rowOption2.setOnClickListener(v -> onOptionClicked(2));
        rowOption3.setOnClickListener(v -> onOptionClicked(3));
        rowOption4.setOnClickListener(v -> onOptionClicked(4));
        rowFrom.setOnClickListener(v -> {
            selectedKey = KEY_CUSTOM_RANGE;
            showDatePicker(customFrom, R.string.history_filter_pick_from, value -> {
                customFrom = value;
                if (customTo.isBefore(customFrom)) {
                    customTo = customFrom;
                }
                render();
            });
        });
        rowTo.setOnClickListener(v -> {
            selectedKey = KEY_CUSTOM_RANGE;
            showDatePicker(customTo, R.string.history_filter_pick_to, value -> {
                customTo = value;
                if (customTo.isBefore(customFrom)) {
                    customFrom = customTo;
                }
                render();
            });
        });
    }

    private void selectAndFinish(String key) {
        selectedKey = key;
        finish();
    }

    private void onOptionClicked(int optionIndex) {
        if (currentTab == TabType.DAY) {
            if (optionIndex == 1) {
                selectAndFinish(KEY_DAY_TODAY);
                return;
            }
            if (optionIndex == 2) {
                selectAndFinish(KEY_DAY_YESTERDAY);
                return;
            }
            selectedKey = KEY_DAY_OTHER;
            showDatePicker(otherDay, R.string.history_filter_pick_day, value -> {
                otherDay = value;
                finish();
            });
            return;
        }
        if (currentTab == TabType.WEEK) {
            selectAndFinish(optionIndex == 1 ? KEY_WEEK_THIS : KEY_WEEK_LAST);
            return;
        }
        if (currentTab == TabType.MONTH) {
            if (optionIndex == 1) {
                selectAndFinish(KEY_MONTH_THIS);
                return;
            }
            if (optionIndex == 2) {
                selectAndFinish(KEY_MONTH_LAST);
                return;
            }
            selectedKey = KEY_MONTH_OTHER;
            LocalDate initial = otherMonth.atDay(1);
            showDatePicker(initial, R.string.history_filter_pick_month, value -> {
                otherMonth = YearMonth.of(value.getYear(), value.getMonth());
                finish();
            });
            return;
        }
        if (currentTab == TabType.QUARTER) {
            if (optionIndex == 1) {
                selectedKey = KEY_QUARTER_1;
            } else if (optionIndex == 2) {
                selectedKey = KEY_QUARTER_2;
            } else if (optionIndex == 3) {
                selectedKey = KEY_QUARTER_3;
            } else {
                selectedKey = KEY_QUARTER_4;
            }
            finish();
            return;
        }
        if (currentTab == TabType.CUSTOM) {
            selectedKey = KEY_CUSTOM_RANGE;
            render();
        }
    }

    private void showDatePicker(LocalDate initialDate, int titleRes, DateCallback callback) {
        DatePickerDialog picker = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> callback.onPicked(LocalDate.of(year, month + 1, dayOfMonth)),
            initialDate.getYear(),
            initialDate.getMonthValue() - 1,
            initialDate.getDayOfMonth()
        );
        picker.setTitle(titleRes);
        picker.show();
    }

    private void render() {
        boolean customTab = currentTab == TabType.CUSTOM;
        rowAllTime.setVisibility(customTab ? View.VISIBLE : View.GONE);
        dividerAllTime.setVisibility(customTab ? View.VISIBLE : View.GONE);
        tvAllTime.setText(R.string.history_filter_all_time);
        setRowSelected(rowAllTime, customTab && KEY_CUSTOM_ALL.equals(selectedKey));
        ivCheckAllTime.setVisibility(customTab && KEY_CUSTOM_ALL.equals(selectedKey) ? View.VISIBLE : View.INVISIBLE);

        boolean showOption2 = currentTab != TabType.CUSTOM;
        boolean showOption3 = currentTab == TabType.DAY || currentTab == TabType.MONTH || currentTab == TabType.QUARTER;
        boolean showOption4 = currentTab == TabType.QUARTER;

        rowOption2.setVisibility(showOption2 ? View.VISIBLE : View.GONE);
        dividerOption1.setVisibility(showOption2 ? View.VISIBLE : View.GONE);
        rowOption3.setVisibility(showOption3 ? View.VISIBLE : View.GONE);
        dividerOption2.setVisibility(showOption3 ? View.VISIBLE : View.GONE);
        rowOption4.setVisibility(showOption4 ? View.VISIBLE : View.GONE);
        dividerOption3.setVisibility(showOption4 ? View.VISIBLE : View.GONE);
        dividerOption4.setVisibility(showOption4 ? View.VISIBLE : View.GONE);

        if (currentTab == TabType.DAY) {
            tvOption1.setText(R.string.history_filter_day_today);
            tvOption2.setText(R.string.history_filter_day_yesterday);
            tvOption3.setText(R.string.history_filter_day_other);
        } else if (currentTab == TabType.WEEK) {
            tvOption1.setText(R.string.history_filter_week_this);
            tvOption2.setText(R.string.history_filter_week_last);
        } else if (currentTab == TabType.MONTH) {
            tvOption1.setText(R.string.history_filter_month_this);
            tvOption2.setText(R.string.history_filter_month_last);
            tvOption3.setText(R.string.history_filter_month_other);
        } else if (currentTab == TabType.QUARTER) {
            tvOption1.setText(R.string.history_filter_quarter_1);
            tvOption2.setText(R.string.history_filter_quarter_2);
            tvOption3.setText(R.string.history_filter_quarter_3);
            tvOption4.setText(R.string.history_filter_quarter_4);
        } else {
            tvOption1.setText(R.string.history_filter_custom);
        }

        setRowSelected(rowOption1, isOptionSelected(1));
        setRowSelected(rowOption2, isOptionSelected(2));
        setRowSelected(rowOption3, isOptionSelected(3));
        setRowSelected(rowOption4, isOptionSelected(4));
        ivCheckOption1.setVisibility(isOptionSelected(1) ? View.VISIBLE : View.INVISIBLE);
        ivCheckOption2.setVisibility(isOptionSelected(2) ? View.VISIBLE : View.INVISIBLE);
        ivCheckOption3.setVisibility(isOptionSelected(3) ? View.VISIBLE : View.INVISIBLE);
        ivCheckOption4.setVisibility(isOptionSelected(4) ? View.VISIBLE : View.INVISIBLE);

        boolean showRange = customTab && KEY_CUSTOM_RANGE.equals(selectedKey);
        layoutRange.setVisibility(showRange ? View.VISIBLE : View.GONE);
        rowFrom.setEnabled(showRange);
        rowTo.setEnabled(showRange);
        rowFrom.setAlpha(showRange ? 1f : 0.45f);
        rowTo.setAlpha(showRange ? 1f : 0.45f);
        tvFromValue.setText(formatDate(customFrom));
        tvToValue.setText(formatDate(customTo));
    }

    private boolean isOptionSelected(int optionIndex) {
        if (currentTab == TabType.DAY) {
            return (optionIndex == 1 && KEY_DAY_TODAY.equals(selectedKey))
                || (optionIndex == 2 && KEY_DAY_YESTERDAY.equals(selectedKey))
                || (optionIndex == 3 && KEY_DAY_OTHER.equals(selectedKey));
        }
        if (currentTab == TabType.WEEK) {
            return (optionIndex == 1 && KEY_WEEK_THIS.equals(selectedKey))
                || (optionIndex == 2 && KEY_WEEK_LAST.equals(selectedKey));
        }
        if (currentTab == TabType.MONTH) {
            return (optionIndex == 1 && KEY_MONTH_THIS.equals(selectedKey))
                || (optionIndex == 2 && KEY_MONTH_LAST.equals(selectedKey))
                || (optionIndex == 3 && KEY_MONTH_OTHER.equals(selectedKey));
        }
        if (currentTab == TabType.QUARTER) {
            return (optionIndex == 1 && KEY_QUARTER_1.equals(selectedKey))
                || (optionIndex == 2 && KEY_QUARTER_2.equals(selectedKey))
                || (optionIndex == 3 && KEY_QUARTER_3.equals(selectedKey))
                || (optionIndex == 4 && KEY_QUARTER_4.equals(selectedKey));
        }
        return optionIndex == 1 && KEY_CUSTOM_RANGE.equals(selectedKey);
    }

    private void setRowSelected(View row, boolean selected) {
        row.setBackgroundColor(selected ? selectedRowColor : normalRowColor);
    }

    private void ensureSelectionFitsTab() {
        if (currentTab == TabType.DAY && !selectedKey.startsWith("day_")) {
            selectedKey = KEY_DAY_TODAY;
        } else if (currentTab == TabType.WEEK && !selectedKey.startsWith("week_")) {
            selectedKey = KEY_WEEK_THIS;
        } else if (currentTab == TabType.MONTH && !selectedKey.startsWith("month_")) {
            selectedKey = KEY_MONTH_THIS;
        } else if (currentTab == TabType.QUARTER && !selectedKey.startsWith("quarter_")) {
            selectedKey = KEY_QUARTER_1;
        } else if (currentTab == TabType.CUSTOM && !selectedKey.startsWith("custom_")) {
            selectedKey = KEY_CUSTOM_ALL;
        }
    }

    private Intent buildResultIntent() {
        FilterSelection selection = resolveSelection();
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT_KEY, selectedKey);
        intent.putExtra(EXTRA_RESULT_LABEL, selection.label);
        intent.putExtra(EXTRA_RESULT_HAS_RANGE, selection.hasRange);
        if (selection.hasRange) {
            intent.putExtra(EXTRA_RESULT_START_EPOCH, selection.startEpochSecond);
            intent.putExtra(EXTRA_RESULT_END_EPOCH, selection.endEpochSecond);
        }
        return intent;
    }

    private FilterSelection resolveSelection() {
        LocalDate today = LocalDate.now(DEVICE_ZONE);
        int year = today.getYear();
        if (KEY_DAY_TODAY.equals(selectedKey)) {
            return rangeSelection(
                getString(R.string.history_filter_day_today),
                today,
                today.plusDays(1)
            );
        }
        if (KEY_DAY_YESTERDAY.equals(selectedKey)) {
            LocalDate start = today.minusDays(1);
            return rangeSelection(getString(R.string.history_filter_day_yesterday), start, start.plusDays(1));
        }
        if (KEY_DAY_OTHER.equals(selectedKey)) {
            return rangeSelection(formatDate(otherDay), otherDay, otherDay.plusDays(1));
        }
        if (KEY_WEEK_THIS.equals(selectedKey)) {
            LocalDate start = startOfWeek(today);
            return rangeSelection(getString(R.string.history_filter_week_this), start, start.plusWeeks(1));
        }
        if (KEY_WEEK_LAST.equals(selectedKey)) {
            LocalDate start = startOfWeek(today).minusWeeks(1);
            return rangeSelection(getString(R.string.history_filter_week_last), start, start.plusWeeks(1));
        }
        if (KEY_MONTH_THIS.equals(selectedKey)) {
            YearMonth month = YearMonth.now(DEVICE_ZONE);
            LocalDate start = month.atDay(1);
            return rangeSelection(getString(R.string.history_filter_month_this), start, start.plusMonths(1));
        }
        if (KEY_MONTH_LAST.equals(selectedKey)) {
            YearMonth month = YearMonth.now(DEVICE_ZONE).minusMonths(1);
            LocalDate start = month.atDay(1);
            return rangeSelection(getString(R.string.history_filter_month_last), start, start.plusMonths(1));
        }
        if (KEY_MONTH_OTHER.equals(selectedKey)) {
            LocalDate start = otherMonth.atDay(1);
            String label = getString(R.string.history_filter_month_format, otherMonth.getMonthValue(), otherMonth.getYear());
            return rangeSelection(label, start, start.plusMonths(1));
        }
        if (KEY_QUARTER_1.equals(selectedKey)) {
            return quarterSelection(getString(R.string.history_filter_quarter_1), year, 1);
        }
        if (KEY_QUARTER_2.equals(selectedKey)) {
            return quarterSelection(getString(R.string.history_filter_quarter_2), year, 2);
        }
        if (KEY_QUARTER_3.equals(selectedKey)) {
            return quarterSelection(getString(R.string.history_filter_quarter_3), year, 3);
        }
        if (KEY_QUARTER_4.equals(selectedKey)) {
            return quarterSelection(getString(R.string.history_filter_quarter_4), year, 4);
        }
        if (KEY_CUSTOM_RANGE.equals(selectedKey)) {
            LocalDate from = customFrom;
            LocalDate to = customTo;
            if (to.isBefore(from)) {
                LocalDate swap = from;
                from = to;
                to = swap;
            }
            String label = getString(R.string.history_filter_range_format, formatDate(from), formatDate(to));
            return rangeSelection(label, from, to.plusDays(1));
        }
        return allTimeSelection();
    }

    private FilterSelection quarterSelection(String label, int year, int quarter) {
        int startMonth = ((quarter - 1) * 3) + 1;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        return rangeSelection(label, start, start.plusMonths(3));
    }

    private FilterSelection allTimeSelection() {
        return new FilterSelection(false, 0L, 0L, getString(R.string.history_filter_all_time));
    }

    private FilterSelection rangeSelection(String label, LocalDate start, LocalDate endExclusive) {
        long startEpoch = start.atStartOfDay(DEVICE_ZONE).toEpochSecond();
        long endEpoch = endExclusive.atStartOfDay(DEVICE_ZONE).toEpochSecond();
        return new FilterSelection(true, startEpoch, endEpoch, label);
    }

    private LocalDate startOfWeek(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    private String formatDate(LocalDate date) {
        return DATE_FORMATTER.format(date);
    }

    private TabType tabForKey(String key) {
        if (key == null) {
            return TabType.MONTH;
        }
        if (key.startsWith("day_")) {
            return TabType.DAY;
        }
        if (key.startsWith("week_")) {
            return TabType.WEEK;
        }
        if (key.startsWith("quarter_")) {
            return TabType.QUARTER;
        }
        if (key.startsWith("custom_")) {
            return TabType.CUSTOM;
        }
        return TabType.MONTH;
    }

    private TabType tabAt(int index) {
        if (index == 0) {
            return TabType.DAY;
        }
        if (index == 1) {
            return TabType.WEEK;
        }
        if (index == 2) {
            return TabType.MONTH;
        }
        if (index == 3) {
            return TabType.QUARTER;
        }
        return TabType.CUSTOM;
    }

    private int indexForTab(TabType tab) {
        if (tab == TabType.DAY) {
            return 0;
        }
        if (tab == TabType.WEEK) {
            return 1;
        }
        if (tab == TabType.MONTH) {
            return 2;
        }
        if (tab == TabType.QUARTER) {
            return 3;
        }
        return 4;
    }

    private static class FilterSelection {
        final boolean hasRange;
        final long startEpochSecond;
        final long endEpochSecond;
        final String label;

        FilterSelection(boolean hasRange, long startEpochSecond, long endEpochSecond, String label) {
            this.hasRange = hasRange;
            this.startEpochSecond = startEpochSecond;
            this.endEpochSecond = endEpochSecond;
            this.label = label;
        }
    }
}
