package dev.myclient.fake;

import dev.myclient.Settings;
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
import java.util.stream.Stream;

/**
 * Fake blocks (real 3D models), the butterfly knife and the fake skeleton spawner, delivered through an
 * automatically managed resource pack (resourcepacks/MyClientFake). Changing a setting rewrites the pack
 * and reloads the game's resources.
 */
public final class FakePack {
    private FakePack() {}

    public static final String NAME = "MyClientFake";
    public static final String ID = "file/" + NAME;

    /** What a block turns into and which blocks / items are affected. */
    public record Swap(String from, String to, String affects) {}

    public static final List<Swap> SWAPS = List.of(
        new Swap("Dirt Block", "Mace", "Dirt (placed block and inventory item, as a real 3D mace)"),
        new Swap("Stone", "Trident", "Stone and Infested Stone (as a real 3D trident)"),
        new Swap("Deepslate", "Ancient Debris", "Deepslate, Infested Deepslate"),
        new Swap("Cobbled Deepslate", "Emerald Block", "Cobbled Deepslate"),
        new Swap("End Stone", "Iron Block", "End Stone"),
        new Swap("Oak Log", "Gold Block", "Oak Log (every direction)"),
        new Swap("Oak Planks", "Diamond Block", "Oak Planks"));

    private static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(NAME);
    }

    private static BufferedImage read(String path) {
        try {
            Identifier id = Identifier.of("minecraft", "textures/" + path + ".png");
            Optional<Resource> r = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (r.isEmpty()) return null;
            try (InputStream in = r.get().getInputStream()) {
                BufferedImage src = ImageIO.read(in);
                if (src == null) return null;
                int s = Math.min(src.getWidth(), src.getHeight());
                BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) out.setRGB(x, y, src.getRGB(x, y));
                return out;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Average colours of an item sprite: [head (upper right), shaft (lower left)]. */
    private static int[] sample(String itemPath, int headFallback, int shaftFallback) {
        BufferedImage img = read(itemPath);
        if (img == null) return new int[]{headFallback, shaftFallback};
        int s = img.getWidth();
        long[] h = new long[4], sh = new long[4];
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int p = img.getRGB(x, y);
                if ((p >>> 24) < 200) continue;
                double fx = x / (double) s, fy = y / (double) s;
                long[] t = (fx + (1 - fy)) > 1.15 ? h : ((fx + (1 - fy)) < 0.85 ? sh : null);
                if (t == null) continue;
                t[0] += (p >> 16) & 255; t[1] += (p >> 8) & 255; t[2] += p & 255; t[3]++;
            }
        }
        return new int[]{h[3] > 0 ? avg(h) : headFallback, sh[3] > 0 ? avg(sh) : shaftFallback};
    }

    private static int avg(long[] a) {
        return ((int) (a[0] / a[3]) << 16) | ((int) (a[1] / a[3]) << 8) | (int) (a[2] / a[3]);
    }

    private static BufferedImage compose(BufferedImage src, int bg) {
        int s = src.getWidth();
        BufferedImage out = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int p = src.getRGB(x, y);
                float a = (p >>> 24) / 255f;
                int r = (int) (((p >> 16) & 255) * a + ((bg >> 16) & 255) * (1 - a));
                int g = (int) (((p >> 8) & 255) * a + ((bg >> 8) & 255) * (1 - a));
                int b = (int) ((p & 255) * a + (bg & 255) * (1 - a));
                out.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static void write(Path root, String rel, String content) throws IOException {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
    }

    private static void writePng(Path root, String rel, BufferedImage img) throws IOException {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        ImageIO.write(img, "png", f.toFile());
    }

    private static String cube(String parent, String... kv) {
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (t.length() > 0) t.append(",");
            t.append("\"").append(kv[i]).append("\":\"").append(kv[i + 1]).append("\"");
        }
        return "{\"parent\":\"" + parent + "\",\"textures\":{" + t + "}}";
    }

    private static void blockModel(Path root, String name, String json) throws IOException {
        write(root, "assets/minecraft/models/block/" + name + ".json", json);
    }

    private static void writeFakeBlocks(Path root) throws IOException {
        int[] mace = sample("item/mace", 0x5A5F69, 0x8B8F96);
        int[] trident = sample("item/trident", 0x4FA9A5, 0x7A6A55);
        writePng(root, "assets/myclient/textures/block/fake_mace.png", BlockModels.palette(mace[0], mace[1]));
        writePng(root, "assets/myclient/textures/block/fake_trident.png", BlockModels.palette(trident[0], trident[1]));
        blockModel(root, "dirt", BlockModels.mace("myclient:block/fake_mace"));
        blockModel(root, "stone", BlockModels.trident("myclient:block/fake_trident"));
        blockModel(root, "deepslate", cube("minecraft:block/cube_column", "end", "minecraft:block/ancient_debris_top", "side", "minecraft:block/ancient_debris_side"));
        blockModel(root, "cobbled_deepslate", cube("minecraft:block/cube_all", "all", "minecraft:block/emerald_block"));
        blockModel(root, "end_stone", cube("minecraft:block/cube_all", "all", "minecraft:block/iron_block"));
        blockModel(root, "oak_log", cube("minecraft:block/cube_column", "end", "minecraft:block/gold_block", "side", "minecraft:block/gold_block"));
        blockModel(root, "oak_log_horizontal", cube("minecraft:block/cube_column_horizontal", "end", "minecraft:block/gold_block", "side", "minecraft:block/gold_block"));
        blockModel(root, "oak_planks", cube("minecraft:block/cube_all", "all", "minecraft:block/diamond_block"));
    }

    private static void writeSpawner(Path root) throws IOException {
        Identifier id = Identifier.tryParse(Settings.spawnerBlock);
        if (id == null) return;
        String ns = id.getNamespace(), path = id.getPath();

        BufferedImage cage = read("block/spawner");
        BufferedImage tex = cage == null ? new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB) : compose(cage, 0xFF0F1117);
        if (cage == null) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) tex.setRGB(x, y, 0xFF2A2D38);
        String[] skull = {"..XXXX..", ".XXXXXX.", "XXXXXXXX", "XDDXXDDX", "XDDXXDDX", "XXXDDXXX", ".XXXXXX.", "..X.X.X."};
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                char c = skull[y].charAt(x);
                if (c == 'X') tex.setRGB(4 + x, 4 + y, 0xFFD9DADF);
                else if (c == 'D') tex.setRGB(4 + x, 4 + y, 0xFF16171C);
            }
        }
        writePng(root, "assets/myclient/textures/block/fake_spawner.png", tex);
        write(root, "assets/myclient/models/block/fake_spawner.json",
            "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"myclient:block/fake_spawner\"}}");
        write(root, "assets/" + ns + "/blockstates/" + path + ".json",
            "{\"multipart\":[{\"apply\":{\"model\":\"myclient:block/fake_spawner\"}}]}");
        write(root, "assets/" + ns + "/items/" + path + ".json",
            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"myclient:block/fake_spawner\"}}");
        String lang = "{\"block." + ns + "." + path + "\":\"Skeleton Spawner\"}";
        write(root, "assets/" + ns + "/lang/en_us.json", lang);
        write(root, "assets/" + ns + "/lang/de_de.json", lang);
    }

    public static boolean isEnabled() {
        try {
            return MinecraftClient.getInstance().getResourcePackManager().getEnabledIds().contains(ID);
        } catch (Exception e) {
            return false;
        }
    }

    private static void setEnabled(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager mgr = mc.getResourcePackManager();
        mgr.scanPacks();
        if (on) {
            if (!mgr.getEnabledIds().contains(ID)) mgr.enable(ID);
        } else {
            mgr.disable(ID);
        }
        mc.options.refreshResourcePacks(mgr);
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            List<Path> all = new ArrayList<>();
            s.forEach(all::add);
            Collections.reverse(all);
            for (Path f : all) Files.deleteIfExists(f);
        }
    }

    /** Rewrites the pack for the current settings and reloads resources. */
    public static void apply() throws IOException {
        Path root = dir();
        deleteTree(root.resolve("assets"));
        boolean any = false;
        if (Settings.fakeBlocks) { writeFakeBlocks(root); any = true; }
        if (Settings.butterfly) { ButterflyKnife.write(root, new HashSet<>()); any = true; }
        if (Settings.fakeSpawner) { writeSpawner(root); any = true; }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!any) {
            if (isEnabled()) setEnabled(false);
            return;
        }
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":46,\"supported_formats\":{\"min_inclusive\":34,\"max_inclusive\":99},"
                + "\"description\":\"Lumen fake blocks, butterfly knife and spawner\"}}");
        if (isEnabled()) mc.reloadResources(); else setEnabled(true);
    }
}
