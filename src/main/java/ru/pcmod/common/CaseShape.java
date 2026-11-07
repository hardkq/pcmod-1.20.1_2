package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Модели корпусов ПК (системных блоков): из каких параллелепипедов состоит корпус, где у него стеклянная стенка,
 * как внутри расположены материнская плата, блок питания и накопитель (сюда клиент рисует вставленное железо)
 * и в каких пикселях задней панели находятся порты. Только данные (без клиентских классов): используется и для коллизий
 * на сервере, и для отрисовки на клиенте.
 *
 * Координаты - в 1/16 блока в «локальной» системе, где лицо корпуса смотрит в +Z (как у метадаты 3), X растёт вправо
 * (если смотреть на корпус спереди), Y растёт вверх, Z растёт к лицу. Задняя панель всегда лежит ровно на задней грани блока (z = 0),
 * чтобы провода крепились к портам вплотную.
 */
public final class CaseShape {
    /** Слоты текстур модели (файлы pcmod:caseN_front / _side / _back / _top / _base / _glass). */
    public static final int FRONT = 0, SIDE = 1, BACK = 2, TOP = 3, BASE = 4, GLASS = 5;
    public static final String[] SLOT_NAMES = {"front", "side", "back", "top", "base", "glass"};

    /** Один параллелепипед корпуса. Слоты текстур: грань +Z (лицо), грань -Z (зад), верх, остальные грани. */
    public static final class Box {
        public final float x0, y0, z0, x1, y1, z1;
        public final int fz, bz, top, side;

