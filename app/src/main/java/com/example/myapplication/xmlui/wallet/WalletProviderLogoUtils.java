package com.example.myapplication.xmlui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WalletProviderLogoUtils {
    public static final String ACCOUNT_TYPE_BANK = "BANK";
    public static final String ACCOUNT_TYPE_EWALLET = "EWALLET";

    private static final String BANK_LOGO_FOLDER = "bank_logos";
    private static final String EWALLET_LOGO_FOLDER = "digital_wallet_logos";
    private static final LruCache<String, Bitmap> LOGO_CACHE = new LruCache<>(120);
    private static volatile Map<String, String> BANK_LOGO_MAP;
    private static volatile Map<String, String> EWALLET_LOGO_MAP;

    private WalletProviderLogoUtils() {
    }

    public static String normalizeShortName(String rawShortName) {
        if (rawShortName == null) {
            return "";
        }
        String normalized = rawShortName.trim().toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    public static List<String> localShortNames(Context context, String accountType) {
        Map<String, String> map = logoMap(context, accountType);
        List<String> result = new ArrayList<>(map.values());
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static String resolveShortName(Context context, String accountType, String rawShortName) {
        String key = normalizeShortName(rawShortName);
        if (key.isEmpty()) {
            return "";
        }
        String resolved = logoMap(context, accountType).get(key);
        return resolved == null ? "" : resolved;
    }

    public static boolean hasLocalLogo(Context context, String accountType, String rawShortName) {
        return !resolveShortName(context, accountType, rawShortName).isEmpty();
    }

    public static boolean bindLogo(
        ImageView imageView,
        String accountType,
        String rawShortName,
        @DrawableRes int fallbackRes
    ) {
        if (imageView == null) {
            return false;
        }
        Context context = imageView.getContext();
        if (context == null) {
            if (fallbackRes != 0) {
                imageView.setImageResource(fallbackRes);
            }
            return false;
        }
        String resolvedShortName = resolveShortName(context, accountType, rawShortName);
        if (resolvedShortName.isEmpty()) {
            if (fallbackRes != 0) {
                imageView.setImageResource(fallbackRes);
            }
            return false;
        }
        String normalizedType = normalizeAccountType(accountType);
        String cacheKey = normalizedType + "|" + resolvedShortName;
        Bitmap cached = LOGO_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return true;
        }
        String folder = folderForType(normalizedType);
        String assetPath = folder + "/" + resolvedShortName + ".png";
        try (InputStream stream = context.getAssets().open(assetPath)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap != null) {
                LOGO_CACHE.put(cacheKey, bitmap);
                imageView.setImageBitmap(bitmap);
                return true;
            }
        } catch (Exception ignored) {
        }
        if (fallbackRes != 0) {
            imageView.setImageResource(fallbackRes);
        }
        return false;
    }

    private static Map<String, String> logoMap(Context context, String accountType) {
        Context appContext = context == null ? null : context.getApplicationContext();
        if (appContext == null) {
            return Collections.emptyMap();
        }
        String normalizedType = normalizeAccountType(accountType);
        if (ACCOUNT_TYPE_EWALLET.equals(normalizedType)) {
            Map<String, String> cached = EWALLET_LOGO_MAP;
            if (cached != null) {
                return cached;
            }
            synchronized (WalletProviderLogoUtils.class) {
                if (EWALLET_LOGO_MAP == null) {
                    EWALLET_LOGO_MAP = loadLogoMap(appContext, EWALLET_LOGO_FOLDER);
                }
                return EWALLET_LOGO_MAP;
            }
        }
        Map<String, String> cached = BANK_LOGO_MAP;
        if (cached != null) {
            return cached;
        }
        synchronized (WalletProviderLogoUtils.class) {
            if (BANK_LOGO_MAP == null) {
                BANK_LOGO_MAP = loadLogoMap(appContext, BANK_LOGO_FOLDER);
            }
            return BANK_LOGO_MAP;
        }
    }

    private static Map<String, String> loadLogoMap(Context context, String folder) {
        Map<String, String> map = new HashMap<>();
        try {
            String[] fileNames = context.getAssets().list(folder);
            if (fileNames != null) {
                for (String fileName : fileNames) {
                    if (fileName == null) {
                        continue;
                    }
                    String name = fileName.trim();
                    if (name.length() <= 4 || !name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                        continue;
                    }
                    String shortName = name.substring(0, name.length() - 4).trim();
                    String normalized = normalizeShortName(shortName);
                    if (!normalized.isEmpty() && !shortName.isEmpty() && !map.containsKey(normalized)) {
                        map.put(normalized, shortName);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Collections.unmodifiableMap(map);
    }

    private static String normalizeAccountType(String accountType) {
        if (accountType == null) {
            return ACCOUNT_TYPE_BANK;
        }
        String normalized = accountType.trim().toUpperCase(Locale.ROOT);
        if (ACCOUNT_TYPE_EWALLET.equals(normalized)) {
            return ACCOUNT_TYPE_EWALLET;
        }
        return ACCOUNT_TYPE_BANK;
    }

    private static String folderForType(String normalizedType) {
        return ACCOUNT_TYPE_EWALLET.equals(normalizedType) ? EWALLET_LOGO_FOLDER : BANK_LOGO_FOLDER;
    }
}
