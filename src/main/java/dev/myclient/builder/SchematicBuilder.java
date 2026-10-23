package dev.myclient.builder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds a loaded Litematica schematic by placing blocks against existing blocks (like a "printer").
 * Order: bottom to top. If the next block it needs is not in the hotbar it pauses until you put it there.
 * Only blocks within reach are placed; enable "Walk to blocks" to let it walk towards the build.
 */
public final class SchematicBuilder {
    private SchematicBuilder() {}

    public static Schematic loaded;
    public static BlockPos origin;
    public static boolean running;
    public static boolean walk;
    public static String status = "Load a schematic first";
    public static String missing = "";

    private static boolean[] done;
    private static int[] lastTry;
    private static int remaining;
    private static int cursor;
    private static int tick;
    private static boolean walking;

    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    public static List<Path> listFiles() {
        List<Path> out = new ArrayList<>();
        Path dir = folder();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.walk(dir, 4)) {
            s.filter(p -> p.toString().toLowerCase().endsWith(".litematic")).sorted().forEach(out::add);
        } catch (IOException ignored) {}
        return out;
    }

    public static void load(Path file) throws IOException {
        Schematic s = Schematic.load(file);
        loaded = s;
        done = new boolean[s.count()];
        lastTry = new int[s.count()];
        remaining = s.count();
        cursor = 0;
        running = false;
        missing = "";
        status = "Loaded " + s.name + ": " + s.count() + " blocks (" + s.sizeX + "x" + s.sizeY + "x" + s.sizeZ + "). Set the origin, then start.";
    }

    public static int total() { return loaded == null ? 0 : loaded.count(); }

    public static int placed() { return loaded == null ? 0 : loaded.count() - remaining; }

    public static void setOriginHere() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) origin = p.getBlockPos();
    }

    public static void setRunning(boolean on) {
        if (on && (loaded == null || origin == null)) {
            status = loaded == null ? "Load a schematic first" : "Set the origin first (stand where the build should start)";
            return;
        }
        running = on;
        if (!on) { releaseKeys(); status = "Stopped"; }
    }

    public static void restart() {
        if (loaded == null) return;
        done = new boolean[loaded.count()];
        lastTry = new int[loaded.count()];
        remaining = loaded.count();
        cursor = 0;
    }

    private static void releaseKeys() {
        if (walking) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            walking = false;
        }
    }

    private static int findSlot(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static void tick(MinecraftClient mc) {
        tick++;
        if (!running || loaded == null || origin == null) { releaseKeys(); return; }
        ClientPlayerEntity p = mc.player;
        World w = mc.world;
        if (p == null || w == null || mc.interactionManager == null) return;

        while (cursor < loaded.count() && done[cursor]) cursor++;
        if (cursor >= loaded.count()) {
            running = false;
            releaseKeys();
            status = "Finished! All " + loaded.count() + " blocks are in place.";
            return;
        }

        Vec3d eye = p.getEyePos();
        int placedNow = 0;
        boolean paused = false, sawUnsupported = false, sawFar = false;
        Set<String> need = new LinkedHashSet<>();
        BlockPos firstTarget = null;

        for (int i = cursor; i < loaded.count() && i < cursor + 500; i++) {
            if (done[i]) continue;
            BlockPos pos = origin.add(loaded.xs[i], loaded.ys[i], loaded.zs[i]);
            Block b = loaded.blocks[i];
            BlockState cur = w.getBlockState(pos);
            if (cur.isOf(b)) { done[i] = true; remaining--; continue; }
            if (!cur.isAir() && !cur.isReplaceable()) { done[i] = true; remaining--; continue; }   // something else is there: skip
            if (firstTarget == null) firstTarget = pos;

            Direction face = null;
            BlockPos neighbor = null;
            Vec3d hitPos = null;
            double best = 4.4 * 4.4;
            boolean supported = false;
            for (Direction d : Direction.values()) {
                BlockPos nb = pos.offset(d);
                BlockState ns = w.getBlockState(nb);
                if (ns.isAir() || ns.isReplaceable() || ns.getCollisionShape(w, nb).isEmpty()) continue;
                supported = true;
                Direction f = d.getOpposite();
                Vec3d hp = Vec3d.ofCenter(nb).add(f.getOffsetX() * 0.5, f.getOffsetY() * 0.5, f.getOffsetZ() * 0.5);
                double dist = eye.squaredDistanceTo(hp);
                if (dist <= best) { best = dist; face = f; neighbor = nb; hitPos = hp; }
            }
            if (!supported) { sawUnsupported = true; continue; }
            if (face == null) { sawFar = true; continue; }

            int slot = findSlot(p, b.asItem());
            if (slot < 0) {
                need.add(b.getName().getString());
                paused = true;
                break;                              // needs a block that is not in the hotbar: stop here
            }
            if (tick - lastTry[i] < 4) continue;
            lastTry[i] = tick;
            p.getInventory().selectedSlot = slot;
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hitPos, face, neighbor, false));
            p.swingHand(Hand.MAIN_HAND);
            if (++placedNow >= 2) break;
        }

        if (paused) {
            missing = String.join(", ", need);
            status = "Paused - put in your hotbar: " + missing;
            releaseKeys();
            return;
        }
        missing = "";
        if (placedNow > 0) {
            status = "Building... " + placed() + " / " + total();
            releaseKeys();
            return;
        }
        if (sawFar && firstTarget != null) {
            if (walk) {
                double dx = firstTarget.getX() + 0.5 - p.getX(), dz = firstTarget.getZ() + 0.5 - p.getZ();
                p.setYaw((float) (Math.toDegrees(Math.atan2(-dx, dz))));
                mc.options.forwardKey.setPressed(Math.sqrt(dx * dx + dz * dz) > 1.5);
                mc.options.jumpKey.setPressed(p.horizontalCollision);
                walking = true;
                status = "Walking to the next blocks... " + placed() + " / " + total();
            } else {
                status = "Waiting - move closer to the build (" + placed() + " / " + total() + ")";
            }
        } else if (sawUnsupported) {
            status = "Nothing to build against yet - stand next to the ground / existing blocks";
            releaseKeys();
        } else {
            status = "Working... " + placed() + " / " + total();
        }
    }
}
