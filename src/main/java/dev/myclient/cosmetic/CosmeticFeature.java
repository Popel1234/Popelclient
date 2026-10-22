package dev.myclient.cosmetic;

import dev.myclient.MyClient;
import dev.myclient.cosmetic.Cosmetic.Animation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Draws equipped cosmetics procedurally (no textures needed) on YOUR player.
 * Model space note: +Y points DOWN, +Z is the back of the player, head top is at y = -0.5.
 * Look at yourself in third person (F5) to see them.
 */
public class CosmeticFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    public CosmeticFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light,
                       PlayerEntityRenderState state, float a, float b) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // only the local player gets cosmetics
        double dx = state.x - mc.player.getX();
        double dy = state.y - mc.player.getY();
        double dz = state.z - mc.player.getZ();
        if (dx * dx + dy * dy + dz * dz > 1.0) return;

        float t = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);
        float lf = 0.4f + 0.6f * Math.max((light >> 4) & 15, (light >> 20) & 15) / 15f;
        float speed = (float) mc.player.getVelocity().horizontalLength();

        for (Cosmetic c : MyClient.COSMETICS.catalog) {
            if (!MyClient.COSMETICS.isEquipped(c)) continue;
            m.push();
            switch (c.type) {
                case HALO -> { getContextModel().head.rotate(m); halo(m, vcp, c, t); }
                case HEADBAND -> { getContextModel().head.rotate(m); band(m, vcp, c, t, lf); }
                case CAPE -> { getContextModel().body.rotate(m); cape(m, vcp, c, t, lf, speed); }
            }
            m.pop();
        }
    }

    // ---------- colours ----------
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

    // ---------- geometry helpers (each quad is written twice so it is visible from both sides) ----------
    private static void vtx(VertexConsumer vc, Matrix4f mat, float x, float y, float z, int rgb, int al) {
        vc.vertex(mat, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, al);
    }

    private static void quad(VertexConsumer vc, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int rgb, int al) {
        vtx(vc, mat, x1, y1, z1, rgb, al); vtx(vc, mat, x2, y2, z2, rgb, al);
        vtx(vc, mat, x3, y3, z3, rgb, al); vtx(vc, mat, x4, y4, z4, rgb, al);
        vtx(vc, mat, x4, y4, z4, rgb, al); vtx(vc, mat, x3, y3, z3, rgb, al);
        vtx(vc, mat, x2, y2, z2, rgb, al); vtx(vc, mat, x1, y1, z1, rgb, al);
    }

    // ---------- HALO ----------
    private void halo(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t) {
        float sp = c.speed;
        float bob = c.animation == Animation.FLOAT ? MathHelper.sin(t * 2f * sp) * 0.035f : 0f;
        float pulse = c.animation == Animation.PULSE ? 1f + 0.12f * MathHelper.sin(t * 4f * sp) : 1f;
        float wob = c.animation == Animation.WAVE ? 0.03f : 0f;
        float y = -0.74f + bob;
        float ri = 0.19f * pulse, ro = 0.27f * pulse;
        final int n = 32;
        Matrix4f mat = m.peek().getPositionMatrix();

        VertexConsumer solid = vcp.getBuffer(RenderLayer.getDebugQuads());
        for (int i = 0; i < n; i++) {
            float a0 = (float) (i * 2 * Math.PI / n), a1 = (float) ((i + 1) * 2 * Math.PI / n);
            float y0 = y + wob * MathHelper.sin(a0 * 3f + t * 3f * sp);
            float y1 = y + wob * MathHelper.sin(a1 * 3f + t * 3f * sp);
            float f = c.animation == Animation.SPIN ? 0.35f + 0.65f * (0.5f + 0.5f * MathHelper.cos(a0 - t * 4f * sp)) : 1f;
            int col = shade(color(c, t, i / (float) n), f);
            quad(solid, mat,
                ri * MathHelper.cos(a0), y0, ri * MathHelper.sin(a0),
                ro * MathHelper.cos(a0), y0, ro * MathHelper.sin(a0),
                ro * MathHelper.cos(a1), y1, ro * MathHelper.sin(a1),
                ri * MathHelper.cos(a1), y1, ri * MathHelper.sin(a1), col, 255);
        }

        VertexConsumer glow = vcp.getBuffer(RenderLayer.getLightning());
        float gi = ri - 0.035f, go = ro + 0.05f;
        for (int i = 0; i < n; i++) {
            float a0 = (float) (i * 2 * Math.PI / n), a1 = (float) ((i + 1) * 2 * Math.PI / n);
            float y0 = y + wob * MathHelper.sin(a0 * 3f + t * 3f * sp);
            float y1 = y + wob * MathHelper.sin(a1 * 3f + t * 3f * sp);
            float f = c.animation == Animation.SPIN ? 0.2f + 0.8f * (0.5f + 0.5f * MathHelper.cos(a0 - t * 4f * sp)) : 1f;
            int col = shade(color(c, t, i / (float) n), f);
            quad(glow, mat,
                gi * MathHelper.cos(a0), y0, gi * MathHelper.sin(a0),
                go * MathHelper.cos(a0), y0, go * MathHelper.sin(a0),
                go * MathHelper.cos(a1), y1, go * MathHelper.sin(a1),
                gi * MathHelper.cos(a1), y1, gi * MathHelper.sin(a1), col, 70);
        }
    }

    // ---------- HEADBAND ----------
    private void band(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t, float lf) {
        float sp = c.speed;
        float p = c.animation == Animation.PULSE ? 0.6f + 0.4f * MathHelper.sin(t * 4f * sp) : 1f;
        float h = 0.27f, y0 = -0.33f, y1 = -0.22f;
        Matrix4f mat = m.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());

        int c0 = shade(color(c, t, 0f), p * lf), c1 = shade(color(c, t, 0.25f), p * lf);
        int c2 = shade(color(c, t, 0.5f), p * lf), c3 = shade(color(c, t, 0.75f), p * lf);
        quad(vc, mat, -h, y0, -h, h, y0, -h, h, y1, -h, -h, y1, -h, c0, 255); // front
        quad(vc, mat, h, y0, -h, h, y0, h, h, y1, h, h, y1, -h, c1, 255);     // right
        quad(vc, mat, h, y0, h, -h, y0, h, -h, y1, h, h, y1, h, c2, 255);     // back
        quad(vc, mat, -h, y0, h, -h, y0, -h, -h, y1, -h, -h, y1, h, c3, 255); // left

        // two tails at the back
        float flap = (c.animation == Animation.NONE || c.animation == Animation.PULSE) ? 0.02f : 0.05f;
        for (int k = 0; k < 2; k++) {
            float xa = k == 0 ? -0.07f : 0.02f, xb = xa + 0.05f;
            float off = 0.05f + flap * MathHelper.sin(t * 3f * sp + k * 1.7f);
            int tc = shade(color(c, t, 0.3f + k * 0.2f), p * lf);
            quad(vc, mat, xa, y0 + 0.01f, h + 0.002f, xb, y0 + 0.01f, h + 0.002f,
                xb + off * 0.3f, y0 + 0.36f, h + off, xa + off * 0.3f, y0 + 0.36f, h + off, tc, 255);
        }
    }

    // ---------- CAPE ----------
    private void cape(MatrixStack m, VertexConsumerProvider vcp, Cosmetic c, float t, float lf, float moveSpeed) {
        float sp = c.speed;
        final int cols = 8, rows = 14;
        final float w = 0.625f, hgt = 1.0f;
        float amp = 0.025f + Math.min(0.12f, moveSpeed * 0.6f);
        Matrix4f mat = m.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());

        for (int r = 0; r < rows; r++) {
            for (int cc = 0; cc < cols; cc++) {
                float xa = -w / 2 + w * cc / cols, xb = -w / 2 + w * (cc + 1) / cols;
                float ya = hgt * r / rows, yb = hgt * (r + 1) / rows;
                float za = capeZ(c, t, r, cc, rows, moveSpeed, amp, sp);
                float zb = capeZ(c, t, r, cc + 1, rows, moveSpeed, amp, sp);
                float zc = capeZ(c, t, r + 1, cc + 1, rows, moveSpeed, amp, sp);
                float zd = capeZ(c, t, r + 1, cc, rows, moveSpeed, amp, sp);
                float f = r / (float) rows;
                int base = c.animation == Animation.RAINBOW ? Palette.rainbow(t * 0.2f * sp + f * 0.6f, 0.75f, 1f) : Palette.rgb(c);
                int col = shade(base, (1f - 0.4f * f) * (cc % 2 == 0 ? 1f : 0.92f) * lf);
                quad(vc, mat, xa, ya, za, xb, ya, zb, xb, yb, zc, xa, yb, zd, col, 255);
            }
        }
    }

    private static float capeZ(Cosmetic c, float t, int r, int cc, int rows, float moveSpeed, float amp, float sp) {
        float f = r / (float) rows;
        float z = 0.14f + f * (0.02f + moveSpeed * 0.9f);
        if (c.animation == Animation.WAVE || c.animation == Animation.RAINBOW)
            z += MathHelper.sin(t * 3f * sp - r * 0.55f + cc * 0.2f) * amp * f;
        else if (c.animation == Animation.FLOAT)
            z += MathHelper.sin(t * 1.5f * sp - r * 0.3f) * amp * 0.5f * f;
        return z;
    }
}
