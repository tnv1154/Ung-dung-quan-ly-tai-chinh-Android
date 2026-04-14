package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ForgotPasswordActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private TextInputEditText etEmail;
    private TextView tvError;
    private MaterialButton btnSubmit;
    private ProgressBar progressBar;
    private boolean submitPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        bindViews();
        setupActions();
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
    }

    private void bindViews() {
        etEmail = findViewById(R.id.etForgotEmail);
        tvError = findViewById(R.id.tvForgotError);
        btnSubmit = findViewById(R.id.btnForgotSubmit);
        progressBar = findViewById(R.id.progressForgot);
    }

    private void setupActions() {
        findViewById(R.id.btnForgotBack).setOnClickListener(v -> finish());
        findViewById(R.id.tvForgotBackLogin).setOnClickListener(v -> backToLogin());
        btnSubmit.setOnClickListener(v -> submitResetEmail());
    }

    private void submitResetEmail() {
        if (submitPending) {
            return;
        }
        String email = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(getString(R.string.auth_error_invalid_email));
            return;
        }
        submitPending = true;
        setLoading(true);
        sessionViewModel.ensureEmailRegistered(email, emailError -> runOnUiThread(() -> {
            if (emailError != null && !emailError.trim().isEmpty()) {
                submitPending = false;
                setLoading(false);
                showError(emailError);
                return;
            }
            sessionViewModel.sendPasswordReset(email, resetError -> runOnUiThread(() -> {
                submitPending = false;
                setLoading(false);
                if (resetError != null && !resetError.trim().isEmpty()) {
                    showError(resetError);
                    return;
                }
                tvError.setVisibility(View.GONE);
                Intent intent = new Intent(this, VerifyResetCodeActivity.class);
                intent.putExtra(VerifyResetCodeActivity.EXTRA_EMAIL, email);
                startActivity(intent);
            }));
        }));
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!loading);
        findViewById(R.id.btnForgotBack).setEnabled(!loading);
        findViewById(R.id.tvForgotBackLogin).setEnabled(!loading);
    }

    private void backToLogin() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
