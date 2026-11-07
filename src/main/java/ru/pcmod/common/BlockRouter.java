package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BlockRouter extends BlockNetDevice {
    public BlockRouter(int tier) { super("router", tier, 2); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileRouter(pos, st); }
}
