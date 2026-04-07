package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class AuthActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextView tvAuthError;
    private TextView tvAuthToggle;
    private TextView tvForgotPassword;
    private MaterialButton btnEmailAuth;
    private MaterialButton btnGoogle;
    private ProgressBar progressAuth;
    private boolean registerMode = false;
    @Nullable
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        bindViews();
        configureGoogle();
        setupViewModel();
        setupActions();
    }

    private void bindViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvAuthError = findViewById(R.id.tvAuthError);
        tvAuthToggle = findViewById(R.id.tvAuthToggle);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnEmailAuth = findViewById(R.id.btnEmailAuth);
        btnGoogle = findViewById(R.id.btnGoogle);
        progressAuth = findViewById(R.id.progressAuth);
    }

    private void configureGoogle() {
        String webClientId = getString(R.string.default_web_client_id);
        if (webClientId == null || webClientId.trim().isEmpty()) {
            googleSignInClient = null;
        } else {
            GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
            googleSignInClient = GoogleSignIn.getClient(this, options);
        }
        googleLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (googleSignInClient == null) {
                    return;
                }
                Intent data = result.getData();
                try {
                    GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data)
                        .getResult(ApiException.class);
                    if (account == null || account.getIdToken() == null || account.getIdToken().trim().isEmpty()) {
                        sessionViewModel.setError(getString(R.string.error_google_token));
                    } else {
                        sessionViewModel.signInWithGoogleIdToken(account.getIdToken());
                    }
                } catch (ApiException ex) {
                    sessionViewModel.setError(ex.getLocalizedMessage() != null ? ex.getLocalizedMessage() : getString(R.string.error_unknown));
                }
            }
        );
    }

    private void setupViewModel() {
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        sessionViewModel.getUiStateLiveData().observe(this, this::renderState);
    }

    private void setupActions() {
        btnEmailAuth.setOnClickListener(v -> handleEmailAuth());
        btnGoogle.setOnClickListener(v -> handleGoogleAuth());
        tvAuthToggle.setOnClickListener(v -> {
            registerMode = !registerMode;
            renderMode();
        });
        tvForgotPassword.setOnClickListener(v -> handleForgotPassword());
        renderMode();
    }

    private void renderMode() {
        btnEmailAuth.setText(registerMode ? R.string.auth_action_register : R.string.auth_action_sign_in);
        tvAuthToggle.setText(registerMode ? R.string.auth_toggle_to_login : R.string.auth_toggle_to_register);
        tvForgotPassword.setVisibility(registerMode ? View.GONE : View.VISIBLE);
        setTitle(registerMode ? R.string.auth_action_register : R.string.auth_title);
    }

    private void handleEmailAuth() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        if (!email.contains("@") || password.length() < 6) {
            tvAuthError.setText(R.string.error_invalid_email_password);
            tvAuthError.setVisibility(View.VISIBLE);
            return;
        }
        if (registerMode) {
            sessionViewModel.register(email, password);
        } else {
            sessionViewModel.signIn(email, password);
        }
    }

    private void handleGoogleAuth() {
        if (googleSignInClient == null) {
            Toast.makeText(this, R.string.error_google_config, Toast.LENGTH_LONG).show();
            return;
        }
        googleSignInClient.signOut().addOnCompleteListener(task -> googleLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void handleForgotPassword() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (email.isEmpty()) {
            tvAuthError.setText(R.string.error_email_required_for_reset);
            tvAuthError.setVisibility(View.VISIBLE);
            return;
        }
        sessionViewModel.sendPasswordReset(email, error -> {
            runOnUiThread(() -> {
                if (error == null) {
                    Toast.makeText(this, getString(R.string.auth_reset_password_message, email), Toast.LENGTH_LONG).show();
                } else {
                    tvAuthError.setText(error);
                    tvAuthError.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void renderState(SessionUiState state) {
        progressAuth.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        btnEmailAuth.setEnabled(!state.isLoading());
        btnGoogle.setEnabled(!state.isLoading());
        tvAuthError.setVisibility(state.getErrorMessage() == null ? View.GONE : View.VISIBLE);
        if (state.getErrorMessage() != null) {
            tvAuthError.setText(state.getErrorMessage());
        }
        if (state.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }
}
