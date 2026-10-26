package dev.myclient.mixin;

import dev.myclient.Settings;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flip / fidget animation for the butterfly knife (first person). */
@Mixin(HeldItemRenderer.class)
public class KnifeMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), require = 0)
    private void myclient$knife(LivingEntity entity, ItemStack stack, @Coerce Object mode, boolean leftHanded,
                                MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (!Settings.butterfly || !stack.isIn(ItemTags.SWORDS)) return;
        float sp = entity.getHandSwingProgress(1f);
        float t = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);
        matrices.translate(0f, 0.04f, 0f);
        if (sp > 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-sp * 540f));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(sp * Math.PI) * 25f));
        } else {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(t * 2.2f) * 5f));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.sin(t * 1.7f) * 4f));
        }
    }
}
