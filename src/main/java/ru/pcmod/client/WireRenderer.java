package ru.pcmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.pcmod.common.BlockAt;
import ru.pcmod.common.Ports;
import ru.pcmod.common.TileConnector;
import ru.pcmod.common.Wires;

/**
 * Рисует провода коннектора и пины его ячейки. Провод - тонкая лента шириной 1 пиксель (1/16 блока), повёрнутая к камере,
 * по почти натянутой дуге между точками крепления (стойка или пиксель на грани). Пин - кубик 1x1x1 пиксель на грани опоры.
 * Каждый провод рисует один из его концов ({@link Wires#owns}), поэтому дублей нет.
 */
public class WireRenderer implements BlockEntityRenderer<TileConnector> {

    @Override
    public void render(TileConnector t, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Level w = t.getLevel();
        if (w == null || t.links.isEmpty()) return;
        BlockPos tp = t.getBlockPos();
        int tx = tp.getX(), ty = tp.getY(), tz = tp.getZ();
        float br = Math.max(0.45F, Math.max(LightTexture.sky(light), LightTexture.block(light)) / 15.0F);
        // камера в системе координат блока
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double cx = cam.x - tx, cy = cam.y - ty, cz = cam.z - tz;
        double ox = tx, oy = ty, oz = tz;

        LegacyTess tess = LegacyTess.instance;
        tess.start(buf, PcRenderTypes.SOLID, ps.last().pose(), 0f);

        if (Wires.isPinCell(w, tx, ty, tz)) {
            for (int code : t.pinCodes()) {
                int pk = Wires.kindOf(w, tx, ty, tz, code);
                if (pk == Ports.NONE) continue;
                float[] col = pk == Ports.VIDEO ? WireDraw.VIDEO : WireDraw.NET;
                double[] b = Wires.pinBox(code);
                WireDraw.box(tess, b[0], b[1], b[2], b[3], b[4], b[5],
                        col[0] * br, col[1] * br, col[2] * br, 1.0F);
            }
        }

        for (int[] l : t.links) {
            if (!Wires.owns(tx, ty, tz, l[3], l[0], l[1], l[2], l[4])) continue;
            if (Wires.badY(w, l[1]) || !BlockAt.loaded(w, l[0], l[1], l[2])) continue;
            int kind = Wires.kindOf(w, tx, ty, tz, l[3]);
            if (kind == Ports.NONE || Wires.kindOf(w, l[0], l[1], l[2], l[4]) != kind) continue;
            float[] col = kind == Ports.VIDEO ? WireDraw.VIDEO : WireDraw.NET;
            tess.setColorRGBA_F(col[0] * br, col[1] * br, col[2] * br, 1.0F);
            double[] a = Wires.anchor(w, tx, ty, tz, l[3]);
            double[] b = Wires.anchor(w, l[0], l[1], l[2], l[4]);
            double[][] c = Wires.curve(a, b);
            for (double[] p : c) { // в систему координат блока
                p[0] -= ox;
                p[1] -= oy;
                p[2] -= oz;
            }
            WireDraw.curve(tess, c, cx, cy, cz);
        }
    }

    /** Было 16384 квадрата (128 блоков). */
    @Override
    public int getViewDistance() {
        return 128;
    }

    /** Провод может уходить далеко от блока: не отсекать по рамке самого блока. */
    @Override
    public boolean shouldRenderOffScreen(TileConnector t) {
        return true;
    }
}
