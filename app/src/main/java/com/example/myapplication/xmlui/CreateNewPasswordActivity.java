package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class CreateNewPasswordActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private TextInputEditText etNewPassword;
    private TextInputEditText etConfirmPassword;
    private TextView tvError;
    private MaterialButton btnCreatePassword;
    private ProgressBar progressBar;
    private String resetCode = "";
    private boolean submitPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_new_password);
        readIntent();
        bindViews();
        setupActions();
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
    }

    private void readIntent() {
        String incoming = getIntent().getStringExtra(VerifyResetCodeActivity.EXTRA_RESET_CODE);
        resetCode = PasswordResetCodeParser.extractCode(incoming);
    }

    private void bindViews() {
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvError = findViewById(R.id.tvCreatePasswordError);
        btnCreatePassword = findViewById(R.id.btnCreatePassword);
        progressBar = findViewById(R.id.progressCreatePassword);
    }

    private void setupActions() {
        findViewById(R.id.btnCreatePasswordBack).setOnClickListener(v -> finish());
        findViewById(R.id.tvCreatePasswordBackLogin).setOnClickListener(v -> backToLogin());
        btnCreatePassword.setOnClickListener(v -> submitNewPassword());
    }

    private void submitNewPassword() {
        if (submitPending) {
            return;
        }
        String password = etNewPassword.getText() == null ? "" : etNewPassword.getText().toString();
        String confirm = etConfirmPassword.getText() == null ? "" : etConfirmPassword.getText().toString();
        if (!isValidPassword(password)) {
            showError(getString(R.string.auth_error_password_policy));
            return;
        }
        if (!password.equals(confirm)) {
            showError(getString(R.string.error_password_mismatch));
            return;
        }
        if (resetCode == null || resetCode.trim().isEmpty()) {
            showError(getString(R.string.auth_error_reset_code_invalid));
            return;
        }
        submitPending = true;
        setLoading(true);
        sessionViewModel.confirmPasswordReset(resetCode, password, error -> runOnUiThread(() -> {
            submitPending = false;
            setLoading(false);
            if (error != null && !error.trim().isEmpty()) {
                showError(error);
                return;
            }
            Toast.makeText(this, R.string.message_password_updated, Toast.LENGTH_SHORT).show();
            backToLogin();
        }));
    }

    private boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLowerCase(ch)) {
                hasLower = true;
            } else if (Character.isUpperCase(ch)) {
                hasUpper = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        return hasLower && hasUpper && hasDigit;
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnCreatePassword.setEnabled(!loading);
        findViewById(R.id.btnCreatePasswordBack).setEnabled(!loading);
        findViewById(R.id.tvCreatePasswordBackLogin).setEnabled(!loading);
    }

    private void backToLogin() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
