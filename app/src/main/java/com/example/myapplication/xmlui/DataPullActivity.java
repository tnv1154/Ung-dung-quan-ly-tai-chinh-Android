package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DataPullActivity extends AppCompatActivity {

    private FinanceViewModel financeViewModel;
    private MaterialButton btnPull;
    private boolean pulling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_pull);
        setupToolbar();
        setupViewModel();
        setupActions();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarDataPull);
        toolbar.setTitle(R.string.data_pull_title);
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
        btnPull = findViewById(R.id.btnDataPullNow);
        btnPull.setOnClickListener(v -> showPullConfirmDialog());
    }

    private void showPullConfirmDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.data_pull_confirm_title)
            .setMessage(R.string.data_pull_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_continue, (dialog, which) -> runPullFromServer())
            .show();
    }

    private void runPullFromServer() {
        if (financeViewModel == null || pulling) {
            Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT).show();
            return;
        }
        pulling = true;
        btnPull.setEnabled(false);
        financeViewModel.pullFromServer(errorMessage -> runOnUiThread(() -> {
            pulling = false;
            btnPull.setEnabled(true);
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                return;
            }
            DataSettingsPrefs.markSyncedNow(this);
            Toast.makeText(this, R.string.message_data_pull_success, Toast.LENGTH_SHORT).show();
        }));
    }

    private void goToAuth() {
        startActivity(
            new Intent(this, AuthActivity.class)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
        );
        finish();
    }
}
