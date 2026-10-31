package com.quizzmusical.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fabrique de vues (UI construite en code, sans XML de layout). */
public final class Ui {
    public static final int BG = 0xFF061F4A;
    public static final int CARD = 0xB30A1E4D;
    public static final int CARD2 = 0xFF1C2F6E;
    public static final int PINK = 0xFFF683CE;
    public static final int GOLD = 0xFFFFB347;
    public static final int GREEN = 0xFF22C55E;
    public static final int RED = 0xFFEF4444;
    public static final int BLUE = 0xFF46A8F3;
    public static final int TXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFB8C4E6;
    public static final int ALERT_BG = 0xFF5A0F24;
    public static final int PURPLE = 0xFF9B6BE8;
    public static final int ORANGE = 0xFFF7931E;
    public static final int YELLOW = 0xFFFFC14D;
    public static final int CYAN = 0xFF8DBDF0;
    public static final int TEAL = 0xFF14B8A6;
    public static final int MAGENTA = 0xFFD45FD8;
    // Palette de l'icône : néon bleu / rose, casque orange, oreillettes bleues, micro argent
    public static final int NEON_BLUE = 0xFF46A8F3;
    public static final int NEON_PINK = 0xFFF683CE;
    public static final int CUP_BLUE = 0xFF2F6FD0;
    public static final int ORANGE_DARK = 0xFFE8650A;
    public static final int SILVER = 0xFFC9D2DE;
    public static final int INDIGO = 0xFF063B7D;
    public static final int PLUM = 0xFF4D1B60;
    public static final int[] RAINBOW = {NEON_BLUE, CYAN, NEON_PINK, ORANGE, PURPLE, YELLOW, CUP_BLUE, MAGENTA};

    private final Context ctx;

    public Ui(Context ctx) { this.ctx = ctx; }

    public int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics());
    }

    public TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public TextView center(String s, float sp, int color, boolean bold) {
        TextView t = text(s, sp, color, bold);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public GradientDrawable gradient(int c1, int c2, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Fond coloré associé à une couleur de bouton (dégradés). */
    private Drawable buttonBg(int bg) {
        if (bg == PINK) return gradient(ORANGE, ORANGE_DARK, 16);
        if (bg == GREEN) return gradient(GREEN, TEAL, 16);
        if (bg == RED) return gradient(NEON_PINK, 0xFFA8329A, 16);
        if (bg == CARD2) return gradient(NEON_BLUE, CUP_BLUE, 16);
        if (bg == BG) {
            GradientDrawable g = rounded(0x22FFFFFF, 16);
            g.setStroke(dp(1), 0x55FFFFFF);
            return g;
        }
        return rounded(bg, 16);
    }

    public TextView gbutton(String label, int c1, int c2) {
        TextView b = button(label, 0, TXT);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), gradient(c1, c2, 16), null));
        return b;
    }

    /** Texte aux lettres multicolores. */
    public CharSequence rainbow(String s) {
        SpannableString sp = new SpannableString(s);
        int k = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') continue;
            sp.setSpan(new ForegroundColorSpan(RAINBOW[k++ % RAINBOW.length]), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
    }

    public TextView button(String label, int bg, int fg) {
        TextView b = center(label, 18, fg, true);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), buttonBg(bg), null));
        b.setShadowLayer(4, 0, 1, 0x66000000);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public LinearLayout vbox() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public LinearLayout hbox() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public LinearLayout card() {
        LinearLayout l = vbox();
        GradientDrawable g = rounded(CARD, 18);
        g.setStroke(dp(1), 0x8046A8F3);
        l.setBackground(g);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        return l;
    }

    public LinearLayout.LayoutParams full(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    public LinearLayout.LayoutParams weight(float w, int sideDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        lp.leftMargin = dp(sideDp);
        lp.rightMargin = dp(sideDp);
        return lp;
    }

    public void add(LinearLayout parent, View v, int topDp) {
        parent.addView(v, full(topDp));
    }
}
