package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TileFiber extends BlockEntity {
    public boolean linked;
    public int px, py, pz;

    public TileFiber(BlockPos pos, BlockState st) { super(ModBlockEntities.FIBER.get(), pos, st); }

    public void linkTo(TileFiber o) {
        unlink(); o.unlink();
        BlockPos a = getBlockPos(), b = o.getBlockPos();
        linked = true; px = b.getX(); py = b.getY(); pz = b.getZ();
        o.linked = true; o.px = a.getX(); o.py = a.getY(); o.pz = a.getZ();
        setChanged(); o.setChanged();
    }

    public void unlink() {
        if (!linked) return;
        BlockPos p = new BlockPos(px, py, pz), me = getBlockPos();
        if (level != null && level.hasChunkAt(p) && level.getBlockEntity(p) instanceof TileFiber o
                && o.linked && o.px == me.getX() && o.py == me.getY() && o.pz == me.getZ()) {
            o.linked = false; o.setChanged();
        }
        linked = false; setChanged();
    }

    public int distance() {
        if (!linked) return 0;
        BlockPos me = getBlockPos();
        double dx = px - me.getX(), dy = py - me.getY(), dz = pz - me.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    @Override public void load(CompoundTag t) {
        super.load(t);
        linked = t.getBoolean("linked"); px = t.getInt("px"); py = t.getInt("py"); pz = t.getInt("pz");
    }

    @Override protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putBoolean("linked", linked); t.putInt("px", px); t.putInt("py", py); t.putInt("pz", pz);
    }
}
