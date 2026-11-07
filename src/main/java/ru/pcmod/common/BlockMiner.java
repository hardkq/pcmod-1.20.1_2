package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlockMiner extends BlockNetDevice {
    public BlockMiner(int tier) { super("miner", tier, 4); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileMiner(pos, st); }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlockEntities.MINER.get(), (lv, p, st, te) -> te.tick());
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState st, LivingEntity who, ItemStack stack) {
        super.setPlacedBy(level, pos, st, who, stack);
        if (!level.isClientSide && who instanceof Player p && level.getBlockEntity(pos) instanceof TileMiner t) {
            t.owner = p.getUUID().toString();
            t.ownerName = p.getName().getString();
            t.setChanged();
        }
    }

    @Override public void onRemove(BlockState st, Level level, BlockPos pos, BlockState ns, boolean moving) {
        if (!st.is(ns.getBlock()) && level.getBlockEntity(pos) instanceof TileMiner t && !level.isClientSide) {
            for (int i = 0; i < t.getContainerSize(); i++) {
                ItemStack s = t.takeSilently(i);
                if (s != null && !s.isEmpty())
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s));
            }
        }
        super.onRemove(st, level, pos, ns, moving);
    }
}
