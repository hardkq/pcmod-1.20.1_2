package ru.pcmod.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.block.Block;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Сеть мода. Хабы — сетевые коннекторы и роутеры. Коннекторы соединяются проводами (см. {@link Wires}) на любое
 * расстояние в пределах длины провода и втыкаются в сетевые порты устройств; роутеры и мосты вплотную друг к другу
 * соединены напрямую.
 * ПК подключается по проводу (коннектор в сетевом порту корпуса, либо роутер вплотную) либо по Wi-Fi
 * (модуль Wi-Fi в ПК + SSID/пароль, сохранённые в ОС на накопителе).
 * Серверы и фермы подключаются к хабам через порт (коннектор) или вплотную (роутер) и видны всем ПК этой сети.
 */
public final class Net {
    public static final int OK = 0, OUT = 1, BAD_PASS = 2, NO_MODULE = 3;
    static final int MAX_HUBS = 512;
    /** Направления в порядке сторон блока: низ, верх, север, юг, запад, восток. */
    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private static final List<TileRouter> ROUTERS = new CopyOnWriteArrayList<TileRouter>();

    private Net() { }

    /** Точка доступа Wi-Fi в зоне действия. */
    public static class Ap {
        public String ssid = "";
        public int signal;
        public boolean secured;
        public TileRouter router;
    }

    // ===================== реестр роутеров =====================
    public static void register(TileRouter r) {
        if (!ROUTERS.contains(r)) ROUTERS.add(r);
    }

    public static void unregister(TileRouter r) {
        ROUTERS.remove(r);
    }

    public static void clear() {
        ROUTERS.clear();
    }

    // ===================== проверка вводимых данных =====================
    public static boolean validSsid(String s) {
        return s != null && s.matches("[A-Za-z0-9_-]{1,16}");
    }

    public static boolean validPass(String s) {
        return s != null && s.matches("[\\x21-\\x7E]{1,16}");
    }

    public static boolean validDomain(String s) {
        return s != null && s.matches("[a-z0-9][a-z0-9.-]{0,19}");
    }

    // ===================== проводная сеть =====================
    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    static boolean isHub(Level w, int x, int y, int z) {
        if (Wires.badY(w, y) || !w.hasChunkAt(new BlockPos(x, y, z))) return false;
        Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
        return Wires.isConnectorFor(w, x, y, z, Ports.NET) || b instanceof BlockRouter || b instanceof BlockFiber;
    }

    /**
     * Второй конец оптоволоконного моста в точке (x, y, z) или null: моста нет, он не связан, второй конец
     * не загружен или не отвечает взаимностью. Так сеть «перепрыгивает» через любое расстояние.
     */
    public static int[] fiberPartner(Level w, int x, int y, int z) {
        if (Wires.badY(w, y) || !w.hasChunkAt(new BlockPos(x, y, z))) return null;
        BlockEntity te = w.getBlockEntity(new BlockPos(x, y, z));
        if (!(te instanceof TileFiber) || te.isRemoved()) return null;
        TileFiber f = (TileFiber) te;
        if (!f.linked || !isHub(w, f.px, f.py, f.pz)) return null;
        BlockEntity o = w.getBlockEntity(new BlockPos(f.px, f.py, f.pz));
        if (!(o instanceof TileFiber)) return null;
        TileFiber of = (TileFiber) o;
        if (of.isRemoved() || !of.linked || of.px != x || of.py != y || of.pz != z) return null;
        return new int[]{f.px, f.py, f.pz};
    }

    /**
     * Хабы, непосредственно связанные с хабом в (x, y, z). Коннектор связан с коннекторами на других концах проводов
     * и с роутером/мостом, в порт которого воткнут. Роутер или мост связан с коннекторами, воткнутыми в его порты,
     * а роутеры и мосты, стоящие вплотную друг к другу, соединены напрямую, как и раньше.
     */
    static void neighbors(Level w, int x, int y, int z, List<int[]> out) {
        if (Wires.isConnectorFor(w, x, y, z, Ports.NET)) {
            for (int[] p : Wires.partnerPositions(w, x, y, z, Ports.NET)) {
                if (isHub(w, p[0], p[1], p[2])) out.add(p);
            }
            for (int[] t : Wires.plugTargets(w, x, y, z, Ports.NET)) {
                if (isHub(w, t[0], t[1], t[2])) out.add(t);
            }
            return;
        }
        for (int d = 0; d < 6; d++) {
            int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
            if (!isHub(w, nx, ny, nz)) continue;
            if (Wires.isConnectorFor(w, nx, ny, nz, Ports.NET)) {
                if (Wires.plugsInto(w, nx, ny, nz, x, y, z, Ports.NET)) out.add(new int[]{nx, ny, nz});
            } else {
                out.add(new int[]{nx, ny, nz});
            }
        }
    }

