package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.CsvImportRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CsvParseResult {
    private final List<CsvImportRow> rows;
    private final int validRows;
    private final int skippedRows;
    private final String errorMessage;

    public CsvParseResult(List<CsvImportRow> rows, int validRows, int skippedRows, String errorMessage) {
        this.rows = rows == null ? Collections.emptyList() : rows;
        this.validRows = Math.max(validRows, 0);
        this.skippedRows = skippedRows;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public List<CsvImportRow> getRows() {
        return rows;
    }

    public int getValidRows() {
        return validRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasErrorMessage() {
        return !errorMessage.trim().isEmpty();
    }

    public List<CsvImportRow> getValidImportRows() {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<CsvImportRow> valid = new ArrayList<>();
        for (CsvImportRow row : rows) {
            if (row != null && row.isValid()) {
                valid.add(row);
            }
        }
        return valid;
    }
}

