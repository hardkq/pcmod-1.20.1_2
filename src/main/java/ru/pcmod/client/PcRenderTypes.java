package ru.pcmod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Свои RenderType для замены GL-режима 1.12: цветные квадраты без текстуры (экраны, провода), свечение (аддитивное смешивание)
 * и текстурные для железа внутри корпуса и стекла. Класс наследует RenderType только ради доступа к protected-состояниям.
 */
public final class PcRenderTypes extends RenderType {
    private PcRenderTypes() {
        super("pcmod", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> { }, () -> { });
    }

    static final ResourceLocation INTERNALS = new ResourceLocation("pcmod", "textures/misc/internals.png");

    /** Плоский цвет, полупрозрачность, без отсечения граней и без освещения. */
    public static final RenderType SOLID = create("pcmod_solid", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            4096, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Мягкое свечение: аддитивное смешивание, глубину не пишет. */
    public static final RenderType GLOW = create("pcmod_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            1024, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private static CompositeState.CompositeStateBuilder tex(ResourceLocation rl) {
        return CompositeState.builder()
                .setShaderState(RENDERTYPE_TEXT_SHADER)
                .setTextureState(new TextureStateShard(rl, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setCullState(NO_CULL);
    }

    private static RenderType texType(String name, ResourceLocation rl, CompositeState s) {
        return create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 8192, false, true, s);
    }

    /** Железо внутри корпуса (плитки атласа internals.png). */
    public static final RenderType INTERIOR = texType("pcmod_interior", INTERNALS, tex(INTERNALS).createCompositeState(false));
    /** Лопасти вентиляторов: лежат почти вплотную к деталям, поэтому с небольшим сдвигом полигонов. */
    public static final RenderType BLADES = texType("pcmod_blades", INTERNALS,
            tex(INTERNALS).setLayeringState(POLYGON_OFFSET_LAYERING).createCompositeState(false));
    /** RGB-подсветка внутри корпуса: поверх всего остального. */
    public static final RenderType INTERIOR_GLOW = texType("pcmod_interior_glow", INTERNALS,
            tex(INTERNALS).setLayeringState(VIEW_OFFSET_Z_LAYERING).createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> GLASS = new HashMap<>();

    /** Стекло корпуса: полупрозрачная текстура, глубину не пишет. */
    public static RenderType glass(ResourceLocation rl) {
        return GLASS.computeIfAbsent(rl, k -> texType("pcmod_glass", k,
                tex(k).setLayeringState(POLYGON_OFFSET_LAYERING).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));
    }
}
