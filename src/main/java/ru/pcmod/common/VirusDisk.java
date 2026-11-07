package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Вирусная дискета: предмет, на который игровые вирусы записываются командой 'virgen save'
 * и с которого их можно загрузить на другом компьютере ('virgen load').
 * Данные лежат в NBT самого предмета (ключ "vdisk"), поэтому дискета переносится вместе с игроком.
 */
public final class VirusDisk {
    /** Сколько вирусов помещается на одну дискету. */
    public static final int CAPACITY = 16;
    private static final String KEY = "vdisk";

    private VirusDisk() { }

    public static boolean isDisk(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() instanceof ItemPart && ((ItemPart) s.getItem()).spec.cat == PartSpec.Cat.DATA;
    }

    /** Все вирусы на дискете (повреждённые записи пропускаются). */
    public static List<Virus> read(ItemStack s) {
        List<Virus> out = new ArrayList<Virus>();
        if (!isDisk(s) || !s.hasTag() || !s.getTag().contains(KEY, 9)) return out;
        ListTag l = s.getTag().getList(KEY, 10);
        for (int i = 0; i < l.size() && out.size() < CAPACITY; i++) {
            Virus v = Virus.readFromNBT(l.getCompound(i));
            if (v != null) out.add(v);
        }
        return out;
    }

    public static int count(ItemStack s) {
        return read(s).size();
    }

    public static void write(ItemStack s, List<Virus> list) {
        if (!isDisk(s)) return;
        CompoundTag tg = s.hasTag() ? s.getTag() : new CompoundTag();
        if (list.isEmpty()) {
            tg.remove(KEY);
        } else {
            ListTag l = new ListTag();
            for (int i = 0; i < list.size() && i < CAPACITY; i++) {
                CompoundTag c = new CompoundTag();
                list.get(i).writeToNBT(c);
                l.add(c);
            }
            tg.put(KEY, l);
        }
        s.setTag(tg.isEmpty() ? null : tg);
    }

    public static boolean contains(List<Virus> list, Virus v) {
        for (Virus o : list) if (o.sameAs(v)) return true;
        return false;
    }
}
