package dev.myclient.mixin;

import dev.myclient.MyClient;
import dev.myclient.module.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: after the game placed the camera, move it to the free camera position. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void myclient$freecam(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                  float tickDelta, CallbackInfo ci) {
        Freecam f = MyClient.MODULES.freecam;
        if (!f.isEnabled()) return;
        f.hooked = true;
        double[] st = f.step(MinecraftClient.getInstance());
        if (st == null) return;
        setRotation((float) st[3], (float) st[4]);
        setPos(st[0], st[1], st[2]);
    }
}
