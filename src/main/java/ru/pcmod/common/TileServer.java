package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TileServer extends BlockEntity implements Container {
    public static final int MAX_BAYS = 8, BASE_HEAT_S1 = 25, BASE_HEAT_S2 = 45, DRIVE_HEAT_W = 14, MAX_BUSY_W = 30;
    public static final double PASSIVE_W = 40.0, AMBIENT = 25.0, MIN_TEMP = 12.0, WARN_TEMP = 70.0, THROTTLE_TEMP = 85.0, SHUTDOWN_TEMP = 100.0;

    public String domain = "";
    public final ItemStack[] drives = new ItemStack[MAX_BAYS];
    // зеркало состояния на клиенте (handleUpdateTag)
    public int cAcc, cMsg, cTemp = 25, cHeat, cCool, cSeal, cChill, cSrv = 1;
    public boolean cDown, cClosed;
    // серверное состояние
    public double temp = 25.0;
    public boolean down;
    public double busy;
    public int tick, chamberTimer, lastSig = Integer.MIN_VALUE;
    public Chamber chamber;
    public double heatW, coolW;
    public final ChatStore store = new ChatStore();
    public final List<Mail> mail = new ArrayList<>();

    public static class Mail {
        public final int to;
        public final Virus virus;
        public Mail(int to, Virus virus) { this.to = to; this.virus = virus; }
    }

    public TileServer(BlockPos pos, BlockState st) {
        super(ModBlockEntities.SERVER.get(), pos, st);
        java.util.Arrays.fill(drives, ItemStack.EMPTY);
    }

    // --- почта вирусов ---
    public int mailCapacity() { return Math.min(256, 16 + driveKB(-1) / 8); }
    public int mailCount() { return mail.size(); }
    public boolean queueVirus(int to, Virus v) {
        if (mail.size() >= mailCapacity()) return false;
        mail.add(new Mail(to, v));
        setChanged();
        return true;
    }
    public boolean hasMailFor(int to) { for (Mail m : mail) if (m.to == to) return true; return false; }
    public List<Virus> takeMail(int to) {
        List<Virus> r = new ArrayList<>();
        for (int i = mail.size() - 1; i >= 0; i--) if (mail.get(i).to == to) r.add(0, mail.remove(i).virus);
        if (!r.isEmpty()) setChanged();
        return r;
    }

    // --- диски ---
    public int tier() { return level == null ? 0 : (getBlockState().getBlock() instanceof BlockNetDevice d ? d.tier : 0); }
    public int bays() { return tier() == 0 ? 4 : 8; }
    public static boolean isDrive(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() instanceof ItemPart p && p.spec.hdd;
    }
    /** Суммарный объём дисков, кроме слота skip. */
    public int driveKB(int skip) {
        int kb = 0;
        for (int i = 0, n = bays(); i < n; i++) if (i != skip && isDrive(drives[i])) kb += ((ItemPart) drives[i].getItem()).spec.storageKB;
        return kb;
    }
    public int driveCount() { int c = 0; for (int i = 0, n = bays(); i < n; i++) if (isDrive(drives[i])) c++; return c; }
    public boolean canRemoveDrive(int i) { return true; }

    public void drivesChanged() {
        setChanged();
        if (level != null && !level.isClientSide) { chamber = null; notifyClients(); }
    }
    public void notifyClients() {
        if (level == null) return;
        BlockState s = getBlockState();
        level.sendBlockUpdated(getBlockPos(), s, s, 3);
    }
    public void syncClient() { if (level != null && !level.isClientSide) notifyClients(); }
    public ItemStack takeDriveSilently(int i) {
        if (i < 0 || i >= MAX_BAYS) return ItemStack.EMPTY;
        ItemStack s = drives[i]; drives[i] = ItemStack.EMPTY; return s;
    }

    // --- Container ---
    @Override public int getContainerSize() { return MAX_BAYS; }
    @Override public boolean isEmpty() { for (ItemStack s : drives) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int i) { return i >= 0 && i < MAX_BAYS ? drives[i] : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int i, int n) {
        if (i < 0 || i >= MAX_BAYS || drives[i].isEmpty()) return ItemStack.EMPTY;
        ItemStack s = drives[i], r;
        if (s.getCount() <= n) { r = s; drives[i] = ItemStack.EMPTY; }
        else { r = s.split(n); if (s.isEmpty()) drives[i] = ItemStack.EMPTY; }
        drivesChanged();
        return r;
    }
    @Override public ItemStack removeItemNoUpdate(int i) {
        if (i < 0 || i >= MAX_BAYS) return ItemStack.EMPTY;
        ItemStack s = drives[i]; drives[i] = ItemStack.EMPTY;
        if (!s.isEmpty()) drivesChanged();
        return s;
    }
    @Override public void setItem(int i, ItemStack s) {
        if (i < 0 || i >= MAX_BAYS) return;
        drives[i] = s == null ? ItemStack.EMPTY : s;
        if (drives[i].getCount() > 1) drives[i].setCount(1);
        drivesChanged();
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean stillValid(Player p) {
        BlockPos b = getBlockPos();
        return level != null && level.getBlockEntity(b) == this && p.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5) <= 64.0;
    }
    @Override public boolean canPlaceItem(int i, ItemStack s) { return i >= 0 && i < bays() && isDrive(s); }
    @Override public void clearContent() { java.util.Arrays.fill(drives, ItemStack.EMPTY); }
    public Component getName() { return Component.translatable("container.pcmod.server"); }

    // --- состояние / тепло ---
    public ChatStore store() { return store; }
    public boolean isUp() { return !down; }
    public double heatWatts() {
        if (down) return 5.0;
        return (tier() == 0 ? BASE_HEAT_S1 : BASE_HEAT_S2) + DRIVE_HEAT_W * driveCount() + Math.min((double) MAX_BUSY_W, busy);
    }
    public void touch() { busy = Math.min(40.0, busy + 2.0); }

    /** @return причина отказа или null, если сервер доступен */
    public String gate() {
        if (down) return "Server overheated (" + (int) temp + " C), it will restart after cooling down";
        if (temp >= THROTTLE_TEMP && level != null && level.random.nextInt(3) == 0)
            return "Server is overloaded by heat (" + (int) temp + " C), try again";
        return null;
    }

    public void thermalStep() {
        if (chamber == null || ++chamberTimer >= 3) { chamberTimer = 0; chamber = Chamber.analyze(level, this); }
        busy *= 0.85;
        if (busy < 0.2) busy = 0;
        heatW = heatWatts();
        int n = Math.max(1, chamber.servers.size());
        double passive = PASSIVE_W * (1.0 - 0.75 * chamber.seal);
        double chill = chamber.chillers * 70.0 * (chamber.closed ? 1.0 : 0.5) / n;
        coolW = passive + chill;
        double target = AMBIENT + (heatW - coolW) * 0.9;
        if (target < MIN_TEMP) target = MIN_TEMP;
        temp += (target - temp) * 0.06;
        if (temp > 140.0) temp = 140.0;
        if (!down && temp >= SHUTDOWN_TEMP) down = true;
        else if (down && temp <= WARN_TEMP) down = false;
        setChanged();
        int sig = (int) temp;
        sig = sig * 31 + (down ? 1 : 0);
        sig = sig * 31 + (int) Math.round(chamber.seal * 100.0);
        sig = sig * 31 + chamber.chillers;
        sig = sig * 31 + n;
        sig = sig * 31 + (chamber.closed ? 1 : 0);
        sig = sig * 31 + (int) heatW;
        sig = sig * 31 + (int) coolW;
        if (sig != lastSig) { lastSig = sig; notifyClients(); }
    }

    /** Серверный тик (ticker в BlockServer). */
    public void tick() {
        if (level == null || level.isClientSide) return;
        if (++tick >= 20) { tick = 0; thermalStep(); }
    }

    public long now() { return level.getGameTime(); }
    public long day() { return level.getDayTime(); }

    // --- чат ---
    public String register(String nick, String pass) {
        String g = gate(); if (g != null) return g;
        touch();
        String r = store().register(nick, pass, now());
        if (r == null) setChanged();
        return r;
    }
    public String login(String nick, String pass) {
        String g = gate(); if (g != null) return g;
        touch();
        return store().login(nick, pass, now());
    }
    public String send(String nick, String pass, String to, String text) {
        String g = gate(); if (g != null) return g;
        touch();
        String r = store().send(nick, pass, to, text, now(), day());
        if (r == null) setChanged();
        return r;
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && domain.length() == 0) {
            BlockPos p = getBlockPos();
            domain = "chat" + Integer.toHexString((p.getX() * 31 + p.getY() * 7 + p.getZ() * 17) & 0xFFFF) + ".net";
        }
    }

    public String applyConfig(String d, boolean wipe) {
        if (!Net.validDomain(d)) return "Domain: 1-20 chars (a-z, 0-9, ., -)";
        domain = d;
        if (wipe) store.wipe();
        setChanged();
        notifyClients();
        return null;
    }

    // --- NBT ---
    @Override public void load(CompoundTag t) {
        super.load(t);
        domain = t.getString("domain");
        store.accounts.clear(); store.msgs.clear();
        ListTag al = t.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) {
            CompoundTag c = al.getCompound(i);
            ChatStore.Account a = new ChatStore.Account();
            a.nick = c.getString("n"); a.hash = c.getString("h"); a.seen = c.getLong("s");
            store.accounts.add(a);
        }
        ListTag ml = t.getList("msgs", Tag.TAG_COMPOUND);
        for (int i = 0; i < ml.size(); i++) {
            CompoundTag c = ml.getCompound(i);
            ChatStore.Msg m = new ChatStore.Msg();
            m.id = c.getInt("id"); m.from = c.getString("f"); m.to = c.getString("t"); m.text = c.getString("x"); m.time = c.getLong("ts");
            store.msgs.add(m);
        }
        java.util.Arrays.fill(drives, ItemStack.EMPTY);
        ListTag dl = t.getList("drives", Tag.TAG_COMPOUND);
        for (int i = 0; i < dl.size(); i++) {
            CompoundTag c = dl.getCompound(i);
            int s = c.getByte("Slot") & 255;
            if (s < MAX_BAYS) drives[s] = ItemStack.of(c);
        }
        mail.clear();
        ListTag vl = t.getList("vmail", Tag.TAG_COMPOUND);
        for (int i = 0; i < vl.size(); i++) {
            CompoundTag c = vl.getCompound(i);
            Virus v = Virus.readFromNBT(c.getCompound("v"));
            int to = c.getInt("to");
            if (v != null && ComputerIds.valid(to)) mail.add(new Mail(to, v));
        }
        temp = t.contains("temp") ? t.getDouble("temp") : 25.0;
        down = t.getBoolean("down");
        store.nextId = Math.max(1, t.getInt("nextId"));
        for (ChatStore.Msg m : store.msgs) store.nextId = Math.max(store.nextId, m.id + 1);
    }

    @Override protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putString("domain", domain);
        ListTag al = new ListTag();
        for (ChatStore.Account a : store.accounts) {
            CompoundTag c = new CompoundTag();
            c.putString("n", a.nick); c.putString("h", a.hash); c.putLong("s", a.seen);
            al.add(c);
        }
        t.put("accounts", al);
        ListTag ml = new ListTag();
        for (ChatStore.Msg m : store.msgs) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", m.id); c.putString("f", m.from); c.putString("t", m.to); c.putString("x", m.text); c.putLong("ts", m.time);
            ml.add(c);
        }
        t.put("msgs", ml);
        ListTag dl = new ListTag();
        for (int i = 0; i < MAX_BAYS; i++) {
            if (drives[i].isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.putByte("Slot", (byte) i);
            drives[i].save(c);
            dl.add(c);
        }
        t.put("drives", dl);
        t.putInt("nextId", store.nextId);
        t.putDouble("temp", temp);
        t.putBoolean("down", down);
        ListTag vl = new ListTag();
        for (Mail m : mail) {
            CompoundTag c = new CompoundTag();
            c.putInt("to", m.to);
            CompoundTag v = new CompoundTag();
            m.virus.writeToNBT(v);
            c.put("v", v);
            vl.add(c);
        }
        t.put("vmail", vl);
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putString("domain", domain);
        t.putInt("acc", store.accounts.size());
        t.putInt("msg", store.msgs.size());
        t.putInt("temp", (int) temp);
        t.putBoolean("down", down);
        t.putInt("heat", (int) heatW);
        t.putInt("cool", (int) coolW);
        t.putInt("seal", chamber == null ? 0 : (int) Math.round(chamber.seal * 100.0));
        t.putInt("chill", chamber == null ? 0 : chamber.chillers);
        t.putInt("srv", chamber == null ? 1 : Math.max(1, chamber.servers.size()));
        t.putBoolean("closed", chamber != null && chamber.closed);
        return t;
    }

    @Override public void handleUpdateTag(CompoundTag t) {
        domain = t.getString("domain");
        cAcc = t.getInt("acc"); cMsg = t.getInt("msg"); cTemp = t.getInt("temp"); cDown = t.getBoolean("down");
        cHeat = t.getInt("heat"); cCool = t.getInt("cool"); cSeal = t.getInt("seal"); cChill = t.getInt("chill");
        cSrv = Math.max(1, t.getInt("srv")); cClosed = t.getBoolean("closed");
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
