package dev.myclient.cosmetic;

/** Short burst of energy (jump, hit, damage) that cosmetics can react to. Decays by itself. */
public final class Impulse {
    private Impulse() {}

    public static float value;

    public static void kick(float v) { value = Math.min(1.5f, Math.max(value, v)); }

    public static void decay(float dt) { value = Math.max(0f, value - dt * 1.4f); }
}
