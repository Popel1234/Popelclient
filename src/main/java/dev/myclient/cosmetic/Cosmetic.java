package dev.myclient.cosmetic;

import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.List;

/** A cosmetic definition. Variants change the look, each cosmetic has its own set of fitting animations. */
public class Cosmetic {
    public enum Type { CAPE, HALO, HEADBAND, WINGS, GLASSES, AURA, TRAIL }
    public enum Animation {
        NONE, PULSE, SPIN, WAVE, RAINBOW, FLOAT,
        ECLIPSE, CRACKLE, FLOW, WHIP, TWINKLE, BILLOW, BURST, SCAN, SWIRL, RISE, COMET
    }

    public final String id;
    public final Type type;
    public final List<String> variants;
    public final List<Animation> anims;
    public String activeVariant;
    public Animation animation;
    public float speed = 1f;
    public int tintRgb = 0xFFFFFF;
    public boolean custom;
    public Identifier texture;   // uploaded cape image
    public Path file;            // uploaded file on disk

    public Cosmetic(String id, Type type, List<String> variants, Animation... anims) {
        this(id, type, variants, List.of(anims));
    }

    public Cosmetic(String id, Type type, List<String> variants, List<Animation> anims) {
        this.id = id; this.type = type; this.variants = variants; this.anims = anims;
        this.activeVariant = variants.get(0);
        this.animation = anims.get(0);
    }

    public void nextAnimation() {
        int i = anims.indexOf(animation);
        animation = anims.get((i + 1) % anims.size());
    }

    public static List<Animation> defaultAnims(Type t) {
        return switch (t) {
            case HALO -> List.of(Animation.FLOAT, Animation.PULSE, Animation.SPIN, Animation.WAVE, Animation.ECLIPSE, Animation.CRACKLE, Animation.RAINBOW);
            case HEADBAND -> List.of(Animation.PULSE, Animation.FLOW, Animation.WHIP, Animation.TWINKLE, Animation.RAINBOW, Animation.NONE);
            case CAPE -> List.of(Animation.WAVE, Animation.BILLOW, Animation.NONE, Animation.TWINKLE, Animation.RAINBOW);
            case WINGS -> List.of(Animation.FLOAT, Animation.WAVE, Animation.BURST, Animation.NONE, Animation.PULSE, Animation.RAINBOW);
            case GLASSES -> List.of(Animation.NONE, Animation.PULSE, Animation.SCAN, Animation.WAVE, Animation.RAINBOW);
            case AURA -> List.of(Animation.SWIRL, Animation.RISE, Animation.BURST, Animation.PULSE, Animation.RAINBOW);
            case TRAIL -> List.of(Animation.FLOW, Animation.COMET, Animation.WAVE);
        };
    }

    /** Name of an animation as shown for this kind of cosmetic. */
    public static String label(Type t, Animation a) {
        String n = a.name().charAt(0) + a.name().substring(1).toLowerCase();
        return switch (t) {
            case HALO -> switch (a) {
                case FLOAT -> "Hover"; case PULSE -> "Breathe"; case SPIN -> "Orbit"; case WAVE -> "Ripple";
                case ECLIPSE -> "Eclipse"; case CRACKLE -> "Crackle"; case RAINBOW -> "Prism"; default -> n;
            };
            case HEADBAND -> switch (a) {
                case PULSE -> "Glow"; case FLOW -> "Flow"; case WHIP -> "Whip"; case TWINKLE -> "Twinkle";
                case RAINBOW -> "Prism"; case NONE -> "Still"; default -> n;
            };
            case CAPE -> switch (a) {
                case WAVE -> "Flow"; case BILLOW -> "Billow"; case NONE -> "Calm"; case TWINKLE -> "Starlight";
                case RAINBOW -> "Prism"; default -> n;
            };
            case WINGS -> switch (a) {
                case FLOAT -> "Glide"; case WAVE -> "Flap"; case BURST -> "Burst"; case NONE -> "Folded";
                case PULSE -> "Glow"; case RAINBOW -> "Prism"; default -> n;
            };
            case GLASSES -> switch (a) {
                case NONE -> "Static"; case PULSE -> "Glow"; case SCAN -> "Scan"; case WAVE -> "Shine";
                case RAINBOW -> "Prism"; default -> n;
            };
            case AURA -> switch (a) {
                case SWIRL -> "Swirl"; case RISE -> "Rise"; case BURST -> "Burst"; case PULSE -> "Pulse";
                case RAINBOW -> "Prism"; default -> n;
            };
            case TRAIL -> switch (a) {
                case FLOW -> "Ribbon"; case COMET -> "Comet"; case WAVE -> "Wave"; default -> n;
            };
        };
    }
}
