package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** links: int[5] = {x, y, z, code, extra}. Рендер-дистанция 128 блоков (было 16384 кв.) — задаётся в BlockEntityRenderer.getViewDistance (этап 6). */
public class TileConnector extends BlockEntity {
    public final List<int[]> links = new ArrayList<>();

    public TileConnector(BlockPos pos, BlockState st) { super(ModBlockEntities.CABLE.get(), pos, st); }

    public boolean has(int a, int b, int c, int d, int e) {
        for (int[] l : links) if (l[0] == a && l[1] == b && l[2] == c && l[3] == d && l[4] == e) return true;
        return false;
    }
    public boolean hasCell(int a, int b, int c) {
        for (int[] l : links) if (l[0] == a && l[1] == b && l[2] == c) return true;
        return false;
    }
    public boolean add(int a, int b, int c, int d, int e) {
        if (has(a, b, c, d, e)) return false;
        links.add(new int[]{a, b, c, d, e});
        return true;
    }
    public boolean remove(int a, int b, int c, int d, int e) {
        for (int i = 0; i < links.size(); i++) {
            int[] l = links.get(i);
            if (l[0] == a && l[1] == b && l[2] == c && l[3] == d && l[4] == e) { links.remove(i); return true; }
        }
        return false;
    }
    public int countAt(int code) {
        int n = 0;
        for (int[] l : links) if (l[3] == code) n++;
        return n;
    }
    public List<Integer> pinCodes() {
        List<Integer> r = new ArrayList<>();
        for (int[] l : links) if (l[3] >= 0 && !r.contains(l[3])) r.add(l[3]);
        return r;
    }

    public void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    @Override public void load(CompoundTag t) {
        super.load(t);
        links.clear();
        if (t.contains("wires")) {
            int[] a = t.getIntArray("wires");
            for (int i = 0; i + 4 < a.length; i += 5) links.add(new int[]{a[i], a[i + 1], a[i + 2], a[i + 3], a[i + 4]});
        } else {
            int[] a = t.getIntArray("links"); // старый формат
            for (int i = 0; i + 2 < a.length; i += 3) links.add(new int[]{a[i], a[i + 1], a[i + 2], -1, -1});
        }
    }

    @Override protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        int[] a = new int[links.size() * 5];
        for (int i = 0; i < links.size(); i++) for (int k = 0; k < 5; k++) a[i * 5 + k] = links.get(i)[k];
        t.putIntArray("wires", a);
    }

    @Override public CompoundTag getUpdateTag() { CompoundTag t = new CompoundTag(); saveAdditional(t); return t; }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag t = pkt.getTag();
        if (t != null) load(t);
        if (level != null) level.setBlocksDirty(getBlockPos(), getBlockState(), getBlockState());
    }

    @Override public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        int x = p.getX(), y = p.getY(), z = p.getZ();
        double x0 = x, y0 = y, z0 = z, x1 = x + 1, y1 = y + 1, z1 = z + 1;
        for (int[] l : links) {
            if (!Wires.owns(x, y, z, l[3], l[0], l[1], l[2], l[4])) continue;
            double dx = l[0] - x, dz = l[2] - z;
            double pad = Math.min(3.0, 1.0 + 0.03 * Math.sqrt(dx * dx + dz * dz));
            x0 = Math.min(x0, l[0]); z0 = Math.min(z0, l[2]);
            x1 = Math.max(x1, l[0] + 1); z1 = Math.max(z1, l[2] + 1);
            y0 = Math.min(y0, Math.min(y, l[1]) - pad);
            y1 = Math.max(y1, l[1] + 1);
        }
        return new AABB(x0, y0, z0, x1, y1, z1);
    }
}
