package ru.pcmod.common;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/** Клиент -> сервер. type: 0 клавиша, 1 питание, 2 reset, 3 открыть железо, 4 открыть монитор, 5 клик мыши (c = x, key = y клетки), 6 курсор над экраном (c = x, key = y клетки; c = 0xFFFF - вне экрана). */
public class PacketAction {
    public final int x, y, z, type, key;
    public final char c;

    public PacketAction(int x, int y, int z, int type, char c, int key) {
        this.x = x; this.y = y; this.z = z; this.type = type; this.c = c; this.key = key;
    }

    public static void encode(PacketAction m, FriendlyByteBuf b) {
        b.writeInt(m.x); b.writeInt(m.y); b.writeInt(m.z);
        b.writeByte(m.type); b.writeChar(m.c); b.writeInt(m.key);
    }

    public static PacketAction decode(FriendlyByteBuf b) {
        int x = b.readInt(), y = b.readInt(), z = b.readInt();
        int type = b.readByte();
        char c = b.readChar();
        return new PacketAction(x, y, z, type, c, b.readInt());
    }

    /** enqueueWork выполняет обработку в основном потоке сервера: очередь drain() больше не нужна. */
    public static void handle(PacketAction m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> run(ctx.getSender(), m));
        ctx.setPacketHandled(true);
    }

    private static void run(ServerPlayer p, PacketAction m) {
        if (p == null) return;
        Level w = p.level();
        BlockPos pos = new BlockPos(m.x, m.y, m.z);
        if (!w.hasChunkAt(pos)) return;
        BlockEntity te = w.getBlockEntity(pos);
        if (!(te instanceof TileComputer)) return;
        if (p.distanceToSqr(m.x + 0.5, m.y + 0.5, m.z + 0.5) > 64) return;
        boolean onScreen = p.containerMenu instanceof ContainerScreen;
        boolean onHw = p.containerMenu instanceof ContainerComputer;
        if (!onScreen && !onHw) return;
        TileComputer tc = (TileComputer) te;
        switch (m.type) {
            case 0:
            case 5:
            case 6:
                if (onScreen) tc.handleAction(p, m.type, m.c, m.key);
                break;
            case 1:
            case 2:
                tc.handleAction(m.type, m.c, m.key);
                break;
            case 3:
                GuiHandler.open(p, 0, pos);
                break;
            case 4:
                if (tc.monitorType() < 0) {
                    p.sendSystemMessage(Component.literal("No monitor connected. Place a monitor next to the PC case or link it with video cables."));
                } else {
                    GuiHandler.open(p, 1, pos);
                }
                break;
            default:
                break;
        }
    }
}
