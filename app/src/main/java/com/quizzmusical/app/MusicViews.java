package com.quizzmusical.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** Visuels animés : fond musical, vinyle qui tourne, égaliseur. */
public final class MusicViews {
    private MusicViews() {}

    /** Fond dégradé avec halos colorés et notes / instruments qui flottent. */
    public static class Backdrop extends View {
        private static final String[] GLYPHS = {"♪", "♫", "♬", "♩", "★", "♪", "♫", "★", "♬", "☆", "🎧", "🎤"};
        private static final int N = 18;
        private final Paint bg = new Paint();
        private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private final float[] x = new float[N], y = new float[N], speed = new float[N], size = new float[N], phase = new float[N];
        private final String[] glyph = new String[N];
        private final int[] alpha = new int[N];
        private final int[] tint = new int[N];
        private Shader[] blobs = new Shader[0];
        private boolean alert;
        private long last;
        private float density = 1f;

        public Backdrop(Context c) {
            super(c);
            density = c.getResources().getDisplayMetrics().density;
            note.setTextAlign(Paint.Align.CENTER);
        }

        public void setAlert(boolean a) {
            if (a == alert) return;
            alert = a;
            buildShaders(getWidth(), getHeight());
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            buildShaders(w, h);
            for (int i = 0; i < N; i++) spawn(i, w, h, true);
        }

        private void buildShaders(int w, int h) {
            if (w <= 0 || h <= 0) return;
            int[] g = alert ? new int[]{0xFF3D0A12, 0xFF7A0F24, 0xFFB3122E} : new int[]{0xFF052A63, 0xFF1F2A78, 0xFF4D1B60};
            bg.setShader(new LinearGradient(0, 0, w, h, g, null, Shader.TileMode.CLAMP));
            blobs = new Shader[]{
                    new RadialGradient(w * 0.12f, h * 0.18f, w * 0.75f, new int[]{(alert ? 0x66FF2E2E : 0x5546A8F3), 0}, null, Shader.TileMode.CLAMP),
                    new RadialGradient(w * 0.95f, h * 0.55f, w * 0.7f, new int[]{(alert ? 0x55FF8A00 : 0x55F683CE), 0}, null, Shader.TileMode.CLAMP),
                    new RadialGradient(w * 0.25f, h * 0.98f, w * 0.8f, new int[]{(alert ? 0x55FF3D7F : 0x33F7931E), 0}, null, Shader.TileMode.CLAMP)
            };
        }

        private void spawn(int i, int w, int h, boolean anywhere) {
            x[i] = rnd.nextFloat() * w;
            y[i] = anywhere ? rnd.nextFloat() * h : h + 40 * density;
            speed[i] = (12 + rnd.nextFloat() * 28) * density; // px / s
            size[i] = (14 + rnd.nextFloat() * 22) * density;
            phase[i] = rnd.nextFloat() * 6.28f;
            glyph[i] = GLYPHS[rnd.nextInt(GLYPHS.length)];
            alpha[i] = 40 + rnd.nextInt(60);
            int[] pal = {Ui.NEON_BLUE, Ui.CYAN, Ui.NEON_PINK, 0xFFF08AD8, Ui.ORANGE, 0xFFFFFFFF};
            tint[i] = pal[rnd.nextInt(pal.length)];
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            c.drawRect(0, 0, w, h, bg);
            for (Shader s : blobs) {
                blob.setShader(s);
                c.drawRect(0, 0, w, h, blob);
            }
            float t = now / 1000f;
            for (int i = 0; i < N; i++) {
                y[i] -= speed[i] * dt;
                if (y[i] < -size[i]) spawn(i, w, h, false);
                float sx = x[i] + (float) Math.sin(t * 0.8f + phase[i]) * 14 * density;
                note.setTextSize(size[i]);
                note.setColor(tint[i]);
                note.setAlpha(alpha[i]);
                c.drawText(glyph[i], sx, y[i], note);
            }
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            last = 0;
        }
    }

