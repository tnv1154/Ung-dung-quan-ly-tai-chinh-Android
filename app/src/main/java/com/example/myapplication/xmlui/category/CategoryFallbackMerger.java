package com.example.myapplication.xmlui;

import com.example.myapplication.finance.model.TransactionCategory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CategoryFallbackMerger {

    private CategoryFallbackMerger() {
    }

    public static List<TransactionCategory> mergeWithFallbacks(List<TransactionCategory> source) {
        List<TransactionCategory> merged = new ArrayList<>();
        if (source != null) {
            merged.addAll(source);
        }

        Set<String> identities = new HashSet<>();
        for (TransactionCategory item : merged) {
            identities.add(identity(item));
        }

        for (TransactionCategory fallback : DefaultCategoryProvider.createDefaultCategories()) {
            String identity = identity(fallback);
            if (identities.contains(identity)) {
                continue;
            }
            merged.add(fallback);
            identities.add(identity);
        }

        return merged;
    }

    private static String identity(TransactionCategory category) {
        if (category == null) {
            return "null";
        }
        return category.getType().name()
            + "|"
            + normalize(category.getParentName())
            + "|"
            + normalize(category.getName());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
