package dev.myclient.fake;

import dev.myclient.Settings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A scoreboard on the right side of the screen that only exists on your screen (fake money and all). */
public final class FakeScoreboard {
    private FakeScoreboard() {}

    public static String formatMoney(double v) { return String.format(Locale.US, "%,.0f", v); }

    /** Understands 1500, 1,500, 1.5k, 2m, 3b. Returns null if it is not a number. */
    public static Double parseAmount(String s) {
        try {
            s = s.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
            double mult = 1;
            if (s.endsWith("k")) { mult = 1e3; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("m")) { mult = 1e6; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("b")) { mult = 1e9; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("t")) { mult = 1e12; s = s.substring(0, s.length() - 1); }
            return Double.parseDouble(s) * mult;
        } catch (Exception e) {
            return null;
        }
    }

    private static Text text(String s) {
        return Text.literal(s.replace('&', '\u00a7'));
    }

    public static void render(DrawContext ctx) {
        if (!Settings.fakeScoreboard) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        List<Text> lines = new ArrayList<>();
        List<String> raw = new ArrayList<>(Settings.fakeLines);
        while (!raw.isEmpty() && raw.get(raw.size() - 1).isBlank()) raw.remove(raw.size() - 1);
        for (String l : raw) lines.add(text(l.replace("{money}", formatMoney(Settings.fakeMoney))));
        Text title = text(Settings.fakeTitle);
        int w = tr.getWidth(title);
        for (Text t : lines) w = Math.max(w, tr.getWidth(t));
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int n = lines.size();
        int bottom = sh / 2 + n * 9 / 3, top = bottom - n * 9;
        int x2 = sw - 3, x1 = x2 - w - 4;
        ctx.fill(x1 - 2, top - 10, x2, top - 1, 0x66000000);
        ctx.fill(x1 - 2, top - 1, x2, bottom, 0x50000000);
        ctx.drawText(tr, title, x1 + (w + 4 - tr.getWidth(title)) / 2, top - 9, 0xFFFFFFFF, false);
        for (int i = 0; i < n; i++) ctx.drawText(tr, lines.get(i), x1, top + i * 9, 0xFFFFFFFF, false);
    }
}
