package dev.myclient.cosmetic;

import java.util.List;

/**
 * A cosmetic definition. Variants change colour/texture, animation changes motion.
 * Custom (user-made) cosmetics use the same shape, so the editor just writes these.
 */
public class Cosmetic {
    public enum Type { CAPE, HALO, HEADBAND }
    public enum Animation { NONE, PULSE, SPIN, WAVE, RAINBOW, FLOAT }

    public final String id;
    public final Type type;
    public final List<String> variants;
    public String activeVariant;
    public Animation animation;
    public float speed = 1f;
    public int tintRgb = 0xFFFFFF;
    public boolean custom;

    public Cosmetic(String id, Type type, List<String> variants, Animation animation) {
        this.id = id; this.type = type; this.variants = variants;
        this.activeVariant = variants.get(0); this.animation = animation;
    }

    /** Animation value 0..1 for use by the renderer. */
    public float phase(long tick, float partial) {
        return (((tick + partial) * speed) % 100f) / 100f;
    }
}
