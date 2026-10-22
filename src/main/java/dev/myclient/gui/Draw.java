package dev.myclient.gui;

import net.minecraft.client.gui.DrawContext;

/** Small 2D helpers: rounded rectangles, circles, colour maths. */
public final class Draw {
    private Draw() {}

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

    public static void rr(DrawContext c, int x, int y, int w, int h, int r, int color) {
        rrGrad(c, x, y, w, h, r, color, color);
    }

    /** Rounded rectangle with a vertical gradient. */
    public static void rrGrad(DrawContext c, int x, int y, int w, int h, int r, int top, int bot) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int k = Math.min(i, h - 1 - i);
            int inset = 0;
            if (k < r) {
                double dy = r - k - 0.5;
                inset = (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy));
            }
            if (inset * 2 >= w) continue;
            int col = top == bot ? top : lerp(top, bot, h <= 1 ? 0f : i / (float) (h - 1));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
        }
    }

    public static void circle(DrawContext c, int cx, int cy, int r, int color) {
        rr(c, cx - r, cy - r, r * 2, r * 2, r, color);
    }
}
