package ru.pcmod.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Банк мира: счета игроков (по UUID) в «консах», купюры, пополнение и снятие.
 * Данные лежат в сохранении мира (SavedData основного мира), поэтому общие для всех ПК.
 * Здесь же хранятся счётчики покупок магазина за текущий период ротации каталога.
 */
public class Bank extends SavedData {
    public static final String ID = "pcmod_bank";
    /** Номиналы купюр (индекс = предмет bill_N, см. ModItems.BILLS). */
    public static final int[] DENOM = {1, 5, 10, 50, 100, 500, 1000};
    public static final long MAX_BALANCE = 2000000000L;
    public static final long MAX_WITHDRAW = 100000L;
    static final int LOG_MAX = 8;

    /** Результат операции: ok + текст для экрана. */
    public static final class Res {
        public final boolean ok;
        public final String msg;

        public Res(boolean ok, String msg) {
            this.ok = ok;
            this.msg = msg;
        }
    }

    private final Map<String, Long> bal = new HashMap<String, Long>();
    private final Map<String, String> names = new HashMap<String, String>();
    private final Map<String, List<String>> logs = new HashMap<String, List<String>>();
    /** Покупки магазина в текущем периоде: "uuid|offer" -> сколько куплено игроком; "offer" -> продано всего. */
    private final Map<String, Integer> bought = new HashMap<String, Integer>();
    private final Map<String, Integer> sold = new HashMap<String, Integer>();
    private long epoch = -1;

    public Bank() {
    }

    public static Bank load(CompoundTag tag) {
        Bank b = new Bank();
        b.readFromNBT(tag);
        return b;
    }

    /** Банк основного мира или null, если сервер ещё не запущен. */
    public static Bank get() {
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return null;
        ServerLevel w = srv.overworld();
        if (w == null) return null;
        return w.getDataStorage().computeIfAbsent(Bank::load, Bank::new, ID);
    }

    // ===================== счета =====================
    public long balance(String id) {
        Long v = bal.get(id);
        return v == null ? 0L : v.longValue();
    }

    public void setBalance(String id, long v) {
        bal.put(id, Long.valueOf(Math.max(0L, Math.min(MAX_BALANCE, v))));
        setDirty();
    }

    public void rename(String id, String name) {
        if (name != null && !name.equals(names.get(id))) {
            names.put(id, name);
            setDirty();
        }
    }

    public void log(String id, String text) {
        List<String> l = logs.get(id);
        if (l == null) {
            l = new ArrayList<String>();
            logs.put(id, l);
        }
        l.add(text);
        while (l.size() > LOG_MAX) l.remove(0);
        setDirty();
    }

    /** Последние операции, новые в конце. */
    public List<String> history(String id) {
        List<String> l = logs.get(id);
        return l == null ? new ArrayList<String>() : l;
    }

    // ===================== периоды магазина =====================
    /** Новый период каталога: счётчики покупок обнуляются. */
    public void roll(long ep) {
        if (epoch != ep) {
            epoch = ep;
            bought.clear();
            sold.clear();
            setDirty();
        }
    }

    public int boughtBy(String id, String offer) {
        Integer v = bought.get(id + "|" + offer);
        return v == null ? 0 : v.intValue();
    }

    public int soldTotal(String offer) {
        Integer v = sold.get(offer);
        return v == null ? 0 : v.intValue();
    }

    public void addPurchase(String id, String offer, int qty) {
        String k = id + "|" + offer;
        bought.put(k, Integer.valueOf(boughtBy(id, offer) + qty));
        sold.put(offer, Integer.valueOf(soldTotal(offer) + qty));
        setDirty();
    }

    // ===================== наличные =====================
    public static int value(ItemStack s) {
        if (s == null || s.isEmpty() || !(s.getItem() instanceof ItemBill)) return 0;
        return Bank.DENOM[((ItemBill) s.getItem()).index];
    }

    /** Сколько консов наличными в основном инвентаре игрока. */
    public static long cash(Player p) {
        long sum = 0;
        List<ItemStack> inv = p.getInventory().items;
        for (int i = 0; i < inv.size(); i++) sum += (long) value(inv.get(i)) * inv.get(i).getCount();
        return sum;
    }

    /** Забирает все купюры из инвентаря, возвращает их сумму. */
    public static long takeCash(Player p) {
        long sum = 0;
        List<ItemStack> inv = p.getInventory().items;
        for (int i = 0; i < inv.size(); i++) {
            int v = value(inv.get(i));
            if (v > 0) {
                sum += (long) v * inv.get(i).getCount();
                inv.set(i, ItemStack.EMPTY);
            }
        }
        syncInventory(p);
        return sum;
    }

    public static void syncInventory(Player p) {
        p.getInventory().setChanged();
        if (p.inventoryMenu != null) p.inventoryMenu.broadcastChanges();
    }

