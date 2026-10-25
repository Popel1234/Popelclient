package dev.myclient.cosmetic;

import dev.myclient.MyClient;
import dev.myclient.Settings;
import dev.myclient.cosmetic.Cosmetic.Animation;
import dev.myclient.fx.WorldFx;
import dev.myclient.mixin.ParticleManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.random.Random;

/** Emits our own smooth particles for halos, auras, trails, jumps and damage (no Minecraft textures). */
public final class Particles {
    private Particles() {}

    private static int tick, lastHurt;
    private static boolean clearBroken;
    private static boolean wasGround = true;

    private static int cosColor(Cosmetic c, float off) {
        if (c.animation == Animation.RAINBOW) return Palette.rainbow(tick * 0.02f * c.speed + off, 0.7f, 1f);
        return Palette.rgb(c);
    }

    private static int accent() {
        for (Cosmetic c : MyClient.COSMETICS.catalog) if (MyClient.COSMETICS.isEquipped(c)) return Palette.rgb(c);
        return 0x9B7BFF;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (Settings.noParticles && !clearBroken) {
            try {
                ((ParticleManagerAccessor) (Object) mc.particleManager).myclient$clearParticles();
            } catch (Throwable t) {
                clearBroken = true;
            }
        }
        if (mc.isPaused()) return;
        tick++;
        ClientPlayerEntity p = mc.player;
        Random r = mc.world.random;

        boolean ground = p.isOnGround();
        if (wasGround && !ground && p.getVelocity().y > 0.2 && Settings.jumpFx) jump(p, r);
        wasGround = ground;

        int h = p.hurtTime;
        if (h > 0 && lastHurt == 0 && Settings.hurtFlash) {
            WorldFx.hurt = 1f;
            WorldFx.burst(p.getX(), p.getY() + 1.0, p.getZ(), 0xFF3050, 14, 2.6f);
            Impulse.kick(1f);
        }
        lastHurt = h;

        double vx = p.getVelocity().x, vz = p.getVelocity().z;
        double speed = Math.sqrt(vx * vx + vz * vz);
        for (Cosmetic c : MyClient.COSMETICS.catalog) {
            if (!MyClient.COSMETICS.isEquipped(c)) continue;
            switch (c.type) {
                case HALO -> halo(p, c, r);
                case AURA -> aura(p, c, r);
                case TRAIL -> { if (speed > 0.04) trail(p, c, r, vx, vz); }
                default -> { }
            }
        }
    }

