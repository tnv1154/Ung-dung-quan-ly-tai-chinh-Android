package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.ui.FinanceViewModel;
import com.example.myapplication.finance.ui.FinanceViewModelFactory;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.security.SecureRandom;

public class DataDeleteActivity extends AppCompatActivity {

    private final SecureRandom secureRandom = new SecureRandom();
    private FinanceViewModel financeViewModel;
    private SessionViewModel sessionViewModel;
    private MaterialButton btnDelete;
    private boolean deleting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_delete);
        bindViews();
        setupToolbar();
        setupViewModels();
        setupActions();
    }

    private void bindViews() {
        btnDelete = findViewById(R.id.btnDataDeleteNow);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarDataDelete);
        toolbar.setTitle(R.string.data_delete_title);
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.text_primary));
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViewModels() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goToAuth();
            return;
        }
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
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
        btnDelete.setOnClickListener(v -> showDeleteConfirmCodeDialog());
    }

    private void showDeleteConfirmCodeDialog() {
        if (deleting) {
            return;
        }
        String verificationCode = String.valueOf(100000 + secureRandom.nextInt(900000));
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_data_delete_confirm_code, null, false);
        TextView tvCode = dialogView.findViewById(R.id.tvDeleteConfirmCodeValue);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.inputDeleteConfirmCode);
        TextInputEditText etCode = dialogView.findViewById(R.id.etDeleteConfirmCode);
        View btnClose = dialogView.findViewById(R.id.btnDeleteDialogClose);
        tvCode.setText(verificationCode);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_agree, null)
            .create();
        dialog.setOnShowListener(d -> {
            btnClose.setOnClickListener(v -> dialog.dismiss());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String enteredCode = etCode.getText() == null ? "" : etCode.getText().toString().trim();
                if (!verificationCode.equals(enteredCode)) {
                    inputLayout.setError(getString(R.string.data_delete_error_code_mismatch));
                    return;
                }
                inputLayout.setError(null);
                dialog.dismiss();
                runDeleteAllData();
            });
        });
        dialog.show();
    }

    private void runDeleteAllData() {
        if (financeViewModel == null || deleting) {
            return;
        }
        deleting = true;
        btnDelete.setEnabled(false);
        Toast.makeText(this, R.string.data_delete_started, Toast.LENGTH_SHORT).show();
        financeViewModel.clearUserCloudData(errorMessage -> runOnUiThread(() -> {
            deleting = false;
            btnDelete.setEnabled(true);
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                return;
            }
            LocalDataCleanup.clear(this);
            financeViewModel.clearInMemoryCache();
            Toast.makeText(this, R.string.data_delete_success, Toast.LENGTH_LONG).show();
            if (sessionViewModel != null) {
                sessionViewModel.signOut();
            } else {
                FirebaseAuth.getInstance().signOut();
            }
            goToAuth();
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
