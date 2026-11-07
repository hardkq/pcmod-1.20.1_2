package ru.pcmod.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Коннектор (основа сетевого и видео). Два вида (свойство PIN):
 * <ul>
 * <li>стойка 6x6 пикселей и 6 пикселей длиной на грани опорного блока (ставится предметом). SUPPORT - сторона, где
 * у коннектора опора. К свободному торцу стойки крепятся провода ({@link Wires}). Если опора - устройство мода,
 * стойку можно поставить только на порт своего типа;</li>
 * <li>ячейка с пинами (PIN = true): создаётся проводом на любом пикселе твёрдой грани и убирается сама, когда проводов
 * не остаётся. Пины 1x1x1 пиксель хранят позиции в {@link TileConnector}, рисует их {@code WireRenderer}; у самой ячейки пустая модель.</li>
 * </ul>
 * Метадата 1.12.2 (SUPPORT | 8 для пинов) заменена свойствами SUPPORT и PIN.
 * Выделение прицелом: форма ячейки с пинами = объединение кубиков пинов (отдельной подсветки одного пина, как раньше, нет).
 */
public abstract class BlockConnector extends BaseEntityBlock {
    /** Сторона опоры: от коннектора к опорному блоку (все 6 сторон). */
    public static final DirectionProperty SUPPORT = DirectionProperty.create("support", Direction.values());
    /** true - ячейка с пинами (см. описание класса). */
    public static final BooleanProperty PIN = BooleanProperty.create("pin");

    /** Сечение стойки: 5..11 из 16. */
    public static final float W0 = 5.0F / 16.0F, W1 = 11.0F / 16.0F;

    public final String name;

    protected BlockConnector(String name) {
        super(Properties.of().strength(0.4F, 2.0F).sound(SoundType.METAL).noOcclusion().noLootTable());
        this.name = name;
        registerDefaultState(stateDefinition.any().setValue(SUPPORT, Direction.DOWN).setValue(PIN, Boolean.FALSE));
    }

    /** Тип коннектора: {@link Ports#NET} или {@link Ports#VIDEO}. */
    public abstract int kind();

    /** Состояние ячейки с пинами (так её создаёт Wires). */
    public static BlockState pinState(Block connector) {
        return connector.defaultBlockState().setValue(SUPPORT, Direction.DOWN).setValue(PIN, Boolean.TRUE);
    }

    public static boolean isPin(BlockState s) {
        return s.getValue(PIN);
    }

    /** Сторона опоры стойки (0..5). */
    public static int supportIndex(BlockState s) {
        return s.getValue(SUPPORT).get3DDataValue();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(SUPPORT, PIN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return new TileConnector(pos, st);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter w, BlockPos p) {
        return true;
    }

    // ===================== установка и опора =====================

    /** Можно ли стоять на опоре в стороне m (от коннектора к опоре). Устройство мода - только по порту своего типа. */
    private boolean supported(LevelReader w, BlockPos pos, int m) {
        BlockPos sp = pos.relative(Direction.from3DDataValue(m));
        if (Wires.badY(w, sp.getY())) return false;
        BlockState ss = w.getBlockState(sp);
        if (Ports.isDevice(ss.getBlock())) return Ports.postFits(ss, m ^ 1, kind());
        return ss.isFaceSturdy(w, sp, Direction.from3DDataValue(m ^ 1));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader w, BlockPos pos) {
        return isPin(state) || supported(w, pos, supportIndex(state));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState st = defaultBlockState().setValue(SUPPORT, c.getClickedFace().getOpposite()).setValue(PIN, Boolean.FALSE);
        return st.canSurvive(c.getLevel(), c.getClickedPos()) ? st : null;
    }

    @Override
    public void neighborChanged(BlockState state, Level w, BlockPos pos, Block nb, BlockPos from, boolean moving) {
        if (w.isClientSide) return;
        if (isPin(state)) {
            TileConnector t = Wires.tile(w, pos.getX(), pos.getY(), pos.getZ());
            if (t != null) Wires.dropUnsupported(w, t);
            else w.removeBlock(pos, false);
            return;
        }
        if (!supported(w, pos, supportIndex(state))) {
            dropResources(state, w, pos);
            w.removeBlock(pos, false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level w, BlockPos pos, BlockState ns, boolean moving) {
        if (!state.is(ns.getBlock()) && !w.isClientSide) {
            BlockEntity te = w.getBlockEntity(pos);
            if (te instanceof TileConnector) Wires.removeAll(w, (TileConnector) te, true);
        }
        super.onRemove(state, w, pos, ns, moving);
    }

    /** Ячейка с пинами сама ничего не роняет: провода возвращаются предметами в {@link Wires#removeAll}. Лут-таблицы у блока нет. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return isPin(state) ? Collections.<ItemStack>emptyList() : new ArrayList<ItemStack>(Collections.singletonList(new ItemStack(this)));
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter w, BlockPos pos, BlockState state) {
        return isPin(state) ? ItemStack.EMPTY : new ItemStack(this);
    }

    @Override
    public InteractionResult use(BlockState state, Level w, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        // провода и кусачки обрабатывают сами предметы
        if (!p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty()) return InteractionResult.PASS;
        if (!w.isClientSide) p.sendSystemMessage(Component.literal(Wires.describe(w, pos.getX(), pos.getY(), pos.getZ())));
        return InteractionResult.sidedSuccess(w.isClientSide);
    }

    // ===================== форма =====================

    /** Границы стойки: от опорной грани на POST пикселей. */
    public static void bounds(int m, float[] out) {
        float lo = 0.0F, hi = (float) Wires.POST;
        if ((m & 1) == 1) {
            lo = 1.0F - (float) Wires.POST;
            hi = 1.0F;
        }
        out[0] = out[1] = out[2] = W0;
        out[3] = out[4] = out[5] = W1;
        int ax = m < 2 ? 1 : m < 4 ? 2 : 0;
        out[ax] = lo;
        out[ax + 3] = hi;
    }

    /** Проходимо: коллизии нет (было NULL_AABB). */
    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter w, BlockPos pos, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState s, BlockGetter w, BlockPos pos) {
        return Shapes.empty();
    }

    /**
     * Форма для прицела: у стойки - её коробка; у ячейки с пинами - объединение кубиков пинов (луч попадает только в них,
     * а не во всю ячейку, иначе она закрывала бы грань опоры).
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter w, BlockPos pos, CollisionContext c) {
        if (isPin(state)) {
            TileConnector t = Wires.tile(w, pos.getX(), pos.getY(), pos.getZ());
            VoxelShape r = Shapes.empty();
            if (t != null) {
                for (int code : t.pinCodes()) {
                    double[] b = Wires.pinBox(code);
                    r = Shapes.or(r, Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]));
                }
            }
            return r.isEmpty() ? Shapes.box(0.0, 0.0, 0.0, 1.0 / 16.0, 1.0 / 16.0, 1.0 / 16.0) : r;
        }
        float[] b = new float[6];
        bounds(supportIndex(state), b);
        return Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]);
    }
}
