package dev.myclient.fx;

import dev.myclient.MyClient;
import dev.myclient.cosmetic.Impulse;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Our own particle system. Everything is drawn as smooth glowing shapes (no Minecraft textures),
 * with additive blending. Also hosts the sky renderer hook.
 */
public final class WorldFx {
    private WorldFx() {}

    public static final int DISC = 0, STAR = 1, SPARK = 2, ORB = 3;
    private static final float TAU = (float) (Math.PI * 2);

    private static final class P {
        double x, y, z;
        float vx, vy, vz, life, age, size, drag, grav;
        int rgb, shape;
    }

    private static final class Ring {
        double x, y, z;
        float life, age, maxR;
        int rgb;
    }

    private static final List<P> parts = new ArrayList<>();
    private static final List<Ring> rings = new ArrayList<>();
    private static long last = System.nanoTime();
    /** red screen flash after taking damage, 0..1 */
    public static float hurt;

    public static void register() {
        WorldRenderEvents.START.register(ctx -> MyClient.MODULES.freecam.frame(MinecraftClient.getInstance()));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldFx::render);
    }

    public static void spark(double x, double y, double z, float vx, float vy, float vz,
                             float life, float size, int rgb, int shape, float drag, float grav) {
        if (parts.size() > 900) return;
        P p = new P();
        p.x = x; p.y = y; p.z = z; p.vx = vx; p.vy = vy; p.vz = vz;
        p.life = life; p.size = size; p.rgb = rgb; p.shape = shape; p.drag = drag; p.grav = grav;
        parts.add(p);
    }

    public static void ring(double x, double y, double z, float maxR, float life, int rgb) {
        if (rings.size() > 24) return;
        Ring r = new Ring();
        r.x = x; r.y = y; r.z = z; r.maxR = maxR; r.life = life; r.rgb = rgb;
        rings.add(r);
    }

    /** Star-shaped burst with a ring on the ground. */
    public static void burst(double x, double y, double z, int rgb, int count, float speed) {
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, e = (rnd.nextDouble() - 0.3) * 1.2;
            float sp = speed * (0.5f + rnd.nextFloat());
            spark(x, y, z, (float) (Math.cos(a) * Math.cos(e) * sp), (float) (Math.sin(e) * sp), (float) (Math.sin(a) * Math.cos(e) * sp),
                0.4f + rnd.nextFloat() * 0.4f, 0.05f + rnd.nextFloat() * 0.05f, rgb, i % 3 == 0 ? STAR : SPARK, 2.2f, 1.2f);
        }
        spark(x, y, z, 0, 0, 0, 0.35f, 0.5f, rgb, DISC, 0f, 0f);
    }

    private static void update(float dt) {
        Impulse.decay(dt);
        hurt = Math.max(0f, hurt - dt * 1.6f);
        for (Iterator<P> it = parts.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) { it.remove(); continue; }
            float d = (float) Math.exp(-p.drag * dt);
            p.vx *= d; p.vy *= d; p.vz *= d;
            p.vy -= p.grav * dt;
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
        }
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring r = it.next();
            r.age += dt;
            if (r.age >= r.life) it.remove();
        }
    }

    private static void render(WorldRenderContext ctx) {
        try { SkyBox.render(ctx); } catch (Throwable ignored) {}
        try { drawFx(ctx); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ drawing
    private static void vtx(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, int a) {
        vc.vertex(m, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, a)));
    }

    private static void tri(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, int a1, float x2, float y2, float z2, int a2,
                            float x3, float y3, float z3, int a3, int rgb) {
        vtx(vc, m, x1, y1, z1, rgb, a1); vtx(vc, m, x2, y2, z2, rgb, a2); vtx(vc, m, x3, y3, z3, rgb, a3); vtx(vc, m, x3, y3, z3, rgb, a3);
        vtx(vc, m, x3, y3, z3, rgb, a3); vtx(vc, m, x3, y3, z3, rgb, a3); vtx(vc, m, x2, y2, z2, rgb, a2); vtx(vc, m, x1, y1, z1, rgb, a1);
    }

    private static void disc(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, Vector3f r, Vector3f u, float s, int rgb, int ac, int ae) {
        final int n = 8;
        for (int i = 0; i < n; i++) {
            float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            tri(vc, m, cx, cy, cz, ac,
                cx + (r.x * c0 + u.x * s0) * s, cy + (r.y * c0 + u.y * s0) * s, cz + (r.z * c0 + u.z * s0) * s, ae,
                cx + (r.x * c1 + u.x * s1) * s, cy + (r.y * c1 + u.y * s1) * s, cz + (r.z * c1 + u.z * s1) * s, ae, rgb);
        }
    }

    private static void star(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, Vector3f r, Vector3f u, float s, int rgb, int ac, int ae) {
        for (int k = 0; k < 4; k++) {
            double ang = k * Math.PI / 2;
            float tx = (float) Math.cos(ang), ty = (float) Math.sin(ang);
            float ix0 = (float) Math.cos(ang - Math.PI / 4) * 0.3f, iy0 = (float) Math.sin(ang - Math.PI / 4) * 0.3f;
            float ix1 = (float) Math.cos(ang + Math.PI / 4) * 0.3f, iy1 = (float) Math.sin(ang + Math.PI / 4) * 0.3f;
            tri(vc, m, cx, cy, cz, ac,
                cx + (r.x * ix0 + u.x * iy0) * s, cy + (r.y * ix0 + u.y * iy0) * s, cz + (r.z * ix0 + u.z * iy0) * s, ae,
                cx + (r.x * tx + u.x * ty) * s * 1.7f, cy + (r.y * tx + u.y * ty) * s * 1.7f, cz + (r.z * tx + u.z * ty) * s * 1.7f, 0, rgb);
            tri(vc, m, cx, cy, cz, ac,
                cx + (r.x * tx + u.x * ty) * s * 1.7f, cy + (r.y * tx + u.y * ty) * s * 1.7f, cz + (r.z * tx + u.z * ty) * s * 1.7f, 0,
                cx + (r.x * ix1 + u.x * iy1) * s, cy + (r.y * ix1 + u.y * iy1) * s, cz + (r.z * ix1 + u.z * iy1) * s, ae, rgb);
        }
    }

    private static void drawFx(WorldRenderContext ctx) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        update(dt);
        if (parts.isEmpty() && rings.isEmpty()) return;

        Camera cam = ctx.camera();
        VertexConsumerProvider vcp = ctx.consumers();
        if (cam == null || vcp == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) ms = new MatrixStack();
        Vec3d cp = cam.getPos();
        Matrix4f mat = ms.peek().getPositionMatrix();
        Quaternionf q = new Quaternionf(cam.getRotation());
        Vector3f right = new Vector3f(1, 0, 0).rotate(q), up = new Vector3f(0, 1, 0).rotate(q);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());

        for (P p : parts) {
            float k = 1f - p.age / p.life;
            int a = (int) (255 * Math.pow(k, 0.8));
            float x = (float) (p.x - cp.x), y = (float) (p.y - cp.y), z = (float) (p.z - cp.z);
            float s = p.size * (0.4f + 0.6f * k);
            switch (p.shape) {
                case STAR -> star(vc, mat, x, y, z, right, up, s, p.rgb, a, a / 2);
                case ORB -> {
                    disc(vc, mat, x, y, z, right, up, s * 1.8f, p.rgb, a / 2, 0);
                    disc(vc, mat, x, y, z, right, up, s * 0.7f, p.rgb | 0x404040, a, a / 2);
                }
                case SPARK -> {
                    float dr = p.vx * right.x + p.vy * right.y + p.vz * right.z, du = p.vx * up.x + p.vy * up.y + p.vz * up.z;
                    float len = (float) Math.sqrt(dr * dr + du * du);
                    if (len < 1e-4f) { disc(vc, mat, x, y, z, right, up, s, p.rgb, a, 0); break; }
                    dr /= len; du /= len;
                    float L = s * 4f, W = s * 0.45f;
                    float hx = x + (right.x * dr + up.x * du) * L, hy = y + (right.y * dr + up.y * du) * L, hz = z + (right.z * dr + up.z * du) * L;
                    float tx = x - (right.x * dr + up.x * du) * L, ty = y - (right.y * dr + up.y * du) * L, tz = z - (right.z * dr + up.z * du) * L;
                    float nxr = -du, nxu = dr;
                    float wx = (right.x * nxr + up.x * nxu) * W, wy = (right.y * nxr + up.y * nxu) * W, wz = (right.z * nxr + up.z * nxu) * W;
                    tri(vc, mat, hx, hy, hz, a, x + wx, y + wy, z + wz, a / 2, x - wx, y - wy, z - wz, a / 2, p.rgb);
                    tri(vc, mat, tx, ty, tz, 0, x + wx, y + wy, z + wz, a / 2, x - wx, y - wy, z - wz, a / 2, p.rgb);
                }
                default -> disc(vc, mat, x, y, z, right, up, s * 1.4f, p.rgb, a, 0);
            }
        }
        for (Ring r : rings) {
            float k = r.age / r.life;
            float rad = r.maxR * (1f - (1f - k) * (1f - k));
            float w = 0.06f + 0.12f * (1f - k);
            int a = (int) (200 * Math.pow(1f - k, 1.5));
            float y = (float) (r.y - cp.y) + 0.03f, cx = (float) (r.x - cp.x), cz = (float) (r.z - cp.z);
            int n = 40;
            for (int i = 0; i < n; i++) {
                float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float ro = rad + w * 0.3f, ri = Math.max(0f, rad - w);
                tri(vc, mat, cx + ri * c0, y, cz + ri * s0, 0, cx + ro * c0, y, cz + ro * s0, a, cx + ro * c1, y, cz + ro * s1, a, r.rgb);
                tri(vc, mat, cx + ri * c0, y, cz + ri * s0, 0, cx + ro * c1, y, cz + ro * s1, a, cx + ri * c1, y, cz + ri * s1, 0, r.rgb);
            }
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLightning());
    }
}
