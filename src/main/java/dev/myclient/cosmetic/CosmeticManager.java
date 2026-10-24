package dev.myclient.cosmetic;

import dev.myclient.cosmetic.Cosmetic.Animation;
import dev.myclient.cosmetic.Cosmetic.Type;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

public class CosmeticManager {
    public final List<Cosmetic> catalog = new ArrayList<>();
    public final Set<String> equipped = new LinkedHashSet<>();
    public final List<String> skies = new ArrayList<>(List.of("Default", "Lumen", "Starry Night", "Galaxy", "Aurora", "Nebula", "Sunset", "Blood Moon"));
    public final Map<String, int[]> uploadedSkies = new HashMap<>();   // name -> {top, bottom} preview colours
    public final Map<String, Path> skyFiles = new HashMap<>();
    public String activeSky = "Default";
    public long ticks;

    public CosmeticManager() {
        catalog.add(new Cosmetic("halo_classic", Type.HALO, List.of("gold", "ice", "neon", "purple"), Animation.FLOAT, Animation.PULSE, Animation.WAVE, Animation.RAINBOW));
        catalog.add(new Cosmetic("halo_celestial", Type.HALO, List.of("angel", "ice", "purple", "gold"), Animation.SPIN, Animation.ECLIPSE, Animation.FLOAT));
        catalog.add(new Cosmetic("halo_storm", Type.HALO, List.of("ice", "neon", "purple"), Animation.CRACKLE, Animation.WAVE, Animation.PULSE));
        catalog.add(new Cosmetic("headband_pulse", Type.HEADBAND, List.of("red", "blue", "green"), Animation.PULSE, Animation.FLOW, Animation.WHIP, Animation.RAINBOW));
        catalog.add(new Cosmetic("headband_night", Type.HEADBAND, List.of("galaxy", "aurora", "nebula", "midnight"), Animation.TWINKLE, Animation.FLOW, Animation.WHIP));
        catalog.add(new Cosmetic("cape_flow", Type.CAPE, List.of("crimson", "ocean", "void"), Animation.WAVE, Animation.BILLOW, Animation.NONE));
        catalog.add(new Cosmetic("cape_night", Type.CAPE, List.of("galaxy", "aurora", "nebula", "midnight"), Animation.TWINKLE, Animation.WAVE, Animation.BILLOW));
        catalog.add(new Cosmetic("cape_rainbow", Type.CAPE, List.of("default"), Animation.RAINBOW, Animation.WAVE));
        catalog.add(new Cosmetic("wings_feather", Type.WINGS, List.of("angel", "demon", "ember", "ice"), Animation.FLOAT, Animation.WAVE, Animation.BURST, Animation.NONE));
        catalog.add(new Cosmetic("glasses_square", Type.GLASSES, List.of("dark", "neon", "gold", "pink"), Animation.NONE, Animation.PULSE, Animation.SCAN));
        catalog.add(new Cosmetic("glasses_round", Type.GLASSES, List.of("dark", "neon", "gold", "pink"), Animation.WAVE, Animation.SCAN, Animation.PULSE, Animation.RAINBOW));
        catalog.add(new Cosmetic("aura_glow", Type.AURA, List.of("light", "flame", "frost", "void", "nature"), Animation.SWIRL, Animation.RISE, Animation.BURST, Animation.PULSE));
        catalog.add(new Cosmetic("trail_particles", Type.TRAIL, List.of("rainbow", "fire", "ice", "stardust", "soul", "magic"), Animation.FLOW, Animation.COMET, Animation.WAVE));
    }

    public void tick() { ticks++; }

    public void toggle(Cosmetic c) {
        boolean wasOn = equipped.contains(c.id);
        catalog.stream().filter(o -> o.type == c.type).forEach(o -> equipped.remove(o.id));
        if (!wasOn) equipped.add(c.id);
    }

    public boolean isEquipped(Cosmetic c) { return equipped.contains(c.id); }

    // ------------------------------------------------------------------ uploads
    private static Path dir(String sub) throws IOException {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("myclient").resolve(sub);
        Files.createDirectories(p);
        return p;
    }

