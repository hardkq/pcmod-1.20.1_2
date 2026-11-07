package ru.pcmod.common;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Слот фермы: принимает только свою деталь (GPU, PSU или кулер). */
public class SlotMiner extends Slot {
    private final TileMiner tile;

    public SlotMiner(TileMiner t, int index, int x, int y) {
        super(t, index, x, y);
        this.tile = t;
    }

    @Override
    public boolean mayPlace(ItemStack s) {
        return tile.canPlaceItem(getSlotIndex(), s);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
