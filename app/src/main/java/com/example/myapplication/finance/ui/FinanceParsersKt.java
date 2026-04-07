package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.CsvImportRow;
import com.example.myapplication.finance.model.TransactionType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FinanceParsersKt {
    private static final Pattern MARKS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern TOKEN_GAP_PATTERN = Pattern.compile("[\\s_\\-]+");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([+-]?\\d[\\d.,]*)\\s?(vnd|đ|vnđ)?", Pattern.CASE_INSENSITIVE);

    private FinanceParsersKt() {
    }

    public static CsvParseResult parseCsvImportRows(String raw) {
        String source = raw == null ? "" : raw;
        String[] splitLines = source.split("\\R");
        List<String> allLines = new ArrayList<>();
        for (String line : splitLines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                allLines.add(trimmed);
            }
        }
        if (allLines.isEmpty()) {
            return new CsvParseResult(Collections.emptyList(), 0);
        }

        List<String> headerCells = parseCsvLine(allLines.get(0));
        List<String> headers = new ArrayList<>(headerCells.size());
        for (String cell : headerCells) {
            headers.add(normalizeToken(cell));
        }

        List<CsvImportRow> parsed = new ArrayList<>();
        int skipped = 0;
        for (int i = 1; i < allLines.size(); i++) {
            List<String> cells = parseCsvLine(allLines.get(i));
            Map<String, String> rowMap = new HashMap<>();
            for (int index = 0; index < headers.size(); index++) {
                String key = headers.get(index);
                if (key == null || key.isBlank()) {
                    continue;
                }
                String value = index < cells.size() ? safe(cells.get(index)).trim() : "";
                rowMap.put(key, value);
            }

            String typeRaw = firstPresent(rowMap, "type", "loai", "transactiontype");
            String amountRaw = firstPresent(rowMap, "amount", "sotien");
            String category = firstPresent(rowMap, "category", "hangmuc", "danhmuc");
            String note = firstPresent(rowMap, "note", "ghichu");
            String wallet = firstPresent(rowMap, "wallet", "vi", "walletname");
            String toWallet = firstPresent(rowMap, "towallet", "vidich", "walletto");

            TransactionType type = parseTransactionType(typeRaw);
            Double amount = parseAmount(amountRaw);
            if (type == null || amount == null || amount <= 0) {
                skipped++;
                continue;
            }

            String finalCategory = category == null || category.isBlank() ? defaultCategoryForType(type) : category;
            parsed.add(new CsvImportRow(type, amount, finalCategory, safe(note), wallet, toWallet));
        }

        return new CsvParseResult(parsed, skipped);
    }

    public static TransactionType parseTransactionType(String raw) {
        String token = normalizeToken(raw == null ? "" : raw);
        switch (token) {
            case "income":
            case "thu":
            case "thutien":
            case "in":
                return TransactionType.INCOME;
            case "expense":
            case "chi":
            case "chitien":
            case "out":
                return TransactionType.EXPENSE;
            case "transfer":
            case "chuyenkhoan":
            case "ck":
                return TransactionType.TRANSFER;
            default:
                return null;
        }
    }

    public static NotificationDraft parseNotificationText(String raw) {
        String content = raw == null ? "" : raw;
        String normalized = normalizeToken(content);
        Double amount = extractAmountFromText(content);
        if (amount == null) {
            return null;
        }

        TransactionType inferredType;
        if (normalized.contains("nhan") || normalized.contains("cong") || normalized.contains("plus")) {
            inferredType = TransactionType.INCOME;
        } else if (normalized.contains("tru") || normalized.contains("thanhtoan") || normalized.contains("chitieu")) {
            inferredType = TransactionType.EXPENSE;
        } else {
            inferredType = content.contains("+") ? TransactionType.INCOME : TransactionType.EXPENSE;
        }

        String category = inferredType == TransactionType.INCOME ? "Thu từ thông báo" : "Chi từ thông báo";
        return new NotificationDraft(inferredType, amount, category, content.trim().substring(0, Math.min(content.trim().length(), 200)));
    }

    public static Double extractAmountFromText(String text) {
        String content = text == null ? "" : text;
        Matcher matcher = AMOUNT_PATTERN.matcher(content);
        Double max = null;
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null) {
                continue;
            }
            String cleaned = token.replace(".", "").replace(",", "");
            Double value = parseAmount(cleaned);
            if (value == null) {
                continue;
            }
            if (max == null || value > max) {
                max = value;
            }
        }
        return max;
    }

    public static String defaultCategoryForType(TransactionType type) {
        if (type == TransactionType.INCOME) {
            return "Thu khác";
        }
        if (type == TransactionType.TRANSFER) {
            return "Chuyển khoản";
        }
        return "Chi khác";
    }

    public static String normalizeToken(String input) {
        String content = input == null ? "" : input;
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFD);
        normalized = MARKS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT);
        return TOKEN_GAP_PATTERN.matcher(normalized).replaceAll("");
    }

    public static List<String> parseCsvLine(String line) {
        String content = line == null ? "" : line;
        List<String> result = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                result.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        result.add(cell.toString());
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String firstPresent(Map<String, String> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static Double parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

