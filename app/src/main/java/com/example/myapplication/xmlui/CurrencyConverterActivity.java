package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.ui.SessionUiState;
import com.example.myapplication.finance.ui.SessionViewModel;
import com.example.myapplication.xmlui.currency.CurrencyFlagUtils;
import com.example.myapplication.xmlui.currency.CurrencyPickerAdapter;
import com.example.myapplication.xmlui.currency.CurrencyRateUtils;
import com.example.myapplication.xmlui.currency.ExchangeRateSnapshotLoader;
import com.example.myapplication.xmlui.currency.SupportedCurrencyCatalog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CurrencyConverterActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final List<String> supportedCurrencies = new ArrayList<>();
    private SessionViewModel sessionViewModel;
    private String observedUserId;
    private ExchangeRateSnapshot latestSnapshot;

    private TextInputEditText etAmount;
    private View cardFrom;
    private View cardTo;
    private ImageButton btnSwap;
    private TextView ivFromFlag;
    private TextView ivToFlag;
    private TextView tvFromCode;
    private TextView tvToCode;
    private MaterialButton btnConvert;
    private TextView tvResult;
    private TextView tvRate;

    private String selectedFrom = "USD";
    private String selectedTo = "VND";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_currency_converter);
        bindViews();
        setupTopBar();
        setupBottomNavigation();
        setupActions();
        setupSession();
        seedLocalCurrencies();
        updateCurrencySelectionUi();
        updateRatePreview();
    }

    private void bindViews() {
        etAmount = findViewById(R.id.etCurrencyConvertAmount);
        cardFrom = findViewById(R.id.cardCurrencyConvertFrom);
        cardTo = findViewById(R.id.cardCurrencyConvertTo);
        btnSwap = findViewById(R.id.btnCurrencyConvertSwap);
        ivFromFlag = findViewById(R.id.ivCurrencyConvertFromFlag);
        ivToFlag = findViewById(R.id.ivCurrencyConvertToFlag);
        tvFromCode = findViewById(R.id.tvCurrencyConvertFromCode);
        tvToCode = findViewById(R.id.tvCurrencyConvertToCode);
        btnConvert = findViewById(R.id.btnCurrencyConvertExecute);
        tvResult = findViewById(R.id.tvCurrencyConvertResult);
        tvRate = findViewById(R.id.tvCurrencyConvertRate);
        MoneyInputFormatter.attach(etAmount);
    }

    private void setupTopBar() {
        ImageButton btnBack = findViewById(R.id.btnCurrencyConverterBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_more);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_overview) {
                startActivity(new Intent(this, OverviewActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_accounts) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_add) {
                Intent addIntent = new Intent(this, AddTransactionActivity.class);
                addIntent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_EXPENSE);
                startActivity(addIntent);
                return false;
            }
            if (id == R.id.nav_report) {
                startActivity(new Intent(this, ReportActivity.class));
                finish();
                return true;
            }
            if (id == R.id.nav_more) {
                startActivity(new Intent(this, MoreActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupActions() {
        cardFrom.setOnClickListener(v -> showCurrencyPickerDialog(true));
        cardTo.setOnClickListener(v -> showCurrencyPickerDialog(false));
        btnSwap.setOnClickListener(v -> {
            String temp = selectedFrom;
            selectedFrom = selectedTo;
            selectedTo = temp;
            updateCurrencySelectionUi();
            updateRatePreview();
        });
        btnConvert.setOnClickListener(v -> executeConversion());
    }

    private void setupSession() {
        sessionViewModel = new ViewModelProvider(this).get(SessionViewModel.class);
        sessionViewModel.getUiStateLiveData().observe(this, this::renderSessionState);
    }

    private void renderSessionState(@NonNull SessionUiState state) {
        if (state.getCurrentUser() == null) {
            goToAuth();
            return;
        }
        String userId = state.getCurrentUser().getUid();
        if (userId == null || userId.isBlank()) {
            return;
        }
        if (userId.equals(observedUserId)) {
            return;
        }
        observedUserId = userId;
        loadExchangeRateSnapshot(userId);
    }

    private void loadExchangeRateSnapshot(String userId) {
        ioExecutor.submit(() -> {
            ExchangeRateSnapshot snapshot = null;
            try {
                snapshot = ExchangeRateSnapshotLoader.loadWithFallback(new FirestoreFinanceRepository(), userId);
            } catch (Exception ignored) {
            }
            ExchangeRateSnapshot finalSnapshot = snapshot;
            runOnUiThread(() -> applySnapshot(finalSnapshot));
        });
    }

    private void applySnapshot(ExchangeRateSnapshot snapshot) {
        latestSnapshot = snapshot;
        if (snapshot != null && snapshot.getRates() != null && !snapshot.getRates().isEmpty()) {
            Set<String> merged = new HashSet<>(snapshot.getRates().keySet());
            merged.add("USD");
            merged.add("VND");
            supportedCurrencies.clear();
            supportedCurrencies.addAll(merged);
            Collections.sort(supportedCurrencies);
        }
        if (!supportedCurrencies.contains(selectedFrom)) {
            selectedFrom = supportedCurrencies.contains("USD") ? "USD" : supportedCurrencies.get(0);
        }
        if (!supportedCurrencies.contains(selectedTo)) {
            selectedTo = supportedCurrencies.contains("VND") ? "VND" : selectedFrom;
        }
        if (selectedFrom.equals(selectedTo) && supportedCurrencies.size() > 1) {
            selectedTo = supportedCurrencies.get(0);
            if (selectedTo.equals(selectedFrom)) {
                selectedTo = supportedCurrencies.get(1);
            }
        }
        btnConvert.setEnabled(snapshot != null);
        updateCurrencySelectionUi();
        updateRatePreview();
    }

    private void seedLocalCurrencies() {
        supportedCurrencies.clear();
        supportedCurrencies.addAll(SupportedCurrencyCatalog.defaultCodes());
        if (supportedCurrencies.isEmpty()) {
            supportedCurrencies.add("USD");
            supportedCurrencies.add("VND");
        }
        if (!supportedCurrencies.contains(selectedFrom)) {
            selectedFrom = "USD";
        }
        if (!supportedCurrencies.contains(selectedTo)) {
            selectedTo = "VND";
        }
    }

    private void showCurrencyPickerDialog(boolean pickingFromCurrency) {
        if (supportedCurrencies.isEmpty()) {
            return;
        }
        String selectedCode = pickingFromCurrency ? selectedFrom : selectedTo;
        CurrencyPickerAdapter adapter = new CurrencyPickerAdapter(this, supportedCurrencies, selectedCode);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.wallet_label_currency)
            .setAdapter(adapter, (dialog, which) -> {
                if (which < 0 || which >= supportedCurrencies.size()) {
                    return;
                }
                if (pickingFromCurrency) {
                    selectedFrom = supportedCurrencies.get(which);
                } else {
                    selectedTo = supportedCurrencies.get(which);
                }
                updateCurrencySelectionUi();
                updateRatePreview();
            })
            .show();
    }

    private void updateCurrencySelectionUi() {
        bindCurrencySelection(ivFromFlag, tvFromCode, selectedFrom);
        bindCurrencySelection(ivToFlag, tvToCode, selectedTo);
    }

    private void bindCurrencySelection(TextView flagView, TextView codeView, String currencyCode) {
        String code = CurrencyRateUtils.normalizeCurrency(currencyCode);
        codeView.setText(code);
        flagView.setText(CurrencyFlagUtils.flagEmojiForCurrency(code));
    }

    private void executeConversion() {
        if (latestSnapshot == null) {
            Toast.makeText(this, R.string.error_currency_rate_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Double amount = parsePositiveAmount(etAmount.getText() == null ? "" : etAmount.getText().toString());
        if (amount == null) {
            tvResult.setTextColor(getColor(R.color.error_red));
            tvResult.setText(R.string.error_invalid_amount);
            return;
        }
        Double converted = CurrencyRateUtils.convert(amount, selectedFrom, selectedTo, latestSnapshot);
        if (converted == null) {
            tvResult.setTextColor(getColor(R.color.error_red));
            tvResult.setText(R.string.error_currency_rate_unavailable);
            return;
        }
        tvResult.setTextColor(getColor(R.color.text_primary));
        tvResult.setText(
            getString(
                R.string.label_currency_convert_result_value,
                formatAmountByCurrency(amount, selectedFrom),
                formatAmountByCurrency(converted, selectedTo)
            )
        );
        updateRatePreview();
    }

    private void updateRatePreview() {
        if (latestSnapshot == null) {
            tvRate.setText(R.string.error_currency_rate_unavailable);
            return;
        }
        Double rate = latestSnapshot.conversionRate(selectedFrom, selectedTo);
        if (rate == null || rate <= 0.0) {
            tvRate.setText(R.string.error_currency_rate_unavailable);
            return;
        }
        tvRate.setText(
            getString(
                R.string.label_currency_convert_rate_value,
                CurrencyRateUtils.normalizeCurrency(selectedFrom),
                formatRateValue(rate),
                CurrencyRateUtils.normalizeCurrency(selectedTo)
            )
        );
    }

    private Double parsePositiveAmount(String raw) {
        String normalized = MoneyInputFormatter.normalizeAmount(raw);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(normalized);
            if (value <= 0.0) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatRateValue(double rate) {
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(Locale.US);
        formatter.applyPattern("#,##0.######");
        return formatter.format(rate);
    }

    private String formatAmountByCurrency(double value, String currencyCode) {
        String currency = CurrencyRateUtils.normalizeCurrency(currencyCode);
        DecimalFormat formatter = (DecimalFormat) DecimalFormat.getInstance(new Locale("vi", "VN"));
        if ("VND".equals(currency)) {
            formatter.applyPattern("#,###");
            return formatter.format(value) + " ₫";
        }
        if ("USD".equals(currency)) {
            formatter.applyPattern("#,##0.00");
            return "$" + formatter.format(value);
        }
        formatter.applyPattern("#,##0.00");
        return formatter.format(value) + " " + currency;
    }

    private void goToAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
