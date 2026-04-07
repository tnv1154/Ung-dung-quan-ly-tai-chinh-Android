package com.example.myapplication.xmlui;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.example.myapplication.R;

import java.util.Locale;

public final class WalletUiMapper {
    private WalletUiMapper() {
    }

    public static String normalizeAccountType(String rawType) {
        if (rawType == null) {
            return "CASH";
        }
        String value = rawType.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "CASH";
        }
        if (value.equals("TIEN_MAT")) {
            return "CASH";
        }
        if (value.equals("NGAN_HANG")) {
            return "BANK";
        }
        if (value.equals("VI_DIEN_TU")) {
            return "EWALLET";
        }
        if (value.equals("TAI_SAN_KHAC")) {
            return "OTHER";
        }
        return value;
    }

    public static String displayType(String accountType) {
        String type = normalizeAccountType(accountType);
        switch (type) {
            case "BANK":
                return "Tài khoản ngân hàng";
            case "EWALLET":
                return "Ví điện tử";
            case "OTHER":
                return "Tài sản khác";
            default:
                return "Tiền mặt";
        }
    }

    public static String iconForType(String accountType) {
        String type = normalizeAccountType(accountType);
        switch (type) {
            case "BANK":
                return "🏦";
            case "EWALLET":
                return "📱";
            case "OTHER":
                return "💎";
            default:
                return "💵";
        }
    }

    public static String iconForKey(String iconKey, String accountType) {
        if (iconKey == null) {
            return iconForType(accountType);
        }
        String key = iconKey.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "bank":
                return "🏦";
            case "ewallet":
                return "📱";
            case "other":
                return "💎";
            case "card":
                return "💳";
            case "wallet":
                return "👛";
            case "cash":
                return "💵";
            default:
                return iconForType(accountType);
        }
    }

    public static String iconKeyForType(String accountType) {
        String type = normalizeAccountType(accountType);
        switch (type) {
            case "BANK":
                return "bank";
            case "EWALLET":
                return "ewallet";
            case "OTHER":
                return "other";
            default:
                return "cash";
        }
    }

    @DrawableRes
    public static int iconResForKey(String iconKey, String accountType) {
        String key = iconKey == null ? "" : iconKey.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "bank":
                return R.drawable.ic_wallet_bank;
            case "ewallet":
                return R.drawable.ic_wallet_ewallet;
            case "card":
                return R.drawable.ic_wallet_card;
            case "wallet":
                return R.drawable.ic_wallet_wallet;
            case "other":
                return R.drawable.ic_wallet_other;
            case "cash":
                return R.drawable.ic_wallet_cash;
            default:
                return iconResForType(accountType);
        }
    }

    @DrawableRes
    public static int iconResForType(String accountType) {
        String type = normalizeAccountType(accountType);
        switch (type) {
            case "BANK":
                return R.drawable.ic_wallet_bank;
            case "EWALLET":
                return R.drawable.ic_wallet_ewallet;
            case "OTHER":
                return R.drawable.ic_wallet_other;
            default:
                return R.drawable.ic_wallet_cash;
        }
    }

    @ColorRes
    public static int iconBackgroundColor(String accountType) {
        String type = normalizeAccountType(accountType);
        switch (type) {
            case "BANK":
                return R.color.wallet_icon_bank_bg;
            case "EWALLET":
                return R.color.wallet_icon_ewallet_bg;
            case "OTHER":
                return R.color.wallet_icon_other_bg;
            default:
                return R.color.wallet_icon_cash_bg;
        }
    }

    @ColorRes
    public static int iconTintColor(String accountType) {
        String type = normalizeAccountType(accountType);
        switch (type) {
            case "BANK":
                return R.color.wallet_icon_bank_tint;
            case "EWALLET":
                return R.color.wallet_icon_ewallet_tint;
            case "OTHER":
                return R.color.wallet_icon_other_tint;
            default:
                return R.color.wallet_icon_cash_tint;
        }
    }
}
