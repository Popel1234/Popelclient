package dev.myclient;

import java.util.ArrayList;
import java.util.List;

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

    // fake textures / knife (resource pack based)
    public static boolean fakeBlocks;
    public static boolean butterfly;

    // fake scoreboard + fake /pay (visual only)
    public static boolean fakeScoreboard;
    public static boolean hideRealScoreboard;
    public static boolean fakePay;
    public static boolean payDeduct = true;
    public static String fakeTitle = "&6&lDonutSMP";
    public static List<String> fakeLines = new ArrayList<>(List.of(
        "&fMoney: &a${money}", "&fShards: &d1,240", "&fKills: &c58", "&fDeaths: &c21",
        "&fPlaytime: &b36h 12m", "&fRegion: &eEU", "", "&edonutsmp.net"));
    public static String payFormat = "&ayou payed {player} {amount}";
    public static double fakeMoney = 1250000;
}
