package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockMonitor extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final int type;

    public BlockMonitor(int type) {
        super(Properties.of().strength(2.0F, 8.0F).sound(SoundType.METAL).noOcclusion());
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    public boolean custom() { return type > 0 && MonitorShape.get(type).custom; }
    public MonitorShape shape() { return MonitorShape.get(type); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    private static VoxelShape box(float[] b) { return Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]); }

    @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return custom() ? box(shape().bounds(BlockAt.facingIndex(s))) : Shapes.block();
    }

    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        if (!custom()) return Shapes.block();
        MonitorShape ms = shape(); int f = BlockAt.facingIndex(s);
        VoxelShape r = Shapes.empty();
        for (int i = 0; i < ms.boxes.length; i++) r = Shapes.or(r, box(ms.box(i, f)));
        return r;
    }

    @Override public InteractionResult use(BlockState st, Level level, BlockPos pos, Player pl, InteractionHand hand, BlockHitResult hit) {
        if (Wires.holdingWire(pl)) return InteractionResult.PASS;
        if (!level.isClientSide && pl instanceof ServerPlayer sp) {
            TileComputer pc = VideoLink.computerOf(level, pos.getX(), pos.getY(), pos.getZ());
            if (pc != null) GuiHandler.open(sp, 1, pc.getBlockPos());
            else sp.sendSystemMessage(Component.literal("This monitor is not connected. Place it next to a PC case, or plug a video connector into it and link it with video wires."));
        }
        return InteractionResult.SUCCESS;
    }
}
