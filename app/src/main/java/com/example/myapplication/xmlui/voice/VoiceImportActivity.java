package com.example.myapplication.xmlui.voice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.R;
import com.example.myapplication.finance.data.FirestoreFinanceRepository;
import com.example.myapplication.finance.model.ExchangeRateSnapshot;
import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.example.myapplication.finance.model.Wallet;
import com.example.myapplication.xmlui.AddTransactionActivity;
import com.example.myapplication.xmlui.CategoryFallbackMerger;
import com.example.myapplication.xmlui.currency.CurrencyRateUtils;
import com.example.myapplication.xmlui.currency.ExchangeRateSnapshotLoader;
import com.example.myapplication.xmlui.receipt.ReceiptAiApiClient;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Source;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
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

public class VoiceImportActivity extends AppCompatActivity {
    public static final String EXTRA_DIRECT_SAVED = "extra_voice_direct_saved";
    private static final String TEMP_FOLDER = "voice_entries";
    private static final String DEFAULT_INCOME_CATEGORY = "Tiền vào";
    private static final String DEFAULT_EXPENSE_CATEGORY = "Tiền ra";
    private static final DateTimeFormatter[] VOICE_DATETIME_FORMATTERS = new DateTimeFormatter[] {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    };
    private static final DateTimeFormatter[] VOICE_DATE_FORMATTERS = new DateTimeFormatter[] {
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> requestAudioPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                startRecordingInternal();
            } else {
                showError(getString(R.string.voice_entry_error_audio_permission));
            }
        });
    private final ActivityResultLauncher<Intent> detailEditLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                return;
            }
            applyEditedResultFromDetailEditor(result.getData());
        });

    private ImageButton btnBack;
    private ImageButton btnDelete;
    private ImageButton btnApply;
    private ImageButton btnResultEdit;
    private ImageButton btnResultClose;
    private FloatingActionButton fabRecord;
    private TextView tvActionHint;
    private TextView tvStatus;
    private TextView tvLoading;
    private LinearLayout layoutLoading;
    private android.view.View layoutCenterIcon;
    private TextView tvHintChip;
    private android.view.View cardResult;
    private TextView tvResultCategory;
    private TextView tvResultAmount;
    private TextView tvResultNote;
    private TextView tvResultDate;
    private TextView tvResultPayment;
    private ImageView ivResultIcon;

    private MediaRecorder mediaRecorder;
    private File recordingFile;
    private boolean isRecording;
    private boolean isProcessing;
    private VoiceAiPayload latestPayload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_import);
        bindViews();
        setupActions();
        renderIdleState();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnVoiceBack);
        btnDelete = findViewById(R.id.btnVoiceDelete);
        btnApply = findViewById(R.id.btnVoiceApply);
        btnResultEdit = findViewById(R.id.btnVoiceResultEdit);
        btnResultClose = findViewById(R.id.btnVoiceResultClose);
        fabRecord = findViewById(R.id.fabVoiceRecord);
        tvActionHint = findViewById(R.id.tvVoiceActionHint);
        tvStatus = findViewById(R.id.tvVoiceStatus);
        tvLoading = findViewById(R.id.tvVoiceLoading);
        layoutLoading = findViewById(R.id.layoutVoiceLoading);
        layoutCenterIcon = findViewById(R.id.layoutVoiceCenterIcon);
        tvHintChip = findViewById(R.id.tvVoiceHintChip);
        cardResult = findViewById(R.id.cardVoiceResult);
        tvResultCategory = findViewById(R.id.tvVoiceResultCategory);
        tvResultAmount = findViewById(R.id.tvVoiceResultAmount);
        tvResultNote = findViewById(R.id.tvVoiceResultNote);
        tvResultDate = findViewById(R.id.tvVoiceResultDate);
        tvResultPayment = findViewById(R.id.tvVoiceResultPayment);
        ivResultIcon = findViewById(R.id.ivVoiceResultIcon);
    }

    private void setupActions() {
        btnBack.setOnClickListener(v -> finish());
        fabRecord.setOnClickListener(v -> onRecordActionPressed());
        btnDelete.setOnClickListener(v -> clearResultCard());
        btnApply.setOnClickListener(v -> saveVoiceEntryImmediately());
        btnResultEdit.setOnClickListener(v -> openDetailedEditScreen());
        btnResultClose.setOnClickListener(v -> clearResultCard());
    }

    private void onRecordActionPressed() {
        if (isProcessing) {
            return;
        }
        if (isRecording) {
            stopRecordingAndAnalyze();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        startRecordingInternal();
    }

    private void startRecordingInternal() {
        if (isProcessing || isRecording) {
            return;
        }
        try {
            recordingFile = createTempAudioFile();
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(96_000);
            mediaRecorder.setAudioSamplingRate(44_100);
            mediaRecorder.setOutputFile(recordingFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            tvStatus.setVisibility(android.view.View.GONE);
            updateActionUi();
        } catch (Exception error) {
            releaseRecorder();
            deleteTempRecordingFile();
            showError(getString(R.string.voice_entry_error_record));
        }
    }

    private void stopRecordingAndAnalyze() {
        if (!isRecording) {
            return;
        }
        boolean stopped = stopRecorderSafely();
        isRecording = false;
        updateActionUi();
        if (!stopped || recordingFile == null || !recordingFile.exists() || recordingFile.length() <= 0L) {
            deleteTempRecordingFile();
            showError(getString(R.string.voice_entry_error_record));
            return;
        }
        uploadVoiceToAi(recordingFile);
    }

    private boolean stopRecorderSafely() {
        if (mediaRecorder == null) {
            return false;
        }
        try {
            mediaRecorder.stop();
            return true;
        } catch (RuntimeException runtimeException) {
            return false;
        } finally {
            releaseRecorder();
        }
    }

    private void releaseRecorder() {
        if (mediaRecorder == null) {
            return;
        }
        try {
            mediaRecorder.reset();
        } catch (Exception ignored) {
        }
        try {
            mediaRecorder.release();
        } catch (Exception ignored) {
        }
        mediaRecorder = null;
    }

    private File createTempAudioFile() throws Exception {
        File folder = new File(getCacheDir(), TEMP_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create voice temp folder");
        }
        return File.createTempFile("voice_", ".m4a", folder);
    }

    private void uploadVoiceToAi(File audioFile) {
        List<String> apiUrls = buildVoiceApiCandidates();
        if (apiUrls.isEmpty()) {
            showError(getString(R.string.voice_entry_error_config));
            return;
        }
        setProcessing(true, getString(R.string.voice_entry_status_processing));
        ioExecutor.submit(() -> {
            try {
                List<TransactionCategory> categories = loadCategoriesForVoice();
                List<Wallet> wallets = loadWalletsForVoice();
                CategoryPromptLists promptCategoryLists = buildPromptCategoryLists(categories);
                RequestBody audioBody = RequestBody.create(MediaType.parse("audio/mp4"), audioFile);
                MultipartBody.Part audioPart = MultipartBody.Part.createFormData("audio", audioFile.getName(), audioBody);
                RequestBody promptPart = RequestBody.create(
                    MediaType.parse("text/plain; charset=utf-8"),
                    buildPrompt(promptCategoryLists, wallets)
                );
                String rawBody = null;
                String lastError = "";
                for (String apiUrl : apiUrls) {
                    Response<ResponseBody> response = ReceiptAiApiClient
                        .getService()
                        .parseVoiceNote(apiUrl, audioPart, promptPart)
                        .execute();
                    if (response.isSuccessful() && response.body() != null) {
                        rawBody = response.body().string();
                        break;
                    }
                    lastError = "HTTP " + response.code() + " - " + apiUrl;
                    if (response.code() != 404) {
                        break;
                    }
                }
                if (rawBody == null || rawBody.trim().isEmpty()) {
                    throw new IllegalStateException(
                        lastError.isEmpty()
                            ? "Không thể kết nối endpoint /asr"
                            : lastError
                    );
                }
                VoiceAiPayload payload = parseVoicePayload(rawBody, categories, wallets);
                runOnUiThread(() -> {
                    setProcessing(false, null);
                    renderResult(payload);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setProcessing(false, null);
                    String message = getString(R.string.voice_entry_error_upload) + " (" + safe(error.getMessage()) + ")";
                    showError(message);
                });
            } finally {
                deleteTempRecordingFile();
            }
        });
    }

    private List<TransactionCategory> loadCategoriesForVoice() {
        List<TransactionCategory> categories = null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirestoreFinanceRepository repository = new FirestoreFinanceRepository();
            try {
                categories = repository.getCategories(user.getUid(), Source.SERVER);
            } catch (Exception serverError) {
                try {
                    categories = repository.getCategories(user.getUid(), Source.CACHE);
                } catch (Exception cacheError) {
                    categories = null;
                }
            }
        }
        return CategoryFallbackMerger.mergeWithFallbacks(categories);
    }

    private List<Wallet> loadWalletsForVoice() {
        List<Wallet> wallets = new ArrayList<>();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return wallets;
        }
        FirestoreFinanceRepository repository = new FirestoreFinanceRepository();
        try {
            List<Wallet> serverWallets = repository.getWallets(user.getUid(), Source.SERVER);
            if (serverWallets != null && !serverWallets.isEmpty()) {
                wallets.addAll(serverWallets);
                return wallets;
            }
        } catch (Exception ignored) {
        }
        try {
            List<Wallet> cacheWallets = repository.getWallets(user.getUid(), Source.CACHE);
            if (cacheWallets != null && !cacheWallets.isEmpty()) {
                wallets.addAll(cacheWallets);
            }
        } catch (Exception ignored) {
        }
        return wallets;
    }

    private CategoryPromptLists buildPromptCategoryLists(List<TransactionCategory> categories) {
        LinkedHashMap<String, String> incomeNames = new LinkedHashMap<>();
        LinkedHashMap<String, String> expenseNames = new LinkedHashMap<>();
        List<TransactionCategory> mergedCategories = CategoryFallbackMerger.mergeWithFallbacks(categories);
        for (TransactionCategory category : mergedCategories) {
            if (category == null) {
                continue;
            }
            if (category.getType() == TransactionType.INCOME) {
                addPromptCategoryName(incomeNames, category.getName());
            } else if (category.getType() == TransactionType.EXPENSE) {
                addPromptCategoryName(expenseNames, category.getName());
            }
        }
        if (incomeNames.isEmpty()) {
            addPromptCategoryName(incomeNames, DEFAULT_INCOME_CATEGORY);
        }
        if (expenseNames.isEmpty()) {
            addPromptCategoryName(expenseNames, DEFAULT_EXPENSE_CATEGORY);
        }
        return new CategoryPromptLists(
            new ArrayList<>(incomeNames.values()),
            new ArrayList<>(expenseNames.values())
        );
    }

    private void addPromptCategoryName(
        @NonNull LinkedHashMap<String, String> normalizedNameMap,
        String rawName
    ) {
        String displayName = safe(rawName).trim();
        if (displayName.isEmpty()) {
            return;
        }
        String normalizedKey = normalizeCategoryKey(displayName);
        if (normalizedKey.isEmpty()) {
            normalizedKey = displayName.toLowerCase(Locale.ROOT);
        }
        if (!normalizedNameMap.containsKey(normalizedKey)) {
            normalizedNameMap.put(normalizedKey, displayName);
        }
    }

    private String buildPrompt(@NonNull CategoryPromptLists categoryLists, List<Wallet> wallets) {
        String incomeCategoryText = categoryLists.incomeCategories.isEmpty()
            ? DEFAULT_INCOME_CATEGORY
            : String.join(", ", categoryLists.incomeCategories);
        String expenseCategoryText = categoryLists.expenseCategories.isEmpty()
            ? DEFAULT_EXPENSE_CATEGORY
            : String.join(", ", categoryLists.expenseCategories);
        String walletText = buildWalletPromptText(wallets);
        return "Bạn là một chuyên gia tài chính và hệ thống trích xuất dữ liệu giao dịch. /no_think\n"
            + "Nhiệm vụ của bạn:\n"
            + "1. Đọc hiểu nội dung ghi chép tài chính từ người dùng.\n"
            + "2. Đưa ra một lời khuyên tài chính thực tế và đồng cảm.\n"
            + "3. Tìm và chuẩn hóa thời gian theo định dạng DD/MM/YYYY HH:MM.\n"
            + "   - Chỉ trả về thời gian NẾU CÓ nhắc đến trong văn bản.\n"
            + "   - Nếu hoàn toàn không tìm thấy thời gian trong văn bản, BẮT BUỘC để null.\n"
            + "4. Trích xuất tổng số tiền giao dịch thành một số nguyên (integer), không chứa ký tự.\n"
            + "5. Phân loại giao dịch thành một trong hai giá trị sau: [\"thu tiền\", \"chi tiền\"].\n"
            + "   - KHÔNG được trả về \"chuyển khoản\".\n"
            + "6. Danh sách hạng mục THU: [" + incomeCategoryText + "].\n"
            + "7. Danh sách hạng mục CHI: [" + expenseCategoryText + "].\n"
            + "8. Hạng mục phải khớp loại giao dịch:\n"
            + "   - Nếu \"phan_loai\" = \"thu tiền\" thì \"hang_muc\" bắt buộc thuộc danh sách THU.\n"
            + "   - Nếu \"phan_loai\" = \"chi tiền\" thì \"hang_muc\" bắt buộc thuộc danh sách CHI.\n"
            + "   - BẮT BUỘC chọn đúng 1 hạng mục phù hợp nhất trong danh sách cùng loại.\n"
            + "9. Danh sách tài khoản ví hợp lệ của người dùng: [" + walletText + "].\n"
            + "10. Trích xuất \"tai_khoan_vi\":\n"
            + "   - Nếu nhận diện rõ tài khoản được nhắc đến thì chọn đúng 1 tên trong danh sách tài khoản ví.\n"
            + "   - Nếu không nhận diện được hoặc không được nhắc đến thì trả về null.\n"
            + "11. Tóm tắt ghi chú: Chỉ tóm lược ngắn gọn các thông tin quan trọng nhất từ ghi chép gốc.\n"
            + "12. Trả về dữ liệu dưới định dạng JSON với cấu trúc sau:\n\n"
            + "{\n"
            + "  \"loi_khuyen\": \"<lời khuyên tài chính>\",\n"
            + "  \"thoi_gian\": \"<ngày giờ hoặc null>\",\n"
            + "  \"tong_tien\": <số tiền tổng cộng>,\n"
            + "  \"phan_loai\": \"<giá trị phân loại>\",\n"
            + "  \"hang_muc\": \"<giá trị hạng mục>\",\n"
            + "  \"tai_khoan_vi\": <tên tài khoản ví hoặc null>,\n"
            + "  \"ghi_chu\": \"<thông tin tóm tắt>\"\n"
            + "}\n\n"
            + "Chỉ trả về JSON, không thêm giải thích.";
    }

    private String buildWalletPromptText(List<Wallet> wallets) {
        if (wallets == null || wallets.isEmpty()) {
            return "null";
        }
        LinkedHashMap<String, String> walletNames = new LinkedHashMap<>();
        for (Wallet wallet : wallets) {
            if (wallet == null || wallet.isLocked()) {
                continue;
            }
            String walletName = safe(wallet.getName()).trim();
            if (walletName.isEmpty()) {
                continue;
            }
            String normalizedName = normalizeCategoryKey(walletName);
            if (normalizedName.isEmpty()) {
                normalizedName = walletName.toLowerCase(Locale.ROOT);
            }
            if (!walletNames.containsKey(normalizedName)) {
                walletNames.put(normalizedName, walletName);
            }
        }
        if (walletNames.isEmpty()) {
            return "null";
        }
        return String.join(", ", walletNames.values());
    }

    private VoiceAiPayload parseVoicePayload(
        String rawBody,
        List<TransactionCategory> categories,
        List<Wallet> wallets
    ) {
        JsonObject root = parseJsonObject(rawBody);
        JsonObject payload = extractPayloadObject(root);
        Double amount = parseAmount(payload, "tong_tien", "Tổng tiền");
        if (amount == null || amount <= 0d) {
            throw new IllegalStateException(getString(R.string.voice_entry_error_invalid_amount));
        }
        String mode = mapMode(jsonValueAsString(payload, "phan_loai", "Phân loại"));
        String note = jsonValueAsString(payload, "ghi_chu", "Ghi chú");
        String advice = jsonValueAsString(payload, "loi_khuyen", "Lời khuyên");
        if (note.isEmpty()) {
            note = advice;
        }
        if (note.isEmpty()) {
            note = getString(R.string.voice_entry_result_default_note);
        }
        long timeMillis = parseVoiceDateTimeMillis(jsonValueAsString(payload, "thoi_gian", "Thời gian"));
        String category = mapCategoryForPrefill(
            jsonValueAsString(payload, "hang_muc", "Hạng mục"),
            mode,
            categories
        );
        String sourceWalletId = mapWalletIdForPrefill(
            jsonValueAsString(
                payload,
                "tai_khoan_vi",
                "tai_khoan",
                "tai_khoan_nguon",
                "vi_su_dung",
                "tai_khoan_su_dung"
            ),
            wallets
        );
        return new VoiceAiPayload(
            mode,
            amount,
            category,
            note,
            timeMillis,
            mapPaymentLabel(mode),
            sourceWalletId,
            null
        );
    }

    private String mapWalletIdForPrefill(String rawWalletName, List<Wallet> wallets) {
        String normalizedTarget = normalizeCategoryKey(rawWalletName);
        if (normalizedTarget.isEmpty() || wallets == null || wallets.isEmpty()) {
            return "";
        }
        String partialMatchId = "";
        for (Wallet wallet : wallets) {
            if (wallet == null || wallet.isLocked()) {
                continue;
            }
            String walletName = safe(wallet.getName()).trim();
            if (walletName.isEmpty()) {
                continue;
            }
            String normalizedName = normalizeCategoryKey(walletName);
            if (normalizedName.equals(normalizedTarget)) {
                return safe(wallet.getId()).trim();
            }
            if (partialMatchId.isEmpty()
                && (normalizedName.contains(normalizedTarget) || normalizedTarget.contains(normalizedName))) {
                partialMatchId = safe(wallet.getId()).trim();
            }
        }
        return partialMatchId;
    }

    private JsonObject extractPayloadObject(JsonObject root) {
        if (containsVoiceKeys(root)) {
            return root;
        }
        for (String key : new String[] {"result", "data", "output", "response"}) {
            if (!root.has(key) || root.get(key) == null || root.get(key).isJsonNull()) {
                continue;
            }
            JsonElement nested = root.get(key);
            if (nested.isJsonObject()) {
                JsonObject object = nested.getAsJsonObject();
                if (containsVoiceKeys(object)) {
                    return object;
                }
            }
            if (nested.isJsonPrimitive()) {
                try {
                    JsonObject object = parseJsonObject(nested.getAsString());
                    if (containsVoiceKeys(object)) {
                        return object;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        throw new IllegalStateException(getString(R.string.voice_entry_error_parse));
    }

    private JsonObject parseJsonObject(String rawJson) {
        String content = safe(rawJson).trim();
        if (content.isEmpty()) {
            throw new IllegalStateException(getString(R.string.voice_entry_error_parse));
        }
        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1);
        }
        return JsonParser.parseString(content).getAsJsonObject();
    }

    private boolean containsVoiceKeys(JsonObject object) {
        return object != null
            && (object.has("tong_tien")
            || object.has("Tổng tiền")
            || object.has("phan_loai")
            || object.has("thoi_gian")
            || object.has("hang_muc"));
    }

    private String jsonValueAsString(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key == null || !object.has(key) || object.get(key) == null || object.get(key).isJsonNull()) {
                continue;
            }
            JsonElement value = object.get(key);
            try {
                String text = value.isJsonPrimitive() ? safe(value.getAsString()).trim() : safe(value.toString()).trim();
                if ("null".equalsIgnoreCase(text)) {
                    return "";
                }
                return text;
            } catch (Exception ignored) {
                return "";
            }
        }
        return "";
    }

    private Double parseAmount(JsonObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || !object.has(key) || object.get(key) == null || object.get(key).isJsonNull()) {
                continue;
            }
            JsonElement value = object.get(key);
            try {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    return value.getAsDouble();
                }
            } catch (Exception ignored) {
            }
            Double parsed = parseAmountString(value.toString());
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Double parseAmountString(String rawValue) {
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

    private long parseVoiceDateTimeMillis(String raw) {
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
        for (DateTimeFormatter formatter : VOICE_DATETIME_FORMATTERS) {
            try {
                LocalDateTime parsed = LocalDateTime.parse(value, formatter);
                return parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        for (DateTimeFormatter formatter : VOICE_DATE_FORMATTERS) {
            try {
                LocalDate parsed = LocalDate.parse(value, formatter);
                return parsed.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        return System.currentTimeMillis();
    }

    private String mapMode(String rawMode) {
        String normalized = normalizeCategoryKey(rawMode);
        if (normalized.contains("thu")) {
            return AddTransactionActivity.MODE_INCOME;
        }
        return AddTransactionActivity.MODE_EXPENSE;
    }

    private String mapCategoryForPrefill(String rawCategory, String mode, List<TransactionCategory> categories) {
        String normalizedTarget = normalizeCategoryKey(rawCategory);
        TransactionType targetType = AddTransactionActivity.MODE_INCOME.equals(mode)
            ? TransactionType.INCOME
            : TransactionType.EXPENSE;
        List<TransactionCategory> typedCategories = new ArrayList<>();
        if (categories != null) {
            for (TransactionCategory category : categories) {
                if (category != null && category.getType() == targetType) {
                    typedCategories.add(category);
                }
            }
        }

        for (TransactionCategory category : typedCategories) {
            String normalizedName = normalizeCategoryKey(category.getName());
            if (!normalizedTarget.isEmpty() && normalizedName.equals(normalizedTarget)) {
                return safe(category.getName()).trim();
            }
        }

        for (TransactionCategory category : typedCategories) {
            String normalizedName = normalizeCategoryKey(category.getName());
            if (normalizedTarget.isEmpty()) {
                continue;
            }
            if (normalizedName.contains(normalizedTarget) || normalizedTarget.contains(normalizedName)) {
                return safe(category.getName()).trim();
            }
        }

        String strictFallback = targetType == TransactionType.EXPENSE
            ? DEFAULT_EXPENSE_CATEGORY
            : DEFAULT_INCOME_CATEGORY;
        String normalizedFallback = normalizeCategoryKey(strictFallback);
        for (TransactionCategory category : typedCategories) {
            if (normalizeCategoryKey(category.getName()).equals(normalizedFallback)) {
                return safe(category.getName()).trim();
            }
        }

        for (TransactionCategory category : typedCategories) {
            String candidate = safe(category.getName()).trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }

        return strictFallback;
    }

    private String mapPaymentLabel(String mode) {
        if (AddTransactionActivity.MODE_TRANSFER.equals(mode)) {
            return getString(R.string.app_title_add_transfer);
        }
        if (AddTransactionActivity.MODE_INCOME.equals(mode)) {
            return getString(R.string.label_destination_wallet);
        }
        return getString(R.string.voice_entry_result_default_payment);
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

    private void renderResult(@NonNull VoiceAiPayload payload) {
        latestPayload = payload;
        cardResult.setVisibility(android.view.View.VISIBLE);
        layoutCenterIcon.setVisibility(android.view.View.GONE);
        tvHintChip.setVisibility(android.view.View.GONE);
        btnDelete.setVisibility(android.view.View.VISIBLE);
        btnApply.setVisibility(android.view.View.VISIBLE);
        ivResultIcon.setImageResource(R.drawable.ic_overview_bag);
        tvResultCategory.setText(
            payload.categoryName.isEmpty()
                ? getString(R.string.voice_entry_result_default_category)
                : payload.categoryName
        );
        tvResultAmount.setText(formatCurrency(payload.amount));
        tvResultNote.setText(payload.note);
        tvResultDate.setText(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
            .format(Instant.ofEpochMilli(payload.timeMillis).atZone(ZoneId.systemDefault())));
        tvResultPayment.setText(
            payload.paymentLabel.isEmpty()
                ? getString(R.string.voice_entry_result_default_payment)
                : payload.paymentLabel
        );
        tvStatus.setVisibility(android.view.View.GONE);
        updateActionUi();
    }

    private void clearResultCard() {
        latestPayload = null;
        cardResult.setVisibility(android.view.View.GONE);
        layoutCenterIcon.setVisibility(android.view.View.VISIBLE);
        tvHintChip.setVisibility(android.view.View.VISIBLE);
        btnDelete.setVisibility(android.view.View.GONE);
        btnApply.setVisibility(android.view.View.GONE);
        tvStatus.setVisibility(android.view.View.GONE);
        updateActionUi();
    }

    private void openDetailedEditScreen() {
        if (latestPayload == null || isProcessing || isRecording) {
            return;
        }
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra(AddTransactionActivity.EXTRA_PREVIEW_EDIT_ONLY, true);
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_MODE, latestPayload.mode);
        if (latestPayload.sourceWalletId != null && !latestPayload.sourceWalletId.isBlank()) {
            intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_SOURCE_WALLET_ID, latestPayload.sourceWalletId);
        }
        if (latestPayload.destinationWalletId != null && !latestPayload.destinationWalletId.isBlank()) {
            intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_DESTINATION_WALLET_ID, latestPayload.destinationWalletId);
        }
        if (latestPayload.amount > 0.0d) {
            intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_AMOUNT, latestPayload.amount);
        }
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_NOTE, latestPayload.note);
        if (!latestPayload.categoryName.isEmpty()) {
            intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_CATEGORY_NAME, latestPayload.categoryName);
        }
        intent.putExtra(AddTransactionActivity.EXTRA_PREFILL_TIME_MILLIS, latestPayload.timeMillis);
        detailEditLauncher.launch(intent);
    }

    private void applyEditedResultFromDetailEditor(@NonNull Intent data) {
        if (latestPayload == null) {
            return;
        }
        String editedMode = safe(data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_MODE)).trim();
        if (editedMode.isEmpty()) {
            editedMode = latestPayload.mode;
        }

        double editedAmount = data.getDoubleExtra(AddTransactionActivity.EXTRA_RESULT_AMOUNT, latestPayload.amount);
        if (editedAmount <= 0.0d) {
            editedAmount = latestPayload.amount;
        }

        String editedNote = safe(data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_NOTE)).trim();
        if (editedNote.isEmpty()) {
            editedNote = latestPayload.note;
        }

        String editedCategory = safe(data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_CATEGORY_NAME)).trim();
        if (editedCategory.isEmpty()) {
            editedCategory = latestPayload.categoryName;
        }

        long editedTimeMillis = data.getLongExtra(AddTransactionActivity.EXTRA_RESULT_TIME_MILLIS, latestPayload.timeMillis);
        if (editedTimeMillis <= 0L) {
            editedTimeMillis = latestPayload.timeMillis;
        }

        String editedSourceWalletId = safe(data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_SOURCE_WALLET_ID)).trim();
        if (editedSourceWalletId.isEmpty()) {
            editedSourceWalletId = latestPayload.sourceWalletId;
        }

        String editedDestinationWalletId =
            safe(data.getStringExtra(AddTransactionActivity.EXTRA_RESULT_DESTINATION_WALLET_ID)).trim();
        if (editedDestinationWalletId.isEmpty()) {
            editedDestinationWalletId = latestPayload.destinationWalletId;
        }

        renderResult(
            new VoiceAiPayload(
                editedMode,
                editedAmount,
                editedCategory,
                editedNote,
                editedTimeMillis,
                mapPaymentLabel(editedMode),
                editedSourceWalletId,
                editedDestinationWalletId
            )
        );
    }

    private void saveVoiceEntryImmediately() {
        if (latestPayload == null || isProcessing || isRecording) {
            return;
        }
        VoiceAiPayload payload = latestPayload;
        setProcessing(true, getString(R.string.voice_entry_status_saving));
        ioExecutor.execute(() -> {
            String errorMessage = null;
            try {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser == null) {
                    throw new IllegalStateException(getString(R.string.auth_error_session_expired));
                }
                String userId = currentUser.getUid();
                FirestoreFinanceRepository repository = new FirestoreFinanceRepository();
                List<Wallet> wallets = loadWalletsForSave(repository, userId);
                SaveDecision saveDecision = resolveSaveDecision(payload, wallets);
                Timestamp createdAt = new Timestamp(new Date(payload.timeMillis));

                if (saveDecision.type == TransactionType.TRANSFER) {
                    Wallet sourceWallet = findWalletById(wallets, saveDecision.sourceWalletId);
                    Wallet destinationWallet = findWalletById(wallets, saveDecision.destinationWalletId);
                    if (sourceWallet == null) {
                        throw new IllegalStateException(getString(R.string.error_wallet_required));
                    }
                    if (destinationWallet == null) {
                        throw new IllegalStateException(getString(R.string.error_destination_required));
                    }

                    String sourceCurrency = CurrencyRateUtils.normalizeCurrency(sourceWallet.getCurrency());
                    String destinationCurrency = CurrencyRateUtils.normalizeCurrency(destinationWallet.getCurrency());
                    double destinationAmount = payload.amount;
                    Double exchangeRate = null;
                    ExchangeRateSnapshot snapshot = null;

                    if (!sourceCurrency.equals(destinationCurrency)) {
                        snapshot = ExchangeRateSnapshotLoader.loadWithFallback(repository, userId);
                        if (snapshot == null) {
                            throw new IllegalStateException(getString(R.string.error_currency_rate_unavailable));
                        }
                        exchangeRate = snapshot.conversionRate(sourceCurrency, destinationCurrency);
                        if (exchangeRate == null || exchangeRate <= 0.0d) {
                            throw new IllegalStateException(getString(R.string.error_currency_rate_unavailable));
                        }
                        destinationAmount = CurrencyRateUtils.roundAmount(payload.amount * exchangeRate);
                        if (destinationAmount <= 0.0d) {
                            throw new IllegalStateException(getString(R.string.error_currency_rate_unavailable));
                        }
                    }

                    repository.addTransferTransaction(
                        userId,
                        saveDecision.sourceWalletId,
                        saveDecision.destinationWalletId,
                        payload.amount,
                        destinationAmount,
                        saveDecision.category,
                        payload.note,
                        sourceCurrency,
                        destinationCurrency,
                        exchangeRate,
                        snapshot == null ? null : snapshot.getFetchedAt(),
                        createdAt
                    );
                } else {
                    repository.addTransaction(
                        userId,
                        saveDecision.sourceWalletId,
                        saveDecision.type,
                        payload.amount,
                        saveDecision.category,
                        payload.note,
                        null,
                        createdAt
                    );
                }
            } catch (Exception error) {
                errorMessage = messageOrFallback(error, getString(R.string.voice_entry_error_save_failed));
            }

            String finalErrorMessage = errorMessage;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setProcessing(false, null);
                if (finalErrorMessage != null && !finalErrorMessage.isBlank()) {
                    showError(finalErrorMessage);
                    return;
                }
                Toast.makeText(this, R.string.message_transaction_saved, Toast.LENGTH_SHORT).show();
                Intent data = new Intent();
                data.putExtra(EXTRA_DIRECT_SAVED, true);
                setResult(RESULT_OK, data);
                finish();
            });
        });
    }

    private List<Wallet> loadWalletsForSave(
        @NonNull FirestoreFinanceRepository repository,
        @NonNull String userId
    ) throws Exception {
        try {
            List<Wallet> serverWallets = repository.getWallets(userId, Source.SERVER);
            if (!serverWallets.isEmpty()) {
                return serverWallets;
            }
        } catch (Exception ignored) {
        }
        return repository.getWallets(userId, Source.CACHE);
    }

    private SaveDecision resolveSaveDecision(
        @NonNull VoiceAiPayload payload,
        @NonNull List<Wallet> wallets
    ) {
        List<Wallet> availableWallets = new ArrayList<>();
        for (Wallet wallet : wallets) {
            if (wallet == null || wallet.isLocked()) {
                continue;
            }
            availableWallets.add(wallet);
        }
        if (availableWallets.isEmpty()) {
            throw new IllegalStateException(getString(R.string.error_wallet_required));
        }

        TransactionType type = mapModeToTransactionType(payload.mode);
        Wallet sourceWallet = findWalletById(availableWallets, payload.sourceWalletId);
        if (sourceWallet == null) {
            sourceWallet = chooseDefaultSourceWallet(availableWallets, type);
        }
        if (sourceWallet == null) {
            throw new IllegalStateException(getString(R.string.error_wallet_required));
        }

        String category = payload.categoryName == null ? "" : payload.categoryName.trim();
        if (category.isEmpty()) {
            category = type == TransactionType.TRANSFER
                ? getString(R.string.transaction_type_transfer)
                : getString(R.string.default_category_other);
        }

        String destinationWalletId = null;
        if (type == TransactionType.TRANSFER) {
            Wallet destinationWallet = findWalletById(availableWallets, payload.destinationWalletId);
            if (destinationWallet == null || destinationWallet.getId().equals(sourceWallet.getId())) {
                destinationWallet = chooseDefaultDestinationWallet(availableWallets, sourceWallet.getId());
            }
            if (destinationWallet == null) {
                throw new IllegalStateException(getString(R.string.error_destination_required));
            }
            destinationWalletId = destinationWallet.getId();
        }

        return new SaveDecision(type, sourceWallet.getId(), destinationWalletId, category);
    }

    private Wallet findWalletById(@NonNull List<Wallet> wallets, String walletId) {
        if (walletId == null || walletId.isBlank()) {
            return null;
        }
        for (Wallet wallet : wallets) {
            if (walletId.equals(wallet.getId())) {
                return wallet;
            }
        }
        return null;
    }

    private Wallet chooseDefaultSourceWallet(@NonNull List<Wallet> wallets, @NonNull TransactionType type) {
        if (wallets.isEmpty()) {
            return null;
        }
        if (type == TransactionType.EXPENSE || type == TransactionType.TRANSFER) {
            for (Wallet wallet : wallets) {
                if (wallet.getBalance() > 0d) {
                    return wallet;
                }
            }
        }
        return wallets.get(0);
    }

    private Wallet chooseDefaultDestinationWallet(@NonNull List<Wallet> wallets, @NonNull String sourceWalletId) {
        for (Wallet wallet : wallets) {
            if (!sourceWalletId.equals(wallet.getId())) {
                return wallet;
            }
        }
        return null;
    }

    private TransactionType mapModeToTransactionType(String mode) {
        if (AddTransactionActivity.MODE_INCOME.equalsIgnoreCase(mode)) {
            return TransactionType.INCOME;
        }
        if (AddTransactionActivity.MODE_TRANSFER.equalsIgnoreCase(mode)) {
            return TransactionType.TRANSFER;
        }
        return TransactionType.EXPENSE;
    }

    private String formatCurrency(double amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("vi", "VN"));
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        return format.format(Math.round(amount)) + " đ";
    }

    private List<String> buildVoiceApiCandidates() {
        String voiceRawUrl = safe(BuildConfig.VOICE_AI_API_URL).trim();
        if (!voiceRawUrl.isEmpty()) {
            return buildAsrCandidatesFromSource(voiceRawUrl);
        }
        String receiptRawUrl = safe(BuildConfig.RECEIPT_AI_API_URL).trim();
        if (receiptRawUrl.isEmpty()) {
            return new ArrayList<>();
        }
        return buildAsrCandidatesFromSource(receiptRawUrl);
    }

    private List<String> buildAsrCandidatesFromSource(String rawUrl) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = rawUrl.toLowerCase(Locale.ROOT).startsWith("http://")
            || rawUrl.toLowerCase(Locale.ROOT).startsWith("https://")
            ? rawUrl
            : "https://" + rawUrl;
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/asr")) {
            candidates.add(normalized);
        } else {
            candidates.add(appendAsrPath(normalized));
            if (lower.endsWith("/ocr")) {
                candidates.add(normalized.substring(0, normalized.length() - 4) + "/asr");
            } else if (lower.endsWith("/ocr/")) {
                candidates.add(normalized.substring(0, normalized.length() - 5) + "/asr");
            }
        }
        try {
            Uri parsed = Uri.parse(normalized);
            String scheme = safe(parsed.getScheme()).trim();
            String host = safe(parsed.getHost()).trim();
            if (!scheme.isEmpty() && !host.isEmpty()) {
                String root = scheme + "://" + host + (parsed.getPort() > 0 ? ":" + parsed.getPort() : "");
                candidates.add(root + "/asr");
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(candidates);
    }

    private String appendAsrPath(String url) {
        if (url.endsWith("/")) {
            return url + "asr";
        }
        return url + "/asr";
    }

    private void setProcessing(boolean processing, String loadingMessage) {
        isProcessing = processing;
        layoutLoading.setVisibility(processing ? android.view.View.VISIBLE : android.view.View.GONE);
        if (processing) {
            tvLoading.setText(loadingMessage);
        }
        updateActionUi();
    }

    private void showError(String message) {
        tvStatus.setText(message);
        tvStatus.setVisibility(android.view.View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        updateActionUi();
    }

    private void renderIdleState() {
        cardResult.setVisibility(android.view.View.GONE);
        layoutCenterIcon.setVisibility(android.view.View.VISIBLE);
        tvHintChip.setVisibility(android.view.View.VISIBLE);
        btnDelete.setVisibility(android.view.View.GONE);
        btnApply.setVisibility(android.view.View.GONE);
        layoutLoading.setVisibility(android.view.View.GONE);
        tvStatus.setVisibility(android.view.View.GONE);
        updateActionUi();
    }

    private void updateActionUi() {
        btnBack.setEnabled(!isProcessing && !isRecording);
        btnResultEdit.setEnabled(!isProcessing && latestPayload != null);
        btnResultClose.setEnabled(!isProcessing && latestPayload != null);
        btnDelete.setEnabled(!isProcessing && latestPayload != null);
        btnApply.setEnabled(!isProcessing && latestPayload != null);
        fabRecord.setEnabled(!isProcessing);

        if (isRecording) {
            fabRecord.setImageResource(R.drawable.ic_voice_stop);
            fabRecord.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.error_red));
            tvActionHint.setText(R.string.voice_entry_action_recording);
            return;
        }

        fabRecord.setImageResource(R.drawable.ic_voice_mic);
        fabRecord.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.white));
        if (latestPayload != null) {
            tvActionHint.setText(R.string.voice_entry_action_continue);
        } else {
            tvActionHint.setText(R.string.voice_entry_action_idle);
        }
    }

    private void deleteTempRecordingFile() {
        if (recordingFile == null) {
            return;
        }
        if (recordingFile.exists() && !recordingFile.delete()) {
            recordingFile.deleteOnExit();
        }
        recordingFile = null;
    }

    private void cleanupTempFiles() {
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String messageOrFallback(Throwable error, String fallback) {
        if (error == null) {
            return fallback;
        }
        String message = safe(error.getMessage()).trim();
        return message.isEmpty() ? fallback : message;
    }

    @Override
    protected void onDestroy() {
        isRecording = false;
        stopRecorderSafely();
        releaseRecorder();
        ioExecutor.shutdownNow();
        deleteTempRecordingFile();
        cleanupTempFiles();
        super.onDestroy();
    }

    private static final class CategoryPromptLists {
        private final List<String> incomeCategories;
        private final List<String> expenseCategories;

        private CategoryPromptLists(List<String> incomeCategories, List<String> expenseCategories) {
            this.incomeCategories = incomeCategories == null ? new ArrayList<>() : new ArrayList<>(incomeCategories);
            this.expenseCategories = expenseCategories == null ? new ArrayList<>() : new ArrayList<>(expenseCategories);
        }
    }

    private static final class SaveDecision {
        private final TransactionType type;
        private final String sourceWalletId;
        private final String destinationWalletId;
        private final String category;

        private SaveDecision(
            TransactionType type,
            String sourceWalletId,
            String destinationWalletId,
            String category
        ) {
            this.type = type;
            this.sourceWalletId = sourceWalletId;
            this.destinationWalletId = destinationWalletId;
            this.category = category;
        }
    }

    private static final class VoiceAiPayload {
        private final String mode;
        private final double amount;
        private final String categoryName;
        private final String note;
        private final long timeMillis;
        private final String paymentLabel;
        private final String sourceWalletId;
        private final String destinationWalletId;

        private VoiceAiPayload(
            String mode,
            double amount,
            String categoryName,
            String note,
            long timeMillis,
            String paymentLabel,
            String sourceWalletId,
            String destinationWalletId
        ) {
            this.mode = mode;
            this.amount = amount;
            this.categoryName = categoryName == null ? "" : categoryName.trim();
            this.note = note == null ? "" : note.trim();
            this.timeMillis = timeMillis <= 0L ? System.currentTimeMillis() : timeMillis;
            this.paymentLabel = paymentLabel == null ? "" : paymentLabel.trim();
            this.sourceWalletId = sourceWalletId == null ? "" : sourceWalletId.trim();
            this.destinationWalletId = destinationWalletId == null ? "" : destinationWalletId.trim();
        }
    }
}
