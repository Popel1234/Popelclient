package dev.myclient.gui;

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
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ClientScreen extends Screen {

    private enum Tab {
        MODS("Mods"), COSMETICS("Cosmetics"), SKIES("Skies"), MACROS("Macros"),
        EDITOR("Editor"), TEXTURES("Textures"), BUILDER("Builder");
        final String label;
        Tab(String l) { label = l; }
    }

    private enum Tool {
        PENCIL("Pencil"), ERASER("Eraser"), FILL("Fill"), PICK("Pick color");
        final String label;
        Tool(String l) { label = l; }
    }

    private static final Type[] EDITABLE = {Type.HALO, Type.HEADBAND, Type.CAPE, Type.WINGS, Type.GLASSES, Type.AURA};

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
    private final int[] contentH = new int[Tab.values().length];

    private int px, py, pw, ph;
    private int cx, cy, cw, ch;
    private int clipY0, clipY1;
    private int mx, my;
    private float time;

    // toast
    private String toast = "";
    private long toastUntil;

    // macros
    private Macro capturing;
    private final List<TextFieldWidget> macroFields = new ArrayList<>();

    // accessory editor
    private TextFieldWidget draftName;
    private String draftNameText = "";
    private Type draftType = Type.HALO;
    private Animation draftAnim = Animation.SPIN;
    private float dr = 124, dg = 92, db = 255, dspeed = 1f;

    // texture editor
    private TextFieldWidget texSearch;
    private String texQuery = "";
    private List<String> texFiltered = new ArrayList<>();
    private int texListScroll;
    private String selTex;
    private Tool tool = Tool.PENCIL;
    private float texR = 255, texG = 255, texB = 255;
    private int gx, gy, gcell, gsize;
    private boolean painting;
    private int listX, listY, listW, listH;

    // schematic builder
    private TextFieldWidget schemSearch;
    private String schemQuery = "";
    private List<Path> schemFiles = new ArrayList<>();
    private List<Path> schemFiltered = new ArrayList<>();
    private int schemListScroll;

    public ClientScreen() {
        super(Text.literal("MyClient"));
        tab = lastTab;
    }

    // ======================================================================== setup
    @Override
    protected void init() {
        pw = Math.min(width - 40, 470);
        ph = Math.min(height - 40, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 18;
        cy = py + 80;
        cw = pw - 36;
        ch = ph - 80 - 14;
        clearChildren();
        macroFields.clear();
        draftName = null;
        texSearch = null;
        schemSearch = null;
        if (tab == Tab.MACROS) buildMacroFields();
        if (tab == Tab.EDITOR) buildEditorFields();
        if (tab == Tab.TEXTURES) buildTextureFields();
        if (tab == Tab.BUILDER) buildBuilderFields();
    }

    private TextFieldWidget field(int w, String placeholder, String initial) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, 0, 0, w, 14, Text.empty());
        f.setDrawsBackground(false);
        f.setMaxLength(120);
        f.setText(initial);
        f.setPlaceholder(Text.literal(placeholder));
        addDrawableChild(f);
        return f;
    }

    private void buildMacroFields() {
        for (Macro m : MyClient.MACROS.macros) {
            TextFieldWidget f = field(Math.max(60, cw - 300), "/command or message", m.command);
            f.setChangedListener(s -> m.command = s);
            macroFields.add(f);
        }
    }

    private void buildEditorFields() {
        int fw = Math.min(250, cw / 2 - 10);
        draftName = field(fw - 24, "My accessory", draftNameText);
        draftName.setMaxLength(24);
        draftName.setChangedListener(s -> draftNameText = s);
    }

    private void buildTextureFields() {
        texSearch = field(156, "Search blocks...", texQuery);
        texSearch.setChangedListener(s -> { texQuery = s.toLowerCase().trim(); refilterTextures(); });
        refilterTextures();
        if (selTex == null && !texFiltered.isEmpty()) {
            selTex = texFiltered.contains("stone") ? "stone" : texFiltered.get(0);
        }
    }

    private void refilterTextures() {
        texFiltered = new ArrayList<>();
        for (String n : TextureLab.names()) if (texQuery.isEmpty() || n.contains(texQuery)) texFiltered.add(n);
        texListScroll = 0;
    }

    private void buildBuilderFields() {
        schemSearch = field(176, "Search schematics...", schemQuery);
        schemSearch.setChangedListener(s -> { schemQuery = s.toLowerCase().trim(); refilterSchematics(); });
        schemFiles = SchematicBuilder.listFiles();
        refilterSchematics();
    }

    private void refilterSchematics() {
        schemFiltered = new ArrayList<>();
        for (Path p : schemFiles) if (schemQuery.isEmpty() || p.getFileName().toString().toLowerCase().contains(schemQuery)) schemFiltered.add(p);
        schemListScroll = 0;
    }

    private void place(TextFieldWidget f, int x, int y) {
        if (f == null) return;
        f.setX(x);
        f.setY(y);
        boolean vis = y >= cy && y + f.getHeight() <= cy + ch;
        f.visible = vis;
        f.active = vis;
    }

    // ======================================================================== helpers
    private void text(DrawContext ctx, String s, float x, float y, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x * Draw.ss, y * Draw.ss, 0);
        ctx.getMatrices().scale(Draw.ss, Draw.ss, 1f);
        ctx.drawText(textRenderer, s, 0, 0, color, false);
        ctx.getMatrices().pop();
    }

    private void textCentered(DrawContext ctx, String s, float mid, float y, int color) {
        text(ctx, s, mid - textRenderer.getWidth(s) / 2f, y, color);
    }

    private void textScaled(DrawContext ctx, String s, float x, float y, float sc, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x * Draw.ss, y * Draw.ss, 0);
        ctx.getMatrices().scale(Draw.ss * sc, Draw.ss * sc, 1f);
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

    private void toast(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 4000;
    }

    private List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (textRenderer.getWidth(test) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private void pill(DrawContext ctx, int x, int y, int w, int h, String label, boolean primary, Runnable run) {
        boolean hv = hover(x, y, w, h);
        int base = primary ? (hv ? 0xFF8E72FF : ACCENT) : (hv ? 0xFF2E3660 : 0xFF262C4A);
        Draw.rr(ctx, x, y, w, h, h / 2f, base);
        textCentered(ctx, label, x + w / 2f, y + (h - 8) / 2f, TEXT);
        addHit(x, y, w, h, run);
    }

    private void toggle(DrawContext ctx, String key, int x, int y, boolean on, Runnable run) {
        float t = anim(key, on ? 1f : 0f);
        int w = 40, h = 20;
        if (t > 0.05f) Draw.rr(ctx, x - 2, y - 2, w + 4, h + 4, 12, Draw.alpha(0x7C5CFF, (int) (50 * t)));
        Draw.rr(ctx, x, y, w, h, 10, Draw.lerp(OFF, ACCENT, t));
        Draw.circle(ctx, x + 10 + (w - 20) * t, y + 10, 7, 0xFFFFFFFF);
        addHit(x, y, w, h, run);
    }

    private void slider(DrawContext ctx, String label, int x, int y, int w, float value, float min, float max,
                        String shown, Consumer<Float> setter) {
        text(ctx, label, x, y + 3, MUTED);
        int tx = x + 40, tw = w - 40 - 34;
        float f = Math.max(0f, Math.min(1f, (value - min) / (max - min)));
        Draw.rr(ctx, tx, y + 4, tw, 6, 3, OFF);
        Draw.rr(ctx, tx, y + 4, Math.max(6, tw * f), 6, 3, ACCENT);
        Draw.circle(ctx, tx + tw * f, y + 7, 6, 0xFFFFFFFF);
        text(ctx, shown, x + w - 28, y + 3, TEXT);
        sliders.add(new Slider(tx, y, tw, min, max, setter));
    }

    private void updateSlider(Slider s, double mouseX) {
        float f = Math.max(0f, Math.min(1f, (float) ((mouseX - s.x()) / s.w())));
        s.set().accept(s.min() + (s.max() - s.min()) * f);
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
                for (int k = 0; k < 8; k++) {
                    float f = k / 7f;
                    int cc = an == Animation.RAINBOW || rgb == 0xFF55FF ? 0xFF000000 | Palette.rainbow(f + t * 0.3f, 0.7f, 1f) : col;
                    Draw.circle(ctx, mid - size * 0.34f + f * size * 0.68f, midY + size * 0.18f - f * size * 0.36f + (float) Math.sin(t * 4 + k) * 2, 1.2f + f * 3.2f, Draw.alpha(cc, 60 + (int) (195 * f)));
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
        gcell = 0;
        clipY0 = 0;
        clipY1 = height;
        time = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);
        float s = Math.max(2f, Math.min(4f, (float) client.getWindow().getScaleFactor()));

        int idx = tab.ordinal();
        scroll[idx] = Math.max(0, Math.min(scroll[idx], Math.max(0, contentH[idx] - ch)));
        int off = (int) scroll[idx];

        Draw.begin(ctx, s);
        ctx.fillGradient(0, 0, Math.round(width * s), Math.round(height * s), 0xD0080A14, 0xE8160C2C);
        Draw.rr(ctx, px - 1, py - 1, pw + 2, ph + 2, 17, BORDER);
        Draw.rrGrad(ctx, px, py, pw, ph, 16, PANEL_TOP, PANEL_BOT);

        Draw.circle(ctx, px + 30, py + 24, 10, ACCENT);
        Draw.circle(ctx, px + 30, py + 24, 5.5f, ACCENT2);
        textScaled(ctx, "MY", px + 48, py + 17, 1.4f, TEXT);
        textScaled(ctx, "CLIENT", px + 48 + textRenderer.getWidth("MY") * 1.4f + 2, py + 17, 1.4f, ACCENT2);
        text(ctx, "v0.2", px + 48, py + 31, MUTED);

        int closeX = px + pw - 26, closeY = py + 24;
        boolean hv = hover(closeX - 10, closeY - 10, 20, 20);
        Draw.circle(ctx, closeX, closeY, 10, hv ? 0xFF4A2A3C : 0xFF262C4A);
        textCentered(ctx, "x", closeX, closeY - 4, hv ? DANGER : MUTED);
        addHit(closeX - 10, closeY - 10, 20, 20, this::close);

        int pad = 20, total = 0;
        for (Tab t : Tab.values()) total += textRenderer.getWidth(t.label) + pad + 4;
        if (total > pw - 36) pad = 10;
        int tx = px + 18;
        for (Tab t : Tab.values()) {
            int w = textRenderer.getWidth(t.label) + pad;
            boolean sel = t == tab, th = hover(tx, py + 46, w, 22);
            if (sel) Draw.rrGrad(ctx, tx, py + 46, w, 22, 11, ACCENT, 0xFF5A3FD6);
            else Draw.rr(ctx, tx, py + 46, w, 22, 11, th ? 0xFF262D4C : 0xFF1B2038);
            textCentered(ctx, t.label, tx + w / 2f, py + 53, sel ? 0xFFFFFFFF : (th ? TEXT : MUTED));
            final Tab target = t;
            addHit(tx, py + 46, w, 22, () -> { tab = target; lastTab = target; capturing = null; init(); });
            tx += w + 4;
        }
        Draw.end(ctx);

        clipY0 = cy;
        clipY1 = cy + ch;
        ctx.enableScissor(cx - 6, cy, cx + cw + 8, cy + ch);
        Draw.begin(ctx, s);
        int y0 = cy - off;
        int h = switch (tab) {
            case MODS -> renderMods(ctx, y0);
            case COSMETICS -> renderCosmetics(ctx, y0);
            case SKIES -> renderSkies(ctx, y0);
            case MACROS -> renderMacros(ctx, y0);
            case EDITOR -> renderEditor(ctx, y0);
            case TEXTURES -> renderTextures(ctx, y0);
            case BUILDER -> renderBuilder(ctx, y0);
        };
        contentH[idx] = h;
        if (h > ch) {
            int barH = Math.max(24, (int) ((long) ch * ch / h));
            int barY = cy + (int) ((ch - barH) * (scroll[idx] / Math.max(1, h - ch)));
            Draw.rr(ctx, cx + cw + 3, barY, 4, barH, 2, 0xFF3A4166);
        }
        Draw.end(ctx);
        ctx.disableScissor();
        clipY0 = 0;
        clipY1 = height;

        if (System.currentTimeMillis() < toastUntil) {
            Draw.begin(ctx, s);
            int tw = textRenderer.getWidth(toast) + 24;
            Draw.rr(ctx, px + pw / 2f - tw / 2f, py + ph - 26, tw, 20, 10, 0xEE2A2F52);
            textCentered(ctx, toast, px + pw / 2f, py + ph - 20, TEXT);
            Draw.end(ctx);
        }
    }

    // ======================================================================== tabs
    private static String desc(Module m) {
        return switch (m.name) {
            case "Fullbright" -> "See everything clearly, even in caves and at night";
            case "Motion Blur" -> "Smooth, cinematic camera movement";
            default -> "";
        };
    }

    private int renderMods(DrawContext ctx, int y0) {
        int y = y0;
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
        return y - y0;
    }

    private void pickCape() {
        FilePicker.pickPng("Choose a cape image (.png)", this::uploadCape,
            () -> toast("No file dialog available - drop the .png onto this window instead"));
    }

    private void uploadCape(Path p) {
        try {
            Cosmetic c = MyClient.COSMETICS.addCape(p);
            toast("Cape added: " + pretty(c.id) + " - equip it below");
        } catch (Exception e) {
            toast("Could not load that PNG");
        }
    }

    private void pickSky() {
        FilePicker.pickPng("Choose a sky image (.png)", this::uploadSky,
            () -> toast("No file dialog available - drop the .png onto this window instead"));
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

    private int renderCosmetics(DrawContext ctx, int y0) {
        pill(ctx, cx, y0, 150, 22, "Upload cape (.png)", true, this::pickCape);
        text(ctx, "or drop a .png onto the window", cx + 160, y0 + 7, MUTED);
        int top = y0 + 34;

        List<Cosmetic> list = new ArrayList<>(MyClient.COSMETICS.catalog);
        int cols = cw >= 430 ? 2 : 1, gap = 12;
        int cardW = (cw - gap * (cols - 1)) / cols, cardH = 100;
        int rows = (list.size() + cols - 1) / cols;

        for (int i = 0; i < list.size(); i++) {
            Cosmetic c = list.get(i);
            int x = cx + (i % cols) * (cardW + gap);
            int y = top + (i / cols) * (cardH + 10);
            if (y + cardH < cy || y > cy + ch) continue;

            boolean on = MyClient.COSMETICS.isEquipped(c);
            boolean hv = hover(x, y, cardW, cardH);
            if (on) Draw.rr(ctx, x - 1, y - 1, cardW + 2, cardH + 2, 15, ACCENT);
            Draw.rr(ctx, x, y, cardW, cardH, 14, hv ? CARD_HOVER : CARD);
            addHit(x, y, cardW, cardH, () -> MyClient.COSMETICS.toggle(c));

            Draw.rr(ctx, x + 10, y + 14, 70, 70, 12, TILE);
            drawIcon(ctx, c.type, c.animation, Palette.rgb(c), x + 45, y + 49, 54, c.speed);

            text(ctx, pretty(c.id), x + 90, y + 12, TEXT);
            text(ctx, nice(c.type) + (c.type == Type.TRAIL ? "" : "  -  " + nice(c.animation)), x + 90, y + 26, MUTED);
            toggle(ctx, "cos:" + c.id, x + cardW - 54, y + 10, on, () -> MyClient.COSMETICS.toggle(c));

            for (int v = 0; v < c.variants.size(); v++) {
                String variant = c.variants.get(v);
                int sx = x + 98 + v * 19, sy = y + 54;
                boolean sel = variant.equals(c.activeVariant);
                if (sel) Draw.circle(ctx, sx, sy, 8.5f, 0xFFFFFFFF);
                Draw.circle(ctx, sx, sy, 6.5f, 0xFF000000 | Palette.swatch(c, variant));
                addHit(sx - 9, sy - 9, 18, 18, () -> c.activeVariant = variant);
            }
            if (c.variants.size() > 1 || !c.custom) {
                text(ctx, c.custom ? "" : pretty(c.activeVariant), x + 90, y + 40, MUTED);
            }

            if (c.type != Type.TRAIL) {
                int pw2 = Math.min(120, cardW - 100);
                pill(ctx, x + 90, y + 72, pw2, 18, "Anim: " + nice(c.animation), false, () ->
                    c.animation = Animation.values()[(c.animation.ordinal() + 1) % Animation.values().length]);
            }
            if (c.custom) {
                int dx = x + cardW - 20, dy = y + cardH - 16;
                Draw.circle(ctx, dx, dy, 9, 0xFF3A2433);
                textCentered(ctx, "x", dx, dy - 4, DANGER);
                addHit(dx - 9, dy - 9, 18, 18, () -> {
                    MyClient.COSMETICS.equipped.remove(c.id);
                    MyClient.COSMETICS.catalog.remove(c);
                    try { if (c.file != null) Files.deleteIfExists(c.file); } catch (Exception ignored) {}
                });
            }
        }
        return 34 + rows * (cardH + 10);
    }

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

    private int renderSkies(DrawContext ctx, int y0) {
        pill(ctx, cx, y0, 140, 22, "Upload sky (.png)", true, this::pickSky);
        text(ctx, "or drop a .png onto the window", cx + 150, y0 + 7, MUTED);
        text(ctx, "Sky rendering in the world is not finished yet - your choice is saved, not shown.", cx + 2, y0 + 30, 0xFFC9B8FF);
        int top = y0 + 46;
        int cols = cw >= 430 ? 2 : 1, gap = 12;
        int cardW = (cw - gap * (cols - 1)) / cols, cardH = 74;
        List<String> skies = new ArrayList<>(MyClient.COSMETICS.skies);
        for (int i = 0; i < skies.size(); i++) {
            String s = skies.get(i);
            int x = cx + (i % cols) * (cardW + gap), y = top + (i / cols) * (cardH + 10);
            if (y + cardH < cy || y > cy + ch) continue;
            boolean sel = s.equals(MyClient.COSMETICS.activeSky);
            boolean hv = hover(x, y, cardW, cardH);
            if (sel) Draw.rr(ctx, x - 1, y - 1, cardW + 2, cardH + 2, 15, ACCENT);
            Draw.rr(ctx, x, y, cardW, cardH, 14, hv ? CARD_HOVER : CARD);
            int[] cc = skyColors(s);
            Draw.rrGrad(ctx, x + 10, y + 10, 90, cardH - 20, 10, cc[0], cc[1]);
            text(ctx, s, x + 112, y + 20, TEXT);
            text(ctx, sel ? "Selected" : "Click to select", x + 112, y + 36, sel ? ACCENT2 : MUTED);
            Draw.circle(ctx, x + cardW - 24, y + cardH / 2f, 9, sel ? ACCENT : OFF);
            if (sel) Draw.circle(ctx, x + cardW - 24, y + cardH / 2f, 4, 0xFFFFFFFF);
            addHit(x, y, cardW, cardH, () -> MyClient.COSMETICS.activeSky = s);
            if (MyClient.COSMETICS.uploadedSkies.containsKey(s)) {
                int dx = x + cardW - 24, dy = y + cardH - 14;
                Draw.circle(ctx, dx, dy, 8, 0xFF3A2433);
                textCentered(ctx, "x", dx, dy - 4, DANGER);
                addHit(dx - 8, dy - 8, 16, 16, () -> MyClient.COSMETICS.removeSky(s));
            }
        }
        int rows = (skies.size() + cols - 1) / cols;
        return 46 + rows * (cardH + 10);
    }

    private String keyLabel(int code) {
        try {
            return InputUtil.fromKeyCode(code, 0).getLocalizedText().getString().toUpperCase();
        } catch (Exception e) {
            return "KEY " + code;
        }
    }

    private int renderMacros(DrawContext ctx, int y0) {
        List<Macro> ms = MyClient.MACROS.macros;
        textScaled(ctx, "Macros", cx + 2, y0 + 4, 1.3f, TEXT);
        if (ms.size() < 5) {
            pill(ctx, cx + cw - 116, y0, 116, 22, "+ New macro", true, () -> {
                ms.add(new Macro("Macro " + (ms.size() + 1), GLFW.GLFW_KEY_G, "", 0));
                init();
            });
        } else {
            text(ctx, "Max 5 macros", cx + cw - 80, y0 + 7, MUTED);
        }
        if (ms.isEmpty()) textCentered(ctx, "No macros yet - click \"+ New macro\".", cx + cw / 2f, y0 + 90, MUTED);
        for (int i = 0; i < ms.size(); i++) {
            Macro m = ms.get(i);
            int ry = y0 + 34 + i * 42;
            Draw.rr(ctx, cx, ry, cw, 36, 12, CARD);
            text(ctx, m.name, cx + 14, ry + 14, TEXT);
            pill(ctx, cx + 104, ry + 7, 68, 22, capturing == m ? "press key" : keyLabel(m.keyCode), capturing == m, () -> capturing = m);
            Draw.rr(ctx, cx + 180, ry + 7, Math.max(80, cw - 280), 22, 11, TILE);
            if (i < macroFields.size()) place(macroFields.get(i), cx + 190, ry + 11);
            toggle(ctx, "mac:" + System.identityHashCode(m), cx + cw - 86, ry + 8, m.enabled, () -> m.enabled = !m.enabled);
            int dx = cx + cw - 24, dy = ry + 18;
            boolean hv = hover(dx - 10, dy - 10, 20, 20);
            Draw.circle(ctx, dx, dy, 10, hv ? 0xFF4A2A3C : 0xFF2A2236);
            textCentered(ctx, "x", dx, dy - 4, DANGER);
            addHit(dx - 10, dy - 10, 20, 20, () -> { ms.remove(m); init(); });
        }
        return 34 + Math.max(1, ms.size()) * 42 + 8;
    }

    private int renderEditor(DrawContext ctx, int y0) {
        int fx = cx + 8, fw = Math.min(250, cw / 2 - 10);
        text(ctx, "Name", fx, y0 + 2, MUTED);
        Draw.rr(ctx, fx, y0 + 14, fw, 22, 11, TILE);
        place(draftName, fx + 12, y0 + 18);

        int half = (fw - 8) / 2;
        pill(ctx, fx, y0 + 48, half, 22, "Type: " + nice(draftType), false, () -> {
            int i = 0;
            for (int k = 0; k < EDITABLE.length; k++) if (EDITABLE[k] == draftType) i = k;
            draftType = EDITABLE[(i + 1) % EDITABLE.length];
        });
        pill(ctx, fx + half + 8, y0 + 48, half, 22, "Anim: " + nice(draftAnim), false,
            () -> draftAnim = Animation.values()[(draftAnim.ordinal() + 1) % Animation.values().length]);

        slider(ctx, "Red", fx, y0 + 88, fw, dr, 0, 255, String.valueOf((int) dr), v -> dr = v);
        slider(ctx, "Green", fx, y0 + 112, fw, dg, 0, 255, String.valueOf((int) dg), v -> dg = v);
        slider(ctx, "Blue", fx, y0 + 136, fw, db, 0, 255, String.valueOf((int) db), v -> db = v);
        slider(ctx, "Speed", fx, y0 + 160, fw, dspeed, 0.2f, 3f, String.format("%.1fx", dspeed), v -> dspeed = v);

        pill(ctx, fx, y0 + 190, fw, 28, "Create accessory", true, this::createDraft);

        int tx = cx + fw + 24, tw = cw - fw - 32, th = 200;
        Draw.rr(ctx, tx, y0 + 4, tw, th, 16, TILE);
        int rgb = ((int) dr << 16) | ((int) dg << 8) | (int) db;
        drawIcon(ctx, draftType, draftAnim, rgb, tx + tw / 2f, y0 + 4 + th / 2f - 8, Math.min(tw, th) - 50, dspeed);
        textCentered(ctx, "Live preview", tx + tw / 2f, y0 + th - 8, MUTED);
        return 232;
    }

    private void createDraft() {
        String name = draftNameText.trim();
        if (name.isEmpty()) { toast("Enter a name first"); return; }
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (slug.isEmpty()) slug = "accessory";
        String base = "custom_" + slug;
        String id = base;
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

    // ---------------------------------------------------------------- texture editor tab
    private void applyTextures() {
        try {
            TextureLab.apply();
            toast("Applying textures - the game reloads resources...");
        } catch (Exception e) {
            toast("Could not write the texture pack");
        }
    }

    private int renderTextures(DrawContext ctx, int y0) {
        Draw.rr(ctx, cx, y0, 180, 22, 11, TILE);
        place(texSearch, cx + 12, y0 + 4);
        pill(ctx, cx + 190, y0, 118, 22, TextureLab.dirtyCount() > 0 ? "Apply + reload (" + TextureLab.dirtyCount() + ")" : "Apply + reload", true, this::applyTextures);
        boolean on = TextureLab.isEnabled();
        String lbl = "Texture pack";
        text(ctx, lbl, cx + cw - 46 - 8 - textRenderer.getWidth(lbl), y0 + 7, MUTED);
        toggle(ctx, "texpack", cx + cw - 44, y0 + 1, on, () -> {
            if (!TextureLab.packExists()) { toast("Edit a texture and press Apply first"); return; }
            TextureLab.setEnabled(!TextureLab.isEnabled());
            toast(TextureLab.isEnabled() ? "Texture pack on" : "Texture pack off");
        });

        int ly = y0 + 32, rowsVis = 9, lh = rowsVis * 20 + 8;
        listX = cx; listY = ly; listW = 180; listH = lh;
        Draw.rr(ctx, cx, ly, 180, lh, 12, CARD);
        texListScroll = Math.max(0, Math.min(texListScroll, Math.max(0, texFiltered.size() - rowsVis)));
        for (int r = 0; r < rowsVis; r++) {
            int idx = texListScroll + r;
            if (idx >= texFiltered.size()) break;
            String nm = texFiltered.get(idx);
            int ry = ly + 4 + r * 20;
            boolean sel = nm.equals(selTex), hv = hover(cx + 4, ry, 172, 18);
            if (sel) Draw.rr(ctx, cx + 4, ry, 172, 18, 9, ACCENT);
            else if (hv) Draw.rr(ctx, cx + 4, ry, 172, 18, 9, 0xFF262D4C);
            text(ctx, textRenderer.trimToWidth(nm, 150), cx + 12, ry + 5, sel ? 0xFFFFFFFF : TEXT);
            if (TextureLab.isDirty(nm)) Draw.circle(ctx, cx + 168, ry + 9, 3, ACCENT2);
            addHit(cx + 4, ry, 172, 18, () -> selTex = nm);
        }
        if (texFiltered.isEmpty()) text(ctx, "No textures found", cx + 12, ly + 12, MUTED);
        text(ctx, texFiltered.size() + " block textures", cx + 4, ly + lh + 4, MUTED);

        int ex = cx + 194;
        int bottom = ly + lh + 16;
        BufferedImage img = selTex == null ? null : TextureLab.get(selTex);
        if (img != null) {
            int size = TextureLab.size(img);
            int cell = Math.max(2, 128 / size), gp = cell * size;
            gx = ex; gy = ly + 16; gcell = cell; gsize = size;
            text(ctx, pretty(selTex) + "  (" + size + "x" + size + (img.getHeight() > img.getWidth() ? ", frame 1" : "") + ")", ex, ly, TEXT);
            Draw.rr(ctx, ex - 3, gy - 3, gp + 6, gp + 6, 8, TILE);
            drawImage(ctx, img, size, ex, gy, cell, true);

            int tx = ex + gp + 12;
            int i = 0;
            for (Tool t : Tool.values()) {
                final Tool tt = t;
                pill(ctx, tx, gy + i * 22, 92, 18, t.label, tool == t, () -> tool = tt);
                i++;
            }
            pill(ctx, tx, gy + i * 22, 44, 18, "Undo", false, () -> { if (selTex != null) TextureLab.undo(selTex); });
            pill(ctx, tx + 48, gy + i * 22, 44, 18, "Reset", false, () -> {
                if (selTex != null) { TextureLab.reset(selTex); toast("Reset - press Apply to reload the original"); }
            });
            // 2x2 preview
            float pp = Math.max(1f, 26f / size);
            Draw.rr(ctx, tx - 3, gy + 138 - 3, pp * size * 2 + 6, pp * size * 2 + 6, 6, TILE);
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++)
                drawImage(ctx, img, size, tx + a * pp * size, gy + 138 + b * pp * size, pp, false);

            int sy = gy + gp + 12, sw = Math.min(gp + 20, 160);
            slider(ctx, "Red", ex, sy, sw, texR, 0, 255, String.valueOf((int) texR), v -> texR = v);
            slider(ctx, "Green", ex, sy + 20, sw, texG, 0, 255, String.valueOf((int) texG), v -> texG = v);
            slider(ctx, "Blue", ex, sy + 40, sw, texB, 0, 255, String.valueOf((int) texB), v -> texB = v);
            Draw.rr(ctx, ex + sw + 8, sy, 26, 26, 8, 0xFF000000 | ((int) texR << 16) | ((int) texG << 8) | (int) texB);
            bottom = Math.max(bottom, sy + 62);
        } else {
            text(ctx, "Pick a block texture on the left", ex, ly + 10, MUTED);
        }
        return bottom - y0 + 6;
    }

    private boolean gridPaint(double mouseX, double mouseY) {
        if (selTex == null || gcell <= 0) return false;
        int cxp = (int) Math.floor((mouseX - gx) / gcell), cyp = (int) Math.floor((mouseY - gy) / gcell);
        if (cxp < 0 || cyp < 0 || cxp >= gsize || cyp >= gsize) return false;
        BufferedImage img = TextureLab.get(selTex);
        if (img == null) return false;
        int argb = 0xFF000000 | ((int) texR << 16) | ((int) texG << 8) | (int) texB;
        switch (tool) {
            case PENCIL -> TextureLab.paint(selTex, cxp, cyp, argb);
            case ERASER -> TextureLab.paint(selTex, cxp, cyp, 0);
            case FILL -> TextureLab.fill(selTex, cxp, cyp, argb);
            case PICK -> {
                int p = img.getRGB(cxp, cyp);
                texR = (p >> 16) & 255; texG = (p >> 8) & 255; texB = p & 255;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- schematic builder tab
    private void loadSchematic(Path p) {
        try {
            SchematicBuilder.load(p);
            toast("Loaded " + SchematicBuilder.loaded.name);
        } catch (Exception e) {
            toast("Could not read that schematic");
        }
    }

    private int renderBuilder(DrawContext ctx, int y0) {
        Draw.rr(ctx, cx, y0, 200, 22, 11, TILE);
        place(schemSearch, cx + 12, y0 + 4);
        text(ctx, "Folder: .minecraft/schematics", cx + 212, y0 + 7, MUTED);

        int ly = y0 + 30, rowsVis = 7, lh = rowsVis * 20 + 8;
        listX = cx; listY = ly; listW = 200; listH = lh;
        Draw.rr(ctx, cx, ly, 200, lh, 12, CARD);
        schemListScroll = Math.max(0, Math.min(schemListScroll, Math.max(0, schemFiltered.size() - rowsVis)));
        for (int r = 0; r < rowsVis; r++) {
            int idx = schemListScroll + r;
            if (idx >= schemFiltered.size()) break;
            Path p = schemFiltered.get(idx);
            String nm = p.getFileName().toString().replaceFirst("\\.litematic$", "");
            int ry = ly + 4 + r * 20;
            boolean sel = SchematicBuilder.loaded != null && SchematicBuilder.loaded.name.equals(nm);
            boolean hv = hover(cx + 4, ry, 192, 18);
            if (sel) Draw.rr(ctx, cx + 4, ry, 192, 18, 9, ACCENT);
            else if (hv) Draw.rr(ctx, cx + 4, ry, 192, 18, 9, 0xFF262D4C);
            text(ctx, textRenderer.trimToWidth(nm, 176), cx + 12, ry + 5, sel ? 0xFFFFFFFF : TEXT);
            addHit(cx + 4, ry, 192, 18, () -> loadSchematic(p));
        }
        if (schemFiltered.isEmpty()) {
            text(ctx, schemFiles.isEmpty() ? "No .litematic files found" : "No match", cx + 12, ly + 12, MUTED);
            if (schemFiles.isEmpty()) text(ctx, "Put them in .minecraft/schematics", cx + 12, ly + 26, MUTED);
        }

        int rx = cx + 214, rw = cw - 214;
        Draw.rr(ctx, rx, ly, rw, lh, 12, CARD);
        int yy = ly + 10;
        if (SchematicBuilder.loaded == null) {
            text(ctx, "Select a schematic on the left.", rx + 12, yy, MUTED);
        } else {
            text(ctx, textRenderer.trimToWidth(SchematicBuilder.loaded.name, rw - 24), rx + 12, yy, TEXT);
            text(ctx, SchematicBuilder.loaded.sizeX + " x " + SchematicBuilder.loaded.sizeY + " x " + SchematicBuilder.loaded.sizeZ
                + "  -  " + SchematicBuilder.loaded.count() + " blocks", rx + 12, yy + 13, MUTED);
            float prog = SchematicBuilder.total() == 0 ? 0f : SchematicBuilder.placed() / (float) SchematicBuilder.total();
            Draw.rr(ctx, rx + 12, yy + 28, rw - 24, 8, 4, OFF);
            Draw.rr(ctx, rx + 12, yy + 28, Math.max(8, (rw - 24) * prog), 8, 4, ACCENT2);
            String o = SchematicBuilder.origin == null ? "Origin: not set"
                : "Origin: " + SchematicBuilder.origin.getX() + " " + SchematicBuilder.origin.getY() + " " + SchematicBuilder.origin.getZ();
            text(ctx, o, rx + 12, yy + 42, TEXT);
            pill(ctx, rx + 12, yy + 56, rw - 24, 18, "Set origin here (build starts at my feet)", false, () -> {
                SchematicBuilder.setOriginHere();
                toast("Origin set to your position");
            });
            text(ctx, "Build", rx + 12, yy + 84, TEXT);
            toggle(ctx, "build", rx + rw - 52, yy + 80, SchematicBuilder.running, () -> SchematicBuilder.setRunning(!SchematicBuilder.running));
            text(ctx, "Walk to blocks", rx + 12, yy + 106, TEXT);
            toggle(ctx, "walk", rx + rw - 52, yy + 102, SchematicBuilder.walk, () -> SchematicBuilder.walk = !SchematicBuilder.walk);
            pill(ctx, rx + 12, yy + 124, rw - 24, 18, "Restart (re-check every block)", false, SchematicBuilder::restart);
        }
        int sy = ly + lh + 10;
        int lines = 0;
        for (String line : wrap(SchematicBuilder.status, cw - 8)) {
            text(ctx, line, cx + 4, sy + lines * 11, SchematicBuilder.missing.isEmpty() ? ACCENT2 : 0xFFFFB454);
            lines++;
        }
        text(ctx, "Builds bottom to top, in reach only. Blocks must be in your hotbar. Many servers forbid this.", cx + 4, sy + lines * 11 + 6, MUTED);
        return sy + lines * 11 + 22 - y0;
    }

    // ======================================================================== drag & drop
    private void handleDrop(List<Path> paths) {
        int ok = 0;
        for (Path p : paths) {
            if (!p.toString().toLowerCase().endsWith(".png")) continue;
            if (tab == Tab.COSMETICS) { uploadCape(p); ok++; }
            else if (tab == Tab.SKIES) { uploadSky(p); ok++; }
        }
        if (ok == 0) toast("Open the Cosmetics tab (cape) or Skies tab (sky), then drop a .png");
    }

    public void onFilesDropped(List<Path> paths) { handleDrop(paths); }

    public void filesDragged(List<Path> paths) { handleDrop(paths); }

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
                if (mouseX >= s.x() - 6 && mouseX <= s.x() + s.w() + 6 && mouseY >= s.y() && mouseY < s.y() + 14
                    && mouseY >= cy && mouseY < cy + ch) {
                    dragging = s;
                    updateSlider(s, mouseX);
                    return true;
                }
            }
            if (tab == Tab.TEXTURES && gcell > 0 && selTex != null && mouseY >= cy && mouseY < cy + ch
                && mouseX >= gx && mouseX < gx + gcell * gsize && mouseY >= gy && mouseY < gy + gcell * gsize) {
                if (tool != Tool.PICK) TextureLab.beginStroke(selTex);
                painting = true;
                gridPaint(mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null) { updateSlider(dragging, mouseX); return true; }
        if (painting && (tool == Tool.PENCIL || tool == Tool.ERASER)) { gridPaint(mouseX, mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        painting = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        boolean overList = mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH;
        if (tab == Tab.TEXTURES && overList) {
            texListScroll = Math.max(0, texListScroll - (int) Math.signum(vertical) * 2);
            return true;
        }
        if (tab == Tab.BUILDER && overList) {
            schemListScroll = Math.max(0, schemListScroll - (int) Math.signum(vertical) * 2);
            return true;
        }
        scroll[tab.ordinal()] -= (float) vertical * 22f;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturing != null) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) capturing.keyCode = keyCode;
            capturing = null;
            return true;
        }
        boolean typing = getFocused() instanceof TextFieldWidget tf && tf.isFocused();
        if (!typing && tab == Tab.TEXTURES && keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && selTex != null) {
            TextureLab.undo(selTex);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT && !typing) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
