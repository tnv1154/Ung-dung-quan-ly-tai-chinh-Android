package com.example.myapplication.xmlui.currency;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import java.io.InputStream;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CurrencyLogoUtils {
    private static final String LOGO_FOLDER = "currency_logos";
    private static final LruCache<String, Bitmap> LOGO_CACHE = new LruCache<>(180);
    private static volatile Set<String> AVAILABLE_LOGO_CODES;

    private CurrencyLogoUtils() {
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }

    public static String displayNameForCode(String rawCode) {
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            return "";
        }
        try {
            Currency currency = Currency.getInstance(code);
            String name = currency.getDisplayName(new Locale("vi", "VN"));
            if (name != null && !name.trim().isEmpty()) {
                return name;
            }
        } catch (Exception ignored) {
        }
        return code;
    }

    public static String displaySymbolForCode(String rawCode) {
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            return "";
        }
        try {
            Currency currency = Currency.getInstance(code);
            String symbol = currency.getSymbol(new Locale("en", "US"));
            if (symbol != null && !symbol.trim().isEmpty()) {
                return symbol;
            }
        } catch (Exception ignored) {
        }
        return code;
    }

    public static Set<String> availableLogoCodes(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        if (appContext == null) {
            return Collections.emptySet();
        }
        Set<String> cached = AVAILABLE_LOGO_CODES;
        if (cached != null) {
            return cached;
        }
        synchronized (CurrencyLogoUtils.class) {
            if (AVAILABLE_LOGO_CODES != null) {
                return AVAILABLE_LOGO_CODES;
            }
            Set<String> codes = new HashSet<>();
            try {
                String[] fileNames = appContext.getAssets().list(LOGO_FOLDER);
                if (fileNames != null) {
                    for (String fileName : fileNames) {
                        if (fileName == null) {
                            continue;
                        }
                        String name = fileName.trim();
                        if (name.length() <= 4 || !name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                            continue;
                        }
                        String code = normalizeCode(name.substring(0, name.length() - 4));
                        if (!code.isEmpty()) {
                            codes.add(code);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            AVAILABLE_LOGO_CODES = Collections.unmodifiableSet(codes);
            return AVAILABLE_LOGO_CODES;
        }
    }

    public static boolean hasLocalLogo(Context context, String rawCode) {
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            return false;
        }
        return availableLogoCodes(context).contains(code);
    }

    public static boolean bindLogo(ImageView imageView, String rawCode, @DrawableRes int fallbackRes) {
        if (imageView == null) {
            return false;
        }
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            if (fallbackRes != 0) {
                imageView.setImageResource(fallbackRes);
            }
            return false;
        }

        Bitmap cached = LOGO_CACHE.get(code);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return true;
        }

        Context context = imageView.getContext();
        if (context == null) {
            if (fallbackRes != 0) {
                imageView.setImageResource(fallbackRes);
            }
            return false;
        }
        if (!hasLocalLogo(context, code)) {
            if (fallbackRes != 0) {
                imageView.setImageResource(fallbackRes);
            }
            return false;
        }
        String assetPath = LOGO_FOLDER + "/" + code + ".png";
        try (InputStream stream = context.getAssets().open(assetPath)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap != null) {
                LOGO_CACHE.put(code, bitmap);
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
}
