package dev.myclient.fake;

import dev.myclient.Settings;
import dev.myclient.fx.WorldFx;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.*;

/**
 * Fake skeleton spawners: blocks YOU place (of the chosen block type) behave like spawners on your screen.
 * They "generate" bones and arrows, right click opens the spawner menu, and you can sell the drops.
 * Everything is client side only; the server only sees an ordinary block.
 */
public final class FakeSpawners {
    private FakeSpawners() {}

    public static final int BONE_PRICE = 150, ARROW_PRICE = 3;
    private static final double CYCLE_SECONDS = 15.0;

    public static final class Spawner {
        public final String world;
        public final BlockPos pos;
        public long bones, arrows, last;
        Spawner(String world, BlockPos pos) { this.world = world; this.pos = pos; this.last = System.currentTimeMillis(); }
    }

    private static final Map<String, Spawner> map = new HashMap<>();
    private static final List<Object[]> pending = new ArrayList<>();   // {BlockPos, Integer ttl}
    private static final Random rnd = new Random();
    private static int tick;

    private static String world(MinecraftClient mc) {
        String srv = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "?";
        return srv + "|" + dim;
    }

    private static String key(String world, BlockPos p) { return world + "|" + p.getX() + "," + p.getY() + "," + p.getZ(); }

    public static Block chosen() {
        Identifier id = Identifier.tryParse(Settings.spawnerBlock);
        return id != null && Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : null;
    }

