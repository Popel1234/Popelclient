package dev.myclient.fake;

import dev.myclient.Settings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * The butterfly knives and the fake skeleton spawner look, delivered through an automatically managed resource
 * pack (resourcepacks/MyClientFake). Changing a setting rewrites the pack and reloads the game's resources.
 */
public final class FakePack {
    private FakePack() {}

    public static final String NAME = "MyClientFake";
    public static final String ID = "file/" + NAME;

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

    /** Makes the chosen block render see-through (like a real spawner). Returns false if Fabric's API was not found. */
    public static boolean registerCutout() {
        try {
            Identifier id = Identifier.tryParse(Settings.spawnerBlock);
            if (id == null || !Registries.BLOCK.containsId(id)) return false;
            Block b = Registries.BLOCK.get(id);
            Class<?> c = Class.forName("net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap");
            Object inst = c.getField("INSTANCE").get(null);
            Method m = c.getMethod("putBlock", Block.class, RenderLayer.class);
            m.invoke(inst, b, RenderLayer.getCutout());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void writeSpawner(Path root) throws IOException {
        Identifier id = Identifier.tryParse(Settings.spawnerBlock);
        if (id == null) return;
        String ns = id.getNamespace(), path = id.getPath();
        boolean cutout = registerCutout();
        String model;
        if (cutout) {
            model = "{\"parent\":\"minecraft:block/spawner\"}";   // the real see-through spawner cage
        } else {
            BufferedImage cage = read("block/spawner");
            BufferedImage tex = cage == null ? new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB) : compose(cage, 0xFF0F1117);
            Path f = root.resolve("assets/myclient/textures/block/fake_spawner.png");
            Files.createDirectories(f.getParent());
            ImageIO.write(tex, "png", f.toFile());
            model = "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"myclient:block/fake_spawner\"}}";
        }
        write(root, "assets/myclient/models/block/fake_spawner.json", model);
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
        if (Settings.butterfly) { KnifeDesign.write(root, new HashSet<>()); any = true; }
        if (Settings.fakeSpawner) { writeSpawner(root); any = true; }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!any) {
            if (isEnabled()) setEnabled(false);
            return;
        }
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":46,\"supported_formats\":{\"min_inclusive\":34,\"max_inclusive\":99},"
                + "\"description\":\"Lumen butterfly knives and skeleton spawner\"}}");
        if (isEnabled()) mc.reloadResources(); else setEnabled(true);
    }
}
