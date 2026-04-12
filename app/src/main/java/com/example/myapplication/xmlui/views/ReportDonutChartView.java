package com.example.myapplication.xmlui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

public class ReportDonutChartView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private final List<Float> values = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    @Nullable
    private OnSegmentClickListener onSegmentClickListener;

    public interface OnSegmentClickListener {
        void onSegmentClick(int index, float value);
    }

    public ReportDonutChartView(Context context) {
        super(context);
        init();
    }

    public ReportDonutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ReportDonutChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.BUTT);
        trackPaint.setColor(ContextCompat.getColor(getContext(), R.color.divider));

        segmentPaint.setStyle(Paint.Style.STROKE);
        segmentPaint.setStrokeCap(Paint.Cap.BUTT);

        values.clear();
        colors.clear();
        values.add(45f);
        values.add(25f);
        values.add(20f);
        values.add(10f);
        colors.add(ContextCompat.getColor(getContext(), R.color.group_bank_tint));
        colors.add(ContextCompat.getColor(getContext(), R.color.group_cash_tint));
        colors.add(ContextCompat.getColor(getContext(), R.color.warning_orange));
        colors.add(ContextCompat.getColor(getContext(), R.color.expense_red));
    }

    public void setSegments(List<Float> segmentValues, List<Integer> segmentColors) {
        values.clear();
        colors.clear();
        if (segmentValues != null) {
            for (Float value : segmentValues) {
                if (value == null || value <= 0f) {
                    continue;
                }
                values.add(value);
            }
        }
        if (segmentColors != null) {
            colors.addAll(segmentColors);
        }
        invalidate();
    }

    public void setOnSegmentClickListener(@Nullable OnSegmentClickListener listener) {
        this.onSegmentClickListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float diameter = Math.min(width, height);
        if (diameter <= 0f) {
            return;
        }

        float stroke = Math.max(8f, diameter * 0.18f);
        trackPaint.setStrokeWidth(stroke);
        segmentPaint.setStrokeWidth(stroke);

        float left = getPaddingLeft() + (width - diameter) / 2f + stroke / 2f;
        float top = getPaddingTop() + (height - diameter) / 2f + stroke / 2f;
        float right = left + diameter - stroke;
        float bottom = top + diameter - stroke;
        arcBounds.set(left, top, right, bottom);

        canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint);

        float total = 0f;
        for (Float value : values) {
            total += value;
        }
        if (total <= 0f) {
            return;
        }

        float startAngle = -90f;
        float gap = values.size() > 1 ? 2f : 0f;
        for (int i = 0; i < values.size(); i++) {
            float sweep = (values.get(i) / total) * 360f;
            if (sweep <= gap) {
                startAngle += sweep;
                continue;
            }
            int color = colors.isEmpty()
                ? ContextCompat.getColor(getContext(), R.color.blue_primary)
                : colors.get(i % colors.size());
            segmentPaint.setColor(color);
            canvas.drawArc(arcBounds, startAngle, sweep - gap, false, segmentPaint);
            startAngle += sweep;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (onSegmentClickListener == null || values.isEmpty()) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        ChartGeometry geometry = resolveGeometry();
        if (geometry == null) {
            return true;
        }
        float dx = event.getX() - geometry.centerX;
        float dy = event.getY() - geometry.centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance < geometry.innerRadius || distance > geometry.outerRadius) {
            return true;
        }

        float total = 0f;
        for (Float value : values) {
            total += value;
        }
        if (total <= 0f) {
            return true;
        }

        float angle = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 450f) % 360f);
        float start = 0f;
        float gap = values.size() > 1 ? 2f : 0f;
        for (int i = 0; i < values.size(); i++) {
            float sweep = (values.get(i) / total) * 360f;
            float visibleSweep = Math.max(0f, sweep - gap);
            if (visibleSweep > 0f && angle >= start && angle < start + visibleSweep) {
                onSegmentClickListener.onSegmentClick(i, values.get(i));
                performClick();
                return true;
            }
            start += sweep;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Nullable
    private ChartGeometry resolveGeometry() {
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float diameter = Math.min(width, height);
        if (diameter <= 0f) {
            return null;
        }
        float stroke = Math.max(8f, diameter * 0.18f);
        float outerRadius = diameter / 2f;
        float innerRadius = outerRadius - stroke;
        float centerX = getPaddingLeft() + width / 2f;
        float centerY = getPaddingTop() + height / 2f;
        return new ChartGeometry(centerX, centerY, innerRadius, outerRadius);
    }

    private static final class ChartGeometry {
        private final float centerX;
        private final float centerY;
        private final float innerRadius;
        private final float outerRadius;

        private ChartGeometry(float centerX, float centerY, float innerRadius, float outerRadius) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.innerRadius = innerRadius;
            this.outerRadius = outerRadius;
        }
    }
}
