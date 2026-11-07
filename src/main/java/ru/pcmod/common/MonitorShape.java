package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Форма и раскладка каждого монитора: из каких параллелепипедов состоит модель и где на ней
 * находятся экран, светодиод питания и RGB-подсветка. Только данные (без клиентских классов),
 * поэтому используется и для коллизий на сервере, и для отрисовки на клиенте.
 *
 * Координаты - в 1/16 блока в «локальной» системе, где лицо монитора смотрит в +Z (как у метадаты 3),
 * X растёт вправо (если смотреть на монитор спереди), Y растёт вверх, Z растёт к лицу.
 * Поля «top» отсчитываются от верха блока вниз (как «y» в старом blockRect).
 */
public final class MonitorShape {
    /** Слоты текстур модели (файлы pcmod:monitorN_front / _side / _back / _top / _base). */
    public static final int FRONT = 0, SIDE = 1, BACK = 2, TOP = 3, BASE = 4;
    public static final String[] SLOT_NAMES = {"front", "side", "back", "top", "base"};

    /** Один параллелепипед модели и текстуры его граней. */
    public static final class Box {
        public final float x0, y0, z0, x1, y1, z1;
        /** Слоты текстур: грань +Z (лицо), грань -Z (зад), верх, остальные грани. */
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

    /** true - у монитора своя модель; false - обычный полный блок (зелёный монохромный M9). */
    public final boolean custom;
    public final Box[] boxes;

    /** Экран: левый край, верх (от верха блока), ширина, высота и глубина плоскости экрана (по Z). */
    public final float scrX, scrTop, scrW, scrH, scrZ;
    /** Плоскость лицевой панели (по Z): на ней светодиод и подсветка. */
    public final float frontZ;
    /** Светодиод питания монитора: x0, top0, x1, top1. */
    public final float[] led;
    /** Передняя RGB-полоса: x, top, ширина сегмента, шаг, высота, число сегментов; или null. */
    public final float[] stripFront;
    /** Задняя RGB-подсветка: прямоугольники x0, y0, x1, y1 (Y вверх) на плоскости backZ; или null. */
    public final float[][] backRgb;
    public final float backZ;

    private MonitorShape(boolean custom, List<Box> boxes, float[] scr, float frontZ, float[] led,
                         float[] stripFront, float[][] backRgb, float backZ) {
        this.custom = custom;
        this.boxes = boxes.toArray(new Box[boxes.size()]);
        this.scrX = scr[0];
        this.scrTop = scr[1];
        this.scrW = scr[2];
        this.scrH = scr[3];
        this.scrZ = scr[4];
        this.frontZ = frontZ;
        this.led = led;
        this.stripFront = stripFront;
        this.backRgb = backRgb;
        this.backZ = backZ;
    }

    // ===================== построение =====================

    private static final class Builder {
        final List<Box> l = new ArrayList<Box>();

        /** Корпус: тёмная/светлая пластмасса, лицо - обычная боковина. */
        void body(float x0, float y0, float z0, float x1, float y1, float z1) {
            add(x0, y0, z0, x1, y1, z1, SIDE, SIDE, TOP, SIDE);
        }

        /** Корпус, на лице которого видна лицевая текстура (стекло экрана в проёме рамки). */
        void glass(float x0, float y0, float z0, float x1, float y1, float z1) {
            add(x0, y0, z0, x1, y1, z1, FRONT, SIDE, TOP, SIDE);
        }

        /** Задняя часть: на заднюю грань кладётся текстура «зад» (разъёмы, логотип). */
        void rear(float x0, float y0, float z0, float x1, float y1, float z1) {
            add(x0, y0, z0, x1, y1, z1, SIDE, BACK, TOP, SIDE);
        }

        /** Подставка. */
        void base(float x0, float y0, float z0, float x1, float y1, float z1) {
            add(x0, y0, z0, x1, y1, z1, BASE, BASE, BASE, BASE);
        }

        /**
         * Лицевая рамка: четыре бруска вокруг проёма экрана. Внешний контур (x0,y0)-(x1,y1),
         * проём (ox0,oy0)-(ox1,oy1) (Y вверх), глубина рамки z0..z1.
         */
        void frame(float x0, float y0, float x1, float y1, float ox0, float oy0, float ox1, float oy1, float z0, float z1) {
            if (y1 > oy1) add(x0, oy1, z0, x1, y1, z1, FRONT, SIDE, TOP, SIDE);
            if (oy0 > y0) add(x0, y0, z0, x1, oy0, z1, FRONT, SIDE, TOP, SIDE);
            if (ox0 > x0) add(x0, oy0, z0, ox0, oy1, z1, FRONT, SIDE, TOP, SIDE);
            if (x1 > ox1) add(ox1, oy0, z0, x1, oy1, z1, FRONT, SIDE, TOP, SIDE);
        }

