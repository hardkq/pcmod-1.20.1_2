package ru.pcmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.pcmod.common.BlockComputer;
import ru.pcmod.common.CaseShape;
import ru.pcmod.common.ModItems;
import ru.pcmod.common.PartSpec;
import ru.pcmod.common.TileComputer;

/**
 * Железо внутри корпуса, видимое через стеклянную стенку: материнская плата (цвет зависит от модели), процессор, кулер
 * (боксовый, башенный или жидкостный с радиатором), планки ОЗУ, видеокарта с вентиляторами, блок питания, накопитель
 * (HDD, SSD или NVMe на плате) и Wi-Fi модуль с антеннами. Всё берётся из слотов {@link TileComputer}.
 * Пока ПК работает, вентиляторы крутятся, а ОЗУ DDR5, видеокарты верхнего класса и жидкостный кулер светятся RGB.
 *
 * Рисуется вызовом из {@link RenderComputer} с уже выставленной системой координат блока (начало в центре, лицо к +Z):
 * точка в пикселях корпуса (x, y, z) - это ((x - 8) / 16, (y - 8) / 16, (z - 8) / 16).
 */
final class CaseInterior {
    // плитки атласа 4x4 (по 16 px)
    private static final int T_WHITE = 0, T_PCB = 1, T_METAL = 2, T_FINS = 3, T_FANBG = 4, T_BLADES = 5, T_RAM = 6, T_SHROUD = 7,
            T_PSU = 8, T_HDD = 9, T_CHIP = 10, T_DARK = 11, T_SSD = 12, T_STICKER = 13, T_RING = 14, T_IO = 15;

    /** Максимальная высота деталей над платой в «единицах платы» (для сжатия по глубине в мелких корпусах). */
    private static final float A_REF = 4.2f;

    private final LegacyTess tess = LegacyTess.instance;

    // система координат платы текущего корпуса
    private float ox, oy, oz, sb, sc, ua, ub;
    private int[] axA, axB, axC;
    private int ia;
    private boolean glassHi;
    private int bright;
    private final float[] p0 = new float[3], p1 = new float[3], pc = new float[3];

    // состояние ПК
    private TileComputer pc_;
    private boolean power;
    private float spin;
    private long now;

    // ===================== вход =====================

    private static final float LAYER = 0.0006f;
    private PoseStack ps;
    private MultiBufferSource buf;

    /** Рисует железо. ps - система координат блока (начало в центре, лицо к +Z); light - упакованный свет блока. */
    void drawParts(TileComputer c, CaseShape s, long time, boolean powered, int light, PoseStack ps, MultiBufferSource buf) {
        this.ps = ps;
        this.buf = buf;
        pc_ = c;
        power = powered;
        now = time;
        spin = powered ? (float) ((time % 650L) / 650.0 * Math.PI * 2.0) : 0.35f;
        bright = (light & 0xFFFF0000) | Math.max(light & 0xFFFF, 0x90);
        setFrame(s.board);
        begin(0f);
        solids(s);
        begin(2f);
        blades();
        begin(4f);
        glow(s);
    }

    /** Стеклянная стенка корпуса: полупрозрачная текстура поверх всего, что внутри (pcmod:textures/block/case<N>_glass.png, UV 0..1). */
    void drawGlass(TileComputer c, CaseShape s, int light, PoseStack ps, MultiBufferSource buf) {
        if (!(c.getBlockState().getBlock() instanceof BlockComputer)) return;
        ResourceLocation rl = new ResourceLocation("pcmod", "textures/block/case" + ((BlockComputer) c.getBlockState().getBlock()).type + "_glass.png");
        tess.start(buf, PcRenderTypes.glass(rl), ps.last().pose(), 0f);
        tess.setBrightness(light);
        tess.setColorOpaque_F(1f, 1f, 1f);
        CaseShape.Glass g = s.glass;
        float u0 = 0f, u1 = 1f, v0 = 0f, v1 = 1f;
        if (g.axis == 0) {
            v(g.pos, g.q1, g.p0, u0, v0);
            v(g.pos, g.q0, g.p0, u0, v1);
            v(g.pos, g.q0, g.p1, u1, v1);
            v(g.pos, g.q1, g.p1, u1, v0);
        } else {
            v(g.p0, g.pos, g.q0, u0, v0);
            v(g.p0, g.pos, g.q1, u0, v1);
            v(g.p1, g.pos, g.q1, u1, v1);
            v(g.p1, g.pos, g.q0, u1, v0);
        }
    }

