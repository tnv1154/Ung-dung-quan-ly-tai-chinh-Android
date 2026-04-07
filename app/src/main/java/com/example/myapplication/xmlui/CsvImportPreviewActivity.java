package com.example.myapplication.xmlui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.ui.CsvImportSummary;
import com.example.myapplication.finance.ui.CsvParseResult;
import com.example.myapplication.finance.ui.FinanceParsersKt;
import com.example.myapplication.finance.ui.FinanceTimeAndIoKt;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class CsvImportPreviewActivity extends AppCompatActivity {
    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private Uri csvFileUri;
    private String csvFileName;
    private String rawCsvContent;
    private CsvParseResult parseResult;

    private TextView tvFoundCount;
    private TextView tvFileName;
    private TextView tvPreviewError;
    private MaterialButton btnConfirm;
    private MaterialButton btnCancel;
    private CsvImportPreviewAdapter previewAdapter;
    private boolean importing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_import_preview);
        readIntentData();
        bindViews();
        setupToolbar();
        setupBottomNavigation();
        setupList();
        setupActions();
        loadRawCsv();
        setupSession();
    }

    private void readIntentData() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String uriRaw = intent.getStringExtra(CsvImportActivity.EXTRA_CSV_URI);
        if (uriRaw != null && !uriRaw.trim().isEmpty()) {
            csvFileUri = Uri.parse(uriRaw);
        }
        csvFileName = intent.getStringExtra(CsvImportActivity.EXTRA_CSV_FILE_NAME);
    }

    private void bindViews() {
        tvFoundCount = findViewById(R.id.tvCsvPreviewFoundCount);
        tvFileName = findViewById(R.id.tvCsvPreviewFileName);
        tvPreviewError = findViewById(R.id.tvCsvPreviewError);
        btnConfirm = findViewById(R.id.btnCsvConfirmImport);
        btnCancel = findViewById(R.id.btnCsvCancelImport);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarCsvPreview);
        toolbar.setTitle(R.string.csv_import_preview_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
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

    private void setupList() {
        RecyclerView recyclerView = findViewById(R.id.rvCsvPreviewRows);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        previewAdapter = new CsvImportPreviewAdapter();
        recyclerView.setAdapter(previewAdapter);
    }

    private void setupActions() {
        btnCancel.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> confirmImport());
    }

    private void loadRawCsv() {
        if (csvFileUri == null) {
            tvPreviewError.setText(R.string.csv_import_preview_error_open_file);
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            btnConfirm.setEnabled(false);
            return;
        }
        try {
            rawCsvContent = FinanceTimeAndIoKt.readTextFromUri(this, csvFileUri);
        } catch (RuntimeException error) {
            rawCsvContent = null;
        }
        tvFileName.setText(csvFileName == null ? "" : csvFileName);
        if (rawCsvContent == null || rawCsvContent.trim().isEmpty()) {
            tvPreviewError.setText(R.string.csv_import_preview_error_open_file);
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            btnConfirm.setEnabled(false);
        }
    }

    private void setupSession() {
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        sessionViewModel.getUiStateLiveData().observe(this, this::renderSessionState);
    }

    private void renderSessionState(@NonNull SessionUiState state) {
        if (state.getCurrentUser() == null) {
            goToAuth();
            return;
        }
        String userId = state.getCurrentUser().getUid();
        if (observedUserId != null && observedUserId.equals(userId)) {
            return;
        }
        observedUserId = userId;
        FinanceViewModelFactory factory = new FinanceViewModelFactory(new FirestoreFinanceRepository(), userId);
        financeViewModel = new ViewModelProvider(this, factory).get(FinanceViewModel.class);
        financeViewModel.getUiStateLiveData().observe(this, this::renderFinanceState);
    }

    private void renderFinanceState(@NonNull FinanceUiState state) {
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        }
        refreshPreview(state);
    }

    private void refreshPreview(FinanceUiState state) {
        if (state == null || rawCsvContent == null || rawCsvContent.trim().isEmpty()) {
            btnConfirm.setEnabled(false);
            return;
        }
        parseResult = FinanceParsersKt.parseCsvImportRows(rawCsvContent, state.getWallets());
        previewAdapter.submit(parseResult.getRows());
        tvFoundCount.setText(getString(R.string.csv_import_preview_found_count, parseResult.getRows().size()));

        if (parseResult.hasErrorMessage()) {
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            tvPreviewError.setText(parseResult.getErrorMessage());
            btnConfirm.setEnabled(false);
            return;
        }

        if (parseResult.getValidRows() <= 0) {
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            tvPreviewError.setText(R.string.csv_import_preview_no_valid_rows);
            btnConfirm.setEnabled(false);
            return;
        }

        if (parseResult.getSkippedRows() > 0) {
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            tvPreviewError.setText(getString(R.string.csv_import_preview_has_invalid_rows, parseResult.getSkippedRows()));
        } else {
            tvPreviewError.setVisibility(android.view.View.GONE);
            tvPreviewError.setText("");
        }
        btnConfirm.setEnabled(!importing);
    }

    private void confirmImport() {
        if (financeViewModel == null || parseResult == null || importing) {
            return;
        }
        java.util.List<com.example.myapplication.finance.model.CsvImportRow> validRows = parseResult.getValidImportRows();
        if (validRows.isEmpty()) {
            Toast.makeText(this, R.string.csv_import_preview_no_valid_rows, Toast.LENGTH_SHORT).show();
            return;
        }

        importing = true;
        btnConfirm.setEnabled(false);
        btnCancel.setEnabled(false);
        financeViewModel.importCsvRows(validRows, summary -> runOnUiThread(() -> onImportCompleted(summary)));
    }

    private void onImportCompleted(CsvImportSummary summary) {
        importing = false;
        btnCancel.setEnabled(true);
        if (summary == null || summary.getSuccessCount() <= 0) {
            btnConfirm.setEnabled(true);
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            tvPreviewError.setText(
                summary != null && summary.hasError()
                    ? summary.getErrorMessage()
                    : getString(R.string.csv_import_preview_import_failed)
            );
            return;
        }

        Intent intent = new Intent(this, CsvImportSuccessActivity.class);
        intent.putExtra(CsvImportSuccessActivity.EXTRA_IMPORTED_COUNT, summary.getSuccessCount());
        intent.putExtra(CsvImportSuccessActivity.EXTRA_SKIPPED_COUNT, summary.getSkippedCount() + parseResult.getSkippedRows());
        intent.putExtra(CsvImportSuccessActivity.EXTRA_TOTAL_INCOME, summary.getTotalIncome());
        intent.putExtra(CsvImportSuccessActivity.EXTRA_TOTAL_EXPENSE, summary.getTotalExpense());
        startActivity(intent);
        finish();
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
