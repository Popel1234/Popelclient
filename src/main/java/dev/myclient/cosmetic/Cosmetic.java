package dev.myclient.cosmetic;

import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.List;

/** A cosmetic definition. Variants change colour/look, animation changes motion. */
public class Cosmetic {
    public enum Type { CAPE, HALO, HEADBAND, WINGS, GLASSES, AURA, TRAIL }
    public enum Animation { NONE, PULSE, SPIN, WAVE, RAINBOW, FLOAT }

    public final String id;
    public final Type type;
    public final List<String> variants;
    public String activeVariant;
    public Animation animation;
    public float speed = 1f;
    public int tintRgb = 0xFFFFFF;
    public boolean custom;
    public Identifier texture;   // uploaded cape image
    public Path file;            // uploaded file on disk

    public Cosmetic(String id, Type type, List<String> variants, Animation animation) {
        this.id = id; this.type = type; this.variants = variants;
        this.activeVariant = variants.get(0); this.animation = animation;
    }
}
