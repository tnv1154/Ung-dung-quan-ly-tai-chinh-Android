package com.example.myapplication.xmlui.currency;

import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.google.firebase.firestore.Source;

public final class ExchangeRateSnapshotLoader {
    private ExchangeRateSnapshotLoader() {
    }

    public static ExchangeRateSnapshot loadWithFallback(
        FirestoreFinanceRepository repository,
        String userId
    ) throws Exception {
        if (repository == null) {
            throw new IllegalArgumentException("repository is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        ExchangeRateSnapshot snapshot = null;
        Exception lastError = null;

        try {
            snapshot = repository.getExchangeRateSnapshot(userId, Source.CACHE);
        } catch (Exception error) {
            lastError = error;
        }

        if (snapshot == null) {
            try {
                snapshot = repository.getExchangeRateSnapshot(userId, Source.SERVER);
            } catch (Exception error) {
                lastError = error;
            }
        }

        if (snapshot == null) {
            try {
                snapshot = FrankfurterRateClient.fetchLatestSnapshot(ExchangeRateSnapshot.DEFAULT_BASE_CURRENCY);
            } catch (Exception error) {
                lastError = error;
            }
            if (snapshot != null) {
                try {
                    repository.upsertExchangeRateSnapshot(userId, snapshot);
                } catch (Exception error) {
                    lastError = error;
                }
            }
        }

        if (snapshot == null && lastError != null) {
            throw lastError;
        }
        return snapshot;
    }
}
