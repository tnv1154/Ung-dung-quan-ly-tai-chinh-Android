package com.example.myapplication.finance.ui;

import com.example.myapplication.finance.model.CsvImportRow;

import java.util.Collections;
import java.util.List;

public class CsvParseResult {
    private final List<CsvImportRow> rows;
    private final int skippedRows;

    public CsvParseResult(List<CsvImportRow> rows, int skippedRows) {
        this.rows = rows == null ? Collections.emptyList() : rows;
        this.skippedRows = skippedRows;
    }

    public List<CsvImportRow> getRows() {
        return rows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }
}

