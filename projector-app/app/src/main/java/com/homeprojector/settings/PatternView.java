package com.homeprojector.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * GRID: the geometry grid shown behind the menu and while moving corners.
 * FOCUS: a focus chart drawn in panel pixels (1 px and 2 px line pairs, a Siemens star and text in
 * several sizes), so sharpness can be judged in the centre, at the sides and in every corner.
 */
final class PatternView extends View {
    static final int GRID = 0, FOCUS = 1;

    private static final String RU = "Съешь же ещё этих мягких французских булок, да выпей же чаю. 0123456789";
    private static final String UA = "Чуєш їх, доцю, га? Кумедна ж ти, прощайся без ґольфів! 0123456789";
    private static final String EN = "The quick brown fox jumps over the lazy dog. 0123456789";
    private static final String MIX = "Il1| O0 rn m  cl d  ВВ8 ЗЭ3 ШЩ  —  ½ ⅓ ¼  %  @  #";
    private static final int ACCENT = 0xFF4FC3F7;

    private final int mode;
    private final Paint fill = new Paint();
    private final Paint line = new Paint();
    private final Paint smooth = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF oval = new RectF();
    private int active = -1;
    private boolean inverted;

    PatternView(Context context, int mode) {
        super(context);
        this.mode = mode;
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(0);
    }

    /** 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left, -1 none. */
    void setActiveCorner(int corner) {
        active = corner;
        invalidate();
    }

    void toggleInverted() {
        inverted = !inverted;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (mode == GRID) grid(c);
        else focus(c);
    }

    private void grid(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.BLACK);
        float cell = w / 16f;
        int rows = Math.max(1, Math.round(h / cell));
        float rowH = h / (float) rows;
        line.setColor(0xFF262B33);
        for (int i = 1; i < 16; i++) c.drawLine(i * cell, 0, i * cell, h, line);
        for (int i = 1; i < rows; i++) c.drawLine(0, i * rowH, w, i * rowH, line);
        line.setColor(0xFF5F6670);
        c.drawLine(w / 2f, 0, w / 2f, h, line);
        c.drawLine(0, h / 2f, w, h / 2f, line);
        c.drawLine(0, 0, w, h, line);
        c.drawLine(w, 0, 0, h, line);
        smooth.setStyle(Paint.Style.STROKE);
        smooth.setStrokeWidth(2);
        smooth.setColor(0xFF5F6670);
        c.drawCircle(w / 2f, h / 2f, h * 0.42f, smooth);
        c.drawCircle(w / 2f, h / 2f, h * 0.12f, smooth);

        fill.setColor(Color.WHITE);
        c.drawRect(0, 0, w, 2, fill);
        c.drawRect(0, h - 2, w, h, fill);
        c.drawRect(0, 0, 2, h, fill);
        c.drawRect(w - 2, 0, w, h, fill);
        int len = Math.round(h * 0.08f), t = 6;
        c.drawRect(0, 0, len, t, fill);
        c.drawRect(0, 0, t, len, fill);
        c.drawRect(w - len, 0, w, t, fill);
        c.drawRect(w - t, 0, w, len, fill);
        c.drawRect(0, h - t, len, h, fill);
        c.drawRect(0, h - len, t, h, fill);
        c.drawRect(w - len, h - t, w, h, fill);
        c.drawRect(w - t, h - len, w, h, fill);

