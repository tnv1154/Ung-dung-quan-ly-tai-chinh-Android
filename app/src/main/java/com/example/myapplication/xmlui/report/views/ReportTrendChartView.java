package com.example.myapplication.xmlui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
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
import java.util.Locale;

public class ReportTrendChartView extends View {

    public static final class Entry {
        private final String label;
        private final double income;
        private final double expense;

        public Entry(@NonNull String label, double income, double expense) {
            this.label = label;
            this.income = Math.max(0.0, income);
            this.expense = Math.max(0.0, expense);
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
    }

    public interface OnEntryClickListener {
        void onEntryClick(int index, @NonNull Entry entry);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomeLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expenseLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noDataPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Entry> entries = new ArrayList<>();
    private final Path incomePath = new Path();
    private final Path incomeFillPath = new Path();
    private final Path expensePath = new Path();
    private final RectF bubbleRect = new RectF();
    @Nullable
    private OnEntryClickListener onEntryClickListener;

    public ReportTrendChartView(Context context) {
        super(context);
        init();
    }

    public ReportTrendChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReportTrendChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.divider));
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setStyle(Paint.Style.STROKE);

        incomeLinePaint.setColor(ContextCompat.getColor(getContext(), R.color.group_cash_tint));
        incomeLinePaint.setStyle(Paint.Style.STROKE);
        incomeLinePaint.setStrokeWidth(dp(3f));
        incomeLinePaint.setStrokeCap(Paint.Cap.ROUND);
        incomeLinePaint.setStrokeJoin(Paint.Join.ROUND);

        incomeFillPaint.setColor(0x3324B07B);
        incomeFillPaint.setStyle(Paint.Style.FILL);

        expenseLinePaint.setColor(ContextCompat.getColor(getContext(), R.color.expense_red));
        expenseLinePaint.setStyle(Paint.Style.STROKE);
        expenseLinePaint.setStrokeWidth(dp(3f));
        expenseLinePaint.setStrokeCap(Paint.Cap.ROUND);
        expenseLinePaint.setStrokeJoin(Paint.Join.ROUND);
        expenseLinePaint.setPathEffect(new DashPathEffect(new float[] {dp(10f), dp(8f)}, 0f));

        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        labelPaint.setTextSize(dp(11f));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        pointPaint.setColor(ContextCompat.getColor(getContext(), R.color.group_cash_tint));
        pointPaint.setStyle(Paint.Style.FILL);

        bubblePaint.setColor(0xFF111827);
        bubblePaint.setStyle(Paint.Style.FILL);

        bubbleTextPaint.setColor(0xFFFFFFFF);
        bubbleTextPaint.setTextSize(dp(12f));
        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);

        noDataPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
        noDataPaint.setTextSize(dp(12f));
        noDataPaint.setTextAlign(Paint.Align.CENTER);
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
        if (entries.size() < 2) {
            canvas.drawText(
                getResources().getString(R.string.label_no_category_data),
                getWidth() / 2f,
                getHeight() / 2f,
                noDataPaint
            );
            return;
        }

        float left = getPaddingLeft() + dp(6f);
        float top = getPaddingTop() + dp(6f);
        float right = getWidth() - getPaddingRight() - dp(6f);
        float bottom = getHeight() - getPaddingBottom() - dp(24f);
        if (right <= left || bottom <= top) {
            return;
        }

        float chartHeight = bottom - top;
        float chartWidth = right - left;
        float xStep = chartWidth / (entries.size() - 1);

        for (int i = 0; i <= 4; i++) {
            float y = top + chartHeight * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }

        double maxValue = 1.0;
        int peakIndex = 0;
        double peakIncome = 0.0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            maxValue = Math.max(maxValue, Math.max(entry.income, entry.expense));
            if (entry.income > peakIncome) {
                peakIncome = entry.income;
                peakIndex = i;
            }
        }

        incomePath.reset();
        incomeFillPath.reset();
        expensePath.reset();

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float x = left + xStep * i;
            float yIncome = (float) (bottom - (entry.income / maxValue) * chartHeight);
            float yExpense = (float) (bottom - (entry.expense / maxValue) * chartHeight);

            if (i == 0) {
                incomePath.moveTo(x, yIncome);
                expensePath.moveTo(x, yExpense);
            } else {
                incomePath.lineTo(x, yIncome);
                expensePath.lineTo(x, yExpense);
            }
        }

        incomeFillPath.addPath(incomePath);
        incomeFillPath.lineTo(right, bottom);
        incomeFillPath.lineTo(left, bottom);
        incomeFillPath.close();

        canvas.drawPath(incomeFillPath, incomeFillPaint);
        canvas.drawPath(incomePath, incomeLinePaint);
        canvas.drawPath(expensePath, expenseLinePaint);

        for (int i = 0; i < entries.size(); i++) {
            float x = left + xStep * i;
            float yIncome = (float) (bottom - (entries.get(i).income / maxValue) * chartHeight);
            if (i == peakIndex) {
                drawPeakBubble(canvas, x, yIncome, entries.get(i).income);
            }
        }

        int labelStep = entries.size() <= 5 ? 1 : 2;
        for (int i = 0; i < entries.size(); i++) {
            if (i % labelStep != 0 && i != entries.size() - 1) {
                continue;
            }
            float x = left + xStep * i;
            canvas.drawText(entries.get(i).label, x, bottom + dp(16f), labelPaint);
        }
    }

    private void drawPeakBubble(Canvas canvas, float x, float y, double value) {
        float bubbleWidth = dp(66f);
        float bubbleHeight = dp(32f);
        float bubbleLeft = x - bubbleWidth / 2f;
        float bubbleTop = Math.max(dp(4f), y - bubbleHeight - dp(14f));
        bubbleRect.set(bubbleLeft, bubbleTop, bubbleLeft + bubbleWidth, bubbleTop + bubbleHeight);
        canvas.drawRoundRect(bubbleRect, dp(10f), dp(10f), bubblePaint);

        Path arrow = new Path();
        arrow.moveTo(x - dp(5f), bubbleRect.bottom);
        arrow.lineTo(x + dp(5f), bubbleRect.bottom);
        arrow.lineTo(x, bubbleRect.bottom + dp(6f));
        arrow.close();
        canvas.drawPath(arrow, bubblePaint);

        canvas.drawText(formatCompact(value), x, bubbleRect.centerY() + dp(4f), bubbleTextPaint);
        canvas.drawCircle(x, y, dp(4.5f), pointPaint);
    }

    private String formatCompact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000d) {
            return String.format(Locale.US, "%.1fB", value / 1_000_000_000d);
        }
        if (abs >= 1_000_000d) {
            return String.format(Locale.US, "%.0fM", value / 1_000_000d);
        }
        if (abs >= 1_000d) {
            return String.format(Locale.US, "%.0fK", value / 1_000d);
        }
        return String.format(Locale.US, "%.0f", value);
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
        float left = getPaddingLeft() + dp(6f);
        float right = getWidth() - getPaddingRight() - dp(6f);
        if (entries.size() < 2 || right <= left) {
            return true;
        }
        float x = event.getX();
        if (x < left || x > right) {
            return true;
        }
        float xStep = (right - left) / (entries.size() - 1);
        int index = Math.round((x - left) / xStep);
        index = Math.min(entries.size() - 1, Math.max(0, index));
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
