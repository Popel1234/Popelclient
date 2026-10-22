package dev.myclient.gui;

import dev.myclient.MyClient;
import dev.myclient.cosmetic.Cosmetic;
import dev.myclient.macro.Macro;
import dev.myclient.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ClientScreen extends Screen {
    private enum Tab { MODS, COSMETICS, SKIES, MACROS, EDITOR }
    private Tab tab = Tab.MODS;

    public ClientScreen() { super(Text.literal("MyClient")); }

    @Override protected void init() {
        clearChildren();
        int tabW = 80, x0 = (width - Tab.values().length * tabW) / 2;
        for (Tab t : Tab.values()) {
            addDrawableChild(ButtonWidget.builder(
                Text.literal((t == tab ? "> " : "") + t.name()), b -> { tab = t; init(); })
                .dimensions(x0 + t.ordinal() * tabW, 8, tabW - 2, 20).build());
        }
        int y = 44, cx = width / 2 - 120;
        switch (tab) {
            case MODS -> {
                for (Module m : MyClient.MODULES.all) {
                    addDrawableChild(ButtonWidget.builder(
                        Text.literal(m.name + ": " + (m.isEnabled() ? "ON" : "OFF")),
                        b -> { m.toggle(); init(); }).dimensions(cx, y, 240, 20).build());
                    y += 24;
                }
            }
            case COSMETICS -> {
                for (Cosmetic c : MyClient.COSMETICS.catalog) {
                    addDrawableChild(ButtonWidget.builder(
                        Text.literal((MyClient.COSMETICS.isEquipped(c) ? "[x] " : "[ ] ") + c.id + " (" + c.type + ")"),
                        b -> { MyClient.COSMETICS.toggle(c); init(); }).dimensions(cx, y, 170, 20).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal(c.activeVariant), b -> {
                        int i = (c.variants.indexOf(c.activeVariant) + 1) % c.variants.size();
                        c.activeVariant = c.variants.get(i); init();
                    }).dimensions(cx + 174, y, 66, 20).build());
                    y += 24;
                }
            }
            case SKIES -> {
                for (String s : MyClient.COSMETICS.skies) {
                    addDrawableChild(ButtonWidget.builder(
                        Text.literal((s.equals(MyClient.COSMETICS.activeSky) ? "> " : "") + s),
                        b -> { MyClient.COSMETICS.activeSky = s; init(); }).dimensions(cx, y, 240, 20).build());
                    y += 24;
                }
            }
            case MACROS -> {
                for (Macro m : MyClient.MACROS.macros) {
                    addDrawableChild(ButtonWidget.builder(
                        Text.literal(m.name + " -> " + m.command), b -> {}).dimensions(cx, y, 240, 20).build());
                    y += 24;
                }
                addDrawableChild(ButtonWidget.builder(Text.literal("+ Add example macro (key G)"), b -> {
                    MyClient.MACROS.macros.add(new Macro("Macro " + (MyClient.MACROS.macros.size() + 1),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_G, "/spawn", 0));
                    init();
                }).dimensions(cx, y, 240, 20).build());
            }
            case EDITOR -> {
                // TODO: text fields for id, type, tint, animation, speed + live preview
                addDrawableChild(ButtonWidget.builder(Text.literal("+ New custom accessory (spin halo)"), b -> {
                    Cosmetic c = new Cosmetic("custom_" + MyClient.COSMETICS.catalog.size(),
                        Cosmetic.Type.HALO, java.util.List.of("custom"), Cosmetic.Animation.SPIN);
                    c.custom = true; MyClient.COSMETICS.catalog.add(c);
                }).dimensions(cx, y, 240, 20).build());
            }
        }
    }

    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
