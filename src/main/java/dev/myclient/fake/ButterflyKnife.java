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

/** Generates a detailed 3D butterfly knife model + texture that replaces every sword. */
final class ButterflyKnife {
    private ButterflyKnife() {}

    static final String[] SWORDS = {"wooden_sword", "stone_sword", "golden_sword", "iron_sword", "diamond_sword", "netherite_sword"};
    private static final int[][] TILE = {{0, 0, 4, 4}, {4, 0, 8, 4}, {8, 0, 12, 4}, {12, 0, 16, 4}, {0, 4, 4, 8}};
    private static final int STEEL = 0, DARK = 1, HANDLE = 2, BRASS = 3, SHINE = 4;

    private static String f(float v) { return String.format(Locale.US, "%.2f", v); }

    private static void el(StringBuilder sb, float x1, float y1, float z1, float x2, float y2, float z2, int tile) {
        if (sb.length() > 0) sb.append(",");
        int[] uv = TILE[tile];
        String face = "{\"uv\":[" + uv[0] + "," + uv[1] + "," + uv[2] + "," + uv[3] + "],\"texture\":\"#0\"}";
        sb.append("{\"from\":[").append(f(x1)).append(",").append(f(y1)).append(",").append(f(z1)).append("],\"to\":[")
            .append(f(x2)).append(",").append(f(y2)).append(",").append(f(z2))
            .append("],\"rotation\":{\"origin\":[8,8,8],\"axis\":\"z\",\"angle\":-45},\"faces\":{")
            .append("\"north\":").append(face).append(",\"east\":").append(face).append(",\"south\":").append(face)
            .append(",\"west\":").append(face).append(",\"up\":").append(face).append(",\"down\":").append(face).append("}}");
    }

    private static String model() {
        StringBuilder e = new StringBuilder();
        // handles (two halves with a dark spacer) and grip grooves
        el(e, 6.0f, 0.5f, 7.0f, 7.4f, 8.0f, 9.0f, HANDLE);
        el(e, 8.6f, 0.5f, 7.0f, 10.0f, 8.0f, 9.0f, HANDLE);
        el(e, 7.4f, 0.5f, 7.3f, 8.6f, 8.0f, 8.7f, DARK);
        for (int k = 0; k < 5; k++) {
            float y = 1.2f + k * 1.4f;
            el(e, 5.9f, y, 6.9f, 7.5f, y + 0.4f, 9.1f, DARK);
            el(e, 8.5f, y, 6.9f, 10.1f, y + 0.4f, 9.1f, DARK);
        }
        // pommel + pivot screws + latches
        el(e, 5.8f, 0.0f, 6.8f, 10.2f, 0.5f, 9.2f, BRASS);
        el(e, 5.7f, 7.0f, 7.6f, 6.0f, 7.8f, 8.4f, BRASS);
        el(e, 10.0f, 7.0f, 7.6f, 10.3f, 7.8f, 8.4f, BRASS);
        el(e, 5.7f, 2.0f, 7.6f, 6.0f, 2.8f, 8.4f, BRASS);
        el(e, 10.0f, 2.0f, 7.6f, 10.3f, 2.8f, 8.4f, BRASS);
        el(e, 5.9f, 5.2f, 6.6f, 7.0f, 6.6f, 7.1f, STEEL);
        el(e, 9.0f, 5.2f, 6.6f, 10.1f, 6.6f, 7.1f, STEEL);
        // guard
        el(e, 6.4f, 8.0f, 7.4f, 9.6f, 8.9f, 8.6f, STEEL);
        el(e, 6.2f, 8.2f, 7.6f, 6.5f, 8.7f, 8.4f, BRASS);
        el(e, 9.5f, 8.2f, 7.6f, 9.8f, 8.7f, 8.4f, BRASS);
        // blade: base, belly, tip, fuller, spine, edge shine
        el(e, 7.3f, 8.9f, 7.6f, 8.7f, 12.0f, 8.4f, STEEL);
        el(e, 7.5f, 12.0f, 7.65f, 8.6f, 14.8f, 8.35f, STEEL);
        el(e, 7.8f, 14.8f, 7.7f, 8.5f, 15.9f, 8.3f, STEEL);
        el(e, 8.1f, 15.9f, 7.8f, 8.5f, 16.5f, 8.2f, SHINE);
        el(e, 7.85f, 9.4f, 8.4f, 8.15f, 14.2f, 8.45f, DARK);
        el(e, 7.85f, 9.4f, 7.55f, 8.15f, 14.2f, 7.6f, DARK);
        el(e, 7.2f, 8.9f, 7.7f, 7.5f, 13.5f, 8.3f, DARK);
        el(e, 8.45f, 9.0f, 7.7f, 8.7f, 14.5f, 8.3f, SHINE);

        return "{\"textures\":{\"0\":\"myclient:item/butterfly\",\"particle\":\"myclient:item/butterfly\"},"
            + "\"elements\":[" + e + "],\"gui_light\":\"front\","
            + "\"display\":{"
            + "\"thirdperson_righthand\":{\"rotation\":[0,-90,55],\"translation\":[0,4,0.5],\"scale\":[0.85,0.85,0.85]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[0,90,-55],\"translation\":[0,4,0.5],\"scale\":[0.85,0.85,0.85]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]},"
            + "\"firstperson_lefthand\":{\"rotation\":[0,90,-25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]},"
            + "\"ground\":{\"translation\":[0,2,0],\"scale\":[0.6,0.6,0.6]},"
            + "\"fixed\":{\"rotation\":[0,90,0]}}}";
    }

    private static void fill(BufferedImage img, int[] t, int rgb, boolean grooves) {
        for (int y = t[1]; y < t[3]; y++) {
            for (int x = t[0]; x < t[2]; x++) {
                float shade = 1f - 0.08f * (y - t[1]) + (((x + y) & 1) == 0 ? 0.03f : -0.02f);
                if (grooves && (y - t[1]) % 2 == 1) shade *= 0.78f;
                int r = Math.min(255, (int) (((rgb >> 16) & 255) * shade)), g = Math.min(255, (int) (((rgb >> 8) & 255) * shade)), b = Math.min(255, (int) ((rgb & 255) * shade));
                img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static BufferedImage texture() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, 0xFF808890);
        fill(img, TILE[STEEL], 0xC9D0DA, false);
        fill(img, TILE[DARK], 0x3A3F4C, false);
        fill(img, TILE[HANDLE], 0x6B4330, true);
        fill(img, TILE[BRASS], 0xD2A94A, false);
        fill(img, TILE[SHINE], 0xF4F8FF, false);
        return img;
    }

    static List<Path> files(Path root) {
        List<Path> out = new ArrayList<>();
        out.add(root.resolve("assets/myclient/textures/item/butterfly.png"));
        for (String s : SWORDS) out.add(root.resolve("assets/minecraft/models/item/" + s + ".json"));
        return out;
    }

    static void write(Path root, Set<String> written) throws IOException {
        Path tex = root.resolve("assets/myclient/textures/item/butterfly.png");
        Files.createDirectories(tex.getParent());
        ImageIO.write(texture(), "png", tex.toFile());
        written.add(tex.toString());
        String json = model();
        for (String s : SWORDS) {
            Path f = root.resolve("assets/minecraft/models/item/" + s + ".json");
            Files.createDirectories(f.getParent());
            Files.writeString(f, json);
            written.add(f.toString());
        }
    }
}
