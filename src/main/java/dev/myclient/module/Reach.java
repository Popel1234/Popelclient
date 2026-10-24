package dev.myclient.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Longer reach for blocks and entities. Servers with their own checks may still reject far hits. */
public class Reach extends Module {
    private boolean applied;

    public Reach() {
        super("Reach");
        configure("Range", 3f, 8f, 4.5f, "m");
    }

    private static void set(PlayerEntity p, double block, double entity) {
        EntityAttributeInstance b = p.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        EntityAttributeInstance e = p.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        if (b != null) b.setBaseValue(block);
        if (e != null) e.setBaseValue(entity);
    }

    private void apply(MinecraftClient mc, double block, double entity) {
        if (mc.player == null) return;
        set(mc.player, block, entity);
        MinecraftServer srv = mc.getServer();
        if (srv != null) {
            final java.util.UUID id = mc.player.getUuid();
            srv.execute(() -> {
                ServerPlayerEntity sp = srv.getPlayerManager().getPlayer(id);
                if (sp != null) set(sp, block, entity);
            });
        }
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        if (isEnabled()) {
            apply(mc, settingValue, settingValue);
            applied = true;
        } else if (applied) {
            apply(mc, 4.5, 3.0);
            applied = false;
        }
    }
}
