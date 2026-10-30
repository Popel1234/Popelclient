package dev.myclient;

import dev.myclient.cosmetic.Cosmetic;
import dev.myclient.fake.FakePack;
import dev.myclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.util.*;

/** Keys for anything in the menu: right-click a row, press a key, done. Works without opening the menu. */
public final class Keybinds {
    private Keybinds() {}

    public static final Map<String, Integer> bindings = new LinkedHashMap<>();
    private static final Set<String> down = new HashSet<>();
    public static String capturingId, capturingLabel;

    public static void startCapture(String id, String label) { capturingId = id; capturingLabel = label; }

    public static void bind(String id, int key) {
        if (key < 0) bindings.remove(id); else bindings.put(id, key);
    }

    public static String keyName(int code) {
        try { return InputUtil.fromKeyCode(code, 0).getLocalizedText().getString().toUpperCase(); }
        catch (Exception e) { return "KEY " + code; }
    }

    public static String label(String id) {
        Integer k = bindings.get(id);
        return k == null ? null : keyName(k);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null || bindings.isEmpty()) return;
        long h = mc.getWindow().getHandle();
        for (Map.Entry<String, Integer> e : new ArrayList<>(bindings.entrySet())) {
            boolean now = InputUtil.isKeyPressed(h, e.getValue());
            if (now) {
                if (down.add(e.getKey())) {
                    String msg = perform(e.getKey());
                    if (msg != null) mc.inGameHud.setOverlayMessage(Text.literal(msg), false);
                }
            } else {
                down.remove(e.getKey());
            }
        }
    }

    private static String state(String name, boolean on) { return name + ": " + (on ? "ON" : "OFF"); }

    private static void applyFake() {
        try { FakePack.apply(); } catch (Exception ignored) {}
    }

    /** Runs the action behind an id and returns a short message for the action bar. */
    public static String perform(String id) {
        if (id.startsWith("mod:")) {
            String n = id.substring(4);
            for (Module m : MyClient.MODULES.all) if (m.name.equals(n)) { m.toggle(); return state(n, m.isEnabled()); }
            return null;
        }
        if (id.startsWith("cos:")) {
            String cid = id.substring(4);
            for (Cosmetic c : MyClient.COSMETICS.catalog) if (c.id.equals(cid)) {
                MyClient.COSMETICS.toggle(c);
                return state(c.id.replace('_', ' '), MyClient.COSMETICS.isEquipped(c));
            }
            return null;
        }
        if (id.startsWith("sky:")) {
            String s = id.substring(4);
            MyClient.COSMETICS.activeSky = s.equals(MyClient.COSMETICS.activeSky) ? "Default" : s;
            return "Sky: " + MyClient.COSMETICS.activeSky;
        }
        if (id.startsWith("set:")) return toggleSetting(id.substring(4));
        return null;
    }

    private static String toggleSetting(String label) {
        switch (label) {
            case "Jump effect": Settings.jumpFx = !Settings.jumpFx; return state(label, Settings.jumpFx);
            case "Hit effect": Settings.hitFx = !Settings.hitFx; return state(label, Settings.hitFx);
            case "Damage flash": Settings.hurtFlash = !Settings.hurtFlash; return state(label, Settings.hurtFlash);
            case "Low fire": Settings.lowFire = !Settings.lowFire; return state(label, Settings.lowFire);
            case "Low shield": Settings.lowShield = !Settings.lowShield; return state(label, Settings.lowShield);
            case "Small totem pop": Settings.smallTotem = !Settings.smallTotem; return state(label, Settings.smallTotem);
            case "No vanilla particles": Settings.noParticles = !Settings.noParticles; return state(label, Settings.noParticles);
            case "Fake blocks": Settings.fakeBlocks = !Settings.fakeBlocks; applyFake(); return state(label, Settings.fakeBlocks);
            case "Butterfly knife": Settings.butterfly = !Settings.butterfly; applyFake(); return state(label, Settings.butterfly);
            case "Fake spawner": Settings.fakeSpawner = !Settings.fakeSpawner; applyFake(); return state(label, Settings.fakeSpawner);
            case "Fake scoreboard": Settings.fakeScoreboard = !Settings.fakeScoreboard; return state(label, Settings.fakeScoreboard);
            case "Hide real scoreboard": Settings.hideRealScoreboard = !Settings.hideRealScoreboard; return state(label, Settings.hideRealScoreboard);
            case "Fake /pay": Settings.fakePay = !Settings.fakePay; return state(label, Settings.fakePay);
            case "Deduct fake money": Settings.payDeduct = !Settings.payDeduct; return state(label, Settings.payDeduct);
            case "Also in chat": Settings.payInChat = !Settings.payInChat; return state(label, Settings.payInChat);
            case "Tracers": MyClient.MODULES.tracers.toggle(); return state(label, MyClient.MODULES.tracers.isEnabled());
            case "X-Ray boxes": MyClient.MODULES.xray.toggle(); return state(label, MyClient.MODULES.xray.isEnabled());
            case "Sus Chunk Finder": MyClient.MODULES.susChunks.toggle(); return state(label, MyClient.MODULES.susChunks.isEnabled());
            case "Build schematic":
                dev.myclient.builder.SchematicBuilder.setRunning(!dev.myclient.builder.SchematicBuilder.running);
                return state(label, dev.myclient.builder.SchematicBuilder.running);
            default: return null;
        }
    }
}
