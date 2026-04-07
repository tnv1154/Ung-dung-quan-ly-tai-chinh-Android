package com.example.myapplication.finance.ui;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.function.Consumer;

public class SessionViewModel extends ViewModel {
    private final FirebaseAuth auth;
    private final MutableLiveData<SessionUiState> uiStateLiveData = new MutableLiveData<>();
    private final FirebaseAuth.AuthStateListener authListener = firebaseAuth ->
        uiStateLiveData.postValue(new SessionUiState(false, firebaseAuth.getCurrentUser(), null));

    public SessionViewModel() {
        this(FirebaseAuth.getInstance());
    }

    public SessionViewModel(FirebaseAuth auth) {
        this.auth = auth;
        uiStateLiveData.setValue(new SessionUiState(false, auth.getCurrentUser(), null));
        this.auth.addAuthStateListener(authListener);
    }

    public LiveData<SessionUiState> getUiStateLiveData() {
        return uiStateLiveData;
    }

    public void signIn(String email, String password) {
        setState(true, currentUser(), null);
        auth.signInWithEmailAndPassword(email, password)
            .addOnFailureListener(error -> setState(false, currentUser(), messageOrDefault(error, "Đăng nhập thất bại")));
    }

    public void register(String email, String password) {
        setState(true, currentUser(), null);
        auth.createUserWithEmailAndPassword(email, password)
            .addOnFailureListener(error -> setState(false, currentUser(), messageOrDefault(error, "Đăng ký thất bại")));
    }

    public void signInWithGoogleIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            setState(false, currentUser(), "Không nhận được Google ID token.");
            return;
        }
        setState(true, currentUser(), null);
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener(result -> setState(false, currentUser(), null))
            .addOnFailureListener(error -> setState(false, currentUser(), messageOrDefault(error, "Đăng nhập Google thất bại")));
    }

    public void signOut() {
        auth.signOut();
    }

    public void updateDisplayName(String displayName, Consumer<String> onComplete) {
        String trimmed = safe(displayName).trim();
        if (trimmed.isBlank()) {
            onComplete.accept("Tên hiển thị không được để trống.");
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onComplete.accept("Không tìm thấy phiên đăng nhập.");
            return;
        }
        setState(true, user, null);
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
            .setDisplayName(trimmed)
            .build();
        user.updateProfile(request)
            .addOnSuccessListener(unused -> {
                setState(false, currentUser(), null);
                onComplete.accept(null);
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Cập nhật hồ sơ thất bại");
                setState(false, currentUser(), message);
                onComplete.accept(message);
            });
    }

    public void updatePhotoUrl(String photoUri, Consumer<String> onComplete) {
        String trimmed = safe(photoUri).trim();
        if (trimmed.isBlank()) {
            onComplete.accept("Ảnh đại diện không hợp lệ.");
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onComplete.accept("Không tìm thấy phiên đăng nhập.");
            return;
        }
        setState(true, user, null);
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
            .setPhotoUri(Uri.parse(trimmed))
            .build();
        user.updateProfile(request)
            .addOnSuccessListener(unused -> {
                setState(false, currentUser(), null);
                onComplete.accept(null);
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Cập nhật ảnh đại diện thất bại");
                setState(false, currentUser(), message);
                onComplete.accept(message);
            });
    }

    public void clearPhotoUrl(Consumer<String> onComplete) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onComplete.accept("Không tìm thấy phiên đăng nhập.");
            return;
        }
        setState(true, user, null);
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
            .setPhotoUri(null)
            .build();
        user.updateProfile(request)
            .addOnSuccessListener(unused -> {
                setState(false, currentUser(), null);
                onComplete.accept(null);
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Xóa ảnh đại diện thất bại");
                setState(false, currentUser(), message);
                onComplete.accept(message);
            });
    }

    public void updatePassword(String newPassword, Consumer<String> onComplete) {
        String trimmed = safe(newPassword).trim();
        if (trimmed.length() < 6) {
            onComplete.accept("Mật khẩu mới phải có ít nhất 6 ký tự.");
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onComplete.accept("Không tìm thấy phiên đăng nhập.");
            return;
        }
        setState(true, user, null);
        user.updatePassword(trimmed)
            .addOnSuccessListener(unused -> {
                setState(false, currentUser(), null);
                onComplete.accept(null);
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Đổi mật khẩu thất bại");
                setState(false, currentUser(), message);
                onComplete.accept(message);
            });
    }

    public void sendPasswordReset(String email, Consumer<String> onComplete) {
        String normalized = safe(email).trim();
        if (normalized.isBlank()) {
            onComplete.accept("Vui lòng nhập email để khôi phục mật khẩu.");
            return;
        }
        setState(true, currentUser(), null);
        auth.sendPasswordResetEmail(normalized)
            .addOnSuccessListener(unused -> {
                setState(false, currentUser(), null);
                onComplete.accept(null);
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Không thể gửi email khôi phục mật khẩu");
                setState(false, currentUser(), message);
                onComplete.accept(message);
            });
    }

    public void setError(String message) {
        setState(false, currentUser(), message);
    }

    public void clearError() {
        SessionUiState current = currentState();
        setState(current.isLoading(), current.getCurrentUser(), null);
    }

    @Override
    protected void onCleared() {
        auth.removeAuthStateListener(authListener);
        super.onCleared();
    }

    private SessionUiState currentState() {
        SessionUiState state = uiStateLiveData.getValue();
        if (state != null) {
            return state;
        }
        return new SessionUiState(false, auth.getCurrentUser(), null);
    }

    private FirebaseUser currentUser() {
        SessionUiState current = currentState();
        return auth.getCurrentUser() != null ? auth.getCurrentUser() : current.getCurrentUser();
    }

    private void setState(boolean isLoading, FirebaseUser user, String errorMessage) {
        uiStateLiveData.postValue(new SessionUiState(isLoading, user, errorMessage));
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String messageOrDefault(Exception error, String fallback) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return fallback;
        }
        return error.getMessage();
    }
}

