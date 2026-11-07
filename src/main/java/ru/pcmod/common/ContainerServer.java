package ru.pcmod.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Меню сервера: отсеки под HDD + инвентарь игрока. Настройки (домен) идут через PacketNetConfig как раньше. */
public class ContainerServer extends ContainerBlank {
    public static final int BAY_Y = 144, INV_X = 19, INV_Y = 172, HOTBAR_Y = 230;
    public final TileServer server;
    public final int bays;

    public ContainerServer(int id, Inventory inv, BlockEntity te) {
        super(ModMenus.SERVER.get(), id, te);
        this.server = (TileServer) te;
        this.bays = server.bays();
        int x0 = bayX0(bays);
        for (int i = 0; i < bays; i++) addSlot(new SlotDrive(server, i, x0 + i * 18, BAY_Y));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    public ContainerServer(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    /** X первого отсека (ширина окна 200), отсеки центрируются. */
    public static int bayX0(int bays) {
        return (200 - bays * 18) / 2;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int idx) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(idx);
        if (slot != null && slot.hasItem()) {
            ItemStack st = slot.getItem();
            result = st.copy();
            if (idx < bays) {
                if (!slot.mayPickup(p)) return ItemStack.EMPTY;
                if (!moveItemStackTo(st, bays, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                boolean moved = false;
                for (int i = 0; i < bays; i++) {
                    Slot s = slots.get(i);
                    if (!s.hasItem() && s.mayPlace(st)) {
                        s.set(st.split(1));
                        moved = true;
                        break;
                    }
                }
                if (!moved) return ItemStack.EMPTY;
            }
            if (st.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            if (st.getCount() == result.getCount()) return ItemStack.EMPTY;
            slot.onTake(p, st);
        }
        return result;
    }
}
