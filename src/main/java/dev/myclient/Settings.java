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
    public static int theme = 0;

    // fake textures / knife / spawner (resource pack based)
    public static boolean fakeBlocks;
    public static boolean butterfly;
    public static boolean fakeSpawner;
    public static String spawnerBlock = "minecraft:crying_obsidian";

    // fake scoreboard + fake /pay (visual only)
    public static boolean fakeScoreboard;
    public static boolean hideRealScoreboard;
    public static boolean fakePay;
    public static boolean payDeduct = true;
    public static boolean payInChat;
    public static String fakeTitle = "DonutSMP";
    public static int titleFrom = 0xFF7A59, titleTo = 0xFF4FA3;
    public static List<String> fakeLines = new ArrayList<>(defaultLines());
    public static String payFormat = "&fyou payed {player} &a$&f{amount}";
    public static String earnFormat = "&fyou earned &a$&f{amount}";
    public static double fakeMoney = 1250000;

    public static List<String> defaultLines() {
        return List.of(
            "&f&#00FF00&l$ &fMoney &#00FF00{money}",
            "&f&#A503FC\u2605 &fShards &#A503FC1,240",
            "&f&#FF0000\u2694 &fKills &#FF000058",
            "&f&#FC7703\u2620 &fDeaths &#FC770321",
            "&f&#FF55FF\u2726 &fKeyall &#FF55FF1h 30m",
            "&f&#FFE600\u231A &fPlaytime &#FFE60036h 12m",
            "&f&#55FFFF\u2691 &fTeam &#55FFFFNone",
            "&f&#AAAAAA\u2708 &fRegion &#AAAAAAEU 32ms");
    }
}
