package dev.myclient.fake;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Balisong (butterfly knife) with two curved handles with inlays and holes, pivot screws, a latch and a
 * curved blade (optionally with comb teeth). Every sword has its own colours. The same parts are used for the
 * static item model and for the animated first-person version.
 */
public final class KnifeDesign {
    private KnifeDesign() {}

    public record Variant(String sword, String name, int blade, int bladeDark, int handle, int inlay, boolean comb) {}
    public record Part(float x1, float y1, float z1, float x2, float y2, float z2, int tile) {}

    public static final int BLADE = 0, BLADE_DARK = 1, HANDLE = 2, INLAY = 3, DARK = 4, SHINE = 5, BRASS = 6;

    public static final Variant[] VARIANTS = {
        new Variant("wooden_sword", "Emerald Comb", 0x3FA34D, 0x1F6B34, 0x131313, 0x35C468, true),
        new Variant("stone_sword", "Ruby Comb", 0xB3263A, 0x6E1424, 0x1A1010, 0xE04A5E, true),
        new Variant("golden_sword", "Aurum", 0xE0B73A, 0x9A7A1C, 0x241C0C, 0xF4D878, false),
        new Variant("iron_sword", "Steel Drop", 0xC4CAD2, 0x7C8591, 0x15171B, 0x6C8AA6, false),
        new Variant("diamond_sword", "Sapphire Comb", 0x3F8FE0, 0x1F4F8F, 0x101820, 0x7CCBFF, true),
        new Variant("netherite_sword", "Void Fang", 0x5B3A7E, 0x2A1A44, 0x0C0A10, 0x9D55E8, false)
    };

    public static Variant variantFor(String itemPath) {
        for (Variant v : VARIANTS) if (v.sword().equals(itemPath)) return v;
        return null;
    }

    public static int color(Variant v, int tile) {
        return switch (tile) {
            case BLADE -> v.blade();
            case BLADE_DARK -> v.bladeDark();
            case HANDLE -> v.handle();
            case INLAY -> v.inlay();
            case SHINE -> 0xF4F8FF;
            case BRASS -> 0xC9A24A;
            default -> 0x101114;
        };
    }

    static float handleX(float y) { float d = y - 4.5f; return 7.7f + 0.035f * d * d; }

    static float bladeX(float y) { float d = y - 9.5f; return 8f - 0.045f * d * d; }

    static float bladeW(float y) { return 2.1f - 0.12f * (y - 9.5f); }

    public static List<Part> handle(boolean front) {
        List<Part> out = new ArrayList<>();
        float z1 = front ? 8.25f : 6.65f, z2 = front ? 9.45f : 7.85f;
        float outer1 = front ? z2 : z1 - 0.1f, outer2 = front ? z2 + 0.1f : z1;
        for (int i = 0; i < 9; i++) {
            float xc = handleX(i + 0.5f);
            out.add(new Part(xc - 0.95f, i, z1, xc + 0.95f, i + 1.02f, z2, HANDLE));
            if (i >= 2 && i <= 6) out.add(new Part(xc - 0.5f, i, outer1, xc + 0.5f, i + 1.02f, outer2, INLAY));
        }
        for (float yh : new float[]{1.0f, 7.8f}) {
            float xc = handleX(yh);
            float h1 = front ? z2 + 0.1f : z1 - 0.2f, h2 = front ? z2 + 0.2f : z1 - 0.1f;
            out.add(new Part(xc - 0.28f, yh - 0.28f, h1, xc + 0.28f, yh + 0.28f, h2, DARK));
        }
        out.add(new Part(handleX(0) - 1.0f, -0.3f, z1, handleX(0) + 1.0f, 0f, z2, DARK));
        float sx = handleX(9);
        out.add(new Part(sx - 0.5f, 8.5f, front ? z2 : z1 - 0.2f, sx + 0.5f, 9.5f, front ? z2 + 0.2f : z1, BRASS));
        if (front) out.add(new Part(handleX(8.5f) + 0.95f, 7.9f, z1 + 0.3f, handleX(8.5f) + 1.6f, 8.9f, z2 - 0.3f, BLADE));
        return out;
    }

