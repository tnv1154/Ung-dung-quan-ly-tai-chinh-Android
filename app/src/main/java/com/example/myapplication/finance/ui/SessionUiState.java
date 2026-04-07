package com.example.myapplication.finance.ui;

import com.google.firebase.auth.FirebaseUser;

public class SessionUiState {
    private final boolean isLoading;
    private final FirebaseUser currentUser;
    private final String errorMessage;

    public SessionUiState() {
        this(false, null, null);
    }

    public SessionUiState(boolean isLoading, FirebaseUser currentUser, String errorMessage) {
        this.isLoading = isLoading;
        this.currentUser = currentUser;
        this.errorMessage = errorMessage;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public FirebaseUser getCurrentUser() {
        return currentUser;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

