package com.example.myapplication.xmlui;

public final class WalletProviderOption {
    private final String shortName;
    private final String displayName;

    public WalletProviderOption(String shortName, String displayName) {
        this.shortName = safe(shortName);
        String safeDisplayName = safe(displayName);
        this.displayName = safeDisplayName.isEmpty() ? this.shortName : safeDisplayName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDisplayName() {
        return displayName;
    }

    private static String safe(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
