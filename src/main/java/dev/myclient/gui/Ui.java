package dev.myclient.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Smooth TrueType UI text (font defined in assets/myclient/font/ui.json). */
public final class Ui {
    private Ui() {}

    public static final Identifier FONT = Identifier.of("myclient", "ui");
    private static final Style STYLE = Style.EMPTY.withFont(FONT);

    public static Text t(String s) { return Text.literal(s).setStyle(STYLE); }

    public static int width(String s) { return MinecraftClient.getInstance().textRenderer.getWidth(t(s)); }

    public static String trim(String s, int maxW) {
        if (width(s) <= maxW) return s;
        while (s.length() > 1 && width(s + "...") > maxW) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    /** Draws text at GUI coordinates while the supersampled matrix from Draw.begin() is active. */
    public static void text(DrawContext ctx, String s, float x, float y, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x * Draw.ss * Draw.k, y * Draw.ss * Draw.k, 0);
        ctx.getMatrices().scale(Draw.ss * Draw.k, Draw.ss * Draw.k, 1f);
        ctx.drawText(MinecraftClient.getInstance().textRenderer, t(s), 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    public static void centered(DrawContext ctx, String s, float mid, float y, int color) {
        text(ctx, s, mid - width(s) / 2f, y, color);
    }
}
