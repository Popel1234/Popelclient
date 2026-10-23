package dev.myclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.myclient.cosmetic.Cosmetic;
import dev.myclient.cosmetic.CosmeticManager;
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
            for (Module m : MyClient.MODULES.all) d.modules.put(m.name, m.isEnabled());
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
                        Cosmetic c = new Cosmetic(cc.id, Cosmetic.Type.valueOf(cc.type),
                            List.of(cc.variant == null ? "custom" : cc.variant), Cosmetic.Animation.valueOf(cc.anim));
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
            if (d.tracerRadius >= 8) MyClient.MODULES.tracers.radius = d.tracerRadius;
        } catch (Exception ignored) {}
    }
}
