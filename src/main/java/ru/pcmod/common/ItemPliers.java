package ru.pcmod.common;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Кусачки для проводов. ПКМ, глядя на провод, - перерезать его (провод выпадает предметом).
 * Shift+ПКМ по коннектору - снять с него все провода.
 */
public class ItemPliers extends Item {
    public ItemPliers(Item.Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level w = ctx.getLevel();
        Player p = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();
        if (p == null || !(w.getBlockState(pos).getBlock() instanceof BlockConnector) || !p.isShiftKeyDown()) return InteractionResult.PASS;
        if (!w.isClientSide) {
            BlockEntity te = w.getBlockEntity(pos);
            if (te instanceof TileConnector) {
                int n = ((TileConnector) te).links.size();
                Wires.removeAll(w, (TileConnector) te, !p.getAbilities().instabuild);
                Wires.prune(w, pos.getX(), pos.getY(), pos.getZ());
                p.displayClientMessage(Component.literal("Wire cutters: " + n + " wire(s) removed from this connector."), false);
            }
        }
        return InteractionResult.sidedSuccess(w.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level w, Player p, InteractionHand hand) {
        if (!w.isClientSide) Wires.cutLooking(w, p);
        return InteractionResultHolder.pass(p.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level world, List<Component> list, TooltipFlag flag) {
        list.add(Component.literal("§7Right-click while looking at a wire to cut it."));
        list.add(Component.literal("§7Shift+right-click a connector or wire point: remove all its wires."));
    }
}
