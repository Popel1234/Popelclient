package dev.myclient.module;

import dev.myclient.cosmetic.Palette;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Draws lines (tracers) to every block of the types you selected, within a radius. */
public class Tracers extends Module {
    public static final class Target {
        public final BlockPos pos;
        public final int color;
        public final String name;
        Target(BlockPos pos, int color, String name) { this.pos = pos; this.color = color; this.name = name; }
    }

    public final Set<String> blocks = new LinkedHashSet<>();
    public int radius = 32;
    public List<Target> found = new ArrayList<>();

    private List<Target> next = new ArrayList<>();
    private final Map<Block, Integer> colors = new HashMap<>();
    private final Map<Block, String> names = new HashMap<>();
    private Set<Block> resolved = new HashSet<>();
    private int version, resolvedVersion = -1, slice;

    public Tracers() { super("Tracers"); }

    public void changed() { version++; }

    public boolean toggleBlock(String id) {
        boolean added = !blocks.remove(id);
        if (added) blocks.add(id);
        changed();
        if (added && !isEnabled()) setEnabled(true);
        return added;
    }

    @Override
    protected void onDisable() { found = new ArrayList<>(); next = new ArrayList<>(); }

    @Override
    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        if (blocks.isEmpty()) { found = new ArrayList<>(); return; }
        if (resolvedVersion != version) {
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
            slice = 0;
            next = new ArrayList<>();
        }
        int r = Math.max(8, Math.min(48, radius));
        int slices = Math.max(8, r / 4);
        BlockPos c = mc.player.getBlockPos();
        int total = 2 * r + 1, per = (total + slices - 1) / slices;
        int x0 = c.getX() - r + slice * per, x1 = Math.min(x0 + per - 1, c.getX() + r);
        int yMin = Math.max(mc.world.getBottomY(), c.getY() - r), yMax = Math.min((mc.world.getBottomY() + mc.world.getHeight() - 1), c.getY() + r);
        BlockPos.Mutable mp = new BlockPos.Mutable();
        for (int x = x0; x <= x1; x++) {
            for (int z = c.getZ() - r; z <= c.getZ() + r; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    mp.set(x, y, z);
                    Block b = mc.world.getBlockState(mp).getBlock();
                    if (resolved.contains(b)) next.add(new Target(mp.toImmutable(), colors.get(b), names.get(b)));
                }
            }
        }
        if (++slice >= slices) {
            slice = 0;
            BlockPos p = c;
            next.sort(Comparator.comparingDouble(t -> t.pos.getSquaredDistance(p)));
            if (next.size() > 300) next = new ArrayList<>(next.subList(0, 300));
            found = next;
            next = new ArrayList<>();
        }
    }
}
