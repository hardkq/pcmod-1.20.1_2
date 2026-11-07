package ru.pcmod.common;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Провод (сетевой или видео) толщиной в 1 пиксель. ПКМ по любому пикселю грани блока (сетка 16x16) запоминает первую точку,
 * ПКМ по второму пикселю - тянет между ними провод. Пока первая точка выбрана, клиент рисует линию-предпросмотр
 * до пикселя под прицелом ({@code WirePreview}). ПКМ по готовой точке (кубик-пин или стойка-коннектор) присоединяет провод к ней,
 * так что точка служит развилкой. Расходуется один провод на каждое соединение. Shift+ПКМ в воздух сбрасывает выбор.
 */
public class ItemWire extends Item {
    private final int kind;

    /** @param kind {@link Ports#NET} или {@link Ports#VIDEO} */
    public ItemWire(Item.Properties props, int kind) {
        super(props.stacksTo(16));
        this.kind = kind;
    }

    /** {@link Ports#NET} или {@link Ports#VIDEO}. */
    public int kind() {
        return kind;
    }

    private static void say(Player p, String m) {
        p.displayClientMessage(Component.literal(m), false);
    }

    private String label() {
        return kind == Ports.VIDEO ? "Video wire" : "Network wire";
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level w = ctx.getLevel();
        Player p = ctx.getPlayer();
        if (w.isClientSide || p == null) return InteractionResult.sidedSuccess(w.isClientSide);
        BlockPos pos = ctx.getClickedPos();
        ItemStack s = ctx.getItemInHand();
        Vec3 h = ctx.getClickLocation();
        float hx = (float) (h.x - pos.getX()), hy = (float) (h.y - pos.getY()), hz = (float) (h.z - pos.getZ());
        // индекс стороны совпадает с номером стороны 1.7.10/1.12.2 (0 вниз, 1 вверх, 2 север, 3 юг, 4 запад, 5 восток)
        Wires.Endpoint ep = Wires.resolve(w, kind, pos.getX(), pos.getY(), pos.getZ(), ctx.getClickedFace().get3DDataValue(), hx, hy, hz);
        if (ep.error != null) {
            say(p, label() + ": " + ep.error + ".");
            return InteractionResult.SUCCESS;
        }
        CompoundTag tg = s.getOrCreateTag();
        if (!tg.getBoolean("has")) {
            Wires.toTag(tg, w, ep);
            say(p, label() + ": first point set. Right-click the second one (a line shows where the wire will go).");
            return InteractionResult.SUCCESS;
        }
        if (!tg.getString("dim").equals(w.dimension().location().toString())) {
            say(p, label() + ": both points must be in the same dimension. Selection kept.");
            return InteractionResult.SUCCESS;
        }
        Wires.Endpoint first = Wires.fromTag(w, tg, kind);
        if (first == null) {
            clear(s);
            say(p, label() + ": the first point is gone or its chunk is not loaded. Selection cleared.");
            return InteractionResult.SUCCESS;
        }
        String err = Wires.link(w, kind, first, ep);
        if (err != null) {
            say(p, label() + ": " + err + ".");
            return InteractionResult.SUCCESS;
        }
        clear(s);
        if (!p.getAbilities().instabuild) s.shrink(1);
        say(p, label() + ": wire connected.");
        return InteractionResult.SUCCESS;
    }

    private static void clear(ItemStack s) {
        CompoundTag tg = s.getTag();
        if (tg == null) return;
        tg.remove("has");
        tg.remove("dim");
        tg.remove("x");
        tg.remove("y");
        tg.remove("z");
        tg.remove("code");
        tg.remove("pend");
        if (tg.isEmpty()) s.setTag(null);
    }

    /** Shift+ПКМ в воздух сбрасывает выбранную первую точку. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level w, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!w.isClientSide && p.isShiftKeyDown() && s.hasTag() && s.getTag().getBoolean("has")) {
            clear(s);
            say(p, label() + ": selection cleared.");
        }
        return InteractionResultHolder.pass(s);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level world, List<Component> list, TooltipFlag flag) {
        list.add(Component.literal("§7Right-click any pixel of a block face, then another one:"));
        list.add(Component.literal("§7a 1-pixel wire joins them (up to " + Wires.maxLen(kind) + " blocks)."));
        if (s.hasTag() && s.getTag().getBoolean("has")) {
            CompoundTag t = s.getTag();
            list.add(Component.literal("§aFirst point: " + t.getInt("x") + ", " + t.getInt("y") + ", " + t.getInt("z")));
        }
    }
}
