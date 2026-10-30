package dev.myclient.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.RotationAxis;

/**
 * Smooth 2D drawing. Everything is drawn at "real pixel" resolution (the GUI matrix is scaled down by `ss`)
 * and edges get partial-alpha pixels, so rounded shapes are not blocky even at high GUI scales.
 * Call begin() before drawing and end() afterwards; all coordinates stay in normal GUI units.
 */
public final class Draw {
    private Draw() {}

    public static float ss = 2f;
    /** GUI scale factor (the menu is drawn at this fraction of normal size). */
    public static float k = 1f;
    /** Scales corner radii of non-circular shapes (themes with sharper or rounder corners). */
    public static float radiusScale = 1f;

    private static float u(float v) { return v * ss * k; }

    public static void begin(DrawContext c, float s) { begin(c, s, 1f); }

    public static void begin(DrawContext c, float s, float scale) {
        ss = s;
        k = scale;
        c.getMatrices().push();
        c.getMatrices().scale(1f / s, 1f / s, 1f);
    }

    public static void end(DrawContext c) { c.getMatrices().pop(); }

    public static int alpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }

    public static int scale(int argb, float f) {
        int a = argb >>> 24;
        int r = Math.min(255, (int) (((argb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((argb & 255) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) ((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    private static int mulAlpha(int col, float f) {
        int a = (int) ((col >>> 24) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (col & 0xFFFFFF);
    }

    public static void rect(DrawContext c, float x, float y, float w, float h, int color) {
        c.fill(Math.round(u(x)), Math.round(u(y)), Math.round(u(x + w)), Math.round(u(y + h)), color);
    }

    public static void rr(DrawContext c, float x, float y, float w, float h, float r, int color) {
        rrGrad(c, x, y, w, h, r, color, color);
    }

    /** Anti-aliased rounded rectangle with a vertical gradient. */
    public static void rrGrad(DrawContext c, float x, float y, float w, float h, float r, int top, int bot) {
        int X = Math.round(u(x)), Y = Math.round(u(y)), W = Math.round(u(w)), H = Math.round(u(h));
        if (W <= 0 || H <= 0) return;
        boolean circle = Math.abs(w - h) < 0.5f && r >= Math.min(w, h) / 2f - 0.5f;
        float R = Math.max(0f, Math.min(u(circle ? r : r * radiusScale), Math.min(W, H) / 2f));
        for (int i = 0; i < H; i++) {
            int k = Math.min(i, H - 1 - i);
            float inset = 0f;
            if (k < R) {
                float dy = R - k - 0.5f;
                inset = R - (float) Math.sqrt(Math.max(0f, R * R - dy * dy));
            }
            int ins = (int) Math.ceil(inset);
            if (ins * 2 >= W) continue;
            int col = top == bot ? top : lerp(top, bot, H <= 1 ? 0f : i / (float) (H - 1));
            c.fill(X + ins, Y + i, X + W - ins, Y + i + 1, col);
            float cov = ins - inset;
            if (ins > 0 && cov > 0.03f) {
                int ec = mulAlpha(col, cov);
                c.fill(X + ins - 1, Y + i, X + ins, Y + i + 1, ec);
                c.fill(X + W - ins, Y + i, X + W - ins + 1, Y + i + 1, ec);
            }
        }
    }

    public static void circle(DrawContext c, float cx, float cy, float r, int color) {
        rr(c, cx - r, cy - r, r * 2, r * 2, r, color);
    }

    /** Straight line between two GUI points. */
    public static void line(DrawContext c, float x1, float y1, float x2, float y2, float thick, int color) {
        float dx = u(x2 - x1), dy = u(y2 - y1);
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        int h = Math.max(1, Math.round(u(thick) / 2f));
        c.getMatrices().push();
        c.getMatrices().translate(u(x1), u(y1), 0);
        c.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        c.fill(0, -h, Math.round(len), h, color);
        c.getMatrices().pop();
    }
}
