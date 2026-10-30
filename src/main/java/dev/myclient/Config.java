package dev.myclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.myclient.cosmetic.Cosmetic;
import dev.myclient.cosmetic.CosmeticManager;
import dev.myclient.fake.FakeSpawners;
import dev.myclient.gui.ClientScreen;
import dev.myclient.macro.Macro;
import dev.myclient.module.Module;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Saves and restores everything you had on (config/myclient/settings.json). */
public final class Config {
    private Config() {}

    public static final class CosState { public String variant; public String anim; public float speed = 1f; }
    public static final class CustomCos { public String id, type, anim, variant; public int tint; public float speed = 1f; }
    public static final class MacroData { public String name, cmd; public int key; public boolean enabled = true; }
    public static final class WinData { public String tab; public float x, y; public int w, h; public boolean open, collapsed; }

    public static final class Data {
        public List<String> equipped = new ArrayList<>();
        public Map<String, CosState> cosmetics = new HashMap<>();
        public List<CustomCos> custom = new ArrayList<>();
        public Map<String, Boolean> modules = new HashMap<>();
        public List<MacroData> macros = new ArrayList<>();
        public String sky;
        public List<String> tracerBlocks = new ArrayList<>();
        public int tracerRadius = 32;
        public List<WinData> windows = new ArrayList<>();
        public Map<String, Float> moduleValues = new HashMap<>();
        public Map<String, Boolean> flags = new HashMap<>();
        public float guiScale = 0.75f;
        public List<String> xrayBlocks = new ArrayList<>();
        public String fakeTitle;
        public List<String> fakeLines;
        public String payFormat;
        public double fakeMoney = -1;
        public Map<String, Integer> keybinds = new HashMap<>();
        public int theme = 0;
        public List<String> spawners = new ArrayList<>();
        public String spawnerBlock;
        public String earnFormat;
        public int titleFrom = -1, titleTo = -1;
    }

    public static Data data = new Data();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() { return CosmeticManager.folder(".").resolve("settings.json"); }

