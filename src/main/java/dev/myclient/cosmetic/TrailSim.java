package dev.myclient.cosmetic;

import java.util.ArrayList;
import java.util.List;

/** Remembers where the player was recently and smooths it into a flowing ribbon path. */
final class TrailSim {
    private TrailSim() {}

    static final float LIFE = 1.3f;
    private static final List<double[]> pts = new ArrayList<>();   // x, y, z, time

    static void add(double x, double y, double z, float t) {
        while (!pts.isEmpty() && t - pts.get(0)[3] > LIFE) pts.remove(0);
        if (!pts.isEmpty()) {
            double[] l = pts.get(pts.size() - 1);
            double dx = x - l[0], dy = y - l[1], dz = z - l[2];
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 64.0 || t < l[3]) { pts.clear(); }
            else if (d2 < 0.01) return;
        }
        pts.add(new double[]{x, y, z, t});
    }

    /** Returns smoothed points {x, y, z, age 0..1}, oldest first, ending at the current position. */
    static List<double[]> smoothed(double cx, double cy, double cz, float t) {
        List<double[]> ctrl = new ArrayList<>(pts);
        ctrl.add(new double[]{cx, cy, cz, t});
        List<double[]> out = new ArrayList<>();
        int n = ctrl.size();
        if (n < 2) return out;
        final int sub = 3;
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = ctrl.get(Math.max(i - 1, 0)), p1 = ctrl.get(i), p2 = ctrl.get(i + 1), p3 = ctrl.get(Math.min(i + 2, n - 1));
            for (int s = 0; s < sub; s++) {
                double k = s / (double) sub, k2 = k * k, k3 = k2 * k;
                double[] r = new double[4];
                for (int a = 0; a < 3; a++) {
                    r[a] = 0.5 * ((2 * p1[a]) + (-p0[a] + p2[a]) * k + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * k2
                        + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * k3);
                }
                double time = p1[3] + (p2[3] - p1[3]) * k;
                r[3] = Math.max(0.0, Math.min(1.0, (t - time) / LIFE));
                out.add(r);
            }
        }
        double[] last = ctrl.get(n - 1);
        out.add(new double[]{last[0], last[1], last[2], 0.0});
        return out;
    }
}
