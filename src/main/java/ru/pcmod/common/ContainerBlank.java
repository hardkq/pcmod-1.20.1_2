package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Пустое меню для GUI настроек роутера и сервера (и база для меню сервера и фермы).
 * Конструкторы: (id, Inventory, BlockEntity) - сервер (GuiHandler), (id, Inventory, FriendlyByteBuf) - клиент (ModMenus).
 */
public class ContainerBlank extends AbstractContainerMenu {
    public final BlockEntity tile;

    public ContainerBlank(int id, Inventory inv, BlockEntity te) {
        this(ModMenus.NET.get(), id, te);
    }

    public ContainerBlank(int id, Inventory inv, FriendlyByteBuf buf) {
        this(ModMenus.NET.get(), id, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    protected ContainerBlank(MenuType<?> type, int id, BlockEntity te) {
        super(type, id);
        this.tile = te;
    }

    /** Блок-сущность по позиции из буфера (клиент). */
    static BlockEntity at(Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return inv.player.level().getBlockEntity(pos);
    }

    @Override
    public boolean stillValid(Player p) {
        Level w = tile.getLevel();
        BlockPos pos = tile.getBlockPos();
        return w != null && w.getBlockEntity(pos) == tile
                && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int idx) {
        return ItemStack.EMPTY;
    }
}
