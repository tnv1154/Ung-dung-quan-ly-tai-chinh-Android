package com.example.myapplication.xmlui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class ProviderPickerActivity extends AppCompatActivity {
    public static final String EXTRA_PROVIDER_SHORT_NAMES = "extra_provider_short_names";
    public static final String EXTRA_PROVIDER_DISPLAY_NAMES = "extra_provider_display_names";
    public static final String EXTRA_SELECTED_PROVIDER = "extra_selected_provider";
    public static final String EXTRA_ACCOUNT_TYPE = "extra_account_type";

    private final List<WalletProviderOption> sourceOptions = new ArrayList<>();
    private final List<WalletProviderOption> filteredOptions = new ArrayList<>();
    private String accountType = WalletProviderLogoUtils.ACCOUNT_TYPE_BANK;
    private String selectedProvider = "";
    private WalletProviderPickerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_picker);
        readIntentData();
        setupToolbar();
        setupList();
        setupSearch();
    }

    private void readIntentData() {
        Intent intent = getIntent();
        String incomingType = safe(intent == null ? "" : intent.getStringExtra(EXTRA_ACCOUNT_TYPE));
        if (WalletProviderLogoUtils.ACCOUNT_TYPE_EWALLET.equalsIgnoreCase(incomingType)) {
            accountType = WalletProviderLogoUtils.ACCOUNT_TYPE_EWALLET;
        }
        selectedProvider = safe(intent == null ? "" : intent.getStringExtra(EXTRA_SELECTED_PROVIDER));

        ArrayList<String> shortNames = intent == null
            ? null
            : intent.getStringArrayListExtra(EXTRA_PROVIDER_SHORT_NAMES);
        ArrayList<String> displayNames = intent == null
            ? null
            : intent.getStringArrayListExtra(EXTRA_PROVIDER_DISPLAY_NAMES);

        if (shortNames != null) {
            for (int i = 0; i < shortNames.size(); i++) {
                String shortName = safe(shortNames.get(i));
                if (shortName.isEmpty()) {
                    continue;
                }
                String displayName = displayNames != null && i < displayNames.size()
                    ? safe(displayNames.get(i))
                    : shortName;
                sourceOptions.add(new WalletProviderOption(shortName, displayName));
            }
        }
        filteredOptions.addAll(sourceOptions);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarProviderPicker);
        toolbar.setTitle(
            WalletProviderLogoUtils.ACCOUNT_TYPE_EWALLET.equals(accountType)
                ? R.string.picker_title_ewallet
                : R.string.picker_title_bank
        );
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupList() {
        ListView listView = findViewById(R.id.lvProviderPicker);
        adapter = new WalletProviderPickerAdapter(this, filteredOptions, selectedProvider, accountType);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredOptions.size()) {
                return;
            }
            returnSelection(filteredOptions.get(position).getShortName());
        });
    }

    private void setupSearch() {
        View inputSearch = findViewById(R.id.inputProviderPickerSearch);
        TextInputEditText etSearch = findViewById(R.id.etProviderPickerSearch);
        boolean showSearch = WalletProviderLogoUtils.ACCOUNT_TYPE_BANK.equals(accountType) || sourceOptions.size() > 8;
        if (!showSearch) {
            inputSearch.setVisibility(View.GONE);
            return;
        }
        etSearch.setHint(
            WalletProviderLogoUtils.ACCOUNT_TYPE_BANK.equals(accountType)
                ? R.string.search_bank_hint
                : R.string.search_ewallet_hint
        );
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                filterProviders(editable == null ? "" : editable.toString());
            }
        });
    }

    private void filterProviders(String rawQuery) {
        String normalizedQuery = SearchTextUtils.normalize(rawQuery);
        filteredOptions.clear();
        for (WalletProviderOption option : sourceOptions) {
            if (SearchTextUtils.matches(normalizedQuery, option.getShortName(), option.getDisplayName())) {
                filteredOptions.add(option);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void returnSelection(String providerShortName) {
        Intent data = new Intent();
        data.putExtra(EXTRA_SELECTED_PROVIDER, safe(providerShortName));
        setResult(RESULT_OK, data);
        finish();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
