package ru.pcmod.common;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Катушка оптоволокна: ПКМ по первому мосту запоминает его, ПКМ по второму - связывает мосты (на любое расстояние). */
public class ItemFiberSpool extends Item {
    public ItemFiberSpool(Item.Properties props) {
        super(props.stacksTo(1));
    }

    private static void say(Player p, String m) {
        p.displayClientMessage(Component.literal(m), false);
    }

    /** Серверная сторона: выбрать первый конец или связать со вторым (вызывает BlockFiber.use). */
    public static void use(Player p, ItemStack spool, Level w, TileFiber me) {
        BlockPos mp = me.getBlockPos();
        CompoundTag tg = spool.getOrCreateTag();
        if (!tg.getBoolean("has")) {
            tg.putBoolean("has", true);
            tg.putString("dim", w.dimension().location().toString());
            tg.putInt("x", mp.getX());
            tg.putInt("y", mp.getY());
            tg.putInt("z", mp.getZ());
            say(p, "Fiber: first bridge selected (" + mp.getX() + ", " + mp.getY() + ", " + mp.getZ()
                    + "). Right-click the second bridge.");
            return;
        }
        int ox = tg.getInt("x"), oy = tg.getInt("y"), oz = tg.getInt("z");
        if (!tg.getString("dim").equals(w.dimension().location().toString())) {
            say(p, "Fiber: both bridges must be in the same dimension. Selection kept.");
            return;
        }
        if (ox == mp.getX() && oy == mp.getY() && oz == mp.getZ()) {
            say(p, "Fiber: that is the first bridge. Pick another one (or shift-use the spool in air to reset).");
            return;
        }
        BlockPos op = new BlockPos(ox, oy, oz);
        BlockEntity o = w.hasChunkAt(op) ? w.getBlockEntity(op) : null;
        if (!(o instanceof TileFiber)) {
            tg.remove("has");
            if (tg.isEmpty()) spool.setTag(null);
            say(p, "Fiber: the first bridge is gone or its chunk is not loaded. Selection cleared.");
            return;
        }
        me.linkTo((TileFiber) o);
        tg.remove("has");
        if (tg.isEmpty()) spool.setTag(null);
        say(p, "Fiber: bridges linked, distance " + me.distance() + " blocks. The network now continues through them.");
    }

    /** ПКМ в воздух сбрасывает выбранный первый мост. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level w, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!w.isClientSide && s.hasTag() && s.getTag().getBoolean("has")) {
            s.getTag().remove("has");
            if (s.getTag().isEmpty()) s.setTag(null);
            say(p, "Fiber: selection cleared.");
        }
        return InteractionResultHolder.pass(s);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level world, List<Component> list, TooltipFlag flag) {
        list.add(Component.literal("§7Right-click a Fiber Bridge, then another one:"));
        list.add(Component.literal("§7they link at any distance (same dimension)."));
        if (s.hasTag() && s.getTag().getBoolean("has")) {
            CompoundTag t = s.getTag();
            list.add(Component.literal("§aFirst bridge: " + t.getInt("x") + ", " + t.getInt("y") + ", " + t.getInt("z")));
        }
    }
}
