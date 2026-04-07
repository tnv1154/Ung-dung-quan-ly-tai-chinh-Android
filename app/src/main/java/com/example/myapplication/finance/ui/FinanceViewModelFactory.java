package com.example.myapplication.finance.ui;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.finance.data.FirestoreFinanceRepository;

public class FinanceViewModelFactory implements ViewModelProvider.Factory {
    private final FirestoreFinanceRepository repository;
    private final String userId;

    public FinanceViewModelFactory(FirestoreFinanceRepository repository, String userId) {
        this.repository = repository;
        this.userId = userId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(Class<T> modelClass) {
        if (modelClass.isAssignableFrom(FinanceViewModel.class)) {
            return (T) new FinanceViewModel(repository, userId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}

