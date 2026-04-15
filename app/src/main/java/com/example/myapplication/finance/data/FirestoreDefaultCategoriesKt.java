package com.example.myapplication.finance.data;

import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.google.firebase.Timestamp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class FirestoreDefaultCategoriesKt {
    private static final Pattern MARKS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-z0-9]+");

    private FirestoreDefaultCategoriesKt() {
    }

    static String slugify(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD);
        normalized = MARKS_PATTERN.matcher(normalized).replaceAll("").toLowerCase(Locale.ROOT);
        String slug = NON_ALNUM_PATTERN.matcher(normalized).replaceAll("_");
        slug = trimUnderscore(slug);
        return slug.isBlank() ? "item" : slug;
    }

    static String categoryIdentity(TransactionType type, String parentName, String name) {
        return type.name() + "|" + slugify(parentName) + "|" + slugify(name);
    }

    static List<TransactionCategory> defaultTransactionCategories(Timestamp now) {
        List<TransactionCategory> items = new ArrayList<>();
        items.add(income("Được cho/tặng", "gift_in", 10, now));
        items.add(income("Lương", "salary", 20, now));
        items.add(income("Thưởng", "bonus", 30, now));
        items.add(income("Tiền vào", "money_in", 40, now));
        items.add(income("Thu nợ", "debt_in", 50, now));
        items.add(income("Khác", "other_in", 70, now));

        items.add(expenseParent("Ăn uống", "food", 100, now));
        items.add(expenseChild("Ăn sáng", "Ăn uống", "food", 101, now));
        items.add(expenseChild("Ăn trưa", "Ăn uống", "food", 102, now));
        items.add(expenseChild("Ăn tối", "Ăn uống", "food", 103, now));
        items.add(expenseChild("Đi chợ", "Ăn uống", "market", 104, now));
        items.add(expenseChild("Cà phê", "Ăn uống", "coffee", 105, now));
        items.add(expenseChild("Ăn hàng", "Ăn uống", "restaurant", 106, now));

        items.add(expenseParent("Con cái", "child", 200, now));
        items.add(expenseChild("Học phí", "Con cái", "school", 201, now));
        items.add(expenseChild("Sữa bỉm", "Con cái", "baby", 202, now));
        items.add(expenseChild("Đồ dùng học tập", "Con cái", "study", 203, now));
        items.add(expenseChild("Vui chơi", "Con cái", "play", 204, now));

        items.add(expenseParent("Dịch vụ sinh hoạt", "utility", 300, now));
        items.add(expenseChild("Điện", "Dịch vụ sinh hoạt", "electricity", 301, now));
        items.add(expenseChild("Nước", "Dịch vụ sinh hoạt", "water", 302, now));
        items.add(expenseChild("Internet", "Dịch vụ sinh hoạt", "internet", 303, now));
        items.add(expenseChild("Điện thoại", "Dịch vụ sinh hoạt", "phone", 304, now));

        items.add(expenseParent("Đi lại", "transport", 400, now));
        items.add(expenseChild("Xăng xe", "Đi lại", "fuel", 401, now));
        items.add(expenseChild("Gửi xe", "Đi lại", "parking", 402, now));
        items.add(expenseChild("Taxi/Grab", "Đi lại", "taxi", 403, now));
        items.add(expenseChild("Bảo dưỡng", "Đi lại", "repair", 404, now));

        items.add(expenseParent("Trang phục", "outfit", 500, now));
        items.add(expenseChild("Quần áo", "Trang phục", "clothes", 501, now));
        items.add(expenseChild("Giày dép", "Trang phục", "shoes", 502, now));
        items.add(expenseChild("Phụ kiện", "Trang phục", "accessory", 503, now));

        items.add(expenseParent("Hiếu hỉ", "ceremony", 600, now));
        items.add(expenseChild("Cưới hỏi", "Hiếu hỉ", "wedding", 601, now));
        items.add(expenseChild("Ma chay", "Hiếu hỉ", "funeral", 602, now));
        items.add(expenseChild("Biếu tặng", "Hiếu hỉ", "gift_out", 603, now));

        items.add(expenseParent("Sức khỏe", "health", 700, now));
        items.add(expenseChild("Khám bệnh", "Sức khỏe", "clinic", 701, now));
        items.add(expenseChild("Thuốc men", "Sức khỏe", "medicine", 702, now));
        items.add(expenseChild("Bảo hiểm", "Sức khỏe", "insurance", 703, now));

        items.add(expenseParent("Nhà cửa", "home", 800, now));
        items.add(expenseChild("Thuê nhà", "Nhà cửa", "rent", 801, now));
        items.add(expenseChild("Sửa chữa", "Nhà cửa", "repair", 802, now));
        items.add(expenseChild("Nội thất", "Nhà cửa", "furniture", 803, now));

        items.add(expenseParent("Hưởng thụ", "enjoy", 900, now));
        items.add(expenseChild("Du lịch", "Hưởng thụ", "travel", 901, now));
        items.add(expenseChild("Giải trí", "Hưởng thụ", "entertainment", 902, now));
        items.add(expenseChild("Mua sắm", "Hưởng thụ", "shopping", 903, now));

        items.add(expenseParent("Phát triển bản thân", "growth", 1000, now));
        items.add(expenseChild("Sách", "Phát triển bản thân", "book", 1001, now));
        items.add(expenseChild("Học tập", "Phát triển bản thân", "study", 1002, now));
        items.add(expenseChild("Khóa học", "Phát triển bản thân", "course", 1003, now));

        items.add(expenseParent("Tiền ra", "money_out", 1100, now));
        items.add(expenseChild("Trả nợ", "Tiền ra", "debt_out", 1101, now));
        items.add(expenseChild("Cho vay", "Tiền ra", "loan", 1102, now));
        return items;
    }

    private static TransactionCategory income(String name, String icon, int order, Timestamp now) {
        return new TransactionCategory("", name, TransactionType.INCOME, "", icon, order, now);
    }

    private static TransactionCategory expenseParent(String name, String icon, int order, Timestamp now) {
        return new TransactionCategory("", name, TransactionType.EXPENSE, "", icon, order, now);
    }

    private static TransactionCategory expenseChild(String name, String parent, String icon, int order, Timestamp now) {
        return new TransactionCategory("", name, TransactionType.EXPENSE, parent, icon, order, now);
    }

    private static String trimUnderscore(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '_') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '_') {
            end--;
        }
        return text.substring(start, end);
    }
}

