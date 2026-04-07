package com.example.myapplication.xmlui;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.example.myapplication.R;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;

import java.text.Normalizer;
import java.util.Locale;

public final class CategoryUiHelper {

    private CategoryUiHelper() {
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "HM";
        }
        String[] tokens = name.trim().split("\\s+");
        if (tokens.length == 1) {
            String token = tokens[0];
            return token.substring(0, Math.min(2, token.length())).toUpperCase(Locale.ROOT);
        }
        String first = tokens[0].substring(0, 1);
        String second = tokens[tokens.length - 1].substring(0, 1);
        return (first + second).toUpperCase(Locale.ROOT);
    }

    @ColorRes
    public static int badgeBg(TransactionType type) {
        return type == TransactionType.INCOME ? R.color.group_cash_bg : R.color.group_bank_bg;
    }

    @ColorRes
    public static int badgeText(TransactionType type) {
        return type == TransactionType.INCOME ? R.color.group_cash_tint : R.color.group_bank_tint;
    }

    public static String canonicalIconKey(String iconKey, TransactionType type) {
        String key = normalize(iconKey);
        if (key.isEmpty()) {
            return type == TransactionType.INCOME ? "money_in" : "dot";
        }
        switch (key) {
            case "food":
            case "market":
            case "coffee":
            case "restaurant":
                return "food";
            case "transport":
            case "fuel":
            case "taxi":
            case "parking":
            case "travel":
                return "transport";
            case "utility":
            case "electricity":
            case "water":
            case "internet":
            case "phone":
                return "utility";
            case "moneyin":
            case "salary":
            case "bonus":
            case "debtin":
            case "interest":
                return "money_in";
            case "moneyout":
            case "debtout":
            case "loan":
            case "invest":
                return "money_out";
            case "gift":
            case "giftin":
            case "giftout":
            case "wedding":
            case "funeral":
            case "ceremony":
                return "gift";
            case "health":
            case "clinic":
            case "medicine":
            case "insurance":
                return "health";
            case "home":
            case "rent":
            case "furniture":
            case "repair":
                return "home";
            case "dot":
            case "default":
            case "other":
            case "otherin":
            case "otherout":
            case "child":
            case "school":
            case "study":
            case "book":
            case "course":
            case "growth":
            case "baby":
            case "play":
            case "outfit":
            case "clothes":
            case "shoes":
            case "accessory":
            case "shopping":
            case "entertainment":
            case "enjoy":
                return "other";
            default:
                return type == TransactionType.INCOME ? "money_in" : "other";
        }
    }

    @DrawableRes
    public static int iconResForCategory(TransactionCategory category) {
        if (category == null) {
            return R.drawable.ic_category_dot;
        }
        return iconResForKey(category.getIconKey(), category.getType());
    }

    @DrawableRes
    public static int iconResForKey(String iconKey, TransactionType type) {
        String key = canonicalIconKey(iconKey, type);
        switch (key) {
            case "food":
                return R.drawable.ic_category_food;
            case "transport":
                return R.drawable.ic_category_transport;
            case "utility":
                return R.drawable.ic_category_utility;
            case "money_in":
                return R.drawable.ic_category_money_in;
            case "money_out":
                return R.drawable.ic_category_money_out;
            case "gift":
                return R.drawable.ic_category_gift;
            case "health":
                return R.drawable.ic_category_health;
            case "home":
                return R.drawable.ic_category_home;
            case "other":
                return R.drawable.ic_category_other;
            default:
                return R.drawable.ic_category_dot;
        }
    }

    @ColorRes
    public static int iconBgForCategory(TransactionCategory category) {
        if (category == null) {
            return R.color.group_other_bg;
        }
        return iconBgForKey(category.getIconKey(), category.getType());
    }

    @ColorRes
    public static int iconTintForCategory(TransactionCategory category) {
        if (category == null) {
            return R.color.group_other_tint;
        }
        return iconTintForKey(category.getIconKey(), category.getType());
    }

    @ColorRes
    public static int iconBgForKey(String iconKey, TransactionType type) {
        String key = canonicalIconKey(iconKey, type);
        switch (key) {
            case "food":
                return R.color.group_cash_bg;
            case "transport":
                return R.color.group_bank_bg;
            case "utility":
                return R.color.chip_bg;
            case "money_in":
                return R.color.overview_icon_income_bg;
            case "money_out":
                return R.color.overview_icon_expense_bg;
            case "gift":
                return R.color.group_ewallet_bg;
            case "health":
                return R.color.overview_warning_bg;
            case "home":
                return R.color.overview_icon_recent_bg;
            case "other":
                return R.color.group_other_bg;
            default:
                return type == TransactionType.INCOME ? R.color.group_cash_bg : R.color.group_bank_bg;
        }
    }

    @ColorRes
    public static int iconTintForKey(String iconKey, TransactionType type) {
        String key = canonicalIconKey(iconKey, type);
        switch (key) {
            case "food":
                return R.color.group_cash_tint;
            case "transport":
                return R.color.group_bank_tint;
            case "utility":
                return R.color.chip_text;
            case "money_in":
                return R.color.overview_icon_income_tint;
            case "money_out":
                return R.color.overview_icon_expense_tint;
            case "gift":
                return R.color.group_ewallet_tint;
            case "health":
                return R.color.overview_warning_text;
            case "home":
                return R.color.overview_icon_recent_tint;
            case "other":
                return R.color.group_other_tint;
            default:
                return type == TransactionType.INCOME ? R.color.group_cash_tint : R.color.group_bank_tint;
        }
    }

    public static boolean matchesSearch(TransactionCategory category, String query) {
        if (category == null) {
            return false;
        }
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String needle = normalize(query);
        return normalize(category.getName()).contains(needle)
            || normalize(category.getParentName()).contains(needle);
    }

    public static String normalize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }
}
