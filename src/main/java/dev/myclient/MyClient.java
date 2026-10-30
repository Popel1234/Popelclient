package dev.myclient;

import dev.myclient.builder.SchematicBuilder;
import dev.myclient.cosmetic.CosmeticFeature;
import dev.myclient.cosmetic.Impulse;
import dev.myclient.fake.FakePay;
import dev.myclient.fake.FakeSpawners;
import dev.myclient.fake.FakeScoreboard;
import dev.myclient.fx.WorldFx;
import dev.myclient.cosmetic.CosmeticManager;
import dev.myclient.cosmetic.Particles;
import dev.myclient.gui.ClientScreen;
import dev.myclient.gui.TracerRenderer;
import dev.myclient.macro.MacroManager;
import dev.myclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.lwjgl.glfw.GLFW;

public class MyClient implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();
    public static final CosmeticManager COSMETICS = new CosmeticManager();
    public static final MacroManager MACROS = new MacroManager();
    private static KeyBinding menuKey;
    private static int saveTimer;

    @Override
    public void onInitializeClient() {
        menuKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.myclient.menu", GLFW.GLFW_KEY_RIGHT_SHIFT, "category.myclient"));

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new CosmeticFeature(playerRenderer));
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            FakeScoreboard.render(ctx);
            TracerRenderer.render(ctx);
        });
        FakePay.register();
        FakeSpawners.register();
        WorldFx.register();

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && Settings.hitFx) {
                WorldFx.burst(entity.getX(), entity.getY() + entity.getHeight() * 0.6, entity.getZ(), 0xBFE9FF, 12, 2.4f);
                Impulse.kick(0.7f);
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.START_CLIENT_TICK.register(mc -> MODULES.freecam.suppress(mc));

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            COSMETICS.loadUploads();
            Config.load();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) mc.setScreen(new ClientScreen());
            MODULES.tick(mc);
            COSMETICS.tick();
            MACROS.tick(mc);
            Particles.tick(mc);
            SchematicBuilder.tick(mc);
            Keybinds.tick(mc);
            FakeSpawners.tick(mc);
            if (++saveTimer >= 1200) { saveTimer = 0; Config.save(); }
        });
    }
}
