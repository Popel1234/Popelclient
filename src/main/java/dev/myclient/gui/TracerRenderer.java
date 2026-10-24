package dev.myclient.gui;

import dev.myclient.MyClient;
import dev.myclient.Settings;
import dev.myclient.fx.WorldFx;
import dev.myclient.module.Tracers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** HUD overlay: tracer lines, X-Ray boxes and the red damage flash. */
public final class TracerRenderer {
    private TracerRenderer() {}

    private static double cpx, cpy, cpz, rx, ry, rz, ux, uy, uz, fx, fy, fz, tan, aspect;
    private static int w, h;

    private static boolean project(double wx, double wy, double wz, float[] out) {
        double vx = wx - cpx, vy = wy - cpy, vz = wz - cpz;
        double xc = vx * rx + vy * ry + vz * rz, yc = vx * ux + vy * uy + vz * uz, zc = vx * fx + vy * fy + vz * fz;
        if (zc <= 0.05) return false;
        out[0] = (float) ((1 + xc / (zc * tan * aspect)) / 2 * w);
        out[1] = (float) ((1 - yc / (zc * tan)) / 2 * h);
        return true;
    }

    private static final int[][] EDGES = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        w = mc.getWindow().getScaledWidth();
        h = mc.getWindow().getScaledHeight();
        float s = Math.max(2f, Math.min(4f, (float) mc.getWindow().getScaleFactor()));

        if (Settings.hurtFlash && WorldFx.hurt > 0.01f) {
            int a = (int) (150 * Math.min(1f, WorldFx.hurt));
            ctx.fillGradient(0, 0, w, (int) (h * 0.3f), (a << 24) | 0xD01030, 0x00D01030);
            ctx.fillGradient(0, (int) (h * 0.7f), w, h, 0x00D01030, (a << 24) | 0xD01030);
            ctx.fill(0, 0, w, h, ((a / 5) << 24) | 0xD01030);
        }

        Tracers tr = MyClient.MODULES.tracers;
        boolean lines = tr.isEnabled(), boxes = MyClient.MODULES.xray.isEnabled();
        if ((!lines && !boxes) || tr.found.isEmpty()) return;
        Camera cam = mc.gameRenderer.getCamera();
        if (cam == null) return;
        Vec3d cp = cam.getPos();
        cpx = cp.x; cpy = cp.y; cpz = cp.z;
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        fx = -Math.sin(yaw) * Math.cos(pitch); fy = -Math.sin(pitch); fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw); ry = 0; rz = -Math.sin(yaw);
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx;
        tan = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        aspect = w / (double) h;

        Draw.begin(ctx, s, 1f);
        int shown = 0, boxCount = 0;
        float[] a = new float[2], b = new float[2];
        float[][] corners = new float[8][2];
        for (Tracers.Target t : tr.found) {
            if (shown++ > 400) break;
            double vx = t.pos.getX() + 0.5 - cpx, vy = t.pos.getY() + 0.5 - cpy, vz = t.pos.getZ() + 0.5 - cpz;
            double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
            int al = (int) Math.max(110, Math.min(240, 240 - 130 * dist / Math.max(16, tr.radius)));
            int col = (al << 24) | (t.color & 0xFFFFFF);
            if (lines) {
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
                Draw.line(ctx, w / 2f, h / 2f, sx, sy, 0.7f, col);
                Draw.circle(ctx, sx, sy, 2.6f, col);
                if (shown <= 6) Ui.text(ctx, t.name + " " + (int) dist + "m", sx + 5, sy - 4, 0xFFFFFFFF);
            }
            if (boxes && boxCount < 160) {
                boolean ok = true;
                for (int i = 0; i < 8 && ok; i++) {
                    ok = project(t.pos.getX() + (i & 1), t.pos.getY() + ((i >> 1) & 1), t.pos.getZ() + ((i >> 2) & 1), a);
                    corners[i][0] = a[0]; corners[i][1] = a[1];
                }
                if (!ok) continue;
                boxCount++;
                for (int[] e : EDGES) Draw.line(ctx, corners[e[0]][0], corners[e[0]][1], corners[e[1]][0], corners[e[1]][1], 0.6f, col);
            }
        }
        Draw.end(ctx);
    }
}
