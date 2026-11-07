package ru.pcmod.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ContainerComputer extends AbstractContainerMenu {
    public final TileComputer tile;
    /** x,y каждого слота компонентов (индекс = номер слота TileComputer). */
    public static final int[][] POS = {
            {8, 22},   // MB
            {34, 22},  // CPU
            {60, 22},  // COOLER
            {8, 58},   // RAM1
            {34, 58},  // RAM2
            {60, 58},  // RAM3
            {86, 58},  // RAM4
            {86, 22},  // GPU
            {138, 22}, // STORAGE
            {112, 22}, // PSU
            {138, 58}, // MEDIA
            {112, 58}, // NET (Wi-Fi модуль)
            {152, 40}  // DISK (вирусная дискета, можно менять на включённом ПК)
    };

    public ContainerComputer(int id, Inventory inv, BlockEntity te) {
        super(ModMenus.COMPUTER.get(), id);
        this.tile = (TileComputer) te;
        for (int i = 0; i < TileComputer.SLOTS; i++) {
            addSlot(new SlotPart(tile, i, POS[i][0], POS[i][1]));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 140 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 198));
        }
    }

    public ContainerComputer(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    public boolean stillValid(Player p) {
        return tile.stillValid(p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int idx) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(idx);
        if (slot != null && slot.hasItem()) {
            ItemStack st = slot.getItem();
            result = st.copy();
            if (idx < TileComputer.SLOTS) {
                if (!moveItemStackTo(st, TileComputer.SLOTS, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                boolean moved = false;
                for (int i = 0; i < TileComputer.SLOTS; i++) {
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
