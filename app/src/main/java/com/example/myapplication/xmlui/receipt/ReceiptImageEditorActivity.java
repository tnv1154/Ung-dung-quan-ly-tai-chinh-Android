package com.example.myapplication.xmlui.receipt;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.example.myapplication.BuildConfig;
import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.io.File;

public class ReceiptImageEditorActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE_URI = "extra_source_uri";
    public static final String EXTRA_CROPPED_URI = "extra_cropped_uri";
    private static final String TEMP_FOLDER = "receipt_images";

    private enum EditorMode {
        EDIT,
        CROP,
        ROTATE
    }

    private MaterialToolbar toolbar;
    private MenuItem menuDone;
    private ImageView ivPreview;
    private CropImageView cropImageView;
    private View layoutMainActions;
    private View layoutCropActions;
    private View layoutRotateActions;
    private MaterialButton btnModeCrop;
    private MaterialButton btnModeRotate;
    private MaterialButton btnRotateLeft;
    private MaterialButton btnFlip;
    private MaterialButton btnRotateRight;
    private MaterialButtonToggleGroup groupCropRatios;
    private boolean processing;
    private EditorMode currentMode = EditorMode.EDIT;
    private Uri sourceUri;
    private Bitmap previewBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_image_editor);
        bindViews();
        setupToolbar();
        setupCropView();
        setupActions();
        setupBackHandler();
        setMode(EditorMode.EDIT);
        loadSourceImage();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarReceiptEditor);
        ivPreview = findViewById(R.id.ivReceiptEditorPreview);
        cropImageView = findViewById(R.id.cropImageViewReceipt);
        layoutMainActions = findViewById(R.id.layoutReceiptEditorMainActions);
        layoutCropActions = findViewById(R.id.layoutReceiptEditorCropActions);
        layoutRotateActions = findViewById(R.id.layoutReceiptEditorRotateActions);
        btnModeCrop = findViewById(R.id.btnReceiptEditorModeCrop);
        btnModeRotate = findViewById(R.id.btnReceiptEditorModeRotate);
        btnRotateLeft = findViewById(R.id.btnReceiptRotateLeft);
        btnFlip = findViewById(R.id.btnReceiptFlipHorizontal);
        btnRotateRight = findViewById(R.id.btnReceiptRotateRight);
        groupCropRatios = findViewById(R.id.groupReceiptCropRatios);
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> handleBackAction());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.actionReceiptEditorDone) {
                if (currentMode == EditorMode.EDIT) {
                    continueCrop();
                } else {
                    setMode(EditorMode.EDIT);
                }
                return true;
            }
            return false;
        });
        menuDone = toolbar.getMenu().findItem(R.id.actionReceiptEditorDone);
    }

    private void setupCropView() {
        CropImageOptions options = new CropImageOptions();
        options.guidelines = CropImageView.Guidelines.OFF;
        options.allowRotation = true;
        options.allowCounterRotation = true;
        options.rotationDegrees = 90;
        options.allowFlipping = true;
        options.fixAspectRatio = false;
        options.showCropOverlay = false;
        options.showProgressBar = true;
        cropImageView.setImageCropOptions(options);

        cropImageView.setOnSetImageUriCompleteListener((view, uri, error) -> {
            if (error != null) {
                Toast.makeText(this, R.string.receipt_error_pick_image, Toast.LENGTH_SHORT).show();
                finishWithCancel();
                return;
            }
            setProcessing(false);
            setMode(EditorMode.EDIT);
        });
        cropImageView.setOnCropImageCompleteListener((view, result) -> {
            setProcessing(false);
            if (result == null || !result.isSuccessful()) {
                Toast.makeText(this, R.string.receipt_error_crop, Toast.LENGTH_SHORT).show();
                return;
            }
            Uri croppedUri = result.getUriContent();
            if (croppedUri == null) {
                Toast.makeText(this, R.string.receipt_error_crop, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent data = new Intent();
            data.putExtra(EXTRA_CROPPED_URI, croppedUri.toString());
            setResult(RESULT_OK, data);
            finish();
        });
    }

    private void setupActions() {
        btnModeCrop.setOnClickListener(v -> {
            if (processing) {
                return;
            }
            setMode(EditorMode.CROP);
        });
        btnModeRotate.setOnClickListener(v -> {
            if (processing) {
                return;
            }
            setMode(EditorMode.ROTATE);
        });
        btnRotateLeft.setOnClickListener(v -> {
            if (!processing) {
                cropImageView.rotateImage(-90);
            }
        });
        btnFlip.setOnClickListener(v -> {
            if (!processing) {
                cropImageView.flipImageHorizontally();
            }
        });
        btnRotateRight.setOnClickListener(v -> {
            if (!processing) {
                cropImageView.rotateImage(90);
            }
        });
        groupCropRatios.check(R.id.btnReceiptRatioFree);
        groupCropRatios.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || checkedId == View.NO_ID || processing) {
                return;
            }
            applyCropRatio(checkedId);
        });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackAction();
            }
        });
    }

    private void loadSourceImage() {
        Intent intent = getIntent();
        String rawUri = intent == null ? null : intent.getStringExtra(EXTRA_SOURCE_URI);
        if (rawUri == null || rawUri.trim().isEmpty()) {
            Toast.makeText(this, R.string.receipt_error_pick_image, Toast.LENGTH_SHORT).show();
            finishWithCancel();
            return;
        }
        sourceUri = Uri.parse(rawUri);
        ivPreview.setImageURI(sourceUri);
        setProcessing(true);
        cropImageView.setImageUriAsync(sourceUri);
    }

    private void setMode(EditorMode mode) {
        currentMode = mode;
        boolean editMode = mode == EditorMode.EDIT;
        boolean cropMode = mode == EditorMode.CROP;
        boolean rotateMode = mode == EditorMode.ROTATE;
        updateToolbarAction(mode);

        if (editMode) {
            cropImageView.setShowCropOverlay(false);
            cropImageView.setGuidelines(CropImageView.Guidelines.OFF);
            renderPreviewImage();
        }

        ivPreview.setVisibility(editMode ? View.VISIBLE : View.GONE);
        cropImageView.setVisibility(editMode ? View.GONE : View.VISIBLE);
        layoutMainActions.setVisibility(editMode ? View.VISIBLE : View.GONE);
        layoutCropActions.setVisibility(cropMode ? View.VISIBLE : View.GONE);
        layoutRotateActions.setVisibility(rotateMode ? View.VISIBLE : View.GONE);

        if (editMode) {
            return;
        }

        if (cropMode) {
            cropImageView.setShowCropOverlay(true);
            cropImageView.setGuidelines(CropImageView.Guidelines.ON);
            applyCropRatio(groupCropRatios.getCheckedButtonId());
            return;
        }

        cropImageView.setFixedAspectRatio(false);
        cropImageView.setShowCropOverlay(true);
        cropImageView.setGuidelines(CropImageView.Guidelines.ON);
    }

    private void updateToolbarAction(EditorMode mode) {
        if (menuDone == null) {
            return;
        }
        if (mode == EditorMode.EDIT) {
            menuDone.setIcon(null);
            menuDone.setTitle(R.string.receipt_editor_done);
        } else {
            menuDone.setTitle("");
            menuDone.setIcon(R.drawable.ic_action_check);
        }
    }

    private void applyCropRatio(int checkedButtonId) {
        if (checkedButtonId == R.id.btnReceiptRatioSquare) {
            cropImageView.setFixedAspectRatio(true);
            cropImageView.setAspectRatio(1, 1);
            return;
        }
        if (checkedButtonId == R.id.btnReceiptRatio4x3) {
            cropImageView.setFixedAspectRatio(true);
            cropImageView.setAspectRatio(4, 3);
            return;
        }
        if (checkedButtonId == R.id.btnReceiptRatio16x9) {
            cropImageView.setFixedAspectRatio(true);
            cropImageView.setAspectRatio(16, 9);
            return;
        }
        if (checkedButtonId == R.id.btnReceiptRatio9x16) {
            cropImageView.setFixedAspectRatio(true);
            cropImageView.setAspectRatio(9, 16);
            return;
        }
        cropImageView.setFixedAspectRatio(false);
    }

    private void renderPreviewImage() {
        Bitmap rendered = null;
        try {
            rendered = cropImageView.getCroppedImage();
        } catch (Exception ignored) {
            rendered = null;
        }
        if (rendered == null) {
            if (sourceUri != null) {
                ivPreview.setImageURI(sourceUri);
            }
            return;
        }
        recyclePreviewBitmap();
        previewBitmap = rendered;
        ivPreview.setImageBitmap(previewBitmap);
    }

    private void continueCrop() {
        if (processing) {
            return;
        }
        try {
            Uri outputUri = createTempImageUri("editor_crop_");
            setProcessing(true);
            cropImageView.croppedImageAsync(
                Bitmap.CompressFormat.JPEG,
                90,
                0,
                0,
                CropImageView.RequestSizeOptions.NONE,
                outputUri
            );
        } catch (Exception error) {
            setProcessing(false);
            Toast.makeText(this, R.string.receipt_error_crop, Toast.LENGTH_SHORT).show();
        }
    }

    private Uri createTempImageUri(String prefix) throws Exception {
        File folder = new File(getCacheDir(), TEMP_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create temp image folder");
        }
        File temp = File.createTempFile(prefix, ".jpg", folder);
        return FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", temp);
    }

    private void setProcessing(boolean processing) {
        this.processing = processing;
        if (menuDone != null) {
            menuDone.setEnabled(!processing);
        }
        btnModeCrop.setEnabled(!processing);
        btnModeRotate.setEnabled(!processing);
        btnRotateLeft.setEnabled(!processing);
        btnFlip.setEnabled(!processing);
        btnRotateRight.setEnabled(!processing);
        for (int i = 0; i < groupCropRatios.getChildCount(); i++) {
            groupCropRatios.getChildAt(i).setEnabled(!processing);
        }
    }

    private void handleBackAction() {
        if (processing) {
            return;
        }
        if (currentMode != EditorMode.EDIT) {
            setMode(EditorMode.EDIT);
            return;
        }
        finishWithCancel();
    }

    private void finishWithCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private void recyclePreviewBitmap() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) {
            previewBitmap.recycle();
        }
        previewBitmap = null;
    }

    @Override
    protected void onDestroy() {
        recyclePreviewBitmap();
        super.onDestroy();
    }
}
