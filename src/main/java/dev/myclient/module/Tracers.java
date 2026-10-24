package dev.myclient.module;

import dev.myclient.MyClient;
import dev.myclient.cosmetic.Palette;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;

/**
 * Finds the blocks you selected in all loaded chunks (up to 512 blocks away) and gives them to the
 * HUD renderer: Tracers draws lines, X-Ray draws boxes.
 */
public class Tracers extends Module {
    public static final class Target {
        public final BlockPos pos;
        public final int color;
        public final String name;
        Target(BlockPos pos, int color, String name) { this.pos = pos; this.color = color; this.name = name; }
    }

    public final Set<String> blocks = new LinkedHashSet<>();
    public int radius = 128;
    public List<Target> found = new ArrayList<>();

    private final Map<Long, List<Target>> perChunk = new HashMap<>();
    private final List<Long> order = new ArrayList<>();
    private final Map<Block, Integer> colors = new HashMap<>();
    private final Map<Block, String> names = new HashMap<>();
    private Set<Block> resolved = new HashSet<>();
    private int version, resolvedVersion = -1, cursor, tickCount;

    public Tracers() { super("Tracers"); }

    public void changed() { version++; }

    public boolean scanNeeded() { return isEnabled() || MyClient.MODULES.xray.isEnabled(); }

    public boolean toggleBlock(String id) {
        boolean added = !blocks.remove(id);
        if (added) blocks.add(id);
        changed();
        if (added && !scanNeeded()) setEnabled(true);
        return added;
    }

    @Override
    protected void onDisable() {
        if (!MyClient.MODULES.xray.isEnabled()) { found = new ArrayList<>(); perChunk.clear(); }
    }

    private void resolve() {
        resolved = new HashSet<>();
        colors.clear();
        names.clear();
        for (String id : blocks) {
            Identifier ident = Identifier.tryParse(id);
            if (ident == null || !Registries.BLOCK.containsId(ident)) continue;
            Block b = Registries.BLOCK.get(ident);
            resolved.add(b);
            colors.put(b, 0xFF000000 | Palette.rainbow((ident.toString().hashCode() & 0x7fffffff) % 360 / 360f, 0.65f, 1f));
            names.put(b, ident.getPath().replace('_', ' '));
        }
        resolvedVersion = version;
        perChunk.clear();
        order.clear();
    }

    private void scanChunk(MinecraftClient mc, int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        Chunk ch = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (ch == null) { perChunk.remove(key); return; }
        ChunkSection[] sections = ch.getSectionArray();
        List<Target> out = new ArrayList<>();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            if (!sec.hasAny(st -> resolved.contains(st.getBlock()))) continue;
            int baseY = ch.getBottomY() + i * 16;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                BlockState st = sec.getBlockState(x, y, z);
                Block b = st.getBlock();
                if (resolved.contains(b)) out.add(new Target(new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z), colors.get(b), names.get(b)));
            }
        }
        if (out.isEmpty()) perChunk.remove(key); else perChunk.put(key, out);
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (!scanNeeded()) return;
        if (mc.world == null || mc.player == null) return;
        if (blocks.isEmpty()) { found = new ArrayList<>(); perChunk.clear(); return; }
        if (resolvedVersion != version) resolve();
        tickCount++;
        int r = Math.max(16, Math.min(512, radius));
        int rc = (r >> 4) + 1;
        ChunkPos pc = mc.player.getChunkPos();

        if (order.isEmpty() || tickCount % 60 == 0) {
            order.clear();
            List<int[]> list = new ArrayList<>();
            for (int dx = -rc; dx <= rc; dx++) for (int dz = -rc; dz <= rc; dz++) list.add(new int[]{pc.x + dx, pc.z + dz, dx * dx + dz * dz});
            list.sort(Comparator.comparingInt(a -> a[2]));
            for (int[] a : list) order.add(ChunkPos.toLong(a[0], a[1]));
            final int lim = rc;
            perChunk.keySet().removeIf(k -> Math.abs(ChunkPos.getPackedX(k) - pc.x) > lim || Math.abs(ChunkPos.getPackedZ(k) - pc.z) > lim);
        }
        for (int n = 0; n < 4 && !order.isEmpty(); n++) {
            long key = order.get(cursor % order.size());
            cursor++;
            scanChunk(mc, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
        }
        if (tickCount % 10 == 0) {
            List<Target> all = new ArrayList<>();
            double max = (double) r * r;
            for (List<Target> l : perChunk.values()) for (Target t : l) if (t.pos.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ()) <= max) all.add(t);
            final net.minecraft.util.math.Vec3d pp = mc.player.getPos();
            all.sort(Comparator.comparingDouble(t -> t.pos.getSquaredDistance(pp.x, pp.y, pp.z)));
            found = all.size() > 500 ? new ArrayList<>(all.subList(0, 500)) : all;
        }
    }
}
