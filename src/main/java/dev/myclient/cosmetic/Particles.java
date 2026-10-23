package dev.myclient.cosmetic;

import dev.myclient.MyClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;

/** Spawns normal Minecraft particles for halos, auras and trails (client side only). */
public final class Particles {
    private Particles() {}

    private static int tick;

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.isPaused()) return;
        tick++;
        ClientPlayerEntity p = mc.player;
        boolean third = !mc.options.getPerspective().isFirstPerson();
        Random r = mc.world.random;
        double vx = p.getVelocity().x, vz = p.getVelocity().z;
        double speed = Math.sqrt(vx * vx + vz * vz);

        for (Cosmetic c : MyClient.COSMETICS.catalog) {
            if (!MyClient.COSMETICS.isEquipped(c)) continue;
            switch (c.type) {
                case HALO -> { if (third) halo(mc, p, c, r); }
                case AURA -> aura(mc, p, c, r);
                case TRAIL -> { if (speed > 0.04) trail(mc, p, c, r, vx, vz); }
                default -> { }
            }
        }
    }

    private static void halo(MinecraftClient mc, ClientPlayerEntity p, Cosmetic c, Random r) {
        boolean celestial = c.id.contains("celestial"), storm = c.id.contains("storm");
        ParticleEffect fx = storm ? ParticleTypes.ELECTRIC_SPARK : haloEffect(c.activeVariant);
        int count = celestial ? 2 : 1;
        double y = p.getY() + (p.isSneaking() ? 2.0 : 2.26);
        for (int k = 0; k < count; k++) {
            double ang = tick * 0.35 * c.speed + r.nextDouble() * 0.5 + k * Math.PI;
            double rad = 0.27 + r.nextDouble() * 0.06;
            mc.world.addParticle(fx, p.getX() + Math.cos(ang) * rad, y + (r.nextDouble() - 0.5) * 0.05,
                p.getZ() + Math.sin(ang) * rad, 0, 0.012 + r.nextDouble() * 0.01, 0);
        }
    }

    private static ParticleEffect haloEffect(String v) {
        return switch (v) {
            case "gold" -> ParticleTypes.WAX_ON;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "neon" -> ParticleTypes.HAPPY_VILLAGER;
            case "purple" -> ParticleTypes.WITCH;
            default -> ParticleTypes.END_ROD;
        };
    }

    private static void aura(MinecraftClient mc, ClientPlayerEntity p, Cosmetic c, Random r) {
        ParticleEffect fx = switch (c.activeVariant) {
            case "flame" -> ParticleTypes.FLAME;
            case "frost" -> ParticleTypes.SNOWFLAKE;
            case "void" -> ParticleTypes.WITCH;
            case "nature" -> ParticleTypes.HAPPY_VILLAGER;
            default -> ParticleTypes.END_ROD;
        };
        if (r.nextInt(2) != 0) return;
        double ang = r.nextDouble() * Math.PI * 2;
        double rad = 0.45 + r.nextDouble() * 0.15;
        mc.world.addParticle(fx, p.getX() + Math.cos(ang) * rad, p.getY() + 0.1 + r.nextDouble() * 1.8,
            p.getZ() + Math.sin(ang) * rad, 0, 0.02 + r.nextDouble() * 0.03, 0);
    }

    private static void trail(MinecraftClient mc, ClientPlayerEntity p, Cosmetic c, Random r, double vx, double vz) {
        for (int k = 0; k < 3; k++) {
            double x = p.getX() - vx * (1.0 + r.nextDouble() * 2.5) + (r.nextDouble() - 0.5) * 0.25;
            double y = p.getY() + 0.08 + r.nextDouble() * 0.25;
            double z = p.getZ() - vz * (1.0 + r.nextDouble() * 2.5) + (r.nextDouble() - 0.5) * 0.25;
            switch (c.activeVariant) {
                case "rainbow" -> mc.world.addParticle(ParticleTypes.NOTE, x, y + 0.3, z, ((tick * 0.03) + r.nextDouble() * 0.2) % 1.0, 0, 0);
                case "fire" -> mc.world.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
                case "ice" -> mc.world.addParticle(ParticleTypes.SNOWFLAKE, x, y + 0.1, z, 0, 0.01, 0);
                case "soul" -> mc.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.01, 0);
                case "magic" -> mc.world.addParticle(ParticleTypes.WITCH, x, y + 0.1, z, 0, 0.01, 0);
                default -> mc.world.addParticle(ParticleTypes.END_ROD, x, y + 0.1, z, 0, 0.005, 0);
            }
        }
    }
}
