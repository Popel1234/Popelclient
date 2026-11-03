package dev.myclient.mixin;

import dev.myclient.emote.Emotes;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies emote poses to your player model. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerPoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"), require = 0)
    private void myclient$emote(PlayerEntityRenderState state, CallbackInfo ci) {
        try {
            Emotes.pose((PlayerEntityModel) (Object) this, state);
        } catch (Throwable ignored) {}
    }
}
