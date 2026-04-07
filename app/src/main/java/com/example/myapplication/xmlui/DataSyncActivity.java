package com.example.myapplication.xmlui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DataSyncActivity extends AppCompatActivity {

    private FinanceViewModel financeViewModel;
    private TextView tvLastSync;
    private SwitchCompat switchWifiOnly;
    private MaterialButton btnSync;
    private boolean syncing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_sync);
        bindViews();
        setupToolbar();
        setupViewModel();
        setupActions();
        updateLastSyncText();
    }

    private void bindViews() {
        tvLastSync = findViewById(R.id.tvDataSyncLastSync);
        switchWifiOnly = findViewById(R.id.switchDataSyncWifiOnly);
        btnSync = findViewById(R.id.btnDataSyncNow);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarDataSync);
        toolbar.setTitle(R.string.data_sync_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViewModel() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goToAuth();
            return;
        }
        FinanceViewModelFactory factory = new FinanceViewModelFactory(new FirestoreFinanceRepository(), user.getUid());
        financeViewModel = new ViewModelProvider(this, factory).get(FinanceViewModel.class);
        financeViewModel.getUiStateLiveData().observe(this, state -> {
            if (state.getErrorMessage() != null && !state.getErrorMessage().trim().isEmpty()) {
                Toast.makeText(this, state.getErrorMessage(), Toast.LENGTH_SHORT).show();
                financeViewModel.clearError();
            }
        });
    }

    private void setupActions() {
        switchWifiOnly.setChecked(DataSettingsPrefs.isWifiOnlyEnabled(this));
        switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) ->
            DataSettingsPrefs.setWifiOnlyEnabled(this, isChecked)
        );
        btnSync.setOnClickListener(v -> runSync());
    }

    private void runSync() {
        if (financeViewModel == null || syncing) {
            Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT).show();
            return;
        }
        if (switchWifiOnly.isChecked() && !isWifiConnected()) {
            Toast.makeText(this, R.string.data_settings_error_wifi_required, Toast.LENGTH_SHORT).show();
            return;
        }
        syncing = true;
        btnSync.setEnabled(false);
        financeViewModel.refreshRealtimeSync(errorMessage -> runOnUiThread(() -> {
            syncing = false;
            btnSync.setEnabled(true);
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                return;
            }
            DataSettingsPrefs.markSyncedNow(this);
            updateLastSyncText();
            Toast.makeText(this, R.string.message_data_sync_success, Toast.LENGTH_SHORT).show();
        }));
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

    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null
            && networkInfo.isConnected()
            && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private void goToAuth() {
        startActivity(
            new android.content.Intent(this, AuthActivity.class)
                .addFlags(
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
        );
        finish();
    }
}
