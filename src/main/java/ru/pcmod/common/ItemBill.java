package ru.pcmod.common;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Купюра «Конс». Номинал {@link Bank#DENOM}[index]; в 1.12.2 был метадатой одного предмета, теперь 7 предметов bill_1..bill_1000. */
public class ItemBill extends Item {
    public final int index;

    public ItemBill(Item.Properties props, int index) {
        super(props.stacksTo(64));
        this.index = Math.max(0, Math.min(Bank.DENOM.length - 1, index));
    }

    public int value() {
        return Bank.DENOM[index];
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
        list.add(Component.literal("§7Kons banknote: " + Bank.DENOM[index] + " K"));
        list.add(Component.literal("§7Deposit it in the PCOS 'Bank' app"));
    }
}
