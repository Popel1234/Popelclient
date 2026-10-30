package dev.myclient.gui;

import dev.myclient.Config;
import dev.myclient.Keybinds;
import dev.myclient.MyClient;
import dev.myclient.Settings;
import dev.myclient.builder.SchematicBuilder;
import dev.myclient.cosmetic.Cosmetic;
import dev.myclient.cosmetic.Cosmetic.Animation;
import dev.myclient.cosmetic.Cosmetic.Type;
import dev.myclient.cosmetic.Palette;
import dev.myclient.cosmetic.Preview;
import dev.myclient.fake.FakePack;
import dev.myclient.fake.FakeScoreboard;
import dev.myclient.fake.FakeSpawners;
import dev.myclient.module.SusChunkFinder;
import dev.myclient.macro.Macro;
import dev.myclient.module.Module;
import dev.myclient.texture.TextureLab;
import dev.myclient.util.FilePicker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
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

/** Compact floating-window menu: drag the title bars, collapse, close, open from the dock at the top. */
public class ClientScreen extends Screen {

    public enum Tab {
        MODS("Mods", 170, 250), COSMETICS("Cosmetics", 230, 300), SKIES("Skies", 210, 270), MACROS("Macros", 240, 240),
        EDITOR("Editor", 240, 340), TEXTURES("Textures", 270, 350), BUILDER("Builder", 270, 330),
        TRACERS("Tracers", 240, 360), EFFECTS("Effects", 200, 270), SEARCH("Search", 240, 330),
        FINDER("Finder", 230, 290), FAKE("Fake", 250, 340), DONUT("Donut", 250, 440),
        SPAWNER("Spawner", 240, 340);
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
        int lx, ly, lw, lh, listScroll;
    }

    private enum Tool {
        PENCIL("Pencil"), ERASER("Eraser"), FILL("Fill"), PICK("Pick color");
        final String label;
        Tool(String l) { label = l; }
    }

    private static final int TITLE = 24;

    // ---- theme (flat, dark, violet + cyan) ----
    private static int PANEL, BORDER, BORDER2, CARD, CARD_HOVER, TILE, ACCENT, ACCENT_HOVER, ACCENT2, TEXT, MUTED, OFF, DANGER,
        BTN, BTN_HOVER, HOVER, ON_ACCENT, DOCK_BG, DOCK_PILL, CHIP, DIM, BG1, BG2;

    private static void applyTheme() {
        Theme.T t = Theme.current();
        PANEL = t.panel; BORDER = t.border; BORDER2 = t.border2; CARD = t.card; CARD_HOVER = t.cardHover; TILE = t.tile;
        ACCENT = t.accent; ACCENT_HOVER = t.accentHover; ACCENT2 = t.accent2; TEXT = t.text; MUTED = t.muted; OFF = t.off;
        DANGER = t.danger; BTN = t.btn; BTN_HOVER = t.btnHover; HOVER = t.hover; ON_ACCENT = t.onAccent;
        DOCK_BG = t.dockBg; DOCK_PILL = t.dockPill; CHIP = t.chip; DIM = t.dim; BG1 = t.bg1; BG2 = t.bg2;
        Draw.radiusScale = t.radius;
    }

    // ---- persistent (static) state ----
    private static final List<Win> WINS = new ArrayList<>();
    private static final TextBox texBox = new TextBox("Search any block...");
    private static final TextBox schemBox = new TextBox("Search schematics...");
    private static final TextBox tracerBox = new TextBox("Search blocks...");
    private static final TextBox searchBox = new TextBox("Search everything...");
    private static final TextBox nameBox = new TextBox("My accessory");
    private static final TextBox spawnerBox = new TextBox("Search blocks...");
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
    private static float pendingScale = -1f;

    private static Type draftType = Type.HALO;
    private static Animation draftAnim = Animation.FLOAT;
    private static float dr = 176, dg = 108, db = 255, dspeed = 1f;

    static {
        texBox.onChange = s -> refilterTextures();
        tracerBox.onChange = s -> refilterBlocks();
        schemBox.onChange = s -> refilterSchematics();
        spawnerBox.onChange = s -> refilterSpawner();
    }

    private static final TextBox donutTitle = new TextBox("Title");
    private static final TextBox donutMoney = new TextBox("Money");
    private static final TextBox donutPay = new TextBox("Pay message");
    private static final TextBox[] donutLines = new TextBox[10];
    private static boolean donutInit;
    private static boolean editXray;

    private static void setLine(int i, String v) {
        while (Settings.fakeLines.size() <= i) Settings.fakeLines.add("");
        Settings.fakeLines.set(i, v);
    }

    private static void initDonut() {
        if (donutInit) return;
        donutInit = true;
        donutTitle.text = Settings.fakeTitle;
        donutTitle.cursor = donutTitle.text.length();
        donutTitle.onChange = v -> Settings.fakeTitle = v;
        donutMoney.text = String.valueOf((long) Settings.fakeMoney);
        donutMoney.cursor = donutMoney.text.length();
        donutMoney.onChange = v -> { Double d = FakeScoreboard.parseAmount(v); if (d != null) Settings.fakeMoney = d; };
        donutPay.text = Settings.payFormat;
        donutPay.cursor = donutPay.text.length();
        donutPay.onChange = v -> Settings.payFormat = v;
        donutEarn.text = Settings.earnFormat;
        donutEarn.cursor = donutEarn.text.length();
        donutEarn.onChange = v -> Settings.earnFormat = v;
        for (int i = 0; i < donutLines.length; i++) {
            final int k = i;
            TextBox b = new TextBox("Line " + (i + 1));
            b.text = i < Settings.fakeLines.size() ? Settings.fakeLines.get(i) : "";
            b.cursor = b.text.length();
            b.onChange = v -> setLine(k, v);
            donutLines[i] = b;
        }
    }

    private static void donutPreset() {
        Settings.fakeTitle = "DonutSMP";
        Settings.fakeLines = new ArrayList<>(Settings.defaultLines());
        Settings.fakeMoney = 1250000;
        Settings.payFormat = "&fyou payed {player} &a$&f{amount}";
        Settings.earnFormat = "&fyou earned &a$&f{amount}";
        donutInit = false;
        initDonut();
    }

    private record Hit(Win win, int x, int y, int w, int h, Runnable run) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Slider(Win win, int x, int y, int w, float min, float max, Consumer<Float> set) {}
    private static List<String> spawnerFiltered;
    private static final TextBox donutEarn = new TextBox("Earn message");

    private static void refilterSpawner() {
        String q = spawnerBox.text.toLowerCase().trim().replace(' ', '_');
        spawnerFiltered = new ArrayList<>();
        for (String id : allBlocks()) if (q.isEmpty() || id.contains(q)) spawnerFiltered.add(id);
        Win w = win(Tab.SPAWNER);
        if (w != null) w.listScroll = 0;
    }

    private record RightHit(Win win, int x, int y, int w, int h, String id, String label) {}
    private record BoxRect(Win win, int x, int y, int w, int h, TextBox box) {}
    private record Result(String cat, String label, boolean on, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<RightHit> rightHits = new ArrayList<>();
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
        super(Text.literal("Lumen"));
    }

