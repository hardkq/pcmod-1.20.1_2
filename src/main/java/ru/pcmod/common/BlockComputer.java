package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockComputer extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public final int type;

    public BlockComputer(int type) {
        super(Properties.of().strength(3.0F, 10.0F).sound(SoundType.METAL).noOcclusion());
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.SOUTH));
    }

    public CaseShape shape() { return CaseShape.get(type); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) { b.add(FACING); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileComputer(pos, st); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlockEntities.COMPUTER.get(), (lv, p, st, te) -> te.tick());
    }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    private static VoxelShape box(float[] b) { return Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]); }

    @Override public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return box(shape().bounds(BlockAt.facingIndex(s)));
    }

    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        CaseShape cs = shape(); int f = BlockAt.facingIndex(s);
        VoxelShape r = Shapes.empty();
        for (int i = 0; i < cs.boxes.length; i++) r = Shapes.or(r, box(cs.box(i, f)));
        return r;
    }

    @Override public InteractionResult use(BlockState st, Level level, BlockPos pos, Player pl, InteractionHand hand, BlockHitResult hit) {
        if (Wires.holdingWire(pl)) return InteractionResult.PASS;
        if (!level.isClientSide && pl instanceof ServerPlayer sp) {
            ItemStack held = pl.getMainHandItem();
            boolean part = !held.isEmpty() && held.getItem() instanceof ItemPart;
            if (!part) {
                BlockEntity te = level.getBlockEntity(pos);
                if (!(te instanceof TileComputer tc) || tc.monitorType() < 0) {
                    sp.sendSystemMessage(Component.literal("No monitor connected. Place a monitor next to the PC case, or link it with video wires (video port - one pixel on the back panel of the case, a video port on the back of the monitor)."));
                    return InteractionResult.SUCCESS;
                }
            }
            GuiHandler.open(sp, part ? 0 : 1, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override public void onRemove(BlockState st, Level level, BlockPos pos, BlockState ns, boolean moving) {
        if (!st.is(ns.getBlock()) && level.getBlockEntity(pos) instanceof TileComputer tc && !level.isClientSide) {
            for (int i = 0; i < 13; i++) {
                ItemStack s = tc.getItem(i);
                if (s != null && !s.isEmpty())
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s));
            }
        }
        super.onRemove(st, level, pos, ns, moving);
    }
}