        Box(float x0, float y0, float z0, float x1, float y1, float z1, int fz, int bz, int top, int side) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.fz = fz;
            this.bz = bz;
            this.top = top;
            this.side = side;
        }
    }

    /**
     * Система координат материнской платы: точка (a, b, c) = origin + A * a + B * b + C * c.
     * a - высота над платой (в сторону стекла), b - вдоль платы от задней панели к лицу, c - вдоль платы «вверх» (к ЦП и ОЗУ).
     * Размеры платы W (по b) и H (по c) и доступная глубина D (по a) - в 1/16 блока.
     */
    public static final class Board {
        public final float ox, oy, oz;
        public final int[] a, b, c;
        public final float w, h, d;

        Board(float ox, float oy, float oz, int[] a, int[] b, int[] c, float w, float h, float d) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.a = a;
            this.b = b;
            this.c = c;
            this.w = w;
            this.h = h;
            this.d = d;
        }
    }

    /** Стеклянная стенка: плоскость axis (0 = X, 1 = Y), её положение pos и прямоугольник p0..p1 (по первой оси) x q0..q1 (по второй). */
    public static final class Glass {
        /** 0: плоскость x = pos, прямоугольник по Z (p) и Y (q); 1: плоскость y = pos, прямоугольник по X (p) и Z (q). */
        public final int axis;
        public final float pos, p0, p1, q0, q1;

        Glass(int axis, float pos, float p0, float p1, float q0, float q1) {
            this.axis = axis;
            this.pos = pos;
            this.p0 = p0;
            this.p1 = p1;
            this.q0 = q0;
            this.q1 = q1;
        }
    }

    public final String name;
    public final Box[] boxes;
    public final Glass glass;
    public final Board board;
    /** Блок питания, накопитель (3.5") и светодиоды подсветки корпуса: {x0, y0, z0, x1, y1, z1} в локальных пикселях. */
    public final float[] psu, drive;
    public final float[][] strips;
    /** Светодиод питания на лицевой грани: x0, y0, x1, y1 (Y вверх) и плоскость лица (по Z). */
    public final float[] led;
    public final float frontZ;
    /** Порты на задней панели: пиксель {ix, iy} (ix - вправо от лица, iy - вверх) для сетевого и видеопорта. */
    public final int[] netPort, videoPort;
    /** Габариты корпуса {minX, minY, minZ, maxX, maxY, maxZ} в локальных пикселях. */
    public final float[] size;
    /** Какие из граней (0 = -X, 1 = +X, 2 = -Y, 3 = +Y, 4 = -Z, 5 = +Z) несут номер ПК. */
    public final boolean[] idFace;

    private CaseShape(String name, List<Box> boxes, Glass glass, Board board, float[] psu, float[] drive, float[][] strips,
                      float[] led, float frontZ, int[] netPort, int[] videoPort, boolean[] idFace) {
        this.name = name;
        this.boxes = boxes.toArray(new Box[boxes.size()]);
        this.glass = glass;
        this.board = board;
        this.psu = psu;
        this.drive = drive;
        this.strips = strips;
        this.led = led;
        this.frontZ = frontZ;
        this.netPort = netPort;
        this.videoPort = videoPort;
        this.idFace = idFace;
        float[] s = {99f, 99f, 99f, -99f, -99f, -99f};
        for (Box b : this.boxes) {
            s[0] = Math.min(s[0], b.x0);
            s[1] = Math.min(s[1], b.y0);
            s[2] = Math.min(s[2], b.z0);
            s[3] = Math.max(s[3], b.x1);
            s[4] = Math.max(s[4], b.y1);
            s[5] = Math.max(s[5], b.z1);
        }
        this.size = s;
    }

    // ===================== построение =====================

    private static final class Builder {
        final List<Box> l = new ArrayList<Box>();

        void add(float x0, float y0, float z0, float x1, float y1, float z1, int fz, int bz, int top, int side) {
            l.add(new Box(x0, y0, z0, x1, y1, z1, fz, bz, top, side));
        }

        /**
         * Башня с окном в левой стенке: внешний объём x0..x1, y0..y1, z0..z1, толщина стенок t, окно wz0..wz1 по Z и wy0..wy1 по Y.
         * Панели не пересекаются: перед, зад и правая стенка идут на всю высоту, крышка и дно - между ними, левая стенка - рамка вокруг окна.
         */
        void tower(float x0, float y0, float z0, float x1, float y1, float z1, float t,
                   float wz0, float wz1, float wy0, float wy1) {
            add(x0, y0, z1 - t, x1, y1, z1, FRONT, SIDE, TOP, SIDE);              // лицо
            add(x0, y0, z0, x1, y1, z0 + t, SIDE, BACK, TOP, SIDE);               // задняя панель
            add(x1 - t, y0, z0 + t, x1, y1, z1 - t, SIDE, SIDE, TOP, SIDE);       // правая стенка
            add(x0 + t, y1 - t, z0 + t, x1 - t, y1, z1 - t, SIDE, SIDE, TOP, SIDE); // крышка
            add(x0 + t, y0, z0 + t, x1 - t, y0 + t, z1 - t, BASE, BASE, BASE, BASE); // дно
            // рамка окна в левой стенке
            add(x0, wy1, z0 + t, x0 + t, y1, z1 - t, SIDE, SIDE, TOP, SIDE);       // верхняя перемычка
            add(x0, y0, z0 + t, x0 + t, wy0, z1 - t, SIDE, SIDE, TOP, SIDE);       // нижняя перемычка
            add(x0, wy0, z0 + t, x0 + t, wy1, wz0, SIDE, SIDE, TOP, SIDE);         // задняя стойка
            add(x0, wy0, wz1, x0 + t, wy1, z1 - t, SIDE, SIDE, TOP, SIDE);         // передняя стойка
        }

        /** Низкий настольный корпус с окном в крышке: окно wx0..wx1 по X и wz0..wz1 по Z. */
        void desktop(float x0, float y0, float z0, float x1, float y1, float z1, float t,
                     float wx0, float wx1, float wz0, float wz1) {
            add(x0, y0, z1 - t, x1, y1, z1, FRONT, SIDE, TOP, SIDE);              // лицо
            add(x0, y0, z0, x1, y1, z0 + t, SIDE, BACK, TOP, SIDE);               // задняя панель
            add(x0, y0, z0 + t, x0 + t, y1, z1 - t, SIDE, SIDE, TOP, SIDE);       // левая стенка
            add(x1 - t, y0, z0 + t, x1, y1, z1 - t, SIDE, SIDE, TOP, SIDE);       // правая стенка
            add(x0 + t, y0, z0 + t, x1 - t, y0 + t, z1 - t, BASE, BASE, BASE, BASE); // дно
            // рамка окна в крышке
            add(x0 + t, y1 - t, z0 + t, wx0, y1, z1 - t, SIDE, SIDE, TOP, SIDE);   // левая перемычка
            add(wx1, y1 - t, z0 + t, x1 - t, y1, z1 - t, SIDE, SIDE, TOP, SIDE);   // правая перемычка
            add(wx0, y1 - t, z0 + t, wx1, y1, wz0, SIDE, SIDE, TOP, SIDE);         // задняя перемычка
            add(wx0, y1 - t, wz1, wx1, y1, z1 - t, SIDE, SIDE, TOP, SIDE);         // передняя перемычка
        }
    }

    private static final int[] PX = {1, 0, 0}, NX = {-1, 0, 0}, PY = {0, 1, 0}, PZ = {0, 0, 1};

    private static final boolean[] ID_TOWER = {false, true, false, true, true, true};
    private static final boolean[] ID_DESKTOP = {true, true, false, false, false, true};

    // ===================== модели =====================

    /** Classic Tower: бежево-серая классическая башня с большим окном на левой стенке. */
    private static CaseShape classic() {
        Builder b = new Builder();
        b.tower(3f, 0f, 0f, 13f, 16f, 16f, 0.75f, 2f, 14f, 1.5f, 14.5f);
        Glass g = new Glass(0, 3.375f, 2f, 14f, 1.5f, 14.5f);
        Board bd = new Board(12.25f, 3.8f, 1.5f, NX, PZ, PY, 10.5f, 11.0f, 8.4f);
        return new CaseShape("Classic Tower", b.l, g, bd,
                new float[]{4.0f, 0.75f, 0.75f, 12.0f, 3.6f, 6.6f},
                new float[]{9.6f, 0.9f, 7.6f, 12.0f, 3.4f, 14.8f},
                new float[0][],
                new float[]{9.75f, 1.5f, 12.25f, 4.0f}, 16f,
                new int[]{5, 8}, new int[]{8, 8}, ID_TOWER);
    }

    /** Mini Cube: компактный кубик Mini-ITX с почти полностью стеклянной левой стенкой. */
    private static CaseShape cube() {
        Builder b = new Builder();
        b.tower(2.5f, 0f, 0f, 13.5f, 12f, 13f, 0.75f, 1.5f, 11.5f, 1.5f, 10.5f);
        Glass g = new Glass(0, 2.875f, 1.5f, 11.5f, 1.5f, 10.5f);
        Board bd = new Board(12.75f, 3.1f, 1.4f, NX, PZ, PY, 8.2f, 8.0f, 9.0f);
        return new CaseShape("Mini Cube", b.l, g, bd,
                new float[]{4.0f, 0.75f, 0.75f, 12.4f, 2.9f, 5.4f},
                new float[]{9.9f, 0.9f, 5.8f, 12.4f, 2.7f, 11.8f},
                new float[0][],
                new float[]{9.5f, 1.25f, 11.75f, 3.25f}, 13f,
                new int[]{5, 6}, new int[]{8, 6}, ID_TOWER);
    }

    /** Mid Tower Glass: чёрная миддл-башня, левая стенка - сплошное закалённое стекло, RGB-полоса у лица. */
    private static CaseShape mid() {
        Builder b = new Builder();
        b.tower(2f, 0f, 0f, 14f, 16f, 16f, 0.75f, 1.5f, 14.5f, 1.5f, 14.5f);
        // кожух блока питания (виден сквозь стекло как тёмная полка)
        b.add(3.0f, 3.8f, 0.75f, 13.0f, 4.1f, 13.0f, BASE, BASE, BASE, BASE);
        Glass g = new Glass(0, 2.375f, 1.5f, 14.5f, 1.5f, 14.5f);
        Board bd = new Board(13.25f, 4.4f, 1.5f, NX, PZ, PY, 10.4f, 10.4f, 9.6f);
        return new CaseShape("Mid Tower Glass", b.l, g, bd,
                new float[]{3.4f, 0.75f, 0.75f, 12.9f, 3.8f, 7.4f},
                new float[]{11.2f, 4.6f, 12.2f, 12.8f, 9.2f, 15.0f},
                new float[][]{{3.2f, 4.4f, 14.2f, 3.6f, 14.8f, 14.8f}},
                new float[]{8.75f, 1.5f, 11.25f, 3.5f}, 16f,
                new int[]{6, 7}, new int[]{9, 7}, ID_TOWER);
    }

    /** Full Tower Showcase: высокая витринная башня на ножках, большое окно, RGB-подсветка по углам. */
    private static CaseShape full() {
        Builder b = new Builder();
        b.tower(1.5f, 1f, 0f, 14.5f, 16f, 16f, 0.75f, 1.5f, 14.5f, 2.5f, 14.5f);
        // ножки
        b.add(2f, 0f, 1.5f, 4f, 1f, 3.5f, BASE, BASE, BASE, BASE);
        b.add(12f, 0f, 1.5f, 14f, 1f, 3.5f, BASE, BASE, BASE, BASE);
        b.add(2f, 0f, 12.5f, 4f, 1f, 14.5f, BASE, BASE, BASE, BASE);
        b.add(12f, 0f, 12.5f, 14f, 1f, 14.5f, BASE, BASE, BASE, BASE);
        // кожух блока питания
        b.add(2.6f, 4.4f, 0.75f, 13.6f, 4.7f, 13.2f, BASE, BASE, BASE, BASE);
        Glass g = new Glass(0, 1.875f, 1.5f, 14.5f, 2.5f, 14.5f);
        Board bd = new Board(13.75f, 4.8f, 1.4f, NX, PZ, PY, 11.4f, 10.2f, 10.8f);
        return new CaseShape("Full Tower Showcase", b.l, g, bd,
                new float[]{3.0f, 1.75f, 0.75f, 13.2f, 4.4f, 8.6f},
                new float[]{12.0f, 5.4f, 13.1f, 13.6f, 9.8f, 15.1f},
                new float[][]{{2.6f, 5.0f, 14.6f, 3.1f, 15.0f, 15.1f}, {2.6f, 14.6f, 1.0f, 3.1f, 15.1f, 15.1f}},
                new float[]{9.5f, 2.5f, 12.0f, 4.5f}, 16f,
                new int[]{6, 7}, new int[]{9, 7}, ID_TOWER);
    }

    /** Slim Desktop: низкий настольный корпус; стекло в крышке, плата лежит на дне, монитор можно ставить сверху. */
    private static CaseShape slim() {
        Builder b = new Builder();
        b.desktop(0.5f, 0f, 0f, 15.5f, 6.5f, 14f, 0.75f, 2f, 14f, 1.5f, 12.5f);
        Glass g = new Glass(1, 6.125f, 2f, 14f, 1.5f, 12.5f);
        Board bd = new Board(5.2f, 0.95f, 1.4f, PY, PZ, PX, 10.6f, 9.4f, 4.7f);
        return new CaseShape("Slim Desktop", b.l, g, bd,
                new float[]{1.3f, 0.75f, 0.75f, 4.9f, 4.4f, 9.0f},
                new float[]{5.4f, 0.8f, 12.2f, 14.4f, 2.2f, 13.2f},
                new float[0][],
                new float[]{12.0f, 0.9f, 14.0f, 2.7f}, 14f,
                new int[]{4, 3}, new int[]{7, 3}, ID_DESKTOP);
    }

    /** Все модели; индекс = тип блока ({@link BlockComputer}). */
    private static final CaseShape[] SHAPES = {classic(), cube(), mid(), full(), slim()};

    public static final int COUNT = SHAPES.length;

    public static CaseShape get(int type) {
        return SHAPES[Math.max(0, Math.min(SHAPES.length - 1, type))];
    }

    // ===================== порты =====================

    /** Пиксель порта этого вида (Ports.NET или Ports.VIDEO) на задней панели: {ix, iy} в локальных пикселях. */
    public int[] port(int kind) {
        return kind == Ports.VIDEO ? videoPort : kind == Ports.NET ? netPort : null;
    }

    /**
     * Пиксель {u, v} на мировой задней грани блока (в той же сетке, что {@link Wires#pixel}) для локального пикселя ix, iy
     * корпуса, повёрнутого лицом к стороне f.
     */
    public static int[] worldPixel(int f, int ix, int iy) {
        float lx = (ix + 0.5f) / 16f;
        float wx = MonitorShape.rotX(lx, 0.5f / 16f, f), wz = MonitorShape.rotZ(lx, 0.5f / 16f, f);
        int back = f ^ 1;
        float h = back == 2 || back == 3 ? wx : wz;
        int u = (int) Math.floor(h * 16f);
        return new int[]{Math.max(0, Math.min(15, u)), Math.max(0, Math.min(15, iy))};
    }

    // ===================== поворот и габариты =====================

    /** Параллелепипед i, повёрнутый для стороны f, в долях блока: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public float[] box(int i, int f) {
        Box b = boxes[i];
        float ax = MonitorShape.rotX(b.x0 / 16f, b.z0 / 16f, f), az = MonitorShape.rotZ(b.x0 / 16f, b.z0 / 16f, f);
        float bx = MonitorShape.rotX(b.x1 / 16f, b.z1 / 16f, f), bz = MonitorShape.rotZ(b.x1 / 16f, b.z1 / 16f, f);
        return new float[]{Math.min(ax, bx), b.y0 / 16f, Math.min(az, bz), Math.max(ax, bx), b.y1 / 16f, Math.max(az, bz)};
    }

    /** Общая габаритная рамка корпуса для стороны f (выделение курсором). */
    public float[] bounds(int f) {
        float[] r = {1f, 1f, 1f, 0f, 0f, 0f};
        for (int i = 0; i < boxes.length; i++) {
            float[] b = box(i, f);
            r[0] = Math.min(r[0], b[0]);
            r[1] = Math.min(r[1], b[1]);
            r[2] = Math.min(r[2], b[2]);
            r[3] = Math.max(r[3], b[3]);
            r[4] = Math.max(r[4], b[4]);
            r[5] = Math.max(r[5], b[5]);
        }
        return r;
    }
}
