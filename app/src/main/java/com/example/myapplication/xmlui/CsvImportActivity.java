package com.example.myapplication.xmlui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.ui.FinanceParsersKt;
import com.example.myapplication.finance.ui.FinanceTimeAndIoKt;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;

public class CsvImportActivity extends AppCompatActivity {
    public static final String EXTRA_CSV_URI = "extra_csv_uri";
    public static final String EXTRA_CSV_FILE_NAME = "extra_csv_file_name";

    private static final int REQUEST_PICK_CSV_FILE = 6201;
    private static final int REQUEST_CREATE_TEMPLATE_FILE = 6202;
    private static final long MAX_CSV_SIZE_BYTES = 5L * 1024L * 1024L;

    private Uri selectedFileUri;
    private String selectedFileName;
    private long selectedFileSizeBytes;

    private android.view.View layoutPickEmpty;
    private android.view.View layoutPickSelected;
    private TextView tvSelectedFileName;
    private TextView tvSelectedFileSize;
    private MaterialButton btnStartImport;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_import);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            goToAuth();
            return;
        }
        bindViews();
        setupToolbar();
        setupBottomNavigation();
        setupActions();
        updateSelectedFileUi();
    }

    private void bindViews() {
        layoutPickEmpty = findViewById(R.id.layoutCsvPickEmpty);
        layoutPickSelected = findViewById(R.id.layoutCsvPickSelected);
        tvSelectedFileName = findViewById(R.id.tvCsvSelectedFileName);
        tvSelectedFileSize = findViewById(R.id.tvCsvSelectedFileSize);
        btnStartImport = findViewById(R.id.btnCsvStartImport);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarCsvImport);
        toolbar.setTitle(R.string.label_more_import_csv_full);
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
                return true;
            }
            return false;
        });
    }

    private void setupActions() {
        MaterialButton btnUpload = findViewById(R.id.btnCsvUpload);
        TextView tvPickAnother = findViewById(R.id.tvCsvPickAnother);
        TextView btnRemoveSelected = findViewById(R.id.btnCsvRemoveSelectedFile);
        TextView tvTemplateDownload = findViewById(R.id.tvCsvTemplateDownload);

        btnUpload.setOnClickListener(v -> pickCsvFile());
        tvPickAnother.setOnClickListener(v -> pickCsvFile());
        btnRemoveSelected.setOnClickListener(v -> clearSelectedFile());
        tvTemplateDownload.setOnClickListener(v -> createTemplateFile());
        btnStartImport.setOnClickListener(v -> openPreview());
    }

    private void pickCsvFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "application/vnd.ms-excel"
        });
        startActivityForResult(intent, REQUEST_PICK_CSV_FILE);
    }

    private void createTemplateFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "mau-nhap-du-lieu.csv");
        startActivityForResult(intent, REQUEST_CREATE_TEMPLATE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_PICK_CSV_FILE) {
            onCsvPicked(uri, data.getFlags());
            return;
        }
        if (requestCode == REQUEST_CREATE_TEMPLATE_FILE) {
            boolean success = FinanceTimeAndIoKt.writeTextToUri(
                this,
                uri,
                FinanceParsersKt.buildCsvImportTemplate()
            );
            Toast.makeText(
                this,
                success ? R.string.csv_import_template_saved : R.string.csv_import_template_save_failed,
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void onCsvPicked(Uri uri, int flags) {
        if (uri == null) {
            return;
        }
        int readFlag = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (readFlag != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri, readFlag);
            } catch (Exception ignored) {
            }
        }

        long size = queryFileSize(uri);
        if (size > MAX_CSV_SIZE_BYTES) {
            Toast.makeText(this, R.string.csv_import_file_too_large, Toast.LENGTH_SHORT).show();
            return;
        }

        selectedFileUri = uri;
        selectedFileName = queryDisplayName(uri);
        selectedFileSizeBytes = size;
        updateSelectedFileUi();
    }

    private void clearSelectedFile() {
        selectedFileUri = null;
        selectedFileName = null;
        selectedFileSizeBytes = 0L;
        updateSelectedFileUi();
    }

    private void updateSelectedFileUi() {
        boolean hasSelectedFile = selectedFileUri != null;
        layoutPickEmpty.setVisibility(hasSelectedFile ? android.view.View.GONE : android.view.View.VISIBLE);
        layoutPickSelected.setVisibility(hasSelectedFile ? android.view.View.VISIBLE : android.view.View.GONE);
        btnStartImport.setEnabled(hasSelectedFile);
        if (!hasSelectedFile) {
            tvSelectedFileName.setText("");
            tvSelectedFileSize.setText("");
            return;
        }
        tvSelectedFileName.setText(
            selectedFileName == null || selectedFileName.trim().isEmpty()
                ? getString(R.string.csv_import_selected_file_unknown)
                : selectedFileName
        );
        tvSelectedFileSize.setText(formatFileSize(selectedFileSizeBytes));
    }

    private void openPreview() {
        if (selectedFileUri == null) {
            Toast.makeText(this, R.string.csv_import_select_file_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, CsvImportPreviewActivity.class);
        intent.putExtra(EXTRA_CSV_URI, selectedFileUri.toString());
        intent.putExtra(EXTRA_CSV_FILE_NAME, selectedFileName == null ? "" : selectedFileName);
        startActivity(intent);
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

    private String formatFileSize(long bytes) {
        if (bytes <= 0L) {
            return getString(R.string.csv_import_file_size_unknown);
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.getDefault(), "%.1fKB", kb);
        }
        return String.format(Locale.getDefault(), "%.1fMB", kb / 1024.0);
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
