package dev.myclient;

import dev.myclient.builder.SchematicBuilder;
import dev.myclient.cosmetic.CosmeticFeature;
import dev.myclient.cosmetic.CosmeticManager;
import dev.myclient.cosmetic.Particles;
import dev.myclient.gui.ClientScreen;
import dev.myclient.macro.MacroManager;
import dev.myclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
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

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new CosmeticFeature(playerRenderer));
            }
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> COSMETICS.loadUploads());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) mc.setScreen(new ClientScreen());
            MODULES.tick(mc);
            COSMETICS.tick();
            MACROS.tick(mc);
            Particles.tick(mc);
            SchematicBuilder.tick(mc);
        });
    }
}
