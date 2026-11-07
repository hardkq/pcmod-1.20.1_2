package ru.pcmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.pcmod.common.BlockAt;
import ru.pcmod.common.BlockComputer;
import ru.pcmod.common.BlockMonitor;
import ru.pcmod.common.CaseShape;
import ru.pcmod.common.MonitorShape;
import ru.pcmod.common.MonitorType;
import ru.pcmod.common.Screen;
import ru.pcmod.common.Sprites;
import ru.pcmod.common.TileComputer;

/**
 * Рисует в мире:
 * - на лицевой грани соседнего блока монитора - живой экран ПК (ОС, рабочий стол, BIOS и т.д.);
 * - на лицевой грани корпуса - светодиод питания (выкл / запускается / работает / сбой).
 * Всё светится в темноте (полная яркость, без освещения).
 */
public class RenderComputer implements BlockEntityRenderer<TileComputer> {
    private static final int CW = 6, CH = 9;
    private static final int PW = Screen.W * CW, PH = Screen.H * CH; // 360 x 198 «пикселей» экрана
    private static final int[] PAL = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final int[] RGB = {0xFF5555, 0x55FF55, 0x5555FF, 0xFF55FF, 0x55FFFF, 0xFFFF55};

    /**
     * Область экрана и плоскости текущего монитора (задаются в drawMonitor из MonitorShape модели):
     * левый верхний угол экрана, размер одного «пикселя» экрана по X и Y, плоскость стекла и плоскость лицевой панели.
     */
    private float sx0, sy0, scx, scy, zs, zf;
    /** Дальше этого расстояния (в блоках) вместо букв рисуются цветные полосы, а иконки пропускаются. */
    private static final double TEXT_DIST = 14.0, SPRITE_DIST = 40.0, SCAN_DIST = 10.0;
    /** Номер ПК на гранях корпуса рисуется только ближе этого расстояния (в блоках). */
    private static final double ID_DIST = 24.0;
    /** Масштаб текста номера: 9 цифр (~53 «пикселя» шрифта) занимают около 0.82 блока. */
    private static final float ID_SCALE = 0.0155f;

    /** Замена glPolygonOffset: слой n поднимается над плоскостью на n * LAYER (в блоках) вдоль локального +Z. */
    private static final float LAYER = 0.0008f;
    private PoseStack ps;
    private MultiBufferSource buf;
    private final LegacyTess tess = LegacyTess.instance;
    private final CaseInterior interior = new CaseInterior();
    /** Железо внутри корпуса рисуется ближе этого расстояния (в блоках), стекло - всегда в зоне прорисовки. */
    private static final double INTERIOR_DIST = 40.0;

    @Override
    public void render(TileComputer c, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level w = c.getLevel();
        if (w == null) return;
        BlockState cst = c.getBlockState();
        if (!(cst.getBlock() instanceof BlockComputer)) return;
        CaseShape shape = ((BlockComputer) cst.getBlock()).shape();
        BlockPos cpos = c.getBlockPos();
        this.ps = ps;
        this.buf = buf;
        // x, y, z - смещение блока от камеры, как в 1.12 (начало ps уже в углу блока)
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double x = cpos.getX() - cam.x, y = cpos.getY() - cam.y, z = cpos.getZ() - cam.z;
        int face = front(BlockAt.facingIndex(cst));

        // светодиод на лицевой грани корпуса
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(angle(face)));
        drawLed(c.screen, shape);
        ps.popPose();

        // уникальный 9-значный номер ПК на всех шести гранях корпуса
        int cid = c.getComputerId();
        if (cid > 0 && x * x + y * y + z * z < ID_DIST * ID_DIST) {
            ps.pushPose();
            ps.translate(0.5, 0.5, 0.5);
            drawComputerId(cid, shape);
            ps.popPose();
        }

