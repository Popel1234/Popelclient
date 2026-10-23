package dev.myclient.cosmetic;

/** Tiny spring-chain simulation so capes swing with speed, jumping, falling and sideways movement. */
final class CapeSim {
    private CapeSim() {}

    static final int ROWS = 24;
    /** segment angle in degrees: 0 = hanging straight down, positive = swung backwards */
    static final float[] ang = new float[ROWS];
    static final float[] angV = new float[ROWS];
    /** sideways offset in blocks at each row boundary */
    static final float[] sway = new float[ROWS + 1];
    static final float[] swayV = new float[ROWS + 1];
    private static long last = System.nanoTime();

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    static void update(float fwd, float lat, float vy, float t) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;
        if (dt <= 0f) return;

        float base = clamp(fwd * 200f, -10f, 62f);
        float lift = clamp(-vy * 70f, 0f, 35f);
        float jump = clamp(vy * 40f, 0f, 15f);
        float latTarget = clamp(lat * 1.6f, -0.3f, 0.3f);

        for (int i = 0; i < ROWS; i++) {
            float f = i / (float) ROWS;
            float parent = i == 0 ? base * 0.3f : ang[i - 1];
            float target = (base + lift - jump) * (0.3f + 0.7f * f)
                + 4f * (float) Math.sin(t * 1.3f - f * 3f) * (0.3f + f);
            float acc = (target - ang[i]) * 55f + (parent - ang[i]) * 30f - angV[i] * 7f;
            angV[i] += acc * dt;
            ang[i] = clamp(ang[i] + angV[i] * dt, -25f, 85f);
        }
        for (int i = 1; i <= ROWS; i++) {
            float f = i / (float) ROWS;
            float target = latTarget * f;
            float acc = (target - sway[i]) * 50f + (sway[i - 1] - sway[i]) * 25f - swayV[i] * 6f;
            swayV[i] += acc * dt;
            sway[i] = clamp(sway[i] + swayV[i] * dt, -0.45f, 0.45f);
        }
    }
}
