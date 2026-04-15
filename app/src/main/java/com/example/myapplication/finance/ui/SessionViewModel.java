package com.example.myapplication.finance.ui;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SessionViewModel extends ViewModel {
    private final FirebaseAuth auth;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
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
        register(email, password, null, null);
    }

    public void register(String email, String password, String displayName) {
        register(email, password, displayName, null);
    }

    public void register(String email, String password, String displayName, Consumer<String> onComplete) {
        setState(true, currentUser(), null);
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(result -> {
                FirebaseUser user = result.getUser();
                seedDefaultCategoriesForNewUser(user);
                String name = safe(displayName).trim();
                if (user == null || name.isBlank()) {
                    setState(false, currentUser(), null);
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    return;
                }
                UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build();
                user.updateProfile(request)
                    .addOnSuccessListener(unused -> {
                        setState(false, currentUser(), null);
                        if (onComplete != null) {
                            onComplete.accept(null);
                        }
                    })
                    .addOnFailureListener(error -> {
                        String message = messageOrDefault(error, "Đăng ký thất bại");
                        setState(false, currentUser(), message);
                        if (onComplete != null) {
                            onComplete.accept(message);
                        }
                    });
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Đăng ký thất bại");
                setState(false, currentUser(), message);
                if (onComplete != null) {
                    onComplete.accept(message);
                }
            });
    }

    public void signInWithGoogleIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            setState(false, currentUser(), "Không nhận được Google ID token.");
            return;
        }
        setState(true, currentUser(), null);
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener(result -> {
                if (result.getAdditionalUserInfo() != null && result.getAdditionalUserInfo().isNewUser()) {
                    seedDefaultCategoriesForNewUser(result.getUser());
                }
                setState(false, currentUser(), null);
            })
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

    public void ensureEmailRegistered(String email, Consumer<String> onComplete) {
        String normalized = safe(email).trim();
        if (normalized.isBlank()) {
            onComplete.accept("Vui lòng nhập email hợp lệ.");
            return;
        }
        setState(true, currentUser(), null);
        auth.fetchSignInMethodsForEmail(normalized)
            .addOnSuccessListener(result -> {
                setState(false, currentUser(), null);
                List<String> methods = result.getSignInMethods();
                if (methods == null || methods.isEmpty()) {
                    onComplete.accept("Email không tồn tại");
                    return;
                }
                onComplete.accept(null);
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Không thể kiểm tra email.");
                setState(false, currentUser(), message);
                onComplete.accept(message);
            });
    }

    public void verifyPasswordResetCode(String resetCode, Consumer<String> onComplete) {
        String code = safe(resetCode).trim();
        if (code.isBlank()) {
            onComplete.accept("Vui lòng nhập mã xác thực.");
            return;
        }
        setState(true, currentUser(), null);
        auth.verifyPasswordResetCode(code)
            .addOnSuccessListener(email -> {
                setState(false, currentUser(), null);
                onComplete.accept(null);
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Mã xác thực không hợp lệ hoặc đã hết hạn.");
                setState(false, currentUser(), message);
                onComplete.accept(message);
            });
    }

    public void confirmPasswordReset(String resetCode, String newPassword, Consumer<String> onComplete) {
        String code = safe(resetCode).trim();
        String password = safe(newPassword).trim();
        if (code.isBlank()) {
            onComplete.accept("Mã xác thực không hợp lệ.");
            return;
        }
        if (password.length() < 8) {
            onComplete.accept("Mật khẩu mới phải có ít nhất 8 ký tự.");
            return;
        }
        setState(true, currentUser(), null);
        auth.confirmPasswordReset(code, password)
            .addOnSuccessListener(unused -> {
                setState(false, currentUser(), null);
                onComplete.accept(null);
            })
            .addOnFailureListener(error -> {
                String message = messageOrDefault(error, "Không thể tạo mật khẩu mới.");
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
        ioExecutor.shutdown();
        super.onCleared();
    }

    private void seedDefaultCategoriesForNewUser(FirebaseUser user) {
        if (user == null || user.getUid() == null || user.getUid().isBlank()) {
            return;
        }
        String userId = user.getUid();
        ioExecutor.submit(() -> {
            try {
                new FirestoreFinanceRepository().seedDefaultCategories(userId);
            } catch (Exception error) {
                setState(false, currentUser(), messageOrDefault(error, "Không thể tạo hạng mục mặc định"));
            }
        });
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
        String mappedAuthMessage = mapFirebaseAuthMessage(error);
        if (mappedAuthMessage != null) {
            return mappedAuthMessage;
        }
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return fallback;
        }
        return error.getMessage();
    }

    private static String mapFirebaseAuthMessage(Exception error) {
        if (!(error instanceof FirebaseAuthException)) {
            return null;
        }
        String errorCode = ((FirebaseAuthException) error).getErrorCode();
        if (errorCode == null || errorCode.isBlank()) {
            return null;
        }
        if ("ERROR_INVALID_EMAIL".equals(errorCode)) {
            return "Email không hợp lệ.";
        }
        if ("ERROR_EMAIL_ALREADY_IN_USE".equals(errorCode)) {
            return "Email này đã được sử dụng.";
        }
        if ("ERROR_USER_NOT_FOUND".equals(errorCode)) {
            return "Email không tồn tại.";
        }
        if ("ERROR_WRONG_PASSWORD".equals(errorCode)) {
            return "Mật khẩu không chính xác.";
        }
        if ("ERROR_WEAK_PASSWORD".equals(errorCode)) {
            return "Mật khẩu quá yếu. Vui lòng dùng mật khẩu mạnh hơn.";
        }
        if ("ERROR_TOO_MANY_REQUESTS".equals(errorCode)) {
            return "Bạn thao tác quá nhiều lần. Vui lòng thử lại sau.";
        }
        if ("ERROR_OPERATION_NOT_ALLOWED".equals(errorCode)) {
            return "Phương thức đăng nhập này chưa được bật trong Firebase Authentication. "
                + "Vào Firebase Console > Authentication > Sign-in method để bật provider tương ứng.";
        }
        return null;
    }
}