    /** Кладёт предметы в инвентарь, остаток, не влезший в сумки, выбрасывает рядом с игроком. */
    public static void give(Player p, ItemStack st) {
        p.getInventory().add(st);
        if (!st.isEmpty()) p.spawnAtLocation(st, 0.5F);
        syncInventory(p);
    }

    /** Выдаёт сумму купюрами (крупными номиналами вперёд). */
    public static void giveCash(Player p, long amount) {
        for (int i = DENOM.length - 1; i >= 0 && amount > 0; i--) {
            long n = amount / DENOM[i];
            amount -= n * DENOM[i];
            while (n > 0) {
                int c = (int) Math.min(n, 64L);
                give(p, new ItemStack(ModItems.bill(i), c));
                n -= c;
            }
        }
    }

    public static String fmt(long v) {
        return v + " K";
    }

    // ===================== операции =====================
    /** Пополнение со своих купюр. amount < 0 — внести всё. Сдача возвращается купюрами. */
    public Res deposit(Player p, long amount) {
        String id = p.getUUID().toString();
        rename(id, p.getGameProfile().getName());
        long have = cash(p);
        if (have <= 0) return new Res(false, "No kons banknotes in your inventory");
        if (amount < 0) amount = have;
        if (amount == 0) return new Res(false, "Enter an amount");
        if (amount > have) return new Res(false, "You carry only " + fmt(have));
        if (balance(id) + amount > MAX_BALANCE) return new Res(false, "Account limit reached");
        long taken = takeCash(p);
        long change = taken - amount;
        if (change > 0) giveCash(p, change);
        setBalance(id, balance(id) + amount);
        log(id, "+" + fmt(amount) + " deposit");
        return new Res(true, "Deposited " + fmt(amount) + (change > 0 ? " (change " + fmt(change) + ")" : ""));
    }

    /** Снятие купюрами. */
    public Res withdraw(Player p, long amount) {
        String id = p.getUUID().toString();
        rename(id, p.getGameProfile().getName());
        if (amount <= 0) return new Res(false, "Enter an amount");
        if (amount > MAX_WITHDRAW) return new Res(false, "Max withdrawal is " + fmt(MAX_WITHDRAW));
        if (balance(id) < amount) return new Res(false, "Not enough funds on the account");
        setBalance(id, balance(id) - amount);
        giveCash(p, amount);
        log(id, "-" + fmt(amount) + " withdraw");
        return new Res(true, "Withdrew " + fmt(amount));
    }

    // ===================== NBT =====================
    public void readFromNBT(CompoundTag tag) {
        bal.clear();
        names.clear();
        logs.clear();
        bought.clear();
        sold.clear();
        ListTag acc = tag.getList("acc", 10);
        for (int i = 0; i < acc.size(); i++) {
            CompoundTag c = acc.getCompound(i);
            String id = c.getString("id");
            if (id.length() == 0) continue;
            bal.put(id, Long.valueOf(c.getLong("b")));
            names.put(id, c.getString("n"));
            ListTag ll = c.getList("log", 8);
            List<String> l = new ArrayList<String>();
            for (int j = 0; j < ll.size(); j++) l.add(ll.getString(j));
            logs.put(id, l);
        }
        epoch = tag.contains("epoch") ? tag.getLong("epoch") : -1L;
        ListTag bl = tag.getList("bought", 10);
        for (int i = 0; i < bl.size(); i++) {
            CompoundTag c = bl.getCompound(i);
            bought.put(c.getString("k"), Integer.valueOf(c.getInt("n")));
        }
        ListTag sl = tag.getList("sold", 10);
        for (int i = 0; i < sl.size(); i++) {
            CompoundTag c = sl.getCompound(i);
            sold.put(c.getString("k"), Integer.valueOf(c.getInt("n")));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag acc = new ListTag();
        for (Map.Entry<String, Long> e : bal.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.getKey());
            c.putLong("b", e.getValue().longValue());
            String n = names.get(e.getKey());
            c.putString("n", n == null ? "" : n);
            ListTag ll = new ListTag();
            for (String s : history(e.getKey())) ll.add(StringTag.valueOf(s));
            c.put("log", ll);
            acc.add(c);
        }
        tag.put("acc", acc);
        tag.putLong("epoch", epoch);
        ListTag bl = new ListTag();
        for (Map.Entry<String, Integer> e : bought.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey());
            c.putInt("n", e.getValue().intValue());
            bl.add(c);
        }
        tag.put("bought", bl);
        ListTag sl = new ListTag();
        for (Map.Entry<String, Integer> e : sold.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey());
            c.putInt("n", e.getValue().intValue());
            sl.add(c);
        }
        tag.put("sold", sl);
        return tag;
    }

    /** Имя игрока по UUID (для команд и экрана). */
    public String nameOf(String id) {
        String n = names.get(id);
        return n == null ? id : n;
    }
}
