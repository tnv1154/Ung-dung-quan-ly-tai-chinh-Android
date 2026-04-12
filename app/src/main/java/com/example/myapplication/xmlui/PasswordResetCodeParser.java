package com.example.myapplication.xmlui;

import android.net.Uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PasswordResetCodeParser {

    private static final Pattern CODE_PATTERN = Pattern.compile("oobCode=([^&\\s]+)");

    private PasswordResetCodeParser() {
    }

    public static String extractCode(String rawInput) {
        String raw = rawInput == null ? "" : rawInput.trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (!raw.contains("oobCode=")) {
            return raw;
        }
        try {
            Uri uri = Uri.parse(raw);
            String code = uri.getQueryParameter("oobCode");
            if (code != null && !code.trim().isEmpty()) {
                return code.trim();
            }
        } catch (Exception ignored) {
        }
        Matcher matcher = CODE_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return raw;
    }

    public static String maskEmail(String email) {
        String trimmed = email == null ? "" : email.trim();
        int index = trimmed.indexOf('@');
        if (index <= 0 || index >= trimmed.length() - 1) {
            return trimmed;
        }
        String local = trimmed.substring(0, index);
        String domain = trimmed.substring(index + 1);
        String prefix = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 3);
        return prefix + "********@" + domain;
    }
}
