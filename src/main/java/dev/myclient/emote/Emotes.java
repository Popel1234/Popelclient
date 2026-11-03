package dev.myclient.emote;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;

/** Client-side emotes for your own character. One of them puts you on the hood of a bouncing Maybach. */
public final class Emotes {
    private Emotes() {}

    public static final String[][] LIST = {
        {"wave", "Wave"}, {"dance", "Dance"}, {"sit", "Sit on the ground"}, {"flex", "Flex"},
        {"maybach", "Maybach hood (recovery mode)"}};

    public static String current;
    private static long start;
    private static final Map<ModelPart, Float> applied = new IdentityHashMap<>();

    public static String nameOf(String id) {
        for (String[] e : LIST) if (e[0].equals(id)) return e[1];
        return id;
    }

    public static void toggle(String id) { if (id.equals(current)) stop(); else start(id); }

    public static void start(String id) {
        current = id;
        start = System.nanoTime();
        MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    public static void stop() { current = null; }

    public static boolean car() { return "maybach".equals(current); }

    private static float time() { return (System.nanoTime() - start) / 1.0e9f; }

    /** Walking, sneaking or jumping ends the emote. */
    public static void tick(MinecraftClient mc) {
        if (current == null || mc.player == null) return;
        Vec3d v = mc.player.getVelocity();
        if (v.x * v.x + v.z * v.z > 0.004 || mc.player.isSneaking() || !mc.player.isOnGround() || mc.options.jumpKey.isPressed()) stop();
    }

    // ------------------------------------------------------------------ pose
    private static void shift(ModelPart p, float off) {
        float prev = applied.getOrDefault(p, 0f);
        p.pivotY += off - prev;
        if (off == 0f) applied.remove(p); else applied.put(p, off);
    }

    private static void restoreAll() {
        for (Map.Entry<ModelPart, Float> e : applied.entrySet()) e.getKey().pivotY -= e.getValue();
        applied.clear();
    }

    /** Up/down motion of the Maybach's air suspension in "recovery mode" (in blocks). */
    static float bounce(float t) {
        return 0.14f + 0.11f * (float) Math.sin(t * 7.6f) + 0.045f * (float) Math.sin(t * 15.2f + 0.8f);
    }

    public static void pose(PlayerEntityModel m, PlayerEntityRenderState state) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean mine = false;
        if (mc.player != null) {
            double dx = state.x - mc.player.getX(), dy = state.y - mc.player.getY(), dz = state.z - mc.player.getZ();
            mine = dx * dx + dy * dy + dz * dz < 1.0;
        }
        if (!mine || current == null) { restoreAll(); return; }

        float t = time();
        ModelPart[] all = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        float off = 0f;
        switch (current) {
            case "wave" -> {
                m.rightArm.pitch = -2.9f;
                m.rightArm.roll = -0.25f + 0.55f * (float) Math.sin(t * 9f);
                m.head.roll = 0.06f * (float) Math.sin(t * 3f);
            }
            case "dance" -> {
                float s = (float) Math.sin(t * 6f);
                m.rightArm.pitch = -2.5f + 0.7f * s;
                m.leftArm.pitch = -2.5f - 0.7f * s;
                m.rightArm.roll = 0.3f;
                m.leftArm.roll = -0.3f;
                m.body.roll = 0.1f * s;
                m.head.roll = -0.1f * s;
                m.rightLeg.pitch = 0.6f * s;
                m.leftLeg.pitch = -0.6f * s;
                off = -Math.abs((float) Math.sin(t * 6f)) * 1.6f;
            }
            case "sit" -> {
                off = 9f;
                m.rightLeg.pitch = -1.45f; m.leftLeg.pitch = -1.45f;
                m.rightLeg.yaw = -0.12f; m.leftLeg.yaw = 0.12f;
                m.rightArm.pitch = -0.8f; m.leftArm.pitch = -0.8f;
                m.rightArm.roll = 0.12f; m.leftArm.roll = -0.12f;
                m.head.pitch = 0.05f * (float) Math.sin(t * 1.2f);
            }
            case "flex" -> {
                float k = 0.1f * (float) Math.sin(t * 5f);
                m.rightArm.roll = 1.35f + k; m.leftArm.roll = -1.35f - k;
                m.rightArm.pitch = -0.3f; m.leftArm.pitch = -0.3f;
                m.head.pitch = -0.15f;
            }
            case "maybach" -> {
                off = -bounce(t) * 17.07f;
                m.rightLeg.pitch = -1.2f; m.leftLeg.pitch = -1.2f;
                m.rightLeg.yaw = -0.14f; m.leftLeg.yaw = 0.14f;
                m.rightArm.pitch = -0.5f; m.leftArm.pitch = -0.5f;
                m.rightArm.roll = 0.18f; m.leftArm.roll = -0.18f;
                m.head.yaw = 0.18f * (float) Math.sin(t * 0.9f);
            }
            default -> { }
        }
        for (ModelPart p : all) shift(p, off);
        m.hat.copyTransform(m.head);
        m.jacket.copyTransform(m.body);
        m.rightSleeve.copyTransform(m.rightArm);
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightPants.copyTransform(m.rightLeg);
        m.leftPants.copyTransform(m.leftLeg);
    }

