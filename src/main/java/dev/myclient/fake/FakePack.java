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

/**
 * Fake block textures and the butterfly knife, delivered through an automatically managed resource pack
 * (resourcepacks/MyClientFake). Turning the settings on/off rewrites the pack and reloads the game.
 */
public final class FakePack {
    private FakePack() {}

    public static final String NAME = "MyClientFake";
    public static final String ID = "file/" + NAME;

    /** from -> to, which texture files change, where the new look comes from, and every block that is affected. */
    public record Swap(String from, String to, String affects, String[] targets, String[] sources, int bg) {}

    public static final List<Swap> SWAPS = List.of(
        new Swap("Dirt Block", "Mace", "Dirt, Grass Block (bottom), Podzol (bottom), Mycelium (bottom), Farmland (bottom), Dirt Path (bottom)",
            new String[]{"dirt"}, new String[]{"item/mace"}, 0xFF5B4636),
        new Swap("Stone", "Trident", "Stone, Stone Stairs, Stone Slab, Stone Button, Stone Pressure Plate, Infested Stone",
            new String[]{"stone"}, new String[]{"item/trident"}, 0xFF4A5A66),
        new Swap("Deepslate", "Ancient Debris", "Deepslate, Infested Deepslate",
            new String[]{"deepslate", "deepslate_top"}, new String[]{"block/ancient_debris_side", "block/ancient_debris_top"}, 0),
        new Swap("Cobbled Deepslate", "Emerald Block", "Cobbled Deepslate, its Stairs, Slab and Wall",
            new String[]{"cobbled_deepslate"}, new String[]{"block/emerald_block"}, 0),
        new Swap("End Stone", "Iron Block", "End Stone",
            new String[]{"end_stone"}, new String[]{"block/iron_block"}, 0),
        new Swap("Oak Log", "Gold Block", "Oak Log, Oak Wood",
            new String[]{"oak_log", "oak_log_top"}, new String[]{"block/gold_block", "block/gold_block"}, 0),
        new Swap("Oak Planks", "Diamond Block", "Oak Planks, Stairs, Slab, Fence, Fence Gate, Button, Pressure Plate",
            new String[]{"oak_planks"}, new String[]{"block/diamond_block"}, 0));

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

    /** Puts see-through item art (mace, trident) on a solid background so the block face is not holey. */
    private static BufferedImage compose(BufferedImage src, int bg) {
        if (bg == 0) return src;
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

    private static Path texPath(Path root, String target) {
        return root.resolve("assets").resolve("minecraft").resolve("textures").resolve("block").resolve(target + ".png");
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

    /** Rewrites the pack for the current settings and reloads resources. */
    public static void apply() throws IOException {
        Path root = dir();
        Set<String> written = new HashSet<>();
        if (Settings.fakeBlocks) {
            for (Swap s : SWAPS) {
                for (int i = 0; i < s.targets().length; i++) {
                    BufferedImage src = read(s.sources()[i]);
                    if (src == null) continue;
                    Path f = texPath(root, s.targets()[i]);
                    Files.createDirectories(f.getParent());
                    ImageIO.write(compose(src, s.bg()), "png", f.toFile());
                    written.add(f.toString());
                }
            }
        }
        if (Settings.butterfly) ButterflyKnife.write(root, written);

        List<Path> all = new ArrayList<>(ButterflyKnife.files(root));
        for (Swap s : SWAPS) for (String t : s.targets()) all.add(texPath(root, t));
        for (Path f : all) if (!written.contains(f.toString())) Files.deleteIfExists(f);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (written.isEmpty()) {
            if (isEnabled()) setEnabled(false);
            return;
        }
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"),
            "{\"pack\":{\"pack_format\":46,\"supported_formats\":{\"min_inclusive\":34,\"max_inclusive\":99},"
                + "\"description\":\"Lumen fake textures and butterfly knife\"}}");
        if (isEnabled()) mc.reloadResources(); else setEnabled(true);
    }
}
