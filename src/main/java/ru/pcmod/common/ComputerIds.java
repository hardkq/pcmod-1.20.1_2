package ru.pcmod.common;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Реестр уникальных 9-значных номеров компьютеров. Хранится в данных мира (общий для всех измерений),
 * поэтому два ПК в одном мире никогда не получат одинаковый номер.
 */
public class ComputerIds extends SavedData {
    private static final String KEY = "pcmod_computer_ids";
    public static final int MIN = 100000000, MAX = 999999999;

    private final Set<Integer> used = new HashSet<Integer>();

    public ComputerIds() {
    }

    public static ComputerIds load(CompoundTag tag) {
        ComputerIds d = new ComputerIds();
        for (int id : tag.getIntArray("ids")) d.used.add(id);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        int[] a = new int[used.size()];
        int i = 0;
        for (int id : used) a[i++] = id;
        tag.putIntArray("ids", a);
        return tag;
    }

    public static boolean valid(int id) {
        return id >= MIN && id <= MAX;
    }

    /** Реестр мира. Берётся из overworld, чтобы номера были общими для всех измерений. На клиенте - пустой временный. */
    public static ComputerIds get(Level w) {
        if (!(w instanceof ServerLevel)) return new ComputerIds();
        ServerLevel base = ((ServerLevel) w).getServer().overworld();
        return base.getDataStorage().computeIfAbsent(ComputerIds::load, ComputerIds::new, KEY);
    }

    /** Выдавался ли такой номер когда-нибудь в этом мире. */
    public synchronized boolean has(int id) {
        return used.contains(id);
    }

    /** Выдать новый, ещё не занятый номер. */
    public synchronized int allocate(RandomSource r) {
        int id;
        do {
            id = MIN + r.nextInt(MAX - MIN + 1);
        } while (used.contains(id));
        used.add(id);
        setDirty();
        return id;
    }

    /** Отметить номер занятым (для уже существующего ПК). Вернёт false, если номер уже у кого-то есть в реестре. */
    public synchronized boolean register(int id) {
        if (!used.add(id)) return false;
        setDirty();
        return true;
    }
}
