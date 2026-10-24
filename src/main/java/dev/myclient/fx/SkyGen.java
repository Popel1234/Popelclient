package dev.myclient.fx;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

/** Paints the built-in skies (gradient, nebula, stars, moons, logo) into an equirectangular image. */
final class SkyGen {
    private SkyGen() {}

    private record Pal(int top, int mid, int bottom, int nebA, int nebB, float neb, int stars, boolean milky, boolean sun, float logoScale) {}

    private static Pal pal(String n) {
        return switch (n) {
            case "Galaxy" -> new Pal(0x07041A, 0x1B0B3F, 0x3A1A6B, 0x7B2FF7, 0xFF4FD8, 0.60f, 3400, true, false, 1f);
            case "Aurora" -> new Pal(0x03070F, 0x082030, 0x0B3A3F, 0x0E5C58, 0x2A7BFF, 0.18f, 2200, false, false, 1f);
            case "Nebula" -> new Pal(0x10041E, 0x3A0B52, 0x5A1750, 0xFF4FBF, 0x4F7CFF, 0.70f, 2400, false, false, 1f);
            case "Sunset" -> new Pal(0x2B1B5A, 0xE0654A, 0xFFB45E, 0xFF8855, 0xB04F9A, 0.25f, 500, false, true, 1f);
            case "Blood Moon" -> new Pal(0x0A0204, 0x2A0610, 0x4A0C14, 0x7A0F1A, 0x3A0A2A, 0.28f, 1300, false, false, 1f);
            case "Lumen" -> new Pal(0x050B1E, 0x0C2347, 0x1C4A7A, 0x2B6BD6, 0x7E4BFF, 0.35f, 2600, true, false, 1.7f);
            default -> new Pal(0x040818, 0x0D1C4A, 0x1B3166, 0x2A3F9A, 0x6C4BB5, 0.28f, 2600, false, false, 1f);
        };
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int add(int base, int c, float k) {
        int r = Math.min(255, ((base >> 16) & 255) + (int) (((c >> 16) & 255) * k));
        int g = Math.min(255, ((base >> 8) & 255) + (int) (((c >> 8) & 255) * k));
        int b = Math.min(255, (base & 255) + (int) ((c & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    private static float[] fbm(Random r, int w, int h, int oct) {
        float[] out = new float[w * h];
        float amp = 1f, total = 0f;
        for (int o = 0; o < oct; o++) {
            int gx = 4 << o, gy = 2 << o;
            float[] g = new float[gx * gy];
            for (int i = 0; i < g.length; i++) g[i] = r.nextFloat();
            for (int y = 0; y < h; y++) {
                float fy = y / (float) h * gy;
                int y0 = Math.min(gy - 1, (int) fy), y1 = Math.min(gy - 1, y0 + 1);
                float ty = fy - y0;
                ty = ty * ty * (3 - 2 * ty);
                for (int x = 0; x < w; x++) {
                    float fx = x / (float) w * gx;
                    int x0 = (int) fx % gx, x1 = (x0 + 1) % gx;
                    float tx = fx - (int) fx;
                    tx = tx * tx * (3 - 2 * tx);
                    float a = g[y0 * gx + x0] + (g[y0 * gx + x1] - g[y0 * gx + x0]) * tx;
                    float b = g[y1 * gx + x0] + (g[y1 * gx + x1] - g[y1 * gx + x0]) * tx;
                    out[y * w + x] += (a + (b - a) * ty) * amp;
                }
            }
            total += amp;
            amp *= 0.5f;
        }
        for (int i = 0; i < out.length; i++) out[i] /= total;
        return out;
    }

    private static float sample(float[] g, int gw, int gh, float fx, float fy) {
        int x0 = Math.min(gw - 1, (int) fx), y0 = Math.min(gh - 1, (int) fy);
        int x1 = (x0 + 1) % gw, y1 = Math.min(gh - 1, y0 + 1);
        float tx = fx - x0, ty = fy - y0;
        float a = g[y0 * gw + x0] + (g[y0 * gw + x1] - g[y0 * gw + x0]) * tx;
        float b = g[y1 * gw + x0] + (g[y1 * gw + x1] - g[y1 * gw + x0]) * tx;
        return a + (b - a) * ty;
    }

    private static void moon(Graphics2D g, int cx, int cy, int r, int rgb, Random rnd) {
        Color c = new Color(rgb);
        g.setPaint(new RadialGradientPaint(new Point(cx, cy), r * 2.6f, new float[]{0f, 0.4f, 1f},
            new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 90), new Color(c.getRed(), c.getGreen(), c.getBlue(), 28), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Float(cx - r * 2.6f, cy - r * 2.6f, r * 5.2f, r * 5.2f));
        Shape old = g.getClip();
        g.setClip(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        Color light = new Color(Math.min(255, c.getRed() + 40), Math.min(255, c.getGreen() + 40), Math.min(255, c.getBlue() + 40));
        Color dark = new Color(c.getRed() * 2 / 3, c.getGreen() * 2 / 3, c.getBlue() * 2 / 3);
        g.setPaint(new RadialGradientPaint(new Point((int) (cx - r * 0.3f), (int) (cy - r * 0.3f)), r * 1.5f, new float[]{0f, 0.55f, 1f}, new Color[]{light, c, dark}));
        g.fillRect(cx - r, cy - r, r * 2, r * 2);
        for (int i = 0; i < 9; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * r * 0.75;
            int cr = (int) (r * (0.08 + rnd.nextDouble() * 0.16));
            int x = (int) (cx + Math.cos(a) * d), y = (int) (cy + Math.sin(a) * d);
            g.setPaint(new Color(0, 0, 0, 38));
            g.fill(new Ellipse2D.Float(x - cr, y - cr, cr * 2, cr * 2));
            g.setPaint(new Color(255, 255, 255, 20));
            g.fill(new Ellipse2D.Float(x - cr * 0.8f, y - cr * 0.8f, cr * 1.6f, cr * 1.6f));
        }
        g.setClip(old);
    }

    private static Font font() {
        try {
            Font f = Font.createFont(Font.TRUETYPE_FONT, SkyGen.class.getResourceAsStream("/assets/myclient/font/ui.ttf"));
            return f;
        } catch (Exception e) {
            return new Font("SansSerif", Font.BOLD, 20);
        }
    }

    /** The Lumen logo: glowing ring, crescent moon, small star and the name. */
    private static void logo(Graphics2D g, int cx, int cy, float s, Font base) {
        int r = (int) (46 * s);
        g.setPaint(new RadialGradientPaint(new Point(cx, cy), r * 2.2f, new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(140, 120, 255, 90), new Color(80, 190, 255, 28), new Color(80, 190, 255, 0)}));
        g.fill(new Ellipse2D.Float(cx - r * 2.2f, cy - r * 2.2f, r * 4.4f, r * 4.4f));
        g.setStroke(new BasicStroke(5f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint(cx - r, cy - r, new Color(150, 120, 255), cx + r, cy + r, new Color(70, 220, 255)));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        Area cres = new Area(new Ellipse2D.Float(cx - r * 0.55f, cy - r * 0.55f, r * 1.1f, r * 1.1f));
        cres.subtract(new Area(new Ellipse2D.Float(cx - r * 0.25f, cy - r * 0.7f, r * 1.1f, r * 1.1f)));
        g.setPaint(new Color(245, 244, 255));
        g.fill(cres);
        float sx = cx + r * 0.32f, sy = cy - r * 0.05f, sr = r * 0.22f;
        Polygon star = new Polygon();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 - Math.PI / 2;
            double rr = i % 2 == 0 ? sr : sr * 0.32;
            star.addPoint((int) (sx + Math.cos(a) * rr), (int) (sy + Math.sin(a) * rr));
        }
        g.setPaint(new Color(200, 245, 255));
        g.fillPolygon(star);

        Font f = base.deriveFont(Font.BOLD, 62f * s);
        g.setFont(f);
        String name = "LUMEN";
        FontMetrics fm = g.getFontMetrics();
        int track = (int) (12 * s);
        int w = 0;
        for (char ch : name.toCharArray()) w += fm.charWidth(ch) + track;
        w -= track;
        int x = cx - w / 2, y = cy + r + (int) (62 * s);
        for (int pass = 0; pass < 2; pass++) {
            int xx = x;
            for (char ch : name.toCharArray()) {
                if (pass == 0) {
                    g.setPaint(new Color(120, 150, 255, 38));
                    for (int dx = -3; dx <= 3; dx += 3) for (int dy = -3; dy <= 3; dy += 3) g.drawString(String.valueOf(ch), xx + dx, y + dy);
                } else {
                    g.setPaint(new GradientPaint(xx, y - fm.getAscent(), new Color(255, 255, 255), xx, y, new Color(170, 200, 255)));
                    g.drawString(String.valueOf(ch), xx, y);
                }
                xx += fm.charWidth(ch) + track;
            }
        }
    }

    static BufferedImage generate(String name) {
        Pal p = pal(name);
        final int W = 1536, H = 768;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        Random rnd = new Random(name.hashCode() * 31L + 7);
        float[] n1 = fbm(rnd, 384, 192, 5), n2 = fbm(rnd, 384, 192, 4);

        for (int y = 0; y < H; y++) {
            float t = y / (float) (H - 1);
            int base = t < 0.5f ? lerp(p.top, p.mid, (float) Math.pow(t / 0.5f, 0.9)) : lerp(p.mid, p.bottom, (t - 0.5f) / 0.5f);
            for (int x = 0; x < W; x++) {
                int c = base;
                if (p.neb > 0) {
                    float a = sample(n1, 384, 192, x / (float) W * 384f, y / (float) H * 191f);
                    float b = sample(n2, 384, 192, x / (float) W * 384f, y / (float) H * 191f);
                    float k = Math.max(0f, (a - 0.35f) * 2.2f);
                    k = k * k * p.neb * Math.max(0f, 1f - Math.abs(t - 0.42f) * 1.5f);
                    c = add(c, lerp(p.nebA, p.nebB, b), k);
                }
                px[y * W + x] = 0xFF000000 | c;
            }
        }

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (p.milky) {   // dense band of stars with a soft glow
            for (int i = 0; i < 2600; i++) {
                int x = rnd.nextInt(W);
                double cyb = H * 0.30 + Math.sin(x / (double) W * Math.PI * 2) * H * 0.10;
                int y = (int) (cyb + rnd.nextGaussian() * H * 0.045);
                if (y < 0 || y >= H) continue;
                g.setColor(new Color(255, 255, 255, 40 + rnd.nextInt(120)));
                g.fillRect(x, y, 1, 1);
            }
            for (int x = 0; x < W; x += 12) {
                double cyb = H * 0.30 + Math.sin(x / (double) W * Math.PI * 2) * H * 0.10;
                g.setPaint(new RadialGradientPaint(new Point(x, (int) cyb), 70f, new float[]{0f, 1f},
                    new Color[]{new Color(190, 170, 255, 16), new Color(190, 170, 255, 0)}));
                g.fill(new Ellipse2D.Float(x - 70, (float) cyb - 70, 140, 140));
            }
        }
        for (int i = 0; i < p.stars; i++) {
            int x = rnd.nextInt(W);
            int y = (int) (Math.pow(rnd.nextFloat(), 0.85) * H * 0.55);
            float b = 0.25f + 0.75f * (float) Math.pow(rnd.nextFloat(), 2.5);
            int tint = rnd.nextInt(3);
            Color c = tint == 0 ? new Color(255, 255, 255, (int) (255 * b)) : tint == 1 ? new Color(190, 215, 255, (int) (255 * b)) : new Color(255, 232, 200, (int) (255 * b));
            g.setColor(c);
            if (b > 0.8f) {
                g.fill(new Ellipse2D.Float(x - 1.2f, y - 1.2f, 2.4f, 2.4f));
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                g.fill(new Ellipse2D.Float(x - 4, y - 4, 8, 8));
            } else {
                g.fillRect(x, y, 1, 1);
            }
        }

        switch (name) {
            case "Galaxy" -> { moon(g, (int) (W * 0.30), (int) (H * 0.22), 56, 0xC9B8FF, rnd); moon(g, (int) (W * 0.78), (int) (H * 0.36), 22, 0xFFC2E6, rnd); }
            case "Aurora" -> moon(g, (int) (W * 0.80), (int) (H * 0.20), 30, 0xE8F4FF, rnd);
            case "Blood Moon" -> moon(g, (int) (W * 0.50), (int) (H * 0.30), 120, 0xC4302B, rnd);
            case "Sunset" -> moon(g, (int) (W * 0.50), (int) (H * 0.50), 80, 0xFFC25A, rnd);
            case "Lumen" -> moon(g, (int) (W * 0.80), (int) (H * 0.18), 34, 0xDCE6FF, rnd);
            case "Nebula" -> { }
            default -> moon(g, (int) (W * 0.62), (int) (H * 0.26), 70, 0xF4F1DE, rnd);
        }

        Font base = font();
        float ls = p.logoScale;
        int ly = (int) (H * 0.5 - (name.equals("Lumen") ? 175 : 120) * (ls > 1 ? 1.0 : 1.0));
        for (int i = 0; i < 4; i++) logo(g, (int) (W * (0.125 + 0.25 * i)), ly, name.equals("Lumen") ? 1.0f : 0.62f, base);
        g.dispose();
        return img;
    }
}
