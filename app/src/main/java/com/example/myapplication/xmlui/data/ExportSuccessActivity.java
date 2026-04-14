package com.example.myapplication.xmlui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class ExportSuccessActivity extends AppCompatActivity {
    private Uri exportedFileUri;
    private String exportedFileName;
    private String exportedFileMime;
    private long exportedFileSize;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_success);
        readIntentData();
        bindFileInfo();
        setupActions();
        setupBottomNavigation();
    }

    private void readIntentData() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String rawUri = intent.getStringExtra(ExportIntentKeys.EXTRA_EXPORTED_FILE_URI);
        if (rawUri != null && !rawUri.trim().isEmpty()) {
            exportedFileUri = Uri.parse(rawUri);
        }
        exportedFileName = intent.getStringExtra(ExportIntentKeys.EXTRA_EXPORTED_FILE_NAME);
        exportedFileMime = intent.getStringExtra(ExportIntentKeys.EXTRA_EXPORTED_FILE_MIME);
        exportedFileSize = intent.getLongExtra(ExportIntentKeys.EXTRA_EXPORTED_FILE_SIZE, 0L);
    }

    private void bindFileInfo() {
        TextView tvFileName = findViewById(R.id.tvExportSuccessFileName);
        TextView tvFileMeta = findViewById(R.id.tvExportSuccessFileMeta);
        tvFileName.setText(
            exportedFileName == null || exportedFileName.trim().isEmpty()
                ? getString(R.string.export_success_file_name_fallback)
                : exportedFileName
        );
        tvFileMeta.setText(getString(R.string.export_success_file_meta, formatFileSize(exportedFileSize)));
    }

    private void setupActions() {
        findViewById(R.id.btnExportSuccessClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnExportSuccessShare).setOnClickListener(v -> shareFile());

        MaterialButton btnHome = findViewById(R.id.btnExportSuccessGoHome);
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, OverviewActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        TextView tvOpenFile = findViewById(R.id.tvExportSuccessOpenFile);
        tvOpenFile.setOnClickListener(v -> openFile());
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

    private void shareFile() {
        if (exportedFileUri == null) {
            Toast.makeText(this, R.string.export_success_file_open_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(normalizeMime(exportedFileMime));
        shareIntent.putExtra(Intent.EXTRA_STREAM, exportedFileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.export_success_share_chooser_title)));
    }

    private void openFile() {
        if (exportedFileUri == null) {
            Toast.makeText(this, R.string.export_success_file_open_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(exportedFileUri, normalizeMime(exportedFileMime));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.export_success_file_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private String normalizeMime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "*/*";
        }
        return raw;
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
}

