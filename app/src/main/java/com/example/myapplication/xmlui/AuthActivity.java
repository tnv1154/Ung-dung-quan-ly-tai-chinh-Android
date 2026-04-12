package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
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
import com.google.android.material.textfield.TextInputLayout;

public class AuthActivity extends AppCompatActivity {

    private SessionViewModel sessionViewModel;
    private TextInputEditText etAccount;
    private TextInputEditText etPassword;
    private TextInputEditText etDisplayName;
    private TextInputLayout inputAccount;
    private TextInputLayout inputPassword;
    private TextInputLayout inputDisplayName;
    private TextView tvAuthTitle;
    private TextView tvAuthError;
    private TextView tvAuthTogglePrefix;
    private TextView tvAuthToggleAction;
    private TextView tvForgotPassword;
    private MaterialButton btnEmailAuth;
    private MaterialButton btnGoogle;
    private ProgressBar progressAuth;
    private ImageView ivAuthHero;
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
        etAccount = findViewById(R.id.etAuthAccount);
        etPassword = findViewById(R.id.etAuthPassword);
        etDisplayName = findViewById(R.id.etAuthDisplayName);
        inputAccount = findViewById(R.id.inputAuthAccount);
        inputPassword = findViewById(R.id.inputAuthPassword);
        inputDisplayName = findViewById(R.id.inputAuthDisplayName);
        tvAuthTitle = findViewById(R.id.tvAuthTitle);
        tvAuthError = findViewById(R.id.tvAuthError);
        tvAuthTogglePrefix = findViewById(R.id.tvAuthTogglePrefix);
        tvAuthToggleAction = findViewById(R.id.tvAuthToggleAction);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnEmailAuth = findViewById(R.id.btnEmailAuth);
        btnGoogle = findViewById(R.id.btnGoogle);
        progressAuth = findViewById(R.id.progressAuth);
        ivAuthHero = findViewById(R.id.ivAuthHero);
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
        tvAuthToggleAction.setOnClickListener(v -> {
            registerMode = !registerMode;
            renderMode();
        });
        tvForgotPassword.setOnClickListener(v -> openForgotPassword());
        renderMode();
    }

    private void renderMode() {
        tvAuthTitle.setText(registerMode ? R.string.auth_register_panel_title : R.string.auth_login_panel_title);
        btnEmailAuth.setText(registerMode ? R.string.auth_action_register : R.string.auth_action_sign_in);
        tvAuthTogglePrefix.setText(registerMode ? R.string.auth_toggle_login_prefix : R.string.auth_toggle_register_prefix);
        tvAuthToggleAction.setText(registerMode ? R.string.auth_toggle_action_login : R.string.auth_toggle_action_register);
        tvForgotPassword.setVisibility(registerMode ? View.GONE : View.VISIBLE);
        inputDisplayName.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        inputAccount.setHint(getString(registerMode ? R.string.auth_hint_account_register : R.string.auth_hint_account_login));
        inputPassword.setHint(getString(registerMode ? R.string.auth_hint_password_register : R.string.auth_hint_password_login));
        ivAuthHero.setImageResource(registerMode ? R.drawable.auth_register_hero_clean : R.drawable.auth_login_hero_clean);
        if (!registerMode) {
            etDisplayName.setText("");
        }
        tvAuthError.setVisibility(View.GONE);
    }

    private void handleEmailAuth() {
        String account = etAccount.getText() != null ? etAccount.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String displayName = etDisplayName.getText() != null ? etDisplayName.getText().toString().trim() : "";
        if (account.isEmpty()) {
            tvAuthError.setText(R.string.auth_error_account_required);
            tvAuthError.setVisibility(View.VISIBLE);
            return;
        }
        if (password.length() < 6) {
            tvAuthError.setText(R.string.auth_error_password_required);
            tvAuthError.setVisibility(View.VISIBLE);
            return;
        }
        if (registerMode && displayName.isEmpty()) {
            tvAuthError.setText(R.string.auth_error_display_name_required);
            tvAuthError.setVisibility(View.VISIBLE);
            return;
        }
        if (registerMode) {
            sessionViewModel.register(account, password, displayName);
        } else {
            sessionViewModel.signIn(account, password);
        }
    }

    private void handleGoogleAuth() {
        if (googleSignInClient == null) {
            Toast.makeText(this, R.string.error_google_config, Toast.LENGTH_LONG).show();
            return;
        }
        googleSignInClient.signOut().addOnCompleteListener(task -> googleLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void openForgotPassword() {
        startActivity(new Intent(this, ForgotPasswordActivity.class));
    }

    private void renderState(SessionUiState state) {
        progressAuth.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        btnEmailAuth.setEnabled(!state.isLoading());
        btnGoogle.setEnabled(!state.isLoading());
        tvAuthToggleAction.setEnabled(!state.isLoading());
        tvForgotPassword.setEnabled(!state.isLoading());
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
