package com.example.myapplication.xmlui;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ReportFilterDialog {

    private ReportFilterDialog() {
    }

    public static void show(
        @NonNull AppCompatActivity activity,
        @NonNull List<Wallet> wallets,
        @NonNull ReportFilterState current,
        @NonNull Consumer<ReportFilterState> onApply
    ) {
        Context context = activity;
        int sidePadding = dp(context, 18);
        int sectionMarginTop = dp(context, 12);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(sidePadding, dp(context, 6), sidePadding, 0);

        TextView walletHeader = sectionHeader(context, context.getString(R.string.report_filter_wallets));
        root.addView(walletHeader);

        CheckBox selectAllWallets = new CheckBox(context);
        selectAllWallets.setText(R.string.label_filter_all);
        root.addView(selectAllWallets);

        List<CheckBox> walletChecks = new ArrayList<>();
        Set<String> selectedWalletIds = new LinkedHashSet<>(current.getWalletIds());
        boolean allWalletsChecked = selectedWalletIds.isEmpty();
        final boolean[] syncingWalletChecks = new boolean[] { false };
        for (Wallet wallet : wallets) {
            CheckBox checkBox = new CheckBox(context);
            checkBox.setText(wallet.getName());
            boolean checked = allWalletsChecked || selectedWalletIds.contains(wallet.getId());
            checkBox.setChecked(checked);
            walletChecks.add(checkBox);
            root.addView(checkBox);
        }
        selectAllWallets.setChecked(allWalletsChecked || areAllChecked(walletChecks));
        selectAllWallets.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncingWalletChecks[0]) {
                return;
            }
            syncingWalletChecks[0] = true;
            for (CheckBox walletCheck : walletChecks) {
                walletCheck.setChecked(isChecked);
            }
            syncingWalletChecks[0] = false;
        });
        for (CheckBox walletCheck : walletChecks) {
            walletCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (syncingWalletChecks[0]) {
                    return;
                }
                syncingWalletChecks[0] = true;
                boolean allChecked = areAllChecked(walletChecks);
                if (selectAllWallets.isChecked() != allChecked) {
                    selectAllWallets.setChecked(allChecked);
                }
                syncingWalletChecks[0] = false;
            });
        }

        TextView typeHeader = sectionHeader(context, context.getString(R.string.report_filter_types));
        LinearLayout.LayoutParams typeHeaderParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        typeHeaderParams.topMargin = sectionMarginTop;
        typeHeader.setLayoutParams(typeHeaderParams);
        root.addView(typeHeader);

        Set<TransactionType> selectedTypes = new LinkedHashSet<>(current.getTransactionTypes());
        boolean allTypesChecked = selectedTypes.isEmpty();
        CheckBox incomeCheck = new CheckBox(context);
        incomeCheck.setText(R.string.transaction_type_income);
        incomeCheck.setChecked(allTypesChecked || selectedTypes.contains(TransactionType.INCOME));
        root.addView(incomeCheck);

        CheckBox expenseCheck = new CheckBox(context);
        expenseCheck.setText(R.string.transaction_type_expense);
        expenseCheck.setChecked(allTypesChecked || selectedTypes.contains(TransactionType.EXPENSE));
        root.addView(expenseCheck);

        CheckBox transferCheck = new CheckBox(context);
        transferCheck.setText(R.string.transaction_type_transfer);
        transferCheck.setChecked(allTypesChecked || selectedTypes.contains(TransactionType.TRANSFER));
        root.addView(transferCheck);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.report_filter_title)
            .setView(root)
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.action_reset_filters, (dialog, which) -> onApply.accept(ReportFilterState.all()))
            .setPositiveButton(R.string.action_apply_filters, (dialog, which) -> {
                Set<String> walletIds = new LinkedHashSet<>();
                for (int i = 0; i < walletChecks.size(); i++) {
                    if (walletChecks.get(i).isChecked()) {
                        walletIds.add(wallets.get(i).getId());
                    }
                }
                if (!walletChecks.isEmpty() && walletIds.size() == walletChecks.size()) {
                    walletIds.clear();
                }

                EnumSet<TransactionType> types = EnumSet.noneOf(TransactionType.class);
                if (incomeCheck.isChecked()) {
                    types.add(TransactionType.INCOME);
                }
                if (expenseCheck.isChecked()) {
                    types.add(TransactionType.EXPENSE);
                }
                if (transferCheck.isChecked()) {
                    types.add(TransactionType.TRANSFER);
                }
                if (types.size() == 3) {
                    types.clear();
                }
                onApply.accept(new ReportFilterState(walletIds, types));
            })
            .show();
    }

    private static TextView sectionHeader(Context context, String text) {
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextSize(14f);
        textView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        return textView;
    }

    private static boolean areAllChecked(List<CheckBox> checks) {
        for (CheckBox check : checks) {
            if (!check.isChecked()) {
                return false;
            }
        }
        return !checks.isEmpty();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
