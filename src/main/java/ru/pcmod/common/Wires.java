package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/**
 * Провода и точки крепления (с 0.15.0 по образцу Create: Power Grid, с 0.16.0 - в любом пикселе грани).
 *
 * <ul>
 * <li>Провод ({@link ItemWire}) толщиной в 1 пиксель крепится к <b>любому пикселю</b> (сетка 16x16) любой твёрдой грани блока:
 * ПКМ по пикселю запоминает первую точку, ПКМ по второму пикселю соединяет их. Пока провод в руке и первая точка выбрана,
 * клиент рисует линию-предпросмотр до пикселя под прицелом ({@code WirePreview}).</li>
 * <li>Точка крепления - «пин» 1x1x1 пиксель на грани опоры. Живёт он в воздушной ячейке перед гранью: это блок-коннектор
 * ({@link BlockConnector}) с состоянием PIN=true (см. {@link BlockConnector}), который создаётся и убирается сам вместе с проводами.
 * В одной ячейке может быть несколько пинов (код пина - {@link #code}).</li>
 * <li>Стойка-коннектор (предмет-блок) работает как раньше: провод крепится к её торцу ({@link #TIP}).</li>
 * <li>Если пин стоит в центральных 8x8 пикселях грани-порта устройства своего типа ({@link Ports}) - а у корпуса ПК ровно на пикселе
 * порта на задней панели, - он воткнут в порт: устройство попадает в сеть. На другие места и грани провод тоже можно прикрепить - получится просто узел для проводов.</li>
 * <li>Длина провода ограничена ({@link #MAX_NET}, {@link #MAX_VIDEO}), у одной точки не больше {@link #MAX_LINKS} проводов.</li>
 * <li>Провод режут кусачками ({@link ItemPliers}); провод возвращается предметом.</li>
 * </ul>
 */
public final class Wires {
    /** Сколько проводов можно присоединить к одной точке. */
    public static final int MAX_LINKS = 4;
    /** Максимальная длина провода, блоков (расстояние между концами). */
    public static final int MAX_NET = 32, MAX_VIDEO = 16;
    /** Провисание: доля горизонтального расстояния. */
    public static final double SAG = 0.03D;
    /** Длина стойки коннектора (в долях блока), провод крепится к её свободному торцу. */
    public static final double POST = 6.0D / 16.0D;
    /** Высота пина над гранью опоры: 1 пиксель. */
    public static final double PIN_H = 1.0D / 16.0D;
    /** Код точки крепления «торец стойки». */
    public static final int TIP = -1;
    /** Биты кода пина: тип провода на пине (0.18.1: в одной ячейке могут быть пины и сетевых, и видеопроводов). Без битов (старые миры) тип берётся по блоку ячейки. */
    public static final int KIND_VIDEO = 1 << 11, KIND_NET = 1 << 12;
    /** Маска «сторона + пиксель» в коде пина (без битов типа). */
    public static final int POS_MASK = 0x7FF;
    /** Радиус выбора провода курсором кусачек. */
    private static final double PICK = 0.25D;
    private static final double REACH = 6.0D;
    private static final String[] DIRN = {"down", "up", "north", "south", "west", "east"};

    private Wires() {
    }

    /** Y вне высот мира (в 1.12.2 было y < 0 || y > 255). */
    public static boolean badY(BlockGetter w, int y) {
        return y < w.getMinBuildHeight() || y >= w.getMaxBuildHeight();
    }

    public static int maxLen(int kind) {
        return kind == Ports.VIDEO ? MAX_VIDEO : MAX_NET;
    }

    public static boolean holdingWire(Player p) {
        ItemStack s = p.getMainHandItem();
        return !s.isEmpty() && s.getItem() instanceof ItemWire;
    }

    public static TileConnector tile(BlockGetter w, int x, int y, int z) {
        BlockEntity te = w.getBlockEntity(new BlockPos(x, y, z));
        return te instanceof TileConnector ? (TileConnector) te : null;
    }

    /** Тип коннектора в точке (NET / VIDEO) или NONE, если там не коннектор. */
    public static int kindAt(BlockGetter w, int x, int y, int z) {
        Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
        return b instanceof BlockConnector ? ((BlockConnector) b).kind() : Ports.NONE;
    }

    /** Ячейка с пинами (а не стойка). */
    public static boolean isPinCell(BlockGetter w, int x, int y, int z) {
        BlockState s = w.getBlockState(new BlockPos(x, y, z));
        return s.getBlock() instanceof BlockConnector && BlockConnector.isPin(s);
    }

