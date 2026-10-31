package dev.myclient.builder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds a loaded Litematica schematic like a player: walks next to the next block, turns the camera to it,
 * swings the arm and places it with the right orientation (log axis, stairs facing/half, slab type ...).
 * Bottom to top. Pauses when a needed block is not in the hotbar.
 */
public final class SchematicBuilder {
    private SchematicBuilder() {}

    public static Schematic loaded;
    public static BlockPos origin;
    public static boolean running;
    public static boolean walk = true;
    public static String status = "Load a schematic first";
    public static String missing = "";
    public static String lastClick = "-";

    private static boolean[] done;
    private static int[] lastTry, fails;
    private static int remaining, cursor, tick, cooldown;
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
        reset(s.count());
        running = false;
        missing = "";
        status = "Loaded " + s.name + ": " + s.count() + " blocks (" + s.sizeX + "x" + s.sizeY + "x" + s.sizeZ + "). Set the origin, then start.";
    }

    private static void reset(int n) {
        done = new boolean[n];
        lastTry = new int[n];
        fails = new int[n];
        remaining = n;
        cursor = 0;
    }

    public static int total() { return loaded == null ? 0 : loaded.count(); }

    public static int placed() { return loaded == null ? 0 : loaded.count() - remaining; }

    /** The schematic's lowest corner goes two blocks in front of you. */
    public static void setOriginHere() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p != null) origin = p.getBlockPos().offset(p.getHorizontalFacing(), 2);
    }

    public static void setRunning(boolean on) {
        if (on && (loaded == null || origin == null)) {
            status = loaded == null ? "Load a schematic first" : "Set the origin first";
            return;
        }
        running = on;
        if (!on) { releaseKeys(); status = "Stopped"; }
    }

    public static void restart() {
        if (loaded != null) reset(loaded.count());
    }

    private static void releaseKeys() {
        if (walking) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            walking = false;
        }
    }

    private static int findSlot(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    private static boolean lookAt(ClientPlayerEntity p, Vec3d eye, Vec3d t) {
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        float ty = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float tp = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float dyaw = MathHelper.wrapDegrees(ty - p.getYaw()), dp = MathHelper.wrapDegrees(tp - p.getPitch());
        float sy = Math.abs(dyaw) < 3f ? dyaw : MathHelper.clamp(dyaw * 0.5f, -40f, 40f);
        float sp = Math.abs(dp) < 3f ? dp : MathHelper.clamp(dp * 0.5f, -30f, 30f);
        p.setYaw(p.getYaw() + sy);
        p.setPitch(MathHelper.clamp(p.getPitch() + sp, -90f, 90f));
        return Math.abs(dyaw) < 7f && Math.abs(dp) < 7f;
    }

    /** Tells the server which way we "face" right now (needed for stairs, furnaces ...) without moving the camera. */
    private static void sendLook(ClientPlayerEntity p, float yaw, float pitch) {
        try {
            for (Constructor<?> c : PlayerMoveC2SPacket.LookAndOnGround.class.getConstructors()) {
                Class<?>[] t = c.getParameterTypes();
                if (t.length == 3 && t[0] == float.class && t[1] == float.class && t[2] == boolean.class) {
                    p.networkHandler.sendPacket((Packet<?>) c.newInstance(yaw, pitch, p.isOnGround()));
                    return;
                }
                if (t.length == 4 && t[0] == float.class && t[1] == float.class && t[2] == boolean.class && t[3] == boolean.class) {
                    p.networkHandler.sendPacket((Packet<?>) c.newInstance(yaw, pitch, p.isOnGround(), p.horizontalCollision));
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static final class Plan {
        Direction face;
        BlockPos nb;
        Vec3d hit;
        double dist;
        float yaw = Float.NaN;
    }

    /** Picks the neighbour/face to click on so the block comes out with the wanted orientation. */
    private static Plan plan(World w, BlockPos pos, BlockState want, Vec3d eye) {
        Double yFrac = null;
        boolean wantTop = false, wantBottom = false;
        if (want.contains(Properties.SLAB_TYPE)) {
            SlabType t = want.get(Properties.SLAB_TYPE);
            wantTop = t == SlabType.TOP; wantBottom = t != SlabType.TOP;
        } else if (want.contains(Properties.BLOCK_HALF)) {
            wantTop = want.get(Properties.BLOCK_HALF) == BlockHalf.TOP; wantBottom = !wantTop;
        }
        if (wantTop) yFrac = 0.75; else if (wantBottom) yFrac = 0.25;

        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos nb2 = pos.offset(d);
            BlockState ns = w.getBlockState(nb2);
            if (ns.isAir() || ns.isReplaceable() || ns.getCollisionShape(w, nb2).isEmpty()) continue;
            Direction f = d.getOpposite();
            Vec3d hp = Vec3d.ofCenter(nb2).add(f.getOffsetX() * 0.5, f.getOffsetY() * 0.5, f.getOffsetZ() * 0.5);
            if (f.getAxis().isHorizontal() && yFrac != null) hp = new Vec3d(hp.x, nb2.getY() + yFrac, hp.z);
            double dist = eye.squaredDistanceTo(hp);
            double score = dist;
            if (want.contains(Properties.AXIS) && f.getAxis() != want.get(Properties.AXIS)) score += 1000;
            if (wantTop && f == Direction.UP) score += 500;
            if (wantBottom && f == Direction.DOWN) score += 500;
            if (score < bestScore) {
                bestScore = score;
                best = new Plan();
                best.face = f; best.nb = nb2; best.hit = hp; best.dist = dist;
            }
        }
        if (best != null && want.contains(Properties.HORIZONTAL_FACING)) {
            Direction hf = want.get(Properties.HORIZONTAL_FACING);
            Direction look = want.getBlock() instanceof StairsBlock ? hf : hf.getOpposite();
            best.yaw = (float) Math.toDegrees(Math.atan2(-look.getOffsetX(), look.getOffsetZ()));
        }
        return best;
    }

    public static void tick(MinecraftClient mc) {
        tick++;
        if (cooldown > 0) cooldown--;
        if (!running || loaded == null || origin == null) { releaseKeys(); return; }
        ClientPlayerEntity p = mc.player;
        World w = mc.world;
        if (p == null || w == null || mc.interactionManager == null) return;

        int n = loaded.count();
        while (cursor < n && done[cursor]) cursor++;
        if (cursor >= n) {
            running = false;
            releaseKeys();
            status = "Finished! All " + n + " blocks are in place.";
            return;
        }

        Vec3d eye = p.getEyePos();
        Box body = p.getBoundingBox();
        int idx = -1, slot = -1;
        BlockState want = null;
        BlockPos pos = null;
        Plan plan = null;
        boolean paused = false, sawUnsupported = false, sawBody = false;
        Set<String> need = new LinkedHashSet<>();

        for (int i = cursor; i < n && i < cursor + 500; i++) {
            if (done[i]) continue;
            BlockPos bp = origin.add(loaded.xs[i], loaded.ys[i], loaded.zs[i]);
            BlockState ws = loaded.states[i];
            Block b = ws.getBlock();
            BlockState cur = w.getBlockState(bp);
            if (cur.isOf(b)) { done[i] = true; remaining--; continue; }
            if (!cur.isAir() && !cur.isReplaceable()) { done[i] = true; remaining--; continue; }
            if (body.intersects(new Box(bp))) { sawBody = true; continue; }
            Plan pl = plan(w, bp, ws, eye);
            if (pl == null) { sawUnsupported = true; continue; }
            int sl = findSlot(p, b.asItem());
            if (sl < 0) {
                need.add(b.getName().getString());
                paused = true;
                break;
            }
            idx = i; slot = sl; want = ws; pos = bp; plan = pl;
            break;
        }

        if (paused) {
            missing = String.join(", ", need);
            status = "Paused - put in your hotbar: " + missing;
            releaseKeys();
            return;
        }
        missing = "";

        if (idx < 0) {
            if (sawBody) {
                if (walk) { mc.options.backKey.setPressed(true); walking = true; }
                status = "Stepping away from the build...";
            } else if (sawUnsupported) {
                status = "Nothing to build against yet - stand next to the ground / existing blocks";
                releaseKeys();
            } else {
                status = "Working... " + placed() + " / " + total();
                releaseKeys();
            }
            return;
        }

        if (plan.dist <= 4.3 * 4.3) {
            releaseKeys();
            boolean aligned = lookAt(p, eye, plan.hit);
            status = "Placing " + want.getBlock().getName().getString() + "... " + placed() + " / " + total();
            if (aligned && cooldown <= 0 && tick - lastTry[idx] >= 3) {
                p.getInventory().selectedSlot = slot;
                p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                boolean spoof = !Float.isNaN(plan.yaw);
                if (spoof) sendLook(p, plan.yaw, p.getPitch());
                ActionResult r = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(plan.hit, plan.face, plan.nb, false));
                if (spoof) sendLook(p, p.getYaw(), p.getPitch());
                lastClick = r.isAccepted() ? "accepted" : r.getClass().getSimpleName();
                if (r.isAccepted()) p.swingHand(Hand.MAIN_HAND);
                lastTry[idx] = tick;
                cooldown = 2;
                if (++fails[idx] > 12) { done[idx] = true; remaining--; }
            }
        } else if (walk) {
            Vec3d center = Vec3d.ofCenter(pos);
            lookAt(p, eye, new Vec3d(center.x, Math.max(center.y, eye.y - 0.5), center.z));
            mc.options.backKey.setPressed(false);
            mc.options.forwardKey.setPressed(true);
            mc.options.jumpKey.setPressed(p.horizontalCollision);
            walking = true;
            status = "Walking to the build... " + placed() + " / " + total();
        } else {
            releaseKeys();
            status = "Waiting - move closer to the build (" + placed() + " / " + total() + ")";
        }
    }
}
