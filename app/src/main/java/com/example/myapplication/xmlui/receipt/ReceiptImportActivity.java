package com.example.myapplication.xmlui.receipt;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.xmlui.AddTransactionActivity;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Source;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ReceiptImportActivity extends AppCompatActivity {
    private static final String TEMP_FOLDER = "receipt_images";
    private static final DateTimeFormatter[] RECEIPT_DATETIME_FORMATTERS = new DateTimeFormatter[] {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    };
    private static final DateTimeFormatter[] RECEIPT_DATE_FORMATTERS = new DateTimeFormatter[] {
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };
    private static final String AI_FALLBACK_CATEGORY = "KHÁC";
    private static final String PREFILL_FALLBACK_CATEGORY = "Tiền ra";

    private ImageButton btnBack;
    private ImageButton btnCapture;
    private MaterialButton btnChooseGallery;
    private MaterialButton btnRetry;
    private PreviewView previewCamera;
    private TextView tvStatus;
    private android.view.View layoutLoading;
    private TextView tvLoading;
    private android.view.View layoutScanning;
    private android.view.View frameScanPreview;
    private ImageView ivScanPreview;
    private android.view.View viewScanLine;
    private TextView tvScanStatus;
    private ObjectAnimator scanLineAnimator;

    private Uri lastCroppedUri;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private volatile boolean activityClosed;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                startCameraPreview();
            } else {
                Toast.makeText(this, R.string.receipt_error_camera_permission, Toast.LENGTH_SHORT).show();
            }
        });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher =
        registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null) {
                return;
            }
            startCrop(uri);
        });

    private final ActivityResultLauncher<Intent> editImageLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            String rawCroppedUri = result.getData().getStringExtra(ReceiptImageEditorActivity.EXTRA_CROPPED_URI);
            if (rawCroppedUri == null || rawCroppedUri.trim().isEmpty()) {
                showErrorState(getString(R.string.receipt_error_crop));
                return;
            }
            Uri output = Uri.parse(rawCroppedUri);
            if (output == null) {
                showErrorState(getString(R.string.receipt_error_crop));
                return;
            }
            onCroppedImageReady(output);
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_import);
        bindViews();
        setupActions();
        ensureCameraPreview();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnReceiptBack);
        btnCapture = findViewById(R.id.btnReceiptCapture);
        btnChooseGallery = findViewById(R.id.btnReceiptChooseGallery);
        btnRetry = findViewById(R.id.btnReceiptRetry);
        previewCamera = findViewById(R.id.previewReceiptCamera);
        tvStatus = findViewById(R.id.tvReceiptStatus);
        layoutLoading = findViewById(R.id.layoutReceiptLoading);
        tvLoading = findViewById(R.id.tvReceiptLoading);
        layoutScanning = findViewById(R.id.layoutReceiptScanning);
        frameScanPreview = findViewById(R.id.frameReceiptScanPreview);
        ivScanPreview = findViewById(R.id.ivReceiptScanPreview);
        viewScanLine = findViewById(R.id.viewReceiptScanLine);
        tvScanStatus = findViewById(R.id.tvReceiptScanStatus);
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> finish());
        btnCapture.setOnClickListener(v -> capturePhoto());
        btnChooseGallery.setOnClickListener(v -> pickImageFromGallery());
        btnRetry.setOnClickListener(v -> {
            if (lastCroppedUri != null) {
                uploadToAi(lastCroppedUri);
            }
        });
    }

    private void ensureCameraPreview() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview();
            return;
        }
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void startCameraPreview() {
        if (isUiClosed()) {
            return;
        }
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                cameraProvider = provider;
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewCamera.getSurfaceProvider());

                ImageCapture.Builder captureBuilder = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY);
                if (previewCamera.getDisplay() != null) {
                    captureBuilder.setTargetRotation(previewCamera.getDisplay().getRotation());
                }
                imageCapture = captureBuilder.build();

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception error) {
                showErrorState(getString(R.string.receipt_error_camera_unavailable));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        if (imageCapture == null) {
            startCameraPreview();
            return;
        }
        try {
            CaptureOutput output = createTempCaptureOutput("capture_");
            setLoading(true, getString(R.string.receipt_processing));
            imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(output.file).build(),
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        setLoading(false, getString(R.string.receipt_processing));
                        startCrop(output.uri);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        showErrorState(getString(R.string.receipt_error_pick_image));
                    }
                }
            );
        } catch (Exception error) {
            showErrorState(getString(R.string.receipt_error_pick_image));
        }
    }

    private void pickImageFromGallery() {
        pickImageLauncher.launch(
            new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()
        );
    }

    private void startCrop(Uri sourceUri) {
        try {
            Intent intent = new Intent(this, ReceiptImageEditorActivity.class);
            intent.putExtra(ReceiptImageEditorActivity.EXTRA_SOURCE_URI, sourceUri.toString());
            editImageLauncher.launch(intent);
        } catch (Exception error) {
            showErrorState(getString(R.string.receipt_error_crop));
        }
    }

    private CaptureOutput createTempCaptureOutput(String prefix) throws Exception {
        File folder = new File(getCacheDir(), TEMP_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create temp image folder");
        }
        File file = File.createTempFile(prefix, ".jpg", folder);
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        return new CaptureOutput(file, uri);
    }

    private void onCroppedImageReady(Uri croppedUri) {
        lastCroppedUri = croppedUri;
        tvStatus.setVisibility(android.view.View.GONE);
        btnRetry.setVisibility(android.view.View.GONE);
        uploadToAi(croppedUri);
    }

    private void uploadToAi(Uri imageUri) {
        String apiUrl = buildApiUrl();
        if (apiUrl.isBlank()) {
            showErrorState(getString(R.string.receipt_error_config));
            return;
        }
        setUploadingState(true, imageUri, getString(R.string.receipt_status_uploading));
        ioExecutor.submit(() -> {
            UploadImageData imageData = null;
            try {
                imageData = copyUriToTempFile(imageUri);
                RequestBody body = RequestBody.create(MediaType.parse(imageData.mimeType), imageData.file);
                MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", imageData.file.getName(), body);
                List<String> categories = loadExpenseParentCategories();
                RequestBody promptPart = RequestBody.create(
                    MediaType.parse("text/plain; charset=utf-8"),
                    buildPrompt(categories)
                );
                Response<ResponseBody> response = ReceiptAiApiClient
                    .getService()
                    .parseReceipt(apiUrl, imagePart, promptPart)
                    .execute();
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                String rawResponse = response.body().string();
                ReceiptOcrPayload payload = parseOcrPayload(rawResponse);
                validatePayload(payload);
                runOnUiThread(() -> {
                    if (!isUiClosed()) {
                        setUploadingState(false, null, null);
                        returnPrefillResult(payload);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isUiClosed()) {
                        setUploadingState(false, null, null);
                        String message = getString(R.string.receipt_error_upload) + " (" + safe(error.getMessage()) + ")";
                        showErrorState(message);
                    }
                });
            } finally {
                if (imageData != null && imageData.file.exists() && !imageData.file.delete()) {
                    imageData.file.deleteOnExit();
                }
            }
        });
    }

    private UploadImageData copyUriToTempFile(Uri uri) throws Exception {
        File folder = new File(getCacheDir(), TEMP_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create upload folder");
        }
        String mimeType = safe(getContentResolver().getType(uri)).trim();
        if (!mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            mimeType = "image/jpeg";
        }
        String extension = extensionFromMimeType(mimeType);
        File outFile = File.createTempFile("upload_", extension, folder);
        try (
            InputStream in = getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(outFile)
        ) {
            if (in == null) {
                throw new IllegalStateException("Cannot open image stream");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        return new UploadImageData(outFile, mimeType);
    }

    private String extensionFromMimeType(String mimeType) {
        String extension = MimeTypeMap
            .getSingleton()
            .getExtensionFromMimeType(mimeType.toLowerCase(Locale.ROOT));
        if (extension == null || extension.trim().isEmpty()) {
            return ".jpg";
        }
        return "." + extension;
    }

    private void validatePayload(ReceiptOcrPayload payload) {
        if (payload == null) {
            throw new IllegalStateException(getString(R.string.receipt_error_parse));
        }
        Double amount = payload.totalAmount;
        if (amount == null || amount <= 0.0) {
            throw new IllegalStateException(getString(R.string.receipt_error_invalid_amount));
        }
    }

    private void returnPrefillResult(ReceiptOcrPayload payload) {
        setLoading(true, getString(R.string.receipt_status_opening_editor));
        Intent data = new Intent();
        data.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, AddTransactionActivity.MODE_EXPENSE);
        data.putExtra(AddTransactionActivity.EXTRA_PREFILL_AMOUNT, payload.totalAmount);
        data.putExtra(AddTransactionActivity.EXTRA_PREFILL_NOTE, buildPrefillNote(payload));
        String category = mapReceiptCategoryForPrefill(payload.categoryName);
        if (!category.isEmpty()) {
            data.putExtra(AddTransactionActivity.EXTRA_PREFILL_CATEGORY_NAME, category);
        }
        String paymentMethod = safe(payload.paymentMethod).trim();
        if (!paymentMethod.isEmpty()) {
            data.putExtra(AddTransactionActivity.EXTRA_PREFILL_PAYMENT_METHOD, paymentMethod);
        }
        long prefillTimeMillis = parseReceiptDateTimeMillis(payload.receiptDatetime);
        if (prefillTimeMillis <= 0L) {
            prefillTimeMillis = System.currentTimeMillis();
        }
        data.putExtra(AddTransactionActivity.EXTRA_PREFILL_TIME_MILLIS, prefillTimeMillis);
        setResult(RESULT_OK, data);
        finish();
    }

    private String buildPrefillNote(ReceiptOcrPayload payload) {
        List<String> lines = new ArrayList<>();
        String merchant = safe(payload.merchantName).trim();
        String address = safe(payload.address).trim();
        if (!merchant.isEmpty()) {
            lines.add("Nơi mua hàng: " + merchant);
        }
        if (!address.isEmpty()) {
            lines.add("Địa chỉ: " + address);
        }
        List<String> itemLines = new ArrayList<>();
        for (ReceiptLineItem item : payload.items) {
            String line = item.toDisplayLine();
            if (!line.isEmpty()) {
                itemLines.add("- " + line);
            }
        }
        if (!itemLines.isEmpty()) {
            lines.add("Danh sách mặt hàng:");
            lines.addAll(itemLines);
        }
        return String.join("\n", lines);
    }

    private String mapReceiptCategoryForPrefill(String rawCategory) {
        String category = safe(rawCategory).trim();
        if (category.isEmpty()) {
            return "";
        }
        if (isAiFallbackCategory(category)) {
            return PREFILL_FALLBACK_CATEGORY;
        }
        return category;
    }

    private boolean isAiFallbackCategory(String rawCategory) {
        return "khac".equals(normalizeCategoryKey(rawCategory));
    }

    private boolean isDefaultExpenseCategory(String rawCategory) {
        return "tienra".equals(normalizeCategoryKey(rawCategory));
    }

    private String normalizeCategoryKey(String value) {
        String normalized = safe(value).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.replaceAll("[^a-z0-9]+", "");
    }

    private long parseReceiptDateTimeMillis(String raw) {
        String value = safe(raw).trim();
        if (value.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        for (DateTimeFormatter formatter : RECEIPT_DATETIME_FORMATTERS) {
            try {
                LocalDateTime parsed = LocalDateTime.parse(value, formatter);
                return parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter formatter : RECEIPT_DATE_FORMATTERS) {
            try {
                LocalDate parsed = LocalDate.parse(value, formatter);
                return parsed.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        return System.currentTimeMillis();
    }

    private String buildApiUrl() {
        String rawUrl = safe(BuildConfig.RECEIPT_AI_API_URL).trim();
        if (rawUrl.isEmpty()) {
            return "";
        }
        if (rawUrl.toLowerCase(Locale.ROOT).startsWith("http://")
            || rawUrl.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return rawUrl;
        }
        return "https://" + rawUrl;
    }

    private List<String> loadExpenseParentCategories() {
        LinkedHashSet<String> categoryNames = new LinkedHashSet<>();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirestoreFinanceRepository repository = new FirestoreFinanceRepository();
            List<TransactionCategory> categories = null;
            try {
                categories = repository.getCategories(user.getUid(), Source.SERVER);
            } catch (Exception serverError) {
                try {
                    categories = repository.getCategories(user.getUid(), Source.CACHE);
                } catch (Exception cacheError) {
                    categories = null;
                }
            }
            if (categories != null) {
                for (TransactionCategory category : categories) {
                    if (category == null || category.getType() != TransactionType.EXPENSE) {
                        continue;
                    }
                    if (!safe(category.getParentName()).trim().isEmpty()) {
                        continue;
                    }
                    String name = safe(category.getName()).trim();
                    if (!name.isEmpty() && !isDefaultExpenseCategory(name)) {
                        categoryNames.add(name);
                    }
                }
            }
        }
        return new ArrayList<>(categoryNames);
    }

    private String buildPrompt(List<String> categories) {
        String categoryText = categories == null || categories.isEmpty()
            ? ""
            : String.join(", ", categories);
        return "Bạn là một hệ thống OCR và phân tích hóa đơn.\n"
            + "Nhiệm vụ của bạn:\n"
            + "1. Đọc hiểu nội dung hóa đơn từ hình ảnh.\n"
            + "2. Phân loại hóa đơn theo các hạng mục sau: [" + categoryText + "].\n"
            + "   - BẮT BUỘC chọn đúng 1 hạng mục trong danh sách nếu hóa đơn có bất kỳ dấu hiệu liên quan đến hạng mục đó.\n"
            + "   - Chỉ được trả về \"" + AI_FALLBACK_CATEGORY + "\" khi hóa đơn HOÀN TOÀN không thuộc bất kỳ hạng mục nào trong danh sách.\n"
            + "   - Không được trả về \"" + AI_FALLBACK_CATEGORY + "\" nếu có thể suy luận hóa đơn thuộc một hạng mục gần nhất trong danh sách.\n"
            + "3. Chuẩn hóa thời gian theo định dạng DD/MM/YYYY HH:MM.\n"
            + "4. Chuẩn hóa phương thức thanh toán thành một trong các giá trị sau: [\"tiền mặt\", \"Ngân hàng\", \"ví điện tử\"].\n"
            + "   - Nếu không tìm thấy hoặc không khớp thì để \"null\".\n"
            + "5. Trả về dữ liệu dưới định dạng JSON với cấu trúc sau:\n\n"
            + "{\n"
            + "  \"Thời gian\": \"<ngày giờ trên hóa đơn, định dạng DD/MM/YYYY HH:MM>\",\n"
            + "  \"Tổng tiền\": \"<số tiền tổng cộng>\",\n"
            + "  \"Hạng mục\": \"<hạng mục hoặc " + AI_FALLBACK_CATEGORY + ">\",\n"
            + "  \"Nơi mua hàng\": \"<tên cửa hàng hoặc null nếu không có>\",\n"
            + "  \"Địa chỉ\": \"<địa chỉ cửa hàng hoặc null nếu không có>\",\n"
            + "  \"Phương thức thanh toán\": \"<giá trị chuẩn hóa hoặc null>\",\n"
            + "  \"Danh sách mặt hàng\": [\n"
            + "    {\n"
            + "      \"Tên mặt hàng\": \"<tên>\",\n"
            + "      \"Đơn giá\": \"<giá>\",\n"
            + "      \"Số lượng\": \"<số lượng>\",\n"
            + "      \"Thành tiền\": \"<thành tiền>\"\n"
            + "    }\n"
            + "  ]\n"
            + "}\n\n"
            + "Chỉ trả về JSON, không thêm giải thích.";
    }

    private ReceiptOcrPayload parseOcrPayload(String rawBody) {
        String content = safe(rawBody).trim();
        if (content.isEmpty()) {
            throw new IllegalStateException(getString(R.string.receipt_error_parse));
        }
        JsonObject root = parseJsonObject(content);
        JsonObject payload = extractPayloadObject(root);

        String amountRaw = jsonValueAsString(payload, "Tổng tiền");
        Double amount = parseAmount(amountRaw);
        if (amount == null || amount <= 0.0) {
            throw new IllegalStateException(getString(R.string.receipt_error_invalid_amount));
        }

        return new ReceiptOcrPayload(
            jsonValueAsString(payload, "Thời gian"),
            amount,
            jsonValueAsString(payload, "Hạng mục"),
            jsonValueAsString(payload, "Nơi mua hàng"),
            jsonValueAsString(payload, "Địa chỉ"),
            jsonValueAsString(payload, "Phương thức thanh toán"),
            parseReceiptItems(payload)
        );
    }

    private JsonObject extractPayloadObject(JsonObject root) {
        if (containsReceiptKeys(root)) {
            return root;
        }
        for (String key : new String[] {"data", "result", "output", "response"}) {
            if (!root.has(key) || root.get(key) == null || root.get(key).isJsonNull()) {
                continue;
            }
            if (root.get(key).isJsonObject()) {
                JsonObject object = root.getAsJsonObject(key);
                if (containsReceiptKeys(object)) {
                    return object;
                }
            }
            if (root.get(key).isJsonPrimitive()) {
                String rawNested = root.get(key).getAsString();
                try {
                    JsonObject object = parseJsonObject(rawNested);
                    if (containsReceiptKeys(object)) {
                        return object;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        throw new IllegalStateException(getString(R.string.receipt_error_parse));
    }

    private JsonObject parseJsonObject(String rawJson) {
        String content = safe(rawJson).trim();
        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1);
        }
        return JsonParser.parseString(content).getAsJsonObject();
    }

    private boolean containsReceiptKeys(JsonObject object) {
        return object != null
            && (object.has("Tổng tiền")
            || object.has("Thời gian")
            || object.has("Hạng mục"));
    }

    private String jsonValueAsString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key) == null || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            String value = safe(object.get(key).getAsString()).trim();
            return "null".equalsIgnoreCase(value) ? "" : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<ReceiptLineItem> parseReceiptItems(JsonObject payload) {
        List<ReceiptLineItem> items = new ArrayList<>();
        if (payload == null || !payload.has("Danh sách mặt hàng")) {
            return items;
        }
        JsonArray itemArray = null;
        JsonElement rawItems = payload.get("Danh sách mặt hàng");
        if (rawItems != null && rawItems.isJsonArray()) {
            itemArray = rawItems.getAsJsonArray();
        } else if (rawItems != null && rawItems.isJsonPrimitive()) {
            String rawArray = safe(rawItems.getAsString()).trim();
            if (rawArray.startsWith("[") && rawArray.endsWith("]")) {
                try {
                    itemArray = JsonParser.parseString(rawArray).getAsJsonArray();
                } catch (Exception ignored) {
                    itemArray = null;
                }
            }
        }
        if (itemArray == null) {
            return items;
        }
        for (JsonElement element : itemArray) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject itemObject = element.getAsJsonObject();
            ReceiptLineItem item = new ReceiptLineItem(
                jsonValueAsString(itemObject, "Tên mặt hàng"),
                jsonValueAsString(itemObject, "Đơn giá"),
                jsonValueAsString(itemObject, "Số lượng"),
                jsonValueAsString(itemObject, "Thành tiền")
            );
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private Double parseAmount(String rawValue) {
        String normalized = safe(rawValue).replaceAll("[^0-9,.-]", "");
        if (normalized.isEmpty()) {
            return null;
        }
        int lastComma = normalized.lastIndexOf(',');
        int lastDot = normalized.lastIndexOf('.');
        if (lastComma > lastDot) {
            if (lastDot < 0 && normalized.indexOf(',') != normalized.lastIndexOf(',')) {
                normalized = normalized.replace(",", "");
            } else {
                normalized = normalized.replace(".", "");
                normalized = normalized.replace(',', '.');
            }
        } else if (lastDot > lastComma) {
            normalized = normalized.replace(",", "");
            if (normalized.indexOf('.') != normalized.lastIndexOf('.')) {
                normalized = normalized.replace(".", "");
            } else if (lastDot >= 0) {
                int digitsAfterDot = normalized.length() - normalized.lastIndexOf('.') - 1;
                if (digitsAfterDot == 3) {
                    normalized = normalized.replace(".", "");
                }
            }
        } else if (lastComma >= 0) {
            if (normalized.indexOf(',') != normalized.lastIndexOf(',')) {
                normalized = normalized.replace(",", "");
            } else {
                int digitsAfterComma = normalized.length() - normalized.lastIndexOf(',') - 1;
                if (digitsAfterComma == 3) {
                    normalized = normalized.replace(",", "");
                } else {
                    normalized = normalized.replace(',', '.');
                }
            }
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private void setLoading(boolean loading, String loadingMessage) {
        layoutLoading.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        tvLoading.setText(loadingMessage);
        if (loading) {
            setUploadingState(false, null, null);
        }
        updateBusyState();
    }

    private void setUploadingState(boolean uploading, Uri previewUri, String statusMessage) {
        layoutScanning.setVisibility(uploading ? android.view.View.VISIBLE : android.view.View.GONE);
        if (uploading) {
            ivScanPreview.setImageURI(previewUri);
            tvScanStatus.setText(
                safe(statusMessage).trim().isEmpty()
                    ? getString(R.string.receipt_status_uploading)
                    : statusMessage
            );
            startScanLineAnimation();
        } else {
            stopScanLineAnimation();
            ivScanPreview.setImageDrawable(null);
        }
        updateBusyState();
    }

    private void startScanLineAnimation() {
        stopScanLineAnimation();
        frameScanPreview.post(() -> {
            int travelDistance = frameScanPreview.getHeight() - viewScanLine.getHeight();
            if (travelDistance <= 0) {
                return;
            }
            scanLineAnimator = ObjectAnimator.ofFloat(viewScanLine, "translationY", 0f, (float) travelDistance);
            scanLineAnimator.setDuration(1600L);
            scanLineAnimator.setRepeatCount(ValueAnimator.INFINITE);
            scanLineAnimator.setRepeatMode(ValueAnimator.REVERSE);
            scanLineAnimator.start();
        });
    }

    private void stopScanLineAnimation() {
        if (scanLineAnimator != null) {
            scanLineAnimator.cancel();
            scanLineAnimator = null;
        }
        viewScanLine.setTranslationY(0f);
    }

    private void updateBusyState() {
        boolean busy = layoutLoading.getVisibility() == android.view.View.VISIBLE
            || layoutScanning.getVisibility() == android.view.View.VISIBLE;
        btnBack.setEnabled(!busy);
        btnCapture.setEnabled(!busy);
        btnChooseGallery.setEnabled(!busy);
        btnRetry.setEnabled(!busy);
    }

    private void showErrorState(String message) {
        setLoading(false, getString(R.string.receipt_processing));
        tvStatus.setVisibility(android.view.View.VISIBLE);
        tvStatus.setText(message);
        btnRetry.setVisibility(lastCroppedUri == null ? android.view.View.GONE : android.view.View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isUiClosed() {
        return activityClosed || isFinishing() || isDestroyed();
    }

    private void cleanupTempImages() {
        File folder = new File(getCacheDir(), TEMP_FOLDER);
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    private static final class ReceiptOcrPayload {
        private final String receiptDatetime;
        private final double totalAmount;
        private final String categoryName;
        private final String merchantName;
        private final String address;
        private final String paymentMethod;
        private final List<ReceiptLineItem> items;

        private ReceiptOcrPayload(
            String receiptDatetime,
            double totalAmount,
            String categoryName,
            String merchantName,
            String address,
            String paymentMethod,
            List<ReceiptLineItem> items
        ) {
            this.receiptDatetime = receiptDatetime;
            this.totalAmount = totalAmount;
            this.categoryName = categoryName;
            this.merchantName = merchantName;
            this.address = address;
            this.paymentMethod = paymentMethod;
            this.items = items == null ? new ArrayList<>() : items;
        }
    }

    private static final class ReceiptLineItem {
        private final String name;
        private final String unitPrice;
        private final String quantity;
        private final String total;

        private ReceiptLineItem(String name, String unitPrice, String quantity, String total) {
            this.name = safeValue(name);
            this.unitPrice = safeValue(unitPrice);
            this.quantity = safeValue(quantity);
            this.total = safeValue(total);
        }

        private boolean isEmpty() {
            return name.isEmpty() && unitPrice.isEmpty() && quantity.isEmpty() && total.isEmpty();
        }

        private String toDisplayLine() {
            List<String> parts = new ArrayList<>();
            if (!name.isEmpty()) {
                parts.add(name);
            }
            if (!quantity.isEmpty()) {
                parts.add("SL: " + quantity);
            }
            if (!unitPrice.isEmpty()) {
                parts.add("Đơn giá: " + unitPrice);
            }
            if (!total.isEmpty()) {
                parts.add("Thành tiền: " + total);
            }
            return String.join(" | ", parts);
        }

        private static String safeValue(String value) {
            if (value == null) {
                return "";
            }
            String normalized = value.trim();
            return "null".equalsIgnoreCase(normalized) ? "" : normalized;
        }
    }

    private static final class UploadImageData {
        private final File file;
        private final String mimeType;

        private UploadImageData(File file, String mimeType) {
            this.file = file;
            this.mimeType = mimeType;
        }
    }

    private static final class CaptureOutput {
        private final File file;
        private final Uri uri;

        private CaptureOutput(File file, Uri uri) {
            this.file = file;
            this.uri = uri;
        }
    }

    @Override
    protected void onDestroy() {
        activityClosed = true;
        stopScanLineAnimation();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        ioExecutor.shutdownNow();
        cleanupTempImages();
        super.onDestroy();
    }
}
