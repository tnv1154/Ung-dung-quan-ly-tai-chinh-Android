package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class VerifyResetCodeActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_reset_email";
    public static final String EXTRA_RESET_CODE = "extra_reset_code";

    private SessionViewModel sessionViewModel;
    private TextInputEditText etCode;
    private TextView tvDescription;
    private TextView tvError;
    private MaterialButton btnContinue;
    private ProgressBar progressBar;
    private boolean submitPending;
    private String email = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_reset_code);
        readIntent();
        bindViews();
        setupActions();
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        renderDescription();
    }

    private void readIntent() {
        String incoming = getIntent().getStringExtra(EXTRA_EMAIL);
        if (incoming != null) {
            email = incoming.trim();
        }
    }

    private void bindViews() {
        etCode = findViewById(R.id.etVerifyCode);
        tvDescription = findViewById(R.id.tvVerifyDescription);
        tvError = findViewById(R.id.tvVerifyError);
        btnContinue = findViewById(R.id.btnVerifyContinue);
        progressBar = findViewById(R.id.progressVerify);
    }

    private void setupActions() {
        findViewById(R.id.btnVerifyBack).setOnClickListener(v -> finish());
        findViewById(R.id.tvVerifyBackLogin).setOnClickListener(v -> backToLogin());
        btnContinue.setOnClickListener(v -> verifyCode());
    }

    private void renderDescription() {
        tvDescription.setText(getString(
            R.string.auth_verify_code_desc,
            PasswordResetCodeParser.maskEmail(email)
        ));
    }

    private void verifyCode() {
        if (submitPending) {
            return;
        }
        String raw = etCode.getText() == null ? "" : etCode.getText().toString().trim();
        String code = PasswordResetCodeParser.extractCode(raw);
        if (code.isEmpty()) {
            showError(getString(R.string.auth_error_code_required));
            return;
        }
        submitPending = true;
        setLoading(true);
        sessionViewModel.verifyPasswordResetCode(code, error -> runOnUiThread(() -> {
            submitPending = false;
            setLoading(false);
            if (error != null && !error.trim().isEmpty()) {
                showError(error);
                return;
            }
            tvError.setVisibility(View.GONE);
            Intent intent = new Intent(this, CreateNewPasswordActivity.class);
            intent.putExtra(EXTRA_RESET_CODE, code);
            intent.putExtra(EXTRA_EMAIL, email);
            startActivity(intent);
        }));
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnContinue.setEnabled(!loading);
        findViewById(R.id.btnVerifyBack).setEnabled(!loading);
        findViewById(R.id.tvVerifyBackLogin).setEnabled(!loading);
    }

    private void backToLogin() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
