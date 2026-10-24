package dev.myclient.module;

import dev.myclient.gui.ClientScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/** Keeps a Totem of Undying in your off hand. */
public class AutoTotem extends Module {
    private int cooldown;

    public AutoTotem() { super("Auto Totem"); }

    @Override
    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ClientScreen)) return;
        if (--cooldown > 0) return;
        PlayerEntity p = mc.player;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        ScreenHandler h = p.playerScreenHandler;
        for (int i = 0; i < h.slots.size(); i++) {
            if (i == 45) continue;
            if (h.slots.get(i).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                mc.interactionManager.clickSlot(h.syncId, i, 40, SlotActionType.SWAP, p);
                cooldown = 6;
                return;
            }
        }
    }
}
