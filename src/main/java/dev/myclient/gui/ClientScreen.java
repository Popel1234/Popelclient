package dev.myclient.gui;

import dev.myclient.MyClient;
import dev.myclient.cosmetic.Cosmetic;
import dev.myclient.cosmetic.Cosmetic.Animation;
import dev.myclient.cosmetic.Cosmetic.Type;
import dev.myclient.cosmetic.Palette;
import dev.myclient.macro.Macro;
import dev.myclient.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ClientScreen extends Screen {

    private enum Tab {
        MODS("Mods"), COSMETICS("Cosmetics"), SKIES("Skies"), MACROS("Macros"), EDITOR("Editor");
        final String label;
        Tab(String l) { label = l; }
    }

    // ---- theme ----
    private static final int PANEL_TOP = 0xFF181C33, PANEL_BOT = 0xFF0E1120, BORDER = 0xFF2D3556;
    private static final int CARD = 0xFF1B2038, CARD_HOVER = 0xFF232A48, TILE = 0xFF11152A;
    private static final int ACCENT = 0xFF7C5CFF, ACCENT2 = 0xFF3DD6FF;
    private static final int TEXT = 0xFFEAECF8, MUTED = 0xFF8B92B0, OFF = 0xFF343A58, DANGER = 0xFFFF5470;

    private static Tab lastTab = Tab.MODS;
    private Tab tab;

    private record Hit(int x, int y, int w, int h, Runnable run) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Slider(int x, int y, int w, float min, float max, Consumer<Float> set) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private Slider dragging;
    private final Map<String, Float> anim = new HashMap<>();
    private final float[] scroll = new float[Tab.values().length];

    private int px, py, pw, ph;   // panel
    private int cx, cy, cw, ch;   // content area
    private int clipY0, clipY1;
    private int mx, my;
    private float time;

    // macros
    private Macro capturing;

    // editor
    private TextFieldWidget draftName;
    private String draftNameText = "";
    private Type draftType = Type.HALO;
    private Animation draftAnim = Animation.SPIN;
    private float dr = 124, dg = 92, db = 255, dspeed = 1f;
    private String toast = "";
    private long toastUntil;

    public ClientScreen() {
        super(Text.literal("MyClient"));
        tab = lastTab;
    }

    // ======================================================================== layout / widgets
    @Override
    protected void init() {
        pw = Math.min(width - 24, 580);
        ph = Math.min(height - 24, 370);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 18;
        cy = py + 84;
        cw = pw - 36;
        ch = ph - 84 - 16;
        clearChildren();
        draftName = null;
        if (tab == Tab.MACROS) buildMacroFields();
        if (tab == Tab.EDITOR) buildEditorFields();
    }

    private void buildMacroFields() {
        List<Macro> ms = MyClient.MACROS.macros;
        for (int i = 0; i < ms.size(); i++) {
            Macro m = ms.get(i);
            int ry = cy + 34 + i * 42;
            TextFieldWidget f = new TextFieldWidget(textRenderer, cx + 190, ry + 11, Math.max(60, cw - 300), 14, Text.empty());
            f.setDrawsBackground(false);
            f.setMaxLength(120);
            f.setText(m.command);
            f.setPlaceholder(Text.literal("/command or message"));
            f.setChangedListener(s -> m.command = s);
            addDrawableChild(f);
        }
    }

    private void buildEditorFields() {
        int fx = cx + 8, fw = Math.min(250, cw / 2 - 10);
        draftName = new TextFieldWidget(textRenderer, fx + 12, cy + 18, fw - 24, 14, Text.empty());
        draftName.setDrawsBackground(false);
        draftName.setMaxLength(24);
        draftName.setText(draftNameText);
        draftName.setPlaceholder(Text.literal("My accessory"));
        draftName.setChangedListener(s -> draftNameText = s);
        addDrawableChild(draftName);
    }

    // ======================================================================== small helpers
    private void text(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawText(textRenderer, s, x, y, color, false);
    }

    private void textCentered(DrawContext ctx, String s, int mid, int y, int color) {
        text(ctx, s, mid - textRenderer.getWidth(s) / 2, y, color);
    }

    private void textScaled(DrawContext ctx, String s, int x, int y, float sc, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(sc, sc, 1f);
        ctx.drawText(textRenderer, s, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    private boolean hover(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipY0 && my < clipY1;
    }

    private void addHit(int x, int y, int w, int h, Runnable r) {
        int y0 = Math.max(y, clipY0), y1 = Math.min(y + h, clipY1);
        if (y1 <= y0) return;
        hits.add(new Hit(x, y0, w, y1 - y0, r));
    }

    private float anim(String key, float target) {
        float v = anim.getOrDefault(key, target);
        v += (target - v) * 0.25f;
        anim.put(key, v);
        return v;
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String p : id.split("_")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String nice(Enum<?> e) {
        return e.name().charAt(0) + e.name().substring(1).toLowerCase();
    }

    private void pill(DrawContext ctx, int x, int y, int w, int h, String label, boolean primary, Runnable run) {
        boolean hv = hover(x, y, w, h);
        int base = primary ? (hv ? 0xFF8E72FF : ACCENT) : (hv ? 0xFF2E3660 : 0xFF262C4A);
        Draw.rr(ctx, x, y, w, h, h / 2, base);
        textCentered(ctx, label, x + w / 2, y + (h - 8) / 2, TEXT);
        addHit(x, y, w, h, run);
    }

    /** Real on/off switch with a sliding knob. */
    private void toggle(DrawContext ctx, String key, int x, int y, boolean on, Runnable run) {
        float t = anim(key, on ? 1f : 0f);
        int w = 40, h = 20;
        if (t > 0.05f) Draw.rr(ctx, x - 2, y - 2, w + 4, h + 4, 12, Draw.alpha(0x7C5CFF, (int) (50 * t)));
        Draw.rr(ctx, x, y, w, h, 10, Draw.lerp(OFF, ACCENT, t));
        Draw.circle(ctx, x + 10 + (int) ((w - 20) * t), y + 10, 7, 0xFFFFFFFF);
        addHit(x, y, w, h, run);
    }

    private void slider(DrawContext ctx, String label, int x, int y, int w, float value, float min, float max,
                        String shown, Consumer<Float> setter) {
        text(ctx, label, x, y + 3, MUTED);
        int tx = x + 52, tw = w - 52 - 40;
        float f = (value - min) / (max - min);
        f = Math.max(0f, Math.min(1f, f));
        Draw.rr(ctx, tx, y + 4, tw, 6, 3, OFF);
        Draw.rr(ctx, tx, y + 4, Math.max(6, (int) (tw * f)), 6, 3, ACCENT);
        Draw.circle(ctx, tx + (int) (tw * f), y + 7, 6, 0xFFFFFFFF);
        text(ctx, shown, x + w - 32, y + 3, TEXT);
        sliders.add(new Slider(tx, y, tw, min, max, setter));
    }

    private void updateSlider(Slider s, double mouseX) {
        float f = (float) ((mouseX - s.x()) / s.w());
        f = Math.max(0f, Math.min(1f, f));
        s.set().accept(s.min() + (s.max() - s.min()) * f);
    }

    // ======================================================================== icons (shared preview)
    private void drawIcon(DrawContext ctx, Type type, Animation an, int rgb, int mid, int midY, int size, float speed) {
        float t = time * speed;
        int col = 0xFF000000 | (an == Animation.RAINBOW ? Palette.rainbow(t * 0.2f, 0.75f, 1f) : rgb);
        switch (type) {
            case HALO -> {
                int bob = an == Animation.FLOAT ? (int) (Math.sin(t * 3) * size * 0.06) : 0;
                float sc = an == Animation.PULSE ? 1f + 0.12f * (float) Math.sin(t * 4) : 1f;
                int r = (int) (size * 0.34f * sc);
                int yy = midY + bob;
                Draw.circle(ctx, mid, yy, r + 4, Draw.alpha(col, 60));
                Draw.circle(ctx, mid, yy, r, col);
                Draw.circle(ctx, mid, yy, Math.max(1, r - Math.max(3, size / 12)), TILE);
                if (an == Animation.SPIN) {
                    for (int i = 0; i < 3; i++) {
                        double ang = t * 3 + i * 2.094;
                        Draw.circle(ctx, mid + (int) (Math.cos(ang) * (r - 2)), yy + (int) (Math.sin(ang) * (r - 2)), 2, 0xFFFFFFFF);
                    }
                }
            }
            case HEADBAND -> {
                int w = (int) (size * 0.8f), h = Math.max(6, (int) (size * 0.2f));
                float p = an == Animation.PULSE ? 0.65f + 0.35f * (float) Math.sin(t * 4) : 1f;
                int cc = Draw.scale(col, p);
                int sway = (an == Animation.WAVE || an == Animation.FLOAT) ? (int) (Math.sin(t * 4) * 3) : 1;
                Draw.rr(ctx, mid + w / 2 - 6, midY, 5, size / 3, 2, Draw.scale(cc, 0.85f));
                Draw.rr(ctx, mid + w / 2 - 1 + sway, midY + 2, 5, size / 3, 2, cc);
                Draw.rr(ctx, mid - w / 2, midY - h / 2 - 2, w, h, h / 2, cc);
            }
            case CAPE -> {
                int w = (int) (size * 0.55f), h = (int) (size * 0.85f), top = midY - h / 2;
                for (int r = 0; r < h; r += 2) {
                    float f = r / (float) h;
                    int off = an == Animation.WAVE ? (int) (Math.sin(t * 3 + r * 0.15f) * 3 * f)
                        : an == Animation.FLOAT ? (int) (Math.sin(t * 2) * 2 * f) : 0;
                    int rc = an == Animation.RAINBOW
                        ? 0xFF000000 | Palette.rainbow(t * 0.2f + f * 0.5f, 0.75f, 1f)
                        : Draw.scale(col, 1f - 0.45f * f);
                    int inset = r < 3 ? 3 - r : 0;
                    ctx.fill(mid - w / 2 + off + inset, top + r, mid + w / 2 + off - inset, top + r + 2, rc);
                }
            }
        }
    }

    // ======================================================================== main render
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX; my = mouseY;
        hits.clear();
        sliders.clear();
        clipY0 = 0; clipY1 = height;
        time = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);

        ctx.fillGradient(0, 0, width, height, 0xD0080A14, 0xE8160C2C);

        // panel
        Draw.rr(ctx, px - 1, py - 1, pw + 2, ph + 2, 17, BORDER);
        Draw.rrGrad(ctx, px, py, pw, ph, 16, PANEL_TOP, PANEL_BOT);

        // header
        Draw.circle(ctx, px + 32, py + 26, 11, ACCENT);
        Draw.circle(ctx, px + 32, py + 26, 6, ACCENT2);
        textScaled(ctx, "MY", px + 52, py + 19, 1.5f, TEXT);
        textScaled(ctx, "CLIENT", px + 52 + (int) (textRenderer.getWidth("MY") * 1.5f) + 2, py + 19, 1.5f, ACCENT2);
        text(ctx, "v0.1.0", px + 52, py + 34, MUTED);

        int closeX = px + pw - 28, closeY = py + 26;
        boolean ch2 = hover(closeX - 11, closeY - 11, 22, 22);
        Draw.circle(ctx, closeX, closeY, 11, ch2 ? 0xFF4A2A3C : 0xFF262C4A);
        textCentered(ctx, "x", closeX, closeY - 4, ch2 ? DANGER : MUTED);
        addHit(closeX - 11, closeY - 11, 22, 22, this::close);

        // tabs
        int tx = px + 18;
        for (Tab t : Tab.values()) {
            int w = textRenderer.getWidth(t.label) + 26;
            boolean sel = t == tab;
            boolean hv = hover(tx, py + 50, w, 24);
            if (sel) Draw.rrGrad(ctx, tx, py + 50, w, 24, 12, ACCENT, 0xFF5A3FD6);
            else Draw.rr(ctx, tx, py + 50, w, 24, 12, hv ? 0xFF262D4C : 0xFF1B2038);
            textCentered(ctx, t.label, tx + w / 2, py + 58, sel ? 0xFFFFFFFF : (hv ? TEXT : MUTED));
            final Tab target = t;
            addHit(tx, py + 50, w, 24, () -> { tab = target; lastTab = target; capturing = null; init(); });
            tx += w + 6;
        }

        // content
        clipY0 = cy; clipY1 = cy + ch;
        ctx.enableScissor(cx - 6, cy, cx + cw + 6, cy + ch);
        switch (tab) {
            case MODS -> renderMods(ctx);
            case COSMETICS -> renderCosmetics(ctx);
            case SKIES -> renderSkies(ctx);
            case MACROS -> renderMacros(ctx);
            case EDITOR -> renderEditor(ctx);
        }
        ctx.disableScissor();
        clipY0 = 0; clipY1 = height;
    }

    // ======================================================================== tabs
    private static String desc(Module m) {
        return switch (m.name) {
            case "Fullbright" -> "See everything clearly, even in caves and at night";
            case "Motion Blur" -> "Smooth, cinematic camera movement";
            default -> "";
        };
    }

    private void renderMods(DrawContext ctx) {
        int y = cy;
        for (Module m : MyClient.MODULES.all) {
            int h = 56;
            boolean hv = hover(cx, y, cw, h);
            Draw.rr(ctx, cx, y, cw, h, 14, hv ? CARD_HOVER : CARD);
            Draw.circle(ctx, cx + 30, y + 28, 16, m.isEnabled() ? ACCENT : OFF);
            textCentered(ctx, m.name.substring(0, 1), cx + 30, y + 24, 0xFFFFFFFF);
            text(ctx, m.name, cx + 58, y + 14, TEXT);
            text(ctx, desc(m), cx + 58, y + 30, MUTED);
            if (m == MyClient.MODULES.motionBlur) {
                int nx = cx + 58 + textRenderer.getWidth(m.name) + 8;
                int cwid = textRenderer.getWidth("COMING SOON") + 10;
                Draw.rr(ctx, nx, y + 11, cwid, 14, 7, 0xFF3A2F66);
                text(ctx, "COMING SOON", nx + 5, y + 14, 0xFFC9B8FF);
            }
            addHit(cx, y, cw, h, m::toggle);
            toggle(ctx, "mod:" + m.name, cx + cw - 58, y + 18, m.isEnabled(), m::toggle);
            y += h + 8;
        }
    }

    private void renderCosmetics(DrawContext ctx) {
        List<Cosmetic> list = MyClient.COSMETICS.catalog;
        int cols = cw >= 430 ? 2 : 1, gap = 12;
        int cardW = (cw - gap * (cols - 1)) / cols, cardH = 96;
        int rows = (list.size() + cols - 1) / cols;
        int total = rows * (cardH + 10);
        int idx = tab.ordinal();
        scroll[idx] = Math.max(0, Math.min(scroll[idx], Math.max(0, total - ch)));
        int off = (int) scroll[idx];

        for (int i = 0; i < list.size(); i++) {
            Cosmetic c = list.get(i);
            int x = cx + (i % cols) * (cardW + gap);
            int y = cy + (i / cols) * (cardH + 10) - off;
            if (y + cardH < cy || y > cy + ch) continue;

            boolean on = MyClient.COSMETICS.isEquipped(c);
            boolean hv = hover(x, y, cardW, cardH);
            if (on) Draw.rr(ctx, x - 1, y - 1, cardW + 2, cardH + 2, 15, ACCENT);
            Draw.rr(ctx, x, y, cardW, cardH, 14, hv ? CARD_HOVER : CARD);
            addHit(x, y, cardW, cardH, () -> MyClient.COSMETICS.toggle(c));

            // preview tile
            Draw.rr(ctx, x + 10, y + 13, 70, 70, 12, TILE);
            drawIcon(ctx, c.type, c.animation, Palette.rgb(c), x + 45, y + 48, 54, c.speed);

            text(ctx, pretty(c.id), x + 90, y + 12, TEXT);
            text(ctx, nice(c.type) + "  -  " + nice(c.animation), x + 90, y + 26, MUTED);
            toggle(ctx, "cos:" + c.id, x + cardW - 54, y + 10, on, () -> MyClient.COSMETICS.toggle(c));

            // variant swatches
            for (int v = 0; v < c.variants.size(); v++) {
                String variant = c.variants.get(v);
                int sx = x + 98 + v * 22, sy = y + 54;
                boolean sel = variant.equals(c.activeVariant);
                if (sel) Draw.circle(ctx, sx, sy, 9, 0xFFFFFFFF);
                Draw.circle(ctx, sx, sy, 7, 0xFF000000 | Palette.swatch(c, variant));
                addHit(sx - 9, sy - 9, 18, 18, () -> c.activeVariant = variant);
            }

            int pw2 = Math.min(120, cardW - 100);
            pill(ctx, x + 90, y + 70, pw2, 18, "Anim: " + nice(c.animation), false, () ->
                c.animation = Animation.values()[(c.animation.ordinal() + 1) % Animation.values().length]);

            if (c.custom) {
                int dx = x + cardW - 20, dy = y + cardH - 16;
                Draw.circle(ctx, dx, dy, 9, 0xFF3A2433);
                textCentered(ctx, "x", dx, dy - 4, DANGER);
                addHit(dx - 9, dy - 9, 18, 18, () -> {
                    MyClient.COSMETICS.equipped.remove(c.id);
                    MyClient.COSMETICS.catalog.remove(c);
                });
            }
        }

        if (total > ch) {
            int barH = Math.max(24, (int) ((long) ch * ch / total));
            int barY = cy + (int) ((ch - barH) * (scroll[idx] / Math.max(1, total - ch)));
            Draw.rr(ctx, cx + cw + 2, barY, 4, barH, 2, 0xFF3A4166);
        }
    }

    private static int[] skyColors(String name) {
        return switch (name) {
            case "Sunset" -> new int[]{0xFFFF7A59, 0xFF3B2A6B};
            case "Galaxy" -> new int[]{0xFF0B0B2E, 0xFF6A2AA0};
            case "Aurora" -> new int[]{0xFF0B2F3B, 0xFF2AF598};
            default -> new int[]{0xFF78A7FF, 0xFFC8E0FF};
        };
    }

    private void renderSkies(DrawContext ctx) {
        text(ctx, "Sky rendering is coming soon - your choice is only saved for now.", cx + 2, cy + 2, MUTED);
        int cols = cw >= 430 ? 2 : 1, gap = 12, top = cy + 22;
        int cardW = (cw - gap * (cols - 1)) / cols, cardH = 74;
        List<String> skies = MyClient.COSMETICS.skies;
        for (int i = 0; i < skies.size(); i++) {
            String s = skies.get(i);
            int x = cx + (i % cols) * (cardW + gap), y = top + (i / cols) * (cardH + 10);
            boolean sel = s.equals(MyClient.COSMETICS.activeSky);
            boolean hv = hover(x, y, cardW, cardH);
            if (sel) Draw.rr(ctx, x - 1, y - 1, cardW + 2, cardH + 2, 15, ACCENT);
            Draw.rr(ctx, x, y, cardW, cardH, 14, hv ? CARD_HOVER : CARD);
            int[] cc = skyColors(s);
            Draw.rrGrad(ctx, x + 10, y + 10, 90, cardH - 20, 10, cc[0], cc[1]);
            text(ctx, s, x + 112, y + 20, TEXT);
            text(ctx, sel ? "Selected" : "Click to select", x + 112, y + 36, sel ? ACCENT2 : MUTED);
            Draw.circle(ctx, x + cardW - 24, y + cardH / 2, 9, sel ? ACCENT : OFF);
            if (sel) Draw.circle(ctx, x + cardW - 24, y + cardH / 2, 4, 0xFFFFFFFF);
            addHit(x, y, cardW, cardH, () -> MyClient.COSMETICS.activeSky = s);
        }
    }

    private String keyLabel(int code) {
        try {
            return InputUtil.fromKeyCode(code, 0).getLocalizedText().getString().toUpperCase();
        } catch (Exception e) {
            return "KEY " + code;
        }
    }

    private void renderMacros(DrawContext ctx) {
        List<Macro> ms = MyClient.MACROS.macros;
        textScaled(ctx, "Macros", cx + 2, cy + 4, 1.3f, TEXT);
        if (ms.size() < 5) {
            pill(ctx, cx + cw - 116, cy, 116, 22, "+ New macro", true, () -> {
                ms.add(new Macro("Macro " + (ms.size() + 1), GLFW.GLFW_KEY_G, "", 0));
                init();
            });
        } else {
            text(ctx, "Max 5 macros", cx + cw - 80, cy + 7, MUTED);
        }
        if (ms.isEmpty()) {
            textCentered(ctx, "No macros yet - click \"+ New macro\".", cx + cw / 2, cy + 90, MUTED);
        }
        for (int i = 0; i < ms.size(); i++) {
            Macro m = ms.get(i);
            int ry = cy + 34 + i * 42;
            Draw.rr(ctx, cx, ry, cw, 36, 12, CARD);
            text(ctx, m.name, cx + 14, ry + 14, TEXT);
            pill(ctx, cx + 104, ry + 7, 68, 22, capturing == m ? "press key" : keyLabel(m.keyCode), capturing == m, () -> capturing = m);
            Draw.rr(ctx, cx + 180, ry + 7, Math.max(80, cw - 280), 22, 11, TILE);
            toggle(ctx, "mac:" + i + ":" + System.identityHashCode(m), cx + cw - 86, ry + 8, m.enabled, () -> m.enabled = !m.enabled);
            int dx = cx + cw - 24, dy = ry + 18;
            boolean hv = hover(dx - 10, dy - 10, 20, 20);
            Draw.circle(ctx, dx, dy, 10, hv ? 0xFF4A2A3C : 0xFF2A2236);
            textCentered(ctx, "x", dx, dy - 4, DANGER);
            addHit(dx - 10, dy - 10, 20, 20, () -> { ms.remove(m); init(); });
        }
    }

    private void renderEditor(DrawContext ctx) {
        int fx = cx + 8, fw = Math.min(250, cw / 2 - 10);
        text(ctx, "Name", fx, cy + 2, MUTED);
        Draw.rr(ctx, fx, cy + 14, fw, 22, 11, TILE);

        int half = (fw - 8) / 2;
        pill(ctx, fx, cy + 48, half, 22, "Type: " + nice(draftType), false,
            () -> draftType = Type.values()[(draftType.ordinal() + 1) % Type.values().length]);
        pill(ctx, fx + half + 8, cy + 48, half, 22, "Anim: " + nice(draftAnim), false,
            () -> draftAnim = Animation.values()[(draftAnim.ordinal() + 1) % Animation.values().length]);

        slider(ctx, "Red", fx, cy + 88, fw, dr, 0, 255, String.valueOf((int) dr), v -> dr = v);
        slider(ctx, "Green", fx, cy + 112, fw, dg, 0, 255, String.valueOf((int) dg), v -> dg = v);
        slider(ctx, "Blue", fx, cy + 136, fw, db, 0, 255, String.valueOf((int) db), v -> db = v);
        slider(ctx, "Speed", fx, cy + 160, fw, dspeed, 0.2f, 3f, String.format("%.1fx", dspeed), v -> dspeed = v);

        pill(ctx, fx, cy + 190, fw, 28, "Create accessory", true, this::createDraft);
        if (System.currentTimeMillis() < toastUntil) text(ctx, toast, fx, cy + 226, ACCENT2);

        // live preview
        int tx = cx + fw + 24, tw = cw - fw - 32, th = 200;
        Draw.rr(ctx, tx, cy + 4, tw, th, 16, TILE);
        int rgb = ((int) dr << 16) | ((int) dg << 8) | (int) db;
        drawIcon(ctx, draftType, draftAnim, rgb, tx + tw / 2, cy + 4 + th / 2 - 8, Math.min(tw, th) - 50, dspeed);
        textCentered(ctx, "Live preview", tx + tw / 2, cy + th - 8, MUTED);
    }

    private void createDraft() {
        String name = draftNameText.trim();
        if (name.isEmpty()) { toast("Enter a name first"); return; }
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (slug.isEmpty()) slug = "accessory";
        String id = "custom_" + slug;
        int n = 2;
        final String base = id;
        while (true) {
            final String tryId = id;
            if (MyClient.COSMETICS.catalog.stream().noneMatch(c -> c.id.equals(tryId))) break;
            id = base + "_" + n++;
        }
        Cosmetic c = new Cosmetic(id, draftType, List.of("custom"), draftAnim);
        c.custom = true;
        c.tintRgb = ((int) dr << 16) | ((int) dg << 8) | (int) db;
        c.speed = dspeed;
        MyClient.COSMETICS.catalog.add(c);
        toast("Created '" + name + "' - equip it in Cosmetics");
    }

    private void toast(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 3500;
    }

    // ======================================================================== input
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.in(mouseX, mouseY)) {
                    setFocused(null);
                    h.run().run();
                    return true;
                }
            }
            for (Slider s : sliders) {
                if (mouseX >= s.x() - 6 && mouseX <= s.x() + s.w() + 6 && mouseY >= s.y() && mouseY < s.y() + 14) {
                    dragging = s;
                    updateSlider(s, mouseX);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null) { updateSlider(dragging, mouseX); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (tab == Tab.COSMETICS) {
            scroll[tab.ordinal()] -= (float) vertical * 22f;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing != null) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) capturing.keyCode = keyCode;
            capturing = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT && !(getFocused() instanceof TextFieldWidget tf && tf.isFocused())) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