        private void add(float x0, float y0, float z0, float x1, float y1, float z1, int fz, int bz, int top, int side) {
            l.add(new Box(x0, y0, z0, x1, y1, z1, fz, bz, top, side));
        }
    }

    // ===================== модели =====================

    /** Старый полный блок (M9): раскладка экрана как раньше - 14/16 в ширину, верх на 3/16. */
    private static MonitorShape legacy() {
        return new MonitorShape(false, new ArrayList<Box>(), new float[]{1f, 3f, 14f, 14f * 198f / 360f, 16f}, 16f,
                new float[]{12.25f, 12.75f, 13.75f, 13.75f}, null, null, 0f);
    }

    /**
     * A12 - янтарный монохромный терминал: компактная коробка с ЭЛТ, конусом трубки сзади,
     * маленькой поворотной ножкой и большим подбородком с ручками яркости.
     */
    private static MonitorShape amber() {
        Builder b = new Builder();
        // ножка
        b.base(3f, 0f, 3f, 13f, 1f, 13f);
        b.base(5.5f, 1f, 5.5f, 10.5f, 1.5f, 10.5f);
        // передний корпус и рамка с проёмом (экран 10 x 7.5, глубина проёма 0.5)
        b.glass(1f, 1.5f, 7f, 15f, 14.5f, 15.5f);
        b.frame(1f, 1.5f, 15f, 14.5f, 3f, 5.5f, 13f, 13f, 15.5f, 16f);
        // конус трубки
        b.body(2.5f, 3f, 3.5f, 13.5f, 13f, 7f);
        b.body(3.5f, 4f, 2f, 12.5f, 12f, 3.5f);
        b.rear(5f, 5f, 0f, 11f, 11f, 2f);
        return new MonitorShape(true, b.l, new float[]{3f, 3f, 10f, 7.5f, 15.5f}, 16f,
                new float[]{11.5f, 12.25f, 13f, 13.25f}, null, null, 0f);
    }

    /** C14 - цветной ЭЛТ: бежевый корпус, глубокий конус трубки, поворотная подставка. */
    private static MonitorShape colorCrt() {
        Builder b = new Builder();
        b.base(3.5f, 0f, 4f, 12.5f, 0.8f, 13f);
        b.base(5f, 0.8f, 5.5f, 11f, 2f, 11.5f);
        b.glass(0.5f, 2f, 8f, 15.5f, 15f, 15.5f);
        b.frame(0.5f, 2f, 15.5f, 15f, 2f, 5f, 14f, 14f, 15.5f, 16f);
        b.body(1.5f, 3.5f, 5f, 14.5f, 13.5f, 8f);
        b.body(3f, 5f, 2.5f, 13f, 12f, 5f);
        b.rear(6f, 7f, 0f, 10f, 10f, 2.5f);
        return new MonitorShape(true, b.l, new float[]{2f, 2f, 12f, 9f, 15.5f}, 16f,
                new float[]{12.5f, 12.5f, 13.5f, 13.25f}, null, null, 0f);
    }

    /** L19 - ЖК-монитор: тонкая панель на стойке с круглой подставкой, «горб» электроники сзади. */
    private static MonitorShape lcd() {
        Builder b = new Builder();
        b.base(4f, 0f, 5.5f, 12f, 1f, 13f);
        b.base(7f, 1f, 8f, 9f, 6.5f, 10f);
        b.glass(0.5f, 4.8f, 12.25f, 15.5f, 14f, 13.75f);
        b.frame(0.5f, 4.8f, 15.5f, 14f, 1f, 5.8f, 15f, 13.5f, 13.75f, 14f);
        b.rear(4f, 6f, 0f, 12f, 12.5f, 12.25f);
        return new MonitorShape(true, b.l, new float[]{1f, 2.5f, 14f, 7.7f, 13.75f}, 14f,
                new float[]{13.5f, 10.3f, 14.5f, 10.8f}, null, null, 0f);
    }