        if (active >= 0) {
            float cx = (active == 0 || active == 3) ? 48 : w - 48;
            float cy = active < 2 ? 48 : h - 48;
            smooth.setColor(ACCENT);
            smooth.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, 14, smooth);
            smooth.setStyle(Paint.Style.STROKE);
            smooth.setStrokeWidth(3);
            c.drawCircle(cx, cy, 26, smooth);
        }
    }

    private void focus(Canvas c) {
        int w = getWidth(), h = getHeight();
        int bg = inverted ? Color.BLACK : Color.WHITE, fg = inverted ? Color.WHITE : Color.BLACK;
        c.drawColor(bg);
        fill.setColor(fg);
        c.drawRect(0, 0, w, 1, fill);
        c.drawRect(0, h - 1, w, h, fill);
        c.drawRect(0, 0, 1, h, fill);
        c.drawRect(w - 1, 0, w, h, fill);

        // Siemens star in the centre: the blur circle in its middle shrinks as focus gets sharper.
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) * 0.2f;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        smooth.setColor(fg);
        smooth.setStyle(Paint.Style.FILL);
        int spokes = 36;
        float sweep = 360f / (spokes * 2);
        for (int i = 0; i < spokes; i++) c.drawArc(oval, i * 2 * sweep, sweep, true, smooth);
        smooth.setStyle(Paint.Style.STROKE);
        smooth.setStrokeWidth(1.5f);
        c.drawCircle(cx, cy, r + 6, smooth);
        c.drawCircle(cx, cy, r * 0.25f, smooth);

        // Line pairs in the four corners and at both sides: 1 px and 2 px, vertical and horizontal.
        int p = Math.round(h * 0.155f) / 4 * 4, m = 18;
        patch(c, m, m, p);
        patch(c, w - m - p, m, p);
        patch(c, m, h - m - p, p);
        patch(c, w - m - p, h - m - p, p);
        patch(c, m, (h - p) / 2, p);
        patch(c, w - m - p, (h - p) / 2, p);
        // Two more stripe bands left and right of the star.
        stripes(c, Math.round(cx - r - 26 - p / 2f), Math.round(cy - p / 2f), p / 2, 1, true);
        stripes(c, Math.round(cx + r + 26), Math.round(cy - p / 2f), p / 2, 1, false);

        // Calibration text: small to large at the top (Cyrillic), large to small at the bottom (Latin).
        text.setColor(fg);
        text.setTextAlign(Paint.Align.LEFT);
        float left = m + p + 26, right = w - m - p - 26, width = right - left;
        int[] sizes = {10, 12, 14, 17, 21, 26};
        float y = 22;
        for (int i = 0; i < sizes.length; i++) {
            text.setTextSize(sizes[i]);
            y += sizes[i] * 1.12f;
            c.drawText(fit(i % 2 == 0 ? RU : UA, width), left, y, text);
        }
        y = h - 16;
        for (int i = 0; i < sizes.length; i++) {
            text.setTextSize(sizes[i]);
            c.drawText(fit(i == 3 ? MIX : EN, width), left, y, text);
            y -= (i + 1 < sizes.length ? sizes[i + 1] : sizes[i]) * 1.12f;
        }

        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(13);
        c.drawText("1 px", m + p / 4f, m + p + 16, text);
        c.drawText("2 px", m + p * 3 / 4f, m + p + 16, text);
        text.setTextSize(14);
        c.drawText(L.t("OK — invert colours   ·   Back — exit", "OK — инвертировать   ·   Назад — выход"), cx, cy + r + 34, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    /** A square of four line-pair blocks: 1 px vertical, 1 px horizontal, 2 px vertical, 2 px horizontal. */
    private void patch(Canvas c, int x, int y, int size) {
        int half = size / 2;
        stripes(c, x, y, half, 1, true);
        stripes(c, x + half, y, half, 1, false);
        stripes(c, x, y + half, half, 2, true);
        stripes(c, x + half, y + half, half, 2, false);
    }

    private void stripes(Canvas c, int x, int y, int size, int width, boolean vertical) {
        for (int i = 0; i + width <= size; i += 2 * width) {
            if (vertical) c.drawRect(x + i, y, x + i + width, y + size, fill);
            else c.drawRect(x, y + i, x + size, y + i + width, fill);
        }
    }

    private String fit(String s, float maxWidth) {
        while (s.length() > 4 && text.measureText(s) > maxWidth) s = s.substring(0, s.length() - 1);
        return s;
    }
}
