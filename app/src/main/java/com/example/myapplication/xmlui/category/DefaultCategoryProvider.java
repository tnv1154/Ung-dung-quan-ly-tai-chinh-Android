package com.example.myapplication.xmlui;

import com.example.myapplication.finance.model.TransactionCategory;
import com.example.myapplication.finance.model.TransactionType;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

public final class DefaultCategoryProvider {

    private DefaultCategoryProvider() {
    }

    public static List<TransactionCategory> createDefaultCategories() {
        Timestamp now = Timestamp.now();
        List<TransactionCategory> items = new ArrayList<>();

        items.add(income("fallback_income_gift", "Được cho/tặng", "gift_in", 10, now));
        items.add(income("fallback_income_salary", "Lương", "salary", 20, now));
        items.add(income("fallback_income_bonus", "Thưởng", "bonus", 30, now));
        items.add(income("fallback_income_money_in", "Tiền vào", "money_in", 40, now));
        items.add(income("fallback_income_other", "Khác", "other_in", 50, now));

        items.add(expenseParent("fallback_expense_food", "Ăn uống", "food", 100, now));
        items.add(expenseChild("fallback_expense_food_breakfast", "Ăn sáng", "Ăn uống", "food", 101, now));
        items.add(expenseChild("fallback_expense_food_lunch", "Ăn trưa", "Ăn uống", "food", 102, now));
        items.add(expenseChild("fallback_expense_food_dinner", "Ăn tối", "Ăn uống", "food", 103, now));

        items.add(expenseParent("fallback_expense_child", "Con cái", "child", 200, now));
        items.add(expenseChild("fallback_expense_child_fee", "Học phí", "Con cái", "school", 201, now));
        items.add(expenseChild("fallback_expense_child_supplies", "Đồ dùng học tập", "Con cái", "study", 202, now));

        items.add(expenseParent("fallback_expense_utility", "Dịch vụ sinh hoạt", "utility", 300, now));
        items.add(expenseChild("fallback_expense_utility_electricity", "Điện", "Dịch vụ sinh hoạt", "electricity", 301, now));
        items.add(expenseChild("fallback_expense_utility_water", "Nước", "Dịch vụ sinh hoạt", "water", 302, now));

        items.add(expenseParent("fallback_expense_transport", "Đi lại", "transport", 400, now));
        items.add(expenseChild("fallback_expense_transport_fuel", "Xăng xe", "Đi lại", "fuel", 401, now));
        items.add(expenseChild("fallback_expense_transport_taxi", "Taxi/Grab", "Đi lại", "taxi", 402, now));

        items.add(expenseParent("fallback_expense_clothes", "Trang phục", "outfit", 500, now));
        items.add(expenseChild("fallback_expense_clothes_items", "Quần áo", "Trang phục", "clothes", 501, now));

        items.add(expenseParent("fallback_expense_ceremony", "Hiếu hỉ", "ceremony", 600, now));
        items.add(expenseChild("fallback_expense_ceremony_gift", "Biếu tặng", "Hiếu hỉ", "gift_out", 601, now));

        items.add(expenseParent("fallback_expense_health", "Sức khỏe", "health", 700, now));
        items.add(expenseChild("fallback_expense_health_clinic", "Khám bệnh", "Sức khỏe", "clinic", 701, now));

        items.add(expenseParent("fallback_expense_home", "Nhà cửa", "home", 800, now));
        items.add(expenseChild("fallback_expense_home_rent", "Thuê nhà", "Nhà cửa", "rent", 801, now));

        items.add(expenseParent("fallback_expense_enjoy", "Hưởng thụ", "enjoy", 900, now));
        items.add(expenseChild("fallback_expense_enjoy_travel", "Du lịch", "Hưởng thụ", "travel", 901, now));

        items.add(expenseParent("fallback_expense_growth", "Phát triển bản thân", "growth", 1000, now));
        items.add(expenseChild("fallback_expense_growth_course", "Khóa học", "Phát triển bản thân", "course", 1001, now));

        items.add(expenseParent("fallback_expense_money_out", "Tiền ra", "money_out", 1100, now));
        return items;
    }

    private static TransactionCategory income(String id, String name, String iconKey, int order, Timestamp now) {
        return new TransactionCategory(
            id,
            name,
            TransactionType.INCOME,
            "",
            iconKey,
            order,
            now
        );
    }

    private static TransactionCategory expenseParent(String id, String name, String iconKey, int order, Timestamp now) {
        return new TransactionCategory(
            id,
            name,
            TransactionType.EXPENSE,
            "",
            iconKey,
            order,
            now
        );
    }

    private static TransactionCategory expenseChild(
        String id,
        String name,
        String parentName,
        String iconKey,
        int order,
        Timestamp now
    ) {
        return new TransactionCategory(
            id,
            name,
            TransactionType.EXPENSE,
            parentName,
            iconKey,
            order,
            now
        );
    }
}
