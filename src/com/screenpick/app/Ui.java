package com.screenpick.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 纯代码建 View 的辅助：单位换算、圆角背景、常用文字样式。 */
final class Ui {

    static final int BRAND = 0xFF1F6FEB;
    static final int TEXT = 0xFF14181F;
    static final int TEXT_SUB = 0xFF6B7280;
    static final int SURFACE = 0xFFFFFFFF;
    static final int LINE = 0xFFE5E7EB;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static float sp(Context c, float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable bg(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable bgStroke(int color, float radiusDp, int strokeColor, float strokeDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, strokeDp), strokeColor);
        return d;
    }

    static TextView text(Context c, CharSequence s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setLineSpacing(dp(c, 5), 1f);
        return t;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static TextView badge(Context c, String label, int fg, int bg) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        t.setTextColor(fg);
        t.setPadding(dp(c, 7), dp(c, 2), dp(c, 7), dp(c, 3));
        t.setBackground(bg(bg, 6f, c));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        v.setLayoutParams(p);
        return v;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpm(Context c, int w, int h, int mtDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, mtDp);
        return p;
    }

    static void margins(View v, Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) v.getLayoutParams();
        if (p == null) return;
        p.leftMargin = dp(c, l);
        p.topMargin = dp(c, t);
        p.rightMargin = dp(c, r);
        p.bottomMargin = dp(c, b);
        v.setLayoutParams(p);
    }
}
