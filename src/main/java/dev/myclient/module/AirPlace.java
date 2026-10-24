package dev.myclient.module;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Hold right click to place the block in your hand into thin air where you look. */
public class AirPlace extends Module {
    private int cooldown;

    public AirPlace() {
        super("Air Place");
        configure("Distance", 2f, 6f, 4.5f, "m");
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (!mc.options.useKey.isPressed()) return;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() != HitResult.Type.MISS) return;
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;
        Vec3d eye = mc.player.getEyePos();
        Vec3d target = eye.add(mc.player.getRotationVec(1f).multiply(settingValue));
        BlockPos pos = BlockPos.ofFloored(target);
        BlockState s = mc.world.getBlockState(pos);
        if (!s.isReplaceable()) return;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        mc.player.swingHand(Hand.MAIN_HAND);
        cooldown = 4;
    }
}
