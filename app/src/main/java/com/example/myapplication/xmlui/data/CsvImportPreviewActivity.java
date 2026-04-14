package com.example.myapplication.xmlui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.CsvImportRow;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.CsvImportSummary;
import com.example.myapplication.finance.ui.CsvParseResult;
import com.example.myapplication.finance.ui.FinanceParsersKt;
import com.example.myapplication.finance.ui.FinanceTimeAndIoKt;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.firebase.Timestamp;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvImportPreviewActivity extends AppCompatActivity {
    private ActivityResultLauncher<Intent> editRowLauncher;

    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;

    private Uri csvFileUri;
    private String csvFileName;
    private String rawCsvContent;
    private CsvParseResult parseResult;
    private final List<CsvImportRow> previewRows = new ArrayList<>();
    private final List<Wallet> availableWallets = new ArrayList<>();
    private boolean hasManualEdits;
    private int editingRowIndex = RecyclerView.NO_POSITION;

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
        setupRowEditorLauncher();
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
        previewAdapter = new CsvImportPreviewAdapter(this::openEditRow);
        recyclerView.setAdapter(previewAdapter);
    }

    private void setupActions() {
        btnCancel.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> confirmImport());
    }

    private void setupRowEditorLauncher() {
        editRowLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int targetIndex = editingRowIndex;
                editingRowIndex = RecyclerView.NO_POSITION;
                if (targetIndex < 0 || targetIndex >= previewRows.size()) {
                    return;
                }
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                CsvImportRow oldRow = previewRows.get(targetIndex);
                CsvImportRow updatedRow = buildEditedRowFromResult(
                    oldRow == null ? targetIndex + 1 : oldRow.getRowNumber(),
                    result.getData()
                );
                if (updatedRow == null) {
                    Toast.makeText(this, R.string.csv_import_preview_edit_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                previewRows.set(targetIndex, updatedRow);
                hasManualEdits = true;
                renderPreviewRows();
            }
        );
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
        availableWallets.clear();
        availableWallets.addAll(state.getWallets());
        if (!hasManualEdits) {
            parseResult = FinanceParsersKt.parseCsvImportRows(rawCsvContent, availableWallets);
            previewRows.clear();
            if (parseResult != null && !parseResult.hasErrorMessage()) {
                previewRows.addAll(parseResult.getRows());
            }
        }
        renderPreviewRows();
    }

    private void renderPreviewRows() {
        previewAdapter.submit(previewRows);
        tvFoundCount.setText(getString(R.string.csv_import_preview_found_count, previewRows.size()));

        if (parseResult != null && parseResult.hasErrorMessage() && previewRows.isEmpty()) {
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            tvPreviewError.setText(parseResult.getErrorMessage());
            btnConfirm.setEnabled(false);
            return;
        }

        int validRows = countValidRows(previewRows);
        int invalidRows = Math.max(0, previewRows.size() - validRows);

        if (validRows <= 0) {
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            tvPreviewError.setText(R.string.csv_import_preview_no_valid_rows);
            btnConfirm.setEnabled(false);
            return;
        }

        if (invalidRows > 0) {
            tvPreviewError.setVisibility(android.view.View.VISIBLE);
            tvPreviewError.setText(getString(R.string.csv_import_preview_has_invalid_rows, invalidRows));
        } else {
            tvPreviewError.setVisibility(android.view.View.GONE);
            tvPreviewError.setText("");
        }
        btnConfirm.setEnabled(!importing);
    }

    private void confirmImport() {
        if (financeViewModel == null || importing) {
            return;
        }
        List<CsvImportRow> validRows = collectValidRows();
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
        intent.putExtra(
            CsvImportSuccessActivity.EXTRA_SKIPPED_COUNT,
            summary.getSkippedCount() + countInvalidRows(previewRows)
        );
        intent.putExtra(CsvImportSuccessActivity.EXTRA_TOTAL_INCOME, summary.getTotalIncome());
        intent.putExtra(CsvImportSuccessActivity.EXTRA_TOTAL_EXPENSE, summary.getTotalExpense());
        startActivity(intent);
        finish();
    }

    private void openEditRow(int position, CsvImportRow row) {
        if (row == null || importing) {
            return;
        }
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra(AddTransactionActivity.EXTRA_PREVIEW_EDIT_ONLY, true);
        intent.putExtra(
            AddTransactionActivity.EXTRA_PREFILL_MODE,
            row.getType() == TransactionType.INCOME ? AddTransactionActivity.MODE_INCOME : AddTransactionActivity.MODE_EXPENSE
        );
        String walletId = resolveWalletId(row);
        if (walletId != null && !walletId.isBlank()) {
            intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_SOURCE_WALLET_ID, walletId);
        }
        if (row.getAmount() > 0.0) {
            intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_AMOUNT, row.getAmount());
        }
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_NOTE, row.getNote());
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_CATEGORY_NAME, row.getCategory());
        long createdAtMillis = timestampToMillis(row.getTransactionCreatedAt());
        if (createdAtMillis > 0L) {
            intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_TIME_MILLIS, createdAtMillis);
        }
        editingRowIndex = position;
        editRowLauncher.launch(intent);
    }

    private CsvImportRow buildEditedRowFromResult(int rowNumber, Intent data) {
        if (data == null) {
            return null;
        }
        String mode = data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_MODE);
        TransactionType type = parsePreviewType(mode);
        if (type == null) {
            return new CsvImportRow(
                rowNumber,
                null,
                0.0,
                Timestamp.now(),
                "",
                "",
                "",
                "",
                null,
                false,
                getString(R.string.csv_import_preview_edit_type_not_supported)
            );
        }

        String walletId = data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_SOURCE_WALLET_ID);
        Wallet wallet = findWalletById(walletId);
        double amount = data.getDoubleExtra(AddTransactionActivity.EXTRA_RESULT_AMOUNT, 0.0);
        String category = safe(data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_CATEGORY_NAME)).trim();
        if (category.isEmpty()) {
            category = type == TransactionType.INCOME
                ? getString(R.string.transaction_type_income)
                : getString(R.string.default_category_other);
        }
        String note = safe(data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_NOTE)).trim();
        long createdAtMillis = data.getLongExtra(AddTransactionActivity.EXTRA_RESULT_TIME_MILLIS, 0L);
        Timestamp createdAt = createdAtMillis > 0L ? new Timestamp(new Date(createdAtMillis)) : Timestamp.now();

        String currencyCode = wallet == null ? "" : normalizeCurrency(wallet.getCurrency());
        String walletName = wallet == null ? "" : safe(wallet.getName()).trim();

        String validationMessage = "";
        if (amount <= 0.0) {
            validationMessage = "Số tiền thu/chi không hợp lệ";
        } else if (wallet == null) {
            validationMessage = "Không tìm thấy tài khoản tương ứng";
        } else if (walletName.isEmpty()) {
            validationMessage = "Thiếu tài khoản";
        } else if (currencyCode.isEmpty()) {
            validationMessage = "Thiếu loại tiền tệ";
        }

        return new CsvImportRow(
            rowNumber,
            type,
            amount,
            createdAt,
            currencyCode,
            category,
            note,
            walletName,
            wallet == null ? null : wallet.getId(),
            validationMessage.isEmpty(),
            validationMessage
        );
    }

    private TransactionType parsePreviewType(String mode) {
        if (AddTransactionActivity.MODE_INCOME.equalsIgnoreCase(mode)) {
            return TransactionType.INCOME;
        }
        if (AddTransactionActivity.MODE_EXPENSE.equalsIgnoreCase(mode)) {
            return TransactionType.EXPENSE;
        }
        return null;
    }

    private Wallet findWalletById(String walletId) {
        if (walletId == null || walletId.isBlank()) {
            return null;
        }
        for (Wallet wallet : availableWallets) {
            if (walletId.equals(wallet.getId())) {
                return wallet;
            }
        }
        return null;
    }

    private String resolveWalletId(CsvImportRow row) {
        if (row == null) {
            return null;
        }
        if (row.getWalletId() != null && !row.getWalletId().isBlank()) {
            return row.getWalletId();
        }
        String walletName = safe(row.getWalletName()).trim();
        if (walletName.isEmpty()) {
            return null;
        }
        for (Wallet wallet : availableWallets) {
            if (walletName.equalsIgnoreCase(safe(wallet.getName()).trim())) {
                return wallet.getId();
            }
        }
        return null;
    }

    private List<CsvImportRow> collectValidRows() {
        List<CsvImportRow> rows = new ArrayList<>();
        for (CsvImportRow row : previewRows) {
            if (row != null && row.isValid()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private int countValidRows(List<CsvImportRow> rows) {
        int count = 0;
        for (CsvImportRow row : rows) {
            if (row != null && row.isValid()) {
                count++;
            }
        }
        return count;
    }

    private int countInvalidRows(List<CsvImportRow> rows) {
        int valid = countValidRows(rows);
        return Math.max(0, rows.size() - valid);
    }

    private long timestampToMillis(Timestamp timestamp) {
        if (timestamp == null) {
            return 0L;
        }
        return timestamp.getSeconds() * 1000L + (timestamp.getNanoseconds() / 1_000_000L);
    }

    private String normalizeCurrency(String raw) {
        String value = safe(raw).trim().toUpperCase(Locale.ROOT);
        return value;
    }

    private String safe(String raw) {
        return raw == null ? "" : raw;
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