    // ===================== геометрия =====================

    /** Слой: 0 - детали, 2 - лопасти (сдвиг полигонов), 4 - свечение (поверх всего). */
    private void begin(float layer) {
        tess.start(buf, layer >= 4f ? PcRenderTypes.INTERIOR_GLOW : layer >= 2f ? PcRenderTypes.BLADES : PcRenderTypes.INTERIOR,
                ps.last().pose(), 0f);
    }

    private void end() {
    }

    private void setFrame(CaseShape.Board bd) {
        ox = bd.ox;
        oy = bd.oy;
        oz = bd.oz;
        axA = bd.a;
        axB = bd.b;
        axC = bd.c;
        sb = bd.w / 10f;
        sc = bd.h / 10f;
        ub = Math.min(sb, sc);
        float squeeze = Math.min(1f, bd.d / (A_REF * ub));
        ua = ub * squeeze;
        ia = axA[0] != 0 ? 0 : axA[1] != 0 ? 1 : 2;
        glassHi = axA[ia] > 0;
    }

    /** Точка платы (a - над платой в единицах ua, b - вдоль платы, c - «вверх» по плате) в пикселях корпуса. */
    private void pt(float a, float b, float c, float[] o) {
        float aa = a * ua, bb = b * sb, cc = c * sc;
        o[0] = ox + axA[0] * aa + axB[0] * bb + axC[0] * cc;
        o[1] = oy + axA[1] * aa + axB[1] * bb + axC[1] * cc;
        o[2] = oz + axA[2] * aa + axB[2] * bb + axC[2] * cc;
    }

    private void v(float x, float y, float z, float u, float vv) {
        tess.addVertexWithUV(x / 16f - 0.5f, y / 16f - 0.5f, z / 16f - 0.5f, u, vv);
    }

    private void quad(int k, float pos, float a0, float a1, float b0, float b1, int tile) {
        int k1 = (k + 1) % 3, k2 = (k + 2) % 3;
        float u0 = (tile % 4) * 0.25f + 0.004f, v0 = (tile / 4) * 0.25f + 0.004f, u1 = u0 + 0.242f, v1 = v0 + 0.242f;
        float[] q = new float[3];
        float[][] c = {{a0, b0, u0, v0}, {a1, b0, u1, v0}, {a1, b1, u1, v1}, {a0, b1, u0, v1}};
        for (float[] e : c) {
            q[k] = pos;
            q[k1] = e[0];
            q[k2] = e[1];
            v(q[0], q[1], q[2], e[2], e[3]);
        }
    }

