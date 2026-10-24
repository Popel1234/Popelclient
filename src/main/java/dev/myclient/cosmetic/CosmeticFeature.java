package dev.myclient.cosmetic;

import dev.myclient.MyClient;
import dev.myclient.cosmetic.Cosmetic.Animation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws equipped cosmetics procedurally on YOUR player (third person, F5).
 * Model space: +Y points DOWN, +Z is the back of the player, the head top is at y = -0.5, feet at y = +1.5.
 */
public class CosmeticFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final float TAU = (float) (Math.PI * 2);

    public CosmeticFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light,
                       PlayerEntityRenderState state, float a, float b) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double dx = state.x - mc.player.getX();
        double dy = state.y - mc.player.getY();
        double dz = state.z - mc.player.getZ();
        if (dx * dx + dy * dy + dz * dz > 1.0) return;

        float t = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);
        float lf = 0.4f + 0.6f * Math.max((light >> 4) & 15, (light >> 20) & 15) / 15f;

        Vec3d vel = mc.player.getVelocity();
        double yaw = Math.toRadians(mc.player.getBodyYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        float fwd = (float) (vel.x * fx + vel.z * fz);
        float lat = (float) (vel.x * (-fz) + vel.z * fx);
        float vy = (float) vel.y;
        float move = Math.abs(fwd);

        List<Cosmetic> toDraw = new ArrayList<>();
        for (Cosmetic c0 : MyClient.COSMETICS.catalog) if (MyClient.COSMETICS.isEquipped(c0)) toDraw.add(c0);
        if (Preview.enabled && Preview.draft != null) toDraw.add(Preview.draft);

        for (Cosmetic c : toDraw) {
            m.push();
            switch (c.type) {
                case HALO -> { getContextModel().head.rotate(m); halo(m, vcp, c, t); }
                case HEADBAND -> { getContextModel().head.rotate(m); band(m, vcp, c, t, lf, move); }
                case GLASSES -> { getContextModel().head.rotate(m); glasses(m, vcp, c, t); }
                case CAPE -> { getContextModel().body.rotate(m); cape(m, vcp, c, t, light, lf, fwd, lat, vy); }
                case WINGS -> { getContextModel().body.rotate(m); wings(m, vcp, c, t, lf); }
                case AURA -> aura(m, vcp, c, t);
                case TRAIL -> trail(m, vcp, c, t, state);
                default -> { }
            }
            m.pop();
        }
    }

    // ===================================================================== colours
    private static boolean isNight(String v) {
        return v.equals("galaxy") || v.equals("aurora") || v.equals("nebula") || v.equals("midnight");
    }

    private static int color(Cosmetic c, float t, float offset) {
        if (c.animation == Animation.RAINBOW) return Palette.rainbow(t * 0.2f * c.speed + offset, 0.75f, 1f);
        return Palette.rgb(c);
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int bl = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | bl;
    }

    private static int mix(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * f);
        return (r << 16) | (g << 8) | bl;
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    private static int night(String var, float u, float v, float t, int hash) {
        return night(var, u, v, t, hash, 11, 1f);
    }

    /** Animated night-sky pattern with twinkling stars. u,v in 0..1. */
    private static int night(String var, float u, float v, float t, int hash, int starMod, float tw) {
        int base;
        switch (var) {
            case "galaxy" -> {
                base = mix(0x0A0F2E, 0x6A3BD8, (0.5f + 0.5f * MathHelper.sin(u * 6f + t * 0.6f)) * (1f - v * 0.4f));
                base = mix(base, 0x2AD6FF, Math.max(0f, MathHelper.sin(u * 10f - t)) * 0.3f);
            }
            case "aurora" -> {
                float g = 0.5f + 0.5f * MathHelper.sin(u * 7f + t * 1.2f + v * 2f);
                base = mix(0x06161E, 0x2DF5A6, g * (1f - v * 0.7f));
                base = mix(base, 0x9A5CFF, Math.max(0f, MathHelper.sin(u * 4f - t * 0.8f)) * 0.35f * v);
            }
            case "nebula" -> {
                base = mix(0x2A0A3A, 0xFF4FBF, 0.5f + 0.5f * MathHelper.sin(u * 5f - t * 0.8f + v * 3f));
                base = mix(base, 0x4F7CFF, Math.max(0f, MathHelper.sin(u * 9f + t * 0.5f)) * 0.3f);
            }
            default -> base = mix(0x050816, 0x1B2A6B, v);
        }
        if (hash % starMod == 0) {
            float s = 0.5f + 0.5f * MathHelper.sin(t * 3f * tw + (hash & 255) * 0.1f);
            base = mix(base, 0xFFFFFF, s * s);
        }
        return base;
    }

    // ===================================================================== vertex helpers
    private static void v(VertexConsumer vc, Matrix4f mat, float x, float y, float z, int rgb, int a) {
        vc.vertex(mat, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, a)));
    }

    private static void quadC(VertexConsumer vc, Matrix4f mat,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float x3, float y3, float z3, float x4, float y4, float z4,
                              int c1, int c2, int c3, int c4, int a1, int a2, int a3, int a4) {
        v(vc, mat, x1, y1, z1, c1, a1); v(vc, mat, x2, y2, z2, c2, a2);
        v(vc, mat, x3, y3, z3, c3, a3); v(vc, mat, x4, y4, z4, c4, a4);
        v(vc, mat, x4, y4, z4, c4, a4); v(vc, mat, x3, y3, z3, c3, a3);
        v(vc, mat, x2, y2, z2, c2, a2); v(vc, mat, x1, y1, z1, c1, a1);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int rgb, int a) {
        quadC(vc, mat, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, rgb, rgb, rgb, rgb, a, a, a, a);
    }

    private static void tv(VertexConsumer vc, Matrix4f mat, float[] p, float u, float w, int light, int rgb) {
        vc.vertex(mat, p[0], p[1], p[2]).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
            .texture(u, w).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 0f, 1f);
    }

    // ===================================================================== HALO
    private static float haloShade(Animation an, float a0, float t, float sp, int i, boolean glow) {
        float f = 1f;
        if (an == Animation.SPIN) f = (glow ? 0.2f : 0.35f) + (glow ? 0.8f : 0.65f) * (0.5f + 0.5f * MathHelper.cos(a0 - t * 4f * sp));
        else if (an == Animation.ECLIPSE) {
            float wd = Math.max(0f, MathHelper.cos(a0 - t * 1.6f * sp));
            f = 1f - 0.85f * wd * wd;
        } else if (an == Animation.CRACKLE && (hash((int) (t * 28f), i, 5) & 3) == 0) f = 0.25f;
        return f;
    }

    private void halo(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t) {
        float sp = c.speed;
        Animation an = c.animation;
        boolean celestial = c.id.contains("celestial"), storm = c.id.contains("storm") || an == Animation.CRACKLE;
        float bob = (an == Animation.FLOAT || celestial) ? MathHelper.sin(t * 2f * sp) * 0.035f : 0f;
        float pulse = (an == Animation.PULSE ? 1f + 0.12f * MathHelper.sin(t * 4f * sp) : 1f) + Impulse.value * 0.2f;
        float wob = an == Animation.WAVE ? 0.03f : 0f;
        float jamp = an == Animation.CRACKLE ? 0.024f : 0.014f;
        float y = -0.76f + bob - Impulse.value * 0.05f;
        float ri = 0.19f * pulse, ro = 0.27f * pulse;
        final int n = 36;
        Matrix4f mat = m.peek().getPositionMatrix();

        VertexConsumer solid = vcp.getBuffer(RenderLayer.getDebugQuads());
        for (int i = 0; i < n; i++) {
            float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            float j0 = storm ? MathHelper.sin(a0 * 9f + t * 25f) * jamp : 0f;
            float j1 = storm ? MathHelper.sin(a1 * 9f + t * 25f) * jamp : 0f;
            float y0 = y + wob * MathHelper.sin(a0 * 3f + t * 3f * sp);
            float y1 = y + wob * MathHelper.sin(a1 * 3f + t * 3f * sp);
            int col = shade(color(c, t, i / (float) n), haloShade(an, a0, t, sp, i, false));
            quad(solid, mat,
                (ri + j0) * MathHelper.cos(a0), y0, (ri + j0) * MathHelper.sin(a0),
                (ro + j0) * MathHelper.cos(a0), y0, (ro + j0) * MathHelper.sin(a0),
                (ro + j1) * MathHelper.cos(a1), y1, (ro + j1) * MathHelper.sin(a1),
                (ri + j1) * MathHelper.cos(a1), y1, (ri + j1) * MathHelper.sin(a1), col, 255);
        }
        if (celestial) {
            for (int i = 0; i < n; i++) {
                float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
                float f = 0.4f + 0.6f * (0.5f + 0.5f * MathHelper.cos(a0 + t * 5f * sp));
                int col = mix(shade(color(c, t, 0.5f), 1f), 0xFFFFFF, f * 0.7f);
                quad(solid, mat,
                    0.12f * MathHelper.cos(a0), y - 0.012f, 0.12f * MathHelper.sin(a0),
                    0.15f * MathHelper.cos(a0), y - 0.012f, 0.15f * MathHelper.sin(a0),
                    0.15f * MathHelper.cos(a1), y - 0.012f, 0.15f * MathHelper.sin(a1),
                    0.12f * MathHelper.cos(a1), y - 0.012f, 0.12f * MathHelper.sin(a1), col, 255);
            }
        }

        VertexConsumer glow = vcp.getBuffer(RenderLayer.getLightning());
        float gi = ri - 0.035f, go = ro + 0.05f;
        for (int i = 0; i < n; i++) {
            float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            int col = shade(color(c, t, i / (float) n), haloShade(an, a0, t, sp, i, true));
            quad(glow, mat,
                gi * MathHelper.cos(a0), y, gi * MathHelper.sin(a0), go * MathHelper.cos(a0), y, go * MathHelper.sin(a0),
                go * MathHelper.cos(a1), y, go * MathHelper.sin(a1), gi * MathHelper.cos(a1), y, gi * MathHelper.sin(a1), col, 70);
        }
        int beams = celestial ? 10 : 6;
        for (int k = 0; k < beams; k++) {
            float ang = k * TAU / beams + t * 0.6f * sp;
            float rr = (ri + ro) / 2f, bx = rr * MathHelper.cos(ang), bz = rr * MathHelper.sin(ang);
            float w = 0.022f, h = 0.26f + 0.1f * MathHelper.sin(t * 3f * sp + k) + Impulse.value * 0.2f;
            int col = color(c, t, k / (float) beams);
            quadC(glow, mat, bx - w, y, bz, bx + w, y, bz, bx + w * 0.3f, y - h, bz, bx - w * 0.3f, y - h, bz,
                col, col, col, col, 90, 90, 0, 0);
            quadC(glow, mat, bx, y, bz - w, bx, y, bz + w, bx, y - h, bz + w * 0.3f, bx, y - h, bz - w * 0.3f,
                col, col, col, col, 90, 90, 0, 0);
        }
        if (celestial) {
            int shift = (int) Math.floor(t * 5f * sp);
            for (int i = 0; i < n; i++) {
                if (((i + shift) % 3 + 3) % 3 == 0) continue;
                float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
                int col = color(c, t, 0.25f);
                quad(glow, mat,
                    0.33f * MathHelper.cos(a0), y, 0.33f * MathHelper.sin(a0), 0.36f * MathHelper.cos(a0), y, 0.36f * MathHelper.sin(a0),
                    0.36f * MathHelper.cos(a1), y, 0.36f * MathHelper.sin(a1), 0.33f * MathHelper.cos(a1), y, 0.33f * MathHelper.sin(a1), col, 130);
            }
        }
        int stars = celestial ? 7 : 4;
        for (int k = 0; k < stars; k++) {
            float ang = t * 2.2f * sp * (celestial ? -1f : 1f) + k * TAU / stars;
            float rad = celestial ? 0.34f : 0.31f;
            float sx = rad * MathHelper.cos(ang), sz = rad * MathHelper.sin(ang);
            float sy = y - 0.04f + 0.05f * MathHelper.sin(t * 3f + k * 1.7f);
            float s = 0.055f * (0.6f + 0.4f * MathHelper.sin(t * 5f + k * 1.3f));
            int col = mix(color(c, t, k / (float) stars), 0xFFFFFF, 0.6f);
            quad(glow, mat, sx, sy - s, sz, sx + s * 0.35f, sy, sz, sx, sy + s, sz, sx - s * 0.35f, sy, sz, col, 210);
            quad(glow, mat, sx, sy - s, sz, sx, sy, sz + s * 0.35f, sx, sy + s, sz, sx, sy, sz - s * 0.35f, col, 210);
            quad(glow, mat, sx - s, sy, sz, sx, sy, sz + s * 0.35f, sx + s, sy, sz, sx, sy, sz - s * 0.35f, col, 210);
        }
    }

    // ===================================================================== HEADBAND
    private void band(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t, float lf, float move) {
        float sp = c.speed;
        Animation an = c.animation;
        boolean night = !c.custom && isNight(c.activeVariant);
        boolean grid = night || an == Animation.FLOW;
        int starMod = an == Animation.TWINKLE ? 6 : 11;
        float tw = an == Animation.TWINKLE ? 2.2f : 1f;
        float p = an == Animation.PULSE && !night ? 0.6f + 0.4f * MathHelper.sin(t * 4f * sp) : 1f;
        float ho = 0.315f, hi = 0.27f, y0 = -0.37f, y1 = -0.20f;
        Matrix4f mat = m.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
        float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

        for (int f = 0; f < 4; f++) {
            float ax = corner[f][0] * ho, az = corner[f][1] * ho;
            float bx = corner[(f + 1) % 4][0] * ho, bz = corner[(f + 1) % 4][1] * ho;
            if (grid) {
                int cols = 14, rows = 3;
                for (int r = 0; r < rows; r++) {
                    for (int cc = 0; cc < cols; cc++) {
                        float u0 = cc / (float) cols, u1 = (cc + 1) / (float) cols;
                        float ya = y0 + (y1 - y0) * r / rows, yb = y0 + (y1 - y0) * (r + 1) / rows;
                        float uu = (f + u0) / 4f;
                        int col;
                        if (night) col = shade(night(c.activeVariant, uu, r / (float) rows, t, hash(f, cc, r), starMod, tw), lf);
                        else {
                            float wave = 0.5f + 0.5f * MathHelper.sin(uu * TAU * 2f - t * 4f * sp);
                            col = shade(color(c, t, uu), (0.35f + 0.75f * wave) * lf);
                        }
                        quad(vc, mat, ax + (bx - ax) * u0, ya, az + (bz - az) * u0, ax + (bx - ax) * u1, ya, az + (bz - az) * u1,
                            ax + (bx - ax) * u1, yb, az + (bz - az) * u1, ax + (bx - ax) * u0, yb, az + (bz - az) * u0, col, 255);
                    }
                }
            } else {
                int col = shade(color(c, t, f * 0.25f), p * lf);
                quad(vc, mat, ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az, col, 255);
            }
            float k = hi / ho;
            int edge = night ? shade(night(c.activeVariant, f / 4f, 0f, t, 7), lf * 0.8f) : shade(color(c, t, f * 0.25f), p * lf * 0.8f);
            quad(vc, mat, ax, y0, az, bx, y0, bz, bx * k, y0, bz * k, ax * k, y0, az * k, edge, 255);
            quad(vc, mat, ax, y1, az, bx, y1, bz, bx * k, y1, bz * k, ax * k, y1, az * k, edge, 255);
        }
        float flapAmp = an == Animation.WHIP ? 0.13f + Math.min(0.2f, move * 1.2f + Impulse.value * 0.1f)
            : (an == Animation.NONE || an == Animation.PULSE) ? 0.02f : 0.05f;
        float flapSpeed = an == Animation.WHIP ? 7f : 3f;
        for (int k = 0; k < 2; k++) {
            float xa = k == 0 ? -0.08f : 0.02f, xb = xa + 0.06f;
            float off = 0.06f + flapAmp * MathHelper.sin(t * flapSpeed * sp + k * 1.7f);
            int tc = night ? shade(night(c.activeVariant, 0.3f + k * 0.2f, 0.5f, t, 3 + k, starMod, tw), lf)
                : shade(color(c, t, 0.3f + k * 0.2f), p * lf);
            quad(vc, mat, xa, y0 + 0.01f, ho + 0.004f, xb, y0 + 0.01f, ho + 0.004f,
                xb + off * 0.3f, y0 + 0.42f, ho + off, xa + off * 0.3f, y0 + 0.42f, ho + off, tc, 255);
        }
    }

    // ===================================================================== GLASSES
    private void glasses(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t) {
        float sp = c.speed;
        Animation an = c.animation;
        boolean round = c.id.contains("round");
        boolean dark = !c.custom && c.activeVariant.equals("dark");
        int frame = dark ? 0x2A2A38 : color(c, t, 0f);
        int lens = dark ? 0x05050A : shade(frame, 0.55f);
        int lensA = dark ? 205 : 125;
        float zf = -0.305f, cy = -0.215f, hw = 0.095f, hh = 0.072f, fw = 0.024f;
        Matrix4f mat = m.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());

        for (int s = -1; s <= 1; s += 2) {
            float cx = s * 0.125f;
            if (!round) {
                quad(vc, mat, cx - hw - fw, cy - hh - fw, zf, cx + hw + fw, cy - hh - fw, zf, cx + hw + fw, cy - hh, zf, cx - hw - fw, cy - hh, zf, frame, 255);
                quad(vc, mat, cx - hw - fw, cy + hh, zf, cx + hw + fw, cy + hh, zf, cx + hw + fw, cy + hh + fw, zf, cx - hw - fw, cy + hh + fw, zf, frame, 255);
                quad(vc, mat, cx - hw - fw, cy - hh, zf, cx - hw, cy - hh, zf, cx - hw, cy + hh, zf, cx - hw - fw, cy + hh, zf, frame, 255);
                quad(vc, mat, cx + hw, cy - hh, zf, cx + hw + fw, cy - hh, zf, cx + hw + fw, cy + hh, zf, cx + hw, cy + hh, zf, frame, 255);
                quad(vc, mat, cx - hw, cy - hh, zf, cx + hw, cy - hh, zf, cx + hw, cy + hh, zf, cx - hw, cy + hh, zf, lens, lensA);
            } else {
                int seg = 14;
                float ex = hw, ey = hh + 0.018f;
                for (int i = 0; i < seg; i++) {
                    float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
                    float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
                    quad(vc, mat, cx + (ex + fw) * c0, cy + (ey + fw) * s0, zf, cx + (ex + fw) * c1, cy + (ey + fw) * s1, zf,
                        cx + ex * c1, cy + ey * s1, zf, cx + ex * c0, cy + ey * s0, zf, frame, 255);
                    quad(vc, mat, cx, cy, zf, cx + ex * c0, cy + ey * s0, zf, cx + ex * c1, cy + ey * s1, zf, cx, cy, zf, lens, lensA);
                }
            }
            float tx = s * (0.125f + hw + fw);
            quad(vc, mat, tx, cy - 0.012f, zf, tx, cy + 0.012f, zf, s * 0.29f, cy + 0.012f, -0.02f, s * 0.29f, cy - 0.012f, -0.02f, frame, 255);
        }
        quad(vc, mat, -0.03f, cy - 0.02f, zf, 0.03f, cy - 0.02f, zf, 0.03f, cy + 0.005f, zf, -0.03f, cy + 0.005f, zf, frame, 255);

        VertexConsumer glow = vcp.getBuffer(RenderLayer.getLightning());
        if (an == Animation.PULSE || (!dark && an == Animation.NONE)) {
            int a = (int) (45 + 35 * MathHelper.sin(t * 3f * sp));
            for (int s = -1; s <= 1; s += 2)
                quad(glow, mat, s * 0.125f - hw, cy - hh, zf - 0.003f, s * 0.125f + hw, cy - hh, zf - 0.003f,
                    s * 0.125f + hw, cy + hh, zf - 0.003f, s * 0.125f - hw, cy + hh, zf - 0.003f, frame, a);
        }
        if (an == Animation.WAVE) {
            float u = (t * 0.5f * sp) % 1f;
            for (int s = -1; s <= 1; s += 2) {
                float xs = s * 0.125f - hw + u * 2f * hw;
                quad(glow, mat, xs - 0.01f, cy - hh, zf - 0.003f, xs + 0.01f, cy - hh, zf - 0.003f,
                    xs - 0.02f, cy + hh, zf - 0.003f, xs - 0.04f, cy + hh, zf - 0.003f, 0xFFFFFF, 130);
            }
        }
        if (an == Animation.SCAN) {   // a scanner line sweeping across both lenses
            float u = 0.5f + 0.5f * MathHelper.sin(t * 2.4f * sp);
            float xs = -0.22f + 0.44f * u;
            int col = dark ? 0x66FFEE : mix(frame, 0xFFFFFF, 0.5f);
            quad(glow, mat, xs - 0.006f, cy - hh - 0.012f, zf - 0.004f, xs + 0.006f, cy - hh - 0.012f, zf - 0.004f,
                xs + 0.006f, cy + hh + 0.012f, zf - 0.004f, xs - 0.006f, cy + hh + 0.012f, zf - 0.004f, col, 200);
            quad(glow, mat, xs - 0.025f, cy - hh - 0.012f, zf - 0.004f, xs + 0.025f, cy - hh - 0.012f, zf - 0.004f,
                xs + 0.025f, cy + hh + 0.012f, zf - 0.004f, xs - 0.025f, cy + hh + 0.012f, zf - 0.004f, col, 60);
        }
    }

    // ===================================================================== WINGS
    private void wings(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t, float lf) {
        float sp = c.speed;
        Animation an = c.animation;
        float baseAngle = switch (an) {
            case FLOAT -> 55f;
            case NONE -> 12f;
            case BURST -> 30f;
            default -> 35f;
        };
        float amp = switch (an) {
            case NONE -> 0f;
            case FLOAT -> 6f;
            case WAVE -> 26f;
            case BURST -> 8f + Impulse.value * 45f;
            case PULSE -> 10f;
            case RAINBOW -> 18f;
            default -> 14f;
        };
        float rate = an == Animation.BURST ? 2.4f + Impulse.value * 3f : 3.2f;
        float flap = MathHelper.sin(t * rate * sp) * amp;
        final int n = 8;
        boolean angel = !c.custom && c.activeVariant.equals("angel");

        for (int side = -1; side <= 1; side += 2) {
            m.push();
            m.translate(side * 0.09f, 0.08f, 0.17f);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * (baseAngle + flap)));
            Matrix4f mat = m.peek().getPositionMatrix();
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
            for (int layer = 0; layer < 2; layer++) {
                float lenScale = layer == 0 ? 1f : 0.55f;
                float zOff = layer == 0 ? 0.004f : 0f;
                for (int k = 0; k < n; k++) {
                    float theta = (float) Math.toRadians(-62f + 90f * k / (n - 1));
                    float len = (0.45f + 0.35f * MathHelper.sin((float) Math.PI * (k + 0.3f) / (n + 0.2f))) * lenScale;
                    float dxx = side * MathHelper.cos(theta), dyy = MathHelper.sin(theta);
                    float px = -dyy, py = dxx;
                    int base = color(c, t, k / (float) n);
                    int rootC = shade(base, (layer == 0 ? 0.5f : 0.75f) * lf);
                    int tipC = angel ? mix(base, 0xFFFFFF, 0.3f) : shade(base, 1.0f * lf);
                    tipC = shade(tipC, layer == 0 ? 1f : 0.9f);
                    float wr = 0.03f, wm = 0.075f, wt = 0.012f, mid = 0.6f;
                    quadC(vc, mat,
                        px * wr, py * wr, zOff, -px * wr, -py * wr, zOff,
                        dxx * len * mid - px * wm, dyy * len * mid - py * wm, zOff, dxx * len * mid + px * wm, dyy * len * mid + py * wm, zOff,
                        rootC, rootC, tipC, tipC, 255, 255, 255, 255);
                    quadC(vc, mat,
                        dxx * len * mid + px * wm, dyy * len * mid + py * wm, zOff, dxx * len * mid - px * wm, dyy * len * mid - py * wm, zOff,
                        dxx * len - px * wt, dyy * len - py * wt, zOff, dxx * len + px * wt, dyy * len + py * wt, zOff,
                        tipC, tipC, tipC, tipC, 255, 255, 255, 255);
                }
            }
            VertexConsumer glow = vcp.getBuffer(RenderLayer.getLightning());
            if (an == Animation.PULSE || an == Animation.RAINBOW || an == Animation.WAVE || an == Animation.BURST) {
                for (int k = 0; k < n; k++) {
                    float theta = (float) Math.toRadians(-62f + 90f * k / (n - 1));
                    float len = 0.45f + 0.35f * MathHelper.sin((float) Math.PI * (k + 0.3f) / (n + 0.2f));
                    float tx = side * MathHelper.cos(theta) * len, ty = MathHelper.sin(theta) * len;
                    int a = (int) (80 + 60 * MathHelper.sin(t * 4f * sp + k) + Impulse.value * 80);
                    int col = color(c, t, k / (float) n);
                    quad(glow, mat, tx - 0.03f, ty, 0f, tx, ty - 0.03f, 0f, tx + 0.03f, ty, 0f, tx, ty + 0.03f, 0f, col, a);
                }
            }
            m.pop();
        }
    }

    // ===================================================================== AURA
    private void aura(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t) {
        float sp = c.speed;
        Animation an = c.animation;
        Matrix4f mat = m.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        final int n = 20;
        float breathe = an == Animation.PULSE ? 40f + 30f * MathHelper.sin(t * 2.4f * sp) : 20f + 12f * MathHelper.sin(t * 2f * sp);
        float shellA = breathe + Impulse.value * 40f;
        float r = 0.47f;
        if (an != Animation.BURST) {
            for (int i = 0; i < n; i++) {
                float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
                int col = color(c, t, i / (float) n);
                quadC(vc, mat,
                    r * MathHelper.cos(a0), -0.62f, r * MathHelper.sin(a0), r * MathHelper.cos(a1), -0.62f, r * MathHelper.sin(a1),
                    r * MathHelper.cos(a1), 1.52f, r * MathHelper.sin(a1), r * MathHelper.cos(a0), 1.52f, r * MathHelper.sin(a0),
                    col, col, col, col, 0, 0, (int) shellA, (int) shellA);
            }
        }
        switch (an) {
            case SWIRL -> {
                for (int s = 0; s < 3; s++) {
                    final int seg = 26;
                    for (int j = 0; j < seg; j++) {
                        float y0 = 1.5f - j * 2.1f / seg, y1 = 1.5f - (j + 1) * 2.1f / seg;
                        float a0 = t * 1.8f * sp + s * TAU / 3f + y0 * 3.4f, a1 = t * 1.8f * sp + s * TAU / 3f + y1 * 3.4f;
                        float r0 = 0.46f + 0.05f * MathHelper.sin(a0 * 2f), r1 = 0.46f + 0.05f * MathHelper.sin(a1 * 2f);
                        int col = color(c, t, j / (float) seg);
                        int al0 = (int) (150 * (1f - j / (float) seg) * 0.9f + 20), al1 = (int) (150 * (1f - (j + 1) / (float) seg) * 0.9f + 20);
                        quadC(vc, mat,
                            r0 * MathHelper.cos(a0), y0, r0 * MathHelper.sin(a0), r0 * MathHelper.cos(a0), y0 + 0.07f, r0 * MathHelper.sin(a0),
                            r1 * MathHelper.cos(a1), y1 + 0.07f, r1 * MathHelper.sin(a1), r1 * MathHelper.cos(a1), y1, r1 * MathHelper.sin(a1),
                            col, col, col, col, al0, al0, al1, al1);
                    }
                }
            }
            case BURST -> {
                float phase = (t * 0.8f * sp) % 1f;
                float rad = 0.4f + phase * 1.5f + Impulse.value * 0.3f;
                int a = (int) (150 * (1f - phase));
                int col = color(c, t, phase);
                for (int i = 0; i < n; i++) {
                    float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
                    quadC(vc, mat,
                        rad * MathHelper.cos(a0), -0.4f, rad * MathHelper.sin(a0), rad * MathHelper.cos(a1), -0.4f, rad * MathHelper.sin(a1),
                        rad * MathHelper.cos(a1), 1.5f, rad * MathHelper.sin(a1), rad * MathHelper.cos(a0), 1.5f, rad * MathHelper.sin(a0),
                        col, col, col, col, 0, 0, a, a);
                }
            }
            case PULSE -> { }
            default -> {   // RISE and RAINBOW: rings rising along the body
                for (int k = 0; k < 5; k++) {
                    float phase = (t * 0.35f * sp + k / 5f) % 1f;
                    float y = 1.5f - phase * 2.2f;
                    float rad = 0.40f + 0.10f * MathHelper.sin(phase * TAU + k);
                    int a = (int) (150 * MathHelper.sin((float) Math.PI * phase));
                    int col = color(c, t, k / 5f);
                    for (int i = 0; i < n; i++) {
                        float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
                        quad(vc, mat,
                            rad * MathHelper.cos(a0), y, rad * MathHelper.sin(a0), rad * MathHelper.cos(a1), y, rad * MathHelper.sin(a1),
                            rad * MathHelper.cos(a1), y + 0.05f, rad * MathHelper.sin(a1), rad * MathHelper.cos(a0), y + 0.05f, rad * MathHelper.sin(a0), col, a);
                    }
                }
            }
        }
        float dr = 0.55f * (0.9f + 0.1f * MathHelper.sin(t * 3f * sp)) * (an == Animation.PULSE ? 1.5f : 1f);
        int gcol = color(c, t, 0f);
        for (int i = 0; i < n; i++) {
            float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            quadC(vc, mat, 0f, 1.49f, 0f, dr * MathHelper.cos(a0), 1.49f, dr * MathHelper.sin(a0),
                dr * MathHelper.cos(a1), 1.49f, dr * MathHelper.sin(a1), 0f, 1.49f, 0f,
                gcol, gcol, gcol, gcol, 70, 0, 0, 70);
        }
    }

    // ===================================================================== CAPE
    private static final class CapeGeo {
        final int rows, cols;
        final float w, thick, t, sp, flutter, billow;
        final float[] px, py, pz, ny, nz;

        CapeGeo(int rows, int cols, float w, float thick, float t, float sp, float flutter, float billow,
                float[] px, float[] py, float[] pz, float[] ny, float[] nz) {
            this.rows = rows; this.cols = cols; this.w = w; this.thick = thick; this.t = t; this.sp = sp;
            this.flutter = flutter; this.billow = billow;
            this.px = px; this.py = py; this.pz = pz; this.ny = ny; this.nz = nz;
        }

        float[] p(int i, int col, int surface) {
            float u = col / (float) cols, f = i / (float) rows;
            float rip = MathHelper.sin(t * 3.2f * sp - i * 0.45f + col * 0.55f) * flutter * f;
            if (billow > 0f) rip += MathHelper.sin(t * 1.7f * sp - i * 0.3f + col * 0.15f) * 0.07f * billow * f * f;
            return new float[]{-w / 2f + w * u + px[i], py[i] + ny[i] * surface * thick, pz[i] + nz[i] * surface * thick + rip};
        }
    }

    private void cape(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t, int light, float lf,
                      float fwd, float lat, float vy) {
        CapeSim.update(fwd, lat, vy, t);
        Animation an = c.animation;
        final int rows = CapeSim.ROWS, cols = 12;
        final float hgt = 1.05f, seg = hgt / rows;
        float[] py = new float[rows + 1], pz = new float[rows + 1], px = new float[rows + 1];
        float[] ny = new float[rows + 1], nz = new float[rows + 1];
        pz[0] = 0.19f;
        for (int i = 0; i < rows; i++) {
            float a = (float) Math.toRadians(CapeSim.ang[i]);
            py[i + 1] = py[i] + seg * MathHelper.cos(a);
            pz[i + 1] = pz[i] + seg * MathHelper.sin(a);
        }
        for (int i = 0; i <= rows; i++) {
            px[i] = CapeSim.sway[i];
            float a = (float) Math.toRadians(CapeSim.ang[Math.min(i, rows - 1)]);
            ny[i] = -MathHelper.sin(a);
            nz[i] = MathHelper.cos(a);
        }
        float flutter = an == Animation.NONE ? 0.004f : 0.012f + Math.min(0.05f, Math.abs(fwd) * 0.25f) + Impulse.value * 0.04f;
        if (an == Animation.BILLOW) flutter *= 2.2f;
        float billow = an == Animation.BILLOW ? 1f : 0f;
        CapeGeo g = new CapeGeo(rows, cols, 0.64f, 0.045f, t, c.speed, flutter, billow, px, py, pz, ny, nz);
        Matrix4f mat = m.peek().getPositionMatrix();
        boolean night = !c.custom && isNight(c.activeVariant);
        int starMod = an == Animation.TWINKLE ? 6 : 11;
        float tw = an == Animation.TWINKLE ? 2.2f : 1f;

        if (c.texture != null) {
            VertexConsumer tex = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(c.texture));
            int br = shade(0xFFFFFF, lf);
            for (int i = 0; i < rows; i++) {
                for (int cc = 0; cc < cols; cc++) {
                    float u0 = cc / (float) cols, u1 = (cc + 1) / (float) cols, v0 = i / (float) rows, v1 = (i + 1) / (float) rows;
                    tv(tex, mat, g.p(i, cc, 0), u0, v0, light, br);
                    tv(tex, mat, g.p(i, cc + 1, 0), u1, v0, light, br);
                    tv(tex, mat, g.p(i + 1, cc + 1, 0), u1, v1, light, br);
                    tv(tex, mat, g.p(i + 1, cc, 0), u0, v1, light, br);
                    tv(tex, mat, g.p(i, cc, 1), 1f - u0, v0, light, br);
                    tv(tex, mat, g.p(i + 1, cc, 1), 1f - u0, v1, light, br);
                    tv(tex, mat, g.p(i + 1, cc + 1, 1), 1f - u1, v1, light, br);
                    tv(tex, mat, g.p(i, cc + 1, 1), 1f - u1, v0, light, br);
                }
            }
        }

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
        if (c.texture == null) {
            for (int surface = 0; surface < 2; surface++) {
                for (int i = 0; i < rows; i++) {
                    for (int cc = 0; cc < cols; cc++) {
                        float u = cc / (float) cols, v = i / (float) rows;
                        int col;
                        if (night) {
                            col = shade(night(c.activeVariant, u, v, t, hash(i, cc, surface), starMod, tw), lf);
                        } else {
                            int base = an == Animation.RAINBOW ? Palette.rainbow(t * 0.2f * c.speed + v * 0.6f, 0.75f, 1f) : Palette.rgb(c);
                            col = shade(base, (1f - 0.4f * v) * (cc % 2 == 0 ? 1f : 0.93f) * lf);
                        }
                        if (surface == 1) col = shade(col, 0.85f);
                        float[] a = g.p(i, cc, surface), b = g.p(i, cc + 1, surface), d = g.p(i + 1, cc + 1, surface), e = g.p(i + 1, cc, surface);
                        quad(vc, mat, a[0], a[1], a[2], b[0], b[1], b[2], d[0], d[1], d[2], e[0], e[1], e[2], col, 255);
                    }
                }
            }
        }
        int edge = shade(Palette.rgb(c), 0.55f * lf);
        for (int i = 0; i < rows; i++) {
            float[] a = g.p(i, 0, 0), b = g.p(i + 1, 0, 0), d = g.p(i + 1, 0, 1), e = g.p(i, 0, 1);
            quad(vc, mat, a[0], a[1], a[2], b[0], b[1], b[2], d[0], d[1], d[2], e[0], e[1], e[2], edge, 255);
            a = g.p(i, cols, 0); b = g.p(i + 1, cols, 0); d = g.p(i + 1, cols, 1); e = g.p(i, cols, 1);
            quad(vc, mat, a[0], a[1], a[2], b[0], b[1], b[2], d[0], d[1], d[2], e[0], e[1], e[2], edge, 255);
        }
        for (int cc = 0; cc < cols; cc++) {
            float[] a = g.p(rows, cc, 0), b = g.p(rows, cc + 1, 0), d = g.p(rows, cc + 1, 1), e = g.p(rows, cc, 1);
            quad(vc, mat, a[0], a[1], a[2], b[0], b[1], b[2], d[0], d[1], d[2], e[0], e[1], e[2], edge, 255);
        }
    }

    // ===================================================================== TRAIL (smooth glowing ribbon)
    private static int trailColor(String variant, float age, float t) {
        switch (variant) {
            case "rainbow": return Palette.rainbow(t * 0.35f - age * 0.9f, 0.7f, 1f);
            case "fire": return age < 0.35f ? mix(0xFFF2A0, 0xFF9020, age / 0.35f) : mix(0xFF9020, 0xD01810, (age - 0.35f) / 0.65f);
            case "ice": return age < 0.4f ? mix(0xFFFFFF, 0x7FE4FF, age / 0.4f) : mix(0x7FE4FF, 0x2E6BFF, (age - 0.4f) / 0.6f);
            case "stardust": return mix(0xFFF6C8, 0xFFB347, age);
            case "soul": return mix(0x9FFFFF, 0x1B6FB0, age);
            default: return mix(0xD08CFF, 0x4F7CFF, age);
        }
    }

    private static float[] toModel(double wx, double wy, double wz, double fx, double fy, double fz, float cs, float sn) {
        double dx = wx - fx, dy = wy - fy, dz = wz - fz;
        double ux = dx * cs - dz * sn, uz = dx * sn + dz * cs;
        return new float[]{(float) (-ux / 0.9375), (float) (-dy / 0.9375) + 1.501f, (float) (uz / 0.9375)};
    }

    private void trail(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t, PlayerEntityRenderState state) {
        Animation an = c.animation;
        double cx = state.x, cy = state.y + 0.95, cz = state.z;
        TrailSim.add(cx, cy, cz, t);
        List<double[]> pts = TrailSim.smoothed(cx, cy, cz, t);
        int n = pts.size();
        if (n < 2) return;
        double phi = Math.toRadians(180.0 - state.bodyYaw);
        float cs = (float) Math.cos(phi), sn = (float) Math.sin(phi);
        Matrix4f mat = m.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());

        for (int layer = 0; layer < 2; layer++) {
            float scale = layer == 0 ? 2.4f : 1f;
            float alphaMul = layer == 0 ? 0.20f : 0.85f;
            float[][] top = new float[n][], bot = new float[n][], lef = new float[n][], rig = new float[n][];
            int[] col = new int[n], al = new int[n];
            for (int i = 0; i < n; i++) {
                double[] p = pts.get(i), pr = pts.get(Math.max(i - 1, 0)), nx = pts.get(Math.min(i + 1, n - 1));
                double tx = nx[0] - pr[0], tz = nx[2] - pr[2], len = Math.sqrt(tx * tx + tz * tz);
                double sx = len < 1e-5 ? 1 : -tz / len, sz = len < 1e-5 ? 0 : tx / len;
                float age = (float) p[3];
                double wave = an == Animation.WAVE ? Math.sin(age * 14.0 - t * 6.0) * 0.10 * Math.min(1.0, age * 3.0) : 0.0;
                double px = p[0] + sx * wave, pz = p[2] + sz * wave;
                float half = (0.012f + 0.13f * (float) Math.pow(1.0 - age, 0.8)) * scale;
                if (an == Animation.COMET) half *= age < 0.12f ? 1.9f - age * 5f : 0.75f;
                top[i] = toModel(px, p[1] + half, pz, cx, state.y, cz, cs, sn);
                bot[i] = toModel(px, p[1] - half, pz, cx, state.y, cz, cs, sn);
                lef[i] = toModel(px + sx * half * 1.2, p[1], pz + sz * half * 1.2, cx, state.y, cz, cs, sn);
                rig[i] = toModel(px - sx * half * 1.2, p[1], pz - sz * half * 1.2, cx, state.y, cz, cs, sn);
                col[i] = trailColor(c.activeVariant, age, t);
                al[i] = (int) (255 * Math.pow(1.0 - age, an == Animation.COMET ? 2.2 : 1.3) * alphaMul);
            }
            for (int i = 0; i < n - 1; i++) {
                int j = i + 1;
                quadC(vc, mat, top[i][0], top[i][1], top[i][2], bot[i][0], bot[i][1], bot[i][2],
                    bot[j][0], bot[j][1], bot[j][2], top[j][0], top[j][1], top[j][2],
                    col[i], col[i], col[j], col[j], al[i], al[i], al[j], al[j]);
                quadC(vc, mat, lef[i][0], lef[i][1], lef[i][2], rig[i][0], rig[i][1], rig[i][2],
                    rig[j][0], rig[j][1], rig[j][2], lef[j][0], lef[j][1], lef[j][2],
                    col[i], col[i], col[j], col[j], al[i], al[i], al[j], al[j]);
            }
        }
    }
}
