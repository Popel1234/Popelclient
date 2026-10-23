package dev.myclient.gui;

import dev.myclient.MyClient;
import dev.myclient.module.Tracers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Draws the tracer lines on the HUD by projecting block positions to the screen. */
public final class TracerRenderer {
    private TracerRenderer() {}

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Tracers tr = MyClient.MODULES.tracers;
        if (!tr.isEnabled() || tr.found.isEmpty() || mc.player == null || mc.world == null || mc.options.hudHidden) return;
        Camera cam = mc.gameRenderer.getCamera();
        if (cam == null) return;
        Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        double tan = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0), aspect = w / (double) h;
        float s = Math.max(2f, Math.min(4f, (float) mc.getWindow().getScaleFactor()));

        Draw.begin(ctx, s);
        int shown = 0;
        for (Tracers.Target t : tr.found) {
            if (shown++ > 200) break;
            double vx = t.pos.getX() + 0.5 - cp.x, vy = t.pos.getY() + 0.5 - cp.y, vz = t.pos.getZ() + 0.5 - cp.z;
            double xc = vx * rx + vy * ry + vz * rz, yc = vx * ux + vy * uy + vz * uz, zc = vx * fx + vy * fy + vz * fz;
            float sx, sy;
            if (zc > 0.05) {
                sx = (float) ((1 + xc / (zc * tan * aspect)) / 2 * w);
                sy = (float) ((1 - yc / (zc * tan)) / 2 * h);
            } else {
                double len = Math.sqrt(xc * xc + yc * yc) + 1e-6;
                float rad = Math.min(w, h) * 0.45f;
                sx = (float) (w / 2.0 + xc / len * rad);
                sy = (float) (h / 2.0 - yc / len * rad);
            }
            sx = Math.max(4, Math.min(w - 4, sx));
            sy = Math.max(4, Math.min(h - 4, sy));
            double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
            int a = (int) Math.max(90, Math.min(230, 230 - 140 * dist / Math.max(8, tr.radius)));
            int col = (a << 24) | (t.color & 0xFFFFFF);
            Draw.line(ctx, w / 2f, h / 2f, sx, sy, 0.7f, col);
            Draw.circle(ctx, sx, sy, 2.6f, col);
            if (shown <= 8) Ui.text(ctx, t.name + " " + (int) dist + "m", sx + 5, sy - 4, 0xFFFFFFFF);
        }
        Draw.end(ctx);
    }
}