    // ------------------------------------------------------------------ the Maybach
    /** car coordinates (x sideways, yUp from the ground, zF forward) -> feature/model space */
    private static float[] p(float x, float yUp, float zF) {
        return new float[]{x / 0.9375f, 1.501f - yUp / 0.9375f, -zF / 0.9375f};
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static void v(VertexConsumer vc, Matrix4f m, float[] a, int rgb, int al) {
        vc.vertex(m, a[0], a[1], a[2]).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, al);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, int rgb, int al) {
        v(vc, m, a, rgb, al); v(vc, m, b, rgb, al); v(vc, m, c, rgb, al); v(vc, m, d, rgb, al);
        v(vc, m, d, rgb, al); v(vc, m, c, rgb, al); v(vc, m, b, rgb, al); v(vc, m, a, rgb, al);
    }

    private static void box(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int rgb, float lf) {
        quad(vc, m, p(x1, y2, z1), p(x2, y2, z1), p(x2, y2, z2), p(x1, y2, z2), shade(rgb, 1.0f * lf), 255);
        quad(vc, m, p(x1, y1, z1), p(x2, y1, z1), p(x2, y1, z2), p(x1, y1, z2), shade(rgb, 0.5f * lf), 255);
        quad(vc, m, p(x1, y1, z2), p(x2, y1, z2), p(x2, y2, z2), p(x1, y2, z2), shade(rgb, 0.88f * lf), 255);
        quad(vc, m, p(x1, y1, z1), p(x2, y1, z1), p(x2, y2, z1), p(x1, y2, z1), shade(rgb, 0.82f * lf), 255);
        quad(vc, m, p(x1, y1, z1), p(x1, y1, z2), p(x1, y2, z2), p(x1, y2, z1), shade(rgb, 0.72f * lf), 255);
        quad(vc, m, p(x2, y1, z1), p(x2, y1, z2), p(x2, y2, z2), p(x2, y2, z1), shade(rgb, 0.76f * lf), 255);
    }

    private static void wheel(VertexConsumer vc, Matrix4f m, float xc, float zc, float lf) {
        float r = 0.34f, th = 0.12f;
        int n = 10;
        for (int k = 0; k < n; k++) {
            double a0 = k * Math.PI * 2 / n, a1 = (k + 1) * Math.PI * 2 / n;
            float y0 = r + r * (float) Math.sin(a0), z0 = zc + r * (float) Math.cos(a0), y1 = r + r * (float) Math.sin(a1), z1 = zc + r * (float) Math.cos(a1);
            quad(vc, m, p(xc - th, y0, z0), p(xc + th, y0, z0), p(xc + th, y1, z1), p(xc - th, y1, z1), shade(0x151515, lf), 255);
            float sx = xc > 0 ? xc + th : xc - th;
            quad(vc, m, p(sx, r, zc), p(sx, y0, z0), p(sx, y1, z1), p(sx, y1, z1), shade(0x20222A, lf), 255);
            float ri = 0.2f;
            float ya = r + ri * (float) Math.sin(a0), za = zc + ri * (float) Math.cos(a0), yb = r + ri * (float) Math.sin(a1), zb = zc + ri * (float) Math.cos(a1);
            float sx2 = xc > 0 ? xc + th + 0.01f : xc - th - 0.01f;
            quad(vc, m, p(sx2, r, zc), p(sx2, ya, za), p(sx2, yb, zb), p(sx2, yb, zb), shade(0xC4C9D1, lf), 255);
        }
    }

