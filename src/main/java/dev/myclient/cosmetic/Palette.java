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
            case "angel" -> 0xFFF1B8;
            case "demon" -> 0xC41230;
            case "ember" -> 0xFF7A1A;
            case "dark" -> 0x23232F;
            case "pink" -> 0xFF5FB8;
            case "light" -> 0xFFF6C0;
            case "flame" -> 0xFF6A00;
            case "frost" -> 0x9FE8FF;
            case "nature" -> 0x4CE06A;
            case "rainbow" -> 0xFF55FF;
            case "fire" -> 0xFF5A1F;
            case "stardust" -> 0xFFE98A;
            case "soul" -> 0x3FD7E8;
            case "magic" -> 0xB26BFF;
            case "galaxy" -> 0x6A3BD8;
            case "aurora" -> 0x2DF5A6;
            case "nebula" -> 0xE04FBF;
            case "midnight" -> 0x1F3A9A;
            default -> 0xE6E6F0;
        };
    }

    /** t is wrapped to 0..1. Returns 24-bit rgb. */
    public static int rainbow(float t, float s, float v) {
        return MathHelper.hsvToRgb(t - (float) Math.floor(t), s, v) & 0xFFFFFF;
    }
}
