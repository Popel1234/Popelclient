package dev.myclient.fake;

import dev.myclient.Settings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

/** First-person butterfly knife with moving handles: opening flicks, rollovers, fans, spins and closing. */
public final class KnifeRender {
    private KnifeRender() {}

    private static float ease(float x) { x = Math.max(0f, Math.min(1f, x)); return x * x * (3 - 2 * x); }

    /** returns {handle A angle, blade angle, roll of the whole knife} in degrees */
    static float[] anim(float t, float swing) {
        if (swing > 0f) return new float[]{720f * ease(swing), -40f * (float) Math.sin(swing * Math.PI), 360f * ease(swing)};
        float p = (t % 9f) / 9f;
        float handle = 0f, blade = 0f, roll = (float) Math.sin(t * 1.7) * 6f;
        if (p < 0.10f) { blade = 180f * (1f - ease(p / 0.10f)); }                                   // flick open
        else if (p < 0.40f) { handle = 4f * (float) Math.sin(t * 6); }                              // idle
        else if (p < 0.56f) { handle = 360f * ease((p - 0.40f) / 0.16f); }                         // rollover
        else if (p < 0.72f) { float k = (p - 0.56f) / 0.16f; handle = 150f * (float) Math.sin(k * Math.PI * 4); blade = 25f * (float) Math.sin(k * Math.PI * 4 + 1); } // fan
        else if (p < 0.88f) { roll = 720f * ease((p - 0.72f) / 0.16f); handle = 30f * (float) Math.sin((p - 0.72f) * 40); } // spin
        else { blade = 180f * ease((p - 0.88f) / 0.12f); }                                          // close
        return new float[]{handle, blade, roll};
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, int a) {
        vc.vertex(m, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    private static void face(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, int rgb, int al) {
        v(vc, m, a[0], a[1], a[2], rgb, al); v(vc, m, b[0], b[1], b[2], rgb, al); v(vc, m, c[0], c[1], c[2], rgb, al); v(vc, m, d[0], d[1], d[2], rgb, al);
        v(vc, m, d[0], d[1], d[2], rgb, al); v(vc, m, c[0], c[1], c[2], rgb, al); v(vc, m, b[0], b[1], b[2], rgb, al); v(vc, m, a[0], a[1], a[2], rgb, al);
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static void box(VertexConsumer vc, Matrix4f m, KnifeDesign.Part p, int rgb, float lf, int al) {
        float x1 = p.x1(), y1 = p.y1(), z1 = p.z1(), x2 = p.x2(), y2 = p.y2(), z2 = p.z2();
        face(vc, m, new float[]{x1, y2, z1}, new float[]{x2, y2, z1}, new float[]{x2, y2, z2}, new float[]{x1, y2, z2}, shade(rgb, 1.0f * lf), al);   // top
        face(vc, m, new float[]{x1, y1, z1}, new float[]{x2, y1, z1}, new float[]{x2, y1, z2}, new float[]{x1, y1, z2}, shade(rgb, 0.55f * lf), al);  // bottom
        face(vc, m, new float[]{x1, y1, z2}, new float[]{x2, y1, z2}, new float[]{x2, y2, z2}, new float[]{x1, y2, z2}, shade(rgb, 0.92f * lf), al);  // front
        face(vc, m, new float[]{x1, y1, z1}, new float[]{x2, y1, z1}, new float[]{x2, y2, z1}, new float[]{x1, y2, z1}, shade(rgb, 0.80f * lf), al);  // back
        face(vc, m, new float[]{x1, y1, z1}, new float[]{x1, y1, z2}, new float[]{x1, y2, z2}, new float[]{x1, y2, z1}, shade(rgb, 0.70f * lf), al);  // left
        face(vc, m, new float[]{x2, y1, z1}, new float[]{x2, y1, z2}, new float[]{x2, y2, z2}, new float[]{x2, y2, z1}, shade(rgb, 0.75f * lf), al);  // right
    }

    private static void group(MatrixStack ms, VertexConsumer vc, KnifeDesign.Variant var, List<KnifeDesign.Part> parts, float angle, float lf) {
        ms.push();
        float px = 8.2f, py = 9.0f;
        ms.translate(px, py, 0f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        ms.translate(-px, -py, 0f);
        Matrix4f mat = ms.peek().getPositionMatrix();
        for (KnifeDesign.Part p : parts) box(vc, mat, p, KnifeDesign.color(var, p.tile()), lf, p.tile() == KnifeDesign.INLAY ? 215 : 255);
        ms.pop();
    }

    /** Returns true when the knife was drawn (the normal sword is then skipped). */
    public static boolean drawFirstPerson(LivingEntity entity, ItemStack stack, boolean left, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        if (!Settings.butterfly) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity != mc.player) return false;
        KnifeDesign.Variant var = KnifeDesign.variantFor(Registries.ITEM.getId(stack.getItem()).getPath());
        if (var == null) return false;

        float t = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);
        float[] a = anim(t, entity.getHandSwingProgress(1f));
        float lf = 0.45f + 0.55f * Math.max((light >> 4) & 15, (light >> 20) & 15) / 15f;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());

        ms.push();
        // same placement as the normal item model uses in the first-person hand
        float sg = left ? -1f : 1f;
        float ry = left ? 90f : -90f, rz = left ? -25f : 25f;
        ms.translate(sg * 1.13f / 16f, 3.2f / 16f, 1.13f / 16f);
        ms.multiply(new Quaternionf().rotationXYZ(0f, (float) Math.toRadians(left ? -ry : ry), (float) Math.toRadians(left ? -rz : rz)));
        ms.scale(0.68f, 0.68f, 0.68f);
        ms.translate(-0.5f, -0.5f, -0.5f);
        ms.scale(1f / 16f, 1f / 16f, 1f / 16f);
        ms.translate(8f, 8f, 8f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-45f));
        ms.translate(-8f, -8f, -8f);
        // roll of the whole knife around its handle
        ms.translate(8f, 4.5f, 8f);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a[2]));
        ms.translate(-8f, -4.5f, -8f);

        group(ms, vc, var, KnifeDesign.handle(false), 0f, lf);
        group(ms, vc, var, KnifeDesign.handle(true), a[0], lf);
        group(ms, vc, var, KnifeDesign.blade(var), a[1], lf);
        ms.pop();
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getDebugQuads());
        return true;
    }
}
