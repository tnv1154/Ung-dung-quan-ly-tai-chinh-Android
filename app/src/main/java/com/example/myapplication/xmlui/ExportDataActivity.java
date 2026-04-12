package com.example.myapplication.xmlui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExportRecordRow;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.finance.ui.ExportFileFormat;
import com.example.myapplication.finance.ui.ExportPeriod;
import com.example.myapplication.finance.ui.ExportPeriodUtils;
import com.example.myapplication.finance.ui.FinanceExportBuilder;
import com.example.myapplication.finance.ui.FinanceExportWriter;
import com.example.myapplication.finance.ui.FinanceUiState;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ExportDataActivity extends AppCompatActivity {
    private SessionViewModel sessionViewModel;
    private FinanceViewModel financeViewModel;
    private String observedUserId;
    private FinanceUiState latestState = new FinanceUiState();

    private TextView tvPeriodValue;
    private TextView tvWalletValue;
    private MaterialCardView cardExcel;
    private MaterialCardView cardCsv;
    private MaterialCardView cardPdf;

    private ExportPeriod selectedPeriod = ExportPeriod.THIS_MONTH;
    private LocalDate customStartDate;
    private LocalDate customEndDate;
    private final Set<String> selectedWalletIds = new LinkedHashSet<>();
    private ExportFileFormat selectedFormat = ExportFileFormat.CSV;

    private ExportFileFormat pendingExportFormat = null;
    private List<ExportRecordRow> pendingExportRows = Collections.emptyList();
    private String pendingExportTitle = "";
    private boolean exporting = false;

    private ActivityResultLauncher<Intent> periodPickerLauncher;
    private ActivityResultLauncher<Intent> walletPickerLauncher;
    private ActivityResultLauncher<Intent> createDocumentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_data);
        ExportPeriodUtils.DateRange initialRange = ExportPeriodUtils.resolveRange(selectedPeriod, null, null);
        customStartDate = initialRange.getStartDate();
        customEndDate = initialRange.getEndDate();
        bindViews();
        setupLaunchers();
        setupToolbar();
        setupBottomNavigation();
        setupActions();
        setupSession();
        updateSummaryUi();
        updateFormatCards();
    }

    private void bindViews() {
        tvPeriodValue = findViewById(R.id.tvExportPeriodValue);
        tvWalletValue = findViewById(R.id.tvExportWalletValue);
        cardExcel = findViewById(R.id.cardExportFormatExcel);
        cardCsv = findViewById(R.id.cardExportFormatCsv);
        cardPdf = findViewById(R.id.cardExportFormatPdf);
    }

    private void setupLaunchers() {
        periodPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Intent data = result.getData();
                selectedPeriod = ExportPeriodUtils.parseOrDefault(
                    data.getStringExtra(ExportIntentKeys.EXTRA_EXPORT_PERIOD),
                    selectedPeriod
                );
                customStartDate = parseDate(data.getStringExtra(ExportIntentKeys.EXTRA_EXPORT_CUSTOM_START), customStartDate);
                customEndDate = parseDate(data.getStringExtra(ExportIntentKeys.EXTRA_EXPORT_CUSTOM_END), customEndDate);
                updateSummaryUi();
            }
        );

        walletPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Intent data = result.getData();
                ArrayList<String> selected = data.getStringArrayListExtra(ExportIntentKeys.EXTRA_EXPORT_SELECTED_WALLET_IDS);
                selectedWalletIds.clear();
                if (selected != null) {
                    selectedWalletIds.addAll(selected);
                }
                updateSummaryUi();
            }
        );

        createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null || result.getData().getData() == null) {
                    return;
                }
                Uri uri = result.getData().getData();
                int flags = result.getData().getFlags();
                try {
                    if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                        getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    }
                    if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                        getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );
                    }
                } catch (SecurityException ignored) {
                }
                exportToUri(uri);
            }
        );
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarExportData);
        toolbar.setTitle(R.string.export_data_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.actionExportHelp) {
                showHelpDialog();
                return true;
            }
            return false;
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

    private void setupActions() {
        findViewById(R.id.rowExportPeriod).setOnClickListener(v -> openPeriodPicker());
        findViewById(R.id.rowExportWallet).setOnClickListener(v -> openWalletPicker());
        cardExcel.setOnClickListener(v -> onFormatSelected(ExportFileFormat.EXCEL));
        cardCsv.setOnClickListener(v -> onFormatSelected(ExportFileFormat.CSV));
        cardPdf.setOnClickListener(v -> onFormatSelected(ExportFileFormat.PDF));
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
        latestState = state;
        if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
            Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
            financeViewModel.clearError();
        }
        removeInvalidWalletIds();
        updateSummaryUi();
    }

    private void removeInvalidWalletIds() {
        if (latestState == null) {
            return;
        }
        Set<String> validIds = new LinkedHashSet<>();
        for (Wallet wallet : latestState.getWallets()) {
            validIds.add(wallet.getId());
        }
        selectedWalletIds.retainAll(validIds);
    }

    private void openPeriodPicker() {
        Intent intent = new Intent(this, ExportPeriodActivity.class);
        intent.putExtra(ExportIntentKeys.EXTRA_EXPORT_PERIOD, selectedPeriod.name());
        intent.putExtra(ExportIntentKeys.EXTRA_EXPORT_CUSTOM_START, formatDate(customStartDate));
        intent.putExtra(ExportIntentKeys.EXTRA_EXPORT_CUSTOM_END, formatDate(customEndDate));
        periodPickerLauncher.launch(intent);
    }

    private void openWalletPicker() {
        Intent intent = new Intent(this, ExportWalletPickerActivity.class);
        intent.putStringArrayListExtra(
            ExportIntentKeys.EXTRA_EXPORT_SELECTED_WALLET_IDS,
            new ArrayList<>(selectedWalletIds)
        );
        walletPickerLauncher.launch(intent);
    }

    private void onFormatSelected(ExportFileFormat format) {
        if (exporting) {
            return;
        }
        selectedFormat = format;
        updateFormatCards();
        requestCreateDocument(format);
    }

    private void requestCreateDocument(ExportFileFormat format) {
        Set<String> walletIds = selectedWalletIds.isEmpty() ? buildAllWalletIds() : new LinkedHashSet<>(selectedWalletIds);
        if (walletIds.isEmpty()) {
            Toast.makeText(this, R.string.error_wallet_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        ExportPeriodUtils.DateRange range = ExportPeriodUtils.resolveRange(selectedPeriod, customStartDate, customEndDate);
        List<ExportRecordRow> rows = FinanceExportBuilder.buildRows(
            latestState,
            selectedPeriod,
            range.getStartDate(),
            range.getEndDate(),
            walletIds
        );
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.export_data_no_transactions, Toast.LENGTH_SHORT).show();
            return;
        }

        pendingExportFormat = format;
        pendingExportRows = rows;
        String periodLabel = ExportPeriodUtils.periodDisplayLabel(this, selectedPeriod, customStartDate, customEndDate);
        pendingExportTitle = getString(R.string.export_report_title_with_period, periodLabel);
        String fileName = "Bao_cao_thu_chi+"
            + ExportPeriodUtils.periodFileNameSuffix(selectedPeriod, customStartDate, customEndDate)
            + "."
            + format.getExtension();

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(format.getMimeType());
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        createDocumentLauncher.launch(intent);
    }

    private void exportToUri(Uri uri) {
        if (pendingExportFormat == null || pendingExportRows == null) {
            return;
        }
        exporting = true;
        Toast.makeText(this, R.string.export_data_exporting, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            FinanceExportWriter.ExportWriteResult result = FinanceExportWriter.writeToUri(
                this,
                uri,
                pendingExportFormat,
                pendingExportTitle,
                pendingExportRows
            );
            long fileSize = queryFileSize(uri);
            if (fileSize <= 0L && result.getBytesWritten() > 0L) {
                fileSize = result.getBytesWritten();
            }
            String fileName = queryDisplayName(uri);
            String mime = pendingExportFormat.getMimeType();
            long finalFileSize = fileSize;
            runOnUiThread(() -> {
                exporting = false;
                if (!result.isSuccess()) {
                    Toast.makeText(
                        this,
                        result.getErrorMessage().isEmpty() ? getString(R.string.export_data_failed) : result.getErrorMessage(),
                        Toast.LENGTH_LONG
                    ).show();
                    return;
                }
                Intent intent = new Intent(this, ExportSuccessActivity.class);
                intent.putExtra(ExportIntentKeys.EXTRA_EXPORTED_FILE_URI, uri.toString());
                intent.putExtra(ExportIntentKeys.EXTRA_EXPORTED_FILE_NAME, fileName);
                intent.putExtra(ExportIntentKeys.EXTRA_EXPORTED_FILE_SIZE, finalFileSize);
                intent.putExtra(ExportIntentKeys.EXTRA_EXPORTED_FILE_MIME, mime);
                startActivity(intent);
            });
        }).start();
    }

    private void showHelpDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_data_help_title)
            .setMessage(R.string.export_data_help_message)
            .setPositiveButton(R.string.action_agree, null)
            .show();
    }

    private void updateSummaryUi() {
        tvPeriodValue.setText(
            ExportPeriodUtils.periodDisplayLabel(this, selectedPeriod, customStartDate, customEndDate)
        );
        tvWalletValue.setText(buildWalletSummary());
    }

    private void updateFormatCards() {
        styleFormatCard(cardExcel, selectedFormat == ExportFileFormat.EXCEL);
        styleFormatCard(cardCsv, selectedFormat == ExportFileFormat.CSV);
        styleFormatCard(cardPdf, selectedFormat == ExportFileFormat.PDF);
    }

    private void styleFormatCard(MaterialCardView card, boolean selected) {
        card.setStrokeWidth(selected ? dp(2) : dp(1));
        card.setStrokeColor(ContextCompat.getColor(this, selected ? R.color.blue_primary : R.color.divider));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Set<String> buildAllWalletIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (latestState == null) {
            return ids;
        }
        for (Wallet wallet : latestState.getWallets()) {
            ids.add(wallet.getId());
        }
        return ids;
    }

    private String buildWalletSummary() {
        if (latestState == null || latestState.getWallets().isEmpty()) {
            return getString(R.string.export_wallet_none);
        }
        if (selectedWalletIds.isEmpty() || selectedWalletIds.size() >= latestState.getWallets().size()) {
            return getString(R.string.export_wallet_all_accounts);
        }
        List<String> selectedNames = new ArrayList<>();
        for (Wallet wallet : latestState.getWallets()) {
            if (selectedWalletIds.contains(wallet.getId())) {
                selectedNames.add(wallet.getName());
            }
        }
        if (selectedNames.isEmpty()) {
            return getString(R.string.export_wallet_all_accounts);
        }
        return TextUtils.join(", ", selectedNames);
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return ExportPeriodUtils.formatDisplayDate(date);
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
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            return LocalDate.of(year, month, day);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String queryDisplayName(Uri uri) {
        if (uri == null) {
            return "";
        }
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return "";
            }
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (index < 0) {
                return "";
            }
            String value = cursor.getString(index);
            return value == null ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private long queryFileSize(Uri uri) {
        if (uri == null) {
            return 0L;
        }
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return 0L;
            }
            int index = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (index < 0) {
                return 0L;
            }
            long size = cursor.getLong(index);
            return Math.max(size, 0L);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

