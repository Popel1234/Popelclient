package dev.myclient.gui;

import dev.myclient.Config;
import dev.myclient.MyClient;
import dev.myclient.builder.SchematicBuilder;
import dev.myclient.cosmetic.Cosmetic;
import dev.myclient.cosmetic.Cosmetic.Animation;
import dev.myclient.cosmetic.Cosmetic.Type;
import dev.myclient.cosmetic.Palette;
import dev.myclient.macro.Macro;
import dev.myclient.module.Module;
import dev.myclient.texture.TextureLab;
import dev.myclient.util.FilePicker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Meteor-style GUI: small floating windows you can drag, collapse, open and close. */
public class ClientScreen extends Screen {

    public enum Tab {
        MODS("Mods", 200, 160), COSMETICS("Cosmetics", 250, 280), SKIES("Skies", 240, 240), MACROS("Macros", 260, 220),
        EDITOR("Editor", 260, 310), TEXTURES("Textures", 290, 340), BUILDER("Builder", 290, 310),
        TRACERS("Tracers", 260, 320), SEARCH("Search", 260, 310);
        final String label;
        final int w, h;
        Tab(String l, int w, int h) { label = l; this.w = w; this.h = h; }
    }

    public static final class Win {
        Tab tab;
        float x, y;
        int w, h;
        boolean open, collapsed;
        float scroll;
        int contentH;
        int lx, ly, lw, lh, listScroll;   // inner list rectangle + scroll
    }

    private enum Tool {
        PENCIL("Pencil"), ERASER("Eraser"), FILL("Fill"), PICK("Pick color");
        final String label;
        Tool(String l) { label = l; }
    }

    private static final int TITLE = 26;
    private static final Type[] EDITABLE = {Type.HALO, Type.HEADBAND, Type.CAPE, Type.WINGS, Type.GLASSES, Type.AURA};

    // ---- theme ----
    private static final int PANEL_TOP = 0xF0181C33, PANEL_BOT = 0xF00E1120, BORDER = 0xFF2D3556;
    private static final int CARD = 0xFF1B2038, CARD_HOVER = 0xFF232A48, TILE = 0xFF11152A;
    private static final int ACCENT = 0xFF7C5CFF, ACCENT2 = 0xFF3DD6FF;
    private static final int TEXT = 0xFFEAECF8, MUTED = 0xFF8B92B0, OFF = 0xFF343A58, DANGER = 0xFFFF5470;

    // ---- persistent (static) state ----
    private static final List<Win> WINS = new ArrayList<>();
    private static final TextBox texBox = new TextBox("Search any block...");
    private static final TextBox schemBox = new TextBox("Search schematics...");
    private static final TextBox tracerBox = new TextBox("Search blocks...");
    private static final TextBox searchBox = new TextBox("Search everything...");
    private static final TextBox nameBox = new TextBox("My accessory");
    private static final Map<Macro, TextBox> macroBoxes = new IdentityHashMap<>();
    private static TextBox focus;

    private static String selTex;
    private static Tool tool = Tool.PENCIL;
    private static float texR = 255, texG = 255, texB = 255;
    private static List<String> texFiltered;
    private static List<String> blockIds;
    private static List<String> blockFiltered;
    private static List<Path> schemFiles = new ArrayList<>();
    private static List<Path> schemFiltered = new ArrayList<>();

    private static Type draftType = Type.HALO;
    private static Animation draftAnim = Animation.SPIN;
    private static float dr = 124, dg = 92, db = 255, dspeed = 1f;

    static {
        texBox.onChange = s -> refilterTextures();
        tracerBox.onChange = s -> refilterBlocks();
        schemBox.onChange = s -> refilterSchematics();
    }

