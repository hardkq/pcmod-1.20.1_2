package ru.pcmod.common;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TileMiner extends BlockEntity implements Container {
    public static final int MAX_SLOTS = 12;
    public static final int OFF = 0, MINING = 1, NO_GPU = 2, POWER = 3, HOT = 4, NO_NET = 5, FULL = 6;

    public final ItemStack[] inv = new ItemStack[MAX_SLOTS];
    public boolean on, hot, online;
    public double temp = 25.0, pending;
    public long stored;
    public String owner = "", ownerName = "";
    public int status, hashNow, baseHash, market = 100, incomeX10, load, psuW, gpuCount, wattsGpu, cooling = 10;
    public int timer, netTimer, saveTimer;

    public TileMiner(BlockPos pos, BlockState st) {
        super(ModBlockEntities.MINER.get(), pos, st);
        java.util.Arrays.fill(inv, ItemStack.EMPTY);
    }

    public int tier() { return getBlockState().getBlock() instanceof BlockNetDevice d ? d.tier : 0; }
    public int gpuSlots() { return tier() == 0 ? 4 : 8; }
    public int psuSlots() { return tier() == 0 ? 1 : 2; }
    public long cap() { return tier() == 0 ? 20000L : 60000L; }

    public static PartSpec specOf(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() instanceof ItemPart p ? p.spec : null;
    }
    public PartSpec spec(int i) { return specOf(inv[i]); }
    public PartSpec.Cat kindOf(int i) {
        if (i < gpuSlots()) return PartSpec.Cat.GPU;
        if (i < gpuSlots() + psuSlots()) return PartSpec.Cat.PSU;
        return PartSpec.Cat.COOLER;
    }

    public static int market(Level w) {
        long day = w == null ? 0 : w.getGameTime() / 24000L;
        Random r = new Random(day * -7046029254386353131L + 7146057691288625177L);
        r.nextInt();
        return 60 + r.nextInt(101);
    }

    public void evaluate() {
        int gpus = 0, hash = 0, watts = 0, psu = 0, cool = 0;
        int g = gpuSlots(), p = psuSlots(), n = getContainerSize();
        for (int i = 0; i < g; i++) { PartSpec s = spec(i); if (s != null) { gpus++; hash += s.hash; watts += s.watts; } }
        for (int i = g; i < g + p; i++) { PartSpec s = spec(i); if (s != null) psu += s.watts; }
        for (int i = g + p; i < n; i++) { PartSpec s = spec(i); if (s != null) cool += s.cooling; }
        gpuCount = gpus; wattsGpu = watts; baseHash = hash;
        load = gpus == 0 ? 0 : watts + 20 * (tier() + 1);
        psuW = psu;
        cooling = Math.max(10, cool * 4);
        market = market(level);
        if (!on) status = hot ? HOT : OFF;
        else if (gpus == 0) status = NO_GPU;
        else if (psu < load) status = POWER;
        else if (!online) status = NO_NET;
        else if (stored >= cap()) status = FULL;
        else status = MINING;
        hashNow = status == MINING ? (temp >= 90.0 ? baseHash / 2 : baseHash) : 0;
        incomeX10 = (int) Math.round(hashNow * 0.5 * market / 100.0 * 10.0);
    }

    /** Раз в тик на сервере (ticker в BlockMiner). */
    public void tick() {
        if (level == null || level.isClientSide) return;
        if (++timer >= 20) { timer = 0; step(); }
    }

    public void step() {
        if (++netTimer >= 5) {
            netTimer = 0;
            BlockPos p = getBlockPos();
            online = Net.onlineAt(level, p.getX(), p.getY(), p.getZ());
        }
        evaluate();
        double target = 25.0;
        if (status == MINING) target = 25.0 + wattsGpu * 45.0 / cooling;
        else if (on && gpuCount > 0 && status != POWER) target = 25.0 + wattsGpu * 45.0 / cooling * 0.15;
        temp += (target - temp) * 0.1;
        if (on && temp >= 100.0) { on = false; hot = true; temp = 100.0; }
        if (status == MINING) {
            pending += hashNow * 0.5 * market / 100.0 / 3600.0;
            long whole = (long) pending;
            if (whole > 0) { pending -= whole; stored = Math.min(cap(), stored + whole); }
        }
        evaluate();
        if (++saveTimer >= 10) { saveTimer = 0; setChanged(); }
    }

    public int[] syncValues() {
        evaluate();
        return new int[]{status, on ? 1 : 0, hashNow, market, incomeX10, (int) temp, load, psuW,
            (int) Math.min(stored, 2000000000L), (int) cap()};
    }

    public String togglePower() {
        if (on) on = false;
        else {
            if (temp > 80.0) return "Farm is too hot (" + (int) temp + " C), let it cool down first";
            on = true; hot = false;
        }
        setChanged(); evaluate();
        return null;
    }

    public String collect(Player pl) {
        if (stored <= 0) return "Nothing to collect yet";
        String uuid = pl.getUUID().toString();
        if (owner.length() > 0 && !owner.equals(uuid)) return "Only the owner (" + ownerName + ") can collect the earnings";
        Bank bank = Bank.get();
        if (bank == null) return "Bank is unavailable";
        if (owner.length() == 0) { owner = uuid; ownerName = pl.getName().getString(); }
        long room = 2000000000L - bank.balance(uuid);
        long take = Math.min(stored, room);
        if (take <= 0) return "Account limit reached";
        bank.rename(uuid, pl.getName().getString());
        bank.setBalance(uuid, bank.balance(uuid) + take);
        bank.log(uuid, "+" + Bank.fmt(take) + " mining");
        stored -= take;
        setChanged();
        return "Collected " + Bank.fmt(take) + " to your bank account";
    }

    public ItemStack takeSilently(int i) {
        if (i < 0 || i >= MAX_SLOTS) return ItemStack.EMPTY;
        ItemStack s = inv[i]; inv[i] = ItemStack.EMPTY; return s;
    }

    // --- Container ---
    @Override public int getContainerSize() { return gpuSlots() + psuSlots() * 2 ; }
    @Override public boolean isEmpty() { for (ItemStack s : inv) if (s != null && !s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int i) { return i >= 0 && i < MAX_SLOTS ? inv[i] : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int i, int n) {
        if (i < 0 || i >= MAX_SLOTS || inv[i].isEmpty()) return ItemStack.EMPTY;
        ItemStack r;
        if (inv[i].getCount() <= n) { r = inv[i]; inv[i] = ItemStack.EMPTY; }
        else { r = inv[i].split(n); if (inv[i].isEmpty()) inv[i] = ItemStack.EMPTY; }
        setChanged();
        return r;
    }
    @Override public ItemStack removeItemNoUpdate(int i) {
        if (i < 0 || i >= MAX_SLOTS) return ItemStack.EMPTY;
        ItemStack s = inv[i]; inv[i] = ItemStack.EMPTY;
        if (!s.isEmpty()) setChanged();
        return s;
    }
    @Override public void setItem(int i, ItemStack s) {
        if (i < 0 || i >= MAX_SLOTS) return;
        inv[i] = s == null ? ItemStack.EMPTY : s;
        if (inv[i].getCount() > 1) inv[i].setCount(1);
        setChanged();
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean stillValid(Player p) {
        BlockPos b = getBlockPos();
        return level != null && level.getBlockEntity(b) == this && p.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5) <= 64.0;
    }
    @Override public boolean canPlaceItem(int i, ItemStack s) {
        PartSpec sp = specOf(s);
        return sp != null && i >= 0 && i < getContainerSize() && sp.cat == kindOf(i) && (sp.cat != PartSpec.Cat.GPU || sp.hash > 0);
    }
    @Override public void clearContent() { java.util.Arrays.fill(inv, ItemStack.EMPTY); }
    public Component getName() { return Component.literal("Miner Farm"); }

    // --- NBT ---
    @Override public void load(CompoundTag t) {
        super.load(t);
        java.util.Arrays.fill(inv, ItemStack.EMPTY);
        ListTag l = t.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            int s = c.getByte("Slot") & 255;
            if (s < MAX_SLOTS) inv[s] = ItemStack.of(c);
        }
        on = t.getBoolean("on"); hot = t.getBoolean("hot");
        temp = t.contains("temp") ? t.getDouble("temp") : 25.0;
        stored = t.getLong("stored"); pending = t.getDouble("pending");
        owner = t.getString("owner"); ownerName = t.getString("ownerName");
    }

    @Override protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ListTag l = new ListTag();
        for (int i = 0; i < MAX_SLOTS; i++) if (!inv[i].isEmpty()) {
            CompoundTag c = new CompoundTag();
            c.putByte("Slot", (byte) i);
            inv[i].save(c);
            l.add(c);
        }
        t.put("Items", l);
        t.putBoolean("on", on); t.putBoolean("hot", hot); t.putDouble("temp", temp);
        t.putLong("stored", stored); t.putDouble("pending", pending);
        t.putString("owner", owner); t.putString("ownerName", ownerName);
    }
}
