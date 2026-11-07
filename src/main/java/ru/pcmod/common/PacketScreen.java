package ru.pcmod.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import ru.pcmod.PCMod;

/** Сервер -> клиент: содержимое экрана. */
public class PacketScreen {
    public final int x, y, z;
    public final byte[] data;

    public PacketScreen(int x, int y, int z, Screen s) {
        this.x = x; this.y = y; this.z = z;
        ByteBuf b = Unpooled.buffer();
        s.write(b);
        data = new byte[b.readableBytes()];
        b.readBytes(data);
    }

    private PacketScreen(int x, int y, int z, byte[] data) {
        this.x = x; this.y = y; this.z = z; this.data = data;
    }

    public static void encode(PacketScreen m, FriendlyByteBuf b) {
        b.writeInt(m.x); b.writeInt(m.y); b.writeInt(m.z);
        b.writeInt(m.data.length);
        b.writeBytes(m.data);
    }

    public static PacketScreen decode(FriendlyByteBuf b) {
        int x = b.readInt(), y = b.readInt(), z = b.readInt();
        byte[] d = new byte[b.readInt()];
        b.readBytes(d);
        return new PacketScreen(x, y, z, d);
    }

    /** enqueueWork выполняется в главном потоке клиента. */
    public static void handle(PacketScreen m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> {
            Level w = PCMod.proxy.clientWorld();
            if (w == null) return;
            BlockPos pos = new BlockPos(m.x, m.y, m.z);
            if (!w.hasChunkAt(pos)) return;
            BlockEntity te = w.getBlockEntity(pos);
            if (te instanceof TileComputer) ((TileComputer) te).screen.read(Unpooled.wrappedBuffer(m.data));
        });
        ctx.setPacketHandled(true);
    }
}