    /** Disque vinyle qui tourne. */
    public static class Vinyl extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float angle;
        private long last;

        public Vinyl(Context c) {
            super(c);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setColor(0xFFFFFFFF);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 2;
            long now = SystemClock.uptimeMillis();
            if (last != 0) angle = (angle + (now - last) * 0.09f) % 360;
            last = now;
            c.save();
            c.rotate(angle, cx, cy);
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(0xFF111111);
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, r / 90));
            for (float k = 0.92f; k > 0.42f; k -= 0.06f) {
                p.setColor(0x22FFFFFF);
                c.drawCircle(cx, cy, r * k, p);
            }
            // Reflets colorés
            p.setStrokeWidth(r * 0.05f);
            p.setColor(0x33FF3D7F);
            c.drawArc(new RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f), 200, 50, false, p);
            p.setColor(0x3300D1FF);
            c.drawArc(new RectF(cx - r * 0.65f, cy - r * 0.65f, cx + r * 0.65f, cy + r * 0.65f), 20, 50, false, p);
            // Étiquette centrale
            label.setShader(new LinearGradient(cx - r * 0.38f, cy - r * 0.38f, cx + r * 0.38f, cy + r * 0.38f,
                    new int[]{Ui.PINK, Ui.ORANGE, Ui.YELLOW}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r * 0.38f, label);
            txt.setTextSize(r * 0.3f);
            c.drawText("♫", cx, cy - r * 0.08f, txt);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF111111);
            c.drawCircle(cx, cy, r * 0.05f, p);
            c.restore();
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            last = 0;
        }
    }

    /** Égaliseur arc-en-ciel animé. */
    public static class Equalizer extends View {
        private static final int BARS = 16;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float[] f = new float[BARS], ph = new float[BARS];

        public Equalizer(Context c) {
            super(c);
            Random rnd = new Random();
            for (int i = 0; i < BARS; i++) { f[i] = 2f + rnd.nextFloat() * 4f; ph[i] = rnd.nextFloat() * 6.28f; }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float gap = w / BARS * 0.28f, bw = w / BARS - gap;
            float t = SystemClock.uptimeMillis() / 1000f;
            for (int i = 0; i < BARS; i++) {
                float lvl = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(t * f[i] + ph[i])) * (0.6f + 0.4f * (float) Math.abs(Math.sin(t * 0.7f + i)));
                float bh = h * lvl;
                float left = i * (bw + gap) + gap / 2;
                r.set(left, h - bh, left + bw, h);
                p.setColor(mix(Ui.NEON_BLUE, Ui.NEON_PINK, i / (float) (BARS - 1)));
                c.drawRoundRect(r, bw / 2, bw / 2, p);
            }
            postInvalidateOnAnimation();
        }
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
        int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
        int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Panneau néon façon icône : fond bleu nuit → violet, contour lumineux bleu → rose. */
    public static class Neon extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float radius, width;

        public Neon(float radiusPx, float strokePx) {
            radius = radiusPx;
            width = strokePx;
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float in = width * 3f;
            r.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
            fill.setShader(new LinearGradient(b.left, 0, b.right, 0, new int[]{0xEB063B7D, 0xEB4D1B60}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, radius, radius, fill);
            stroke.setShader(new LinearGradient(b.left, 0, b.right, 0, new int[]{Ui.NEON_BLUE, Ui.NEON_PINK}, null, Shader.TileMode.CLAMP));
            for (int i = 3; i >= 1; i--) { // halo
                stroke.setStrokeWidth(width * (1 + i * 1.6f));
                stroke.setAlpha(28 * (4 - i));
                c.drawRoundRect(r, radius, radius, stroke);
            }
            stroke.setAlpha(255);
            stroke.setStrokeWidth(width);
            c.drawRoundRect(r, radius, radius, stroke);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
