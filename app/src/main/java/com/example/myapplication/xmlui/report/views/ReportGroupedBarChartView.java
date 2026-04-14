package com.example.myapplication.xmlui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

public class ReportGroupedBarChartView extends View {

    public static final class Entry {
        private final String label;
        private final double income;
        private final double expense;
        private final boolean highlighted;

        public Entry(@NonNull String label, double income, double expense, boolean highlighted) {
            this.label = label;
            this.income = Math.max(0.0, income);
            this.expense = Math.max(0.0, expense);
            this.highlighted = highlighted;
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        public double getIncome() {
            return income;
        }

        public double getExpense() {
            return expense;
        }

        public boolean isHighlighted() {
            return highlighted;
        }
    }

    public interface OnEntryClickListener {
        void onEntryClick(int index, @NonNull Entry entry);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private final List<Entry> entries = new ArrayList<>();
    @Nullable
    private OnEntryClickListener onEntryClickListener;

    public ReportGroupedBarChartView(Context context) {
        super(context);
        init();
    }

    public ReportGroupedBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReportGroupedBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.divider));

        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(11f));

        incomePaint.setStyle(Paint.Style.FILL);
        expensePaint.setStyle(Paint.Style.FILL);
    }

    public void setEntries(List<Entry> values) {
        entries.clear();
        if (values != null) {
            entries.addAll(values);
        }
        invalidate();
    }

    public void setOnEntryClickListener(@Nullable OnEntryClickListener listener) {
        this.onEntryClickListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (entries.isEmpty()) {
            drawEmpty(canvas);
            return;
        }

        float left = getPaddingLeft() + dp(8f);
        float top = getPaddingTop() + dp(8f);
        float right = getWidth() - getPaddingRight() - dp(8f);
        float bottom = getHeight() - getPaddingBottom() - dp(24f);
        if (right <= left || bottom <= top) {
            return;
        }

        float chartHeight = bottom - top;
        float chartWidth = right - left;

        for (int i = 0; i <= 4; i++) {
            float y = top + (chartHeight * i / 4f);
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        double maxValue = 1.0;
        for (Entry entry : entries) {
            maxValue = Math.max(maxValue, Math.max(entry.income, entry.expense));
        }

        float groupWidth = chartWidth / entries.size();
        float barWidth = Math.min(dp(12f), groupWidth * 0.24f);
        float barGap = barWidth * 0.45f;
        float radius = dp(3f);

        int incomeStrong = ContextCompat.getColor(getContext(), R.color.blue_primary);
        int incomeSoft = ContextCompat.getColor(getContext(), R.color.group_bank_bg);
        int expenseStrong = ContextCompat.getColor(getContext(), R.color.expense_red);
        int expenseSoft = 0xFFEFC5C5;

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float centerX = left + groupWidth * (i + 0.5f);

            float incomeHeight = (float) ((entry.income / maxValue) * chartHeight);
            float expenseHeight = (float) ((entry.expense / maxValue) * chartHeight);

            incomePaint.setColor(entry.highlighted ? incomeStrong : incomeSoft);
            expensePaint.setColor(entry.highlighted ? expenseStrong : expenseSoft);

            float incomeLeft = centerX - barGap - barWidth;
            float incomeRight = centerX - barGap;
            barRect.set(incomeLeft, bottom - incomeHeight, incomeRight, bottom);
            canvas.drawRoundRect(barRect, radius, radius, incomePaint);

            float expenseLeft = centerX + barGap;
            float expenseRight = centerX + barGap + barWidth;
            barRect.set(expenseLeft, bottom - expenseHeight, expenseRight, bottom);
            canvas.drawRoundRect(barRect, radius, radius, expensePaint);

            canvas.drawText(entry.label, centerX, bottom + dp(16f), labelPaint);
        }
    }

    private void drawEmpty(Canvas canvas) {
        Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextSize(dp(12f));
        canvas.drawText(
            getResources().getString(R.string.label_no_category_data),
            getWidth() / 2f,
            getHeight() / 2f,
            emptyPaint
        );
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (onEntryClickListener == null || entries.isEmpty()) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        float left = getPaddingLeft() + dp(8f);
        float right = getWidth() - getPaddingRight() - dp(8f);
        if (right <= left) {
            return true;
        }
        float x = event.getX();
        if (x < left || x > right) {
            return true;
        }
        float groupWidth = (right - left) / entries.size();
        int index = Math.min(entries.size() - 1, Math.max(0, (int) ((x - left) / groupWidth)));
        onEntryClickListener.onEntryClick(index, entries.get(index));
        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
