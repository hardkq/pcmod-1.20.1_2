package ru.pcmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.pcmod.common.ItemWire;
import ru.pcmod.common.Wires;

/**
 * Предпросмотр провода. Пока провод в руке:
 * <ul>
 * <li>пиксель грани под прицелом подсвечивается кубиком будущего пина (зелёный - можно, красный - нельзя);</li>
 * <li>если первая точка уже выбрана, от неё до пикселя под прицелом тянется линия-провод по той же кривой, что и готовый провод;
 * зелёная - соединение возможно, красная - нет (причина и длина пишутся под прицелом), серая - прицел не на блоке.</li>
 * </ul>
 * Расчёт ({@link Wires#resolve}, {@link Wires#check}) тот же, что у сервера, поэтому предпросмотр не врёт.
 */
public class WirePreview {
    private static final float[] OK = {0.35F, 1.0F, 0.45F};
    private static final float[] BAD = {1.0F, 0.28F, 0.28F};
    private static final float[] IDLE = {0.85F, 0.85F, 0.85F};

    private static String hint;
    private static int hintColor;

    @SubscribeEvent
    public void onWorld(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        hint = null;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        Level w = mc.level;
        if (p == null || w == null) return;
        // провод может лежать в любой руке: сначала основная
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty() || !(held.getItem() instanceof ItemWire)) held = p.getOffhandItem();
        if (held.isEmpty() || !(held.getItem() instanceof ItemWire)) return;
        int kind = ((ItemWire) held.getItem()).kind();
        Wires.Endpoint a = Wires.fromTag(w, held.getTag(), kind);

        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        double cx = cam.x, cy = cam.y, cz = cam.z;

        Wires.Endpoint b = null;
        String err = null;
        double[] end;
        boolean onBlock = mc.hitResult instanceof BlockHitResult && mc.hitResult.getType() == HitResult.Type.BLOCK;
        if (onBlock) {
            BlockHitResult mop = (BlockHitResult) mc.hitResult;
            BlockPos bp = mop.getBlockPos();
            Vec3 hv = mop.getLocation();
            // индекс Direction.get3DDataValue совпадает с номером стороны 1.7.10
            b = Wires.resolve(w, kind, bp.getX(), bp.getY(), bp.getZ(), mop.getDirection().get3DDataValue(),
                    (float) (hv.x - bp.getX()), (float) (hv.y - bp.getY()), (float) (hv.z - bp.getZ()));
            if (b.error != null) {
                err = b.error;
                end = new double[]{hv.x, hv.y, hv.z};
                b = null;
            } else {
                end = b.pos;
                if (a != null) err = Wires.check(w, kind, a, b);
            }
        } else {
            Vec3 look = p.getViewVector(pt);
            end = new double[]{cx + look.x * 3.0D, cy + look.y * 3.0D, cz + look.z * 3.0D};
        }
        boolean ok = onBlock && err == null;
        float[] col = !onBlock ? IDLE : ok ? OK : BAD;

        // текст под прицелом
        if (err != null) {
            hint = cap(err);
            hintColor = 0xFF5555;
        } else if (a != null && b != null) {
            hint = "Length " + ((int) (Wires.distance(a.pos, b.pos) * 10.0D) / 10.0D) + " / " + Wires.maxLen(kind);
            hintColor = 0x55FF55;
        } else if (a == null && b != null) {
            hint = "Right-click: set the first point";
            hintColor = 0xDDDDDD;
        }

        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        LegacyTess tess = LegacyTess.instance;
        tess.start(bs, PcRenderTypes.SOLID, ps.last().pose(), 0f);

        // кубик будущего пина под прицелом (координаты относительно камеры)
        if (b != null && !b.existing) {
            double[] bx = Wires.pinBox(b.code);
            double g = 0.004D;
            WireDraw.box(tess, b.x + bx[0] - cx - g, b.y + bx[1] - cy - g, b.z + bx[2] - cz - g,
                    b.x + bx[3] - cx + g, b.y + bx[4] - cy + g, b.z + bx[5] - cz + g,
                    col[0], col[1], col[2], 0.75F);
        }
        // линия от первой точки до прицела
        if (a != null) {
            double[][] c = Wires.curve(a.pos, end);
            for (double[] q : c) {
                q[0] -= cx;
                q[1] -= cy;
                q[2] -= cz;
            }
            tess.setColorRGBA_F(col[0], col[1], col[2], 0.9F);
            WireDraw.curve(tess, c, 0.0D, 0.0D, 0.0D);
        }
        bs.endBatch(PcRenderTypes.SOLID);
    }

    private static String cap(String s) {
        return s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @SubscribeEvent
    public void onOverlay(RenderGuiOverlayEvent.Post e) {
        if (e.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type() || hint == null) return;
        Font fr = Minecraft.getInstance().font;
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        e.getGuiGraphics().drawString(fr, hint, (w - fr.width(hint)) / 2, h / 2 + 12, hintColor, true);
    }
}