    public static List<Part> blade(Variant v) {
        List<Part> out = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            float y0 = 9 + i, yc = y0 + 0.5f, xc = bladeX(yc), w = bladeW(yc);
            out.add(new Part(xc - w / 2, y0, 7.6f, xc + w / 2, y0 + 1.05f, 8.4f, i % 2 == 0 ? BLADE : BLADE_DARK));
            if (!v.comb()) out.add(new Part(xc + w / 2 - 0.12f, y0, 7.75f, xc + w / 2 + 0.12f, y0 + 1.05f, 8.25f, SHINE));
        }
        if (v.comb()) {
            for (int k = 0; k < 17; k++) {
                float y = 9.7f + k * 0.5f, xr = bladeX(y) + bladeW(y) / 2;
                out.add(new Part(xr - 0.05f, y - 0.1f, 7.7f, xr + 1.25f - k * 0.045f, y + 0.1f, 8.3f, BLADE_DARK));
            }
        }
        float tx = bladeX(18.5f);
        out.add(new Part(tx - 0.35f, 18.0f, 7.8f, tx + 0.35f, 18.9f, 8.2f, SHINE));
        return out;
    }

    // ------------------------------------------------------------------ static item model (open pose)
    private static String f(float v) { return String.format(Locale.US, "%.2f", v); }

    private static void el(StringBuilder sb, Part p) {
        if (sb.length() > 0) sb.append(",");
        int u = (p.tile() % 4) * 4, vv = (p.tile() / 4) * 4;
        String face = "{\"uv\":[" + u + "," + vv + "," + (u + 4) + "," + (vv + 4) + "],\"texture\":\"#0\"}";
        sb.append("{\"from\":[").append(f(p.x1())).append(",").append(f(p.y1())).append(",").append(f(p.z1())).append("],\"to\":[")
            .append(f(p.x2())).append(",").append(f(p.y2())).append(",").append(f(p.z2()))
            .append("],\"rotation\":{\"origin\":[8,8,8],\"axis\":\"z\",\"angle\":-45},\"faces\":{")
            .append("\"north\":").append(face).append(",\"east\":").append(face).append(",\"south\":").append(face)
            .append(",\"west\":").append(face).append(",\"up\":").append(face).append(",\"down\":").append(face).append("}}");
    }

    static String model(Variant v) {
        StringBuilder e = new StringBuilder();
        for (Part p : handle(false)) el(e, p);
        for (Part p : handle(true)) el(e, p);
        for (Part p : blade(v)) el(e, p);
        String tex = "myclient:item/butterfly_" + v.sword();
        return "{\"textures\":{\"0\":\"" + tex + "\",\"particle\":\"" + tex + "\"},"
            + "\"elements\":[" + e + "],\"gui_light\":\"front\","
            + "\"display\":{"
            + "\"thirdperson_righthand\":{\"rotation\":[0,-90,55],\"translation\":[0,4,0.5],\"scale\":[0.85,0.85,0.85]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[0,90,-55],\"translation\":[0,4,0.5],\"scale\":[0.85,0.85,0.85]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]},"
            + "\"firstperson_lefthand\":{\"rotation\":[0,90,-25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]},"
            + "\"gui\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[0.8,0.8,0.8]},"
            + "\"ground\":{\"translation\":[0,2,0],\"scale\":[0.6,0.6,0.6]},"
            + "\"fixed\":{\"rotation\":[0,90,0]}}}";
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static BufferedImage texture(Variant v) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, 0xFF808080);
        for (int t = 0; t < 7; t++) {
            int base = color(v, t);
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int px = (t % 4) * 4 + x, py = (t / 4) * 4 + y;
                    int h = ((px * 73856093) ^ (py * 19349663) ^ (t * 83492791)) & 255;
                    float f = 0.92f + 0.16f * (h / 255f);
                    if (t == BLADE || t == BLADE_DARK) f += 0.10f * (float) Math.sin((x + y * 0.7) * 1.3 + t);   // marbled
                    if (t == INLAY) f += 0.08f * (1.5f - y * 0.5f);
                    img.setRGB(px, py, shade(base, f));
                }
            }
        }
        return img;
    }

    public static void write(Path root, Set<String> written) throws IOException {
        for (Variant v : VARIANTS) {
            Path tex = root.resolve("assets/myclient/textures/item/butterfly_" + v.sword() + ".png");
            Files.createDirectories(tex.getParent());
            ImageIO.write(texture(v), "png", tex.toFile());
            Path model = root.resolve("assets/minecraft/models/item/" + v.sword() + ".json");
            Files.createDirectories(model.getParent());
            Files.writeString(model, model(v));
            written.add(tex.toString());
            written.add(model.toString());
        }
    }
}
