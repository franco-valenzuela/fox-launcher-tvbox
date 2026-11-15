package com.foxlauncher.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

/**
 * One tile, drawn by hand in a single View so a screen full of apps stays a flat, cheap view tree.
 *
 * Big tiles live in the home row. With three or more of them a TV banner fills the whole card;
 * with one or two the cards stretch and show the art on the left with the name beside it. Small
 * tiles are the all-apps grid. {@code app == null} marks the hidden-apps tile, which
 * only shows {@code text}.
 */
final class TileView extends View {

    /** Hold OK this long to open the app menu. */
    private static final int LONG_PRESS_MS = 500;
    private static final int AMBER = 0xFFFFC107;
    private static final int INK_DARK = 0xFF1A1D20;
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    /** Not final: moving a tile in edit mode swaps the apps of two neighbouring views. */
    App app;
    final boolean big;
    final String text;
    /** Set between the OK press and the app taking the screen. */
    boolean opening;

    private final MainActivity act;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF bounds = new RectF();
    private final Path clip = new Path();
    private DashPathEffect dash;
    private boolean okDown;
    private boolean longFired;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            longFired = true;
            act.onTileMenu(TileView.this);
        }
    };

    TileView(MainActivity act, App app, boolean big, String text) {
        super(act);
        this.act = act;
        this.app = app;
        this.big = big;
        this.text = text;
        setFocusable(true);
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                TileView.this.act.onTileClick(TileView.this);
            }
        });
        // Pointer devices (air mouse, touch): the platform long-click opens the same menu.
        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                TileView.this.act.onTileMenu(TileView.this);
                return true;
            }
        });
    }

    static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Edit mode and "pick which one to replace" take the keys first.
        if (act.onTileKey(this, keyCode, event)) {
            removeCallbacks(longPress);
            okDown = false;
            return true;
        }
        if (isOk(keyCode)) {
            if (event.getRepeatCount() == 0) {
                okDown = true;
                longFired = false;
                postDelayed(longPress, LONG_PRESS_MS);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            act.onTileMenu(this);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isOk(keyCode)) {
            removeCallbacks(longPress);
            // Only a press that started on this tile counts, and only if the menu did not open.
            if (okDown && !longFired) {
                performClick();
            }
            okDown = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) {
            act.onTileFocus(this);
        } else {
            removeCallbacks(longPress);
            okDown = false;
            act.onTileBlur(this);
        }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(longPress);
        super.onDetachedFromWindow();
    }

    // --------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        float u = act.u;
        int w = getWidth();
        int h = getHeight();
        boolean focused = isFocused();
        float radius = (big ? 12 : 8) * u;
        boolean banner = big && app != null && app.big != null && app.bigIsBanner;
        // A banner with an opaque background gets a card in that same colour, so it reads as
        // one full tile instead of a picture pasted on a grey one.
        boolean card = banner && app.cardColor != 0;
        boolean lightCard = card && luminance(app.cardColor) > 150;

        bounds.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(card ? app.cardColor
                : opening ? 0x59FFFFFF : focused ? 0x38FFFFFF : 0x18FFFFFF);
        c.drawRoundRect(bounds, radius, radius, paint);

        textPaint.setColor(lightCard ? INK_DARK : 0xFFFFFFFF);
        textPaint.setTypeface(Typeface.DEFAULT);
        paint.setColor(0xFFFFFFFF);
        String label = app == null ? text : opening ? S.t("Abriendo…", "Opening…") : app.label;

        if (app == null) {
            textPaint.setTextSize(19 * u);
            drawLabel(c, label, 14 * u, w - 28 * u, centerBaseline(h), false);
        } else if (!big) {
            drawSmall(c, u, w, h, label);
        } else if (act.wideTiles()) {
            drawWide(c, u, w, h, label);
        } else if (banner) {
            drawBanner(c, u, w, h, radius);
        } else {
            drawIcon(c, u, w, h, label);
        }

        if (card && !focused && !opening) {
            // Keep a bright banner from outshining the tile that has focus.
            paint.setColor(0x40000000);
            c.drawRoundRect(bounds, radius, radius, paint);
        }
        drawBorder(c, u, radius, focused, lightCard);
    }

    /** Three or more tiles: the banner fills the card. Its own artwork carries the name. */
    private void drawBanner(Canvas c, float u, int w, int h, float radius) {
        if (opening) {
            textPaint.setTextSize(24 * u);
            drawLabel(c, S.t("Abriendo…", "Opening…"), 0, w, centerBaseline(h), true);
            return;
        }
        Bitmap art = app.big;
        float scale = Math.min((float) w / art.getWidth(), (float) h / art.getHeight());
        float dw = art.getWidth() * scale;
        float dh = art.getHeight() * scale;
        rect.set((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
        clip.rewind();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.drawBitmap(art, null, rect, paint);
        c.restore();
    }

    /** Three or more tiles, no banner: icon above the name. */
    private void drawIcon(Canvas c, float u, int w, int h, String label) {
        float labelSize = 22 * u;
        float pad = 14 * u;
        float artBottom = h - pad - labelSize - 10 * u;
        Bitmap art = app.big;
        if (art != null) {
            float maxW = w - 2 * pad;
            float maxH = artBottom - pad;
            // Never blown up past its own size: a stretched icon is a blurred icon.
            float scale = Math.min(1f, Math.min(maxW / art.getWidth(), maxH / art.getHeight()));
            float dw = art.getWidth() * scale;
            float dh = art.getHeight() * scale;
            float left = (w - dw) / 2;
            float top = pad + (maxH - dh) / 2;
            rect.set(left, top, left + dw, top + dh);
            c.drawBitmap(art, null, rect, paint);
        }
        textPaint.setTextSize(labelSize);
        drawLabel(c, label, pad, w - 2 * pad, h - pad - textPaint.descent(), true);
    }

    /** One or two tiles: the card stretches; art on the left, name large beside it. */
    private void drawWide(Canvas c, float u, int w, int h, String label) {
        float pad = 20 * u;
        float x = pad;
        Bitmap art = app.big;
        if (art != null) {
            float scale = (h - 2 * pad) / art.getHeight();
            if (!app.bigIsBanner) {
                scale = Math.min(scale, 1f);
            }
            float dw = art.getWidth() * scale;
            float dh = art.getHeight() * scale;
            float top = (h - dh) / 2;
            rect.set(x, top, x + dw, top + dh);
            c.drawBitmap(art, null, rect, paint);
            x += dw;
        }
        x += 26 * u;
        textPaint.setTextSize(40 * u);
        textPaint.setTypeface(MEDIUM);
        drawLabel(c, label, x, w - x - pad, centerBaseline(h), false);
    }

    private void drawSmall(Canvas c, float u, int w, int h, String label) {
        float iconSize = 34 * u;
        float pad = 10 * u;
        if (app.icon != null) {
            float top = (h - iconSize) / 2;
            rect.set(pad, top, pad + iconSize, top + iconSize);
            c.drawBitmap(app.icon, null, rect, paint);
        }
        textPaint.setTextSize(19 * u);
        float x = pad + iconSize + 9 * u;
        drawLabel(c, label, x, w - x - pad, centerBaseline(h), false);
    }

    private void drawBorder(Canvas c, float u, float radius, boolean focused, boolean lightCard) {
        boolean held = big && app != null && app.pkg.equals(act.grabbedPkg());
        boolean editable = big && app != null && (act.isEditing() || act.isReplacing());
        if (held) {
            stroke(c, 5 * u, AMBER, radius, 0, false);
        } else if (focused) {
            stroke(c, 4 * u, 0xFFFFFFFF, radius, 0, false);
            if (lightCard) {
                // A white ring on a white card is invisible; a dark line inside it marks focus.
                stroke(c, 2 * u, (INK_DARK & 0x00FFFFFF) | 0xB3000000, radius, 4 * u, false);
            }
        } else if (editable) {
            stroke(c, 2 * u, lightCard ? 0x99000000 : 0x99FFFFFF, radius, 0, true);
        }
    }

    private void stroke(Canvas c, float width, int color, float radius, float inset, boolean dashed) {
        rect.set(bounds);
        rect.inset(inset + width / 2, inset + width / 2);
        if (dashed && dash == null) {
            dash = new DashPathEffect(new float[]{9 * act.u, 7 * act.u}, 0);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        paint.setPathEffect(dashed ? dash : null);
        float r = Math.max(0, radius - inset);
        c.drawRoundRect(rect, r, r, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
    }

    private float centerBaseline(int h) {
        return h / 2f - (textPaint.ascent() + textPaint.descent()) / 2;
    }

    private void drawLabel(Canvas c, String s, float x, float maxWidth, float baseline, boolean centered) {
        if (s == null || maxWidth <= 0) {
            return;
        }
        CharSequence shown = TextUtils.ellipsize(s, textPaint, maxWidth, TextUtils.TruncateAt.END);
        float tx = centered ? x + (maxWidth - textPaint.measureText(shown, 0, shown.length())) / 2 : x;
        c.drawText(shown, 0, shown.length(), tx, baseline, textPaint);
    }

    private static int luminance(int color) {
        return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
    }
}
