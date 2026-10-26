package dev.myclient.texture;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * In-game block texture editing. Edited textures are written into a resource pack folder
 * (resourcepacks/MyClientTextures) that the client creates, enables and reloads by itself.
 */
public final class TextureLab {
    private TextureLab() {}

    public static final String PACK_NAME = "MyClientTextures";
    public static final String PACK_ID = "file/" + PACK_NAME;

    private static List<String> names;
    private static final Map<String, BufferedImage> cache = new HashMap<>();
    private static final Set<String> dirty = new LinkedHashSet<>();
    private static final Map<String, Deque<int[]>> undo = new HashMap<>();

    // ---------------------------------------------------------------- paths
    private static Path packDir() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(PACK_NAME);
    }

    private static Path file(String name) {
        return packDir().resolve("assets").resolve("minecraft").resolve("textures").resolve("block").resolve(name + ".png");
    }

    // ---------------------------------------------------------------- texture list
    public static List<String> names() {
        if (names == null) {
            names = new ArrayList<>();
            try {
                Map<Identifier, Resource> found = MinecraftClient.getInstance().getResourceManager().findResources(
                    "textures/block", id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith(".png"));
                String pre = "textures/block/";
                for (Identifier id : found.keySet()) {
                    String p = id.getPath();
                    if (!p.startsWith(pre)) continue;
                    String n = p.substring(pre.length(), p.length() - 4);
                    if (!n.contains("/")) names.add(n);
                }
                Collections.sort(names);
            } catch (Exception ignored) {}
        }
        return names;
    }

    public static boolean isDirty(String name) { return dirty.contains(name); }

    public static int dirtyCount() { return dirty.size(); }

    // ---------------------------------------------------------------- image access
    public static BufferedImage get(String name) {
        BufferedImage img = cache.get(name);
        if (img != null) return img;
        try {
            Identifier id = Identifier.of("minecraft", "textures/block/" + name + ".png");
            Optional<Resource> r = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (r.isEmpty()) return null;
            try (InputStream in = r.get().getInputStream()) {
                BufferedImage src = ImageIO.read(in);
                if (src == null) return null;
                BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < src.getHeight(); y++)
                    for (int x = 0; x < src.getWidth(); x++) argb.setRGB(x, y, src.getRGB(x, y));
                cache.put(name, argb);
                return argb;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Edge length of the square (first frame) we edit. */
    public static int size(BufferedImage img) { return Math.min(img.getWidth(), img.getHeight()); }

    // ---------------------------------------------------------------- editing
    public static void beginStroke(String name) {
        BufferedImage img = get(name);
        if (img == null) return;
        int[] snap = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        Deque<int[]> d = undo.computeIfAbsent(name, k -> new ArrayDeque<>());
        d.push(snap);
        while (d.size() > 40) d.removeLast();
    }

    public static void paint(String name, int x, int y, int argb) {
        BufferedImage img = get(name);
        if (img == null || x < 0 || y < 0 || x >= size(img) || y >= size(img)) return;
        img.setRGB(x, y, argb);
        dirty.add(name);
    }

    public static void fill(String name, int x, int y, int argb) {
        BufferedImage img = get(name);
        if (img == null) return;
        int s = size(img);
        if (x < 0 || y < 0 || x >= s || y >= s) return;
        int target = img.getRGB(x, y);
        if (target == argb) return;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{x, y});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            if (p[0] < 0 || p[1] < 0 || p[0] >= s || p[1] >= s || img.getRGB(p[0], p[1]) != target) continue;
            img.setRGB(p[0], p[1], argb);
            stack.push(new int[]{p[0] + 1, p[1]}); stack.push(new int[]{p[0] - 1, p[1]});
            stack.push(new int[]{p[0], p[1] + 1}); stack.push(new int[]{p[0], p[1] - 1});
        }
        dirty.add(name);
    }

    public static void undo(String name) {
        Deque<int[]> d = undo.get(name);
        BufferedImage img = cache.get(name);
        if (d == null || d.isEmpty() || img == null) return;
        int[] snap = d.pop();
        img.setRGB(0, 0, img.getWidth(), img.getHeight(), snap, 0, img.getWidth());
        dirty.add(name);
    }

    /** Deletes our edited file for this texture; press Apply afterwards to reload the vanilla look. */
    public static void reset(String name) {
        cache.remove(name);
        dirty.remove(name);
        undo.remove(name);
        try { Files.deleteIfExists(file(name)); } catch (IOException ignored) {}
    }

    // ---------------------------------------------------------------- copy / paste between blocks
    private static BufferedImage clip;
    private static String clipName = "";

    public static boolean copy(String name) {
        BufferedImage img = get(name);
        if (img == null) return false;
        int s = size(img);
        BufferedImage c = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) c.setRGB(x, y, img.getRGB(x, y));
        clip = c;
        clipName = name;
        return true;
    }

    /** Pastes the copied texture onto this block (scaled if the sizes differ). */
    public static boolean paste(String name) {
        if (clip == null) return false;
        BufferedImage img = get(name);
        if (img == null) return false;
        beginStroke(name);
        int ts = size(img), cs = clip.getWidth();
        for (int y = 0; y < ts; y++) for (int x = 0; x < ts; x++) img.setRGB(x, y, clip.getRGB(x * cs / ts, y * cs / ts));
        dirty.add(name);
        return true;
    }

    public static String clipInfo() {
        return clip == null ? "empty" : clipName + " (" + clip.getWidth() + "x" + clip.getHeight() + ")";
    }

    // ---------------------------------------------------------------- pack handling
    public static boolean packExists() { return Files.exists(packDir().resolve("pack.mcmeta")); }

    public static boolean isEnabled() {
        try {
            return MinecraftClient.getInstance().getResourcePackManager().getEnabledIds().contains(PACK_ID);
        } catch (Exception e) {
            return false;
        }
    }

    private static void writePack() throws IOException {
        for (String n : dirty) {
            BufferedImage img = cache.get(n);
            if (img == null) continue;
            Path f = file(n);
            Files.createDirectories(f.getParent());
            ImageIO.write(img, "png", f.toFile());
        }
        dirty.clear();
        Files.createDirectories(packDir());
        Files.writeString(packDir().resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":46,\"supported_formats\":{\"min_inclusive\":34,\"max_inclusive\":99},"
                + "\"description\":\"MyClient texture editor pack\"}}");
    }

    /** Writes all edits, makes sure the pack is enabled and reloads resources. */
    public static void apply() throws IOException {
        writePack();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (isEnabled()) {
            mc.reloadResources();
        } else {
            setEnabled(true);
        }
    }

    public static void setEnabled(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager mgr = mc.getResourcePackManager();
        mgr.scanPacks();
        if (on) {
            if (!mgr.getEnabledIds().contains(PACK_ID)) mgr.enable(PACK_ID);
        } else {
            mgr.disable(PACK_ID);
        }
        mc.options.refreshResourcePacks(mgr);
    }
}
