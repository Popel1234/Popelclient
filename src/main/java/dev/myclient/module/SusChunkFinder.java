package dev.myclient.module;

import dev.myclient.MyClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import org.joml.Matrix4f;

import java.util.*;

/**
 * Scans every loaded chunk (from bedrock to the build limit) for fully or almost grown amethyst buds
 * (amethyst clusters and large amethyst buds) and marks those chunks red.
 */
public class SusChunkFinder extends Module {
    public static final class Sus {
        public final int cx, cz;
        public int count;
        public BlockPos first;
        Sus(int cx, int cz) { this.cx = cx; this.cz = cz; }
    }

    public final Map<Long, Sus> found = new HashMap<>();
    public int scanned;
    private final Set<Long> announced = new HashSet<>();
    private final List<Long> order = new ArrayList<>();
    private int cursor, tickCount;

    public SusChunkFinder() { super("Sus Chunk Finder"); }

    @Override
    protected void onDisable() { found.clear(); announced.clear(); order.clear(); }

    private void scanChunk(MinecraftClient mc, int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        Chunk ch = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (ch == null) { found.remove(key); announced.remove(key); return; }
        scanned++;
        int count = 0;
        BlockPos first = null;
        ChunkSection[] sections = ch.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty()) continue;
            if (!sec.hasAny(st -> st.isOf(Blocks.AMETHYST_CLUSTER) || st.isOf(Blocks.LARGE_AMETHYST_BUD))) continue;
            int baseY = ch.getBottomY() + i * 16;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                var st = sec.getBlockState(x, y, z);
                if (st.isOf(Blocks.AMETHYST_CLUSTER) || st.isOf(Blocks.LARGE_AMETHYST_BUD)) {
                    count++;
                    if (first == null) first = new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z);
                }
            }
        }
        if (count > 0) {
            Sus s = found.computeIfAbsent(key, k -> new Sus(cx, cz));
            s.count = count;
            s.first = first;
            if (announced.add(key) && mc.player != null) {
                mc.player.sendMessage(Text.literal("[Lumen] Sus chunk " + cx + ", " + cz + " (block " + cx * 16 + ", " + cz * 16
                    + ") - " + count + " grown amethyst buds"), false);
            }
        } else {
            found.remove(key);
            announced.remove(key);
        }
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        tickCount++;
        int rc = Math.max(2, Math.min(40, mc.options.getViewDistance().getValue())) + 1;
        ChunkPos pc = mc.player.getChunkPos();
        if (order.isEmpty() || tickCount % 100 == 0) {
            order.clear();
            List<int[]> list = new ArrayList<>();
            for (int dx = -rc; dx <= rc; dx++) for (int dz = -rc; dz <= rc; dz++) list.add(new int[]{pc.x + dx, pc.z + dz, dx * dx + dz * dz});
            list.sort(Comparator.comparingInt(a -> a[2]));
            for (int[] a : list) order.add(ChunkPos.toLong(a[0], a[1]));
        }
        for (int n = 0; n < 10 && !order.isEmpty(); n++) {
            long key = order.get(cursor % order.size());
            cursor++;
            scanChunk(mc, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int a) {
        vc.vertex(m, x, y, z).color(255, 40, 40, Math.max(0, Math.min(255, a)));
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, int a) {
        v(vc, m, x1, y1, z1, a); v(vc, m, x2, y2, z2, a); v(vc, m, x3, y3, z3, a); v(vc, m, x4, y4, z4, a);
        v(vc, m, x4, y4, z4, a); v(vc, m, x3, y3, z3, a); v(vc, m, x2, y2, z2, a); v(vc, m, x1, y1, z1, a);
    }

    /** Red curtain around every sus chunk, from bedrock to the sky. */
    public static void renderWorld(WorldRenderContext ctx) {
        SusChunkFinder f = MyClient.MODULES.susChunks;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!f.isEnabled() || f.found.isEmpty() || mc.world == null) return;
        VertexConsumerProvider vcp = ctx.consumers();
        if (vcp == null || ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) ms = new MatrixStack();
        Vec3d cp = ctx.camera().getPos();
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        float yb = (float) (mc.world.getBottomY() - cp.y), yt = (float) (mc.world.getBottomY() + mc.world.getHeight() - cp.y);
        for (Sus s : f.found.values()) {
            float x0 = (float) (s.cx * 16 - cp.x), x1 = x0 + 16, z0 = (float) (s.cz * 16 - cp.z), z1 = z0 + 16;
            quad(vc, mat, x0, yb, z0, x1, yb, z0, x1, yt, z0, x0, yt, z0, 46);
            quad(vc, mat, x1, yb, z0, x1, yb, z1, x1, yt, z1, x1, yt, z0, 46);
            quad(vc, mat, x1, yb, z1, x0, yb, z1, x0, yt, z1, x1, yt, z1, 46);
            quad(vc, mat, x0, yb, z1, x0, yb, z0, x0, yt, z0, x0, yt, z1, 46);
            float w = 0.12f;
            float[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
            for (float[] c : corners) {
                quad(vc, mat, c[0] - w, yb, c[1], c[0] + w, yb, c[1], c[0] + w, yt, c[1], c[0] - w, yt, c[1], 150);
                quad(vc, mat, c[0], yb, c[1] - w, c[0], yb, c[1] + w, c[0], yt, c[1] + w, c[0], yt, c[1] - w, 150);
            }
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLightning());
    }
}