    /**
     * Дотягивается ли хаб в (x, y, z) до устройства (ПК, сервер, ферма) в направлении d: коннектор - только если он
     * воткнут в порт этого устройства, роутер или мост - вплотную, как и раньше.
     */
    static boolean attached(Level w, int x, int y, int z, int d) {
        if (Wires.isConnectorFor(w, x, y, z, Ports.NET)) return Wires.plugged(w, x, y, z, d, Ports.NET);
        return true;
    }

    /** Все хабы, связанные с начальными (поиск в ширину, не больше MAX_HUBS). */
    static List<int[]> bfs(Level w, List<int[]> seeds) {
        List<int[]> out = new ArrayList<int[]>();
        HashSet<Long> seen = new HashSet<Long>();
        ArrayDeque<int[]> q = new ArrayDeque<int[]>();
        for (int[] s : seeds) {
            if (seen.add(key(s[0], s[1], s[2]))) {
                q.add(s);
                out.add(s);
            }
        }
        List<int[]> near = new ArrayList<int[]>();
        while (!q.isEmpty() && out.size() < MAX_HUBS) {
            int[] c = q.poll();
            near.clear();
            neighbors(w, c[0], c[1], c[2], near);
            for (int[] n : near) {
                if (!seen.add(key(n[0], n[1], n[2]))) continue;
                q.add(n);
                out.add(n);
            }
            int[] far = fiberPartner(w, c[0], c[1], c[2]); // дальняя связь через оптоволоконный мост
            if (far != null && seen.add(key(far[0], far[1], far[2]))) {
                q.add(far);
                out.add(far);
            }
        }
        return out;
    }

    static List<int[]> hubsAroundPc(Level w, TileComputer pc) {
        List<int[]> seeds = new ArrayList<int[]>();
        for (int d = 0; d < 6; d++) {
            int x = pc.getBlockPos().getX() + DX[d], y = pc.getBlockPos().getY() + DY[d], z = pc.getBlockPos().getZ() + DZ[d];
            if (isHub(w, x, y, z) && attached(w, x, y, z, d ^ 1)) seeds.add(new int[]{x, y, z});
        }
        return seeds.isEmpty() ? seeds : bfs(w, seeds);
    }

    static List<int[]> hubsOfRouter(Level w, TileRouter r) {
        List<int[]> seeds = new ArrayList<int[]>();
        seeds.add(new int[]{r.getBlockPos().getX(), r.getBlockPos().getY(), r.getBlockPos().getZ()});
        return bfs(w, seeds);
    }

    static TileRouter firstRouter(Level w, List<int[]> hubs) {
        for (int[] h : hubs) {
            BlockEntity te = w.getBlockEntity(new BlockPos(h[0], h[1], h[2]));
            if (te instanceof TileRouter && !te.isRemoved()) return (TileRouter) te;
        }
        return null;
    }

    /** Подключён ли блок (например, майнер-ферма) к сети с роутером: сосед-хаб и роутер в той же сети. */
    public static boolean onlineAt(Level w, int x, int y, int z) {
        List<int[]> seeds = new ArrayList<int[]>();
        for (int d = 0; d < 6; d++) {
            int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
            if (isHub(w, nx, ny, nz) && attached(w, nx, ny, nz, d ^ 1)) seeds.add(new int[]{nx, ny, nz});
        }
        if (seeds.isEmpty()) return false;
        return firstRouter(w, bfs(w, seeds)) != null;
    }

    static void collectServers(Level w, List<int[]> hubs, List<TileServer> out) {
        for (int[] h : hubs) {
            for (int d = 0; d < 6; d++) {
                int x = h[0] + DX[d], y = h[1] + DY[d], z = h[2] + DZ[d];
                if (Wires.badY(w, y) || !w.hasChunkAt(new BlockPos(x, y, z))) continue;
                if (!attached(w, h[0], h[1], h[2], d)) continue;
                BlockEntity te = w.getBlockEntity(new BlockPos(x, y, z));
                if (te instanceof TileServer && !te.isRemoved() && !out.contains(te)) out.add((TileServer) te);
            }
        }
    }

    // ===================== общая сеть двух ПК =====================
    /** Ключи всех хабов (кабели/роутеры) сети, в которой работает ПК с данным подключением. */
    static HashSet<Long> networkKeys(Level w, TileComputer pc, NetLink k) {
        HashSet<Long> out = new HashSet<Long>();
        if (k == null || !k.up() || k.router == null) return out;
        List<int[]> hubs = k.type == NetLink.WIRED ? hubsAroundPc(w, pc) : hubsOfRouter(w, k.router);
        for (int[] h : hubs) out.add(key(h[0], h[1], h[2]));
        return out;
    }

    /**
     * Достижим ли ПК b из ПК a по сети: оба в одном мире, оба с работающим подключением, и их
     * хабы (кабели/роутеры, в том числе через Wi-Fi-роутер) входят в одну проводную сеть.
     */
    public static boolean sameNetwork(TileComputer a, NetLink ka, TileComputer b, NetLink kb) {
        if (a == b || a.getLevel() == null || a.getLevel() != b.getLevel()) return false;
        if (ka == null || kb == null || !ka.up() || !kb.up()) return false;
        HashSet<Long> sa = networkKeys(a.getLevel(), a, ka);
        if (sa.isEmpty()) return false;
        for (Long k : networkKeys(b.getLevel(), b, kb)) {
            if (sa.contains(k)) return true;
        }
        return false;
    }

