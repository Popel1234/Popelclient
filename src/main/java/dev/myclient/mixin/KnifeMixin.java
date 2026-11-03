package dev.myclient.mixin;

import dev.myclient.Settings;
import dev.myclient.fake.KnifeRender;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the animated butterfly knife instead of the sword in your first-person hand. */
@Mixin(HeldItemRenderer.class)
public class KnifeMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void myclient$knife(LivingEntity entity, ItemStack stack, @Coerce Object mode, boolean leftHanded,
                                MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (!Settings.butterfly || !stack.isIn(ItemTags.SWORDS)) return;
        if (!String.valueOf(mode).contains("FIRST_PERSON")) return;
        try {
            if (KnifeRender.drawFirstPerson(entity, stack, leftHanded, matrices, vcp, light)) ci.cancel();
        } catch (Throwable ignored) {}
    }
}
