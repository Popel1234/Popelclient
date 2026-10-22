package dev.myclient;

import dev.myclient.cosmetic.CosmeticManager;
import dev.myclient.gui.ClientScreen;
import dev.myclient.macro.MacroManager;
import dev.myclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

public class MyClient implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();
    public static final CosmeticManager COSMETICS = new CosmeticManager();
    public static final MacroManager MACROS = new MacroManager();
    private static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.myclient.menu", GLFW.GLFW_KEY_RIGHT_SHIFT, "category.myclient"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) mc.setScreen(new ClientScreen());
            MODULES.tick(mc);
            COSMETICS.tick();
            MACROS.tick(mc);
        });
    }
}
