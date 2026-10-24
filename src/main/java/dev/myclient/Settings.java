package dev.myclient;

/** Simple on/off settings shared by mixins, effects and the GUI (saved in settings.json). */
public final class Settings {
    private Settings() {}

    public static boolean lowFire;
    public static boolean lowShield;
    public static boolean smallTotem;
    public static boolean noParticles;
    public static boolean jumpFx = true;
    public static boolean hitFx = true;
    public static boolean hurtFlash = true;
    public static float guiScale = 0.75f;
}