    /** Параллелепипед в пикселях корпуса; грань, обращённая к стеклу, получает плитку tTop, остальные - tSide. */
    private void pxBox(float x0, float y0, float z0, float x1, float y1, float z1, int tTop, int tSide,
                       float r, float g, float b, int br) {
        float[] lo = {Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1)};
        float[] hi = {Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)};
        for (int k = 0; k < 3; k++) {
            int k1 = (k + 1) % 3, k2 = (k + 2) % 3;
            for (int s = 0; s < 2; s++) {
                float shade = k == 1 ? (s == 1 ? 1f : 0.55f) : k == 0 ? 0.7f : 0.85f;
                tess.setBrightness(br);
                tess.setColorOpaque_F(r * shade, g * shade, b * shade);
                int tile = k == ia && s == (glassHi ? 1 : 0) ? tTop : tSide;
                quad(k, s == 0 ? lo[k] : hi[k], lo[k1], hi[k1], lo[k2], hi[k2], tile);
            }
        }
    }

    /** Параллелепипед в координатах платы. */
    private void box(float a0, float a1, float b0, float b1, float c0, float c1, int tTop, int tSide,
                     float r, float g, float b) {
        pt(a0, b0, c0, p0);
        pt(a1, b1, c1, p1);
        pxBox(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], tTop, tSide, r, g, b, bright);
    }

    private void glowBox(float a0, float a1, float b0, float b1, float c0, float c1, float r, float g, float b) {
        pt(a0, b0, c0, p0);
        pt(a1, b1, c1, p1);
        pxBox(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], T_WHITE, T_WHITE, r, g, b, 0xF000F0);
    }

    /** Вентилятор: квадрат со стороной 2*half (в единицах платы), повёрнутый на ang, в плоскости на высоте a над платой. */
    private void fanQuad(float a, float bc, float cc, float half, float ang, int tile, float r, float g, float b, int br) {
        pt(a, bc, cc, pc);
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        float u0 = (tile % 4) * 0.25f + 0.004f, v0 = (tile / 4) * 0.25f + 0.004f, u1 = u0 + 0.242f, v1 = v0 + 0.242f;
        float[][] c = {{-1, -1, u0, v0}, {1, -1, u1, v0}, {1, 1, u1, v1}, {-1, 1, u0, v1}};
        tess.setBrightness(br);
        tess.setColorOpaque_F(r, g, b);
        for (float[] e : c) {
            float lx = e[0] * half * ub, ly = e[1] * half * ub;
            float db = lx * cs - ly * sn, dc = lx * sn + ly * cs;
            float x = pc[0] + axB[0] * db + axC[0] * dc;
            float y = pc[1] + axB[1] * db + axC[1] * dc;
            float z = pc[2] + axB[2] * db + axC[2] * dc;
            v(x, y, z, e[2], e[3]);
        }
    }

    // ===================== цвета =====================

    private static final float[][] MB_COL = {
            {0.20f, 0.62f, 0.30f}, {0.22f, 0.45f, 0.80f}, {0.30f, 0.30f, 0.34f}, {0.80f, 0.20f, 0.20f}, {0.58f, 0.32f, 0.85f}};

    private static int mbIndex(Item i) {
        if (i == ModItems.part("mb_basic")) return 0;
        if (i == ModItems.part("mb_plus")) return 1;
        if (i == ModItems.part("mb_mid")) return 2;
        if (i == ModItems.part("mb_pro")) return 3;
        return 4;
    }

    private static float[] hue(double h) {
        h = h - Math.floor(h);
        double x = h * 6.0;
        int i = (int) x;
        float f = (float) (x - i);
        switch (i) {
            case 0: return new float[]{1f, f, 0f};
            case 1: return new float[]{1f - f, 1f, 0f};
            case 2: return new float[]{0f, 1f, f};
            case 3: return new float[]{0f, 1f - f, 1f};
            case 4: return new float[]{f, 0f, 1f};
            default: return new float[]{1f, 0f, 1f - f};
        }
    }

    private float[] rgb(double offset) {
        return hue(now / 2400.0 + offset);
    }

    // ===================== детали (непрозрачный проход) =====================

    private PartSpec spec(int slot) {
        return pc_.spec(slot);
    }

    private int gpuTier() {
        PartSpec g = spec(TileComputer.GPU);
        return g == null ? 0 : Math.max(1, Math.min(3, g.tier));
    }

    private void solids(CaseShape s) {
        // блок питания
        PartSpec psu = spec(TileComputer.PSU);
        if (psu != null) {
            float[] r = s.psu;
            float k = psu.watts >= 1000 ? 0.12f : psu.watts >= 750 ? 0.17f : psu.watts >= 500 ? 0.24f : 0.34f;
            pxBox(r[0], r[1], r[2], r[3], r[4], r[5], T_PSU, T_METAL, k, k, k + 0.02f, bright);
        }
        // накопитель
        PartSpec st = spec(TileComputer.STORAGE);
        Item stItem = pc_.getItem(TileComputer.STORAGE).isEmpty() ? null : pc_.getItem(TileComputer.STORAGE).getItem();
        if (st != null && stItem != ModItems.part("nvme")) {
            float[] r = s.drive.clone();
            if (!st.hdd) {
                // SSD - тонкая плитка у стенки
                float t = r[3 + ia] - r[ia];
                if (glassHi) r[3 + ia] = r[ia] + t * 0.4f;
                else r[ia] = r[3 + ia] - t * 0.4f;
                pxBox(r[0], r[1], r[2], r[3], r[4], r[5], T_SSD, T_DARK, 0.9f, 0.9f, 0.95f, bright);
            } else {
                pxBox(r[0], r[1], r[2], r[3], r[4], r[5], T_HDD, T_METAL, 0.88f, 0.88f, 0.9f, bright);
            }
        }

        PartSpec mb = spec(TileComputer.MB);
        if (mb == null) return;
        ItemStack mbStack = pc_.getItem(TileComputer.MB);
        float[] pcb = MB_COL[mbIndex(mbStack.getItem())];

        // сама плата, задняя панель с разъёмами, радиаторы и сокет
        box(0.12f, 0.55f, 0f, 10f, 0f, 10f, T_PCB, T_PCB, pcb[0], pcb[1], pcb[2]);
        box(0.55f, 1.55f, -0.3f, 0.75f, 6.0f, 9.7f, T_IO, T_METAL, 0.8f, 0.8f, 0.82f);
        box(0.55f, 1.3f, 2.4f, 5.0f, 8.7f, 9.5f, T_FINS, T_METAL, 0.62f, 0.62f, 0.66f);
        box(0.55f, 1.3f, 0.9f, 1.7f, 6.0f, 8.6f, T_FINS, T_METAL, 0.62f, 0.62f, 0.66f);
        box(0.55f, 1.2f, 6.3f, 8.7f, 0.8f, 2.6f, T_FINS, T_METAL, 0.55f, 0.55f, 0.6f);
        box(0.55f, 0.8f, 2.85f, 4.35f, 6.25f, 7.75f, T_METAL, T_METAL, 0.78f, 0.74f, 0.66f); // сокет

        // слоты ОЗУ
        for (int k = 0; k < mb.ramSlots && k < 4; k++) {
            float bc = 5.9f + 0.62f * k;
            box(0.55f, 0.9f, bc - 0.17f, bc + 0.17f, 4.9f, 8.7f, T_DARK, T_DARK, 0.9f, 0.9f, 0.9f);
        }
        // планки ОЗУ
        for (int i = 0; i < 4 && i < mb.ramSlots; i++) {
            PartSpec r = spec(TileComputer.RAM0 + i);
            if (r == null) continue;
            float bc = 5.9f + 0.62f * i;
            float[] col = r.ramType == 1 ? new float[]{0.35f, 0.55f, 0.95f}
                    : r.ramType == 2 ? new float[]{0.95f, 0.35f, 0.3f} : new float[]{0.95f, 0.95f, 1f};
            box(0.85f, 1.6f, bc - 0.13f, bc + 0.13f, 5.0f, 8.6f, T_RAM, T_RAM, col[0], col[1], col[2]);
        }

        // NVMe на плате
        if (stItem == ModItems.part("nvme")) {
            box(0.55f, 0.75f, 1.0f, 3.1f, 4.6f, 5.1f, T_DARK, T_DARK, 0.8f, 0.8f, 0.85f);
            box(0.75f, 0.85f, 1.2f, 2.9f, 4.7f, 5.0f, T_STICKER, T_DARK, 0.95f, 0.75f, 0.2f);
        }

        // процессор
        PartSpec cpu = spec(TileComputer.CPU);
        if (cpu != null) {
            float[] col = cpu.socket == 1 ? new float[]{0.86f, 0.87f, 0.92f}
                    : cpu.socket == 2 ? new float[]{0.95f, 0.8f, 0.45f} : new float[]{0.55f, 0.62f, 0.78f};
            box(0.8f, 1.05f, 2.95f, 4.25f, 6.35f, 7.65f, T_CHIP, T_METAL, col[0], col[1], col[2]);
        }

        // кулер
        PartSpec cool = spec(TileComputer.COOLER);
        if (cool != null) {
            if (cool.cooling < 100) {
                box(1.05f, 2.0f, 2.75f, 4.45f, 6.15f, 7.85f, T_FINS, T_FINS, 0.7f, 0.7f, 0.75f);
                box(2.0f, 2.4f, 2.75f, 4.45f, 6.15f, 7.85f, T_FANBG, T_DARK, 0.9f, 0.9f, 0.9f);
            } else if (cool.cooling < 200) {
                box(1.05f, 3.1f, 2.45f, 4.75f, 5.7f, 8.3f, T_FINS, T_FINS, 0.78f, 0.78f, 0.84f);
                box(1.05f, 1.5f, 2.6f, 4.6f, 5.9f, 8.1f, T_METAL, T_METAL, 0.85f, 0.55f, 0.35f); // медное основание
                box(3.1f, 3.5f, 2.55f, 4.65f, 5.85f, 8.15f, T_FANBG, T_DARK, 0.9f, 0.9f, 0.9f);
            } else {
                // жидкостной: помпа, трубки, радиатор сверху
                box(1.05f, 2.0f, 2.9f, 4.3f, 6.3f, 7.7f, T_RING, T_DARK, 0.9f, 0.9f, 0.95f);
                box(1.4f, 1.7f, 3.05f, 3.3f, 7.7f, 9.0f, T_DARK, T_DARK, 0.35f, 0.35f, 0.4f);
                box(1.4f, 1.7f, 3.95f, 4.2f, 7.7f, 9.0f, T_DARK, T_DARK, 0.35f, 0.35f, 0.4f);
                box(1.7f, 2.6f, 1.3f, 8.7f, 9.0f, 10.0f, T_FINS, T_FINS, 0.5f, 0.5f, 0.55f);
                for (int i = 0; i < 3; i++) box(2.6f, 2.66f, 2.0f + 2.7f * i - 0.5f, 2.0f + 2.7f * i + 0.5f, 9.0f, 10.0f, T_FANBG, T_DARK, 0.9f, 0.9f, 0.9f);
            }
        }

        // видеокарта в вертикальном креплении: вентиляторы смотрят в окно
        int tier = gpuTier();
        if (tier > 0) {
            float len = tier == 1 ? 6.0f : tier == 2 ? 7.6f : 8.8f;
            float hgt = tier == 1 ? 2.4f : tier == 2 ? 3.2f : 3.9f;
            float c0 = 2.4f - hgt / 2f, c1 = 2.4f + hgt / 2f;
            float k = tier == 1 ? 0.2f : tier == 2 ? 0.16f : 0.1f;
            box(0.9f, 1.1f, 0.6f, 0.6f + len, c0, c1, T_PCB, T_PCB, 0.2f, 0.2f, 0.24f);
            box(1.1f, 2.4f, 0.6f, 0.6f + len, c0, c1, T_SHROUD, T_SHROUD, k + 0.04f, k + 0.04f, k + 0.1f);
            int fans = tier;
            float step = len / fans;
            for (int i = 0; i < fans; i++) {
                float bc = 0.6f + step * (i + 0.5f);
                float hs = Math.min(step, hgt) * 0.44f;
                box(2.4f, 2.46f, bc - hs, bc + hs, 2.4f - hs, 2.4f + hs, T_FANBG, T_DARK, 0.9f, 0.9f, 0.9f);
            }
        }

        // Wi-Fi: карта и две антенны
        if (spec(TileComputer.NET) != null) {
            box(0.55f, 1.4f, 0.25f, 0.5f, 5.0f, 6.0f, T_PCB, T_PCB, 0.15f, 0.55f, 0.3f);
            box(1.4f, 3.0f, 0.27f, 0.42f, 5.15f, 5.3f, T_DARK, T_DARK, 0.15f, 0.15f, 0.17f);
            box(1.4f, 3.0f, 0.27f, 0.42f, 5.75f, 5.9f, T_DARK, T_DARK, 0.15f, 0.15f, 0.17f);
        }
    }

    // ===================== вентиляторы (вращаются) =====================

    private void blades() {
        PartSpec mb = spec(TileComputer.MB);
        PartSpec cool = spec(TileComputer.COOLER);
        if (mb != null && cool != null) {
            if (cool.cooling < 100) {
                fanQuad(2.43f, 3.6f, 7.0f, 0.78f, spin, T_BLADES, 1f, 1f, 1f, bright);
            } else if (cool.cooling < 200) {
                fanQuad(3.53f, 3.7f, 7.0f, 0.9f, spin, T_BLADES, 1f, 1f, 1f, bright);
            } else {
                for (int i = 0; i < 3; i++) {
                    fanQuad(2.69f, 2.0f + 2.7f * i, 9.5f, 0.5f, spin * (i % 2 == 0 ? 1f : -1f), T_BLADES, 1f, 1f, 1f, bright);
                }
            }
        }
        int tier = mb == null ? 0 : gpuTier();
        if (tier > 0) {
            float len = tier == 1 ? 6.0f : tier == 2 ? 7.6f : 8.8f;
            float hgt = tier == 1 ? 2.4f : tier == 2 ? 3.2f : 3.9f;
            float step = len / tier;
            for (int i = 0; i < tier; i++) {
                float bc = 0.6f + step * (i + 0.5f);
                float hs = Math.min(step, hgt) * 0.44f;
                fanQuad(2.49f, bc, 2.4f, hs, spin * (i % 2 == 0 ? 1f : -1f) * 1.2f, T_BLADES, 1f, 1f, 1f, bright);
            }
        }
    }

    // ===================== подсветка (светится сама) =====================

    private void glow(CaseShape s) {
        if (!power) return;
        PartSpec mb = spec(TileComputer.MB);
        // светодиодные ленты корпуса
        for (int i = 0; i < s.strips.length; i++) {
            float[] r = s.strips[i], c = rgb(i * 0.25);
            pxBox(r[0], r[1], r[2], r[3], r[4], r[5], T_WHITE, T_WHITE, c[0], c[1], c[2], 0xF000F0);
        }
        if (mb == null) return;
        // ОЗУ DDR5 и DDR4: полоска на торце планки
        for (int i = 0; i < 4 && i < mb.ramSlots; i++) {
            PartSpec r = spec(TileComputer.RAM0 + i);
            if (r == null || r.ramType < 2) continue;
            float bc = 5.9f + 0.62f * i;
            float[] c = r.ramType == 3 ? rgb(i * 0.12) : new float[]{1f, 0.25f, 0.2f};
            glowBox(1.6f, 1.66f, bc - 0.1f, bc + 0.1f, 5.1f, 8.5f, c[0], c[1], c[2]);
        }
        // жидкостный кулер: кольцо на помпе
        PartSpec cool = spec(TileComputer.COOLER);
        if (cool != null && cool.cooling >= 200) {
            float[] c = rgb(0.5);
            glowBox(2.0f, 2.06f, 3.05f, 4.15f, 6.45f, 7.55f, c[0], c[1], c[2]);
            for (int i = 0; i < 3; i++) {
                float[] cf = rgb(0.2 * i);
                glowBox(2.66f, 2.7f, 1.55f + 2.7f * i, 1.7f + 2.7f * i, 9.05f, 9.95f, cf[0], cf[1], cf[2]);
            }
        }
        // видеокарта: у старших моделей светящаяся полоса и логотип
        int tier = gpuTier();
        if (tier == 3) {
            float len = 8.8f, hgt = 3.9f;
            float c1 = 2.4f + hgt / 2f;
            float[] c = rgb(0.75);
            glowBox(2.4f, 2.5f, 0.9f, 0.6f + len - 0.3f, c1 - 0.3f, c1 - 0.12f, c[0], c[1], c[2]);
        } else if (tier == 2) {
            glowBox(2.4f, 2.45f, 1.0f, 3.5f, 2.4f - 1.6f - 0.02f, 2.4f - 1.6f + 0.1f, 0.3f, 0.85f, 0.3f);
        }
        // чипсет флагманской платы
        if (pc_.getItem(TileComputer.MB).getItem() == ModItems.part("mb_elite")) {
            float[] c = rgb(0.9);
            glowBox(1.2f, 1.26f, 6.5f, 8.5f, 1.0f, 1.12f, c[0], c[1], c[2]);
        }
    }
}
