package com.example.myapplication.xmlui;

public final class AuthPasswordPolicy {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private AuthPasswordPolicy() {
    }

    public static RuleState evaluate(String password) {
        String value = password == null ? "" : password;
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLowerCase(ch)) {
                hasLower = true;
            } else if (Character.isUpperCase(ch)) {
                hasUpper = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }
        return new RuleState(value.length() >= MIN_PASSWORD_LENGTH, hasLower && hasUpper, hasDigit);
    }

    public static boolean isValid(String password) {
        return evaluate(password).isValid();
    }

    public static final class RuleState {
        private final boolean hasMinLength;
        private final boolean hasMixedCase;
        private final boolean hasDigit;

        private RuleState(boolean hasMinLength, boolean hasMixedCase, boolean hasDigit) {
            this.hasMinLength = hasMinLength;
            this.hasMixedCase = hasMixedCase;
            this.hasDigit = hasDigit;
        }

        public boolean hasMinLength() {
            return hasMinLength;
        }

        public boolean hasMixedCase() {
            return hasMixedCase;
        }

        public boolean hasDigit() {
            return hasDigit;
        }

        public boolean isValid() {
            return hasMinLength && hasMixedCase && hasDigit;
        }
    }
}
