package com.example.myapplication.xmlui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class CategoryAssetIconLoader {
    private static final Pattern MARKS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Map<String, Bitmap> BITMAP_CACHE = new ConcurrentHashMap<>();

    private CategoryAssetIconLoader() {
    }

    public static boolean applyCategoryIcon(
        ImageView imageView,
        TransactionCategory category,
        @DrawableRes int fallbackRes
    ) {
        if (category == null) {
            if (imageView != null) {
                imageView.setImageResource(fallbackRes);
            }
            return false;
        }
        return applyCategoryIcon(imageView, category.getType(), category.getName(), fallbackRes);
    }

    public static boolean applyCategoryIcon(
        ImageView imageView,
        TransactionType type,
        String categoryName,
        @DrawableRes int fallbackRes
    ) {
        if (imageView == null) {
            return false;
        }
        String assetPath = buildAssetPath(type, categoryName);
        if (assetPath != null) {
            Bitmap bitmap = loadBitmap(imageView.getContext().getApplicationContext(), assetPath);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setImageTintList(null);
                return true;
            }
        }
        imageView.setImageResource(fallbackRes);
        return false;
    }

    public static String slugifyCategoryName(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return "";
        }
        String sanitized = categoryName
            .replace('đ', 'd')
            .replace('Đ', 'D');
        String normalized = Normalizer.normalize(sanitized, Normalizer.Form.NFD);
        normalized = MARKS_PATTERN.matcher(normalized).replaceAll("").toLowerCase(Locale.ROOT);
        String slug = NON_ALNUM_PATTERN.matcher(normalized).replaceAll("_");
        return trimUnderscores(slug);
    }

    private static String buildAssetPath(TransactionType type, String categoryName) {
        String folder = resolveFolder(type);
        String slug = slugifyCategoryName(categoryName);
        if (folder == null || slug.isEmpty()) {
            return null;
        }
        return "icon/" + folder + "/" + slug + ".png";
    }

    private static String resolveFolder(TransactionType type) {
        if (type == TransactionType.INCOME) {
            return "thu_tien";
        }
        if (type == TransactionType.EXPENSE) {
            return "chi_tien";
        }
        return null;
    }

    private static Bitmap loadBitmap(Context context, String assetPath) {
        Bitmap cached = BITMAP_CACHE.get(assetPath);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        try (InputStream input = context.getAssets().open(assetPath)) {
            Bitmap decoded = BitmapFactory.decodeStream(input);
            if (decoded != null) {
                BITMAP_CACHE.put(assetPath, decoded);
            }
            return decoded;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String trimUnderscores(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '_') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '_') {
            end--;
        }
        return value.substring(start, end);
    }
}

