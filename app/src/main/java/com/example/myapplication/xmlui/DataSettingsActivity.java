package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;

public class DataSettingsActivity extends AppCompatActivity {

    private TextView tvLastSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_settings);
        bindViews();
        setupToolbar();
        setupActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLastSyncText();
    }

    private void bindViews() {
        tvLastSync = findViewById(R.id.tvDataSettingsLastSync);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarDataSettings);
        toolbar.setTitle(R.string.data_settings_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupActions() {
        findViewById(R.id.rowDataSync).setOnClickListener(v ->
            startActivity(new Intent(this, DataSyncActivity.class))
        );
        findViewById(R.id.rowDataPull).setOnClickListener(v ->
            startActivity(new Intent(this, DataPullActivity.class))
        );
        findViewById(R.id.rowDataDelete).setOnClickListener(v ->
            startActivity(new Intent(this, DataDeleteActivity.class))
        );
    }

    private void updateLastSyncText() {
        long lastSyncAt = DataSettingsPrefs.getLastSyncAt(this);
        if (lastSyncAt <= 0L) {
            tvLastSync.setText(R.string.data_settings_last_sync_empty);
            return;
        }
        tvLastSync.setText(
            getString(
                R.string.data_settings_last_sync_format,
                DataSettingsPrefs.formatLastSync(lastSyncAt)
            )
        );
    }
}
