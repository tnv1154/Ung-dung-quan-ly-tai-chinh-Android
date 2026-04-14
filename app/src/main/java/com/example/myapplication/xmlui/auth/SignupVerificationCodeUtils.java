package com.example.myapplication.xmlui;

import android.net.Uri;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SignupVerificationCodeUtils {

    private static final Pattern CODE_EXACT_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern CODE_NAMED_PATTERN = Pattern.compile(
        "(?:verify_code|verification_code|otp|code)=([0-9]{6})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CODE_ANYWHERE_PATTERN = Pattern.compile("(\\d{6})");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SignupVerificationCodeUtils() {
    }

    public static String generateCode() {
        return String.format(Locale.US, "%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    public static boolean isCodeValid(String code) {
        String value = code == null ? "" : code.trim();
        return CODE_EXACT_PATTERN.matcher(value).matches();
    }

    public static String extractCode(String rawInput) {
        String raw = rawInput == null ? "" : rawInput.trim();
        if (isCodeValid(raw)) {
            return raw;
        }
        try {
            Uri uri = Uri.parse(raw);
            String fromQuery = firstValidCode(
                uri.getQueryParameter("verify_code"),
                uri.getQueryParameter("verification_code"),
                uri.getQueryParameter("otp"),
                uri.getQueryParameter("code")
            );
            if (!fromQuery.isEmpty()) {
                return fromQuery;
            }
            String continueUrl = uri.getQueryParameter("continueUrl");
            if (continueUrl == null || continueUrl.trim().isEmpty()) {
                continueUrl = uri.getQueryParameter("continue_url");
            }
            String fromContinueUrl = extractCodeFromNamedPattern(continueUrl);
            if (!fromContinueUrl.isEmpty()) {
                return fromContinueUrl;
            }
        } catch (Exception ignored) {
        }
        String fromNamedPattern = extractCodeFromNamedPattern(raw);
        if (!fromNamedPattern.isEmpty()) {
            return fromNamedPattern;
        }
        if (looksLikeUrl(raw)) {
            return raw;
        }
        Matcher matcher = CODE_ANYWHERE_PATTERN.matcher(raw);
        if (matcher.find()) {
            String code = matcher.group(1);
            if (isCodeValid(code)) {
                return code;
            }
        }
        return raw;
    }

    public static String maskEmail(String email) {
        return PasswordResetCodeParser.maskEmail(email);
    }

    private static String firstValidCode(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (isCodeValid(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String extractCodeFromNamedPattern(String text) {
        String source = text == null ? "" : text.trim();
        if (source.isEmpty()) {
            return "";
        }
        Matcher matcher = CODE_NAMED_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String decoded = Uri.decode(source);
        matcher = CODE_NAMED_PATTERN.matcher(decoded);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static boolean looksLikeUrl(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("http://")
            || lower.contains("https://")
            || lower.contains("continueurl=")
            || lower.contains("oobcode=");
    }
}