    public static void save() {
        try {
            Data d = new Data();
            CosmeticManager cm = MyClient.COSMETICS;
            d.equipped.addAll(cm.equipped);
            for (Cosmetic c : cm.catalog) {
                CosState s = new CosState();
                s.variant = c.activeVariant;
                s.anim = c.animation.name();
                s.speed = c.speed;
                d.cosmetics.put(c.id, s);
                if (c.custom && c.texture == null && c.file == null) {
                    CustomCos cc = new CustomCos();
                    cc.id = c.id; cc.type = c.type.name(); cc.anim = c.animation.name();
                    cc.variant = c.activeVariant; cc.tint = c.tintRgb; cc.speed = c.speed;
                    d.custom.add(cc);
                }
            }
            for (Module m : MyClient.MODULES.all) {
                d.modules.put(m.name, m.isEnabled());
                if (m.settingLabel != null) d.moduleValues.put(m.name, m.settingValue);
            }
            d.flags.put("lowFire", Settings.lowFire);
            d.flags.put("lowShield", Settings.lowShield);
            d.flags.put("smallTotem", Settings.smallTotem);
            d.flags.put("noParticles", Settings.noParticles);
            d.flags.put("jumpFx", Settings.jumpFx);
            d.flags.put("hitFx", Settings.hitFx);
            d.flags.put("hurtFlash", Settings.hurtFlash);
            d.flags.put("fakeSpawner", Settings.fakeSpawner);
            d.flags.put("payInChat", Settings.payInChat);
            d.keybinds = new HashMap<>(Keybinds.bindings);
            d.theme = Settings.theme;
            d.spawners = FakeSpawners.serialize();
            d.spawnerBlock = Settings.spawnerBlock;
            d.earnFormat = Settings.earnFormat;
            d.titleFrom = Settings.titleFrom;
            d.titleTo = Settings.titleTo;
            d.flags.put("fakeBlocks", Settings.fakeBlocks);
            d.flags.put("butterfly", Settings.butterfly);
            d.flags.put("fakeScoreboard", Settings.fakeScoreboard);
            d.flags.put("hideRealScoreboard", Settings.hideRealScoreboard);
            d.flags.put("fakePay", Settings.fakePay);
            d.flags.put("payDeduct", Settings.payDeduct);
            d.xrayBlocks = new ArrayList<>(MyClient.MODULES.xray.blocks);
            d.fakeTitle = Settings.fakeTitle;
            d.fakeLines = new ArrayList<>(Settings.fakeLines);
            d.payFormat = Settings.payFormat;
            d.fakeMoney = Settings.fakeMoney;
            d.guiScale = Settings.guiScale;
            for (Macro m : MyClient.MACROS.macros) {
                MacroData md = new MacroData();
                md.name = m.name; md.cmd = m.command; md.key = m.keyCode; md.enabled = m.enabled;
                d.macros.add(md);
            }
            d.sky = cm.activeSky;
            d.tracerBlocks = new ArrayList<>(MyClient.MODULES.tracers.blocks);
            d.tracerRadius = MyClient.MODULES.tracers.radius;
            d.windows = ClientScreen.snapshot();
            Files.writeString(file(), GSON.toJson(d));
            data = d;
        } catch (Exception ignored) {}
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Data d = GSON.fromJson(Files.readString(f), Data.class);
            if (d == null) return;
            data = d;
            CosmeticManager cm = MyClient.COSMETICS;

            if (d.custom != null) {
                for (CustomCos cc : d.custom) {
                    try {
                        if (cm.catalog.stream().anyMatch(c -> c.id.equals(cc.id))) continue;
                        Cosmetic.Type ct = Cosmetic.Type.valueOf(cc.type);
                        Cosmetic c = new Cosmetic(cc.id, ct, List.of(cc.variant == null ? "custom" : cc.variant), Cosmetic.defaultAnims(ct));
                        try { c.animation = Cosmetic.Animation.valueOf(cc.anim); } catch (Exception ignored) {}
                        c.custom = true;
                        c.tintRgb = cc.tint;
                        c.speed = cc.speed;
                        cm.catalog.add(c);
                    } catch (Exception ignored) {}
                }
            }
            if (d.cosmetics != null) {
                for (Cosmetic c : cm.catalog) {
                    CosState s = d.cosmetics.get(c.id);
                    if (s == null) continue;
                    if (s.variant != null && c.variants.contains(s.variant)) c.activeVariant = s.variant;
                    try { if (s.anim != null) c.animation = Cosmetic.Animation.valueOf(s.anim); } catch (Exception ignored) {}
                    if (s.speed > 0) c.speed = s.speed;
                }
            }
            if (d.equipped != null) {
                for (String id : d.equipped) if (cm.catalog.stream().anyMatch(c -> c.id.equals(id))) cm.equipped.add(id);
            }
            if (d.moduleValues != null) {
                for (Module m : MyClient.MODULES.all) {
                    Float v = d.moduleValues.get(m.name);
                    if (v != null && m.settingLabel != null) m.settingValue = Math.max(m.settingMin, Math.min(m.settingMax, v));
                }
            }
            if (d.flags != null) {
                Settings.lowFire = d.flags.getOrDefault("lowFire", false);
                Settings.lowShield = d.flags.getOrDefault("lowShield", false);
                Settings.smallTotem = d.flags.getOrDefault("smallTotem", false);
                Settings.noParticles = d.flags.getOrDefault("noParticles", false);
                Settings.jumpFx = d.flags.getOrDefault("jumpFx", true);
                Settings.hitFx = d.flags.getOrDefault("hitFx", true);
                Settings.hurtFlash = d.flags.getOrDefault("hurtFlash", true);
                Settings.fakeSpawner = d.flags.getOrDefault("fakeSpawner", false);
                Settings.payInChat = d.flags.getOrDefault("payInChat", false);
                Settings.fakeBlocks = d.flags.getOrDefault("fakeBlocks", false);
                Settings.butterfly = d.flags.getOrDefault("butterfly", false);
                Settings.fakeScoreboard = d.flags.getOrDefault("fakeScoreboard", false);
                Settings.hideRealScoreboard = d.flags.getOrDefault("hideRealScoreboard", false);
                Settings.fakePay = d.flags.getOrDefault("fakePay", false);
                Settings.payDeduct = d.flags.getOrDefault("payDeduct", true);
            }
            if (d.guiScale >= 0.5f && d.guiScale <= 1.5f) Settings.guiScale = d.guiScale;
            if (d.xrayBlocks != null && !d.xrayBlocks.isEmpty()) {
                MyClient.MODULES.xray.blocks.clear();
                MyClient.MODULES.xray.blocks.addAll(d.xrayBlocks);
            }
            if (d.keybinds != null) { Keybinds.bindings.clear(); Keybinds.bindings.putAll(d.keybinds); }
            Settings.theme = d.theme;
            if (d.spawners != null) FakeSpawners.load(d.spawners);
            if (d.spawnerBlock != null) Settings.spawnerBlock = d.spawnerBlock;
            if (d.earnFormat != null) Settings.earnFormat = d.earnFormat;
            if (d.titleFrom >= 0) Settings.titleFrom = d.titleFrom;
            if (d.titleTo >= 0) Settings.titleTo = d.titleTo;
            if (d.fakeTitle != null) Settings.fakeTitle = d.fakeTitle.equals("&6&lDonutSMP") ? "DonutSMP" : d.fakeTitle;
            if (d.fakeLines != null) Settings.fakeLines = new ArrayList<>(d.fakeLines);
            if (!Settings.fakeLines.isEmpty() && Settings.fakeLines.get(0).startsWith("&fMoney: ")) Settings.fakeLines = new ArrayList<>(Settings.defaultLines());
            if (d.payFormat != null && !d.payFormat.equals("&ayou payed {player} {amount}")) Settings.payFormat = d.payFormat;
            if (d.fakeMoney >= 0) Settings.fakeMoney = d.fakeMoney;
            if (d.modules != null) {
                for (Module m : MyClient.MODULES.all) {
                    Boolean on = d.modules.get(m.name);
                    if (on != null) m.setEnabled(on);
                }
            }
            if (d.macros != null) {
                MyClient.MACROS.macros.clear();
                for (MacroData md : d.macros) {
                    Macro m = new Macro(md.name == null ? "Macro" : md.name, md.key, md.cmd == null ? "" : md.cmd, 0);
                    m.enabled = md.enabled;
                    MyClient.MACROS.macros.add(m);
                }
            }
            if (d.sky != null && cm.skies.contains(d.sky)) cm.activeSky = d.sky;
            if (d.tracerBlocks != null) {
                MyClient.MODULES.tracers.blocks.addAll(d.tracerBlocks);
                MyClient.MODULES.tracers.changed();
            }
            if (d.tracerRadius >= 16) MyClient.MODULES.tracers.radius = Math.min(512, d.tracerRadius);
        } catch (Exception ignored) {}
    }
}
