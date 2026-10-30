package dev.myclient.builder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Reads Litematica (.litematic) files including block properties (stairs facing, slab type, log axis ...). */
public final class Schematic {
    public final String name;
    public final int sizeX, sizeY, sizeZ;
    public final int[] xs, ys, zs;
    public final BlockState[] states;

    private Schematic(String name, int sx, int sy, int sz, int[] xs, int[] ys, int[] zs, BlockState[] states) {
        this.name = name; this.sizeX = sx; this.sizeY = sy; this.sizeZ = sz;
        this.xs = xs; this.ys = ys; this.zs = zs; this.states = states;
    }

    public int count() { return states.length; }

    private static <T extends Comparable<T>> BlockState withProp(BlockState s, Property<T> p, String v) {
        Optional<T> o = p.parse(v);
        return o.isPresent() ? s.with(p, o.get()) : s;
    }

    public static Schematic load(Path file) throws IOException {
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound regions = root.getCompound("Regions");

        List<int[]> pos = new ArrayList<>();
        List<BlockState> blk = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (String key : regions.getKeys()) {
            NbtCompound reg = regions.getCompound(key);
            NbtCompound p = reg.getCompound("Position"), s = reg.getCompound("Size");
            int sx = s.getInt("x"), sy = s.getInt("y"), sz = s.getInt("z");
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            if (ax == 0 || ay == 0 || az == 0) continue;
            int ox = p.getInt("x") + (sx < 0 ? sx + 1 : 0);
            int oy = p.getInt("y") + (sy < 0 ? sy + 1 : 0);
            int oz = p.getInt("z") + (sz < 0 ? sz + 1 : 0);

            NbtList pal = reg.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            BlockState[] palette = new BlockState[pal.size()];
            for (int i = 0; i < palette.length; i++) {
                NbtCompound e = pal.getCompound(i);
                Identifier id = Identifier.tryParse(e.getString("Name"));
                Block b = id == null ? Blocks.AIR : Registries.BLOCK.get(id);
                BlockState st = b.getDefaultState();
                NbtCompound props = e.getCompound("Properties");
                for (String k : props.getKeys()) {
                    Property<?> pr = b.getStateManager().getProperty(k);
                    if (pr != null) st = withProp(st, pr, props.getString(k));
                }
                palette[i] = st;
            }
            if (palette.length == 0) continue;
            long[] states = reg.getLongArray("BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
            long mask = (1L << bits) - 1L;
            long total = (long) ax * ay * az;

            for (int idx = 0; idx < total; idx++) {
                long bitIndex = (long) idx * bits;
                int start = (int) (bitIndex >>> 6), off = (int) (bitIndex & 63);
                if (start >= states.length) break;
                long val = states[start] >>> off;
                if (off + bits > 64 && start + 1 < states.length) val |= states[start + 1] << (64 - off);
                int pi = (int) (val & mask);
                if (pi >= palette.length) continue;
                BlockState st = palette[pi];
                Block b = st.getBlock();
                if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR || b.asItem() == Items.AIR) continue;
                if (b == Blocks.PISTON_HEAD || b == Blocks.MOVING_PISTON) continue;
                if (st.contains(Properties.DOUBLE_BLOCK_HALF) && st.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue;
                if (st.contains(Properties.BED_PART) && st.get(Properties.BED_PART) == BedPart.HEAD) continue;
                int x = ox + idx % ax, z = oz + (idx / ax) % az, y = oy + idx / (ax * az);
                pos.add(new int[]{x, y, z});
                blk.add(st);
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
        }
        if (pos.isEmpty()) throw new IOException("Schematic has no placeable blocks");

        Integer[] order = new Integer[pos.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.<Integer>comparingInt(i -> pos.get(i)[1])
            .thenComparingInt(i -> pos.get(i)[0]).thenComparingInt(i -> pos.get(i)[2]));

        int n = order.length;
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        BlockState[] bs = new BlockState[n];
        for (int i = 0; i < n; i++) {
            int[] q = pos.get(order[i]);
            xs[i] = q[0] - minX; ys[i] = q[1] - minY; zs[i] = q[2] - minZ;
            bs[i] = blk.get(order[i]);
        }
        String nm = file.getFileName().toString().replaceFirst("\\.litematic$", "");
        return new Schematic(nm, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, xs, ys, zs, bs);
    }
}