    // ===================== Wi-Fi =====================
    static double dist(TileRouter r, TileComputer pc) {
        double dx = r.getBlockPos().getX() - pc.getBlockPos().getX(), dy = r.getBlockPos().getY() - pc.getBlockPos().getY(), dz = r.getBlockPos().getZ() - pc.getBlockPos().getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static boolean inRange(TileRouter r, TileComputer pc) {
        return !r.isRemoved() && r.wifiOn && r.getLevel() == pc.getLevel() && dist(r, pc) <= r.range();
    }

    static int signal(double d, int range) {
        double q = d / Math.max(1, range);
        return q <= 0.25 ? 4 : q <= 0.5 ? 3 : q <= 0.75 ? 2 : 1;
    }

    /** Сети Wi-Fi в зоне действия (по одной на SSID, самая сильная), сильные первыми. */
    public static List<Ap> scan(TileComputer pc) {
        List<Ap> out = new ArrayList<Ap>();
        if (pc.spec(TileComputer.NET) == null || pc.getLevel() == null) return out;
        Map<String, Ap> best = new HashMap<String, Ap>();
        for (TileRouter r : ROUTERS) {
            if (!inRange(r, pc) || r.ssid.length() == 0) continue;
            int sig = signal(dist(r, pc), r.range());
            Ap old = best.get(r.ssid);
            if (old == null || sig > old.signal) {
                Ap a = new Ap();
                a.ssid = r.ssid;
                a.signal = sig;
                a.secured = r.pass.length() > 0;
                a.router = r;
                best.put(r.ssid, a);
            }
        }
        out.addAll(best.values());
        Collections.sort(out, new Comparator<Ap>() {
            @Override
            public int compare(Ap a, Ap b) {
                if (a.signal != b.signal) return b.signal - a.signal;
                return a.ssid.compareTo(b.ssid);
            }
        });
        return out;
    }

    /** Подходящий роутер (ближайший с этим SSID, в зоне действия, пароль совпал) или null. */
    static TileRouter bestRouter(TileComputer pc, String ssid, String pass) {
        TileRouter best = null;
        double bd = 1e18;
        for (TileRouter r : ROUTERS) {
            if (!inRange(r, pc) || !r.ssid.equals(ssid)) continue;
            if (r.pass.length() > 0 && !r.pass.equals(pass)) continue;
            double d = dist(r, pc);
            if (d < bd) {
                bd = d;
                best = r;
            }
        }
        return best;
    }

    /** OK / OUT (сеть не найдена или далеко) / BAD_PASS / NO_MODULE. */
    public static int check(TileComputer pc, String ssid, String pass) {
        if (pc.spec(TileComputer.NET) == null) return NO_MODULE;
        boolean found = false;
        for (TileRouter r : ROUTERS) {
            if (!inRange(r, pc) || !r.ssid.equals(ssid)) continue;
            found = true;
            if (r.pass.length() == 0 || r.pass.equals(pass)) return OK;
        }
        return found ? BAD_PASS : OUT;
    }

    // ===================== итоговое подключение =====================
    /** Считает текущее подключение ПК. root — корень ОС на накопителе (там лежат настройки Wi-Fi). */
    public static NetLink resolve(TileComputer pc, CompoundTag root) {
        NetLink k = new NetLink();
        Level w = pc.getLevel();
        if (w == null) return k;

        // 1) провод имеет приоритет
        List<int[]> hubs = hubsAroundPc(w, pc);
        boolean cable = !hubs.isEmpty();
        if (cable) {
            TileRouter r = firstRouter(w, hubs);
            if (r != null) {
                k.type = NetLink.WIRED;
                k.router = r;
                collectServers(w, hubs, k.servers);
                return k;
            }
            k.status = "Cable has no router";
        }

        // 2) Wi-Fi
        if (pc.spec(TileComputer.NET) == null) {
            if (!cable) k.status = "No cable, no Wi-Fi module";
            return k;
        }
        String ssid = root == null ? "" : root.getString("wifi_ssid");
        String pass = root == null ? "" : root.getString("wifi_pass");
        if (ssid.length() == 0) {
            if (!cable) k.status = "Wi-Fi: not connected";
            return k;
        }
        TileRouter r = bestRouter(pc, ssid, pass);
        if (r == null) {
            int c = check(pc, ssid, pass);
            k.status = c == BAD_PASS ? "Wi-Fi: wrong password" : "Wi-Fi: " + ssid + " out of range";
            return k;
        }
        k.type = NetLink.WIFI;
        k.ssid = ssid;
        k.router = r;
        k.signal = signal(dist(r, pc), r.range());
        collectServers(w, hubsOfRouter(w, r), k.servers);
        return k;
    }
}
