package ru.pcmod.common;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

/**
 * Клиент -> сервер: настройки роутера или сервера из GUI.
 * flags: 1 = Wi-Fi включён, 2 = убрать пароль, 4 = стереть данные чата (только сервер).
 * Роутер: a = SSID, b = новый пароль (пусто = не менять). Сервер: a = домен.
 */
public class PacketNetConfig {
    public final int x, y, z, flags;
    public final String a, b;

    public PacketNetConfig(int x, int y, int z, int flags, String a, String b) {
        this.x = x; this.y = y; this.z = z; this.flags = flags; this.a = a; this.b = b;
    }

    public static void encode(PacketNetConfig m, FriendlyByteBuf buf) {
        buf.writeInt(m.x); buf.writeInt(m.y); buf.writeInt(m.z); buf.writeInt(m.flags);
        buf.writeUtf(m.a);
        buf.writeUtf(m.b);
    }

    public static PacketNetConfig decode(FriendlyByteBuf buf) {
        int x = buf.readInt(), y = buf.readInt(), z = buf.readInt(), f = buf.readInt();
        return new PacketNetConfig(x, y, z, f, buf.readUtf(), buf.readUtf());
    }

    public static void handle(PacketNetConfig m, Supplier<NetworkEvent.Context> sup) {
        NetworkEvent.Context ctx = sup.get();
        ctx.enqueueWork(() -> run(ctx.getSender(), m));
        ctx.setPacketHandled(true);
    }

    private static void run(ServerPlayer p, PacketNetConfig m) {
        if (p == null || !(p.containerMenu instanceof ContainerBlank)) return;
        ContainerBlank cb = (ContainerBlank) p.containerMenu;
        BlockEntity te = cb.tile;
        BlockPos tp = te.getBlockPos();
        if (tp.getX() != m.x || tp.getY() != m.y || tp.getZ() != m.z || !cb.stillValid(p)) return;
        String err = null;
        String ok = null;
        if (te instanceof TileRouter) {
            TileRouter r = (TileRouter) te;
            // порядок параметров в порте: (ssid, pass, clearPass, wifi)
            err = r.applyConfig(m.a.trim(), m.b, (m.flags & 2) != 0, (m.flags & 1) != 0);
            ok = "Router saved: " + r.ssid + (r.pass.length() > 0 ? " (password set)" : " (open network)")
                    + (r.wifiOn ? ", Wi-Fi on" : ", Wi-Fi off");
        } else if (te instanceof TileServer) {
            TileServer s = (TileServer) te;
            err = s.applyConfig(m.a.trim(), (m.flags & 4) != 0);
            ok = "Server saved: " + s.domain + ((m.flags & 4) != 0 ? " (chat data wiped)" : "");
        }
        if (ok != null) p.sendSystemMessage(Component.literal(err != null ? err : ok));
    }
}
