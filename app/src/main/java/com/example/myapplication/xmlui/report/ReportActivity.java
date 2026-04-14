package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.views.ReportDonutChartView;
import com.example.myapplication.xmlui.views.ReportTrendChartView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class ReportActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        setupTopBar();
        setupCardPreviews();
        setupEntryCards();
        setupBottomNavigation();
        setupSession();
    }

    private void setupTopBar() {
        findViewById(R.id.btnReportBack).setOnClickListener(v -> finish());
    }

    private void setupEntryCards() {
        findViewById(R.id.cardReportSpendingOverview).setOnClickListener(v -> {
            startActivity(new Intent(this, MonthlyReportActivity.class));
        });
        findViewById(R.id.cardReportCategoryAnalysis).setOnClickListener(v -> {
            startActivity(new Intent(this, CategoryAnalysisActivity.class));
        });
        findViewById(R.id.cardReportFinancialTrend).setOnClickListener(v -> {
            startActivity(new Intent(this, FinancialTrendActivity.class));
        });
    }

    private void setupCardPreviews() {
        ReportDonutChartView donutPreview = findViewById(R.id.previewCategoryDonut);
        List<Float> donutValues = new ArrayList<>();
        donutValues.add(45f);
        donutValues.add(25f);
        donutValues.add(20f);
        donutValues.add(10f);
        List<Integer> donutColors = new ArrayList<>();
        donutColors.add(getColor(R.color.blue_primary));
        donutColors.add(getColor(R.color.group_cash_tint));
        donutColors.add(getColor(R.color.warning_orange));
        donutColors.add(getColor(R.color.expense_red));
        donutPreview.setSegments(donutValues, donutColors);

        ReportTrendChartView trendPreview = findViewById(R.id.previewTrendChart);
        List<ReportTrendChartView.Entry> trendEntries = new ArrayList<>();
        trendEntries.add(new ReportTrendChartView.Entry("Th 6", 12, 15));
        trendEntries.add(new ReportTrendChartView.Entry("Th 7", 20, 13));
        trendEntries.add(new ReportTrendChartView.Entry("Th 8", 28, 10));
        trendEntries.add(new ReportTrendChartView.Entry("Th 9", 14, 7));
        trendEntries.add(new ReportTrendChartView.Entry("Th 10", 33, 18));
        trendPreview.setEntries(trendEntries);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_report);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_report) {
                return true;
            }
            if (id == R.id.nav_accounts) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_overview) {
                startActivity(new Intent(this, OverviewActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_add) {
                Intent addIntent = new Intent(this, AddTransactionActivity.class);
                addIntent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_EXPENSE);
                startActivity(addIntent);
                return false;
            }
            if (id == R.id.nav_more) {
                startActivity(new Intent(this, MoreActivity.class));
                finish();
                return true;
            }
            Toast.makeText(this, R.string.message_feature_in_progress, Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    private void setupSession() {
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        sessionViewModel.getUiStateLiveData().observe(this, this::renderSessionState);
    }

    private void renderSessionState(@NonNull SessionUiState state) {
        if (state.getCurrentUser() == null) {
            goToAuth();
        }
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
