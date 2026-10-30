package dev.myclient.fake;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The fake skeleton spawner menu: a chest-style screen with the drops, a sell button and your inventory. */
public class SpawnerScreen extends Screen {
    private final FakeSpawners.Spawner sp;
    private int left, top;

    public SpawnerScreen(FakeSpawners.Spawner sp) {
        super(Text.literal("Skeleton Spawner"));
        this.sp = sp;
    }

    @Override
    protected void init() {
        left = (width - 176) / 2;
        top = (height - 166) / 2;
    }

    // slot ids in the chest part
    private static final int INFO = 4, BONES = 11, SELL = 13, ARROWS = 15, CLOSE = 22;

    private int sx(int slot) { return left + 7 + (slot % 9) * 18; }

    private int sy(int slot) { return top + 17 + (slot / 9) * 18; }

    private static String abbr(long v) {
        if (v >= 1_000_000) return String.format(Locale.US, "%.1fM", v / 1_000_000.0);
        if (v >= 10_000) return String.format(Locale.US, "%.1fK", v / 1000.0);
        return String.valueOf(v);
    }

    private void slotBox(DrawContext ctx, int x, int y) {
        ctx.fill(x, y, x + 18, y + 18, 0xFF373737);
        ctx.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        ctx.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    private void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0xFF000000);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 3, 0xFFFFFFFF);
        ctx.fill(x + 1, y + 1, x + 3, y + h - 1, 0xFFFFFFFF);
        ctx.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        ctx.fill(x + w - 3, y + 1, x + w - 1, y + h - 1, 0xFF555555);
    }

    private int slotAt(int mx, int my) {
        for (int s : new int[]{INFO, BONES, SELL, ARROWS, CLOSE}) {
            if (mx >= sx(s) && mx < sx(s) + 18 && my >= sy(s) && my < sy(s) + 18) return s;
        }
        return -1;
    }

    private void item(DrawContext ctx, ItemStack st, int slot, long count) {
        ctx.drawItem(st, sx(slot) + 1, sy(slot) + 1);
        if (count >= 0) {
            String t = abbr(count);
            ctx.drawText(textRenderer, Text.literal(t), sx(slot) + 17 - textRenderer.getWidth(t), sy(slot) + 9, 0xFFFFFFFF, true);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        FakeSpawners.update(sp);
        ctx.fill(0, 0, width, height, 0x90000000);
        panel(ctx, left, top, 176, 166);
        ctx.drawText(textRenderer, Text.literal("Skeleton Spawner"), left + 8, top + 6, 0xFF404040, false);
        ctx.drawText(textRenderer, Text.literal("Inventory"), left + 8, top + 73, 0xFF404040, false);
        for (int i = 0; i < 27; i++) slotBox(ctx, sx(i), sy(i));

        PlayerInventory inv = client.player.getInventory();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) {
            int x = left + 7 + c * 18, y = top + 84 + r * 18;
            slotBox(ctx, x, y);
            ItemStack st = inv.getStack(9 + r * 9 + c);
            ctx.drawItem(st, x + 1, y + 1);
            ctx.drawStackOverlay(textRenderer, st, x + 1, y + 1);
        }
        for (int c = 0; c < 9; c++) {
            int x = left + 7 + c * 18, y = top + 142;
            slotBox(ctx, x, y);
            ItemStack st = inv.getStack(c);
            ctx.drawItem(st, x + 1, y + 1);
            ctx.drawStackOverlay(textRenderer, st, x + 1, y + 1);
        }

        item(ctx, new ItemStack(Items.SPAWNER), INFO, -1);
        item(ctx, new ItemStack(Items.BONE), BONES, sp.bones);
        item(ctx, new ItemStack(Items.GOLD_INGOT), SELL, -1);
        item(ctx, new ItemStack(Items.ARROW), ARROWS, sp.arrows);
        item(ctx, new ItemStack(Items.BARRIER), CLOSE, -1);

        int hover = slotAt(mouseX, mouseY);
        if (hover >= 0) {
            List<Text> tip = new ArrayList<>();
            long boneValue = sp.bones * FakeSpawners.BONE_PRICE, arrowValue = sp.arrows * FakeSpawners.ARROW_PRICE;
            switch (hover) {
                case INFO -> {
                    tip.add(Text.literal("Skeleton Spawner").formatted(Formatting.YELLOW));
                    tip.add(Text.literal("Drops: bones and arrows").formatted(Formatting.GRAY));
                }
                case BONES -> {
                    tip.add(Text.literal("Bones").formatted(Formatting.WHITE));
                    tip.add(Text.literal("Stored: " + FakeScoreboard.formatMoney(sp.bones)).formatted(Formatting.GRAY));
                    tip.add(Text.literal("Worth: $" + FakeScoreboard.formatMoney(boneValue) + " ($" + FakeSpawners.BONE_PRICE + " each)").formatted(Formatting.GREEN));
                    tip.add(Text.literal("Click to sell").formatted(Formatting.YELLOW));
                }
                case ARROWS -> {
                    tip.add(Text.literal("Arrows").formatted(Formatting.WHITE));
                    tip.add(Text.literal("Stored: " + FakeScoreboard.formatMoney(sp.arrows)).formatted(Formatting.GRAY));
                    tip.add(Text.literal("Worth: $" + FakeScoreboard.formatMoney(arrowValue) + " ($" + FakeSpawners.ARROW_PRICE + " each)").formatted(Formatting.GREEN));
                    tip.add(Text.literal("Click to sell").formatted(Formatting.YELLOW));
                }
                case SELL -> {
                    tip.add(Text.literal("Sell All").formatted(Formatting.GOLD));
                    tip.add(Text.literal("Total: $" + FakeScoreboard.formatMoney(boneValue + arrowValue)).formatted(Formatting.GREEN));
                    tip.add(Text.literal("Click to sell everything").formatted(Formatting.YELLOW));
                }
                default -> tip.add(Text.literal("Close").formatted(Formatting.RED));
            }
            ctx.drawTooltip(textRenderer, tip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int s = slotAt((int) mouseX, (int) mouseY);
        if (s < 0) return super.mouseClicked(mouseX, mouseY, button);
        long earned = 0;
        switch (s) {
            case BONES -> earned = FakeSpawners.sell(sp, true, false);
            case ARROWS -> earned = FakeSpawners.sell(sp, false, true);
            case SELL -> earned = FakeSpawners.sell(sp, true, true);
            case CLOSE -> { close(); return true; }
            default -> { return true; }
        }
        client.getSoundManager().play(PositionedSoundInstance.master(earned > 0 ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client.options.inventoryKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
