package com.example.myapplication.xmlui.receipt;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ReceiptAiApiClient {
    private static final long OCR_TIMEOUT_SECONDS = 240L;
    private static ReceiptAiApiService service;

    private ReceiptAiApiClient() {
    }

    public static synchronized ReceiptAiApiService getService() {
        if (service != null) {
            return service;
        }
        OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl("https://example.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        service = retrofit.create(ReceiptAiApiService.class);
        return service;
    }
}