    public static Path folder(String sub) {
        try { return dir(sub); } catch (IOException e) { return FabricLoader.getInstance().getConfigDir(); }
    }

    private static String slug(String s) {
        String r = s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return r.isEmpty() ? "image" : r;
    }

    private static String baseName(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static Path copyIn(Path src, String sub) throws IOException {
        Path dest = dir(sub).resolve(slug(baseName(src)) + ".png");
        if (!src.toAbsolutePath().equals(dest.toAbsolutePath())) Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private static int[] averages(Path file) {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            int w = img.getWidth(), h = img.getHeight();
            long[] top = new long[4], bot = new long[4], all = new long[4];
            int stepX = Math.max(1, w / 64), stepY = Math.max(1, h / 64);
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int p = img.getRGB(x, y);
                    if ((p >>> 24) < 40) continue;
                    long[] t = y < h * 0.25 ? top : (y > h * 0.75 ? bot : null);
                    for (long[] arr : t == null ? new long[][]{all} : new long[][]{all, t}) {
                        arr[0] += (p >> 16) & 255; arr[1] += (p >> 8) & 255; arr[2] += p & 255; arr[3]++;
                    }
                }
            }
            return new int[]{avg(top, all), avg(bot, all), avg(all, all)};
        } catch (Exception e) {
            return new int[]{0x6A3BD8, 0x1F3A9A, 0x6A3BD8};
        }
    }

    private static int avg(long[] a, long[] fallback) {
        long[] s = a[3] > 0 ? a : fallback;
        if (s[3] == 0) return 0xFF7C5CFF;
        return 0xFF000000 | ((int) (s[0] / s[3]) << 16) | ((int) (s[1] / s[3]) << 8) | (int) (s[2] / s[3]);
    }

    public Cosmetic addCape(Path src) throws IOException {
        return registerCape(copyIn(src, "capes"));
    }

    private Cosmetic registerCape(Path file) throws IOException {
        String slug = slug(baseName(file));
        String id = "cape_" + slug;
        NativeImage img;
        try (InputStream in = Files.newInputStream(file)) { img = NativeImage.read(in); }
        Identifier tex = Identifier.of("myclient", "capes/" + slug);
        MinecraftClient.getInstance().getTextureManager().registerTexture(tex, new NativeImageBackedTexture(img));
        Cosmetic c = new Cosmetic(id, Type.CAPE, List.of("custom"), Animation.WAVE, Animation.BILLOW, Animation.NONE, Animation.RAINBOW);
        c.custom = true;
        c.texture = tex;
        c.file = file;
        c.tintRgb = averages(file)[2] & 0xFFFFFF;
        catalog.removeIf(o -> o.id.equals(id));
        catalog.add(c);
        return c;
    }

    public String addSky(Path src) throws IOException {
        Path file = copyIn(src, "skies");
        return registerSky(file);
    }

    private String registerSky(Path file) {
        String name = baseName(file).replace('_', ' ').replace('-', ' ');
        name = name.isEmpty() ? "Custom" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
        int[] a = averages(file);
        uploadedSkies.put(name, new int[]{a[0], a[1]});
        skyFiles.put(name, file);
        if (!skies.contains(name)) skies.add(name);
        return name;
    }

    public void removeSky(String name) {
        Path f = skyFiles.remove(name);
        uploadedSkies.remove(name);
        skies.remove(name);
        if (name.equals(activeSky)) activeSky = "Default";
        dev.myclient.fx.SkyBox.forget(name);
        try { if (f != null) Files.deleteIfExists(f); } catch (IOException ignored) {}
    }

    /** Re-adds capes and skies the user uploaded in earlier sessions. */
    public void loadUploads() {
        try (Stream<Path> s = Files.list(dir("capes"))) {
            s.filter(p -> p.toString().toLowerCase().endsWith(".png")).forEach(p -> {
                try { registerCape(p); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        try (Stream<Path> s = Files.list(dir("skies"))) {
            s.filter(p -> p.toString().toLowerCase().endsWith(".png")).forEach(this::registerSky);
        } catch (Exception ignored) {}
    }
}
