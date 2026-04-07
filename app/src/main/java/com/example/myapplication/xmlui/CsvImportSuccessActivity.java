package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class CsvImportSuccessActivity extends AppCompatActivity {
    public static final String EXTRA_IMPORTED_COUNT = "extra_imported_count";
    public static final String EXTRA_SKIPPED_COUNT = "extra_skipped_count";
    public static final String EXTRA_TOTAL_INCOME = "extra_total_income";
    public static final String EXTRA_TOTAL_EXPENSE = "extra_total_expense";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_import_success);
        setupBottomNavigation();
        bindSummary();
        setupActions();
    }

    private void bindSummary() {
        int importedCount = getIntent().getIntExtra(EXTRA_IMPORTED_COUNT, 0);
        double totalIncome = getIntent().getDoubleExtra(EXTRA_TOTAL_INCOME, 0.0);
        double totalExpense = getIntent().getDoubleExtra(EXTRA_TOTAL_EXPENSE, 0.0);

        TextView tvMessage = findViewById(R.id.tvCsvImportSuccessMessage);
        TextView tvIncome = findViewById(R.id.tvCsvImportSuccessIncomeValue);
        TextView tvExpense = findViewById(R.id.tvCsvImportSuccessExpenseValue);

        tvMessage.setText(getString(R.string.csv_import_success_subtitle, importedCount));
        tvIncome.setText("+" + UiFormatters.moneyRaw(totalIncome));
        tvExpense.setText("-" + UiFormatters.moneyRaw(totalExpense));
    }

    private void setupActions() {
        MaterialButton btnHome = findViewById(R.id.btnCsvImportGoHome);
        MaterialButton btnHistory = findViewById(R.id.btnCsvImportViewHistory);

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, OverviewActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.putExtra(HistoryActivity.EXTRA_SOURCE_NAV_ITEM_ID, R.id.nav_more);
            startActivity(intent);
            finish();
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_more);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_overview) {
                startActivity(new Intent(this, OverviewActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_accounts) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_add) {
                Intent addIntent = new Intent(this, AddTransactionActivity.class);
                addIntent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_EXPENSE);
                startActivity(addIntent);
                return false;
            }
            if (id == R.id.nav_report) {
                startActivity(new Intent(this, ReportActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_more) {
                startActivity(new Intent(this, MoreActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }
}
