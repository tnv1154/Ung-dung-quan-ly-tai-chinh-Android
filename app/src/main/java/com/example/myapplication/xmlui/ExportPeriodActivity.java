package com.example.myapplication.xmlui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.finance.ui.ExportPeriod;
import com.example.myapplication.finance.ui.ExportPeriodUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.time.LocalDate;

public class ExportPeriodActivity extends AppCompatActivity {
    private ExportPeriod selectedPeriod = ExportPeriod.THIS_MONTH;
    private LocalDate customStartDate = LocalDate.now();
    private LocalDate customEndDate = LocalDate.now();

    private View rowThisMonth;
    private View rowLastMonth;
    private View rowThisQuarter;
    private View rowLastQuarter;
    private View rowThisYear;
    private View rowLastYear;
    private View rowCustom;
    private View customContainer;
    private TextView tvCustomFrom;
    private TextView tvCustomTo;
    private View ivCheckThisMonth;
    private View ivCheckLastMonth;
    private View ivCheckThisQuarter;
    private View ivCheckLastQuarter;
    private View ivCheckThisYear;
    private View ivCheckLastYear;
    private View ivCheckCustom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_period);
        readIntentData();
        bindViews();
        setupToolbar();
        setupActions();
        updateUi();
    }

    private void readIntentData() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        selectedPeriod = ExportPeriodUtils.parseOrDefault(
            intent.getStringExtra(ExportIntentKeys.EXTRA_EXPORT_PERIOD),
            ExportPeriod.THIS_MONTH
        );
        customStartDate = parseDate(
            intent.getStringExtra(ExportIntentKeys.EXTRA_EXPORT_CUSTOM_START),
            customStartDate
        );
        customEndDate = parseDate(
            intent.getStringExtra(ExportIntentKeys.EXTRA_EXPORT_CUSTOM_END),
            customEndDate
        );
        if (customEndDate.isBefore(customStartDate)) {
            LocalDate swap = customStartDate;
            customStartDate = customEndDate;
            customEndDate = swap;
        }
    }

    private void bindViews() {
        rowThisMonth = findViewById(R.id.rowExportPeriodThisMonth);
        rowLastMonth = findViewById(R.id.rowExportPeriodLastMonth);
        rowThisQuarter = findViewById(R.id.rowExportPeriodThisQuarter);
        rowLastQuarter = findViewById(R.id.rowExportPeriodLastQuarter);
        rowThisYear = findViewById(R.id.rowExportPeriodThisYear);
        rowLastYear = findViewById(R.id.rowExportPeriodLastYear);
        rowCustom = findViewById(R.id.rowExportPeriodCustom);
        customContainer = findViewById(R.id.layoutExportCustomDateRange);
        tvCustomFrom = findViewById(R.id.tvExportCustomFromValue);
        tvCustomTo = findViewById(R.id.tvExportCustomToValue);
        ivCheckThisMonth = findViewById(R.id.ivExportCheckThisMonth);
        ivCheckLastMonth = findViewById(R.id.ivExportCheckLastMonth);
        ivCheckThisQuarter = findViewById(R.id.ivExportCheckThisQuarter);
        ivCheckLastQuarter = findViewById(R.id.ivExportCheckLastQuarter);
        ivCheckThisYear = findViewById(R.id.ivExportCheckThisYear);
        ivCheckLastYear = findViewById(R.id.ivExportCheckLastYear);
        ivCheckCustom = findViewById(R.id.ivExportCheckCustom);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarExportPeriod);
        toolbar.setTitle(R.string.export_period_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);
    }

    private boolean onToolbarMenuItemClick(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.actionExportPeriodConfirm) {
            submitSelection();
            return true;
        }
        return false;
    }

    private void setupActions() {
        rowThisMonth.setOnClickListener(v -> selectPeriod(ExportPeriod.THIS_MONTH));
        rowLastMonth.setOnClickListener(v -> selectPeriod(ExportPeriod.LAST_MONTH));
        rowThisQuarter.setOnClickListener(v -> selectPeriod(ExportPeriod.THIS_QUARTER));
        rowLastQuarter.setOnClickListener(v -> selectPeriod(ExportPeriod.LAST_QUARTER));
        rowThisYear.setOnClickListener(v -> selectPeriod(ExportPeriod.THIS_YEAR));
        rowLastYear.setOnClickListener(v -> selectPeriod(ExportPeriod.LAST_YEAR));
        rowCustom.setOnClickListener(v -> selectPeriod(ExportPeriod.CUSTOM));

        findViewById(R.id.rowExportCustomFrom).setOnClickListener(v -> openDatePicker(true));
        findViewById(R.id.rowExportCustomTo).setOnClickListener(v -> openDatePicker(false));
    }

    private void selectPeriod(ExportPeriod period) {
        selectedPeriod = period;
        if (selectedPeriod == ExportPeriod.CUSTOM && customEndDate.isBefore(customStartDate)) {
            customEndDate = customStartDate;
        }
        updateUi();
    }

    private void updateUi() {
        boolean isCustom = selectedPeriod == ExportPeriod.CUSTOM;
        ivCheckThisMonth.setVisibility(selectedPeriod == ExportPeriod.THIS_MONTH ? View.VISIBLE : View.INVISIBLE);
        ivCheckLastMonth.setVisibility(selectedPeriod == ExportPeriod.LAST_MONTH ? View.VISIBLE : View.INVISIBLE);
        ivCheckThisQuarter.setVisibility(selectedPeriod == ExportPeriod.THIS_QUARTER ? View.VISIBLE : View.INVISIBLE);
        ivCheckLastQuarter.setVisibility(selectedPeriod == ExportPeriod.LAST_QUARTER ? View.VISIBLE : View.INVISIBLE);
        ivCheckThisYear.setVisibility(selectedPeriod == ExportPeriod.THIS_YEAR ? View.VISIBLE : View.INVISIBLE);
        ivCheckLastYear.setVisibility(selectedPeriod == ExportPeriod.LAST_YEAR ? View.VISIBLE : View.INVISIBLE);
        ivCheckCustom.setVisibility(isCustom ? View.VISIBLE : View.INVISIBLE);
        customContainer.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        tvCustomFrom.setText(ExportPeriodUtils.formatDisplayDate(customStartDate));
        tvCustomTo.setText(ExportPeriodUtils.formatDisplayDate(customEndDate));
    }

    private void openDatePicker(boolean pickStartDate) {
        LocalDate selected = pickStartDate ? customStartDate : customEndDate;
        DatePickerDialog picker = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                LocalDate value = LocalDate.of(year, month + 1, dayOfMonth);
                if (pickStartDate) {
                    customStartDate = value;
                    if (customEndDate.isBefore(customStartDate)) {
                        customEndDate = customStartDate;
                    }
                } else {
                    customEndDate = value;
                    if (customEndDate.isBefore(customStartDate)) {
                        customStartDate = customEndDate;
                    }
                }
                updateUi();
            },
            selected.getYear(),
            selected.getMonthValue() - 1,
            selected.getDayOfMonth()
        );
        picker.show();
    }

    private void submitSelection() {
        if (selectedPeriod == ExportPeriod.CUSTOM && customEndDate.isBefore(customStartDate)) {
            Toast.makeText(this, R.string.export_period_invalid_range, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra(ExportIntentKeys.EXTRA_EXPORT_PERIOD, selectedPeriod.name());
        result.putExtra(ExportIntentKeys.EXTRA_EXPORT_CUSTOM_START, ExportPeriodUtils.formatDisplayDate(customStartDate));
        result.putExtra(ExportIntentKeys.EXTRA_EXPORT_CUSTOM_END, ExportPeriodUtils.formatDisplayDate(customEndDate));
        setResult(RESULT_OK, result);
        finish();
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            String[] parts = raw.split("/");
            if (parts.length != 3) {
                return fallback;
            }
            return LocalDate.of(
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[0])
            );
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