    /** Сторона, где у стойки-коннектора опора (метадата коннектора). */
    public static int supportDir(BlockGetter w, int x, int y, int z) {
        BlockState s = w.getBlockState(new BlockPos(x, y, z));
        return s.getBlock() instanceof BlockConnector ? BlockConnector.supportIndex(s) : 0;
    }

    // ===================== пины: коды и геометрия =====================

    /** Код пина: сторона опоры m (от ячейки к опоре), пиксель u, v (0..15) на грани. */
    public static int code(int m, int u, int v, int kind) {
        return (kind == Ports.VIDEO ? KIND_VIDEO : KIND_NET) | (m << 8) | (u << 4) | v;
    }

    /** Сторона опоры m из кода пина. */
    public static int dirOf(int code) {
        return (code >> 8) & 7;
    }

    /** Тип провода конца с кодом code в ячейке (x, y, z): NONE, если там не коннектор. */
    public static int kindOf(BlockGetter w, int x, int y, int z, int code) {
        Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
        if (!(b instanceof BlockConnector)) return Ports.NONE;
        int bk = ((BlockConnector) b).kind();
        if (code < 0) return bk;
        if ((code & KIND_VIDEO) != 0) return Ports.VIDEO;
        if ((code & KIND_NET) != 0) return Ports.NET;
        return bk;
    }

    /** Относится ли блок в (x, y, z) к проводам типа kind: стойка этого типа или ячейка с пинами (в ней могут быть пины любого типа). */
    public static boolean isConnectorFor(BlockGetter w, int x, int y, int z, int kind) {
        Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
        if (!(b instanceof BlockConnector)) return false;
        return isPinCell(w, x, y, z) || ((BlockConnector) b).kind() == kind;
    }

    /** Ось, перпендикулярная грани опоры m: 0 = X, 1 = Y, 2 = Z. */
    public static int axisOf(int m) {
        return m < 2 ? 1 : m < 4 ? 2 : 0;
    }

    /** Оси пикселей u и v на грани, перпендикулярной оси ax. */
    private static int[] tangents(int ax) {
        return ax == 1 ? new int[]{0, 2} : ax == 2 ? new int[]{0, 1} : new int[]{2, 1};
    }

    private static int px(double f) {
        int i = (int) Math.floor(f * 16.0D);
        return i < 0 ? 0 : i > 15 ? 15 : i;
    }

    /** Пиксель {u, v} грани side блока, в которую попал клик с долями (hx, hy, hz) внутри блока. */
    public static int[] pixel(int side, float hx, float hy, float hz) {
        int[] t = tangents(axisOf(side));
        double[] h = {hx, hy, hz};
        return new int[]{px(h[t[0]]), px(h[t[1]])};
    }

    /** Коробка пина в долях ячейки: {x0, y0, z0, x1, y1, z1}. */
    public static double[] pinBox(int code) {
        int m = dirOf(code), u = (code >> 4) & 15, v = code & 15;
        int ax = axisOf(m);
        int[] t = tangents(ax);
        double[] b = new double[6];
        b[t[0]] = u / 16.0D;
        b[t[0] + 3] = (u + 1) / 16.0D;
        b[t[1]] = v / 16.0D;
        b[t[1] + 3] = (v + 1) / 16.0D;
        if ((m & 1) == 0) {
            b[ax] = 0.0D;
            b[ax + 3] = PIN_H;
        } else {
            b[ax] = 1.0D - PIN_H;
            b[ax + 3] = 1.0D;
        }
        return b;
    }

    /** Точка крепления провода на пине: центр его кубика (мировые координаты). */
    public static double[] pinPoint(int x, int y, int z, int code) {
        double[] b = pinBox(code);
        return new double[]{x + (b[0] + b[3]) * 0.5D, y + (b[1] + b[4]) * 0.5D, z + (b[2] + b[5]) * 0.5D};
    }

    /** Точка крепления на стойке: центр свободного торца (в мировых координатах). */
    public static double[] tip(BlockGetter w, int x, int y, int z) {
        int m = supportDir(w, x, y, z);
        double o = 0.5D - POST;
        return new double[]{x + 0.5D + Net.DX[m] * o, y + 0.5D + Net.DY[m] * o, z + 0.5D + Net.DZ[m] * o};
    }

    /** Мировая точка крепления с кодом code в блоке (x, y, z). */
    public static double[] anchor(BlockGetter w, int x, int y, int z, int code) {
        return code < 0 ? tip(w, x, y, z) : pinPoint(x, y, z, code);
    }

