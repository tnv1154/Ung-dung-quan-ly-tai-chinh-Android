package com.example.myapplication.finance.ui;

import android.content.Context;
import android.net.Uri;

import com.example.myapplication.finance.model.FinanceTransaction;
import com.example.myapplication.finance.model.TransactionType;
import com.google.firebase.Timestamp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class FinanceTimeAndIoKt {
    private FinanceTimeAndIoKt() {
    }

    public static double calculateCurrentMonthExpenseForCategory(List<FinanceTransaction> transactions, String category) {
        ZonedDateTime now = ZonedDateTime.now();
        double total = 0.0;
        for (FinanceTransaction tx : transactions) {
            ZonedDateTime time = toZonedDateTime(tx.getCreatedAt());
            boolean sameMonth = time.getYear() == now.getYear() && time.getMonth() == now.getMonth();
            if (sameMonth && tx.getType() != TransactionType.INCOME && tx.getCategory().equalsIgnoreCase(category)) {
                total += tx.getAmount();
            }
        }
        return total;
    }

    public static String readTextFromUri(Context context, Uri uri) {
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, count);
                }
                return builder.toString();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static boolean writeTextToUri(Context context, Uri uri, String content) {
        try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
            if (output != null) {
                output.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static List<FinanceTransaction> filterTransactionsByPeriod(List<FinanceTransaction> transactions, ReportPeriod period) {
        ZonedDateTime now = ZonedDateTime.now();
        List<FinanceTransaction> filtered = new ArrayList<>();
        for (FinanceTransaction tx : transactions) {
            ZonedDateTime time = toZonedDateTime(tx.getCreatedAt());
            boolean include;
            switch (period) {
                case DAY:
                    include = time.toLocalDate().equals(now.toLocalDate());
                    break;
                case WEEK:
                    java.time.LocalDate weekStart = now.toLocalDate().minusDays(now.getDayOfWeek().getValue() - 1L);
                    java.time.LocalDate weekEnd = weekStart.plusDays(6);
                    java.time.LocalDate d = time.toLocalDate();
                    include = !d.isBefore(weekStart) && !d.isAfter(weekEnd);
                    break;
                case MONTH:
                    include = time.getYear() == now.getYear() && time.getMonth() == now.getMonth();
                    break;
                case QUARTER:
                    int quarterNow = ((now.getMonthValue() - 1) / 3) + 1;
                    int quarterTx = ((time.getMonthValue() - 1) / 3) + 1;
                    include = time.getYear() == now.getYear() && quarterTx == quarterNow;
                    break;
                default:
                    include = false;
                    break;
            }
            if (include) {
                filtered.add(tx);
            }
        }
        return filtered;
    }

    private static ZonedDateTime toZonedDateTime(Timestamp timestamp) {
        Timestamp source = timestamp == null ? Timestamp.now() : timestamp;
        return Instant.ofEpochSecond(source.getSeconds(), source.getNanoseconds())
            .atZone(ZoneId.systemDefault());
    }
}