    /** G24 - игровой монитор: безрамочная панель, V-образные ножки, угловатый «горб» сзади с RGB-кольцом. */
    private static MonitorShape gaming() {
        Builder b = new Builder();
        // V-образная подставка
        b.base(1.5f, 0f, 6f, 5.5f, 0.8f, 13f);
        b.base(10.5f, 0f, 6f, 14.5f, 0.8f, 13f);
        b.base(5.5f, 0f, 6f, 10.5f, 0.8f, 8.5f);
        b.base(6.5f, 0.8f, 8f, 9.5f, 8f, 10f);
        // панель с тонкой рамкой (экран 15 x 8.4)
        b.glass(0f, 5f, 13.25f, 16f, 15f, 14.5f);
        b.frame(0f, 5f, 16f, 15f, 0.5f, 6.1f, 15.5f, 14.5f, 14.5f, 14.75f);
        // ступенчатый задний кожух
        b.rear(2.5f, 6f, 11f, 13.5f, 14f, 13.25f);
        b.rear(5f, 7.5f, 0f, 11f, 12.5f, 11f);
        return new MonitorShape(true, b.l, new float[]{0.5f, 1.5f, 15f, 8.4f, 14.5f}, 14.75f,
                new float[]{13.5f, 10.3f, 14.5f, 10.8f},
                new float[]{2f, 10.25f, 0.9f, 1f, 0.4f, 7f},
                new float[][]{
                        {5.5f, 11.9f, 10.5f, 12.3f},
                        {5.5f, 7.7f, 10.5f, 8.1f},
                        {5.2f, 8.1f, 5.6f, 11.9f},
                        {10.4f, 8.1f, 10.8f, 11.9f}},
                0f);
    }

    private static final MonitorShape[] SHAPES = {legacy(), amber(), colorCrt(), lcd(), gaming()};

    public static MonitorShape get(int type) {
        return SHAPES[Math.max(0, Math.min(SHAPES.length - 1, type))];
    }

    // ===================== поворот =====================

    /** Сторона лица по метадате: 2 = север, 3 = юг, 4 = запад, 5 = восток (иначе юг). */
    public static int facing(int meta) {
        return meta < 2 || meta > 5 ? 3 : meta;
    }

    /** Поворот X (в долях блока) вокруг центра блока для стороны лица f; поворот такой же, как glRotatef в RenderComputer. */
    public static float rotX(float x, float z, int f) {
        float cx = x - 0.5f, cz = z - 0.5f;
        switch (f) {
            case 5: return cz + 0.5f;
            case 2: return -cx + 0.5f;
            case 4: return -cz + 0.5f;
            default: return cx + 0.5f;
        }
    }

    public static float rotZ(float x, float z, int f) {
        float cx = x - 0.5f, cz = z - 0.5f;
        switch (f) {
            case 5: return -cx + 0.5f;
            case 2: return -cz + 0.5f;
            case 4: return cx + 0.5f;
            default: return cz + 0.5f;
        }
    }

    /**
     * На какой глубине (по Z, в 1/16 блока от задней грани блока) находится задняя стенка модели в центре грани,
     * где стоит видеопорт. 0 - стенка вплотную к грани блока (и у обычного блока M9).
     */
    public float rearZ() {
        float z = 16f;
        boolean any = false;
        for (Box b : boxes) {
            if (b.x0 <= 8f && b.x1 >= 8f && b.y0 <= 8f && b.y1 >= 8f && b.z0 < z) {
                z = b.z0;
                any = true;
            }
        }
        return any ? z : 0f;
    }

    /** Гильза видеопорта 4x4 пикселя от задней стенки модели до грани блока (null, если стенка и так у грани). */
    public Box portSleeve() {
        float z = rearZ();
        if (!custom || z < 0.75f) return null;
        return new Box(6f, 6f, 0f, 10f, 10f, z, SIDE, BACK, SIDE, SIDE);
    }

    /** Параллелепипед i, повёрнутый для стороны f, в долях блока: {minX, minY, minZ, maxX, maxY, maxZ}. */
    public float[] box(int i, int f) {
        Box b = boxes[i];
        float ax = rotX(b.x0 / 16f, b.z0 / 16f, f), az = rotZ(b.x0 / 16f, b.z0 / 16f, f);
        float bx = rotX(b.x1 / 16f, b.z1 / 16f, f), bz = rotZ(b.x1 / 16f, b.z1 / 16f, f);
        return new float[]{Math.min(ax, bx), b.y0 / 16f, Math.min(az, bz), Math.max(ax, bx), b.y1 / 16f, Math.max(az, bz)};
    }

    /** Общая габаритная рамка модели для стороны f (выделение курсором). */
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
