package ru.pcmod.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Серверная камера (с 0.17.0). Камера - замкнутый объём (до {@link #MAX_VOLUME} клеток воздуха), примыкающий к серверу.
 * Её стены - блоки {@link BlockChamberWall}, {@link BlockChamberGlass}, {@link BlockChiller} и устройства мода
 * (сам сервер, роутер...). Обычные твёрдые блоки утепляют слабо (30%), остальное - дыра.
 * <ul>
 * <li>{@link #seal} - доля герметичных граней стен (0..1): чем выше, тем меньше тепла уходит пассивно (до -75%);</li>
 * <li>кондиционеры {@link BlockChiller} в стенах камеры (или вплотную к серверу) дают {@link #CHILLER_W} Вт охлаждения;
 * вне камеры, вплотную, они работают вполсилы;</li>
 * <li>несколько серверов в одной камере делят охлаждение между собой.</li>
 * </ul>
 * Если рядом с сервером нет замкнутого объёма, камеры нет ({@link #closed} = false, seal = 0).
 */
public final class Chamber {
    public static final int MAX_VOLUME = 343;
    public static final double CHILLER_W = 70;

    private static final int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};

    public boolean closed;
    public int volume, chillers;
    public double seal;
    public final List<TileServer> servers = new ArrayList<TileServer>();

    private Chamber() {
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    /** Клетка, через которую ходит воздух: воздух, коннекторы, факелы и т. п. */
    private static boolean passable(Level w, int x, int y, int z) {
        if (Wires.badY(w, y)) return false;
        BlockPos pos = new BlockPos(x, y, z);
        BlockState s = w.getBlockState(pos);
        // 1.12.2: воздух или (не твёрдый и не жидкость); Material убран -> форма коллизии и жидкость
        return s.isAir() || (s.getCollisionShape(w, pos).isEmpty() && s.getFluidState().isEmpty());
    }

    private static double sealOf(Level w, BlockPos pos, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof BlockChamberWall || b instanceof BlockChamberGlass || b instanceof BlockChiller
                || Ports.isDevice(b)) {
            return 1.0;
        }
        if (s.isSolidRender(w, pos)) return 0.3;
        if (!s.getCollisionShape(w, pos).isEmpty()) return 0.1;
        return 0.0;
    }

    /** Разбор окружения сервера. Вызывается на сервере раз в несколько секунд. */
    public static Chamber analyze(Level w, TileServer self) {
        Chamber c = new Chamber();
        Set<Long> seen = new HashSet<Long>();
        for (int d = 0; d < 6; d++) {
            int x = self.getBlockPos().getX() + DX[d], y = self.getBlockPos().getY() + DY[d], z = self.getBlockPos().getZ() + DZ[d];
            if (!w.hasChunkAt(new BlockPos(x, y, z)) || !passable(w, x, y, z) || seen.contains(key(x, y, z))) continue;
            List<int[]> cells = flood(w, x, y, z, seen);
            if (cells != null) {
                c.fill(w, self, cells);
                return c;
            }
        }
        // камеры нет: считаем только кондиционеры вплотную к серверу
        c.servers.add(self);
        for (int d = 0; d < 6; d++) {
            int x = self.getBlockPos().getX() + DX[d], y = self.getBlockPos().getY() + DY[d], z = self.getBlockPos().getZ() + DZ[d];
            if (w.hasChunkAt(new BlockPos(x, y, z)) && w.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof BlockChiller) c.chillers++;
        }
        return c;
    }

    /** null - объём не замкнут (вышел за лимит или в незагруженный чанк). */
    private static List<int[]> flood(Level w, int sx, int sy, int sz, Set<Long> seen) {
        List<int[]> cells = new ArrayList<int[]>();
        List<int[]> queue = new ArrayList<int[]>();
        queue.add(new int[]{sx, sy, sz});
        seen.add(key(sx, sy, sz));
        for (int i = 0; i < queue.size(); i++) {
            int[] p = queue.get(i);
            cells.add(p);
            if (cells.size() > MAX_VOLUME) return null;
            for (int d = 0; d < 6; d++) {
                int x = p[0] + DX[d], y = p[1] + DY[d], z = p[2] + DZ[d];
                if (Wires.badY(w, y)) continue;
                if (!w.hasChunkAt(new BlockPos(x, y, z))) return null;
                long k = key(x, y, z);
                if (seen.contains(k) || !passable(w, x, y, z)) continue;
                seen.add(k);
                queue.add(new int[]{x, y, z});
            }
        }
        return cells;
    }

    private void fill(Level w, TileServer self, List<int[]> cells) {
        closed = true;
        volume = cells.size();
        Set<Long> region = new HashSet<Long>();
        for (int[] p : cells) region.add(key(p[0], p[1], p[2]));
        Set<Long> counted = new HashSet<Long>();
        double sum = 0;
        int faces = 0;
        for (int[] p : cells) {
            for (int d = 0; d < 6; d++) {
                int x = p[0] + DX[d], y = p[1] + DY[d], z = p[2] + DZ[d];
                long k = key(x, y, z);
                if (region.contains(k)) continue;
                faces++;
                if (Wires.badY(w, y)) continue;
                BlockState bs = w.getBlockState(new BlockPos(x, y, z));
                Block b = bs.getBlock();
                sum += sealOf(w, new BlockPos(x, y, z), bs);
                if (b instanceof BlockChiller) {
                    if (counted.add(k)) chillers++;
                } else if (b instanceof BlockServer) {
                    if (counted.add(k)) {
                        BlockEntity te = w.getBlockEntity(new BlockPos(x, y, z));
                        if (te instanceof TileServer && !te.isRemoved()) servers.add((TileServer) te);
                    }
                }
            }
        }
        for (int d = 0; d < 6; d++) {
            int x = self.getBlockPos().getX() + DX[d], y = self.getBlockPos().getY() + DY[d], z = self.getBlockPos().getZ() + DZ[d];
            if (!Wires.badY(w, y) && w.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof BlockChiller && counted.add(key(x, y, z))) chillers++;
        }
        seal = faces == 0 ? 0 : sum / faces;
        if (!servers.contains(self)) servers.add(self);
    }
}