    // ---- per-screen state ----
    private record Hit(Win win, int x, int y, int w, int h, Runnable run) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Slider(Win win, int x, int y, int w, float min, float max, Consumer<Float> set) {}
    private record BoxRect(Win win, int x, int y, int w, int h, TextBox box) {}
    private record Result(String cat, String label, boolean on, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final List<BoxRect> boxes = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private Slider dragging;
    private Win dragWin;
    private float dragOffX, dragOffY;
    private Win curWin, topAtMouse;
    private int cx, cy, cw, ch, clipY0, clipY1, mx, my;
    private float time;
    private String toast = "";
    private long toastUntil;
    private Macro capturing;
    private int gx, gy, gcell, gsize;
    private boolean painting;

    public ClientScreen() {
        super(Text.literal("MyClient"));
    }

    // ======================================================================== windows
    private static void ensureWindows() {
        if (!WINS.isEmpty()) return;
        List<Config.WinData> saved = Config.data == null ? null : Config.data.windows;
        if (saved != null) {
            for (Config.WinData d : saved) {
                try {
                    Win w = new Win();
                    w.tab = Tab.valueOf(d.tab);
                    w.x = d.x; w.y = d.y; w.w = d.w > 100 ? d.w : w.tab.w; w.h = d.h > 60 ? d.h : w.tab.h;
                    w.open = d.open; w.collapsed = d.collapsed;
                    WINS.add(w);
                } catch (Exception ignored) {}
            }
        }
        int i = 0;
        for (Tab t : Tab.values()) {
            final Tab tt = t;
            if (WINS.stream().noneMatch(w -> w.tab == tt)) {
                Win w = new Win();
                w.tab = t; w.w = t.w; w.h = t.h;
                w.x = 24 + (t.ordinal() % 4) * 40 + i * 4;
                w.y = 44 + (t.ordinal() % 4) * 30;
                w.open = t == Tab.MODS || t == Tab.COSMETICS;
                if (t == Tab.COSMETICS) w.x = 240;
                WINS.add(w);
            }
            i++;
        }
    }

    public static List<Config.WinData> snapshot() {
        List<Config.WinData> out = new ArrayList<>();
        for (Win w : WINS) {
            Config.WinData d = new Config.WinData();
            d.tab = w.tab.name(); d.x = w.x; d.y = w.y; d.w = w.w; d.h = w.h; d.open = w.open; d.collapsed = w.collapsed;
            out.add(d);
        }
        return out;
    }

    private static Win win(Tab t) {
        for (Win w : WINS) if (w.tab == t) return w;
        return null;
    }

    private void openWindow(Tab t) {
        Win w = win(t);
        if (w == null) return;
        w.open = true;
        w.collapsed = false;
        WINS.remove(w);
        WINS.add(w);
    }

    @Override
    protected void init() {
        ensureWindows();
        for (Win w : WINS) {
            w.x = Math.max(0, Math.min(w.x, Math.max(0, width - w.w)));
            w.y = Math.max(0, Math.min(w.y, Math.max(0, height - TITLE)));
        }
        schemFiles = SchematicBuilder.listFiles();
        refilterSchematics();
        if (texFiltered == null) refilterTextures();
        if (blockFiltered == null) refilterBlocks();
    }

    @Override
    public void removed() {
        focus = null;
        Config.save();
    }

    private static void refilterTextures() {
        String q = texBox.text.toLowerCase().trim();
        texFiltered = new ArrayList<>();
        for (String n : TextureLab.names()) if (q.isEmpty() || n.contains(q)) texFiltered.add(n);
        Win w = win(Tab.TEXTURES);
        if (w != null) w.listScroll = 0;
        if (selTex == null && !texFiltered.isEmpty()) selTex = texFiltered.contains("stone") ? "stone" : texFiltered.get(0);
    }

    private static List<String> allBlocks() {
        if (blockIds == null) {
            blockIds = new ArrayList<>();
            for (Identifier id : Registries.BLOCK.getIds()) {
                String s = id.toString();
                if (!s.endsWith("air")) blockIds.add(s);
            }
            Collections.sort(blockIds);
        }
        return blockIds;
    }

    private static void refilterBlocks() {
        String q = tracerBox.text.toLowerCase().trim().replace(' ', '_');
        blockFiltered = new ArrayList<>();
        for (String s : allBlocks()) if (q.isEmpty() || s.contains(q)) blockFiltered.add(s);
        Win w = win(Tab.TRACERS);
        if (w != null) w.listScroll = 0;
    }

    private static void refilterSchematics() {
        String q = schemBox.text.toLowerCase().trim();
        schemFiltered = new ArrayList<>();
        for (Path p : schemFiles) if (q.isEmpty() || p.getFileName().toString().toLowerCase().contains(q)) schemFiltered.add(p);
        Win w = win(Tab.BUILDER);
        if (w != null) w.listScroll = 0;
    }

    // ======================================================================== helpers
    private boolean hover(int x, int y, int w, int h) {
        return curWin == topAtMouse && mx >= x && mx < x + w && my >= y && my < y + h && my >= clipY0 && my < clipY1;
    }

    private void addHit(int x, int y, int w, int h, Runnable r) {
        int y0 = Math.max(y, clipY0), y1 = Math.min(y + h, clipY1);
        if (y1 <= y0) return;
        hits.add(new Hit(curWin, x, y0, w, y1 - y0, r));
    }

    private float anim(String key, float target) {
        float v = anim.getOrDefault(key, target);
        v += (target - v) * 0.25f;
        anim.put(key, v);
        return v;
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String p : id.replace(':', '_').split("_")) {
            if (p.isEmpty() || p.equals("minecraft")) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String nice(Enum<?> e) { return e.name().charAt(0) + e.name().substring(1).toLowerCase(); }

    private void toast(String s) { toast = s; toastUntil = System.currentTimeMillis() + 4000; }

    private List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (Ui.width(test) > width && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(test);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private void text(DrawContext ctx, String s, float x, float y, int c) { Ui.text(ctx, s, x, y, c); }

    private void centered(DrawContext ctx, String s, float mid, float y, int c) { Ui.centered(ctx, s, mid, y, c); }

    private void pill(DrawContext ctx, int x, int y, int w, int h, String label, boolean primary, Runnable run) {
        boolean hv = hover(x, y, w, h);
        int base = primary ? (hv ? 0xFF8E72FF : ACCENT) : (hv ? 0xFF2E3660 : 0xFF262C4A);
        Draw.rr(ctx, x, y, w, h, h / 2f, base);
        centered(ctx, Ui.trim(label, w - 8), x + w / 2f, y + (h - 9) / 2f, TEXT);
        addHit(x, y, w, h, run);
    }

    private void toggle(DrawContext ctx, String key, int x, int y, boolean on, Runnable run) {
        float t = anim(key, on ? 1f : 0f);
        int w = 36, h = 18;
        if (t > 0.05f) Draw.rr(ctx, x - 2, y - 2, w + 4, h + 4, 11, Draw.alpha(0x7C5CFF, (int) (50 * t)));
        Draw.rr(ctx, x, y, w, h, 9, Draw.lerp(OFF, ACCENT, t));
        Draw.circle(ctx, x + 9 + (w - 18) * t, y + 9, 6.5f, 0xFFFFFFFF);
        addHit(x, y, w, h, run);
    }

    private void slider(DrawContext ctx, String label, int x, int y, int w, float value, float min, float max,
                        String shown, Consumer<Float> setter) {
        text(ctx, label, x, y + 2, MUTED);
        int tx = x + 44, tw = w - 44 - 34;
        float f = Math.max(0f, Math.min(1f, (value - min) / (max - min)));
        Draw.rr(ctx, tx, y + 4, tw, 6, 3, OFF);
        Draw.rr(ctx, tx, y + 4, Math.max(6, tw * f), 6, 3, ACCENT);
        Draw.circle(ctx, tx + tw * f, y + 7, 6, 0xFFFFFFFF);
        text(ctx, shown, x + w - 28, y + 2, TEXT);
        sliders.add(new Slider(curWin, tx, y, tw, min, max, setter));
    }

    private void updateSlider(Slider s, double mouseX) {
        float f = Math.max(0f, Math.min(1f, (float) ((mouseX - s.x()) / s.w())));
        s.set().accept(s.min() + (s.max() - s.min()) * f);
    }

    private void box(DrawContext ctx, TextBox b, int x, int y, int w, int h) {
        boolean f = focus == b;
        Draw.rr(ctx, x - 1, y - 1, w + 2, h + 2, (h + 2) / 2f, f ? ACCENT : 0xFF2A3050);
        Draw.rr(ctx, x, y, w, h, h / 2f, TILE);
        int avail = w - 22;
        int start = 0;
        while (start < b.cursor && Ui.width(b.text.substring(start, b.cursor)) > avail) start++;
        int end = b.text.length();
        while (end > start && Ui.width(b.text.substring(start, end)) > avail) end--;
        String shown = b.text.substring(start, end);
        float ty = y + (h - 9) / 2f;
        if (b.text.isEmpty()) text(ctx, b.placeholder, x + 11, ty, MUTED);
        else text(ctx, shown, x + 11, ty, TEXT);
        if (f && (System.currentTimeMillis() / 500) % 2 == 0) {
            float cxp = x + 11 + Ui.width(b.text.substring(start, Math.min(b.cursor, end)));
            Draw.rect(ctx, cxp, y + 4, 1, h - 8, TEXT);
        }
        int y0 = Math.max(y, clipY0), y1 = Math.min(y + h, clipY1);
        if (y1 > y0) boxes.add(new BoxRect(curWin, x, y0, w, y1 - y0, b));
    }

    private void drawImage(DrawContext ctx, BufferedImage img, int size, float x, float y, float p, boolean checker) {
        for (int j = 0; j < size; j++) {
            int i = 0;
            while (i < size) {
                int col = img.getRGB(i, j);
                int a = col >>> 24;
                if (a == 255) {
                    int k = i + 1;
                    while (k < size && img.getRGB(k, j) == col) k++;
                    Draw.rect(ctx, x + i * p, y + j * p, (k - i) * p, p, col);
                    i = k;
                } else {
                    if (checker) Draw.rect(ctx, x + i * p, y + j * p, p, p, ((i + j) & 1) == 0 ? 0xFF2A2F4A : 0xFF353B5C);
                    if (a > 0) Draw.rect(ctx, x + i * p, y + j * p, p, p, col);
                    i++;
                }
            }
        }
    }

    // ======================================================================== icons
    private void drawIcon(DrawContext ctx, Type type, Animation an, int rgb, float mid, float midY, int size, float speed) {
        float t = time * speed;
        int col = 0xFF000000 | (an == Animation.RAINBOW ? Palette.rainbow(t * 0.2f, 0.75f, 1f) : rgb);
        switch (type) {
            case HALO -> {
                float bob = an == Animation.FLOAT ? (float) Math.sin(t * 3) * size * 0.06f : 0;
                float sc = an == Animation.PULSE ? 1f + 0.12f * (float) Math.sin(t * 4) : 1f;
                float r = size * 0.34f * sc, yy = midY + bob;
                Draw.circle(ctx, mid, yy, r + 4, Draw.alpha(col, 60));
                Draw.circle(ctx, mid, yy, r, col);
                Draw.circle(ctx, mid, yy, Math.max(1, r - Math.max(3, size / 12f)), TILE);
                for (int i = 0; i < 3; i++) {
                    double ang = t * 3 + i * 2.094;
                    Draw.circle(ctx, mid + (float) Math.cos(ang) * (r - 2), yy + (float) Math.sin(ang) * (r - 2), 1.8f, 0xFFFFFFFF);
                }
            }
            case HEADBAND -> {
                float w = size * 0.8f, h = Math.max(6, size * 0.22f);
                float p = an == Animation.PULSE ? 0.65f + 0.35f * (float) Math.sin(t * 4) : 1f;
                int cc = Draw.scale(col, p);
                float sway = (an == Animation.WAVE || an == Animation.FLOAT) ? (float) Math.sin(t * 4) * 3 : 1;
                Draw.rr(ctx, mid + w / 2 - 6, midY, 5, size / 3f, 2, Draw.scale(cc, 0.85f));
                Draw.rr(ctx, mid + w / 2 - 1 + sway, midY + 2, 5, size / 3f, 2, cc);
                Draw.rr(ctx, mid - w / 2, midY - h / 2 - 2, w, h, h / 2, cc);
            }
            case CAPE -> {
                float w = size * 0.55f, h = size * 0.85f, top = midY - h / 2;
                for (int r = 0; r < h; r += 2) {
                    float f = r / h;
                    float off = an == Animation.WAVE ? (float) Math.sin(t * 3 + r * 0.15f) * 3 * f
                        : an == Animation.FLOAT ? (float) Math.sin(t * 2) * 2 * f : 0;
                    int rc = an == Animation.RAINBOW ? 0xFF000000 | Palette.rainbow(t * 0.2f + f * 0.5f, 0.75f, 1f) : Draw.scale(col, 1f - 0.45f * f);
                    float inset = r < 3 ? 3 - r : 0;
                    Draw.rect(ctx, mid - w / 2 + off + inset, top + r, w - inset * 2, 2, rc);
                }
            }
            case WINGS -> {
                float flap = an == Animation.NONE ? 0 : (float) Math.sin(t * 3.2f) * 10;
                for (int sd = -1; sd <= 1; sd += 2) {
                    for (int k = 0; k < 5; k++) {
                        double ang = Math.toRadians(-55 + 24 * k + flap);
                        float len = size * (0.28f + 0.1f * (float) Math.sin(Math.PI * (k + 0.5) / 5));
                        for (float s = 3; s < len; s += 2) {
                            Draw.circle(ctx, mid + sd * (4 + (float) Math.cos(ang) * s), midY - 3 + (float) Math.sin(ang) * s, 1.7f, Draw.scale(col, 0.6f + 0.4f * s / len));
                        }
                    }
                }
            }
            case GLASSES -> {
                float lw = size * 0.36f, lh = size * 0.26f;
                for (int sd = -1; sd <= 1; sd += 2) {
                    float lx = mid + sd * size * 0.22f - lw / 2;
                    Draw.rr(ctx, lx, midY - lh / 2, lw, lh, 5, col);
                    Draw.rr(ctx, lx + 2, midY - lh / 2 + 2, lw - 4, lh - 4, 3, Draw.alpha(0x05050A, 220));
                }
                Draw.rect(ctx, mid - 3, midY - 2, 6, 2, col);
            }
            case AURA -> {
                float pulse = 1f + 0.08f * (float) Math.sin(t * 3);
                Draw.circle(ctx, mid, midY, size * 0.46f * pulse, Draw.alpha(col, 35));
                Draw.circle(ctx, mid, midY, size * 0.36f * pulse, Draw.alpha(col, 55));
                Draw.circle(ctx, mid, midY, size * 0.24f * pulse, Draw.alpha(col, 90));
                Draw.circle(ctx, mid, midY, size * 0.1f, 0xFFFFFFFF);
            }
            case TRAIL -> {
                for (int k = 0; k < 14; k++) {
                    float f = k / 13f;
                    int cc = rgb == 0xFF55FF ? 0xFF000000 | Palette.rainbow(f - t * 0.3f, 0.7f, 1f) : col;
                    float yy = midY + size * 0.12f - f * size * 0.24f + (float) Math.sin(t * 3 + f * 5) * 3;
                    Draw.circle(ctx, mid - size * 0.4f + f * size * 0.8f, yy, 0.8f + f * 3f, Draw.alpha(cc, 40 + (int) (215 * f)));
                }
            }
        }
    }

    // ======================================================================== main render
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        hits.clear();
        sliders.clear();
        boxes.clear();
        gcell = 0;
        time = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);
        float s = Math.max(2f, Math.min(4f, (float) client.getWindow().getScaleFactor()));

        topAtMouse = null;
        for (int i = WINS.size() - 1; i >= 0; i--) {
            Win w = WINS.get(i);
            if (!w.open) continue;
            int hh = w.collapsed ? TITLE : w.h;
            if (mouseX >= w.x && mouseX < w.x + w.w && mouseY >= w.y && mouseY < w.y + hh) { topAtMouse = w; break; }
        }

        // dock
        curWin = null;
        clipY0 = 0;
        clipY1 = height;
        Draw.begin(ctx, s);
        ctx.fillGradient(0, 0, Math.round(width * s), Math.round(height * s), 0x60060812, 0x90160C2C);
        int total = 0;
        for (Tab t : Tab.values()) total += Ui.width(t.label) + 18 + 4;
        int dx = (width - total) / 2;
        Draw.rr(ctx, dx - 6, 3, total + 8, 26, 13, 0xE0141830);
        int tx = dx;
        for (Tab t : Tab.values()) {
            int w = Ui.width(t.label) + 18;
            Win win = win(t);
            boolean open = win != null && win.open;
            boolean hv = hover(tx, 7, w, 18);
            if (open) Draw.rrGrad(ctx, tx, 7, w, 18, 9, ACCENT, 0xFF5A3FD6);
            else Draw.rr(ctx, tx, 7, w, 18, 9, hv ? 0xFF262D4C : 0xFF1B2038);
            centered(ctx, t.label, tx + w / 2f, 11, open ? 0xFFFFFFFF : (hv ? TEXT : MUTED));
            final Tab target = t;
            addHit(tx, 7, w, 18, () -> {
                Win ww = win(target);
                if (ww.open && WINS.get(WINS.size() - 1) == ww) ww.open = false;
                else openWindow(target);
            });
            tx += w + 4;
        }
        Draw.end(ctx);

        for (Win w : new ArrayList<>(WINS)) if (w.open) renderWindow(ctx, w, s);
        curWin = null;
        clipY0 = 0;
        clipY1 = height;

        if (System.currentTimeMillis() < toastUntil) {
            Draw.begin(ctx, s);
            int tw = Ui.width(toast) + 26;
            Draw.rr(ctx, width / 2f - tw / 2f, height - 34, tw, 22, 11, 0xEE2A2F52);
            centered(ctx, toast, width / 2f, height - 28, TEXT);
            Draw.end(ctx);
        }
    }

    private void renderWindow(DrawContext ctx, Win w, float s) {
        curWin = w;
        clipY0 = 0;
        clipY1 = height;
        int x = (int) w.x, y = (int) w.y;
        int hh = w.collapsed ? TITLE : w.h;
        Draw.begin(ctx, s);
        Draw.rr(ctx, x - 2, y - 2, w.w + 4, hh + 4, 14, 0x30000000);
        Draw.rr(ctx, x - 1, y - 1, w.w + 2, hh + 2, 13, BORDER);
        Draw.rrGrad(ctx, x, y, w.w, hh, 12, PANEL_TOP, PANEL_BOT);
        Draw.rr(ctx, x + 12, y + TITLE - 3, 18, 2, 1, ACCENT);
        text(ctx, w.tab.label, x + 12, y + 7, TEXT);
        int bx = x + w.w - 16, by = y + TITLE / 2;
        boolean hc = hover(bx - 7, by - 7, 14, 14), hm = hover(bx - 25, by - 7, 14, 14);
        Draw.circle(ctx, bx, by, 6.5f, hc ? 0xFF6A2A40 : 0xFF2A2236);
        Draw.circle(ctx, bx - 18, by, 6.5f, hm ? 0xFF3A4676 : 0xFF262C4A);
        Draw.rect(ctx, bx - 21, by - 0.5f, 6, 1.4f, TEXT);
        if (w.collapsed) Draw.rect(ctx, bx - 18.7f, by - 3, 1.4f, 6, TEXT);
        addHit(bx - 7, by - 7, 14, 14, () -> w.open = false);
        addHit(bx - 25, by - 7, 14, 14, () -> w.collapsed = !w.collapsed);
        Draw.end(ctx);
        if (w.collapsed) return;

        cx = x + 10;
        cy = y + TITLE + 2;
        cw = w.w - 20;
        ch = w.h - TITLE - 8;
        clipY0 = cy;
        clipY1 = cy + ch;
        w.scroll = Math.max(0, Math.min(w.scroll, Math.max(0, w.contentH - ch)));
        int y0 = cy - (int) w.scroll;

        ctx.enableScissor(x + 3, cy, x + w.w - 3, cy + ch);
        Draw.begin(ctx, s);
        int h = switch (w.tab) {
            case MODS -> renderMods(ctx, w, y0);
            case COSMETICS -> renderCosmetics(ctx, w, y0);
            case SKIES -> renderSkies(ctx, w, y0);
            case MACROS -> renderMacros(ctx, w, y0);
            case EDITOR -> renderEditor(ctx, w, y0);
            case TEXTURES -> renderTextures(ctx, w, y0);
            case BUILDER -> renderBuilder(ctx, w, y0);
            case TRACERS -> renderTracers(ctx, w, y0);
            case SEARCH -> renderSearch(ctx, w, y0);
        };
        w.contentH = h;
        if (h > ch) {
            int barH = Math.max(20, (int) ((long) ch * ch / h));
            int barY = cy + (int) ((ch - barH) * (w.scroll / Math.max(1, h - ch)));
            Draw.rr(ctx, x + w.w - 8, barY, 3, barH, 1.5f, 0xFF3A4166);
        }
        Draw.end(ctx);
        ctx.disableScissor();
        clipY0 = 0;
        clipY1 = height;
    }

    // ======================================================================== MODS
    private static String desc(Module m) {
        return switch (m.name) {
            case "Fullbright" -> "Always bright, even in caves";
            case "Motion Blur" -> "Smooth camera - coming soon";
            case "Tracers" -> "Lines to the blocks you pick";
            default -> "";
        };
    }

    private int renderMods(DrawContext ctx, Win w, int y0) {
        int y = y0;
        for (Module m : MyClient.MODULES.all) {
            int h = 44;
            boolean hv = hover(cx, y, cw, h);
            Draw.rr(ctx, cx, y, cw, h, 12, hv ? CARD_HOVER : CARD);
            Draw.circle(ctx, cx + 22, y + 22, 12, m.isEnabled() ? ACCENT : OFF);
            centered(ctx, m.name.substring(0, 1), cx + 22, y + 17, 0xFFFFFFFF);
            text(ctx, m.name, cx + 42, y + 8, TEXT);
            text(ctx, Ui.trim(desc(m), cw - 42 - 50), cx + 42, y + 23, MUTED);
            addHit(cx, y, cw, h, m::toggle);
            toggle(ctx, "mod:" + m.name, cx + cw - 44, y + 13, m.isEnabled(), m::toggle);
            y += h + 6;
        }
        return y - y0;
    }

    // ======================================================================== COSMETICS
    private void pickCape() {
        FilePicker.pickPng("Choose a cape image (.png)", this::uploadCape,
            () -> toast("No file dialog here - drop the .png onto the window instead"));
    }

    private void uploadCape(Path p) {
        try {
            Cosmetic c = MyClient.COSMETICS.addCape(p);
            toast("Cape added: " + pretty(c.id));
        } catch (Exception e) {
            toast("Could not load that PNG");
        }
    }

    private void pickSky() {
        FilePicker.pickPng("Choose a sky image (.png)", this::uploadSky,
            () -> toast("No file dialog here - drop the .png onto the window instead"));
    }

    private void uploadSky(Path p) {
        try {
            String name = MyClient.COSMETICS.addSky(p);
            MyClient.COSMETICS.activeSky = name;
            toast("Sky added: " + name);
        } catch (Exception e) {
            toast("Could not load that PNG");
        }
    }

    private int renderCosmetics(DrawContext ctx, Win w, int y0) {
        pill(ctx, cx, y0, cw, 22, "Upload cape (.png)", true, this::pickCape);
        text(ctx, Ui.trim("or drop a .png onto the window", cw), cx + 2, y0 + 27, MUTED);
        int top = y0 + 44, cardH = 88;
        List<Cosmetic> list = new ArrayList<>(MyClient.COSMETICS.catalog);
        for (int i = 0; i < list.size(); i++) {
            Cosmetic c = list.get(i);
            int x = cx, y = top + i * (cardH + 8);
            if (y + cardH < cy || y > cy + ch) continue;
            boolean on = MyClient.COSMETICS.isEquipped(c);
            boolean hv = hover(x, y, cw, cardH);
            if (on) Draw.rr(ctx, x - 1, y - 1, cw + 2, cardH + 2, 13, ACCENT);
            Draw.rr(ctx, x, y, cw, cardH, 12, hv ? CARD_HOVER : CARD);
            addHit(x, y, cw, cardH, () -> MyClient.COSMETICS.toggle(c));
            Draw.rr(ctx, x + 8, y + 12, 60, 60, 10, TILE);
            drawIcon(ctx, c.type, c.animation, Palette.rgb(c), x + 38, y + 42, 46, c.speed);
            text(ctx, Ui.trim(pretty(c.id), cw - 76 - 50), x + 76, y + 8, TEXT);
            text(ctx, nice(c.type) + (c.type == Type.TRAIL ? "" : " - " + nice(c.animation)), x + 76, y + 22, MUTED);
            toggle(ctx, "cos:" + c.id, x + cw - 44, y + 8, on, () -> MyClient.COSMETICS.toggle(c));
            for (int v = 0; v < c.variants.size(); v++) {
                String variant = c.variants.get(v);
                int sx = x + 84 + v * 18, sy = y + 48;
                boolean sel = variant.equals(c.activeVariant);
                if (sel) Draw.circle(ctx, sx, sy, 8, 0xFFFFFFFF);
                Draw.circle(ctx, sx, sy, 6, 0xFF000000 | Palette.swatch(c, variant));
                addHit(sx - 8, sy - 8, 16, 16, () -> c.activeVariant = variant);
            }
            if (c.type != Type.TRAIL) {
                pill(ctx, x + 76, y + 62, Math.min(110, cw - 108), 18, "Anim: " + nice(c.animation), false, () ->
                    c.animation = Animation.values()[(c.animation.ordinal() + 1) % Animation.values().length]);
            }
            if (c.custom) {
                int dx = x + cw - 16, dy = y + cardH - 14;
                Draw.circle(ctx, dx, dy, 8, 0xFF3A2433);
                centered(ctx, "x", dx, dy - 5, DANGER);
                addHit(dx - 8, dy - 8, 16, 16, () -> {
                    MyClient.COSMETICS.equipped.remove(c.id);
                    MyClient.COSMETICS.catalog.remove(c);
                    try { if (c.file != null) Files.deleteIfExists(c.file); } catch (Exception ignored) {}
                });
            }
        }
        return 44 + list.size() * (cardH + 8);
    }

    // ======================================================================== SKIES
    private int[] skyColors(String name) {
        int[] up = MyClient.COSMETICS.uploadedSkies.get(name);
        if (up != null) return up;
        return switch (name) {
            case "Sunset" -> new int[]{0xFFFF7A59, 0xFF3B2A6B};
            case "Galaxy" -> new int[]{0xFF0B0B2E, 0xFF6A2AA0};
            case "Aurora" -> new int[]{0xFF0B2F3B, 0xFF2AF598};
            case "Starry Night" -> new int[]{0xFF050B24, 0xFF1C2D6B};
            case "Nebula" -> new int[]{0xFF2A0A3A, 0xFFE04FBF};
            default -> new int[]{0xFF78A7FF, 0xFFC8E0FF};
        };
    }

    private int renderSkies(DrawContext ctx, Win w, int y0) {
        pill(ctx, cx, y0, cw, 22, "Upload sky (.png)", true, this::pickSky);
        text(ctx, Ui.trim("or drop a .png onto the window", cw), cx + 2, y0 + 27, MUTED);
        int yy = y0 + 44;
        for (String line : wrap("Not shown in the world yet - your choice is only saved.", cw)) {
            text(ctx, line, cx + 2, yy, 0xFFC9B8FF);
            yy += 12;
        }
        int top = yy + 4, cardH = 60;
        List<String> skies = new ArrayList<>(MyClient.COSMETICS.skies);
        for (int i = 0; i < skies.size(); i++) {
            String s = skies.get(i);
            int y = top + i * (cardH + 8);
            if (y + cardH < cy || y > cy + ch) continue;
            boolean sel = s.equals(MyClient.COSMETICS.activeSky);
            boolean hv = hover(cx, y, cw, cardH);
            if (sel) Draw.rr(ctx, cx - 1, y - 1, cw + 2, cardH + 2, 13, ACCENT);
            Draw.rr(ctx, cx, y, cw, cardH, 12, hv ? CARD_HOVER : CARD);
            int[] cc = skyColors(s);
            Draw.rrGrad(ctx, cx + 8, y + 8, 64, cardH - 16, 9, cc[0], cc[1]);
            text(ctx, Ui.trim(s, cw - 80 - 30), cx + 82, y + 14, TEXT);
            text(ctx, sel ? "Selected" : "Click to select", cx + 82, y + 30, sel ? ACCENT2 : MUTED);
            Draw.circle(ctx, cx + cw - 18, y + 20, 8, sel ? ACCENT : OFF);
            if (sel) Draw.circle(ctx, cx + cw - 18, y + 20, 3.5f, 0xFFFFFFFF);
            addHit(cx, y, cw, cardH, () -> MyClient.COSMETICS.activeSky = s);
            if (MyClient.COSMETICS.uploadedSkies.containsKey(s)) {
                int dx = cx + cw - 18, dy = y + cardH - 14;
                Draw.circle(ctx, dx, dy, 7, 0xFF3A2433);
                centered(ctx, "x", dx, dy - 5, DANGER);
                addHit(dx - 8, dy - 8, 16, 16, () -> MyClient.COSMETICS.removeSky(s));
            }
        }
        return top - y0 + skies.size() * (cardH + 8);
    }

    // ======================================================================== MACROS
    private String keyLabel(int code) {
        try { return InputUtil.fromKeyCode(code, 0).getLocalizedText().getString().toUpperCase(); }
        catch (Exception e) { return "KEY " + code; }
    }

    private int renderMacros(DrawContext ctx, Win w, int y0) {
        List<Macro> ms = MyClient.MACROS.macros;
        text(ctx, "Macros", cx + 2, y0 + 5, TEXT);
        if (ms.size() < 6) {
            pill(ctx, cx + cw - 104, y0, 104, 22, "+ New macro", true, () ->
                ms.add(new Macro("Macro " + (ms.size() + 1), GLFW.GLFW_KEY_G, "", 0)));
        }
        if (ms.isEmpty()) centered(ctx, "No macros yet.", cx + cw / 2f, y0 + 80, MUTED);
        for (int i = 0; i < ms.size(); i++) {
            Macro m = ms.get(i);
            int ry = y0 + 30 + i * 66;
            Draw.rr(ctx, cx, ry, cw, 60, 12, CARD);
            text(ctx, Ui.trim(m.name, 70), cx + 10, ry + 9, TEXT);
            pill(ctx, cx + 82, ry + 5, 66, 20, capturing == m ? "press key" : keyLabel(m.keyCode), capturing == m, () -> capturing = m);
            toggle(ctx, "mac:" + System.identityHashCode(m), cx + cw - 78, ry + 6, m.enabled, () -> m.enabled = !m.enabled);
            int dx = cx + cw - 20, dy = ry + 15;
            boolean hv = hover(dx - 9, dy - 9, 18, 18);
            Draw.circle(ctx, dx, dy, 9, hv ? 0xFF4A2A3C : 0xFF2A2236);
            centered(ctx, "x", dx, dy - 5, DANGER);
            addHit(dx - 9, dy - 9, 18, 18, () -> ms.remove(m));
            TextBox b = macroBoxes.computeIfAbsent(m, k -> {
                TextBox nb = new TextBox("/command or message");
                nb.set(k.command);
                nb.onChange = s -> k.command = s;
                return nb;
            });
            box(ctx, b, cx + 8, ry + 31, cw - 16, 22);
        }
        return 30 + Math.max(1, ms.size()) * 66;
    }

    // ======================================================================== EDITOR
    private int renderEditor(DrawContext ctx, Win w, int y0) {
        text(ctx, "Name", cx + 2, y0, MUTED);
        box(ctx, nameBox, cx, y0 + 14, cw, 22);
        int half = (cw - 8) / 2;
        pill(ctx, cx, y0 + 44, half, 22, "Type: " + nice(draftType), false, () -> {
            int i = 0;
            for (int k = 0; k < EDITABLE.length; k++) if (EDITABLE[k] == draftType) i = k;
            draftType = EDITABLE[(i + 1) % EDITABLE.length];
        });
        pill(ctx, cx + half + 8, y0 + 44, half, 22, "Anim: " + nice(draftAnim), false,
            () -> draftAnim = Animation.values()[(draftAnim.ordinal() + 1) % Animation.values().length]);
        slider(ctx, "Red", cx, y0 + 76, cw, dr, 0, 255, String.valueOf((int) dr), v -> dr = v);
        slider(ctx, "Green", cx, y0 + 98, cw, dg, 0, 255, String.valueOf((int) dg), v -> dg = v);
        slider(ctx, "Blue", cx, y0 + 120, cw, db, 0, 255, String.valueOf((int) db), v -> db = v);
        slider(ctx, "Speed", cx, y0 + 142, cw, dspeed, 0.2f, 3f, String.format("%.1fx", dspeed), v -> dspeed = v);
        pill(ctx, cx, y0 + 168, cw, 26, "Create accessory", true, this::createDraft);
        Draw.rr(ctx, cx, y0 + 204, cw, 96, 14, TILE);
        int rgb = ((int) dr << 16) | ((int) dg << 8) | (int) db;
        drawIcon(ctx, draftType, draftAnim, rgb, cx + cw / 2f, y0 + 252, 64, dspeed);
        return 312;
    }

    private void createDraft() {
        String name = nameBox.text.trim();
        if (name.isEmpty()) { toast("Enter a name first"); return; }
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (slug.isEmpty()) slug = "accessory";
        String base = "custom_" + slug, id = base;
        int n = 2;
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

    // ======================================================================== TEXTURES
    private void applyTextures() {
        try {
            TextureLab.apply();
            toast("Applying textures - the game reloads...");
        } catch (Exception e) {
            toast("Could not write the texture pack");
        }
    }

    private int renderTextures(DrawContext ctx, Win w, int y0) {
        if (texFiltered == null) refilterTextures();
        box(ctx, texBox, cx, y0, cw, 24);
        int ry = y0 + 30;
        pill(ctx, cx, ry, cw - 110, 22, TextureLab.dirtyCount() > 0 ? "Apply + reload (" + TextureLab.dirtyCount() + ")" : "Apply + reload", true, this::applyTextures);
        text(ctx, "Pack", cx + cw - 104, ry + 5, MUTED);
        toggle(ctx, "texpack", cx + cw - 40, ry + 2, TextureLab.isEnabled(), () -> {
            if (!TextureLab.packExists()) { toast("Edit a texture and press Apply first"); return; }
            TextureLab.setEnabled(!TextureLab.isEnabled());
            toast(TextureLab.isEnabled() ? "Texture pack on" : "Texture pack off");
        });

        int ly = y0 + 60, rows = 5, lh = rows * 20 + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 12, CARD);
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, texFiltered.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= texFiltered.size()) break;
            String nm = texFiltered.get(idx);
            int rr = ly + 4 + r * 20;
            boolean sel = nm.equals(selTex), hv = hover(cx + 4, rr, cw - 8, 18);
            if (sel) Draw.rr(ctx, cx + 4, rr, cw - 8, 18, 9, ACCENT);
            else if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, 18, 9, 0xFF262D4C);
            text(ctx, Ui.trim(nm, cw - 40), cx + 12, rr + 4, sel ? 0xFFFFFFFF : TEXT);
            if (TextureLab.isDirty(nm)) Draw.circle(ctx, cx + cw - 16, rr + 9, 3, ACCENT2);
            addHit(cx + 4, rr, cw - 8, 18, () -> selTex = nm);
        }
        if (texFiltered.isEmpty()) text(ctx, "No textures found", cx + 12, ly + 12, MUTED);
        text(ctx, texFiltered.size() + " block textures (scroll the list)", cx + 4, ly + lh + 3, MUTED);

        int ty = ly + lh + 20;
        BufferedImage img = selTex == null ? null : TextureLab.get(selTex);
        int bottom = ty + 20;
        if (img != null) {
            int size = TextureLab.size(img);
            int cell = Math.max(2, 128 / size), gp = cell * size;
            gx = cx + 3; gy = ty + 16; gcell = cell; gsize = size;
            text(ctx, Ui.trim(pretty(selTex) + " (" + size + "x" + size + (img.getHeight() > img.getWidth() ? ", frame 1" : "") + ")", cw), cx + 2, ty, TEXT);
            Draw.rr(ctx, gx - 3, gy - 3, gp + 6, gp + 6, 8, TILE);
            drawImage(ctx, img, size, gx, gy, cell, true);

            int tx = gx + gp + 14, tw = Math.min(92, cx + cw - tx);
            int i = 0;
            for (Tool t : Tool.values()) {
                final Tool tt = t;
                pill(ctx, tx, gy + i * 22, tw, 18, t.label, tool == t, () -> tool = tt);
                i++;
            }
            pill(ctx, tx, gy + i * 22, tw / 2 - 2, 18, "Undo", false, () -> { if (selTex != null) TextureLab.undo(selTex); });
            pill(ctx, tx + tw / 2 + 2, gy + i * 22, tw / 2 - 2, 18, "Reset", false, () -> {
                if (selTex != null) { TextureLab.reset(selTex); toast("Reset - press Apply to reload the original"); }
            });
            float pp = Math.max(1f, 26f / size);
            Draw.rr(ctx, tx - 3, gy + 138 - 3, pp * size * 2 + 6, pp * size * 2 + 6, 6, TILE);
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++)
                drawImage(ctx, img, size, tx + a * pp * size, gy + 138 + b * pp * size, pp, false);

            int sy = gy + gp + 12;
            slider(ctx, "Red", cx, sy, cw - 36, texR, 0, 255, String.valueOf((int) texR), v -> texR = v);
            slider(ctx, "Green", cx, sy + 20, cw - 36, texG, 0, 255, String.valueOf((int) texG), v -> texG = v);
            slider(ctx, "Blue", cx, sy + 40, cw - 36, texB, 0, 255, String.valueOf((int) texB), v -> texB = v);
            Draw.rr(ctx, cx + cw - 28, sy + 4, 26, 30, 8, 0xFF000000 | ((int) texR << 16) | ((int) texG << 8) | (int) texB);
            bottom = sy + 62;
        } else {
            text(ctx, "Pick a block texture above", cx + 2, ty, MUTED);
        }
        return bottom - y0 + 6;
    }

    private boolean gridPaint(double mouseX, double mouseY) {
        if (selTex == null || gcell <= 0) return false;
        int px = (int) Math.floor((mouseX - gx) / gcell), py = (int) Math.floor((mouseY - gy) / gcell);
        if (px < 0 || py < 0 || px >= gsize || py >= gsize) return false;
        BufferedImage img = TextureLab.get(selTex);
        if (img == null) return false;
        int argb = 0xFF000000 | ((int) texR << 16) | ((int) texG << 8) | (int) texB;
        switch (tool) {
            case PENCIL -> TextureLab.paint(selTex, px, py, argb);
            case ERASER -> TextureLab.paint(selTex, px, py, 0);
            case FILL -> TextureLab.fill(selTex, px, py, argb);
            case PICK -> {
                int p = img.getRGB(px, py);
                texR = (p >> 16) & 255; texG = (p >> 8) & 255; texB = p & 255;
            }
        }
        return true;
    }

    // ======================================================================== BUILDER
    private void loadSchematic(Path p) {
        try {
            SchematicBuilder.load(p);
            toast("Loaded " + SchematicBuilder.loaded.name);
        } catch (Exception e) {
            toast("Could not read that schematic");
        }
    }

    private int renderBuilder(DrawContext ctx, Win w, int y0) {
        box(ctx, schemBox, cx, y0, cw, 24);
        int ly = y0 + 30, rows = 5, lh = rows * 20 + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 12, CARD);
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, schemFiltered.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= schemFiltered.size()) break;
            Path p = schemFiltered.get(idx);
            String nm = p.getFileName().toString().replaceFirst("\\.litematic$", "");
            int rr = ly + 4 + r * 20;
            boolean sel = SchematicBuilder.loaded != null && SchematicBuilder.loaded.name.equals(nm);
            boolean hv = hover(cx + 4, rr, cw - 8, 18);
            if (sel) Draw.rr(ctx, cx + 4, rr, cw - 8, 18, 9, ACCENT);
            else if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, 18, 9, 0xFF262D4C);
            text(ctx, Ui.trim(nm, cw - 28), cx + 12, rr + 4, sel ? 0xFFFFFFFF : TEXT);
            addHit(cx + 4, rr, cw - 8, 18, () -> loadSchematic(p));
        }
        if (schemFiltered.isEmpty()) {
            text(ctx, schemFiles.isEmpty() ? "No .litematic files found" : "No match", cx + 12, ly + 10, MUTED);
            if (schemFiles.isEmpty()) text(ctx, "Put them in .minecraft/schematics", cx + 12, ly + 24, MUTED);
        }

        int iy = ly + lh + 8, ih = 150;
        Draw.rr(ctx, cx, iy, cw, ih, 12, CARD);
        int yy = iy + 8;
        if (SchematicBuilder.loaded == null) {
            text(ctx, "Select a schematic above.", cx + 10, yy, MUTED);
        } else {
            text(ctx, Ui.trim(SchematicBuilder.loaded.name, cw - 20), cx + 10, yy, TEXT);
            text(ctx, SchematicBuilder.loaded.sizeX + "x" + SchematicBuilder.loaded.sizeY + "x" + SchematicBuilder.loaded.sizeZ
                + " - " + SchematicBuilder.loaded.count() + " blocks", cx + 10, yy + 12, MUTED);
            float prog = SchematicBuilder.total() == 0 ? 0f : SchematicBuilder.placed() / (float) SchematicBuilder.total();
            Draw.rr(ctx, cx + 10, yy + 28, cw - 20, 8, 4, OFF);
            Draw.rr(ctx, cx + 10, yy + 28, Math.max(8, (cw - 20) * prog), 8, 4, ACCENT2);
            String o = SchematicBuilder.origin == null ? "Origin: not set"
                : "Origin: " + SchematicBuilder.origin.getX() + " " + SchematicBuilder.origin.getY() + " " + SchematicBuilder.origin.getZ();
            text(ctx, o, cx + 10, yy + 42, TEXT);
            pill(ctx, cx + 10, yy + 56, cw - 20, 18, "Set origin here (my feet)", false, () -> {
                SchematicBuilder.setOriginHere();
                toast("Origin set to your position");
            });
            text(ctx, "Build", cx + 10, yy + 82, TEXT);
            toggle(ctx, "build", cx + cw - 46, yy + 79, SchematicBuilder.running, () -> SchematicBuilder.setRunning(!SchematicBuilder.running));
            text(ctx, "Walk to blocks", cx + 10, yy + 102, TEXT);
            toggle(ctx, "walk", cx + cw - 46, yy + 99, SchematicBuilder.walk, () -> SchematicBuilder.walk = !SchematicBuilder.walk);
            pill(ctx, cx + 10, yy + 122, cw - 20, 18, "Restart", false, SchematicBuilder::restart);
        }
        int sy = iy + ih + 8, lines = 0;
        for (String line : wrap(SchematicBuilder.status, cw - 4)) {
            text(ctx, line, cx + 2, sy + lines * 12, SchematicBuilder.missing.isEmpty() ? ACCENT2 : 0xFFFFB454);
            lines++;
        }
        for (String line : wrap("Builds bottom to top, in reach only, from your hotbar. Many servers forbid this.", cw - 4)) {
            text(ctx, line, cx + 2, sy + lines * 12 + 4, MUTED);
            lines++;
        }
        return sy + lines * 12 + 10 - y0;
    }

    // ======================================================================== TRACERS
    private int renderTracers(DrawContext ctx, Win w, int y0) {
        dev.myclient.module.Tracers tr = MyClient.MODULES.tracers;
        text(ctx, "Tracers", cx + 2, y0 + 4, TEXT);
        toggle(ctx, "tracers", cx + cw - 40, y0 + 1, tr.isEnabled(), tr::toggle);
        slider(ctx, "Range", cx, y0 + 30, cw, tr.radius, 8, 48, tr.radius + "m", v -> { tr.radius = Math.round(v); tr.changed(); });

        int y = y0 + 56;
        text(ctx, "Selected blocks (" + tr.blocks.size() + ") - click to remove", cx + 2, y, MUTED);
        y += 14;
        if (tr.blocks.isEmpty()) {
            text(ctx, "None yet - pick blocks below", cx + 2, y + 2, MUTED);
            y += 20;
        } else {
            int x = cx;
            for (String id : new ArrayList<>(tr.blocks)) {
                String label = pretty(id);
                int cwid = Ui.width(label) + 24;
                if (x + cwid > cx + cw) { x = cx; y += 22; }
                boolean hv = hover(x, y, cwid, 18);
                Draw.rr(ctx, x, y, cwid, 18, 9, hv ? 0xFF4A2A3C : 0xFF2A2F55);
                Draw.circle(ctx, x + 9, y + 9, 3.5f, 0xFF000000 | Palette.rainbow((id.hashCode() & 0x7fffffff) % 360 / 360f, 0.65f, 1f));
                text(ctx, label, x + 17, y + 4, TEXT);
                addHit(x, y, cwid, 18, () -> tr.toggleBlock(id));
                x += cwid + 4;
            }
            y += 26;
        }

        box(ctx, tracerBox, cx, y, cw, 24);
        int ly = y + 30, rows = 6, rh = 24, lh = rows * rh + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 12, CARD);
        if (blockFiltered == null) refilterBlocks();
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, blockFiltered.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= blockFiltered.size()) break;
            String id = blockFiltered.get(idx);
            int rr = ly + 4 + r * rh;
            boolean on = tr.blocks.contains(id), hv = hover(cx + 4, rr, cw - 8, rh - 2);
            if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, rh - 2, 10, 0xFF262D4C);
            text(ctx, Ui.trim(pretty(id), cw - 70), cx + 12, rr + 6, TEXT);
            addHit(cx + 4, rr, cw - 8, rh - 2, () -> tr.toggleBlock(id));
            toggle(ctx, "tb:" + id, cx + cw - 50, rr + 2, on, () -> tr.toggleBlock(id));
        }
        text(ctx, blockFiltered.size() + " blocks (scroll the list)", cx + 4, ly + lh + 3, MUTED);
        return ly + lh + 20 - y0;
    }

    // ======================================================================== SEARCH
    private List<Result> results(String q) {
        List<Result> out = new ArrayList<>();
        q = q.toLowerCase().trim();
        if (q.isEmpty()) return out;
        final String query = q;
        String qb = q.replace(' ', '_');
        for (Tab t : Tab.values()) if (t.label.toLowerCase().contains(query)) {
            Win ww = win(t);
            out.add(new Result("Window", t.label, ww != null && ww.open, () -> openWindow(t)));
        }
        for (Module m : MyClient.MODULES.all) if (m.name.toLowerCase().contains(query))
            out.add(new Result("Module", m.name, m.isEnabled(), m::toggle));
        int n = 0;
        for (Cosmetic c : MyClient.COSMETICS.catalog) {
            if (n >= 12) break;
            if (pretty(c.id).toLowerCase().contains(query) || c.variants.stream().anyMatch(v -> v.contains(query))) {
                out.add(new Result("Cosmetic", pretty(c.id), MyClient.COSMETICS.isEquipped(c), () -> MyClient.COSMETICS.toggle(c)));
                n++;
            }
        }
        for (String s : MyClient.COSMETICS.skies) if (s.toLowerCase().contains(query))
            out.add(new Result("Sky", s, s.equals(MyClient.COSMETICS.activeSky), () -> MyClient.COSMETICS.activeSky = s));
        for (Macro m : MyClient.MACROS.macros) if (m.name.toLowerCase().contains(query))
            out.add(new Result("Macro", m.name, m.enabled, () -> m.enabled = !m.enabled));
        n = 0;
        for (Path p : schemFiles) {
            if (n >= 8) break;
            String nm = p.getFileName().toString().replaceFirst("\\.litematic$", "");
            if (nm.toLowerCase().contains(query)) {
                out.add(new Result("Schematic", nm, SchematicBuilder.loaded != null && SchematicBuilder.loaded.name.equals(nm),
                    () -> { loadSchematic(p); openWindow(Tab.BUILDER); }));
                n++;
            }
        }
        n = 0;
        for (String id : allBlocks()) {
            if (n >= 15) break;
            if (id.contains(qb)) {
                out.add(new Result("Tracer", pretty(id), MyClient.MODULES.tracers.blocks.contains(id), () -> MyClient.MODULES.tracers.toggleBlock(id)));
                n++;
            }
        }
        n = 0;
        for (String name : TextureLab.names()) {
            if (n >= 15) break;
            if (name.contains(qb)) {
                out.add(new Result("Texture", pretty(name), name.equals(selTex), () -> {
                    selTex = name;
                    openWindow(Tab.TEXTURES);
                }));
                n++;
            }
        }
        return out;
    }

    private int renderSearch(DrawContext ctx, Win w, int y0) {
        box(ctx, searchBox, cx, y0, cw, 26);
        List<Result> res = results(searchBox.text);
        int ly = y0 + 34, rows = 9, rh = 24, lh = rows * rh + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 12, CARD);
        if (searchBox.text.isBlank()) {
            int yy = ly + 12;
            for (String line : wrap("Type to find modules, cosmetics, skies, macros, schematics, block tracers, block textures and windows.", cw - 24)) {
                text(ctx, line, cx + 12, yy, MUTED);
                yy += 12;
            }
        } else if (res.isEmpty()) {
            text(ctx, "Nothing found", cx + 12, ly + 12, MUTED);
        }
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, res.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= res.size()) break;
            Result rs = res.get(idx);
            int rr = ly + 4 + r * rh;
            boolean hv = hover(cx + 4, rr, cw - 8, rh - 2);
            if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, rh - 2, 10, 0xFF262D4C);
            int cwid = Ui.width(rs.cat()) + 12;
            Draw.rr(ctx, cx + 10, rr + 4, cwid, 15, 7.5f, 0xFF2A2F55);
            text(ctx, rs.cat(), cx + 16, rr + 7, ACCENT2);
            text(ctx, Ui.trim(rs.label(), cw - cwid - 56), cx + 18 + cwid, rr + 7, TEXT);
            Draw.circle(ctx, cx + cw - 18, rr + 11, 5, rs.on() ? ACCENT : OFF);
            addHit(cx + 4, rr, cw - 8, rh - 2, rs.run());
        }
        if (res.size() > 0) text(ctx, res.size() + " results", cx + 4, ly + lh + 3, MUTED);
        return ly + lh + 20 - y0;
    }

    // ======================================================================== drag & drop
    private void handleDrop(List<Path> paths) {
        int ok = 0;
        Win top = WINS.isEmpty() ? null : WINS.get(WINS.size() - 1);
        for (Path p : paths) {
            if (!p.toString().toLowerCase().endsWith(".png")) continue;
            if (top != null && top.tab == Tab.SKIES) { uploadSky(p); ok++; }
            else { uploadCape(p); ok++; }
        }
        if (ok == 0) toast("Drop a .png file");
    }

    public void onFilesDropped(List<Path> paths) { handleDrop(paths); }

    public void filesDragged(List<Path> paths) { handleDrop(paths); }

    // ======================================================================== input
    private Win windowAt(double x, double y) {
        for (int i = WINS.size() - 1; i >= 0; i--) {
            Win w = WINS.get(i);
            if (!w.open) continue;
            int hh = w.collapsed ? TITLE : w.h;
            if (x >= w.x && x < w.x + w.w && y >= w.y && y < w.y + hh) return w;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        Win w = windowAt(mouseX, mouseY);
        if (w == null) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.win() == null && h.in(mouseX, mouseY)) { focus = null; h.run().run(); return true; }
            }
            focus = null;
            return true;
        }
        WINS.remove(w);
        WINS.add(w);

        for (BoxRect b : boxes) {
            if (b.win() == w && mouseX >= b.x() && mouseX < b.x() + b.w() && mouseY >= b.y() && mouseY < b.y() + b.h()) {
                focus = b.box();
                b.box().cursor = b.box().text.length();
                return true;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.win() == w && h.in(mouseX, mouseY)) {
                focus = null;
                h.run().run();
                return true;
            }
        }
        for (Slider s : sliders) {
            if (s.win() == w && mouseX >= s.x() - 6 && mouseX <= s.x() + s.w() + 6 && mouseY >= s.y() && mouseY < s.y() + 14) {
                dragging = s;
                updateSlider(s, mouseX);
                return true;
            }
        }
        if (w.tab == Tab.TEXTURES && gcell > 0 && selTex != null && !w.collapsed
            && mouseX >= gx && mouseX < gx + gcell * gsize && mouseY >= gy && mouseY < gy + gcell * gsize
            && mouseY >= cy && mouseY < cy + ch) {
            if (tool != Tool.PICK) TextureLab.beginStroke(selTex);
            painting = true;
            gridPaint(mouseX, mouseY);
            return true;
        }
        if (mouseY < w.y + TITLE) {
            dragWin = w;
            dragOffX = (float) (mouseX - w.x);
            dragOffY = (float) (mouseY - w.y);
            return true;
        }
        focus = null;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null) { updateSlider(dragging, mouseX); return true; }
        if (painting && (tool == Tool.PENCIL || tool == Tool.ERASER)) { gridPaint(mouseX, mouseY); return true; }
        if (dragWin != null) {
            dragWin.x = Math.max(0, Math.min((float) (mouseX - dragOffX), Math.max(0, width - dragWin.w)));
            dragWin.y = Math.max(0, Math.min((float) (mouseY - dragOffY), Math.max(0, height - TITLE)));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        painting = false;
        dragWin = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        Win w = windowAt(mouseX, mouseY);
        if (w == null || w.collapsed) return true;
        boolean overList = w.lw > 0 && mouseX >= w.lx && mouseX < w.lx + w.lw && mouseY >= w.ly && mouseY < w.ly + w.lh
            && (w.tab == Tab.TEXTURES || w.tab == Tab.BUILDER || w.tab == Tab.TRACERS || w.tab == Tab.SEARCH);
        if (overList) w.listScroll = Math.max(0, w.listScroll - (int) Math.signum(vertical) * 2);
        else w.scroll -= (float) vertical * 24f;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focus != null) { focus.type(chr); return true; }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing != null) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) capturing.keyCode = keyCode;
            capturing = null;
            return true;
        }
        if (focus != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { focus = null; return true; }
            String clip = null;
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                try { clip = client.keyboard.getClipboard(); } catch (Exception ignored) {}
            }
            focus.key(keyCode, modifiers, clip);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && selTex != null) {
            TextureLab.undo(selTex);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }
}
