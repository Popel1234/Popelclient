package dev.myclient.fake;

import dev.myclient.Settings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A scoreboard on the right side of the screen that only exists on your screen (fake money and all). */
public final class FakeScoreboard {
    private FakeScoreboard() {}

    public static String formatMoney(double v) { return String.format(Locale.US, "%,.0f", v); }

    /** 1.2M, 340K, 1.5B ... */
    public static String compact(double v) {
        String[] suf = {"", "K", "M", "B", "T"};
        int i = 0;
        double d = v;
        while (d >= 1000 && i < suf.length - 1) { d /= 1000; i++; }
        if (i == 0) return String.format(Locale.US, "%,.0f", d);
        String s = String.format(Locale.US, "%.1f", d);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + suf[i];
    }

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

    /** Colour codes: &a &l &r ... and hex colours like &#FF8800. */
    public static Text parse(String s) {
        MutableText out = Text.empty();
        StringBuilder cur = new StringBuilder();
        Style style = Style.EMPTY;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '#' && i + 8 <= s.length()) {
                    try {
                        int rgb = Integer.parseInt(s.substring(i + 2, i + 8), 16);
                        if (cur.length() > 0) { out.append(Text.literal(cur.toString()).setStyle(style)); cur.setLength(0); }
                        style = style.withColor(rgb);
                        i += 7;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }
                Formatting f = Formatting.byCode(n);
                if (f != null) {
                    if (cur.length() > 0) { out.append(Text.literal(cur.toString()).setStyle(style)); cur.setLength(0); }
                    if (f == Formatting.RESET) style = Style.EMPTY;
                    else if (f.isColor()) style = Style.EMPTY.withColor(f);
                    else style = style.withFormatting(f);
                    i++;
                    continue;
                }
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.append(Text.literal(cur.toString()).setStyle(style));
        return out;
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Title: animated gradient + bold, unless the title uses & colour codes itself. */
    private static Text title() {
        String t = Settings.fakeTitle;
        if (t.indexOf('&') >= 0) return parse(t);
        MutableText out = Text.empty();
        float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0e9f;
        for (int i = 0; i < t.length(); i++) {
            float k = 0.5f + 0.5f * (float) Math.sin(time * 1.4f + i * 0.45f);
            out.append(Text.literal(String.valueOf(t.charAt(i))).setStyle(Style.EMPTY.withBold(true).withColor(lerp(Settings.titleFrom, Settings.titleTo, k))));
        }
        return out;
    }

    public static void render(DrawContext ctx) {
        if (!Settings.fakeScoreboard) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        List<Text> lines = new ArrayList<>();
        List<String> raw = new ArrayList<>(Settings.fakeLines);
        while (!raw.isEmpty() && raw.get(raw.size() - 1).isBlank()) raw.remove(raw.size() - 1);
        for (String l : raw) lines.add(parse(l.replace("{money_full}", formatMoney(Settings.fakeMoney)).replace("{money}", compact(Settings.fakeMoney))));
        Text title = title();
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
