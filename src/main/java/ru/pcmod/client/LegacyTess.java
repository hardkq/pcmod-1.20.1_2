package ru.pcmod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Тонкая обёртка над VertexConsumer с теми же приёмами, что были у Tessellator 1.7.10: цвет, яркость и текстурные
 * координаты задаются «текущими» значениями, вершина берёт их при добавлении. Это позволило перенести рендер железа,
 * экранов и проводов без переписывания тысяч вызовов. {@link #start} вместо startDrawingQuads: задаёт тип отрисовки, матрицу
 * (вершины сразу умножаются на неё) и сдвиг по z (замена glPolygonOffset). draw() не нужен: буфер сбрасывает MultiBufferSource.
 * Между start() и последней вершиной нельзя вызывать getBuffer другого типа (в том числе рисовать текст).
 */
final class LegacyTess {
    static final LegacyTess instance = new LegacyTess();

    private float r = 1f, g = 1f, b = 1f, a = 1f;
    private int light = 0xF000F0;
    private float lu, lv, zOff;
    private VertexConsumer vc;
    private Matrix4f mat;
    private boolean tex;

    private LegacyTess() {
    }

    /** Начать серию четырёхугольников; цвет, яркость и UV сбрасываются. */
    void start(MultiBufferSource src, RenderType type, Matrix4f pose, float zOffset) {
        r = g = b = a = 1f;
        light = 0xF000F0;
        lu = lv = 0f;
        zOff = zOffset;
        mat = pose;
        vc = src.getBuffer(type);
        tex = type.format() == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;
    }

    /** Яркость в упакованном виде getLightColor: небо в старших 16 битах, блок в младших. */
    void setBrightness(int packed) {
        light = packed;
    }

    void setColorOpaque_F(float r, float g, float b) {
        setColorRGBA_F(r, g, b, 1f);
    }

    void setColorRGBA_F(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    void setColorOpaque_I(int rgb) {
        setColorRGBA_I(rgb, 255);
    }

    void setColorRGBA_I(int rgb, int alpha) {
        r = ((rgb >> 16) & 255) / 255f;
        g = ((rgb >> 8) & 255) / 255f;
        b = (rgb & 255) / 255f;
        a = alpha / 255f;
    }

    void setNormal(float x, float y, float z) {
    }

    void addVertex(double x, double y, double z) {
        vc.vertex(mat, (float) x, (float) y, (float) (z + zOff)).color(r, g, b, a);
        if (tex) vc.uv(lu, lv).uv2(light);
        vc.endVertex();
    }

    void addVertexWithUV(double x, double y, double z, double u, double v) {
        lu = (float) u;
        lv = (float) v;
        addVertex(x, y, z);
    }
}
