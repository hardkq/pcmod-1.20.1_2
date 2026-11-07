package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlockServer extends BlockNetDevice {
    public BlockServer(int tier) { super("server", tier, 3); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileServer(pos, st); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlockEntities.SERVER.get(), (lv, p, st, te) -> te.tick());
    }

    @Override public void onRemove(BlockState st, Level level, BlockPos pos, BlockState ns, boolean moving) {
        if (!st.is(ns.getBlock()) && level.getBlockEntity(pos) instanceof TileServer t && !level.isClientSide) {
            for (int i = 0; i < t.getContainerSize(); i++) {
                ItemStack s = t.takeDriveSilently(i);
                if (s != null && !s.isEmpty())
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s));
            }
        }
        super.onRemove(st, level, pos, ns, moving);
    }
}
