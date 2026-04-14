package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

public class SignupEmailVerificationActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_signup_email";
    public static final String EXTRA_PASSWORD = "extra_signup_password";
    public static final String EXTRA_DISPLAY_NAME = "extra_signup_display_name";
    public static final String EXTRA_CODE = "extra_signup_code";
    public static final String EXTRA_EXPIRES_AT_MILLIS = "extra_signup_expires_at_millis";
    public static final String EXTRA_LAST_SENT_AT_MILLIS = "extra_signup_last_sent_at_millis";

    private static final String STATE_EMAIL = "state_email";
    private static final String STATE_PASSWORD = "state_password";
    private static final String STATE_DISPLAY_NAME = "state_display_name";
    private static final String STATE_CODE = "state_code";
    private static final String STATE_EXPIRES_AT = "state_expires_at";
    private static final String STATE_LAST_SENT_AT = "state_last_sent_at";

    private static final long EXPIRE_WINDOW_MILLIS = 3 * 60 * 1000L;
    private static final long RESEND_WINDOW_MILLIS = 60 * 1000L;
    private static final long CLOCK_TICK_MILLIS = 1_000L;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeUi();
            clockHandler.postDelayed(this, CLOCK_TICK_MILLIS);
        }
    };

    private SessionViewModel sessionViewModel;
    private TextInputEditText etCode;
    private TextView tvDescription;
    private TextView tvTimer;
    private TextView tvResendHint;
    private TextView tvError;
    private MaterialButton btnVerify;
    private MaterialButton btnResend;
    private ProgressBar progressBar;

    private String email = "";
    private String password = "";
    private String displayName = "";
    private String verificationCode = "";
    private long expiresAtMillis;
    private long lastSentAtMillis;
    private boolean submitPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_email_verification);
        if (!restoreState(savedInstanceState)) {
            Toast.makeText(this, R.string.auth_signup_verify_missing_data, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        bindViews();
        setupActions();
        renderDescription();
        updateTimeUi();
        clockHandler.post(clockRunnable);
    }

    @Override
    protected void onDestroy() {
        clockHandler.removeCallbacks(clockRunnable);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_EMAIL, email);
        outState.putString(STATE_PASSWORD, password);
        outState.putString(STATE_DISPLAY_NAME, displayName);
        outState.putString(STATE_CODE, verificationCode);
        outState.putLong(STATE_EXPIRES_AT, expiresAtMillis);
        outState.putLong(STATE_LAST_SENT_AT, lastSentAtMillis);
        super.onSaveInstanceState(outState);
    }

    private boolean restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            email = safe(savedInstanceState.getString(STATE_EMAIL)).trim();
            password = safe(savedInstanceState.getString(STATE_PASSWORD));
            displayName = safe(savedInstanceState.getString(STATE_DISPLAY_NAME)).trim();
            verificationCode = safe(savedInstanceState.getString(STATE_CODE)).trim();
            expiresAtMillis = savedInstanceState.getLong(STATE_EXPIRES_AT, 0L);
            lastSentAtMillis = savedInstanceState.getLong(STATE_LAST_SENT_AT, 0L);
        } else {
            Intent intent = getIntent();
            email = safe(intent.getStringExtra(EXTRA_EMAIL)).trim();
            password = safe(intent.getStringExtra(EXTRA_PASSWORD));
            displayName = safe(intent.getStringExtra(EXTRA_DISPLAY_NAME)).trim();
            verificationCode = safe(intent.getStringExtra(EXTRA_CODE)).trim();
            expiresAtMillis = intent.getLongExtra(EXTRA_EXPIRES_AT_MILLIS, 0L);
            lastSentAtMillis = intent.getLongExtra(EXTRA_LAST_SENT_AT_MILLIS, 0L);
        }
        return !email.isEmpty()
            && !password.isEmpty()
            && SignupVerificationCodeUtils.isCodeValid(verificationCode)
            && expiresAtMillis > 0L;
    }

    private void bindViews() {
        etCode = findViewById(R.id.etSignupVerifyCode);
        tvDescription = findViewById(R.id.tvSignupVerifyDescription);
        tvTimer = findViewById(R.id.tvSignupVerifyTimer);
        tvResendHint = findViewById(R.id.tvSignupVerifyResendHint);
        tvError = findViewById(R.id.tvSignupVerifyError);
        btnVerify = findViewById(R.id.btnSignupVerifySubmit);
        btnResend = findViewById(R.id.btnSignupVerifyResend);
        progressBar = findViewById(R.id.progressSignupVerify);
    }

    private void setupActions() {
        findViewById(R.id.btnSignupVerifyBack).setOnClickListener(v -> finish());
        findViewById(R.id.tvSignupVerifyBackLogin).setOnClickListener(v -> backToLogin());
        btnVerify.setOnClickListener(v -> submitVerification());
        btnResend.setOnClickListener(v -> resendVerificationCode());
    }

    private void renderDescription() {
        tvDescription.setText(
            getString(R.string.auth_signup_verify_desc, SignupVerificationCodeUtils.maskEmail(email))
        );
    }

    private void submitVerification() {
        if (submitPending) {
            return;
        }
        if (isCodeExpired()) {
            showError(getString(R.string.auth_signup_verify_expired_error));
            return;
        }
        String input = etCode.getText() == null ? "" : etCode.getText().toString().trim();
        String code = SignupVerificationCodeUtils.extractCode(input);
        if (!SignupVerificationCodeUtils.isCodeValid(code)) {
            showError(getString(R.string.auth_signup_verify_code_format_error));
            return;
        }
        if (!verificationCode.equals(code)) {
            showError(getString(R.string.auth_signup_verify_invalid));
            return;
        }
        submitPending = true;
        setLoading(true);
        sessionViewModel.register(email, password, displayName, error -> runOnUiThread(() -> {
            submitPending = false;
            setLoading(false);
            if (error != null && !error.trim().isEmpty()) {
                showError(error);
                return;
            }
            tvError.setVisibility(View.GONE);
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }));
    }

    private void resendVerificationCode() {
        if (submitPending || !isResendReady()) {
            return;
        }
        submitPending = true;
        setLoading(true);
        String refreshedCode = SignupVerificationCodeUtils.generateCode();
        sessionViewModel.sendSignupVerificationCode(email, refreshedCode, error -> runOnUiThread(() -> {
            submitPending = false;
            setLoading(false);
            if (error != null && !error.trim().isEmpty()) {
                showError(error);
                return;
            }
            long now = System.currentTimeMillis();
            verificationCode = refreshedCode;
            lastSentAtMillis = now;
            expiresAtMillis = now + EXPIRE_WINDOW_MILLIS;
            etCode.setText("");
            tvError.setVisibility(View.GONE);
            Toast.makeText(this, R.string.auth_signup_verify_code_resent, Toast.LENGTH_SHORT).show();
            updateTimeUi();
        }));
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnVerify.setEnabled(!loading);
        btnResend.setEnabled(!loading && isResendReady());
        findViewById(R.id.btnSignupVerifyBack).setEnabled(!loading);
        findViewById(R.id.tvSignupVerifyBackLogin).setEnabled(!loading);
    }

    private void updateTimeUi() {
        long now = System.currentTimeMillis();
        long remainingToExpire = expiresAtMillis - now;
        if (remainingToExpire > 0L) {
            tvTimer.setText(
                getString(
                    R.string.auth_signup_verify_timer,
                    formatRemainingDuration(remainingToExpire)
                )
            );
        } else {
            tvTimer.setText(R.string.auth_signup_verify_expired);
        }

        long resendAtMillis = lastSentAtMillis + RESEND_WINDOW_MILLIS;
        if (resendAtMillis > now) {
            tvResendHint.setText(
                getString(
                    R.string.auth_signup_verify_resend_in,
                    formatRemainingDuration(resendAtMillis - now)
                )
            );
        } else {
            tvResendHint.setText(R.string.auth_signup_verify_resend_ready);
        }
        btnResend.setEnabled(!submitPending && isResendReady());
    }

    private boolean isCodeExpired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }

    private boolean isResendReady() {
        return System.currentTimeMillis() >= (lastSentAtMillis + RESEND_WINDOW_MILLIS);
    }

    private String formatRemainingDuration(long millis) {
        long seconds = Math.max(0L, (long) Math.ceil(millis / 1000.0d));
        long minutePart = seconds / 60L;
        long secondPart = seconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutePart, secondPart);
    }

    private void backToLogin() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
