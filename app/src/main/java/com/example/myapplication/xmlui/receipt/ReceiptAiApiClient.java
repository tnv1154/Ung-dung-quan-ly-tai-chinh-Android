package com.example.myapplication.xmlui.receipt;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ReceiptAiApiClient {
    private static ReceiptAiApiService service;

    private ReceiptAiApiClient() {
    }

    public static synchronized ReceiptAiApiService getService() {
        if (service != null) {
            return service;
        }
        OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(180, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
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