    private static void jump(ClientPlayerEntity p, Random r) {
        int col = accent();
        WorldFx.ring(p.getX(), p.getY(), p.getZ(), 1.5f, 0.6f, col);
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + r.nextDouble() * 0.3;
            WorldFx.spark(p.getX(), p.getY() + 0.1, p.getZ(), (float) Math.cos(a) * 2.2f, 0.8f + r.nextFloat() * 0.8f, (float) Math.sin(a) * 2.2f,
                0.45f, 0.05f, col, WorldFx.SPARK, 2.5f, 2f);
        }
        Impulse.kick(0.8f);
    }

    private static void halo(ClientPlayerEntity p, Cosmetic c, Random r) {
        boolean celestial = c.id.contains("celestial"), storm = c.id.contains("storm") || c.animation == Animation.CRACKLE;
        double y = p.getY() + (p.isSneaking() ? 1.92 : 2.12);
        int count = celestial ? 2 : 1;
        for (int k = 0; k < count; k++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 0.2 + r.nextDouble() * 0.12;
            double x = p.getX() + Math.cos(ang) * rad, z = p.getZ() + Math.sin(ang) * rad;
            int col = cosColor(c, (float) (ang / (Math.PI * 2)));
            if (storm) {
                for (int s = 0; s < 2; s++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    WorldFx.spark(x, y, z, (float) Math.cos(a) * 1.4f, (r.nextFloat() - 0.2f) * 1.2f, (float) Math.sin(a) * 1.4f,
                        0.22f, 0.035f, 0xE6F4FF, WorldFx.SPARK, 3f, 0f);
                }
            } else if (celestial) {
                double oa = tick * 0.35 + k * Math.PI;
                WorldFx.spark(p.getX() + Math.cos(oa) * 0.3, y, p.getZ() + Math.sin(oa) * 0.3,
                    (float) -Math.sin(oa) * 0.5f, 0.1f, (float) Math.cos(oa) * 0.5f, 0.9f, 0.04f, col, WorldFx.ORB, 1.2f, 0f);
                WorldFx.spark(x, y, z, 0f, 0.25f + r.nextFloat() * 0.2f, 0f, 1.0f, 0.035f, col, WorldFx.STAR, 0.5f, 0f);
            } else {
                WorldFx.spark(x, y, z, (float) Math.cos(ang) * 0.1f, 0.2f + r.nextFloat() * 0.25f, (float) Math.sin(ang) * 0.1f,
                    0.8f + r.nextFloat() * 0.5f, 0.03f + r.nextFloat() * 0.03f, col, WorldFx.STAR, 0.6f, 0f);
            }
        }
    }

    private static void aura(ClientPlayerEntity p, Cosmetic c, Random r) {
        String v = c.activeVariant;
        Animation an = c.animation;
        for (int k = 0; k < 3; k++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 0.5 + r.nextDouble() * 1.9;
            double x = p.getX() + Math.cos(ang) * rad, z = p.getZ() + Math.sin(ang) * rad;
            double y = p.getY() + r.nextDouble() * 2.3;
            float tf = an == Animation.SWIRL ? 1.5f : 0.4f;               // sideways swirl speed
            float rf = an == Animation.BURST ? 1.6f : 0f;                 // outward push
            float uf = an == Animation.RISE ? 1.3f : 0.5f;                // upward speed
            float tx = (float) -Math.sin(ang) * tf, tz = (float) Math.cos(ang) * tf;
            float ox = (float) Math.cos(ang) * rf, oz = (float) Math.sin(ang) * rf;
            int col = c.animation == Animation.RAINBOW ? Palette.rainbow(r.nextFloat(), 0.7f, 1f) : Palette.byName(v);
            if (c.custom) col = Palette.rgb(c);
            switch (v) {
                case "flame" -> WorldFx.spark(x, y, z, tx * 0.4f, uf * (0.9f + r.nextFloat() * 0.8f), tz * 0.4f, 0.8f + r.nextFloat() * 0.6f,
                    0.07f + r.nextFloat() * 0.07f, r.nextInt(3) == 0 ? 0xFFD060 : col, WorldFx.ORB, 0.6f, 0f);
                case "frost" -> WorldFx.spark(x, y, z, tx * 0.5f + ox, -0.1f + r.nextFloat() * 0.2f, tz * 0.5f + oz, 1.6f + r.nextFloat(),
                    0.045f + r.nextFloat() * 0.04f, col, WorldFx.STAR, 0.5f, 0f);
                case "void" -> {
                    float inx = (float) (p.getX() - x) * 0.7f, inz = (float) (p.getZ() - z) * 0.7f;
                    WorldFx.spark(x, y, z, inx + tx, 0.1f, inz + tz, 1.3f + r.nextFloat() * 0.6f, 0.08f + r.nextFloat() * 0.07f, col, WorldFx.ORB, 0.4f, 0f);
                }
                case "nature" -> WorldFx.spark(x, y, z, tx * 0.3f + ox, 0.2f + r.nextFloat() * 0.2f, tz * 0.3f + oz, 2f + r.nextFloat(),
                    0.04f + r.nextFloat() * 0.03f, col, WorldFx.ORB, 0.3f, 0f);
                default -> WorldFx.spark(x, y, z, tx * 0.4f + ox, uf * (0.5f + r.nextFloat() * 0.6f), tz * 0.4f + oz, 1.2f + r.nextFloat() * 0.8f,
                    0.05f + r.nextFloat() * 0.05f, col, WorldFx.STAR, 0.4f, 0f);
            }
        }
    }

    private static int trailColor(String v, float k) {
        return switch (v) {
            case "rainbow" -> Palette.rainbow(tick * 0.03f + k, 0.7f, 1f);
            case "fire" -> k < 0.5f ? 0xFFC060 : 0xFF6A20;
            case "ice" -> k < 0.5f ? 0xFFFFFF : 0x7FE4FF;
            case "stardust" -> 0xFFE98A;
            case "soul" -> 0x6FFFFF;
            default -> k < 0.5f ? 0xD08CFF : 0x7F9CFF;
        };
    }

    private static void trail(ClientPlayerEntity p, Cosmetic c, Random r, double vx, double vz) {
        int count = c.animation == Animation.COMET ? 4 : 2;
        for (int k = 0; k < count; k++) {
            double x = p.getX() - vx * (1.0 + r.nextDouble() * 2.5) + (r.nextDouble() - 0.5) * 0.3;
            double y = p.getY() + 0.5 + r.nextDouble() * 0.9;
            double z = p.getZ() - vz * (1.0 + r.nextDouble() * 2.5) + (r.nextDouble() - 0.5) * 0.3;
            int col = trailColor(c.activeVariant, r.nextFloat());
            WorldFx.spark(x, y, z, (r.nextFloat() - 0.5f) * 0.3f, (r.nextFloat() - 0.3f) * 0.3f, (r.nextFloat() - 0.5f) * 0.3f,
                0.6f + r.nextFloat() * 0.5f, 0.04f + r.nextFloat() * 0.05f, col, r.nextBoolean() ? WorldFx.STAR : WorldFx.ORB, 1f, 0f);
        }
    }
}
