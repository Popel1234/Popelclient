package dev.myclient.fake;

import java.awt.image.BufferedImage;
import java.util.Locale;

/** Real 3D block models: the block itself becomes a mace or a trident (not a flat picture on a cube). */
final class BlockModels {
    private BlockModels() {}

    private static final int[][] TILE = {{0, 0, 4, 4}, {4, 0, 8, 4}, {8, 0, 12, 4}, {12, 0, 16, 4}};
    private static final int HEAD = 0, HEAD_DARK = 1, SHAFT = 2, SHAFT_DARK = 3;

    private static String f(float v) { return String.format(Locale.US, "%.2f", v); }

    private static void el(StringBuilder sb, float x1, float y1, float z1, float x2, float y2, float z2, int tile) {
        if (sb.length() > 0) sb.append(",");
        int[] uv = TILE[tile];
        String face = "{\"uv\":[" + uv[0] + "," + uv[1] + "," + uv[2] + "," + uv[3] + "],\"texture\":\"#0\"}";
        sb.append("{\"from\":[").append(f(x1)).append(",").append(f(y1)).append(",").append(f(z1)).append("],\"to\":[")
            .append(f(x2)).append(",").append(f(y2)).append(",").append(f(z2)).append("],\"faces\":{")
            .append("\"north\":").append(face).append(",\"east\":").append(face).append(",\"south\":").append(face)
            .append(",\"west\":").append(face).append(",\"up\":").append(face).append(",\"down\":").append(face).append("}}");
    }

    private static String wrap(String tex, StringBuilder elements) {
        return "{\"textures\":{\"0\":\"" + tex + "\",\"particle\":\"" + tex + "\"},\"ambientocclusion\":false,"
            + "\"elements\":[" + elements + "],\"display\":{"
            + "\"gui\":{\"rotation\":[30,225,0],\"translation\":[0,0,0],\"scale\":[0.625,0.625,0.625]},"
            + "\"ground\":{\"rotation\":[0,0,0],\"translation\":[0,3,0],\"scale\":[0.25,0.25,0.25]},"
            + "\"fixed\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[0.5,0.5,0.5]},"
            + "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.375,0.375,0.375]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"translation\":[0,0,0],\"scale\":[0.4,0.4,0.4]},"
            + "\"firstperson_lefthand\":{\"rotation\":[0,225,0],\"translation\":[0,0,0],\"scale\":[0.4,0.4,0.4]}}}";
    }

    static String mace(String tex) {
        StringBuilder e = new StringBuilder();
        el(e, 7f, 0f, 7f, 9f, 8f, 9f, SHAFT);                       // handle
        el(e, 6.5f, 1.5f, 6.5f, 9.5f, 2.3f, 9.5f, SHAFT_DARK);       // grip rings
        el(e, 6.5f, 4.5f, 6.5f, 9.5f, 5.3f, 9.5f, SHAFT_DARK);
        el(e, 6f, 0f, 6f, 10f, 1f, 10f, SHAFT_DARK);                 // pommel
        el(e, 4f, 8f, 4f, 12f, 15f, 12f, HEAD);                      // head core
        el(e, 3.6f, 10.5f, 3.6f, 12.4f, 11.5f, 12.4f, HEAD_DARK);    // band
        el(e, 1.5f, 9f, 6.5f, 4f, 14f, 9.5f, HEAD);                  // four big flanges
        el(e, 12f, 9f, 6.5f, 14.5f, 14f, 9.5f, HEAD);
        el(e, 6.5f, 9f, 1.5f, 9.5f, 14f, 4f, HEAD);
        el(e, 6.5f, 9f, 12f, 9.5f, 14f, 14.5f, HEAD);
        el(e, 3f, 9.5f, 3f, 5f, 13.5f, 5f, HEAD_DARK);               // corner spikes
        el(e, 11f, 9.5f, 3f, 13f, 13.5f, 5f, HEAD_DARK);
        el(e, 3f, 9.5f, 11f, 5f, 13.5f, 13f, HEAD_DARK);
        el(e, 11f, 9.5f, 11f, 13f, 13.5f, 13f, HEAD_DARK);
        el(e, 6f, 15f, 6f, 10f, 16f, 10f, HEAD_DARK);                // top cap
        return wrap(tex, e);
    }

    static String trident(String tex) {
        StringBuilder e = new StringBuilder();
        el(e, 7.2f, 0f, 7.2f, 8.8f, 11f, 8.8f, SHAFT);               // shaft
        el(e, 6.6f, 0f, 6.6f, 9.4f, 1.2f, 9.4f, SHAFT_DARK);         // pommel
        el(e, 6.6f, 3.5f, 6.6f, 9.4f, 4.3f, 9.4f, SHAFT_DARK);       // grip wraps
        el(e, 6.6f, 6.5f, 6.6f, 9.4f, 7.3f, 9.4f, SHAFT_DARK);
        el(e, 3.5f, 9.5f, 7f, 12.5f, 10.8f, 9f, HEAD);               // cross piece
        el(e, 7f, 10.8f, 7f, 9f, 15f, 9f, HEAD);                     // centre prong
        el(e, 7.5f, 15f, 7.5f, 8.5f, 16f, 8.5f, HEAD_DARK);
        el(e, 3.5f, 10.8f, 7.2f, 5f, 15f, 8.8f, HEAD);               // side prongs
        el(e, 3.9f, 15f, 7.5f, 4.6f, 16f, 8.5f, HEAD_DARK);
        el(e, 11f, 10.8f, 7.2f, 12.5f, 15f, 8.8f, HEAD);
        el(e, 11.4f, 15f, 7.5f, 12.1f, 16f, 8.5f, HEAD_DARK);
        el(e, 2.8f, 12f, 7.4f, 3.5f, 14f, 8.6f, HEAD_DARK);          // barbs
        el(e, 12.5f, 12f, 7.4f, 13.2f, 14f, 8.6f, HEAD_DARK);
        el(e, 6.4f, 12f, 7.4f, 7f, 14f, 8.6f, HEAD_DARK);
        el(e, 9f, 12f, 7.4f, 9.6f, 14f, 8.6f, HEAD_DARK);
        el(e, 5f, 9f, 7.2f, 7f, 10f, 8.8f, HEAD_DARK);               // shoulders
        el(e, 9f, 9f, 7.2f, 11f, 10f, 8.8f, HEAD_DARK);
        return wrap(tex, e);
    }

    /** 16x16 palette: 4 tiles (head, head dark, shaft, shaft dark) with a little shading. */
    static BufferedImage palette(int head, int shaft) {
        int[] cols = {head, shade(head, 0.62f), shaft, shade(shaft, 0.62f)};
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, 0xFF808080);
        for (int t = 0; t < 4; t++) {
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    float s = 1.08f - 0.07f * y + (((x + y) & 1) == 0 ? 0.03f : -0.02f);
                    img.setRGB(TILE[t][0] + x, TILE[t][1] + y, shade(cols[t], s));
                }
            }
        }
        return img;
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
