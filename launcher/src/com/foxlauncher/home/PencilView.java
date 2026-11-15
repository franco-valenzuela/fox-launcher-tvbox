package com.foxlauncher.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Small pencil button next to the home row title: enters and leaves edit mode. */
final class PencilView extends View {

    private final MainActivity act;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path tip = new Path();

    PencilView(MainActivity act) {
        super(act);
        this.act = act;
        setFocusable(true);
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PencilView.this.act.onPencilClick();
            }
        });
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) {
            act.onPencilFocus();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float u = act.u;
        int w = getWidth();
        int h = getHeight();
        boolean focused = isFocused();
        float radius = 7 * u;

        rect.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(act.isEditing() ? 0x66FFFFFF : focused ? 0x38FFFFFF : 0x24FFFFFF);
        c.drawRoundRect(rect, radius, radius, paint);
        if (focused) {
            float stroke = 3 * u;
            rect.inset(stroke / 2, stroke / 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(0xFFFFFFFF);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // A pencil pointing down-left: body, then the tip.
        float cx = w / 2f;
        float cy = h / 2f;
        float half = 2.5f * u;
        float top = cy - 9 * u;
        float shoulder = cy + 4 * u;
        paint.setColor(0xFFFFFFFF);
        c.save();
        c.rotate(45, cx, cy);
        c.drawRect(cx - half, top, cx + half, shoulder, paint);
        tip.rewind();
        tip.moveTo(cx - half, shoulder + u);
        tip.lineTo(cx + half, shoulder + u);
        tip.lineTo(cx, shoulder + 6 * u);
        tip.close();
        c.drawPath(tip, paint);
        c.restore();
    }
}
