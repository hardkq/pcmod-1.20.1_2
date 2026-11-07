package ru.pcmod.common;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Порты устройств (с 0.15.0). Порт - гнездо в центре грани устройства, в которое втыкается коннектор
 * ({@link BlockConnector}); провода ({@link ItemWire}) тянутся уже от коннектора к коннектору.
 * Сетевой коннектор встаёт только на сетевой порт, видеоконнектор - только на видеопорт.
 *
 * Грани: 0 низ, 1 верх, 2 север, 3 юг, 4 запад, 5 восток. «Лицо» устройства - грань FACING (2..5).
 * Раскладка портов (относительно лица устройства):
 * <pre>
 *   Корпус ПК        : NET и VIDEO - на задней панели, каждый порт - ровно ОДИН пиксель (см. {@link CaseShape#port})
 *   Монитор          : VIDEO - сзади, один пиксель в центре задней стенки
 *   Роутер, Оптомост : NET - сзади, слева и справа
 *   Сервер, Майнер   : NET - сзади
 * </pre>
 * Вместо метадаты 1.12.2 методы принимают BlockState (лицо = BlockAt.facingIndex).
 */
public final class Ports {
    public static final int NONE = 0, NET = 1, VIDEO = 2;
    /** Пиксель видеопорта на задней стенке монитора (локальные ix, iy): центр. */
    public static final int[] MONITOR_PORT = {8, 8};

    private Ports() {
    }

    /** Грань-лицо по номеру стороны (2..5, иначе 3). */
    public static int front(int meta) {
        return meta < 2 || meta > 5 ? 3 : meta;
    }

    /** Грань-лицо устройства по его состоянию. */
    public static int front(BlockState s) {
        return front(BlockAt.facingIndex(s));
    }

    /** Грань «справа», если смотреть на лицо блока, повёрнутого к грани f. */
    public static int right(int f) {
        switch (f) {
            case 2: return 4;
            case 3: return 5;
            case 4: return 3;
            default: return 2;
        }
    }

    /** Грань «слева», если смотреть на лицо блока, повёрнутого к грани f. */
    public static int left(int f) {
        return right(f) ^ 1;
    }

    /** Блок, у которого вообще бывают порты (устройство мода). */
    public static boolean isDevice(Block b) {
        return b instanceof BlockComputer || b instanceof BlockMonitor || b instanceof BlockRouter
                || b instanceof BlockFiber || b instanceof BlockServer || b instanceof BlockMiner;
    }

    /** Тип порта на грани face устройства s (NONE, если порта нет). */
    public static int type(BlockState s, int face) {
        Block b = s == null ? null : s.getBlock();
        if (face < 2 || face > 5 || b == null) return NONE;
        int f = front(s);
        int back = f ^ 1;
        if (b instanceof BlockComputer) return NONE; // порты корпуса - отдельные пиксели, см. pinFits
        if (b instanceof BlockMonitor) return face == back ? VIDEO : NONE;
        if (b instanceof BlockRouter || b instanceof BlockFiber) {
            return face == back || face == left(f) || face == right(f) ? NET : NONE;
        }
        if (b instanceof BlockServer || b instanceof BlockMiner) return face == back ? NET : NONE;
        return NONE;
    }

    /** Можно ли поставить на грань face устройства s коннектор-стойку вида kind (стойка 6x6 пикселей - только в центр порта-пластины). */
    public static boolean postFits(BlockState s, int face, int kind) {
        if (s.getBlock() instanceof BlockMonitor) return false;
        return type(s, face) == kind;
    }

    /**
     * Попадает ли пин провода вида kind на пикселе (u, v) грани face устройства s в порт. Корпус ПК: пиксель должен совпасть
     * с пикселем порта на задней панели; остальные устройства: пиксель в центральной пластине 8x8.
     */
    public static boolean pinFits(BlockState s, int face, int u, int v, int kind) {
        Block b = s.getBlock();
        if (b instanceof BlockComputer) {
            if (face < 2 || face > 5 || face != (front(s) ^ 1)) return false;
            int[] p = ((BlockComputer) b).shape().port(kind);
            if (p == null) return false;
            int[] w = CaseShape.worldPixel(front(s), p[0], p[1]);
            return w[0] == u && w[1] == v;
        }
        if (b instanceof BlockMonitor) {
            // видеопорт монитора - один пиксель в центре задней стенки
            if (kind != VIDEO || face != (front(s) ^ 1)) return false;
            int[] w = CaseShape.worldPixel(front(s), MONITOR_PORT[0], MONITOR_PORT[1]);
            return w[0] == u && w[1] == v;
        }
        if (u < 4 || u > 11 || v < 4 || v > 11) return false;
        return type(s, face) == kind;
    }

    public static String name(int kind) {
        return kind == NET ? "network" : kind == VIDEO ? "video" : "none";
    }
}
