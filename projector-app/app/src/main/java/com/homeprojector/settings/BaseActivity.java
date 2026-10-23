package com.homeprojector.settings;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The look shared by the Projector screens: dark glass panels with rows, steppers and checkboxes. */
abstract class BaseActivity extends Activity {
    protected static final int WHITE = Color.WHITE, GREY = 0xFF9AA0A6;

    interface Step {
        void step(int direction);
    }

    protected Typeface font;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            font = getResources().getFont(R.font.outfit);
        } catch (RuntimeException e) {
            font = Typeface.SANS_SERIF;
        }
        // Outfit has no Cyrillic; Android falls back per glyph, but a single family reads better.
        if (L.ru()) font = Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    protected LinearLayout panel(String title) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(glass(26));
        p.setPadding(dp(18), dp(20), dp(18), dp(18));
        TextView t = text(30, WHITE);
        t.setText(title);
        t.setPadding(dp(14), 0, 0, dp(12));
        p.addView(t);
        return p;
    }

    protected TextView checkRow(LinearLayout parent, String label, View.OnClickListener click) {
        TextView box = (TextView) row(parent, label, "", click, null).getTag();
        box.setGravity(Gravity.CENTER);
        box.setTypeface(Typeface.DEFAULT_BOLD);
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(26), dp(26)));
        box.setTag(Boolean.TRUE);   // marks a checkbox: its colour does not follow focus
        return box;
    }

    protected View row(LinearLayout parent, String label, String value, View.OnClickListener click, Step step) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(10), dp(14), dp(10));
        r.setFocusable(true);
        r.setBackground(rowBackground());
        TextView l = text(18, WHITE);
        l.setText(label);
        l.setSingleLine(true);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = text(16, GREY);
        v.setText(value);
        r.addView(v);
        r.setTag(v);
        if (click != null) r.setOnClickListener(click);
        if (step != null) {
            r.setOnKeyListener((view, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    step.step(-1);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    step.step(1);
                    return true;
                }
                return false;
            });
        }
        r.setOnFocusChangeListener((view, has) -> {
            if (v.getTag() == null) v.setTextColor(has ? WHITE : GREY);
        });
        parent.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    protected void check(TextView box, boolean on) {
        box.setText(on ? "✓" : "");
        box.setTextColor(0xFF0B0D10);
        box.setTextSize(17);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(7));
        if (on) g.setColor(0xFF4FC3F7);
        else g.setStroke(dp(2), GREY);
        box.setBackground(g);
    }

    protected TextView text(float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(font);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    protected GradientDrawable glass(int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xE614171C);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(1, 0x33FFFFFF);
        return g;
    }

    protected StateListDrawable rowBackground() {
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x33FFFFFF);
        focused.setCornerRadius(dp(14));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    protected int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    protected static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    protected static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
