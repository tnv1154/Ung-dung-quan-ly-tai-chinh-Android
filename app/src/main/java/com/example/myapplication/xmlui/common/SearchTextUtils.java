package com.example.myapplication.xmlui;

import java.text.Normalizer;
import java.util.Locale;

public final class SearchTextUtils {
    private SearchTextUtils() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String normalizedQuery, String... candidates) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return true;
        }
        if (candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (normalize(candidate).contains(normalizedQuery)) {
                return true;
            }
        }
        return false;
    }
}
