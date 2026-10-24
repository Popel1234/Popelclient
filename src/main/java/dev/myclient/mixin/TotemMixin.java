package dev.myclient.mixin;

import dev.myclient.Settings;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class TotemMixin {
    @Shadow private ItemStack floatingItem;

    @Inject(method = "showFloatingItem", at = @At("TAIL"), require = 0)
    private void myclient$smallTotem(ItemStack stack, CallbackInfo ci) {
        if (Settings.smallTotem) this.floatingItem = null;
    }
}
