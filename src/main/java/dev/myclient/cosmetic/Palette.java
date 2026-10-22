package dev.myclient.cosmetic;

import net.minecraft.util.math.MathHelper;

public final class Palette {
    private Palette() {}

    public static int rgb(Cosmetic c) {
        return c.custom ? (c.tintRgb & 0xFFFFFF) : byName(c.activeVariant);
    }

    public static int swatch(Cosmetic c, String variant) {
        return c.custom ? (c.tintRgb & 0xFFFFFF) : byName(variant);
    }

    public static int byName(String v) {
        return switch (v) {
            case "gold" -> 0xFFC93C;
            case "ice" -> 0x8FE3FF;
            case "neon" -> 0x39FF88;
            case "white" -> 0xFFFFFF;
            case "purple" -> 0xB26BFF;
            case "crimson" -> 0xD81E3F;
            case "ocean" -> 0x1E88FF;
            case "void" -> 0x5B32B0;
            case "red" -> 0xFF3B4E;
            case "blue" -> 0x3B82FF;
            case "green" -> 0x35E07A;
            default -> 0xE6E6F0;
        };
    }

    /** t is wrapped to 0..1. Returns 24-bit rgb. */
    public static int rainbow(float t, float s, float v) {
        return MathHelper.hsvToRgb(t - (float) Math.floor(t), s, v) & 0xFFFFFF;
    }
}
