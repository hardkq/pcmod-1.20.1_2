package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

/** Серверная часть бывшего GuiHandler: gui 0 hardware, 1 screen, 2 router, 3 server, 4 miner. Клиент — Screen через MenuScreens (этап 6).
 *  Контракт меню (этап 6): Container*(int id, Inventory inv, BlockEntity te) на сервере и (int id, Inventory inv, FriendlyByteBuf buf) на клиенте. */
public final class GuiHandler {
    public static void open(ServerPlayer p, int gui, BlockPos pos) {
        BlockEntity te = p.level().getBlockEntity(pos);
        MenuProvider mp = null;
        Component title = te == null ? Component.empty() : te.getBlockState().getBlock().getName();
        switch (gui) {
            case 2 -> { if (te instanceof TileRouter) mp = new SimpleMenuProvider((id, inv, pl) -> new ContainerBlank(id, inv, te), title); }
            case 3 -> { if (te instanceof TileServer t) { t.syncClient(); mp = new SimpleMenuProvider((id, inv, pl) -> new ContainerServer(id, inv, te), title); } }
            case 4 -> { if (te instanceof TileMiner) mp = new SimpleMenuProvider((id, inv, pl) -> new ContainerMiner(id, inv, te), title); }
            default -> { if (te instanceof TileComputer t) {
                t.screen.dirty = true;
                mp = gui == 0 ? new SimpleMenuProvider((id, inv, pl) -> new ContainerComputer(id, inv, te), title)
                              : new SimpleMenuProvider((id, inv, pl) -> new ContainerScreen(id, inv, te), title);
            } }
        }
        if (mp != null) NetworkHooks.openScreen(p, mp, pos);
    }
    private GuiHandler() {}
}
