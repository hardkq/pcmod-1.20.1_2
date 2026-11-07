package ru.pcmod.common;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Отсек сервера: принимает только HDD. Лимитов на аккаунты больше нет, диск вынимается свободно. */
public class SlotDrive extends Slot {
    private final TileServer tile;

    public SlotDrive(TileServer t, int index, int x, int y) {
        super(t, index, x, y);
        this.tile = t;
    }

    @Override
    public boolean mayPlace(ItemStack s) {
        return tile.canPlaceItem(getSlotIndex(), s);
    }

    @Override
    public boolean mayPickup(Player p) {
        return tile.canRemoveDrive(getSlotIndex());
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
