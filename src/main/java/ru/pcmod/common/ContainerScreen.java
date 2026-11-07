package ru.pcmod.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Пустое меню: нужно, чтобы открыть экран монитора. */
public class ContainerScreen extends AbstractContainerMenu {
    public final TileComputer tile;

    public ContainerScreen(int id, Inventory inv, BlockEntity te) {
        super(ModMenus.SCREEN.get(), id);
        this.tile = (TileComputer) te;
    }

    public ContainerScreen(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    public boolean stillValid(Player p) {
        return tile.stillValid(p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int idx) {
        return ItemStack.EMPTY;
    }
}