    /** Ломаная провисшего провода между двумя точками. */
    public static double[][] curve(double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double h = Math.sqrt(dx * dx + dz * dz);
        double len = Math.sqrt(h * h + dy * dy);
        int n = (int) Math.max(6, Math.min(32, Math.ceil(len * 1.5D)));
        double sag = Math.min(3.0D, h * SAG + len * 0.005D);
        double[][] p = new double[n + 1][3];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            p[i][0] = a[0] + dx * t;
            p[i][1] = a[1] + dy * t - sag * 4.0D * t * (1.0D - t);
            p[i][2] = a[2] + dz * t;
        }
        return p;
    }

    /** Провод между двумя точками рисует и режет тот конец, чья позиция «меньше» (чтобы не было дублей). */
    public static boolean owns(int ax, int ay, int az, int ac, int bx, int by, int bz, int bc) {
        if (ax != bx) return ax < bx;
        if (ay != by) return ay < by;
        if (az != bz) return az < bz;
        return ac < bc;
    }

    // ===================== порты =====================

    /** Стойка-коннектор (6x6 пикселей) воткнута в порт-пластину устройства за гранью m. Порты корпуса ПК - одиночные пиксели, стойка к ним не подходит. */
    private static int[] plugAt(BlockGetter w, int x, int y, int z, int m, int kind) {
        int sx = x + Net.DX[m], sy = y + Net.DY[m], sz = z + Net.DZ[m];
        if (Wires.badY(w, sy)) return null;
        BlockState sst = w.getBlockState(new BlockPos(sx, sy, sz));
        if (!Ports.isDevice(sst.getBlock())) return null;
        return Ports.postFits(sst, m ^ 1, kind) ? new int[]{sx, sy, sz} : null;
    }

    /**
     * Пин воткнут в порт: у роутеров, серверов и мониторов - если стоит на центральной пластине 8x8 пикселей, у корпуса ПК -
     * только на пикселе порта (один пиксель на задней панели).
     */
    private static int[] pinPlug(BlockGetter w, int x, int y, int z, int code, int kind) {
        int u = (code >> 4) & 15, v = code & 15, m = dirOf(code);
        int sx = x + Net.DX[m], sy = y + Net.DY[m], sz = z + Net.DZ[m];
        if (Wires.badY(w, sy)) return null;
        BlockState sst = w.getBlockState(new BlockPos(sx, sy, sz));
        if (!Ports.isDevice(sst.getBlock())) return null;
        return Ports.pinFits(sst, m ^ 1, u, v, kind) ? new int[]{sx, sy, sz} : null;
    }

    /** Устройства, в порты которых воткнут коннектор или пины ячейки для проводов типа kind (пусто: ни во что не воткнут). */
    public static List<int[]> plugTargets(BlockGetter w, int x, int y, int z, int kind) {
        List<int[]> out = new ArrayList<int[]>();
        if (kind == Ports.NONE) return out;
        if (!isPinCell(w, x, y, z)) {
            if (kindAt(w, x, y, z) != kind) return out;
            int[] t = plugAt(w, x, y, z, supportDir(w, x, y, z), kind);
            if (t != null) out.add(t);
            return out;
        }
        TileConnector t = tile(w, x, y, z);
        if (t == null) return out;
        for (int code : t.pinCodes()) {
            if (kindOf(w, x, y, z, code) != kind) continue;
            int[] tg = pinPlug(w, x, y, z, code, kind);
            if (tg == null) continue;
            boolean dup = false;
            for (int[] o : out) if (o[0] == tg[0] && o[1] == tg[1] && o[2] == tg[2]) dup = true;
            if (!dup) out.add(tg);
        }
        return out;
    }

    /** Первое устройство, в порт которого воткнут коннектор для проводов типа kind (или null). */
    public static int[] plugTarget(BlockGetter w, int x, int y, int z, int kind) {
        List<int[]> l = plugTargets(w, x, y, z, kind);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Воткнут ли коннектор в порт устройства, которое лежит в направлении d от него (для проводов типа kind). */
    public static boolean plugged(BlockGetter w, int x, int y, int z, int d, int kind) {
        if (kind == Ports.NONE) return false;
        if (!isPinCell(w, x, y, z)) {
            return kindAt(w, x, y, z) == kind && d == supportDir(w, x, y, z) && plugTarget(w, x, y, z, kind) != null;
        }
        TileConnector t = tile(w, x, y, z);
        if (t == null) return false;
        for (int code : t.pinCodes()) {
            if (kindOf(w, x, y, z, code) == kind && dirOf(code) == d && pinPlug(w, x, y, z, code, kind) != null) return true;
        }
        return false;
    }

    /** Воткнут ли коннектор (x, y, z) в порт устройства (tx, ty, tz) проводом типа kind. */
    public static boolean plugsInto(BlockGetter w, int x, int y, int z, int tx, int ty, int tz, int kind) {
        for (int[] t : plugTargets(w, x, y, z, kind)) {
            if (t[0] == tx && t[1] == ty && t[2] == tz) return true;
        }
        return false;
    }

    /** Позиции {x, y, z} действующих партнёров коннектора по проводам типа kind (другой конец загружен, тип совпал, связь взаимная). Мёртвые связи на сервере удаляются. */
    public static List<int[]> partnerPositions(Level w, int x, int y, int z, int kind) {
        List<int[]> out = new ArrayList<int[]>();
        TileConnector t = tile(w, x, y, z);
        if (t == null) return out;
        boolean dirty = false;
        for (int[] l : new ArrayList<int[]>(t.links)) {
            if (kindOf(w, x, y, z, l[3]) != kind) continue; // провод другого типа в этой же ячейке
            if (Wires.badY(w, l[1])) continue;
            if (!w.hasChunkAt(new BlockPos(l[0], l[1], l[2]))) continue; // другой конец не загружен: связь «спит»
            boolean ok = kindOf(w, l[0], l[1], l[2], l[4]) == kind;
            if (ok) {
                TileConnector o = tile(w, l[0], l[1], l[2]);
                ok = o != null && !o.isRemoved() && o.has(x, y, z, l[4], l[3]);
            }
            if (ok) {
                boolean dup = false;
                for (int[] p : out) if (p[0] == l[0] && p[1] == l[1] && p[2] == l[2]) dup = true;
                if (!dup) out.add(new int[]{l[0], l[1], l[2]});
            } else if (!w.isClientSide) {
                t.remove(l[0], l[1], l[2], l[3], l[4]);
                dirty = true;
            }
        }
        if (dirty) {
            t.changed();
            prune(w, x, y, z);
        }
        return out;
    }

    // ===================== опора и размещение пинов =====================

    /** Держит ли блок за гранью m от ячейки (x, y, z) провод: твёрдая грань или устройство мода. */
    public static boolean supportOk(BlockGetter w, int x, int y, int z, int m) {
        int sx = x + Net.DX[m], sy = y + Net.DY[m], sz = z + Net.DZ[m];
        if (Wires.badY(w, sy)) return false;
        BlockPos sp = new BlockPos(sx, sy, sz);
        BlockState ss = w.getBlockState(sp);
        Block sb = ss.getBlock();
        if (Ports.isDevice(sb)) return true;
        return ss.isFaceSturdy(w, sp, Direction.from3DDataValue(m ^ 1));
    }

    /** Причина, по которой пин с кодом code нельзя поставить в ячейку (cx, cy, cz), или null. */
    private static String hostError(Level w, int kind, int cx, int cy, int cz, int code) {
        if (Wires.badY(w, cy)) return "the wire point would be outside the world";
        if (!supportOk(w, cx, cy, cz, dirOf(code))) return "this face cannot hold a wire (it is not solid)";
        Block cb = w.getBlockState(new BlockPos(cx, cy, cz)).getBlock();
        if (cb instanceof BlockConnector) {
            // ячейка с пинами общая для сетевых и видеопроводов; занята только стойка и тот же самый пиксель
            if (!isPinCell(w, cx, cy, cz)) return "this spot is taken by a connector post";
            TileConnector tc = tile(w, cx, cy, cz);
            if (tc != null) {
                for (int c : tc.pinCodes()) {
                    if ((c & POS_MASK) == (code & POS_MASK) && kindOf(w, cx, cy, cz, c) != kind) {
                        return "this pixel already holds a " + Ports.name(kindOf(w, cx, cy, cz, c)) + " wire";
                    }
                }
            }
            return null;
        }
        BlockState cbs = w.getBlockState(new BlockPos(cx, cy, cz));
        if (cbs.isAir() || cbs.canBeReplaced()) return null;
        return "there is no room for a wire point in front of this face";
    }

    // ===================== выбор конца провода =====================

    /** Конец провода: существующая точка (стойка или пин) или ещё не созданный пин на грани. */
    public static final class Endpoint {
        /** Ячейка с точкой (для нового пина - ячейка перед гранью). */
        public int x, y, z;
        /** Код точки: {@link #TIP} или код пина. */
        public int code;
        /** true: точка уже есть в мире; false: пин будет создан при соединении. */
        public boolean existing;
        public String error;
        /** Мировая точка крепления. */
        public double[] pos;
    }

    private static Endpoint fail(String msg) {
        Endpoint e = new Endpoint();
        e.error = msg;
        return e;
    }

    /**
     * Что получится, если кликнуть проводом типа kind по блоку (x, y, z), грани side, долям (hx, hy, hz).
     * Общая функция сервера (ItemWire) и клиента (предпросмотр), поэтому не меняет мир.
     */
    public static Endpoint resolve(Level w, int kind, int x, int y, int z, int side, float hx, float hy, float hz) {
        Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
        if (b instanceof BlockConnector) {
            BlockConnector bc = (BlockConnector) b;
            Endpoint e = new Endpoint();
            e.x = x;
            e.y = y;
            e.z = z;
            e.existing = true;
            if (!isPinCell(w, x, y, z)) {
                if (bc.kind() != kind) {
                    return fail("this is a " + Ports.name(bc.kind()) + " connector, the wire does not fit");
                }
                e.code = TIP;
                e.pos = tip(w, x, y, z);
                return e;
            }
            TileConnector t = tile(w, x, y, z);
            List<Integer> all = t == null ? new ArrayList<Integer>() : t.pinCodes();
            if (all.isEmpty()) return fail("there is no wire point here");
            List<Integer> pins = new ArrayList<Integer>();
            for (int c : all) if (kindOf(w, x, y, z, c) == kind) pins.add(c);
            if (pins.isEmpty()) {
                return fail("this is a " + Ports.name(kindOf(w, x, y, z, all.get(0))) + " wire point, the wire does not fit");
            }
            double qx = x + hx, qy = y + hy, qz = z + hz, best = 1e18;
            for (int code : pins) {
                double[] p = pinPoint(x, y, z, code);
                double d = (p[0] - qx) * (p[0] - qx) + (p[1] - qy) * (p[1] - qy) + (p[2] - qz) * (p[2] - qz);
                if (d < best) {
                    best = d;
                    e.code = code;
                }
            }
            e.pos = pinPoint(x, y, z, e.code);
            return e;
        }
        if (side < 0 || side > 5) return fail("click on a block face");
        int cx = x + Net.DX[side], cy = y + Net.DY[side], cz = z + Net.DZ[side];
        int[] pxl = pixel(side, hx, hy, hz);
        int code = code(side ^ 1, pxl[0], pxl[1], kind);
        String err = hostError(w, kind, cx, cy, cz, code);
        if (err != null) return fail(err);
        Endpoint e = new Endpoint();
        e.x = cx;
        e.y = cy;
        e.z = cz;
        e.code = code;
        TileConnector t = tile(w, cx, cy, cz);
        e.existing = t != null && isPinCell(w, cx, cy, cz) && t.countAt(code) > 0 && kindOf(w, cx, cy, cz, code) == kind;
        e.pos = pinPoint(cx, cy, cz, code);
        return e;
    }

    /** Записать конец провода в NBT предмета (первая точка). */
    public static void toTag(CompoundTag tg, Level w, Endpoint e) {
        tg.putBoolean("has", true);
        tg.putString("dim", w.dimension().location().toString());
        tg.putInt("x", e.x);
        tg.putInt("y", e.y);
        tg.putInt("z", e.z);
        tg.putInt("code", e.code);
        tg.putBoolean("pend", !e.existing);
    }

    /** Прочитать выбранную первую точку из NBT предмета; null, если её нет или она пропала. */
    public static Endpoint fromTag(Level w, CompoundTag tg, int kind) {
        if (tg == null || !tg.getBoolean("has")) return null;
        if (!tg.getString("dim").equals(w.dimension().location().toString())) return null;
        Endpoint e = new Endpoint();
        e.x = tg.getInt("x");
        e.y = tg.getInt("y");
        e.z = tg.getInt("z");
        e.code = tg.getInt("code");
        boolean pend = tg.getBoolean("pend");
        e.existing = !pend;
        if (e.y < 0 || e.y > 255 || !w.hasChunkAt(new BlockPos(e.x, e.y, e.z))) return null;
        if (pend) {
            if (e.code < 0 || hostError(w, kind, e.x, e.y, e.z, e.code) != null) return null;
            e.pos = pinPoint(e.x, e.y, e.z, e.code);
            return e;
        }
        Block b = w.getBlockState(new BlockPos(e.x, e.y, e.z)).getBlock();
        if (!(b instanceof BlockConnector)) return null;
        TileConnector t = tile(w, e.x, e.y, e.z);
        if (t == null) return null;
        if (e.code < 0) {
            if (isPinCell(w, e.x, e.y, e.z) || ((BlockConnector) b).kind() != kind) return null;
        } else if (!isPinCell(w, e.x, e.y, e.z) || t.countAt(e.code) == 0 || kindOf(w, e.x, e.y, e.z, e.code) != kind) {
            return null;
        }
        e.pos = anchor(w, e.x, e.y, e.z, e.code);
        return e;
    }

    public static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Можно ли соединить два конца проводом типа kind: null, если да, иначе причина. Мир не меняет. */
    public static String check(Level w, int kind, Endpoint a, Endpoint b) {
        if (a.x == b.x && a.y == b.y && a.z == b.z && a.code == b.code) return "that is the same point";
        TileConnector ta = a.existing ? tile(w, a.x, a.y, a.z) : null;
        TileConnector tb = b.existing ? tile(w, b.x, b.y, b.z) : null;
        if (a.existing && ta == null) return "the first point is gone";
        if (b.existing && tb == null) return "the second point is gone";
        if (ta != null && ta.countAt(a.code) >= MAX_LINKS) return "a wire point holds at most " + MAX_LINKS + " wires";
        if (tb != null && tb.countAt(b.code) >= MAX_LINKS) return "a wire point holds at most " + MAX_LINKS + " wires";
        if (ta != null && ta.has(b.x, b.y, b.z, a.code, b.code)) return "these points are already joined";
        double len = distance(a.pos, b.pos);
        if (len > maxLen(kind)) {
            return "the wire is too long (" + (int) Math.ceil(len) + " blocks, the limit is " + maxLen(kind) + ")";
        }
        return null;
    }

    // ===================== соединение и разрыв =====================

    private static Item wireItem(int kind) {
        return kind == Ports.VIDEO ? ModItems.WIRE_VIDEO.get() : ModItems.WIRE_NET.get();
    }

    private static void drop(Level w, double x, double y, double z, int kind) {
        Item it = wireItem(kind);
        if (it == null) return;
        ItemEntity e = new ItemEntity(w, x, y, z, new ItemStack(it));
        e.setPickUpDelay(10);
        w.addFreshEntity(e);
    }

    /** Блок-коннектор конца: существующий или только что созданная ячейка с пином. */
    private static TileConnector materialize(Level w, int kind, Endpoint e) {
        if (!(w.getBlockState(new BlockPos(e.x, e.y, e.z)).getBlock() instanceof BlockConnector)) {
            // ячейка с пинами общая для обоих типов проводов, тип каждого пина записан в его коде
            BlockPos cell = new BlockPos(e.x, e.y, e.z);
            if (!w.setBlock(cell, BlockConnector.pinState(ModBlocks.CABLE.get()), 3)) return null;
            if (!(w.getBlockEntity(cell) instanceof TileConnector)) {
                w.setBlockEntity(new TileConnector(cell, w.getBlockState(cell)));
            }
        }
        return tile(w, e.x, e.y, e.z);
    }

    /** Соединить два конца проводом (создаёт нужные пины). Возвращает null, если получилось, иначе причину отказа. */
    public static String link(Level w, int kind, Endpoint a, Endpoint b) {
        String err = check(w, kind, a, b);
        if (err != null) return err;
        if (!a.existing) {
            err = hostError(w, kind, a.x, a.y, a.z, a.code);
            if (err != null) return err;
        }
        if (!b.existing) {
            err = hostError(w, kind, b.x, b.y, b.z, b.code);
            if (err != null) return err;
        }
        TileConnector ta = materialize(w, kind, a);
        TileConnector tb = materialize(w, kind, b);
        if (ta == null || tb == null) {
            prune(w, a.x, a.y, a.z);
            prune(w, b.x, b.y, b.z);
            return "could not place the wire point";
        }
        ta.add(b.x, b.y, b.z, a.code, b.code);
        tb.add(a.x, a.y, a.z, b.code, a.code);
        ta.changed();
        tb.changed();
        return null;
    }

    /** Убрать ячейку с пинами, если в ней не осталось проводов. */
    public static void prune(Level w, int x, int y, int z) {
        if (w.isClientSide || !isPinCell(w, x, y, z)) return;
        TileConnector t = tile(w, x, y, z);
        if (t == null || t.links.isEmpty()) w.removeBlock(new BlockPos(x, y, z), false);
    }

    /** Убрать провод l (запись из a.links) с обоих концов; при drop провод выпадает предметом в точке (dx, dy, dz). */
    public static void cut(Level w, TileConnector a, int[] l, boolean drop, double dx, double dy, double dz) {
        int ox = l[0], oy = l[1], oz = l[2], mine = l[3], theirs = l[4];
        int kind = kindOf(w, a.getBlockPos().getX(), a.getBlockPos().getY(), a.getBlockPos().getZ(), mine);
        a.remove(ox, oy, oz, mine, theirs);
        a.changed();
        TileConnector o = null;
        if (oy >= 0 && oy <= 255 && w.hasChunkAt(new BlockPos(ox, oy, oz))) {
            o = tile(w, ox, oy, oz);
            if (o != null) {
                o.remove(a.getBlockPos().getX(), a.getBlockPos().getY(), a.getBlockPos().getZ(), theirs, mine);
                o.changed();
            }
        }
        if (drop && kind != Ports.NONE) drop(w, dx, dy, dz, kind);
        prune(w, a.getBlockPos().getX(), a.getBlockPos().getY(), a.getBlockPos().getZ());
        if (o != null && o != a) prune(w, ox, oy, oz);
    }

    /**
     * Убрать все провода блока (его ломают). Сам блок здесь не удаляется (иначе рекурсия в breakBlock): ячейки-пины на
     * другом конце, оставшиеся без проводов, убираются.
     */
    public static void removeAll(Level w, TileConnector t, boolean drop) {
        for (int[] l : new ArrayList<int[]>(t.links)) {
            int kind = kindOf(w, t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ(), l[3]);
            t.remove(l[0], l[1], l[2], l[3], l[4]);
            TileConnector o = null;
            if (l[1] >= 0 && l[1] <= 255 && w.hasChunkAt(new BlockPos(l[0], l[1], l[2]))) {
                o = tile(w, l[0], l[1], l[2]);
                if (o != null) {
                    o.remove(t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ(), l[4], l[3]);
                    o.changed();
                }
            }
            if (drop && kind != Ports.NONE) drop(w, t.getBlockPos().getX() + 0.5D, t.getBlockPos().getY() + 0.5D, t.getBlockPos().getZ() + 0.5D, kind);
            if (o != null && o != t) prune(w, l[0], l[1], l[2]);
        }
        t.changed();
    }

    /** Провода пинов, у которых пропала опора (соседний блок убрали), выпадают предметами; пустая ячейка исчезает. */
    public static void dropUnsupported(Level w, TileConnector t) {
        for (int[] l : new ArrayList<int[]>(t.links)) {
            if (l[3] >= 0 && !supportOk(w, t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ(), dirOf(l[3]))) {
                cut(w, t, l, true, t.getBlockPos().getX() + 0.5D, t.getBlockPos().getY() + 0.5D, t.getBlockPos().getZ() + 0.5D);
            }
        }
        prune(w, t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ());
    }

    // ===================== выбор провода курсором =====================

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double clamp(double v) {
        return v < 0.0D ? 0.0D : v > 1.0D ? 1.0D : v;
    }

    /** Расстояние между отрезком луча (p1..q1) и отрезком провода (p2..q2); в res[0] - доля пути по лучу. */
    private static double segDist(double[] p1, double[] q1, double[] p2, double[] q2, double[] res) {
        double[] d1 = {q1[0] - p1[0], q1[1] - p1[1], q1[2] - p1[2]};
        double[] d2 = {q2[0] - p2[0], q2[1] - p2[1], q2[2] - p2[2]};
        double[] r = {p1[0] - p2[0], p1[1] - p2[1], p1[2] - p2[2]};
        double a = dot(d1, d1), e = dot(d2, d2), f = dot(d2, r);
        double s, t;
        if (a <= 1e-9D && e <= 1e-9D) {
            s = 0.0D;
            t = 0.0D;
        } else if (a <= 1e-9D) {
            s = 0.0D;
            t = clamp(f / e);
        } else {
            double c = dot(d1, r);
            if (e <= 1e-9D) {
                t = 0.0D;
                s = clamp(-c / a);
            } else {
                double b = dot(d1, d2);
                double denom = a * e - b * b;
                s = denom > 1e-9D ? clamp((b * f - c * e) / denom) : 0.0D;
                t = (b * s + f) / e;
                if (t < 0.0D) {
                    t = 0.0D;
                    s = clamp(-c / a);
                } else if (t > 1.0D) {
                    t = 1.0D;
                    s = clamp((b - c) / a);
                }
            }
        }
        double[] c1 = {p1[0] + d1[0] * s, p1[1] + d1[1] * s, p1[2] + d1[2] * s};
        double[] c2 = {p2[0] + d2[0] * t, p2[1] + d2[1] * t, p2[2] + d2[2] * t};
        res[0] = s;
        res[1] = c2[0];
        res[2] = c2[1];
        res[3] = c2[2];
        double dx = c1[0] - c2[0], dy = c1[1] - c2[1], dz = c1[2] - c2[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void say(Player p, String m) {
        p.displayClientMessage(Component.literal(m), false);
    }

    /** Кусачки: перерезать провод, на который смотрит игрок (ближайший к лучу взгляда, не дальше {@link #REACH} блоков). */
    public static void cutLooking(Level w, Player p) {
        Vec3 look = p.getLookAngle();
        double[] eye = {p.getX(), p.getEyeY(), p.getZ()};
        double[] end = {eye[0] + look.x * REACH, eye[1] + look.y * REACH, eye[2] + look.z * REACH};
        double best = PICK;
        TileConnector bt = null;
        int[] bl = null;
        double[] bp = null;
        double[] res = new double[4];
        // в 1.20 нет loadedTileEntityList: берём BlockEntity чанков в радиусе 5 чанков вокруг игрока
        List<TileConnector> all = new ArrayList<TileConnector>();
        int pcx = ((int) Math.floor(eye[0])) >> 4, pcz = ((int) Math.floor(eye[2])) >> 4;
        for (int cdx = -5; cdx <= 5; cdx++) {
            for (int cdz = -5; cdz <= 5; cdz++) {
                LevelChunk ch = w.getChunkSource().getChunkNow(pcx + cdx, pcz + cdz);
                if (ch == null) continue;
                for (BlockEntity be : new ArrayList<BlockEntity>(ch.getBlockEntities().values())) {
                    if (be instanceof TileConnector) all.add((TileConnector) be);
                }
            }
        }
        for (TileConnector t : all) {
            if (t.isRemoved() || t.links.isEmpty()) continue;
            double mx = t.getBlockPos().getX() - eye[0], my = t.getBlockPos().getY() - eye[1], mz = t.getBlockPos().getZ() - eye[2];
            if (mx * mx + my * my + mz * mz > 70.0D * 70.0D) continue;
            for (int[] l : t.links) {
                if (!owns(t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ(), l[3], l[0], l[1], l[2], l[4])) continue;
                if (Wires.badY(w, l[1]) || !w.hasChunkAt(new BlockPos(l[0], l[1], l[2]))) continue;
                int k1 = kindOf(w, t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ(), l[3]);
                if (k1 == Ports.NONE || kindOf(w, l[0], l[1], l[2], l[4]) != k1) continue;
                double[][] c = curve(anchor(w, t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ(), l[3]), anchor(w, l[0], l[1], l[2], l[4]));
                for (int i = 0; i + 1 < c.length; i++) {
                    double d = segDist(eye, end, c[i], c[i + 1], res);
                    if (d < best) {
                        best = d;
                        bt = t;
                        bl = l;
                        bp = new double[]{res[1], res[2], res[3]};
                    }
                }
            }
        }
        if (bt == null) return;
        cut(w, bt, bl, !p.getAbilities().instabuild, bp[0], bp[1], bp[2]);
        say(p, "Wire cut.");
    }

    /** Описание коннектора для сообщения игроку. */
    public static String describe(Level w, int x, int y, int z) {
        Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
        if (!(b instanceof BlockConnector)) return "";
        if (isPinCell(w, x, y, z)) {
            TileConnector t = tile(w, x, y, z);
            StringBuilder sb = new StringBuilder("Wire point(s): ");
            int pins = t == null ? 0 : t.pinCodes().size();
            sb.append(pins).append(" pin(s), ").append(t == null ? 0 : t.links.size()).append(" wire(s)");
            for (int kind = Ports.NET; kind <= Ports.VIDEO; kind++) {
                int[] tg = plugTarget(w, x, y, z, kind);
                if (tg != null) {
                    sb.append("; ").append(Ports.name(kind)).append(" wire plugged into the ")
                            .append(Ports.name(kind)).append(" port of ")
                            .append(w.getBlockState(new BlockPos(tg[0], tg[1], tg[2])).getBlock().getName().getString());
                }
            }
            return sb.append(".").toString();
        }
        int kind = kindAt(w, x, y, z);
        String name = kind == Ports.NET ? "Network" : "Video";
        int[] t = plugTarget(w, x, y, z, kind);
        String where = t != null ? "plugged into the " + Ports.name(kind) + " port of "
                + w.getBlockState(new BlockPos(t[0], t[1], t[2])).getBlock().getName().getString()
                : "on a " + DIRN[supportDir(w, x, y, z)] + " support, no device port";
        TileConnector tc = tile(w, x, y, z);
        return name + " connector: " + where + "; wires " + (tc == null ? 0 : tc.countAt(TIP)) + "/" + MAX_LINKS + ".";
    }
}
