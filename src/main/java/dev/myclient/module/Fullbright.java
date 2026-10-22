package dev.myclient.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Client-side only night vision effect: works on every 1.21.x without mixins. */
public class Fullbright extends Module {
    public Fullbright() { super("Fullbright"); }

    @Override public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null) return;
        mc.player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
    }

    @Override protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}
