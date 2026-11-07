package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class BlockFiber extends BlockNetDevice {
    public BlockFiber() { super("fiber", 0, 0); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileFiber(pos, st); }

    @Override public InteractionResult use(BlockState st, Level level, BlockPos pos, Player pl, InteractionHand hand, BlockHitResult hit) {
        if (Wires.holdingWire(pl)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TileFiber t)) return InteractionResult.SUCCESS;
        ItemStack held = pl.getMainHandItem();
        if (!held.isEmpty() && held.getItem() instanceof ItemFiberSpool) {
            ItemFiberSpool.use(pl, held, level, t);
            return InteractionResult.SUCCESS;
        }
        if (pl instanceof ServerPlayer sp) {
            if (t.linked) {
                boolean up = Net.fiberPartner(level, pos.getX(), pos.getY(), pos.getZ()) != null;
                sp.sendSystemMessage(Component.literal("Fiber bridge: linked to " + t.px + ", " + t.py + ", " + t.pz
                    + " (" + t.distance() + " blocks), " + (up ? "link is UP" : "link DOWN: other end not loaded")));
            } else sp.sendSystemMessage(Component.literal("Fiber bridge: not linked. Use a Fiber Spool on two bridges."));
        }
        return InteractionResult.SUCCESS;
    }

    @Override public void onRemove(BlockState st, Level level, BlockPos pos, BlockState ns, boolean moving) {
        if (!st.is(ns.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof TileFiber t) t.unlink();
        super.onRemove(st, level, pos, ns, moving);
    }
}