        // железо внутри корпуса и стекло окна
        {
            long now = System.currentTimeMillis();
            boolean power = c.screen.stage == 1 || c.screen.stage == 2;
            ps.pushPose();
            ps.translate(0.5, 0.5, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(angle(face)));
            if (x * x + y * y + z * z < INTERIOR_DIST * INTERIOR_DIST) interior.drawParts(c, shape, now, power, light, ps, buf);
            interior.drawGlass(c, shape, light, ps, buf);
            ps.popPose();
        }

        // экран на каждом подключённом мониторе (вплотную или через видеокабели)
        for (int[] mp : c.monitors()) {
            int mx = mp[0], my = mp[1], mz = mp[2];
            if (!BlockAt.loaded(w, mx, my, mz)) continue;
            BlockState ms = BlockAt.state(w, mx, my, mz);
            if (!(ms.getBlock() instanceof BlockMonitor)) continue;
            BlockMonitor bm = (BlockMonitor) ms.getBlock();
            int side = front(BlockAt.facingIndex(ms));
            // перед лицом монитора сплошной блок (например, корпус или стена) - экрана снаружи не видно
            int fx = mx + sideX(side), fz = mz + sideZ(side);
            if (BlockAt.loaded(w, fx, my, fz)) {
                BlockState fs = BlockAt.state(w, fx, my, fz);
                if (fs.isSolidRender(w, new BlockPos(fx, my, fz)) || fs.getBlock() instanceof BlockComputer) continue;
            }
            double cx = x + (mx - cpos.getX()) + 0.5, cy = y + (my - cpos.getY()) + 0.5, cz = z + (mz - cpos.getZ()) + 0.5;
            double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
            ps.pushPose();
            ps.translate(mx - cpos.getX() + 0.5, my - cpos.getY() + 0.5, mz - cpos.getZ() + 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(angle(side)));
            drawMonitor(c.screen, MonitorType.get(bm.type), bm.shape(), dist);
            ps.popPose();
        }
    }

    /** Экран монитора может висеть далеко от ПК (видеокабели): рисуем с запасом. */
    @Override
    public int getViewDistance() {
        return 96;
    }

    // ===================== геометрия =====================

    /** Сторона лица блока по метадате (как в getIcon): 2 = север, 3 = юг, 4 = запад, 5 = восток. */
    private static int front(int meta) {
        return meta < 2 || meta > 5 ? 3 : meta;
    }

    /** Поворот вокруг Y, после которого лицевая грань смотрит в локальный +Z. */
    private static float angle(int side) {
        switch (side) {
            case 5: return 90f;
            case 2: return 180f;
            case 4: return 270f;
            default: return 0f;
        }
    }

    private static int sideX(int side) {
        return side == 5 ? 1 : side == 4 ? -1 : 0;
    }

    private static int sideZ(int side) {
        return side == 3 ? 1 : side == 2 ? -1 : 0;
    }

    // ===================== рисование прямоугольников =====================

    private void begin(float layer) {
        tess.start(buf, PcRenderTypes.SOLID, ps.last().pose(), layer * LAYER);
    }

    private void beginGlow(float layer) {
        tess.start(buf, PcRenderTypes.GLOW, ps.last().pose(), layer * LAYER);
    }

    private void end() {
    }

    /** Прямоугольник в координатах блока (единицы 1/16 от левого верхнего угла грани) на плоскости z. */
    private void blockRect(float x0, float y0, float x1, float y1, int rgb, float z) {
        float lx0 = -0.5f + x0 / 16f, lx1 = -0.5f + x1 / 16f, ly0 = 0.5f - y1 / 16f, ly1 = 0.5f - y0 / 16f;
        tess.setColorOpaque_I(rgb);
        tess.addVertex(lx0, ly0, z);
        tess.addVertex(lx1, ly0, z);
        tess.addVertex(lx1, ly1, z);
        tess.addVertex(lx0, ly1, z);
    }

    /** Прямоугольник на задней стороне монитора: координаты в 1/16 блока, Y вверх, плоскость z (локальная). */
    private void backRect(float x0, float y0, float x1, float y1, int rgb, float z) {
        float lx0 = -0.5f + x0 / 16f, lx1 = -0.5f + x1 / 16f, ly0 = -0.5f + y0 / 16f, ly1 = -0.5f + y1 / 16f;
        tess.setColorOpaque_I(rgb);
        tess.addVertex(lx0, ly0, z);
        tess.addVertex(lx1, ly0, z);
        tess.addVertex(lx1, ly1, z);
        tess.addVertex(lx0, ly1, z);
    }

    /** Прямоугольник в «пикселях» экрана (0..360 x 0..198, начало слева сверху). */
    private void pxRect(float x0, float y0, float x1, float y1, int rgb, int alpha) {
        float lx0 = sx0 + x0 * scx, lx1 = sx0 + x1 * scx, ly0 = sy0 - y1 * scy, ly1 = sy0 - y0 * scy;
        if (alpha >= 255) tess.setColorOpaque_I(rgb);
        else tess.setColorRGBA_I(rgb, alpha);
        tess.addVertex(lx0, ly0, zs);
        tess.addVertex(lx1, ly0, zs);
        tess.addVertex(lx1, ly1, zs);
        tess.addVertex(lx0, ly1, zs);
    }

    // ===================== светодиод на корпусе =====================

    /** Прямоугольник на лице корпуса: координаты в пикселях корпуса (Y вверх), плоскость z. */
    private void localRect(float x0, float y0, float x1, float y1, int rgb, float z) {
        tess.setColorOpaque_I(rgb);
        tess.addVertex(x0 / 16f - 0.5f, y0 / 16f - 0.5f, z);
        tess.addVertex(x1 / 16f - 0.5f, y0 / 16f - 0.5f, z);
        tess.addVertex(x1 / 16f - 0.5f, y1 / 16f - 0.5f, z);
        tess.addVertex(x0 / 16f - 0.5f, y1 / 16f - 0.5f, z);
    }

    private void drawLed(Screen sc, CaseShape shape) {
        long now = System.currentTimeMillis();
        int col;
        switch (sc.stage) {
            case 1: col = (now / 250L) % 2L == 0L ? 0xFFAA00 : 0x553300; break;   // запускается: янтарный, мигает
            case 2: col = 0x33FF33; break;                                          // работает: зелёный
            case 3: col = (now / 160L) % 2L == 0L ? 0xFF2222 : 0x440808; break;    // сбой / ОС не найдена: красный, мигает часто
            default: col = 0x2A0E0E; break;                                         // выключен: еле тлеет
        }
        float[] l = shape.led;
        float z = shape.frontZ / 16f - 0.5f + 0.0005f;
        float m = 0.35f;
        begin(1f);
        localRect(l[0] - m, l[1] - m, l[2] + m, l[3] + m, 0x0C0C0C, z); // подложка
        end();
        begin(2f);
        localRect(l[0], l[1], l[2], l[3], col, z);
        end();
        if (sc.stage != 0) {
            // мягкое свечение вокруг включённого светодиода
            beginGlow(3f);
            tess.setColorRGBA_I(col, 0x30);
            tess.addVertex((l[0] - 0.7f) / 16f - 0.5f, (l[1] - 0.7f) / 16f - 0.5f, z);
            tess.addVertex((l[2] + 0.7f) / 16f - 0.5f, (l[1] - 0.7f) / 16f - 0.5f, z);
            tess.addVertex((l[2] + 0.7f) / 16f - 0.5f, (l[3] + 0.7f) / 16f - 0.5f, z);
            tess.addVertex((l[0] - 0.7f) / 16f - 0.5f, (l[3] + 0.7f) / 16f - 0.5f, z);
            end();
        }
    }

    // ===================== номер ПК на гранях =====================

    /**
     * Рисует номер компьютера на гранях корпуса (на тех, что указаны в {@link CaseShape#idFace}; начало координат - центр блока).
     * Табличка лежит у верхнего края грани, по её фактическим размерам: корпуса меньше блока, поэтому грань считается по габаритам модели.
     */
    private void drawComputerId(int id, CaseShape shape) {
        Font fr = Minecraft.getInstance().font;
        String txt = String.valueOf(id);
        float tw = fr.width(txt);
        float[] sz = shape.size;
        float minX = sz[0] - 8f, minY = sz[1] - 8f, minZ = sz[2] - 8f, maxX = sz[3] - 8f, maxY = sz[4] - 8f, maxZ = sz[5] - 8f;
        for (int face = 0; face < 6; face++) {
            if (!shape.idFace[face]) continue;
            // d - расстояние до грани, w x h - её размер, (cx, cy) - центр в повёрнутой системе (лицо к +Z)
            float d, w, h, cx, cy;
            switch (face) {
                case 5: d = maxZ; w = maxX - minX; h = maxY - minY; cx = (minX + maxX) / 2f; cy = (minY + maxY) / 2f; break;
                case 4: d = -minZ; w = maxX - minX; h = maxY - minY; cx = -(minX + maxX) / 2f; cy = (minY + maxY) / 2f; break;
                case 1: d = maxX; w = maxZ - minZ; h = maxY - minY; cx = -(minZ + maxZ) / 2f; cy = (minY + maxY) / 2f; break;
                case 0: d = -minX; w = maxZ - minZ; h = maxY - minY; cx = (minZ + maxZ) / 2f; cy = (minY + maxY) / 2f; break;
                case 3: d = maxY; w = maxX - minX; h = maxZ - minZ; cx = (minX + maxX) / 2f; cy = -(minZ + maxZ) / 2f; break;
                default: d = -minY; w = maxX - minX; h = maxZ - minZ; cx = (minX + maxX) / 2f; cy = (minZ + maxZ) / 2f; break;
            }
            float pw = w - 0.6f, ph = Math.min(3.5f, h * 0.4f);
            float top = cy + h / 2f - 0.75f;
            float z = d / 16f + 0.0005f;
            ps.pushPose();
            switch (face) {
                case 4: ps.mulPose(Axis.YP.rotationDegrees(180f)); break;
                case 1: ps.mulPose(Axis.YP.rotationDegrees(90f)); break;
                case 0: ps.mulPose(Axis.YP.rotationDegrees(270f)); break;
                case 3: ps.mulPose(Axis.XP.rotationDegrees(-90f)); break;
                case 2: ps.mulPose(Axis.XP.rotationDegrees(90f)); break;
                default: break;
            }
            // подложка
            begin(3f);
            frameRect(cx - pw / 2f, top - ph, cx + pw / 2f, top, 0x0A100A, z);
            end();
            begin(4f);
            frameRect(cx - pw / 2f + 0.25f, top - ph + 0.25f, cx + pw / 2f - 0.25f, top - 0.25f, 0x142014, z);
            end();
            // цифры
            float scale = ID_SCALE * Math.min(1f, (pw - 1f) / 13.1f);
            ps.translate(cx / 16f - tw * scale / 2f, (top - ph / 2f) / 16f + 4f * scale, z + 6f * LAYER);
            ps.scale(scale, -scale, scale);
            fr.drawInBatch(txt, 0f, 0f, 0xFF55FF55, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0,
                    LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }

    /** Прямоугольник в пикселях относительно центра блока (Y вверх) на плоскости z. */
    private void frameRect(float x0, float y0, float x1, float y1, int rgb, float z) {
        tess.setColorOpaque_I(rgb);
        tess.addVertex(x0 / 16f, y0 / 16f, z);
        tess.addVertex(x1 / 16f, y0 / 16f, z);
        tess.addVertex(x1 / 16f, y1 / 16f, z);
        tess.addVertex(x0 / 16f, y1 / 16f, z);
    }

    // ===================== экран на мониторе =====================

    /** Цвет символа с учётом видеокарты и типа монитора (та же логика, что в GuiMonitor). */
    private static int color(int fg, int tier, MonitorType mt) {
        if (mt.mono) return fg >= 8 ? mt.monoBright : fg == 0 ? 0x000000 : mt.monoDim;
        if (tier == 1) return fg >= 8 ? 0x33FF33 : fg == 0 ? 0x000000 : 0x1E9E1E;
        return PAL[fg & 15];
    }

    private void drawMonitor(Screen sc, MonitorType mt, MonitorShape sh, double dist) {
        long now = System.currentTimeMillis();
        // раскладка экрана и панели берётся из модели монитора
        sx0 = -0.5f + sh.scrX / 16f;
        sy0 = 0.5f - sh.scrTop / 16f;
        scx = sh.scrW / 16f / PW;
        scy = sh.scrH / 16f / PH;
        zs = sh.scrZ / 16f - 0.5f + 0.0005f;
        zf = sh.frontZ / 16f - 0.5f + 0.0005f;

        // фон экрана и индикатор питания монитора
        begin(1f);
        pxRect(0, 0, PW, PH, sc.on ? 0x000000 : 0x0B0D0B, 255);
        end();
        begin(2f);
        blockRect(sh.led[0], sh.led[1], sh.led[2], sh.led[3], sc.on ? 0x33FF33 : 0x331111, zf);
        if (mt.rgb && sc.on) {
            // RGB-подсветка игрового монитора: бегущая полоса на лицевой панели и кольцо на задней стороне
            long phase = now / 300L;
            if (sh.stripFront != null) {
                float[] st = sh.stripFront;
                int n = (int) st[5];
                for (int i = 0; i < n; i++) {
                    float x = st[0] + i * st[3];
                    blockRect(x, st[1], x + st[2], st[1] + st[4], RGB[(int) ((i + phase) % RGB.length)], zf);
                }
            }
            if (sh.backRgb != null) {
                float bz = sh.backZ / 16f - 0.5f - 0.0005f - 4f * LAYER;
                for (int i = 0; i < sh.backRgb.length; i++) {
                    float[] r = sh.backRgb[i];
                    backRect(r[0], r[1], r[2], r[3], RGB[(int) ((i * 2 + phase) % RGB.length)], bz);
                }
            }
        }
        end();

        if (!sc.on) {
            return;
        }
        if (sc.noSignal) {
            drawNoSignal(dist);
            return;
        }

        // инверсные клетки (фон выделения)
        begin(3f);
        for (int y = 0; y < Screen.H; y++) {
            int x = 0;
            while (x < Screen.W) {
                int at = sc.at[y * Screen.W + x] & 0xFF;
                if ((at & 16) == 0) {
                    x++;
                    continue;
                }
                int x2 = x + 1;
                while (x2 < Screen.W) {
                    int at2 = sc.at[y * Screen.W + x2] & 0xFF;
                    if ((at2 & 16) == 0 || (at2 & 15) != (at & 15)) break;
                    x2++;
                }
                pxRect(x * CW, y * CH, x2 * CW, y * CH + CH, color(at & 15, sc.gpuTier, mt), 255);
                x = x2;
            }
        }
        end();

        // пиксельные иконки PixelOS
        if (dist < SPRITE_DIST && sc.spN > 0) {
            begin(4f);
            drawSprites(sc, mt);
            end();
        }

        if (dist < TEXT_DIST) {
            drawText(sc, mt);
        } else {
            // издалека буквы не разобрать - рисуем слова цветными полосами
            begin(5f);
            for (int y = 0; y < Screen.H; y++) {
                int x = 0;
                while (x < Screen.W) {
                    int i = y * Screen.W + x;
                    if (sc.ch[i] == ' ') {
                        x++;
                        continue;
                    }
                    int at = sc.at[i] & 0xFF;
                    int fg = (at & 16) != 0 ? 0x000000 : color(at & 15, sc.gpuTier, mt);
                    int x2 = x + 1;
                    while (x2 < Screen.W) {
                        int j = y * Screen.W + x2;
                        if (sc.ch[j] == ' ' || (sc.at[j] & 0xFF) != at) break;
                        x2++;
                    }
                    pxRect(x * CW + 1, y * CH + 2, x2 * CW - 1, y * CH + 7, fg, 255);
                    x = x2;
                }
            }
            end();
        }

        // курсор и развёртка ЭЛТ
        boolean cursor = sc.cursor && (now / 500L) % 2L == 0L && sc.cx < Screen.W && sc.cy < Screen.H;
        boolean scan = mt.scanlines && dist < SCAN_DIST;
        if (cursor) {
            begin(7f);
            int px = sc.cx * CW, py = sc.cy * CH;
            pxRect(px, py + CH - 2, px + CW - 1, py + CH, color(7, sc.gpuTier, mt), 255);
            end();
        }
        if (scan) {
            begin(8f);
            for (int y = 1; y < PH; y += 2) pxRect(0, y, PW, y + 1, 0x000000, 0x44);
            end();
        }
    }

    /** Пиксельные иконки: каждый пиксель иконки - один пиксель экрана, соседние одинаковые склеиваются в полосы. */
    private void drawSprites(Screen sc, MonitorType mt) {
        for (int i = 0; i < sc.spN; i++) {
            int id = sc.spId[i] & 0xFF;
            if (id >= Sprites.ART.length) continue;
            String[] art = Sprites.ART[id];
            int sh = art.length, sw = art[0].length();
            int bx = (sc.spX[i] & 0xFF) * CW + (sw >= 16 ? 1 : 2);
            int by = (sc.spY[i] & 0xFF) * CH + (sh >= 16 ? 1 : 0);
            for (int yy = 0; yy < sh; yy++) {
                String row = art[yy];
                int xx = 0;
                while (xx < sw) {
                    char c = row.charAt(xx);
                    if (c == '.') {
                        xx++;
                        continue;
                    }
                    int x2 = xx + 1;
                    while (x2 < sw && row.charAt(x2) == c) x2++;
                    int ci = Character.digit(c, 16);
                    if (ci >= 0) pxRect(bx + xx, by + yy, bx + x2, by + yy + 1, color(ci, sc.gpuTier, mt), 255);
                    xx = x2;
                }
            }
        }
    }

    /** Текст шрифтом Minecraft, по одному символу в клетку, как в GuiMonitor. */
    private void drawText(Screen sc, MonitorType mt) {
        Font fr = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(sx0, sy0, zs + 5.5f * LAYER);
        ps.scale(scx, -scy, scx);
        for (int y = 0; y < Screen.H; y++) {
            for (int x = 0; x < Screen.W; x++) {
                int i = y * Screen.W + x;
                char ch = sc.ch[i];
                if (ch == ' ') continue;
                int at = sc.at[i] & 0xFF;
                int fg = (at & 16) != 0 ? 0x000000 : color(at & 15, sc.gpuTier, mt);
                String st = String.valueOf(ch);
                int gw = ch > 126 ? fr.width(st) : 0;
                if (gw > CW) {
                    ps.pushPose();
                    ps.translate(x * CW, y * CH + 1, 0f);
                    ps.scale((float) CW / gw, 1f, 1f);
                    fr.drawInBatch(st, 0f, 0f, 0xFF000000 | fg, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0,
                            LightTexture.FULL_BRIGHT);
                    ps.popPose();
                } else {
                    fr.drawInBatch(st, x * CW, y * CH + 1, 0xFF000000 | fg, false, ps.last().pose(), buf,
                            Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
                }
            }
        }
        ps.popPose();
    }

    /** «NO SIGNAL» по центру экрана. */
    private void drawNoSignal(double dist) {
        if (dist >= TEXT_DIST) {
            begin(5f);
            pxRect(140, 92, 220, 102, 0xAAAAAA, 255);
            end();
            return;
        }
        Font fr = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(sx0, sy0, zs + 5.5f * LAYER);
        ps.scale(scx, -scy, scx);
        String s = "NO SIGNAL";
        fr.drawInBatch(s, (PW - fr.width(s)) / 2f, PH / 2f - 4f, 0xFFAAAAAA, false, ps.last().pose(), buf,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }
}
