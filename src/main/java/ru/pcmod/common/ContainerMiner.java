package ru.pcmod.common;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Меню майнер-фермы: слоты GPU / PSU / кулер + инвентарь игрока.
 * Состояние фермы идёт клиенту через DataSlot (каждое число двумя половинками по 16 бит, как раньше «полоски прогресса»),
 * кнопки окна приходят как clickMenuButton (0 = Power, 1 = Collect), отдельные пакеты не нужны.
 */
public class ContainerMiner extends ContainerBlank {
    public static final int HEIGHT = 246, INV_X = 19, INV_Y = 166, HOTBAR_Y = 224, GPU_Y = 104, SUP_Y = 134;
    public static final int VALUES = 10;

    public final TileMiner miner;
    public final int ownSlots;
    /** Значения для экрана (на клиенте приходят с сервера). */
    public final int[] data = new int[VALUES];
    private final int[] lo = new int[VALUES], hi = new int[VALUES];
    /** Снимок значений на сервере (обновляется в broadcastChanges). */
    private int[] cache = new int[VALUES];

    public ContainerMiner(int id, Inventory inv, BlockEntity te) {
        super(ModMenus.MINER.get(), id, te);
        this.miner = (TileMiner) te;
        this.ownSlots = miner.getContainerSize();
        int g = miner.gpuSlots(), p = miner.psuSlots();
        int gx = (200 - g * 18) / 2;
        for (int i = 0; i < g; i++) addSlot(new SlotMiner(miner, i, gx + i * 18, GPU_Y));
        for (int i = 0; i < p; i++) addSlot(new SlotMiner(miner, g + i, 10 + i * 18, SUP_Y));
        for (int i = 0; i < p; i++) addSlot(new SlotMiner(miner, g + p + i, 110 + i * 18, SUP_Y));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
        for (int i = 0; i < VALUES; i++) {
            final int k = i;
            addDataSlot(new DataSlot() {
                @Override public int get() { return cache[k] & 0xFFFF; }
                @Override public void set(int v) { lo[k] = v & 0xFFFF; data[k] = lo[k] | (hi[k] << 16); }
            });
            addDataSlot(new DataSlot() {
                @Override public int get() { return cache[k] >>> 16; }
                @Override public void set(int v) { hi[k] = v & 0xFFFF; data[k] = lo[k] | (hi[k] << 16); }
            });
        }
        if (miner.getLevel() != null && !miner.getLevel().isClientSide) cache = miner.syncValues();
    }

    public ContainerMiner(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    public void broadcastChanges() {
        if (miner.getLevel() != null && !miner.getLevel().isClientSide) cache = miner.syncValues();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (miner.getLevel() == null || miner.getLevel().isClientSide) return false;
        String msg = id == 0 ? miner.togglePower() : id == 1 ? miner.collect(p) : null;
        if (msg != null) p.displayClientMessage(Component.literal(msg), false);
        return true;
    }

    @Override
    public boolean stillValid(Player p) {
        return miner.stillValid(p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int idx) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = super.slots.get(idx);
        if (slot != null && slot.hasItem()) {
            ItemStack st = slot.getItem();
            result = st.copy();
            if (idx < ownSlots) {
                if (!moveItemStackTo(st, ownSlots, super.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                boolean moved = false;
                for (int i = 0; i < ownSlots; i++) {
                    Slot s = super.slots.get(i);
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
