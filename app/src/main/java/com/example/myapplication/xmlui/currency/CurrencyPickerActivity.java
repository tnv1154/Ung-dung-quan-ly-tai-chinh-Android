package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.xmlui.currency.CurrencyLogoUtils;
import com.example.myapplication.xmlui.currency.CurrencyPickerAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class CurrencyPickerActivity extends AppCompatActivity {
    public static final String EXTRA_CURRENCY_OPTIONS = "extra_currency_options";
    public static final String EXTRA_SELECTED_CURRENCY = "extra_selected_currency";
    public static final String EXTRA_PICKER_TITLE = "extra_picker_title";

    private final List<String> sourceCurrencies = new ArrayList<>();
    private final List<String> filteredCurrencies = new ArrayList<>();
    private String selectedCurrency = "VND";
    private CurrencyPickerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_currency_picker);
        readIntentData();
        setupToolbar();
        setupList();
        setupSearch();
    }

    private void readIntentData() {
        Intent intent = getIntent();
        ArrayList<String> options = intent == null
            ? null
            : intent.getStringArrayListExtra(EXTRA_CURRENCY_OPTIONS);
        if (options != null) {
            sourceCurrencies.addAll(options);
        }
        if (sourceCurrencies.isEmpty()) {
            sourceCurrencies.add("VND");
            sourceCurrencies.add("USD");
        }
        filteredCurrencies.addAll(sourceCurrencies);
        String incomingSelected = intent == null ? "" : safe(intent.getStringExtra(EXTRA_SELECTED_CURRENCY));
        if (!incomingSelected.isEmpty()) {
            selectedCurrency = incomingSelected;
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarCurrencyPicker);
        String title = safe(getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_PICKER_TITLE));
        toolbar.setTitle(title.isEmpty() ? getString(R.string.picker_title_currency) : title);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupList() {
        ListView listView = findViewById(R.id.lvCurrencyPicker);
        adapter = new CurrencyPickerAdapter(this, filteredCurrencies, selectedCurrency);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredCurrencies.size()) {
                return;
            }
            returnSelection(filteredCurrencies.get(position));
        });
    }

    private void setupSearch() {
        TextInputEditText etSearch = findViewById(R.id.etCurrencyPickerSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                filterCurrencies(editable == null ? "" : editable.toString());
            }
        });
    }

    private void filterCurrencies(String rawQuery) {
        String normalizedQuery = SearchTextUtils.normalize(rawQuery);
        filteredCurrencies.clear();
        for (String currencyCode : sourceCurrencies) {
            if (SearchTextUtils.matches(
                normalizedQuery,
                currencyCode,
                CurrencyLogoUtils.displayNameForCode(currencyCode),
                CurrencyLogoUtils.displaySymbolForCode(currencyCode)
            )) {
                filteredCurrencies.add(currencyCode);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void returnSelection(String currencyCode) {
        Intent data = new Intent();
        data.putExtra(EXTRA_SELECTED_CURRENCY, safe(currencyCode));
        setResult(RESULT_OK, data);
        finish();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
