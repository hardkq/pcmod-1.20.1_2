package ru.pcmod.common;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Слот для компонента: пока ПК включён, менять железо нельзя. */
public class SlotPart extends Slot {
    private final TileComputer tile;

    public SlotPart(TileComputer t, int index, int x, int y) {
        super(t, index, x, y);
        this.tile = t;
    }

    @Override
    public boolean mayPlace(ItemStack s) {
        return (!tile.isOn() || hotSwap()) && tile.canPlaceItem(getSlotIndex(), s);
    }

    @Override
    public boolean mayPickup(Player p) {
        return !tile.isOn() || hotSwap();
    }

    /** Дискету с вирусами можно вставлять и вынимать на включённом ПК (как флешку). */
    private boolean hotSwap() {
        return getSlotIndex() == TileComputer.DISK;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
