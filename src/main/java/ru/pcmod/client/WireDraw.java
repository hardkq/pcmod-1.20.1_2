package ru.pcmod.client;

/**
 * Общие приёмы рисования проводов: лента шириной в 1 пиксель, повёрнутая к камере, и кубик (пин) без текстуры.
 * Все точки и камера заданы в одной системе координат (в блоке для {@link WireRenderer}, в мире за вычетом камеры для
 * {@link WirePreview}). Цвет задаёт вызывающий через {@link LegacyTess}.
 */
final class WireDraw {
    /** Половина ширины ленты: полпикселя. */
    static final double HALF = 0.5D / 16.0D;
    static final float[] NET = {0.82F, 0.49F, 0.14F};
    static final float[] VIDEO = {0.12F, 0.66F, 0.60F};

    private WireDraw() {
    }

    /** Лента по ломаной pts; камера в точке (cx, cy, cz). */
    static void curve(LegacyTess tess, double[][] pts, double cx, double cy, double cz) {
        for (int i = 0; i + 1 < pts.length; i++) ribbon(tess, pts[i], pts[i + 1], cx, cy, cz);
    }

    private static void ribbon(LegacyTess tess, double[] p, double[] q, double cx, double cy, double cz) {
        double sx = q[0] - p[0], sy = q[1] - p[1], sz = q[2] - p[2];
        double vx = cx - (p[0] + q[0]) * 0.5D, vy = cy - (p[1] + q[1]) * 0.5D, vz = cz - (p[2] + q[2]) * 0.5D;
        // сторона ленты: перпендикуляр к отрезку и к направлению на камеру
        double nx = sy * vz - sz * vy, ny = sz * vx - sx * vz, nz = sx * vy - sy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9D) return;
        nx = nx / len * HALF;
        ny = ny / len * HALF;
        nz = nz / len * HALF;
        tess.addVertex(p[0] - nx, p[1] - ny, p[2] - nz);
        tess.addVertex(p[0] + nx, p[1] + ny, p[2] + nz);
        tess.addVertex(q[0] + nx, q[1] + ny, q[2] + nz);
        tess.addVertex(q[0] - nx, q[1] - ny, q[2] - nz);
    }

    /** Кубик x0..x1, y0..y1, z0..z1 с простым затенением граней; цвет r, g, b и прозрачность a. */
    static void box(LegacyTess t, double x0, double y0, double z0, double x1, double y1, double z1,
                    float r, float g, float b, float a) {
        t.setColorRGBA_F(r, g, b, a); // верх
        t.addVertex(x0, y1, z0);
        t.addVertex(x0, y1, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y1, z0);
        t.setColorRGBA_F(r * 0.5F, g * 0.5F, b * 0.5F, a); // низ
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y0, z1);
        t.addVertex(x0, y0, z1);
        t.setColorRGBA_F(r * 0.8F, g * 0.8F, b * 0.8F, a); // север и юг
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x0, y1, z1);
        t.setColorRGBA_F(r * 0.65F, g * 0.65F, b * 0.65F, a); // запад и восток
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z1);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y0, z1);
    }
}
