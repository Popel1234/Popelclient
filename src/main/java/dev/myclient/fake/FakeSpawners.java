package dev.myclient.fake;

import dev.myclient.Settings;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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
}