    public static void renderCar(MatrixStack ms, VertexConsumerProvider vcp, float lf) {
        float t = time(), b = bounce(t);
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
        int body = 0x15171C, chrome = 0xC9CED6, glass = 0x1A2A3A;

        box(vc, mat, -0.95f, 0.30f + b, -3.9f, 0.95f, 0.75f + b, 1.4f, body, lf);              // body
        box(vc, mat, -0.9f, 0.75f + b, -0.95f, 0.9f, 0.79f + b, 1.38f, 0x23262D, lf);          // hood
        box(vc, mat, -0.92f, 0.75f + b, -3.88f, 0.92f, 0.9f + b, -2.95f, body, lf);            // trunk
        box(vc, mat, -0.88f, 0.75f + b, -2.95f, 0.88f, 1.42f + b, -0.95f, body, lf);           // cabin
        box(vc, mat, -0.96f, 0.58f + b, -3.8f, -0.95f, 0.62f + b, 1.35f, chrome, lf);          // chrome lines
        box(vc, mat, 0.95f, 0.58f + b, -3.8f, 0.96f, 0.62f + b, 1.35f, chrome, lf);
        quad(vc, mat, p(-0.8f, 0.86f + b, -0.94f), p(0.8f, 0.86f + b, -0.94f), p(0.8f, 1.36f + b, -0.94f), p(-0.8f, 1.36f + b, -0.94f), shade(glass, lf), 235);   // windshield
        quad(vc, mat, p(-0.8f, 0.86f + b, -2.96f), p(0.8f, 0.86f + b, -2.96f), p(0.8f, 1.36f + b, -2.96f), p(-0.8f, 1.36f + b, -2.96f), shade(glass, lf), 235);  // rear window
        for (float sx : new float[]{-0.89f, 0.89f}) {
            quad(vc, mat, p(sx, 0.9f + b, -2.8f), p(sx, 0.9f + b, -1.15f), p(sx, 1.34f + b, -1.15f), p(sx, 1.34f + b, -2.8f), shade(glass, lf), 235);
        }
        box(vc, mat, -0.45f, 0.38f + b, 1.4f, 0.45f, 0.7f + b, 1.43f, 0x0A0B0D, lf);          // grille
        for (int k = 0; k < 7; k++) box(vc, mat, -0.42f + k * 0.14f, 0.4f + b, 1.43f, -0.40f + k * 0.14f, 0.68f + b, 1.45f, chrome, lf);
        for (int k = 0; k < 3; k++) {                                                            // three-pointed star on the hood
            double a = Math.PI / 2 + k * Math.PI * 2 / 3;
            float cx = 0f, cy = 0.9f + b, zz = 1.2f, ex = (float) Math.cos(a) * 0.1f, ey = (float) Math.sin(a) * 0.1f;
            quad(vc, mat, p(cx - 0.015f, cy, zz), p(cx + 0.015f, cy, zz), p(cx + ex + 0.015f, cy + ey, zz), p(cx + ex - 0.015f, cy + ey, zz), shade(chrome, lf), 255);
        }
        float[] wx = {-0.95f, 0.95f};
        for (float x : wx) { wheel(vc, mat, x, 0.85f, lf); wheel(vc, mat, x, -2.9f, lf); }

        VertexConsumer glow = vcp.getBuffer(RenderLayer.getLightning());
        for (float x : new float[]{-0.7f, 0.7f}) {
            quad(glow, mat, p(x - 0.15f, 0.55f + b, 1.44f), p(x + 0.15f, 0.55f + b, 1.44f), p(x + 0.15f, 0.66f + b, 1.44f), p(x - 0.15f, 0.66f + b, 1.44f), 0xFFF2D0, 230);
            quad(glow, mat, p(x - 0.3f, 0.47f + b, 1.45f), p(x + 0.3f, 0.47f + b, 1.45f), p(x + 0.3f, 0.74f + b, 1.45f), p(x - 0.3f, 0.74f + b, 1.45f), 0xFFF2D0, 50);
        }
    }
}
