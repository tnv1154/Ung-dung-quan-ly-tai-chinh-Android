package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.CsvImportRow;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.google.firebase.Timestamp;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
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
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^\\p{Alnum}]+");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([+-]?\\d[\\d.,]*)\\s?(vnd|đ|vnđ)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MB_GD_PATTERN = Pattern.compile(
        "GD\\s*:\\s*([+-])\\s*([\\d.,]+)\\s*([A-Za-zđĐ]{1,5})?\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})?\\s*(\\d{1,2}:\\d{2})?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MB_DATE_TIME_PATTERN = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(\\d{1,2}:\\d{2})");
    private static final Pattern MB_ACCOUNT_PATTERN = Pattern.compile("TK\\s*([A-Za-z0-9xX*]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIDV_AMOUNT_PATTERN = Pattern.compile("([+-])\\s*([\\d.,]+)\\s*([A-Za-zđĐ]{1,5})?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIDV_TIME_DATE_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})");
    private static final Pattern BIDV_ACCOUNT_PATTERN = Pattern.compile(
        "(?:Tài khoản thanh toán|Tai khoan thanh toan)\\s*:\\s*([A-Za-z0-9xX*]+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VCB_TRANSACTION_PATTERN = Pattern.compile(
        "(?:Số\\s*dư|So\\s*du)\\s*TK\\s*VCB\\s*([A-Za-z0-9xX*]+)\\s*([+-])\\s*([\\d.,]+)\\s*([A-Za-zđĐ]{1,5})?\\s*(?:lúc|luc)\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VIETIN_TIME_PATTERN = Pattern.compile(
        "(?:Thời\\s*gian|Thoi\\s*gian)\\s*:\\s*(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VIETIN_ACCOUNT_PATTERN = Pattern.compile(
        "(?:Tài\\s*khoản|Tai\\s*khoan)\\s*:\\s*([A-Za-z0-9xX*]+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VIETIN_TRANSACTION_PATTERN = Pattern.compile(
        "(?:Giao\\s*dịch|Giao\\s*dich)\\s*:\\s*([+-])\\s*([\\d.,]+)\\s*([A-Za-zđĐ]{1,5})?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ACCOUNT_TOKEN_PATTERN = Pattern.compile("([A-Za-z0-9xX*]{4,})");
    private static final DateTimeFormatter CSV_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter CSV_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private static final String[] HEADER_STT = {"stt", "sothutu", "no"};
    private static final String[] HEADER_DATE = {"ngay", "ngaygiaodich", "date"};
    private static final String[] HEADER_TIME = {"gio", "thoigian", "time"};
    private static final String[] HEADER_INCOME = {"sotienthu", "tienthu", "thu"};
    private static final String[] HEADER_EXPENSE = {"sotienchi", "tienchi", "chi"};
    private static final String[] HEADER_CURRENCY = {"loaitiente", "tiente", "currency"};
    private static final String[] HEADER_WALLET = {"taikhoan", "vi", "wallet"};
    private static final String[] HEADER_CATEGORY = {"hangmuc", "danhmuc", "category"};
    private static final String[] HEADER_NOTE = {"diengiai", "ghichu", "note"};

    private FinanceParsersKt() {
    }

    public static CsvParseResult parseCsvImportRows(String raw) {
        return parseCsvImportRows(raw, Collections.emptyList());
    }

    public static CsvParseResult parseCsvImportRows(String raw, List<Wallet> wallets) {
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
            return new CsvParseResult(Collections.emptyList(), 0, 0, "File CSV rỗng.");
        }

        List<String> headerCells = parseCsvLine(allLines.get(0));
        List<String> headers = new ArrayList<>(headerCells.size());
        for (String cell : headerCells) {
            headers.add(normalizeToken(cell));
        }

        int idxStt = findHeaderIndex(headers, HEADER_STT);
        int idxDate = findHeaderIndex(headers, HEADER_DATE);
        int idxTime = findHeaderIndex(headers, HEADER_TIME);
        int idxIncome = findHeaderIndex(headers, HEADER_INCOME);
        int idxExpense = findHeaderIndex(headers, HEADER_EXPENSE);
        int idxCurrency = findHeaderIndex(headers, HEADER_CURRENCY);
        int idxWallet = findHeaderIndex(headers, HEADER_WALLET);
        int idxCategory = findHeaderIndex(headers, HEADER_CATEGORY);
        int idxNote = findHeaderIndex(headers, HEADER_NOTE);

        List<String> missing = new ArrayList<>();
        if (idxStt < 0) {
            missing.add("STT");
        }
        if (idxDate < 0) {
            missing.add("Ngày");
        }
        if (idxTime < 0) {
            missing.add("Giờ");
        }
        if (idxIncome < 0) {
            missing.add("Số tiền thu");
        }
        if (idxExpense < 0) {
            missing.add("Số tiền chi");
        }
        if (idxCurrency < 0) {
            missing.add("Loại tiền tệ");
        }
        if (idxWallet < 0) {
            missing.add("Tài khoản");
        }
        if (idxCategory < 0) {
            missing.add("Hạng mục");
        }
        if (idxNote < 0) {
            missing.add("Diễn giải");
        }
        if (!missing.isEmpty()) {
            return new CsvParseResult(
                Collections.emptyList(),
                0,
                0,
                "Thiếu cột bắt buộc: " + String.join(", ", missing) + "."
            );
        }

        Map<String, Wallet> walletsByName = new HashMap<>();
        if (wallets != null) {
            for (Wallet wallet : wallets) {
                if (wallet == null || wallet.getName() == null) {
                    continue;
                }
                walletsByName.put(normalizeToken(wallet.getName()), wallet);
            }
        }

        List<CsvImportRow> parsed = new ArrayList<>();
        int valid = 0;
        int skipped = 0;
        for (int i = 1; i < allLines.size(); i++) {
            List<String> cells = parseCsvLine(allLines.get(i));
            int rowNumber = parseStt(readCell(cells, idxStt), i);
            String dateRaw = readCell(cells, idxDate);
            String timeRaw = readCell(cells, idxTime);
            String incomeRaw = readCell(cells, idxIncome);
            String expenseRaw = readCell(cells, idxExpense);
            String currencyRaw = readCell(cells, idxCurrency);
            String walletName = readCell(cells, idxWallet);
            String categoryRaw = readCell(cells, idxCategory);
            String noteRaw = readCell(cells, idxNote);

            String validationMessage = "";
            LocalDate parsedDate = null;
            LocalTime parsedTime = null;
            if (dateRaw.isBlank()) {
                validationMessage = "Thiếu ngày giao dịch";
            } else {
                parsedDate = parseCsvDate(dateRaw);
                if (parsedDate == null) {
                    validationMessage = "Ngày giao dịch không đúng định dạng DD/MM/YYYY";
                }
            }
            if (validationMessage.isEmpty()) {
                if (timeRaw.isBlank()) {
                    validationMessage = "Thiếu giờ giao dịch";
                } else {
                    parsedTime = parseCsvTime(timeRaw);
                    if (parsedTime == null) {
                        validationMessage = "Giờ giao dịch không đúng định dạng HH:MM";
                    }
                }
            }

            Double incomeAmount = parseMoney(incomeRaw);
            Double expenseAmount = parseMoney(expenseRaw);
            if (validationMessage.isEmpty() && (incomeAmount == null || expenseAmount == null)) {
                validationMessage = "Số tiền thu/chi không hợp lệ";
            }

            boolean hasIncome = incomeAmount != null && incomeAmount > 0.0;
            boolean hasExpense = expenseAmount != null && expenseAmount > 0.0;
            if (validationMessage.isEmpty() && hasIncome == hasExpense) {
                validationMessage = "Chỉ được nhập một trong hai cột Số tiền thu hoặc Số tiền chi";
            }

            TransactionType type = hasIncome ? TransactionType.INCOME : (hasExpense ? TransactionType.EXPENSE : null);
            double amount = hasIncome ? incomeAmount : (hasExpense ? expenseAmount : 0.0);
            String normalizedCurrency = normalizeCurrencyCode(currencyRaw);
            Wallet resolvedWallet = findWalletByName(walletName, wallets, walletsByName);

            if (validationMessage.isEmpty() && normalizedCurrency.isEmpty()) {
                validationMessage = "Thiếu loại tiền tệ";
            }
            if (validationMessage.isEmpty() && (walletName == null || walletName.trim().isEmpty())) {
                validationMessage = "Thiếu tài khoản";
            }
            if (validationMessage.isEmpty() && resolvedWallet == null) {
                validationMessage = "Không tìm thấy tài khoản tương ứng";
            }
            if (validationMessage.isEmpty() && resolvedWallet != null) {
                String walletCurrency = normalizeCurrencyCode(resolvedWallet.getCurrency());
                if (!walletCurrency.equals(normalizedCurrency)) {
                    validationMessage = "Loại tiền tệ không khớp với tài khoản";
                }
            }

            String category = safe(categoryRaw).trim();
            if (category.isEmpty()) {
                category = defaultCategoryForType(type);
            }
            String note = safe(noteRaw).trim();

            Timestamp createdAt = null;
            if (parsedDate != null && parsedTime != null) {
                LocalDateTime localDateTime = LocalDateTime.of(parsedDate, parsedTime);
                java.time.ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
                createdAt = new Timestamp(zonedDateTime.toEpochSecond(), zonedDateTime.getNano());
            }

            boolean isValid = validationMessage.isEmpty();
            if (isValid) {
                valid++;
            } else {
                skipped++;
            }
            parsed.add(
                new CsvImportRow(
                    rowNumber,
                    type,
                    amount,
                    createdAt,
                    normalizedCurrency,
                    category,
                    note,
                    walletName,
                    resolvedWallet == null ? null : resolvedWallet.getId(),
                    isValid,
                    validationMessage
                )
            );
        }

        return new CsvParseResult(parsed, valid, skipped, "");
    }

    public static String buildCsvImportTemplate() {
        return String.join(
            "\n",
            "STT,Ngày,Giờ,Số tiền thu,Số tiền chi,Loại tiền tệ,Tài khoản,Hạng mục,Diễn giải",
            "1,10/11/2023,08:30,15000000,,VND,Ví tiền mặt,Lương,Lương tháng 11",
            "2,15/11/2023,11:15,,500000,VND,Ví tiền mặt,Ăn uống,Mua sắm bách hóa"
        );
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
        return parseNotificationText(raw, null, null);
    }

    public static NotificationDraft parseMbBankNotificationText(String raw, String sourcePackage, String sourceAppName) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty()) {
            return null;
        }
        return parseMbBankNotification(content, sourcePackage, sourceAppName);
    }

    public static NotificationDraft parseBidvNotificationText(String raw, String sourcePackage, String sourceAppName) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty()) {
            return null;
        }
        return parseBidvNotification(content, sourcePackage, sourceAppName);
    }

    public static NotificationDraft parseVietcombankNotificationText(String raw, String sourcePackage, String sourceAppName) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty()) {
            return null;
        }
        return parseVietcombankNotification(content, sourcePackage, sourceAppName);
    }

    public static NotificationDraft parseVietinBankNotificationText(String raw, String sourcePackage, String sourceAppName) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty()) {
            return null;
        }
        return parseVietinBankNotification(content, sourcePackage, sourceAppName);
    }

    public static NotificationDraft parseNotificationText(String raw, String sourcePackage, String sourceAppName) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty()) {
            return null;
        }

        NotificationDraft mbDraft = parseMbBankNotification(content, sourcePackage, sourceAppName);
        if (mbDraft != null) {
            return mbDraft;
        }
        NotificationDraft bidvDraft = parseBidvNotification(content, sourcePackage, sourceAppName);
        if (bidvDraft != null) {
            return bidvDraft;
        }
        NotificationDraft vcbDraft = parseVietcombankNotification(content, sourcePackage, sourceAppName);
        if (vcbDraft != null) {
            return vcbDraft;
        }
        NotificationDraft vietinDraft = parseVietinBankNotification(content, sourcePackage, sourceAppName);
        if (vietinDraft != null) {
            return vietinDraft;
        }

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
        String note = content.substring(0, Math.min(content.length(), 200));
        String source = safe(sourceAppName).trim();
        return new NotificationDraft(
            inferredType,
            amount,
            category,
            note,
            "VND",
            source,
            "",
            0L
        );
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
            Double value = parseMoney(token);
            if (value == null) {
                continue;
            }
            value = Math.abs(value);
            if (max == null || value > max) {
                max = value;
            }
        }
        return max;
    }

    private static NotificationDraft parseMbBankNotification(String content, String sourcePackage, String sourceAppName) {
        if (!isMbBankNotification(content, sourcePackage, sourceAppName)) {
            return null;
        }
        String compact = content.replace('\n', ' ').replace('\r', ' ').trim();
        Matcher gdMatcher = MB_GD_PATTERN.matcher(compact);
        if (!gdMatcher.find()) {
            return null;
        }

        String sign = safe(gdMatcher.group(1)).trim();
        Double parsedAmount = parseMoney(gdMatcher.group(2));
        if (parsedAmount == null) {
            return null;
        }
        double amount = Math.abs(parsedAmount);
        if (amount <= 0.0) {
            return null;
        }

        TransactionType type = "+".equals(sign) ? TransactionType.INCOME : TransactionType.EXPENSE;
        String currency = normalizeMbCurrency(gdMatcher.group(3));

        long txTimeMillis = parseMbDateTimeToMillis(gdMatcher.group(4), gdMatcher.group(5));
        if (txTimeMillis <= 0L) {
            Matcher fallback = MB_DATE_TIME_PATTERN.matcher(compact);
            if (fallback.find()) {
                txTimeMillis = parseMbDateTimeToMillis(fallback.group(1), fallback.group(2));
            }
        }

        String accountHint = extractMbAccountHint(compact);
        String noteSegment = extractSectionValue(compact, "ND:");
        if (noteSegment.isEmpty()) {
            noteSegment = compact;
        }
        noteSegment = noteSegment.trim();

        String normalizedNote = normalizeToken(noteSegment);
        String category;
        if (normalizedNote.contains("chuyentien") || normalizedNote.contains("chuyenkhoan")) {
            category = type == TransactionType.INCOME ? "Thu chuyển khoản" : "Chuyển khoản";
        } else {
            category = defaultCategoryForType(type);
        }

        StringBuilder noteBuilder = new StringBuilder("MB Bank");
        if (!accountHint.isEmpty()) {
            noteBuilder.append(" ").append(accountHint);
        }
        if (!noteSegment.isEmpty()) {
            noteBuilder.append(" - ").append(noteSegment);
        }
        String note = noteBuilder.toString().trim();
        if (note.length() > 200) {
            note = note.substring(0, 200);
        }

        return new NotificationDraft(
            type,
            amount,
            category,
            note,
            currency,
            "MB Bank",
            accountHint,
            txTimeMillis
        );
    }

    private static NotificationDraft parseVietcombankNotification(String content, String sourcePackage, String sourceAppName) {
        if (!isVietcombankNotification(content, sourcePackage, sourceAppName)) {
            return null;
        }
        String compact = content.replace('\n', ' ').replace('\r', ' ').trim();
        Matcher transactionMatcher = VCB_TRANSACTION_PATTERN.matcher(compact);
        if (!transactionMatcher.find()) {
            return null;
        }
        String accountHint = safe(transactionMatcher.group(1)).trim();
        String sign = safe(transactionMatcher.group(2)).trim();
        Double parsedAmount = parseMoney(transactionMatcher.group(3));
        if (parsedAmount == null) {
            return null;
        }
        double amount = Math.abs(parsedAmount);
        if (amount <= 0.0) {
            return null;
        }
        TransactionType type = "+".equals(sign) ? TransactionType.INCOME : TransactionType.EXPENSE;
        String currency = normalizeCurrencyToken(transactionMatcher.group(4));
        long txTimeMillis = parseVcbDateTimeToMillis(transactionMatcher.group(5), transactionMatcher.group(6));

        String noteSegment = firstNonBlank(
            extractAfterMarker(compact, "Ref "),
            extractAfterMarker(compact, "REF "),
            extractAfterMarker(compact, "Ref."),
            extractAfterMarker(compact, "REF."),
            compact
        );
        noteSegment = noteSegment.trim();
        String normalizedNote = normalizeToken(noteSegment);
        String category;
        if (normalizedNote.contains("chuyentien")
            || normalizedNote.contains("chuyenkhoan")
            || normalizedNote.contains("cttu")) {
            category = type == TransactionType.INCOME ? "Thu chuyển khoản" : "Chuyển khoản";
        } else {
            category = defaultCategoryForType(type);
        }

        StringBuilder noteBuilder = new StringBuilder("Vietcombank");
        if (!accountHint.isEmpty()) {
            noteBuilder.append(" ").append(accountHint);
        }
        if (!noteSegment.isEmpty()) {
            noteBuilder.append(" - ").append(noteSegment);
        }
        String note = noteBuilder.toString().trim();
        if (note.length() > 200) {
            note = note.substring(0, 200);
        }
        return new NotificationDraft(
            type,
            amount,
            category,
            note,
            currency,
            "Vietcombank",
            accountHint,
            txTimeMillis
        );
    }

    private static NotificationDraft parseVietinBankNotification(String content, String sourcePackage, String sourceAppName) {
        if (!isVietinBankNotification(content, sourcePackage, sourceAppName)) {
            return null;
        }
        String compact = content.replace('\n', ' ').replace('\r', ' ').trim();

        Matcher transactionMatcher = VIETIN_TRANSACTION_PATTERN.matcher(compact);
        if (!transactionMatcher.find()) {
            return null;
        }
        String sign = safe(transactionMatcher.group(1)).trim();
        Double parsedAmount = parseMoney(transactionMatcher.group(2));
        if (parsedAmount == null) {
            return null;
        }
        double amount = Math.abs(parsedAmount);
        if (amount <= 0.0) {
            return null;
        }
        TransactionType type = "+".equals(sign) ? TransactionType.INCOME : TransactionType.EXPENSE;
        String currency = normalizeCurrencyToken(transactionMatcher.group(3));

        Matcher accountMatcher = VIETIN_ACCOUNT_PATTERN.matcher(compact);
        String accountHint = accountMatcher.find() ? safe(accountMatcher.group(1)).trim() : "";

        long txTimeMillis = 0L;
        Matcher timeMatcher = VIETIN_TIME_PATTERN.matcher(compact);
        if (timeMatcher.find()) {
            txTimeMillis = parseDateTimeToMillis(safe(timeMatcher.group(1)), safe(timeMatcher.group(2)));
        }

        String noteSegment = firstNonBlank(
            extractAfterMarker(compact, "Nội dung:"),
            extractAfterMarker(compact, "Noi dung:"),
            compact
        );
        noteSegment = noteSegment.trim();
        String normalizedNote = normalizeToken(noteSegment);

        String category;
        if (normalizedNote.contains("chuyentien")
            || normalizedNote.contains("chuyenkhoan")
            || normalizedNote.contains("ctden")
            || normalizedNote.contains("cttu")) {
            category = type == TransactionType.INCOME ? "Thu chuyển khoản" : "Chuyển khoản";
        } else {
            category = defaultCategoryForType(type);
        }

        StringBuilder noteBuilder = new StringBuilder("VietinBank");
        if (!accountHint.isEmpty()) {
            noteBuilder.append(" ").append(accountHint);
        }
        if (!noteSegment.isEmpty()) {
            noteBuilder.append(" - ").append(noteSegment);
        }
        String note = noteBuilder.toString().trim();
        if (note.length() > 200) {
            note = note.substring(0, 200);
        }
        return new NotificationDraft(
            type,
            amount,
            category,
            note,
            currency,
            "VietinBank",
            accountHint,
            txTimeMillis
        );
    }

    private static NotificationDraft parseBidvNotification(String content, String sourcePackage, String sourceAppName) {
        if (!isBidvNotification(content, sourcePackage, sourceAppName)) {
            return null;
        }
        String compact = content.replace('\n', ' ').replace('\r', ' ').trim();
        String amountSection = firstNonBlank(
            extractSectionValue(compact, "Số tiền GD:"),
            extractSectionValue(compact, "So tien GD:")
        );
        if (amountSection.isEmpty()) {
            return null;
        }
        Matcher amountMatcher = BIDV_AMOUNT_PATTERN.matcher(amountSection);
        if (!amountMatcher.find()) {
            return null;
        }
        String sign = safe(amountMatcher.group(1)).trim();
        Double parsedAmount = parseMoney(amountMatcher.group(2));
        if (parsedAmount == null) {
            return null;
        }
        double amount = Math.abs(parsedAmount);
        if (amount <= 0.0) {
            return null;
        }
        TransactionType type = "+".equals(sign) ? TransactionType.INCOME : TransactionType.EXPENSE;
        String currency = normalizeCurrencyToken(amountMatcher.group(3));

        String timeSection = firstNonBlank(
            extractSectionValue(compact, "Thời gian giao dịch:"),
            extractSectionValue(compact, "Thoi gian giao dich:")
        );
        long txTimeMillis = 0L;
        Matcher timeMatcher = BIDV_TIME_DATE_PATTERN.matcher(timeSection.isEmpty() ? compact : timeSection);
        if (timeMatcher.find()) {
            txTimeMillis = parseDateTimeToMillis(safe(timeMatcher.group(2)), safe(timeMatcher.group(1)));
        }

        String accountHint = firstNonBlank(
            extractBidvAccountHint(content),
            extractBidvAccountHint(compact),
            extractFirstAccountToken(
                firstNonBlank(
                    extractSectionValue(compact, "Tài khoản thanh toán:"),
                    extractSectionValue(compact, "Tai khoan thanh toan:")
                )
            )
        );
        String noteSegment = firstNonBlank(
            extractBetweenMarkers(compact, "Nội dung giao dịch:", "Mã giao dịch:"),
            extractBetweenMarkers(compact, "Noi dung giao dich:", "Ma giao dich:"),
            extractSectionValue(compact, "Nội dung giao dịch:"),
            extractSectionValue(compact, "Noi dung giao dich:")
        );
        if (noteSegment.isEmpty()) {
            noteSegment = compact;
        }
        noteSegment = noteSegment.trim();

        String normalizedNote = normalizeToken(noteSegment);
        String category;
        if (normalizedNote.contains("chuyentien") || normalizedNote.contains("chuyenkhoan")) {
            category = type == TransactionType.INCOME ? "Thu chuyển khoản" : "Chuyển khoản";
        } else {
            category = defaultCategoryForType(type);
        }

        StringBuilder noteBuilder = new StringBuilder("BIDV");
        if (!accountHint.isEmpty()) {
            noteBuilder.append(" ").append(accountHint);
        }
        if (!noteSegment.isEmpty()) {
            noteBuilder.append(" - ").append(noteSegment);
        }
        String note = noteBuilder.toString().trim();
        if (note.length() > 200) {
            note = note.substring(0, 200);
        }
        return new NotificationDraft(
            type,
            amount,
            category,
            note,
            currency,
            "BIDV SmartBanking",
            accountHint,
            txTimeMillis
        );
    }

    private static boolean isMbBankNotification(String content, String sourcePackage, String sourceAppName) {
        String normalizedContent = normalizeToken(content);
        String normalizedPackage = normalizeToken(sourcePackage);
        String normalizedSource = normalizeToken(sourceAppName);

        boolean sourceLooksMb = normalizedPackage.contains("mbbank")
            || normalizedPackage.contains("mbmobile")
            || (normalizedPackage.contains("mb") && (normalizedPackage.contains("bank") || normalizedPackage.contains("mobile")))
            || normalizedSource.contains("mbbank")
            || normalizedSource.contains("mbbankbaygio");
        if (sourceLooksMb) {
            return true;
        }
        return normalizedContent.contains("thongbaobiendongsodu")
            && normalizedContent.contains("gd")
            && normalizedContent.contains("tk");
    }

    private static boolean isBidvNotification(String content, String sourcePackage, String sourceAppName) {
        String normalizedContent = normalizeToken(content);
        String normalizedPackage = normalizeToken(sourcePackage);
        String normalizedSource = normalizeToken(sourceAppName);

        boolean sourceLooksBidv = normalizedPackage.contains("bidv")
            || normalizedPackage.contains("smartbanking")
            || normalizedSource.contains("bidv")
            || normalizedSource.contains("smartbanking");
        if (sourceLooksBidv) {
            return true;
        }
        return normalizedContent.contains("thongbaobidv")
            && normalizedContent.contains("sotiengd")
            && normalizedContent.contains("taikhoanthanhtoan");
    }

    private static boolean isVietcombankNotification(String content, String sourcePackage, String sourceAppName) {
        String normalizedContent = normalizeToken(content);
        String normalizedPackage = normalizeToken(sourcePackage);
        String normalizedSource = normalizeToken(sourceAppName);

        boolean sourceLooksVcb = normalizedPackage.contains("vietcombank")
            || normalizedPackage.contains("vcbdigibank")
            || normalizedSource.contains("vietcombank")
            || normalizedSource.contains("vcb");
        if (sourceLooksVcb) {
            return true;
        }
        return normalizedContent.contains("sodutkvcb")
            && normalizedContent.contains("luc")
            && normalizedContent.contains("ref");
    }

    private static boolean isVietinBankNotification(String content, String sourcePackage, String sourceAppName) {
        String normalizedContent = normalizeToken(content);
        String normalizedPackage = normalizeToken(sourcePackage);
        String normalizedSource = normalizeToken(sourceAppName);

        boolean sourceLooksVietin = normalizedPackage.contains("vietin")
            || normalizedSource.contains("vietin")
            || normalizedSource.contains("vietinbank");
        if (sourceLooksVietin) {
            return true;
        }
        return normalizedContent.contains("thoigian")
            && normalizedContent.contains("taikhoan")
            && normalizedContent.contains("giaodich")
            && normalizedContent.contains("soduhientai")
            && normalizedContent.contains("noidung");
    }

    private static String extractMbAccountHint(String content) {
        Matcher matcher = MB_ACCOUNT_PATTERN.matcher(content);
        if (!matcher.find()) {
            return "";
        }
        return safe(matcher.group(1)).trim();
    }

    private static String extractBidvAccountHint(String content) {
        Matcher matcher = BIDV_ACCOUNT_PATTERN.matcher(safe(content));
        if (!matcher.find()) {
            return "";
        }
        return safe(matcher.group(1)).trim();
    }

    private static String extractFirstAccountToken(String raw) {
        Matcher matcher = ACCOUNT_TOKEN_PATTERN.matcher(safe(raw));
        if (!matcher.find()) {
            return "";
        }
        return safe(matcher.group(1)).trim();
    }

    private static String extractSectionValue(String content, String marker) {
        if (content == null || marker == null || marker.trim().isEmpty()) {
            return "";
        }
        String upper = content.toUpperCase(Locale.ROOT);
        String upperMarker = marker.toUpperCase(Locale.ROOT);
        int start = upper.indexOf(upperMarker);
        if (start < 0) {
            return "";
        }
        int from = start + marker.length();
        int end = content.indexOf("|", from);
        if (end < 0) {
            end = content.length();
        }
        return safe(content.substring(from, end)).trim();
    }

    private static long parseMbDateTimeToMillis(String rawDate, String rawTime) {
        return parseDateTimeToMillis(rawDate, rawTime);
    }

    private static long parseVcbDateTimeToMillis(String rawDate, String rawTime) {
        String datePart = safe(rawDate).trim().replace('/', '-');
        String timePart = safe(rawTime).trim();
        if (datePart.isEmpty() || timePart.isEmpty()) {
            return 0L;
        }
        String[] dateParts = datePart.split("-");
        String[] timeParts = timePart.split(":");
        if (dateParts.length != 3 || timeParts.length < 2) {
            return 0L;
        }
        try {
            int day = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int year = Integer.parseInt(dateParts[2]);
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            int second = timeParts.length >= 3 ? Integer.parseInt(timeParts[2]) : 0;
            if (year < 100) {
                year += 2000;
            }
            LocalDateTime localDateTime = LocalDateTime.of(year, month, day, hour, minute, second);
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (NumberFormatException | DateTimeException ignored) {
            return 0L;
        }
    }

    private static long parseDateTimeToMillis(String rawDate, String rawTime) {
        String datePart = safe(rawDate).trim();
        String timePart = safe(rawTime).trim();
        if (datePart.isEmpty() || timePart.isEmpty()) {
            return 0L;
        }
        String[] dateParts = datePart.split("/");
        String[] timeParts = timePart.split(":");
        if (dateParts.length != 3 || timeParts.length != 2) {
            return 0L;
        }
        try {
            int day = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int year = Integer.parseInt(dateParts[2]);
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            if (year < 100) {
                year += 2000;
            }
            LocalDateTime localDateTime = LocalDateTime.of(year, month, day, hour, minute);
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (NumberFormatException | DateTimeException ignored) {
            return 0L;
        }
    }

    private static String normalizeMbCurrency(String rawCurrency) {
        return normalizeCurrencyToken(rawCurrency);
    }

    private static String normalizeCurrencyToken(String rawCurrency) {
        String currency = safe(rawCurrency).trim().toUpperCase(Locale.ROOT);
        if (currency.isEmpty()) {
            return "VND";
        }
        if ("Đ".equals(currency) || "VNĐ".equals(currency) || "VND".equals(currency)) {
            return "VND";
        }
        return currency;
    }

    private static String extractBetweenMarkers(String content, String startMarker, String endMarker) {
        if (content == null || startMarker == null || startMarker.trim().isEmpty()) {
            return "";
        }
        String upper = content.toUpperCase(Locale.ROOT);
        String upperStart = startMarker.toUpperCase(Locale.ROOT);
        int start = upper.indexOf(upperStart);
        if (start < 0) {
            return "";
        }
        int from = start + startMarker.length();
        int end = content.length();
        if (endMarker != null && !endMarker.trim().isEmpty()) {
            String upperEnd = endMarker.toUpperCase(Locale.ROOT);
            int candidateEnd = upper.indexOf(upperEnd, from);
            if (candidateEnd >= 0) {
                end = candidateEnd;
            }
        }
        if (from >= end || from >= content.length()) {
            return "";
        }
        return safe(content.substring(from, end)).trim();
    }

    private static String extractAfterMarker(String content, String marker) {
        if (content == null || marker == null || marker.trim().isEmpty()) {
            return "";
        }
        String upper = content.toUpperCase(Locale.ROOT);
        String upperMarker = marker.toUpperCase(Locale.ROOT);
        int start = upper.indexOf(upperMarker);
        if (start < 0) {
            return "";
        }
        int from = start + marker.length();
        if (from >= content.length()) {
            return "";
        }
        return safe(content.substring(from)).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = safe(value).trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return "";
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
        return NON_ALNUM_PATTERN.matcher(normalized).replaceAll("");
    }

    public static Wallet findWalletByName(String walletName, List<Wallet> wallets) {
        Map<String, Wallet> exactMap = new HashMap<>();
        if (wallets != null) {
            for (Wallet wallet : wallets) {
                if (wallet == null || wallet.getName() == null) {
                    continue;
                }
                exactMap.put(normalizeToken(wallet.getName()), wallet);
            }
        }
        return findWalletByName(walletName, wallets, exactMap);
    }

    public static List<String> parseCsvLine(String line) {
        String content = line == null ? "" : line;
        List<String> result = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                result.add(cell.toString().trim());
                cell.setLength(0);
                continue;
            }
            cell.append(ch);
        }
        result.add(cell.toString().trim());
        return result;
    }

    private static String readCell(List<String> cells, int index) {
        if (cells == null || index < 0 || index >= cells.size()) {
            return "";
        }
        return safe(cells.get(index)).trim();
    }

    private static int parseStt(String raw, int fallback) {
        String value = safe(raw).trim();
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static LocalDate parseCsvDate(String raw) {
        try {
            return LocalDate.parse(raw.trim(), CSV_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalTime parseCsvTime(String raw) {
        try {
            return LocalTime.parse(raw.trim(), CSV_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Wallet findWalletByName(String walletName, List<Wallet> wallets, Map<String, Wallet> exactMap) {
        String keyword = normalizeToken(walletName);
        if (keyword.isEmpty()) {
            return null;
        }
        if (exactMap != null) {
            Wallet exact = exactMap.get(keyword);
            if (exact != null) {
                return exact;
            }
        }
        if (wallets == null || wallets.isEmpty()) {
            return null;
        }

        Wallet best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean tie = false;
        for (Wallet wallet : wallets) {
            if (wallet == null || wallet.getName() == null) {
                continue;
            }
            String candidate = normalizeToken(wallet.getName());
            int score = scoreWalletMatch(keyword, candidate);
            if (score < 0) {
                continue;
            }
            if (score < bestScore) {
                best = wallet;
                bestScore = score;
                tie = false;
            } else if (score == bestScore) {
                tie = true;
            }
        }
        return tie ? null : best;
    }

    private static int scoreWalletMatch(String keyword, String candidate) {
        if (keyword == null || candidate == null || keyword.isEmpty() || candidate.isEmpty()) {
            return -1;
        }
        if (keyword.equals(candidate)) {
            return 0;
        }
        if (candidate.startsWith(keyword) || keyword.startsWith(candidate)) {
            return 10 + Math.abs(candidate.length() - keyword.length());
        }
        if (candidate.contains(keyword) || keyword.contains(candidate)) {
            return 20 + Math.abs(candidate.length() - keyword.length());
        }
        return -1;
    }

    private static int findHeaderIndex(List<String> normalizedHeaders, String[] aliases) {
        if (normalizedHeaders == null || aliases == null) {
            return -1;
        }
        for (int i = 0; i < normalizedHeaders.size(); i++) {
            String header = normalizedHeaders.get(i);
            if (header == null || header.isBlank()) {
                continue;
            }
            for (String alias : aliases) {
                if (alias.equals(header)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String normalizeCurrencyCode(String raw) {
        return safe(raw).trim().toUpperCase(Locale.ROOT);
    }

    private static Double parseMoney(String raw) {
        String value = safe(raw).trim();
        if (value.isEmpty()) {
            return 0.0;
        }
        value = value.replaceAll("[^0-9,.-]", "");

        int lastDot = value.lastIndexOf('.');
        int lastComma = value.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            if (lastDot > lastComma) {
                value = value.replace(",", "");
            } else {
                value = value.replace(".", "");
                value = value.replace(',', '.');
            }
        } else if (lastComma >= 0) {
            if (value.indexOf(',') != lastComma) {
                value = value.replace(",", "");
            } else {
                int digitsAfter = value.length() - lastComma - 1;
                if (digitsAfter == 3) {
                    value = value.replace(",", "");
                } else {
                    value = value.replace(',', '.');
                }
            }
        } else if (lastDot >= 0) {
            if (value.indexOf('.') != lastDot) {
                value = value.replace(".", "");
            } else {
                int digitsAfter = value.length() - lastDot - 1;
                if (digitsAfter == 3) {
                    value = value.replace(".", "");
                }
            }
        }
        if (value.isEmpty() || "-".equals(value) || ".".equals(value) || ",".equals(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