    public static int count() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String w = world(mc);
        int n = 0;
        for (Spawner s : map.values()) if (s.world.equals(w)) n++;
        return n;
    }

    /** Production since the last visit: every 15 s one to four skeletons drop 0-2 bones and 0-2 arrows each. */
    public static void update(Spawner s) {
        long now = System.currentTimeMillis();
        double dt = Math.min(3600.0, (now - s.last) / 1000.0);
        int cycles = (int) (dt / CYCLE_SECONDS);
        if (cycles <= 0) return;
        s.last += (long) (cycles * CYCLE_SECONDS * 1000);
        int exact = Math.min(cycles, 400);
        for (int c = 0; c < exact; c++) {
            int spawns = 1 + rnd.nextInt(4);
            for (int k = 0; k < spawns; k++) { s.bones += rnd.nextInt(3); s.arrows += rnd.nextInt(3); }
        }
        if (cycles > exact) { s.bones += (long) ((cycles - exact) * 2.5); s.arrows += (long) ((cycles - exact) * 2.5); }
        s.bones = Math.min(s.bones, 100000);
        s.arrows = Math.min(s.arrows, 100000);
    }

    /** Sells everything of one kind; returns the money earned. */
    public static long sell(Spawner s, boolean bones, boolean arrows) {
        update(s);
        long total = 0;
        if (bones) { total += s.bones * BONE_PRICE; s.bones = 0; }
        if (arrows) { total += s.arrows * ARROW_PRICE; s.arrows = 0; }
        if (total > 0) {
            Settings.fakeMoney += total;
            FakePay.show(Settings.earnFormat.replace("{amount}", FakeScoreboard.formatMoney(total)));
        }
        return total;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!Settings.fakeSpawner || !world.isClient() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (player != mc.player) return ActionResult.PASS;
            Block chosen = chosen();
            if (chosen == null) return ActionResult.PASS;
            BlockPos clicked = hit.getBlockPos();
            Spawner s = map.get(key(world(mc), clicked));
            if (s != null && !player.isSneaking()) {
                mc.setScreen(new SpawnerScreen(s));
                return ActionResult.SUCCESS;
            }
            ItemStack st = player.getMainHandStack();
            if (st.getItem() instanceof BlockItem bi && bi.getBlock() == chosen) {
                BlockState cs = world.getBlockState(clicked);
                BlockPos place = cs.isReplaceable() ? clicked : clicked.offset(hit.getSide());
                pending.add(new Object[]{place, 15});
                world.playSound(player, place, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 1.0f, 0.9f);
            }
            return ActionResult.PASS;
        });
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        tick++;
        Block chosen = chosen();
        if (chosen == null) return;
        String w = world(mc);
        for (Iterator<Object[]> it = pending.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            BlockPos pos = (BlockPos) o[0];
            int ttl = (Integer) o[1] - 1;
            o[1] = ttl;
            if (mc.world.getBlockState(pos).isOf(chosen)) {
                map.putIfAbsent(key(w, pos), new Spawner(w, pos));
                it.remove();
            } else if (ttl <= 0) {
                it.remove();
            }
        }
        if (tick % 40 == 0) {
            map.values().removeIf(s -> s.world.equals(w)
                && mc.world.getChunkManager().isChunkLoaded(s.pos.getX() >> 4, s.pos.getZ() >> 4)
                && !mc.world.getBlockState(s.pos).isOf(chosen));
        }
    }

    public static List<String> serialize() {
        List<String> out = new ArrayList<>();
        for (Spawner s : map.values()) {
            out.add(s.world + ";" + s.pos.getX() + ";" + s.pos.getY() + ";" + s.pos.getZ() + ";" + s.bones + ";" + s.arrows + ";" + s.last);
        }
        return out;
    }

    public static void load(List<String> lines) {
        map.clear();
        for (String l : lines) {
            try {
                String[] p = l.split(";");
                Spawner s = new Spawner(p[0], new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                s.bones = Long.parseLong(p[4]);
                s.arrows = Long.parseLong(p[5]);
                s.last = Long.parseLong(p[6]);
                map.put(key(s.world, s.pos), s);
            } catch (Exception ignored) {}
        }
    }

    // ------------------------------------------------------------------ the skeleton inside the cage
    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f)), g = Math.min(255, (int) (((rgb >> 8) & 255) * f)), b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb) {
        vc.vertex(m, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255);
    }

    private static void face(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d, int rgb) {
        v(vc, m, a[0], a[1], a[2], rgb); v(vc, m, b[0], b[1], b[2], rgb); v(vc, m, c[0], c[1], c[2], rgb); v(vc, m, d[0], d[1], d[2], rgb);
        v(vc, m, d[0], d[1], d[2], rgb); v(vc, m, c[0], c[1], c[2], rgb); v(vc, m, b[0], b[1], b[2], rgb); v(vc, m, a[0], a[1], a[2], rgb);
    }

    private static void box(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int rgb) {
        face(vc, m, new float[]{x1, y2, z1}, new float[]{x2, y2, z1}, new float[]{x2, y2, z2}, new float[]{x1, y2, z2}, shade(rgb, 1.0f));
        face(vc, m, new float[]{x1, y1, z1}, new float[]{x2, y1, z1}, new float[]{x2, y1, z2}, new float[]{x1, y1, z2}, shade(rgb, 0.55f));
        face(vc, m, new float[]{x1, y1, z2}, new float[]{x2, y1, z2}, new float[]{x2, y2, z2}, new float[]{x1, y2, z2}, shade(rgb, 0.9f));
        face(vc, m, new float[]{x1, y1, z1}, new float[]{x2, y1, z1}, new float[]{x2, y2, z1}, new float[]{x1, y2, z1}, shade(rgb, 0.8f));
        face(vc, m, new float[]{x1, y1, z1}, new float[]{x1, y1, z2}, new float[]{x1, y2, z2}, new float[]{x1, y2, z1}, shade(rgb, 0.7f));
        face(vc, m, new float[]{x2, y1, z1}, new float[]{x2, y1, z2}, new float[]{x2, y2, z2}, new float[]{x2, y2, z1}, shade(rgb, 0.75f));
    }

    /** Skeleton in pixel units (feet at y = 0, 32 px tall). */
    private static void skeleton(VertexConsumer vc, Matrix4f m) {
        int bone = 0xE6E4D6, dark = 0x24262B;
        box(vc, m, -3, 0, -1, -1, 12, 1, bone);
        box(vc, m, 1, 0, -1, 3, 12, 1, bone);
        box(vc, m, -4, 12, -2, 4, 24, 2, bone);
        for (float y : new float[]{14, 17, 20}) box(vc, m, -3.5f, y, 2f, 3.5f, y + 1f, 2.15f, dark);
        box(vc, m, -6, 12, -1, -4, 24, 1, bone);
        box(vc, m, 4, 12, -1, 6, 24, 1, bone);
        box(vc, m, -4, 24, -4, 4, 32, 4, bone);
        box(vc, m, -3, 28, 4, -1, 30, 4.15f, dark);
        box(vc, m, 1, 28, 4, 3, 30, 4.15f, dark);
        box(vc, m, -0.5f, 26.5f, 4, 0.5f, 27.5f, 4.15f, dark);
        box(vc, m, -2, 25, 4, 2, 25.8f, 4.15f, dark);
    }

    /** Draws the slowly spinning skeleton inside every fake spawner near you. */
    public static void renderWorld(WorldRenderContext ctx) {
        if (!Settings.fakeSpawner || map.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || ctx.camera() == null) return;
        VertexConsumerProvider vcp = ctx.consumers();
        if (vcp == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) ms = new MatrixStack();
        String w = world(mc);
        Vec3d cp = ctx.camera().getPos();
        float t = (float) ((System.nanoTime() % 1_000_000_000_000L) / 1.0e9);
        VertexConsumer vc = null;
        for (Spawner s : map.values()) {
            if (!s.world.equals(w)) continue;
            double dx = s.pos.getX() + 0.5 - cp.x, dy = s.pos.getY() - cp.y, dz = s.pos.getZ() + 0.5 - cp.z;
            if (dx * dx + dy * dy + dz * dz > 48 * 48) continue;
            if (vc == null) vc = vcp.getBuffer(RenderLayer.getDebugQuads());
            ms.push();
            ms.translate(dx, dy + 0.1, dz);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 70f));
            float sc = 0.46f / 16f;
            ms.scale(sc, sc, sc);
            skeleton(vc, ms.peek().getPositionMatrix());
            ms.pop();
        }
        if (vc != null && vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getDebugQuads());
    }

    /** Small flames around the skeleton, like a real spawner. */
    public static void emitFx(MinecraftClient mc) {
        if (!Settings.fakeSpawner || map.isEmpty() || mc.world == null || mc.player == null || mc.isPaused()) return;
        String w = world(mc);
        int[] cols = {0xFF9A1F, 0xFFD25A, 0xFF5A12};
        for (Spawner s : map.values()) {
            if (!s.world.equals(w)) continue;
            double dx = s.pos.getX() + 0.5 - mc.player.getX(), dz = s.pos.getZ() + 0.5 - mc.player.getZ(), dy = s.pos.getY() - mc.player.getY();
            if (dx * dx + dy * dy + dz * dz > 32 * 32) continue;
            for (int k = 0; k < 2; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * 0.38;
                WorldFx.spark(s.pos.getX() + 0.5 + Math.cos(a) * r, s.pos.getY() + 0.15 + rnd.nextDouble() * 0.9, s.pos.getZ() + 0.5 + Math.sin(a) * r,
                    (rnd.nextFloat() - 0.5f) * 0.2f, 0.5f + rnd.nextFloat() * 0.8f, (rnd.nextFloat() - 0.5f) * 0.2f,
                    0.5f + rnd.nextFloat() * 0.4f, 0.05f + rnd.nextFloat() * 0.05f, cols[rnd.nextInt(3)], WorldFx.ORB, 0.8f, 0f);
            }
        }
    }
}
