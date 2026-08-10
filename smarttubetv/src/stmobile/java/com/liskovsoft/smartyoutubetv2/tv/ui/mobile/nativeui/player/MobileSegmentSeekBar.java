package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatSeekBar;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** SeekBar that can paint SponsorBlock/chapter ranges without changing playback semantics. */
public final class MobileSegmentSeekBar extends AppCompatSeekBar {
    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<SeekBarSegment> segments = Collections.emptyList();
    private final float segmentHeight;
    private final float cornerRadius;

    public MobileSegmentSeekBar(Context context) {
        this(context, null);
    }

    public MobileSegmentSeekBar(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.seekBarStyle);
    }

    public MobileSegmentSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        segmentHeight = Math.max(3f, 4f * density);
        cornerRadius = 1.5f * density;
    }

    public void setSegments(List<SeekBarSegment> value) {
        if (value == null || value.isEmpty()) {
            segments = Collections.emptyList();
        } else {
            List<SeekBarSegment> copy = new ArrayList<>(value.size());
            for (SeekBarSegment source : value) {
                if (source == null) continue;
                SeekBarSegment item = new SeekBarSegment();
                item.startProgress = clamp01(source.startProgress);
                item.endProgress = clamp01(source.endProgress);
                item.color = source.color;
                if (item.endProgress < item.startProgress) {
                    float swap = item.startProgress;
                    item.startProgress = item.endProgress;
                    item.endProgress = swap;
                }
                copy.add(item);
            }
            segments = copy.isEmpty() ? Collections.emptyList() : copy;
        }
        invalidate();
    }

    public void clearSegments() {
        setSegments(null);
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (segments.isEmpty() || getWidth() <= getPaddingLeft() + getPaddingRight()) return;

        float left = getPaddingLeft();
        float right = getWidth() - getPaddingRight();
        float width = right - left;
        float centerY = getHeight() / 2f;
        float top = centerY - segmentHeight / 2f;
        float bottom = centerY + segmentHeight / 2f;

        for (SeekBarSegment segment : segments) {
            float start = left + width * clamp01(segment.startProgress);
            float end = left + width * clamp01(segment.endProgress);
            if (end <= start) continue;
            segmentPaint.setColor(segment.color);
            segmentPaint.setAlpha(220);
            canvas.drawRoundRect(start, top, end, bottom,
                    cornerRadius, cornerRadius, segmentPaint);
        }

        // Segment overlays are intentionally painted after the progress drawable so their
        // category colors remain visible. Redraw the thumb on top to preserve touch feedback.
        Drawable thumb = getThumb();
        if (thumb != null) {
            int save = canvas.save();
            canvas.translate(getPaddingLeft() - getThumbOffset(), getPaddingTop());
            thumb.draw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
