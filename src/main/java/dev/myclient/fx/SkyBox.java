package dev.myclient.fx;

import dev.myclient.MyClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Draws the selected sky as a big textured sphere around the camera, painted after the world's solid
 * parts so only empty sky pixels get covered. Adds twinkling stars, falling stars and aurora curtains.
 */
public final class SkyBox {
    private SkyBox() {}

    private static final float TAU = (float) (Math.PI * 2);
    private static final int LON = 64, LAT = 32;
    private static final Map<String, Identifier> TEX = new HashMap<>();
    private static final Set<String> pending = new HashSet<>();
    private static final List<float[]> meteors = new ArrayList<>();   // lon, lat, dlon, dlat, age, life
    private static final Random rnd = new Random();
    private static float[] twinkle;
    private static float timer = 1f;
    private static long last = System.nanoTime();

    private static String slug(String s) {
        String r = s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return r.isEmpty() ? "sky" : r;
    }

    public static void forget(String name) { TEX.remove(name); pending.remove(name); }

    private static void request(String sky) {
        if (!pending.add(sky)) return;
        Thread th = new Thread(() -> {
            try {
                byte[] png;
                Path f = MyClient.COSMETICS.skyFiles.get(sky);
                if (f != null) {
                    png = Files.readAllBytes(f);
                } else {
                    BufferedImage img = SkyGen.generate(sky);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    ImageIO.write(img, "png", bo);
                    png = bo.toByteArray();
                }
                final byte[] data = png;
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    try {
                        NativeImage ni = NativeImage.read(new ByteArrayInputStream(data));
                        Identifier id = Identifier.of("myclient", "sky/" + slug(sky));
                        mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(ni));
                        TEX.put(sky, id);
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }, "myclient-sky");
        th.setDaemon(true);
        th.start();
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, int a) {
        vc.vertex(m, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, a)));
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float[] a, int aa, float[] b, int ab, float[] c, int ac, float[] d, int ad, int rgb) {
        vtx(vc, m, a[0], a[1], a[2], rgb, aa); vtx(vc, m, b[0], b[1], b[2], rgb, ab);
        vtx(vc, m, c[0], c[1], c[2], rgb, ac); vtx(vc, m, d[0], d[1], d[2], rgb, ad);
        vtx(vc, m, d[0], d[1], d[2], rgb, ad); vtx(vc, m, c[0], c[1], c[2], rgb, ac);
        vtx(vc, m, b[0], b[1], b[2], rgb, ab); vtx(vc, m, a[0], a[1], a[2], rgb, aa);
    }

    private static float[] sph(float lon, float lat, float r) {
        float cl = (float) Math.cos(lat);
        return new float[]{cl * (float) Math.cos(lon) * r, (float) Math.sin(lat) * r, cl * (float) Math.sin(lon) * r};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] norm(float[] a, float s) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (l < 1e-6f) return new float[]{0, 0, 0};
        return new float[]{a[0] / l * s, a[1] / l * s, a[2] / l * s};
    }

    private static float[] add(float[] a, float[] b, float k) {
        return new float[]{a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k};
    }

    public static void render(WorldRenderContext ctx) {
        String sky = MyClient.COSMETICS.activeSky;
        if (sky == null || sky.equals("Default")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Identifier tex = TEX.get(sky);
        if (tex == null) { request(sky); return; }
        VertexConsumerProvider vcp = ctx.consumers();
        if (vcp == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) ms = new MatrixStack();

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        float t = (float) ((now % 1_000_000_000_000L) / 1.0e9);
        float vd = mc.options.getViewDistance().getValue();
        float R = Math.max(200f, vd * 16f * 1.55f + 48f);

        ms.push();
        ms.scale(R, R, R);
        Matrix4f mat = ms.peek().getPositionMatrix();

        RenderLayer layer = RenderLayer.getGuiTextured(tex);
        VertexConsumer vc = vcp.getBuffer(layer);
        float[] cl = new float[LON + 1], sl = new float[LON + 1];
        for (int j = 0; j <= LON; j++) { cl[j] = (float) Math.cos(j * TAU / LON); sl[j] = (float) Math.sin(j * TAU / LON); }
        for (int i = 0; i < LAT; i++) {
            float p0 = (float) (Math.PI / 2 - i * Math.PI / LAT), p1 = (float) (Math.PI / 2 - (i + 1) * Math.PI / LAT);
            float c0 = (float) Math.cos(p0), s0 = (float) Math.sin(p0), c1 = (float) Math.cos(p1), s1 = (float) Math.sin(p1);
            float v0 = i / (float) LAT, v1 = (i + 1) / (float) LAT;
            for (int j = 0; j < LON; j++) {
                float u0 = 1f - j / (float) LON, u1 = 1f - (j + 1) / (float) LON;
                float[][] pos = {
                    {c0 * cl[j], s0, c0 * sl[j]}, {c0 * cl[j + 1], s0, c0 * sl[j + 1]},
                    {c1 * cl[j + 1], s1, c1 * sl[j + 1]}, {c1 * cl[j], s1, c1 * sl[j]}};
                float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                int[] order = {0, 1, 2, 3, 3, 2, 1, 0};
                for (int k : order) {
                    vc.vertex(mat, pos[k][0], pos[k][1], pos[k][2]).color(255, 255, 255, 255).texture(uv[k][0], uv[k][1])
                        .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0f, 1f, 0f);
                }
            }
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);

        // ---- animated overlays (additive) ----
        VertexConsumer glow = vcp.getBuffer(RenderLayer.getLightning());
        if (twinkle == null) {
            twinkle = new float[160 * 2];
            for (int i = 0; i < 160; i++) { twinkle[i * 2] = rnd.nextFloat() * TAU; twinkle[i * 2 + 1] = 0.12f + rnd.nextFloat() * 1.35f; }
        }
        for (int i = 0; i < 160; i++) {
            float lon = twinkle[i * 2], lat = twinkle[i * 2 + 1];
            float s = 0.5f + 0.5f * (float) Math.sin(t * (1.5f + (i % 7) * 0.4f) + i);
            float[] pc = sph(lon, lat, 0.995f);
            float[] e1 = norm(cross(pc, new float[]{0, 1, 0}), 1f), e2 = norm(cross(pc, e1), 1f);
            float sz = 0.0035f * (0.4f + 0.6f * s);
            int a = (int) (60 + 190 * s * s);
            quad(glow, mat, add(pc, e1, sz * 2.2f), 0, add(pc, e2, sz), a, add(pc, e1, -sz * 2.2f), 0, add(pc, e2, -sz), a, 0xFFFFFF);
            quad(glow, mat, add(pc, e2, sz * 2.2f), 0, add(pc, e1, -sz), a, add(pc, e2, -sz * 2.2f), 0, add(pc, e1, sz), a, 0xCFE4FF);
        }

        timer -= dt;
        if (timer <= 0f) {
            timer = 1.2f + rnd.nextFloat() * 2.6f;
            float dl = (0.25f + rnd.nextFloat() * 0.3f) * (rnd.nextBoolean() ? 1 : -1);
            meteors.add(new float[]{rnd.nextFloat() * TAU, 0.45f + rnd.nextFloat() * 0.8f, dl, -(0.2f + rnd.nextFloat() * 0.25f), 0f, 1.0f + rnd.nextFloat() * 0.8f});
        }
        for (Iterator<float[]> it = meteors.iterator(); it.hasNext(); ) {
            float[] m = it.next();
            m[4] += dt;
            if (m[4] >= m[5]) { it.remove(); continue; }
            float k = 1f - m[4] / m[5];
            float lon = m[0] + m[2] * m[4], lat = m[1] + m[3] * m[4];
            float[] head = sph(lon, lat, 0.99f), tail = sph(lon - m[2] * 0.3f, lat - m[3] * 0.3f, 0.99f);
            float[] dir = new float[]{tail[0] - head[0], tail[1] - head[1], tail[2] - head[2]};
            float[] side = norm(cross(head, dir), 0.0034f);
            int a = (int) (240 * Math.min(1f, k * 1.6f));
            quad(glow, mat, add(head, side, 1f), a, add(head, side, -1f), a, add(tail, side, -0.15f), 0, add(tail, side, 0.15f), 0, 0xDDEEFF);
            quad(glow, mat, add(head, side, 2.6f), a / 4, add(head, side, -2.6f), a / 4, add(tail, side, -0.3f), 0, add(tail, side, 0.3f), 0, 0x8FB8FF);
            float[] e1 = norm(cross(head, new float[]{0, 1, 0}), 1f), e2 = norm(cross(head, e1), 1f);
            float hs = 0.006f;
            quad(glow, mat, add(head, e1, hs), a, add(head, e2, hs), a, add(head, e1, -hs), a, add(head, e2, -hs), a, 0xFFFFFF);
        }

        if (sky.equals("Aurora")) {
            for (int band = 0; band < 3; band++) {
                float start = band * (TAU / 3f) + 0.3f;
                int steps = 40;
                for (int i = 0; i < steps; i++) {
                    float l0 = start + i * 1.1f / steps, l1 = start + (i + 1) * 1.1f / steps;
                    float b0 = 0.38f + 0.07f * (float) Math.sin(l0 * 3f + t * 0.5f + band), b1 = 0.38f + 0.07f * (float) Math.sin(l1 * 3f + t * 0.5f + band);
                    float h0 = 0.45f + 0.15f * (float) Math.sin(l0 * 2f - t * 0.7f), h1 = 0.45f + 0.15f * (float) Math.sin(l1 * 2f - t * 0.7f);
                    float fade = (float) Math.sin(Math.PI * i / steps);
                    int aBot = (int) (110 * fade), col = band == 1 ? 0x35F0B5 : (band == 0 ? 0x3DFFA0 : 0x59A6FF);
                    quad(glow, mat, sph(l0, b0, 0.985f), aBot, sph(l1, b1, 0.985f), aBot, sph(l1, b1 + h1, 0.985f), 0, sph(l0, b0 + h0, 0.985f), 0, col);
                    quad(glow, mat, sph(l0, b0 + h0 * 0.7f, 0.985f), 0, sph(l1, b1 + h1 * 0.7f, 0.985f), 0, sph(l1, b1 + h1, 0.985f), (int) (50 * fade), sph(l0, b0 + h0, 0.985f), (int) (50 * fade), 0xA77CFF);
                }
            }
        }
        ms.pop();
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLightning());
    }
}
