package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public abstract class BlockNetDevice extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final int tier;
    public final String kind;
    public final int gui;

    protected BlockNetDevice(String kind, int tier, int gui) {
        super(Properties.of().strength(2.0F, 8.0F).sound(SoundType.METAL));
        this.kind = kind; this.tier = tier; this.gui = gui;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Override public InteractionResult use(BlockState st, Level level, BlockPos pos, Player pl, InteractionHand hand, BlockHitResult hit) {
        if (Wires.holdingWire(pl)) return InteractionResult.PASS;
        if (!level.isClientSide && pl instanceof ServerPlayer sp) GuiHandler.open(sp, gui, pos);
        return InteractionResult.SUCCESS;
    }
}
