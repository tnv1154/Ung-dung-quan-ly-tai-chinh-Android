package com.example.myapplication.finance.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.example.myapplication.finance.model.ExportRecordRow;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class FinanceExportWriter {
    private static final String[] HEADERS = new String[] {
        "STT",
        "Ngày",
        "Giờ",
        "Số tiền thu",
        "Số tiền chi",
        "Loại tiền tệ",
        "Tài khoản",
        "Hạng mục",
        "Diễn giải"
    };

    private FinanceExportWriter() {
    }

    public static ExportWriteResult writeToUri(
        Context context,
        Uri uri,
        ExportFileFormat format,
        String reportTitle,
        List<ExportRecordRow> rows
    ) {
        if (context == null || uri == null || format == null) {
            return ExportWriteResult.failed("Thiếu thông tin để xuất dữ liệu.");
        }
        List<ExportRecordRow> safeRows = rows == null ? Collections.emptyList() : rows;
        switch (format) {
            case CSV:
                return writeTextContent(context, uri, buildCsvContent(safeRows));
            case EXCEL:
                return writeTextContent(context, uri, buildExcelXmlContent(safeRows));
            case PDF:
                return writePdfContent(context, uri, reportTitle, safeRows);
            default:
                return ExportWriteResult.failed("Định dạng tệp chưa được hỗ trợ.");
        }
    }

    private static ExportWriteResult writeTextContent(Context context, Uri uri, String content) {
        String output = content == null ? "" : content;
        byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
        try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) {
            if (stream == null) {
                return ExportWriteResult.failed("Không thể mở tệp để ghi dữ liệu.");
            }
            stream.write(bytes);
            stream.flush();
            return ExportWriteResult.success(bytes.length);
        } catch (IOException error) {
            return ExportWriteResult.failed(readableMessage(error, "Không thể ghi dữ liệu."));
        }
    }

    private static ExportWriteResult writePdfContent(
        Context context,
        Uri uri,
        String reportTitle,
        List<ExportRecordRow> rows
    ) {
        PdfDocument document = new PdfDocument();
        try {
            final int pageWidth = 595;
            final int pageHeight = 842;
            final int marginLeft = 24;
            final int marginTop = 28;
            final int marginBottom = 28;
            final int[] columnWidths = new int[] {28, 58, 40, 62, 62, 42, 66, 64, 109};
            final int rowHeight = 18;

            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(Color.parseColor("#D7DEE8"));
            borderPaint.setStrokeWidth(1f);
            borderPaint.setStyle(Paint.Style.STROKE);

            Paint headerFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            headerFillPaint.setColor(Color.parseColor("#EEF3FF"));

            Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setColor(Color.parseColor("#111827"));
            titlePaint.setTextSize(14f);
            titlePaint.setFakeBoldText(true);

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.parseColor("#111827"));
            textPaint.setTextSize(8.5f);

            Paint headerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            headerTextPaint.setColor(Color.parseColor("#1E3A8A"));
            headerTextPaint.setTextSize(8.5f);
            headerTextPaint.setFakeBoldText(true);

            int pageNumber = 1;
            int rowIndex = 0;
            while (rowIndex < rows.size() || pageNumber == 1) {
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                int y = marginTop;
                String title = reportTitle == null || reportTitle.trim().isEmpty() ? "Báo cáo thu chi" : reportTitle;
                canvas.drawText(title, marginLeft, y, titlePaint);
                y += 20;

                y = drawPdfHeaderRow(
                    canvas,
                    marginLeft,
                    y,
                    rowHeight,
                    columnWidths,
                    headerFillPaint,
                    borderPaint,
                    headerTextPaint
                );

                while (rowIndex < rows.size()) {
                    if (y + rowHeight > pageHeight - marginBottom) {
                        break;
                    }
                    ExportRecordRow row = rows.get(rowIndex);
                    drawPdfDataRow(
                        canvas,
                        marginLeft,
                        y,
                        rowHeight,
                        columnWidths,
                        borderPaint,
                        textPaint,
                        row
                    );
                    y += rowHeight;
                    rowIndex++;
                }

                Paint pagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                pagePaint.setColor(Color.parseColor("#64748B"));
                pagePaint.setTextSize(8f);
                canvas.drawText(
                    String.format(Locale.ROOT, "Trang %d", pageNumber),
                    pageWidth - marginLeft - 44,
                    pageHeight - 10,
                    pagePaint
                );

                document.finishPage(page);
                pageNumber++;
                if (rows.isEmpty()) {
                    break;
                }
            }

            try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) {
                if (stream == null) {
                    return ExportWriteResult.failed("Không thể mở tệp để ghi dữ liệu.");
                }
                document.writeTo(stream);
                stream.flush();
            }
            return ExportWriteResult.success(-1L);
        } catch (IOException error) {
            return ExportWriteResult.failed(readableMessage(error, "Không thể tạo tệp PDF."));
        } finally {
            document.close();
        }
    }

    private static int drawPdfHeaderRow(
        Canvas canvas,
        int startX,
        int y,
        int rowHeight,
        int[] widths,
        Paint fillPaint,
        Paint borderPaint,
        Paint textPaint
    ) {
        int x = startX;
        for (int i = 0; i < widths.length; i++) {
            int width = widths[i];
            canvas.drawRect(x, y, x + width, y + rowHeight, fillPaint);
            canvas.drawRect(x, y, x + width, y + rowHeight, borderPaint);
            String text = HEADERS[i];
            canvas.drawText(ellipsize(text, textPaint, width - 6), x + 3, y + 12, textPaint);
            x += width;
        }
        return y + rowHeight;
    }

    private static void drawPdfDataRow(
        Canvas canvas,
        int startX,
        int y,
        int rowHeight,
        int[] widths,
        Paint borderPaint,
        Paint textPaint,
        ExportRecordRow row
    ) {
        List<String> values = rowToStringCells(row);
        int x = startX;
        for (int i = 0; i < widths.length; i++) {
            int width = widths[i];
            canvas.drawRect(x, y, x + width, y + rowHeight, borderPaint);
            String value = i < values.size() ? values.get(i) : "";
            canvas.drawText(ellipsize(value, textPaint, width - 6), x + 3, y + 12, textPaint);
            x += width;
        }
    }

    private static String ellipsize(String value, Paint paint, int maxWidth) {
        String source = safe(value);
        if (source.isEmpty() || paint.measureText(source) <= maxWidth) {
            return source;
        }
        String suffix = "...";
        float suffixWidth = paint.measureText(suffix);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char next = source.charAt(i);
            float nextWidth = paint.measureText(builder.toString() + next);
            if (nextWidth + suffixWidth > maxWidth) {
                break;
            }
            builder.append(next);
        }
        if (builder.length() == 0) {
            return suffix;
        }
        return builder + suffix;
    }

    public static String buildCsvContent(List<ExportRecordRow> rows) {
        List<ExportRecordRow> safeRows = rows == null ? Collections.emptyList() : rows;
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", HEADERS)).append('\n');
        for (ExportRecordRow row : safeRows) {
            List<String> cells = rowToStringCells(row);
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(escapeCsvCell(cells.get(i)));
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public static String buildExcelXmlContent(List<ExportRecordRow> rows) {
        List<ExportRecordRow> safeRows = rows == null ? Collections.emptyList() : rows;
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<?mso-application progid=\"Excel.Sheet\"?>\n");
        xml.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ")
            .append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n");
        xml.append("  <Worksheet ss:Name=\"Bao_cao_thu_chi\">\n");
        xml.append("    <Table>\n");
        appendExcelHeaderRow(xml);
        for (ExportRecordRow row : safeRows) {
            appendExcelDataRow(xml, row);
        }
        xml.append("    </Table>\n");
        xml.append("  </Worksheet>\n");
        xml.append("</Workbook>\n");
        return xml.toString();
    }

    private static void appendExcelHeaderRow(StringBuilder xml) {
        xml.append("      <Row>\n");
        for (String header : HEADERS) {
            xml.append("        <Cell><Data ss:Type=\"String\">")
                .append(escapeXml(header))
                .append("</Data></Cell>\n");
        }
        xml.append("      </Row>\n");
    }

    private static void appendExcelDataRow(StringBuilder xml, ExportRecordRow row) {
        List<String> values = rowToStringCells(row);
        List<Boolean> numeric = rowToNumericCells(row);
        xml.append("      <Row>\n");
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            boolean isNumeric = i < numeric.size() && numeric.get(i);
            String type = isNumeric ? "Number" : "String";
            xml.append("        <Cell><Data ss:Type=\"")
                .append(type)
                .append("\">")
                .append(escapeXml(value))
                .append("</Data></Cell>\n");
        }
        xml.append("      </Row>\n");
    }

    private static List<String> rowToStringCells(ExportRecordRow row) {
        if (row == null) {
            return List.of("", "", "", "", "", "", "", "", "");
        }
        List<String> cells = new ArrayList<>(HEADERS.length);
        cells.add(String.valueOf(row.getStt()));
        cells.add(safe(row.getDate()));
        cells.add(safe(row.getTime()));
        cells.add(formatAmount(row.getIncomeAmount()));
        cells.add(formatAmount(row.getExpenseAmount()));
        cells.add(safe(row.getCurrency()));
        cells.add(safe(row.getWalletName()));
        cells.add(safe(row.getCategory()));
        cells.add(safe(row.getNote()));
        return cells;
    }

    private static List<Boolean> rowToNumericCells(ExportRecordRow row) {
        if (row == null) {
            return List.of(false, false, false, false, false, false, false, false, false);
        }
        List<Boolean> numeric = new ArrayList<>(HEADERS.length);
        numeric.add(true);
        numeric.add(false);
        numeric.add(false);
        numeric.add(row.getIncomeAmount() != null);
        numeric.add(row.getExpenseAmount() != null);
        numeric.add(false);
        numeric.add(false);
        numeric.add(false);
        numeric.add(false);
        return numeric;
    }

    private static String escapeCsvCell(String value) {
        String source = safe(value);
        boolean mustQuote = source.contains(",") || source.contains("\"") || source.contains("\n");
        if (!mustQuote) {
            return source;
        }
        return "\"" + source.replace("\"", "\"\"") + "\"";
    }

    private static String escapeXml(String value) {
        String source = safe(value);
        return source
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    private static String formatAmount(Double value) {
        if (value == null) {
            return "";
        }
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        if (decimal.scale() < 0) {
            decimal = decimal.setScale(0, RoundingMode.HALF_UP);
        }
        return decimal.toPlainString();
    }

    private static String readableMessage(Exception error, String fallback) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return fallback;
        }
        return error.getMessage();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class ExportWriteResult {
        private final boolean success;
        private final long bytesWritten;
        private final String errorMessage;

        private ExportWriteResult(boolean success, long bytesWritten, String errorMessage) {
            this.success = success;
            this.bytesWritten = bytesWritten;
            this.errorMessage = errorMessage;
        }

        public static ExportWriteResult success(long bytesWritten) {
            return new ExportWriteResult(true, bytesWritten, null);
        }

        public static ExportWriteResult failed(String message) {
            return new ExportWriteResult(false, -1L, message == null ? "" : message);
        }

        public boolean isSuccess() {
            return success;
        }

        public long getBytesWritten() {
            return bytesWritten;
        }

        public String getErrorMessage() {
            return errorMessage == null ? "" : errorMessage;
        }
    }
}