    private static float K() { return Settings.guiScale; }

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
        for (Tab t : Tab.values()) {
            final Tab tt = t;
            if (WINS.stream().noneMatch(w -> w.tab == tt)) {
                Win w = new Win();
                w.tab = t; w.w = t.w; w.h = t.h;
                w.x = 20 + (t.ordinal() % 5) * 36;
                w.y = 40 + (t.ordinal() % 5) * 24;
                w.open = t == Tab.MODS || t == Tab.COSMETICS;
                if (t == Tab.COSMETICS) w.x = 200;
                WINS.add(w);
            }
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
        int lw = (int) (width / K()), lh = (int) (height / K());
        for (Win w : WINS) {
            w.x = Math.max(0, Math.min(w.x, Math.max(0, lw - w.w)));
            w.y = Math.max(0, Math.min(w.y, Math.max(0, lh - TITLE)));
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
    private int winHeight(Win w, int lh) {
        return w.collapsed ? TITLE : Math.min(w.h, Math.max(TITLE + 50, lh - 8));
    }

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

    private void bindable(int x, int y, int w, int h, String id, String label) {
        int y0 = Math.max(y, clipY0), y1 = Math.min(y + h, clipY1);
        if (y1 > y0) rightHits.add(new RightHit(curWin, x, y0, w, y1 - y0, id, label));
    }

    private void keyTag(DrawContext ctx, String id, int rightX, int y) {
        String k = Keybinds.label(id);
        if (k != null) {
            String t = "[" + k + "]";
            Ui.text(ctx, t, rightX - Ui.width(t), y, ACCENT2);
        }
    }

    private void text(DrawContext ctx, String s, float x, float y, int c) { Ui.text(ctx, s, x, y, c); }

    private void centered(DrawContext ctx, String s, float mid, float y, int c) { Ui.centered(ctx, s, mid, y, c); }

    private void pill(DrawContext ctx, int x, int y, int w, int h, String label, boolean primary, Runnable run) {
        boolean hv = hover(x, y, w, h);
        int base = primary ? (hv ? ACCENT_HOVER : ACCENT) : (hv ? BTN_HOVER : BTN);
        Draw.rr(ctx, x, y, w, h, h / 2f, base);
        centered(ctx, Ui.trim(label, w - 8), x + w / 2f, y + (h - 9) / 2f, primary ? ON_ACCENT : TEXT);
        addHit(x, y, w, h, run);
    }

    private void toggle(DrawContext ctx, String key, int x, int y, boolean on, Runnable run) {
        float t = anim(key, on ? 1f : 0f);
        int w = 32, h = 16;
        if (t > 0.05f) Draw.rr(ctx, x - 2, y - 2, w + 4, h + 4, 10, Draw.alpha(ACCENT, (int) (46 * t)));
        Draw.rr(ctx, x, y, w, h, 8, Draw.lerp(OFF, ACCENT, t));
        Draw.circle(ctx, x + 8 + (w - 16) * t, y + 8, 5.5f, 0xFFFFFFFF);
        addHit(x, y, w, h, run);
    }

    private void slider(DrawContext ctx, String label, int x, int y, int w, float value, float min, float max,
                        String shown, Consumer<Float> setter) {
        text(ctx, label, x, y + 2, MUTED);
        int tx = x + 44, tw = w - 44 - 40;
        float f = Math.max(0f, Math.min(1f, (value - min) / (max - min)));
        Draw.rr(ctx, tx, y + 4, tw, 5, 2.5f, OFF);
        Draw.rr(ctx, tx, y + 4, Math.max(5, tw * f), 5, 2.5f, ACCENT);
        Draw.circle(ctx, tx + tw * f, y + 6.5f, 5.5f, 0xFFFFFFFF);
        text(ctx, shown, x + w - 34, y + 2, TEXT);
        sliders.add(new Slider(curWin, tx, y, tw, min, max, setter));
    }

    private void updateSlider(Slider s, double lx) {
        float f = Math.max(0f, Math.min(1f, (float) ((lx - s.x()) / s.w())));
        s.set().accept(s.min() + (s.max() - s.min()) * f);
    }

    private void box(DrawContext ctx, TextBox b, int x, int y, int w, int h) {
        boolean f = focus == b;
        Draw.rr(ctx, x - 1, y - 1, w + 2, h + 2, (h + 2) / 2f, f ? ACCENT : BORDER2);
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
                    if (checker) Draw.rect(ctx, x + i * p, y + j * p, p, p, ((i + j) & 1) == 0 ? 0xFF2A2A44 : 0xFF353552);
                    if (a > 0) Draw.rect(ctx, x + i * p, y + j * p, p, p, col);
                    i++;
                }
            }
        }
    }

    private void settingRow(DrawContext ctx, int y, String label, String sub, boolean on, Runnable run) {
        boolean hv = hover(cx, y, cw, 30);
        Draw.rr(ctx, cx, y, cw, 30, 8, hv ? CARD_HOVER : CARD);
        text(ctx, Ui.trim(label, cw - 56), cx + 9, y + 4, TEXT);
        if (sub != null) text(ctx, Ui.trim(sub, cw - 56), cx + 9, y + 17, MUTED);
        addHit(cx, y, cw, 30, run);
        bindable(cx, y, cw, 30, "set:" + label, label);
        keyTag(ctx, "set:" + label, cx + cw - 46, y + 10);
        toggle(ctx, "set:" + label, cx + cw - 40, y + 7, on, run);
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
                float sway = (an == Animation.WHIP || an == Animation.FLOW) ? (float) Math.sin(t * 5) * 3 : 1;
                Draw.rr(ctx, mid + w / 2 - 6, midY, 5, size / 3f, 2, Draw.scale(cc, 0.85f));
                Draw.rr(ctx, mid + w / 2 - 1 + sway, midY + 2, 5, size / 3f, 2, cc);
                Draw.rr(ctx, mid - w / 2, midY - h / 2 - 2, w, h, h / 2, cc);
            }
            case CAPE -> {
                float w = size * 0.55f, h = size * 0.85f, top = midY - h / 2;
                for (int r = 0; r < h; r += 2) {
                    float f = r / h;
                    float off = (an == Animation.WAVE || an == Animation.BILLOW) ? (float) Math.sin(t * 3 + r * 0.15f) * 3 * f : 0;
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
        applyTheme();
        float K = K();
        int lw = (int) (width / K), lh = (int) (height / K);
        mx = (int) (mouseX / K);
        my = (int) (mouseY / K);
        hits.clear();
        rightHits.clear();
        sliders.clear();
        boxes.clear();
        gcell = 0;
        time = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);
        float s = Math.max(2f, Math.min(4f, (float) client.getWindow().getScaleFactor()));

        topAtMouse = null;
        for (int i = WINS.size() - 1; i >= 0; i--) {
            Win w = WINS.get(i);
            if (!w.open) continue;
            int hh = winHeight(w, lh);
            if (mx >= w.x && mx < w.x + w.w && my >= w.y && my < w.y + hh) { topAtMouse = w; break; }
        }

        curWin = null;
        clipY0 = 0;
        clipY1 = lh;
        Draw.begin(ctx, s, K);
        ctx.fillGradient(0, 0, Math.round(width * s), Math.round(height * s), BG1, BG2);
        int padT = 16;
        { int t0 = 0; for (Tab t : Tab.values()) t0 += Ui.width(t.label) + 16 + 3; if (t0 + 80 > lw) padT = 6; }
        int total = 0;
        for (Tab t : Tab.values()) total += Ui.width(t.label) + padT + 3;
        int dx = (lw - total - 44) / 2;
        Draw.rr(ctx, dx - 5, 3, total + 7, 24, 12, DOCK_BG);
        int tx = dx;
        for (Tab t : Tab.values()) {
            int w = Ui.width(t.label) + padT;
            Win win = win(t);
            boolean open = win != null && win.open;
            boolean hv = hover(tx, 7, w, 16);
            if (open) Draw.rr(ctx, tx, 7, w, 16, 8, ACCENT);
            else Draw.rr(ctx, tx, 7, w, 16, 8, hv ? HOVER : DOCK_PILL);
            centered(ctx, t.label, tx + w / 2f, 10, open ? ON_ACCENT : (hv ? TEXT : MUTED));
            final Tab target = t;
            addHit(tx, 7, w, 16, () -> {
                Win ww = win(target);
                if (ww.open && WINS.get(WINS.size() - 1) == ww) ww.open = false;
                else openWindow(target);
            });
            tx += w + 3;
        }
        for (int bi = 0; bi < 2; bi++) {   // menu size - / + (always reachable, even when the menu is huge)
            int bx2 = dx + total + 14 + bi * 20;
            boolean hvb = hover(bx2 - 8, 7, 16, 16);
            Draw.circle(ctx, bx2, 15, 8, hvb ? BTN_HOVER : DOCK_BG);
            centered(ctx, bi == 0 ? "-" : "+", bx2, 10, TEXT);
            final int dir = bi == 0 ? -1 : 1;
            addHit(bx2 - 8, 7, 16, 16, () -> Settings.guiScale = Math.max(0.5f, Math.min(1.3f, Math.round((Settings.guiScale + dir * 0.05f) * 20f) / 20f)));
        }
        Draw.end(ctx);

        for (Win w : new ArrayList<>(WINS)) if (w.open) renderWindow(ctx, w, s, K);
        curWin = null;
        clipY0 = 0;
        clipY1 = lh;

        if (System.currentTimeMillis() < toastUntil) {
            Draw.begin(ctx, s, K);
            int tw = Ui.width(toast) + 24;
            Draw.rr(ctx, lw / 2f - tw / 2f, lh - 34, tw, 20, 10, 0xEE24243C);
            centered(ctx, toast, lw / 2f, lh - 29, TEXT);
            Draw.end(ctx);
        }
    }

    private void renderWindow(DrawContext ctx, Win w, float s, float K) {
        curWin = w;
        int lh = (int) (height / K);
        clipY0 = 0;
        clipY1 = lh;
        int hh = winHeight(w, lh);
        w.y = Math.max(0, Math.min(w.y, Math.max(0, lh - hh)));
        int x = (int) w.x, y = (int) w.y;
        Draw.begin(ctx, s, K);
        Draw.rr(ctx, x - 3, y - 3, w.w + 6, hh + 6, 12, 0x28000000);
        Draw.rr(ctx, x - 1, y - 1, w.w + 2, hh + 2, 10, BORDER);
        Draw.rr(ctx, x, y, w.w, hh, 9, PANEL);
        Draw.circle(ctx, x + 12, y + TITLE / 2f, 3, ACCENT);
        text(ctx, w.tab.label, x + 22, y + 7, TEXT);
        int bx = x + w.w - 14, by = y + TITLE / 2;
        boolean hc = hover(bx - 7, by - 7, 14, 14), hm = hover(bx - 25, by - 7, 14, 14);
        Draw.circle(ctx, bx, by, 5.5f, hc ? DANGER : OFF);
        Draw.circle(ctx, bx - 17, by, 5.5f, hm ? ACCENT2 : OFF);
        addHit(bx - 7, by - 7, 14, 14, () -> w.open = false);
        addHit(bx - 25, by - 7, 14, 14, () -> w.collapsed = !w.collapsed);
        Draw.end(ctx);
        if (w.collapsed) return;

        cx = x + 9;
        cy = y + TITLE + 3;
        cw = w.w - 18;
        ch = hh - TITLE - 9;
        clipY0 = cy;
        clipY1 = cy + ch;
        w.scroll = Math.max(0, Math.min(w.scroll, Math.max(0, w.contentH - ch)));
        int y0 = cy - (int) w.scroll;

        ctx.enableScissor((int) ((x + 3) * K), (int) (cy * K), (int) ((x + w.w - 3) * K), (int) ((cy + ch) * K));
        Draw.begin(ctx, s, K);
        int h = switch (w.tab) {
            case MODS -> renderMods(ctx, w, y0);
            case COSMETICS -> renderCosmetics(ctx, w, y0);
            case SKIES -> renderSkies(ctx, w, y0);
            case MACROS -> renderMacros(ctx, w, y0);
            case EDITOR -> renderEditor(ctx, w, y0);
            case TEXTURES -> renderTextures(ctx, w, y0);
            case BUILDER -> renderBuilder(ctx, w, y0);
            case TRACERS -> renderTracers(ctx, w, y0);
            case EFFECTS -> renderEffects(ctx, w, y0);
            case SEARCH -> renderSearch(ctx, w, y0);
            case FINDER -> renderFinder(ctx, w, y0);
            case FAKE -> renderFake(ctx, w, y0);
            case DONUT -> renderDonut(ctx, w, y0);
            case SPAWNER -> renderSpawner(ctx, w, y0);
        };
        w.contentH = h;
        if (h > ch) {
            int barH = Math.max(20, (int) ((long) ch * ch / h));
            int barY = cy + (int) ((ch - barH) * (w.scroll / Math.max(1, h - ch)));
            Draw.rr(ctx, x + w.w - 7, barY, 3, barH, 1.5f, BTN_HOVER);
        }
        Draw.end(ctx);
        ctx.disableScissor();
        clipY0 = 0;
        clipY1 = lh;
    }

    // ======================================================================== MODS
    private int renderMods(DrawContext ctx, Win w, int y0) {
        int y = y0;
        for (Module m : MyClient.MODULES.all) {
            boolean hasSlider = m.settingLabel != null;
            int h = hasSlider ? 44 : 26;
            boolean hv = hover(cx, y, cw, h);
            Draw.rr(ctx, cx, y, cw, h, 8, hv ? CARD_HOVER : CARD);
            Draw.circle(ctx, cx + 11, y + 13, 3, m.isEnabled() ? ACCENT : OFF);
            text(ctx, Ui.trim(m.name, cw - 70), cx + 22, y + 8, m.isEnabled() ? TEXT : DIM);
            addHit(cx, y, cw, 26, m::toggle);
            bindable(cx, y, cw, 26, "mod:" + m.name, m.name);
            keyTag(ctx, "mod:" + m.name, cx + cw - 46, y + 8);
            toggle(ctx, "mod:" + m.name, cx + cw - 40, y + 5, m.isEnabled(), m::toggle);
            if (hasSlider) {
                slider(ctx, m.settingLabel, cx + 9, y + 27, cw - 18, m.settingValue, m.settingMin, m.settingMax,
                    String.format("%.1f", m.settingValue) + m.settingUnit, v -> m.settingValue = Math.round(v * 10f) / 10f);
            }
            y += h + 4;
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
        pill(ctx, cx, y0, cw, 20, "Upload cape (.png)", true, this::pickCape);
        text(ctx, Ui.trim("or drop a .png onto the window", cw), cx + 2, y0 + 24, MUTED);
        int top = y0 + 40, cardH = 84;
        List<Cosmetic> list = new ArrayList<>(MyClient.COSMETICS.catalog);
        for (int i = 0; i < list.size(); i++) {
            Cosmetic c = list.get(i);
            int x = cx, y = top + i * (cardH + 6);
            if (y + cardH < cy || y > cy + ch) continue;
            boolean on = MyClient.COSMETICS.isEquipped(c);
            boolean hv = hover(x, y, cw, cardH);
            if (on) Draw.rr(ctx, x - 1, y - 1, cw + 2, cardH + 2, 10, ACCENT);
            Draw.rr(ctx, x, y, cw, cardH, 9, hv ? CARD_HOVER : CARD);
            addHit(x, y, cw, cardH, () -> MyClient.COSMETICS.toggle(c));
            bindable(x, y, cw, cardH, "cos:" + c.id, pretty(c.id));
            keyTag(ctx, "cos:" + c.id, x + cw - 46, y + 22);
            Draw.rr(ctx, x + 7, y + 11, 58, 58, 8, TILE);
            drawIcon(ctx, c.type, c.animation, Palette.rgb(c), x + 36, y + 40, 44, c.speed);
            text(ctx, Ui.trim(pretty(c.id), cw - 72 - 44), x + 72, y + 7, TEXT);
            text(ctx, nice(c.type) + (c.type == Type.TRAIL ? "" : " - " + Cosmetic.label(c.type, c.animation)), x + 72, y + 20, MUTED);
            toggle(ctx, "cos:" + c.id, x + cw - 40, y + 6, on, () -> MyClient.COSMETICS.toggle(c));
            for (int v = 0; v < c.variants.size(); v++) {
                String variant = c.variants.get(v);
                int sx = x + 79 + v * 17, sy = y + 44;
                boolean sel = variant.equals(c.activeVariant);
                if (sel) Draw.circle(ctx, sx, sy, 7.5f, 0xFFFFFFFF);
                Draw.circle(ctx, sx, sy, 5.5f, 0xFF000000 | Palette.swatch(c, variant));
                addHit(sx - 8, sy - 8, 16, 16, () -> c.activeVariant = variant);
            }
            pill(ctx, x + 72, y + 58, Math.min(120, cw - 100), 17, "Anim: " + Cosmetic.label(c.type, c.animation), false, c::nextAnimation);
            if (c.custom) {
                int dx = x + cw - 14, dy = y + cardH - 13;
                Draw.circle(ctx, dx, dy, 7, 0xFF3A2433);
                centered(ctx, "x", dx, dy - 5, DANGER);
                addHit(dx - 8, dy - 8, 16, 16, () -> {
                    MyClient.COSMETICS.equipped.remove(c.id);
                    MyClient.COSMETICS.catalog.remove(c);
                    try { if (c.file != null) Files.deleteIfExists(c.file); } catch (Exception ignored) {}
                });
            }
        }
        return 40 + list.size() * (cardH + 6);
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
            case "Blood Moon" -> new int[]{0xFF0A0204, 0xFFC4302B};
            case "Lumen" -> new int[]{0xFF050B1E, 0xFF2B6BD6};
            default -> new int[]{0xFF78A7FF, 0xFFC8E0FF};
        };
    }

    private int renderSkies(DrawContext ctx, Win w, int y0) {
        pill(ctx, cx, y0, cw, 20, "Upload sky (.png)", true, this::pickSky);
        text(ctx, Ui.trim("or drop a .png (2:1 panorama works best)", cw), cx + 2, y0 + 24, MUTED);
        int top = y0 + 40, cardH = 56;
        List<String> skies = new ArrayList<>(MyClient.COSMETICS.skies);
        for (int i = 0; i < skies.size(); i++) {
            String s = skies.get(i);
            int y = top + i * (cardH + 6);
            if (y + cardH < cy || y > cy + ch) continue;
            boolean sel = s.equals(MyClient.COSMETICS.activeSky);
            boolean hv = hover(cx, y, cw, cardH);
            if (sel) Draw.rr(ctx, cx - 1, y - 1, cw + 2, cardH + 2, 10, ACCENT);
            Draw.rr(ctx, cx, y, cw, cardH, 9, hv ? CARD_HOVER : CARD);
            int[] cc = skyColors(s);
            Draw.rrGrad(ctx, cx + 7, y + 7, 62, cardH - 14, 7, cc[0], cc[1]);
            text(ctx, Ui.trim(s, cw - 80 - 26), cx + 78, y + 12, TEXT);
            text(ctx, sel ? "Active" : "Click to use", cx + 78, y + 27, sel ? ACCENT2 : MUTED);
            Draw.circle(ctx, cx + cw - 16, y + 17, 7, sel ? ACCENT : OFF);
            if (sel) Draw.circle(ctx, cx + cw - 16, y + 17, 3, 0xFFFFFFFF);
            addHit(cx, y, cw, cardH, () -> MyClient.COSMETICS.activeSky = s);
            bindable(cx, y, cw, cardH, "sky:" + s, s);
            keyTag(ctx, "sky:" + s, cx + cw - 28, y + 30);
            if (MyClient.COSMETICS.uploadedSkies.containsKey(s)) {
                int dx = cx + cw - 16, dy = y + cardH - 13;
                Draw.circle(ctx, dx, dy, 6.5f, 0xFF3A2433);
                centered(ctx, "x", dx, dy - 5, DANGER);
                addHit(dx - 8, dy - 8, 16, 16, () -> MyClient.COSMETICS.removeSky(s));
            }
        }
        return 40 + skies.size() * (cardH + 6);
    }

    // ======================================================================== MACROS
    private String keyLabel(int code) {
        try { return InputUtil.fromKeyCode(code, 0).getLocalizedText().getString().toUpperCase(); }
        catch (Exception e) { return "KEY " + code; }
    }

    private int renderMacros(DrawContext ctx, Win w, int y0) {
        List<Macro> ms = MyClient.MACROS.macros;
        if (ms.size() < 6) {
            pill(ctx, cx, y0, cw, 20, "+ New macro", true, () -> ms.add(new Macro("Macro " + (ms.size() + 1), GLFW.GLFW_KEY_G, "", 0)));
        }
        if (ms.isEmpty()) centered(ctx, "No macros yet.", cx + cw / 2f, y0 + 70, MUTED);
        for (int i = 0; i < ms.size(); i++) {
            Macro m = ms.get(i);
            int ry = y0 + 28 + i * 60;
            Draw.rr(ctx, cx, ry, cw, 54, 9, CARD);
            text(ctx, Ui.trim(m.name, 64), cx + 9, ry + 8, TEXT);
            pill(ctx, cx + 76, ry + 4, 60, 18, capturing == m ? "press key" : keyLabel(m.keyCode), capturing == m, () -> capturing = m);
            toggle(ctx, "mac:" + System.identityHashCode(m), cx + cw - 64, ry + 5, m.enabled, () -> m.enabled = !m.enabled);
            int dx = cx + cw - 15, dy = ry + 13;
            boolean hv = hover(dx - 9, dy - 9, 18, 18);
            Draw.circle(ctx, dx, dy, 7.5f, hv ? 0xFF4A2A3C : 0xFF2A2236);
            centered(ctx, "x", dx, dy - 5, DANGER);
            addHit(dx - 9, dy - 9, 18, 18, () -> ms.remove(m));
            TextBox b = macroBoxes.computeIfAbsent(m, k -> {
                TextBox nb = new TextBox("/command or message");
                nb.set(k.command);
                nb.onChange = s -> k.command = s;
                return nb;
            });
            box(ctx, b, cx + 7, ry + 28, cw - 14, 20);
        }
        return 28 + Math.max(1, ms.size()) * 60;
    }

    // ======================================================================== EDITOR
    private int renderEditor(DrawContext ctx, Win w, int y0) {
        int rgb = ((int) dr << 16) | ((int) dg << 8) | (int) db;
        Preview.update(draftType, draftAnim, rgb, dspeed);

        Draw.rr(ctx, cx, y0, cw, 84, 10, TILE);
        drawIcon(ctx, draftType, draftAnim, rgb, cx + cw / 2f, y0 + 42, 62, dspeed);
        text(ctx, "Preview", cx + 8, y0 + 6, MUTED);

        text(ctx, "Show on my character (third person)", cx + 2, y0 + 92, TEXT);
        toggle(ctx, "preview", cx + cw - 36, y0 + 102, Preview.enabled, () -> {
            Preview.enabled = !Preview.enabled;
            if (Preview.enabled) client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        });
        text(ctx, Preview.enabled ? "Live on your character - move the sliders" : "Turn on to see it on you", cx + 2, y0 + 106, MUTED);

        int y = y0 + 126;
        box(ctx, nameBox, cx, y, cw, 22);
        int half = (cw - 6) / 2;
        pill(ctx, cx, y + 30, half, 20, "Type: " + nice(draftType), false, () -> {
            Type[] all = {Type.HALO, Type.HEADBAND, Type.CAPE, Type.WINGS, Type.GLASSES, Type.AURA};
            int i = 0;
            for (int k = 0; k < all.length; k++) if (all[k] == draftType) i = k;
            draftType = all[(i + 1) % all.length];
            draftAnim = Cosmetic.defaultAnims(draftType).get(0);
        });
        pill(ctx, cx + half + 6, y + 30, half, 20, Cosmetic.label(draftType, draftAnim), false, () -> {
            List<Animation> l = Cosmetic.defaultAnims(draftType);
            draftAnim = l.get((Math.max(0, l.indexOf(draftAnim)) + 1) % l.size());
        });
        slider(ctx, "Red", cx, y + 60, cw, dr, 0, 255, String.valueOf((int) dr), v -> dr = v);
        slider(ctx, "Green", cx, y + 80, cw, dg, 0, 255, String.valueOf((int) dg), v -> dg = v);
        slider(ctx, "Blue", cx, y + 100, cw, db, 0, 255, String.valueOf((int) db), v -> db = v);
        slider(ctx, "Speed", cx, y + 120, cw, dspeed, 0.2f, 3f, String.format("%.1fx", dspeed), v -> dspeed = v);
        pill(ctx, cx, y + 146, cw, 22, "Create accessory", true, this::createDraft);
        return y + 176 - y0;
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
        Cosmetic c = new Cosmetic(id, draftType, List.of("custom"), Cosmetic.defaultAnims(draftType));
        c.animation = draftAnim;
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
        pill(ctx, cx, ry, cw - 90, 20, TextureLab.dirtyCount() > 0 ? "Apply + reload (" + TextureLab.dirtyCount() + ")" : "Apply + reload", true, this::applyTextures);
        text(ctx, "Pack", cx + cw - 82, ry + 5, MUTED);
        toggle(ctx, "texpack", cx + cw - 36, ry + 2, TextureLab.isEnabled(), () -> {
            if (!TextureLab.packExists()) { toast("Edit a texture and press Apply first"); return; }
            TextureLab.setEnabled(!TextureLab.isEnabled());
            toast(TextureLab.isEnabled() ? "Texture pack on" : "Texture pack off");
        });

        int ly = y0 + 58, rows = 5, lh = rows * 19 + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 9, CARD);
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, texFiltered.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= texFiltered.size()) break;
            String nm = texFiltered.get(idx);
            int rr = ly + 4 + r * 19;
            boolean sel = nm.equals(selTex), hv = hover(cx + 4, rr, cw - 8, 17);
            if (sel) Draw.rr(ctx, cx + 4, rr, cw - 8, 17, 8, ACCENT);
            else if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, 17, 8, HOVER);
            text(ctx, Ui.trim(nm, cw - 40), cx + 11, rr + 4, sel ? ON_ACCENT : TEXT);
            if (TextureLab.isDirty(nm)) Draw.circle(ctx, cx + cw - 14, rr + 8.5f, 3, ACCENT2);
            addHit(cx + 4, rr, cw - 8, 17, () -> selTex = nm);
        }
        if (texFiltered.isEmpty()) text(ctx, "No textures found", cx + 11, ly + 12, MUTED);
        text(ctx, texFiltered.size() + " block textures (scroll the list)", cx + 3, ly + lh + 3, MUTED);

        int ty = ly + lh + 19;
        BufferedImage img = selTex == null ? null : TextureLab.get(selTex);
        int bottom = ty + 20;
        if (img != null) {
            int size = TextureLab.size(img);
            int cell = Math.max(2, 112 / size), gp = cell * size;
            gx = cx + 3; gy = ty + 15; gcell = cell; gsize = size;
            text(ctx, Ui.trim(pretty(selTex) + " (" + size + "x" + size + (img.getHeight() > img.getWidth() ? ", frame 1" : "") + ")", cw), cx + 2, ty, TEXT);
            Draw.rr(ctx, gx - 3, gy - 3, gp + 6, gp + 6, 7, TILE);
            drawImage(ctx, img, size, gx, gy, cell, true);

            int tx = gx + gp + 12, tw = Math.min(88, cx + cw - tx);
            int i = 0;
            for (Tool t : Tool.values()) {
                final Tool tt = t;
                pill(ctx, tx, gy + i * 20, tw, 17, t.label, tool == t, () -> tool = tt);
                i++;
            }
            pill(ctx, tx, gy + i * 20, tw / 2 - 2, 17, "Undo", false, () -> { if (selTex != null) TextureLab.undo(selTex); });
            pill(ctx, tx + tw / 2 + 2, gy + i * 20, tw / 2 - 2, 17, "Reset", false, () -> {
                if (selTex != null) { TextureLab.reset(selTex); toast("Reset - press Apply to reload the original"); }
            });
            pill(ctx, tx, gy + (i + 1) * 20, tw / 2 - 2, 17, "Copy", false, () -> {
                if (selTex != null && TextureLab.copy(selTex)) toast("Copied " + selTex);
            });
            pill(ctx, tx + tw / 2 + 2, gy + (i + 1) * 20, tw / 2 - 2, 17, "Paste", false, () -> {
                if (selTex == null) return;
                if (TextureLab.paste(selTex)) toast("Pasted onto " + selTex + " - press Apply");
                else toast("Copy a texture first");
            });
            float pp = Math.max(1f, 22f / size);
            Draw.rr(ctx, tx - 3, gy + 123, pp * size * 2 + 6, pp * size * 2 + 6, 5, TILE);
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++)
                drawImage(ctx, img, size, tx + a * pp * size, gy + 126 + b * pp * size, pp, false);

            int sy = gy + gp + 11;
            slider(ctx, "Red", cx, sy, cw - 30, texR, 0, 255, String.valueOf((int) texR), v -> texR = v);
            slider(ctx, "Green", cx, sy + 19, cw - 30, texG, 0, 255, String.valueOf((int) texG), v -> texG = v);
            slider(ctx, "Blue", cx, sy + 38, cw - 30, texB, 0, 255, String.valueOf((int) texB), v -> texB = v);
            Draw.rr(ctx, cx + cw - 24, sy + 3, 22, 28, 7, 0xFF000000 | ((int) texR << 16) | ((int) texG << 8) | (int) texB);
            text(ctx, "Clipboard: " + TextureLab.clipInfo(), cx + 2, sy + 58, MUTED);
            bottom = sy + 74;
        } else {
            text(ctx, "Pick a block texture above", cx + 2, ty, MUTED);
        }
        return bottom - y0 + 6;
    }

    private boolean gridPaint(double lx, double ly) {
        if (selTex == null || gcell <= 0) return false;
        int px = (int) Math.floor((lx - gx) / gcell), py = (int) Math.floor((ly - gy) / gcell);
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
        int ly = y0 + 30, rows = 5, lh = rows * 19 + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 9, CARD);
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, schemFiltered.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= schemFiltered.size()) break;
            Path p = schemFiltered.get(idx);
            String nm = p.getFileName().toString().replaceFirst("\\.litematic$", "");
            int rr = ly + 4 + r * 19;
            boolean sel = SchematicBuilder.loaded != null && SchematicBuilder.loaded.name.equals(nm);
            boolean hv = hover(cx + 4, rr, cw - 8, 17);
            if (sel) Draw.rr(ctx, cx + 4, rr, cw - 8, 17, 8, ACCENT);
            else if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, 17, 8, HOVER);
            text(ctx, Ui.trim(nm, cw - 28), cx + 11, rr + 4, sel ? ON_ACCENT : TEXT);
            addHit(cx + 4, rr, cw - 8, 17, () -> loadSchematic(p));
        }
        if (schemFiltered.isEmpty()) {
            text(ctx, schemFiles.isEmpty() ? "No .litematic files found" : "No match", cx + 11, ly + 10, MUTED);
            if (schemFiles.isEmpty()) text(ctx, "Put them in .minecraft/schematics", cx + 11, ly + 23, MUTED);
        }

        int iy = ly + lh + 7, ih = 142;
        Draw.rr(ctx, cx, iy, cw, ih, 9, CARD);
        int yy = iy + 8;
        if (SchematicBuilder.loaded == null) {
            text(ctx, "Select a schematic above.", cx + 9, yy, MUTED);
        } else {
            text(ctx, Ui.trim(SchematicBuilder.loaded.name, cw - 18), cx + 9, yy, TEXT);
            text(ctx, SchematicBuilder.loaded.sizeX + "x" + SchematicBuilder.loaded.sizeY + "x" + SchematicBuilder.loaded.sizeZ
                + " - " + SchematicBuilder.loaded.count() + " blocks", cx + 9, yy + 12, MUTED);
            float prog = SchematicBuilder.total() == 0 ? 0f : SchematicBuilder.placed() / (float) SchematicBuilder.total();
            Draw.rr(ctx, cx + 9, yy + 27, cw - 18, 6, 3, OFF);
            Draw.rr(ctx, cx + 9, yy + 27, Math.max(6, (cw - 18) * prog), 6, 3, ACCENT2);
            String o = SchematicBuilder.origin == null ? "Origin: not set"
                : "Origin: " + SchematicBuilder.origin.getX() + " " + SchematicBuilder.origin.getY() + " " + SchematicBuilder.origin.getZ();
            text(ctx, o, cx + 9, yy + 39, TEXT);
            pill(ctx, cx + 9, yy + 53, cw - 18, 17, "Set origin 2 blocks ahead", false, () -> {
                SchematicBuilder.setOriginHere();
                toast("Origin set to your position");
            });
            text(ctx, "Build", cx + 9, yy + 79, TEXT);
            toggle(ctx, "build", cx + cw - 42, yy + 76, SchematicBuilder.running, () -> SchematicBuilder.setRunning(!SchematicBuilder.running));
            bindable(cx + 9, yy + 74, cw - 18, 20, "set:Build schematic", "Build schematic");
            keyTag(ctx, "set:Build schematic", cx + cw - 48, yy + 79);
            text(ctx, "Auto-move + look", cx + 9, yy + 98, TEXT);
            toggle(ctx, "walk", cx + cw - 42, yy + 95, SchematicBuilder.walk, () -> SchematicBuilder.walk = !SchematicBuilder.walk);
            pill(ctx, cx + 9, yy + 115, cw - 18, 17, "Restart", false, SchematicBuilder::restart);
        }
        int sy = iy + ih + 7, lines = 0;
        for (String line : wrap(SchematicBuilder.status, cw - 4)) {
            text(ctx, line, cx + 2, sy + lines * 11, SchematicBuilder.missing.isEmpty() ? ACCENT2 : 0xFFFFB454);
            lines++;
        }
        text(ctx, "Last click: " + SchematicBuilder.lastClick, cx + 2, sy + lines * 11, MUTED);
        lines++;
        for (String line : wrap("Walks, looks at each block and places it like a player, bottom to top, from your hotbar. Many servers forbid this.", cw - 4)) {
            text(ctx, line, cx + 2, sy + lines * 11 + 4, MUTED);
            lines++;
        }
        return sy + lines * 11 + 10 - y0;
    }

    // ======================================================================== TRACERS
    private void flipBlock(String id) {
        if (editXray) MyClient.MODULES.xray.toggleBlock(id); else MyClient.MODULES.tracers.toggleBlock(id);
    }

    private int renderTracers(DrawContext ctx, Win w, int y0) {
        dev.myclient.module.Tracers tr = MyClient.MODULES.tracers;
        Module xr = MyClient.MODULES.xray;
        settingRow(ctx, y0, "Tracers", "Lines to your blocks", tr.isEnabled(), tr::toggle);
        settingRow(ctx, y0 + 34, "X-Ray boxes", "Boxes through walls", xr.isEnabled(), xr::toggle);
        slider(ctx, "Range", cx, y0 + 72, cw, tr.radius, 16, 512, tr.radius + "m", v -> { tr.radius = Math.round(v / 8f) * 8; tr.changed(); });

        int y = y0 + 96;
        pill(ctx, cx, y, cw, 20, "Editing: " + (editXray ? "X-Ray blocks" : "Tracer blocks") + " - click to switch", true, () -> editXray = !editXray);
        y += 26;
        java.util.Set<String> set = editXray ? MyClient.MODULES.xray.blocks : tr.blocks;
        text(ctx, "Selected (" + set.size() + ") - click to remove", cx + 2, y, MUTED);
        y += 13;
        if (set.isEmpty()) {
            text(ctx, "None yet - pick blocks below", cx + 2, y + 2, MUTED);
            y += 18;
        } else {
            int x = cx;
            for (String id : new ArrayList<>(set)) {
                String label = pretty(id);
                int cwid = Ui.width(label) + 22;
                if (x + cwid > cx + cw) { x = cx; y += 20; }
                boolean hv = hover(x, y, cwid, 16);
                Draw.rr(ctx, x, y, cwid, 16, 8, hv ? 0xFF4A2A3C : CHIP);
                Draw.circle(ctx, x + 8, y + 8, 3, 0xFF000000 | Palette.rainbow((id.hashCode() & 0x7fffffff) % 360 / 360f, 0.65f, 1f));
                text(ctx, label, x + 15, y + 3, TEXT);
                addHit(x, y, cwid, 16, () -> flipBlock(id));
                x += cwid + 4;
            }
            y += 24;
        }

        box(ctx, tracerBox, cx, y, cw, 22);
        int ly = y + 28, rows = 6, rh = 22, lh = rows * rh + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 9, CARD);
        if (blockFiltered == null) refilterBlocks();
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, blockFiltered.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= blockFiltered.size()) break;
            String id = blockFiltered.get(idx);
            int rr = ly + 4 + r * rh;
            boolean on = set.contains(id), hv = hover(cx + 4, rr, cw - 8, rh - 2);
            if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, rh - 2, 8, HOVER);
            text(ctx, Ui.trim(pretty(id), cw - 62), cx + 11, rr + 5, TEXT);
            addHit(cx + 4, rr, cw - 8, rh - 2, () -> flipBlock(id));
            toggle(ctx, "tb:" + (editXray ? "x" : "t") + id, cx + cw - 42, rr + 2, on, () -> flipBlock(id));
        }
        text(ctx, blockFiltered.size() + " blocks (scroll the list)", cx + 3, ly + lh + 3, MUTED);
        return ly + lh + 18 - y0;
    }

    // ======================================================================== EFFECTS
    private int renderEffects(DrawContext ctx, Win w, int y0) {
        int y = y0;
        settingRow(ctx, y, "Jump effect", "Ring + sparks on jump", Settings.jumpFx, () -> Settings.jumpFx = !Settings.jumpFx); y += 34;
        settingRow(ctx, y, "Hit effect", "Burst when you hit", Settings.hitFx, () -> Settings.hitFx = !Settings.hitFx); y += 34;
        settingRow(ctx, y, "Damage flash", "Red flash when hurt", Settings.hurtFlash, () -> Settings.hurtFlash = !Settings.hurtFlash); y += 34;
        settingRow(ctx, y, "Low fire", "Fire overlay sits lower", Settings.lowFire, () -> Settings.lowFire = !Settings.lowFire); y += 34;
        settingRow(ctx, y, "Low shield", "Shield sits lower", Settings.lowShield, () -> Settings.lowShield = !Settings.lowShield); y += 34;
        settingRow(ctx, y, "Small totem pop", "Hides the big totem", Settings.smallTotem, () -> Settings.smallTotem = !Settings.smallTotem); y += 34;
        settingRow(ctx, y, "No vanilla particles", "Removes all of them", Settings.noParticles, () -> Settings.noParticles = !Settings.noParticles); y += 40;
        pill(ctx, cx, y, cw, 20, "Theme: " + Theme.current().name + " (click to change)", true, () -> Settings.theme = (Settings.theme + 1) % Theme.ALL.length);
        y += 28;
        float shown = pendingScale > 0 ? pendingScale : Settings.guiScale;
        slider(ctx, "Menu size", cx, y, cw, shown, 0.5f, 1.3f, String.format("%.2f", shown), v -> pendingScale = Math.round(v * 20f) / 20f);
        y += 22;
        text(ctx, "Applies when you let go", cx + 2, y, MUTED);
        return y + 16 - y0;
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
        box(ctx, searchBox, cx, y0, cw, 24);
        List<Result> res = results(searchBox.text);
        int ly = y0 + 30, rows = 9, rh = 22, lh = rows * rh + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 9, CARD);
        if (searchBox.text.isBlank()) {
            int yy = ly + 10;
            for (String line : wrap("Type to find modules, cosmetics, skies, macros, schematics, block tracers, block textures and windows.", cw - 22)) {
                text(ctx, line, cx + 11, yy, MUTED);
                yy += 11;
            }
        } else if (res.isEmpty()) {
            text(ctx, "Nothing found", cx + 11, ly + 11, MUTED);
        }
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, res.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= res.size()) break;
            Result rs = res.get(idx);
            int rr = ly + 4 + r * rh;
            boolean hv = hover(cx + 4, rr, cw - 8, rh - 2);
            if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, rh - 2, 8, HOVER);
            int cwid = Ui.width(rs.cat()) + 12;
            Draw.rr(ctx, cx + 9, rr + 3, cwid, 14, 7, CHIP);
            text(ctx, rs.cat(), cx + 15, rr + 6, ACCENT2);
            text(ctx, Ui.trim(rs.label(), cw - cwid - 50), cx + 16 + cwid, rr + 6, TEXT);
            Draw.circle(ctx, cx + cw - 15, rr + 10, 4.5f, rs.on() ? ACCENT : OFF);
            addHit(cx + 4, rr, cw - 8, rh - 2, rs.run());
        }
        if (res.size() > 0) text(ctx, res.size() + " results", cx + 3, ly + lh + 3, MUTED);
        return ly + lh + 18 - y0;
    }

    // ======================================================================== SPAWNER
    private int renderSpawner(DrawContext ctx, Win w, int y0) {
        if (spawnerFiltered == null) refilterSpawner();
        int y = y0;
        settingRow(ctx, y, "Fake spawner", "Chosen block = skeleton spawner", Settings.fakeSpawner, () -> { Settings.fakeSpawner = !Settings.fakeSpawner; applyFake(); });
        y += 36;
        text(ctx, "Block: " + pretty(Settings.spawnerBlock), cx + 2, y, TEXT);
        y += 14;
        for (String line : wrap("Place that block: it looks like a skeleton spawner and plays the spawner sound. Right click it for the spawner menu. Bones ($150) and arrows ($3) build up over time and can be sold. Sneak + right click places blocks next to it. Blocks of that type you did not place stay normal blocks.", cw - 4)) {
            text(ctx, line, cx + 2, y, MUTED);
            y += 11;
        }
        y += 4;
        text(ctx, "Your fake spawners here: " + FakeSpawners.count(), cx + 2, y, TEXT);
        y += 16;
        box(ctx, spawnerBox, cx, y, cw, 22);
        int ly = y + 28, rows = 6, rh = 22, lh = rows * rh + 8;
        w.lx = cx; w.ly = ly; w.lw = cw; w.lh = lh;
        Draw.rr(ctx, cx, ly, cw, lh, 9, CARD);
        w.listScroll = Math.max(0, Math.min(w.listScroll, Math.max(0, spawnerFiltered.size() - rows)));
        for (int r = 0; r < rows; r++) {
            int idx = w.listScroll + r;
            if (idx >= spawnerFiltered.size()) break;
            String id = spawnerFiltered.get(idx);
            int rr = ly + 4 + r * rh;
            boolean sel = id.equals(Settings.spawnerBlock), hv = hover(cx + 4, rr, cw - 8, rh - 2);
            if (sel) Draw.rr(ctx, cx + 4, rr, cw - 8, rh - 2, 8, ACCENT);
            else if (hv) Draw.rr(ctx, cx + 4, rr, cw - 8, rh - 2, 8, HOVER);
            text(ctx, Ui.trim(pretty(id), cw - 30), cx + 11, rr + 5, sel ? ON_ACCENT : TEXT);
            addHit(cx + 4, rr, cw - 8, rh - 2, () -> {
                Settings.spawnerBlock = id;
                if (Settings.fakeSpawner) applyFake();
            });
        }
        text(ctx, spawnerFiltered.size() + " blocks (scroll the list)", cx + 3, ly + lh + 3, MUTED);
        return ly + lh + 18 - y0;
    }

    // ======================================================================== FINDER
    private int renderFinder(DrawContext ctx, Win w, int y0) {
        SusChunkFinder f = MyClient.MODULES.susChunks;
        settingRow(ctx, y0, "Sus Chunk Finder", "Grown amethyst buds -> red chunk", f.isEnabled(), f::toggle);
        int y = y0 + 38;
        for (String line : wrap("Scans every loaded chunk from bedrock to the sky for amethyst clusters and large amethyst buds and marks that chunk red (in the world and through walls).", cw - 4)) {
            text(ctx, line, cx + 2, y, MUTED);
            y += 11;
        }
        y += 4;
        text(ctx, "Chunks scanned: " + f.scanned + "   Sus chunks: " + f.found.size(), cx + 2, y, TEXT);
        y += 13;
        text(ctx, "Chunks with any amethyst: " + f.anyAmethyst.size(), cx + 2, y, TEXT);
        y += 13;
        for (String line : wrap("If this stays 0 on a server although geodes exist, the server does not send those blocks to your game (anti-xray) - nothing can find them then.", cw - 4)) {
            text(ctx, line, cx + 2, y, MUTED);
            y += 11;
        }
        y += 6;
        List<SusChunkFinder.Sus> list = new ArrayList<>(f.found.values());
        if (client.player != null) {
            final double px = client.player.getX(), pz = client.player.getZ();
            list.sort(Comparator.comparingDouble(c -> Math.pow(c.cx * 16 + 8 - px, 2) + Math.pow(c.cz * 16 + 8 - pz, 2)));
        }
        if (list.isEmpty()) text(ctx, f.isEnabled() ? "Nothing found yet" : "Turn it on above", cx + 2, y, MUTED);
        int shown = 0;
        for (SusChunkFinder.Sus c : list) {
            if (shown++ >= 40) break;
            boolean hv = hover(cx, y, cw, 26);
            Draw.rr(ctx, cx, y, cw, 26, 8, hv ? CARD_HOVER : CARD);
            Draw.circle(ctx, cx + 11, y + 13, 4, 0xFFFF3030);
            text(ctx, "Chunk " + c.cx + ", " + c.cz, cx + 22, y + 4, TEXT);
            text(ctx, "Block " + c.cx * 16 + ", " + c.cz * 16 + "  -  " + c.count + " buds", cx + 22, y + 15, MUTED);
            addHit(cx, y, cw, 26, () -> toast("Chunk " + c.cx + ", " + c.cz + " -> go to X " + (c.cx * 16 + 8) + "  Z " + (c.cz * 16 + 8)));
            y += 30;
        }
        return y - y0 + 6;
    }

    // ======================================================================== FAKE (textures + knife)
    private void applyFake() {
        try {
            FakePack.apply();
            toast("Applying - the game reloads resources...");
        } catch (Exception e) {
            toast("Could not write the pack");
        }
    }

    private int renderFake(DrawContext ctx, Win w, int y0) {
        int y = y0;
        settingRow(ctx, y, "Fake blocks", "Blocks look like other blocks", Settings.fakeBlocks, () -> { Settings.fakeBlocks = !Settings.fakeBlocks; applyFake(); });
        y += 36;
        text(ctx, "Blocks that change:", cx + 2, y, MUTED);
        y += 14;
        for (FakePack.Swap s : FakePack.SWAPS) {
            List<String> notes = wrap("Affects: " + s.affects(), cw - 20);
            int h = 22 + notes.size() * 11;
            Draw.rr(ctx, cx, y, cw, h, 8, CARD);
            text(ctx, s.from() + "  ->  " + s.to(), cx + 9, y + 5, Settings.fakeBlocks ? TEXT : DIM);
            int ny = y + 18;
            for (String n : notes) { text(ctx, n, cx + 9, ny, MUTED); ny += 11; }
            y += h + 4;
        }
        y += 8;
        settingRow(ctx, y, "Butterfly knife", "Every sword becomes a knife", Settings.butterfly, () -> { Settings.butterfly = !Settings.butterfly; applyFake(); });
        y += 36;
        for (String line : wrap("The knife flips when you swing (first person). Pack folder: resourcepacks/MyClientFake.", cw - 4)) {
            text(ctx, line, cx + 2, y, MUTED);
            y += 11;
        }
        return y + 8 - y0;
    }

    // ======================================================================== DONUT (fake scoreboard + fake /pay)
    private int renderDonut(DrawContext ctx, Win w, int y0) {
        initDonut();
        if (focus != donutMoney) {
            donutMoney.text = String.valueOf((long) Settings.fakeMoney);
            donutMoney.cursor = Math.min(donutMoney.cursor, donutMoney.text.length());
        }
        int y = y0;
        settingRow(ctx, y, "Fake scoreboard", "Only on your screen", Settings.fakeScoreboard, () -> Settings.fakeScoreboard = !Settings.fakeScoreboard);
        y += 34;
        settingRow(ctx, y, "Hide real scoreboard", "Hides the server's board", Settings.hideRealScoreboard, () -> Settings.hideRealScoreboard = !Settings.hideRealScoreboard);
        y += 38;
        text(ctx, "Title", cx + 2, y, MUTED);
        box(ctx, donutTitle, cx, y + 12, cw, 22);
        y += 40;
        text(ctx, "Money (use {money} in a line)", cx + 2, y, MUTED);
        box(ctx, donutMoney, cx, y + 12, cw, 22);
        y += 40;
        text(ctx, "Lines (& = colors, e.g. &a green)", cx + 2, y, MUTED);
        y += 13;
        for (int i = 0; i < donutLines.length; i++) {
            box(ctx, donutLines[i], cx, y, cw, 20);
            y += 24;
        }
        pill(ctx, cx, y, cw, 20, "Load DonutSMP-style preset", false, DonutPresetRunner::run);
        y += 34;
        settingRow(ctx, y, "Fake /pay", "/pay is not sent, only shown", Settings.fakePay, () -> Settings.fakePay = !Settings.fakePay);
        y += 34;
        settingRow(ctx, y, "Deduct fake money", "Fake scoreboard money goes down", Settings.payDeduct, () -> Settings.payDeduct = !Settings.payDeduct);
        y += 34;
        settingRow(ctx, y, "Also in chat", "Pay message in chat too", Settings.payInChat, () -> Settings.payInChat = !Settings.payInChat);
        y += 38;
        text(ctx, "Pay message ({player} {amount})", cx + 2, y, MUTED);
        box(ctx, donutPay, cx, y + 12, cw, 22);
        y += 42;
        text(ctx, "Earn message ({amount}) - spawner sales", cx + 2, y, MUTED);
        box(ctx, donutEarn, cx, y + 12, cw, 22);
        y += 42;
        for (String line : wrap("Visual only: nothing is sent to the server and nobody else sees it. Do not use it to mislead other players.", cw - 4)) {
            text(ctx, line, cx + 2, y, MUTED);
            y += 11;
        }
        return y + 8 - y0;
    }

    private static final class DonutPresetRunner {
        static void run() { donutPreset(); }
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
            int hh = winHeight(w, (int) (height / K()));
            if (x >= w.x && x < w.x + w.w && y >= w.y && y < w.y + hh) return w;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            double rx = mouseX / K(), ry = mouseY / K();
            Win rw = windowAt(rx, ry);
            if (rw != null) {
                for (int i = rightHits.size() - 1; i >= 0; i--) {
                    RightHit h = rightHits.get(i);
                    if (h.win() == rw && rx >= h.x() && rx < h.x() + h.w() && ry >= h.y() && ry < h.y() + h.h()) {
                        Keybinds.startCapture(h.id(), h.label());
                        toast("Press a key for \"" + h.label() + "\"  (Esc = cancel, Backspace = remove)");
                        return true;
                    }
                }
            }
            return true;
        }
        if (button != 0) return true;
        double lx = mouseX / K(), ly = mouseY / K();
        Win w = windowAt(lx, ly);
        if (w == null) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.win() == null && h.in(lx, ly)) { focus = null; h.run().run(); return true; }
            }
            focus = null;
            return true;
        }
        WINS.remove(w);
        WINS.add(w);

        for (BoxRect b : boxes) {
            if (b.win() == w && lx >= b.x() && lx < b.x() + b.w() && ly >= b.y() && ly < b.y() + b.h()) {
                focus = b.box();
                b.box().cursor = b.box().text.length();
                return true;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.win() == w && h.in(lx, ly)) {
                focus = null;
                h.run().run();
                return true;
            }
        }
        for (Slider s : sliders) {
            if (s.win() == w && lx >= s.x() - 6 && lx <= s.x() + s.w() + 6 && ly >= s.y() && ly < s.y() + 13) {
                dragging = s;
                updateSlider(s, lx);
                return true;
            }
        }
        if (w.tab == Tab.TEXTURES && gcell > 0 && selTex != null && !w.collapsed
            && lx >= gx && lx < gx + gcell * gsize && ly >= gy && ly < gy + gcell * gsize
            && ly >= cy && ly < cy + ch) {
            if (tool != Tool.PICK) TextureLab.beginStroke(selTex);
            painting = true;
            gridPaint(lx, ly);
            return true;
        }
        if (ly < w.y + TITLE) {
            dragWin = w;
            dragOffX = (float) (lx - w.x);
            dragOffY = (float) (ly - w.y);
            return true;
        }
        focus = null;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        double lx = mouseX / K(), ly = mouseY / K();
        if (dragging != null) { updateSlider(dragging, lx); return true; }
        if (painting && (tool == Tool.PENCIL || tool == Tool.ERASER)) { gridPaint(lx, ly); return true; }
        if (dragWin != null) {
            dragWin.x = Math.max(0, Math.min((float) (lx - dragOffX), Math.max(0, width / K() - dragWin.w)));
            dragWin.y = Math.max(0, Math.min((float) (ly - dragOffY), Math.max(0, height / K() - TITLE)));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        painting = false;
        dragWin = null;
        if (pendingScale > 0) {
            Settings.guiScale = pendingScale;
            pendingScale = -1f;
            init();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double lx = mouseX / K(), ly = mouseY / K();
        Win w = windowAt(lx, ly);
        if (w == null || w.collapsed) return true;
        boolean overList = w.lw > 0 && lx >= w.lx && lx < w.lx + w.lw && ly >= w.ly && ly < w.ly + w.lh
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
        if (Keybinds.capturingId != null) {
            String id = Keybinds.capturingId, lab = Keybinds.capturingLabel;
            Keybinds.capturingId = null;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) toast("Cancelled");
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) { Keybinds.bind(id, -1); toast("Key removed: " + lab); }
            else { Keybinds.bind(id, keyCode); toast(lab + " -> " + Keybinds.keyName(keyCode)); }
            return true;
        }
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
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            Settings.guiScale = Math.max(0.5f, Math.round((Settings.guiScale - 0.05f) * 20f) / 20f);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            Settings.guiScale = Math.min(1.3f, Math.round((Settings.guiScale + 0.05f) * 20f) / 20f);
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
