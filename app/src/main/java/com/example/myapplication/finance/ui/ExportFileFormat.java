package com.example.myapplication.finance.ui;

public enum ExportFileFormat {
    EXCEL("xls", "application/vnd.ms-excel"),
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String mimeType;

    ExportFileFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public String getMimeType() {
        return mimeType;
    }
}

