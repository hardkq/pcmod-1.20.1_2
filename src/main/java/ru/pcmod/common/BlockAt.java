package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BlockAt {
    public static BlockState state(BlockGetter w, int x, int y, int z) { return w.getBlockState(new BlockPos(x, y, z)); }
    public static Block block(BlockGetter w, int x, int y, int z) { return state(w, x, y, z).getBlock(); }
    /** meta больше нет (BlockState); оставлено для вызовов из Ports/Wires — они переписываются на этапе 5 под BlockState. */
    public static BlockEntity tile(BlockGetter w, int x, int y, int z) { return w.getBlockEntity(new BlockPos(x, y, z)); }
    public static boolean loaded(Level w, int x, int y, int z) { return w.hasChunkAt(new BlockPos(x, y, z)); }
    public static int facingIndex(BlockState s) { return s.getValue(HorizontalDirectionalBlock.FACING).get3DDataValue(); }
    public static Direction facingFromMeta(int m) { return m >= 2 && m <= 5 ? Direction.from3DDataValue(m) : Direction.SOUTH; }
    public static Direction placementFacing(LivingEntity e) { return e.getDirection().getOpposite(); }
}
