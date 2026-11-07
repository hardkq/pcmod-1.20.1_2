package ru.pcmod.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Поиск связи «корпус ПК - монитор»: вплотную или через видеопровода (до MAX_WIRES штук подряд между
 * видеоконнекторами, воткнутыми в видеопорты корпуса и монитора).
 */
public final class VideoLink {
    /** Сколько видеопроводов может быть в цепочке между корпусом и монитором (длина каждого - до {@link Wires#MAX_VIDEO} блоков). */
    public static final int MAX_WIRES = 3;
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private VideoLink() {
    }

    /** Позиции {x, y, z} всех мониторов, подключённых к корпусу в (x, y, z). Ближайшие идут первыми. */
    public static List<int[]> monitorsOf(Level w, int x, int y, int z) {
        return search(w, x, y, z, true);
    }

    /** ПК, к которому подключён монитор в (x, y, z), или null. */
    public static TileComputer computerOf(Level w, int x, int y, int z) {
        for (int[] p : search(w, x, y, z, false)) {
            BlockEntity te = w.getBlockEntity(new BlockPos(p[0], p[1], p[2]));
            if (te instanceof TileComputer) return (TileComputer) te;
        }
        return null;
    }

    private static boolean wanted(Block b, boolean wantMonitor) {
        return wantMonitor ? b instanceof BlockMonitor : b instanceof BlockComputer;
    }

    /**
     * Поиск от точки старта: сначала устройства вплотную (как раньше), затем видеоконнекторы, воткнутые в порты
     * старта, и дальше по видеопроводам. На другом конце провода ищется устройство, в порт которого воткнут коннектор.
     */
    private static List<int[]> search(Level w, int sx, int sy, int sz, boolean wantMonitor) {
        List<int[]> found = new ArrayList<int[]>();
        Set<Long> seen = new HashSet<Long>();
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        seen.add(key(sx, sy, sz));
        for (int[] dir : DIRS) {
            int x = sx + dir[0], y = sy + dir[1], z = sz + dir[2];
            if (Wires.badY(w, y) || !w.hasChunkAt(new BlockPos(x, y, z))) continue;
            Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
            if (wanted(b, wantMonitor)) {
                if (seen.add(key(x, y, z))) found.add(new int[]{x, y, z});
            } else if (Wires.isConnectorFor(w, x, y, z, Ports.VIDEO)) {
                if (Wires.plugsInto(w, x, y, z, sx, sy, sz, Ports.VIDEO) && seen.add(key(x, y, z))) {
                    queue.add(new int[]{x, y, z, 0}); // последнее число - сколько проводов пройдено
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] t : Wires.plugTargets(w, cur[0], cur[1], cur[2], Ports.VIDEO)) {
                if (seen.add(key(t[0], t[1], t[2])) && wanted(w.getBlockState(new BlockPos(t[0], t[1], t[2])).getBlock(), wantMonitor)) {
                    found.add(t);
                }
            }
            if (cur[3] >= MAX_WIRES) continue;
            for (int[] p : Wires.partnerPositions(w, cur[0], cur[1], cur[2], Ports.VIDEO)) {
                if (!Wires.isConnectorFor(w, p[0], p[1], p[2], Ports.VIDEO)) continue;
                if (seen.add(key(p[0], p[1], p[2]))) queue.add(new int[]{p[0], p[1], p[2], cur[3] + 1});
            }
        }
        return found;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
