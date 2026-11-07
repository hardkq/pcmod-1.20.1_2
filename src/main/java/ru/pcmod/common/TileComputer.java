package ru.pcmod.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import ru.pcmod.PCMod;

public class TileComputer extends BlockEntity implements Container {
    public static final int SLOTS = 13, MB = 0, CPU = 1, COOLER = 2, RAM0 = 3, RAM3 = 6, GPU = 7, STORAGE = 8, PSU = 9, MEDIA = 10, NET = 11, DISK = 12;
    public static final int VIRUS_LIBRARY_MAX = 64;
    public static final PartSpec.Cat[] SLOT_CAT = {
        PartSpec.Cat.MOTHERBOARD, PartSpec.Cat.CPU, PartSpec.Cat.COOLER,
        PartSpec.Cat.RAM, PartSpec.Cat.RAM, PartSpec.Cat.RAM, PartSpec.Cat.RAM,
        PartSpec.Cat.GPU, PartSpec.Cat.STORAGE, PartSpec.Cat.PSU, PartSpec.Cat.MEDIA, PartSpec.Cat.NET, PartSpec.Cat.DATA
    };
    private static final Map<Integer, TileComputer> BY_ID = new HashMap<>();

    public final ItemStack[] inv = new ItemStack[SLOTS];
    public int bootOrder;
    public boolean fastBoot;
    public final Screen screen = new Screen();
    public double temp = 25.0;
    public ComputerLogic logic;
    public int computerId;
    public boolean idRegistered;
    public final List<Virus> viruses = new ArrayList<>();
    private List<int[]> monCache = new ArrayList<>();
    private long monCacheAt = -1000;

    public TileComputer(BlockPos pos, BlockState st) {
        super(ModBlockEntities.COMPUTER.get(), pos, st);
        java.util.Arrays.fill(inv, ItemStack.EMPTY);
    }

    // --- реестр id ---
    public static TileComputer byId(int id) {
        TileComputer t = BY_ID.get(id);
        return t != null && !t.isRemoved() && t.level != null ? t : null;
    }
    public static List<TileComputer> loaded() {
        List<TileComputer> r = new ArrayList<>();
        for (TileComputer t : BY_ID.values()) if (!t.isRemoved() && t.level != null) r.add(t);
        return r;
    }
    public static void clearRegistry() { BY_ID.clear(); }
    public void unregisterId() { if (BY_ID.get(computerId) == this) BY_ID.remove(computerId); }
    @Override public void setRemoved() { super.setRemoved(); unregisterId(); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); unregisterId(); }

    public ComputerLogic logic() {
        if (level == null || level.isClientSide) return null;
        if (logic == null) logic = new ComputerLogic(this);
        return logic;
    }

    public int getComputerId() {
        if (computerId == 0 && level != null && !level.isClientSide) ensureId();
        return computerId;
    }

    public void ensureId() {
        if (level == null || level.isClientSide) return;
        ComputerIds ids = ComputerIds.get(level);
        if (!ComputerIds.valid(computerId)) {
            computerId = ids.allocate(level.random);
            idRegistered = true;
            BY_ID.put(computerId, this);
            setChanged();
            notifyClients();
        } else if (!idRegistered) {
            ids.register(computerId);
            idRegistered = true;
            BY_ID.put(computerId, this);
        }
    }

    public void notifyClients() {
        if (level == null || level.isClientSide) return;
        BlockState s = getBlockState();
        level.sendBlockUpdated(getBlockPos(), s, s, 3);
    }

    // --- вирусы ---
    public List<Virus> viruses() { return viruses; }
    public boolean addVirus(Virus v) {
        if (viruses.size() >= VIRUS_LIBRARY_MAX) return false;
        viruses.add(v);
        setChanged();
        return true;
    }
    public boolean receiveVirus(Virus v) {
        Virus c = v.copy();
        c.incoming = c.originId != getComputerId();
        c.receivedAt = level == null ? 0 : level.getGameTime();
        return addVirus(c);
    }
    public boolean removeVirus(int i) {
        if (i < 0 || i >= viruses.size()) return false;
        viruses.remove(i);
        setChanged();
        return true;
    }
    public ItemStack dataDisk() { ItemStack s = inv[DISK]; return VirusDisk.isDisk(s) ? s : null; }

    /** Серверный тик (ticker в BlockComputer). */
    public void tick() {
        if (level == null || level.isClientSide) return;
        if (computerId == 0 || !idRegistered) ensureId();
        if (logic == null) logic = new ComputerLogic(this);
        logic.tick();
        if (screen.dirty && level.getGameTime() % 2 == 0) {
            screen.dirty = false;
            BlockPos p = getBlockPos();
            PCMod.NET.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 24.0, level.dimension())),
                new PacketScreen(p.getX(), p.getY(), p.getZ(), screen));
        }
    }

    // --- синхронизация клиенту ---
    @Override public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        ByteBuf buf = Unpooled.buffer();
        screen.write(buf);
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        t.putByteArray("scr", b);
        t.putInt("cid", computerId);
        t.put("Items", writeItems(true));
        return t;
    }
    @Override public void handleUpdateTag(CompoundTag t) {
        byte[] b = t.getByteArray("scr");
        if (b.length > 0) screen.read(Unpooled.wrappedBuffer(b));
        computerId = t.getInt("cid");
        if (t.contains("Items")) readItems(t.getList("Items", Tag.TAG_COMPOUND));
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    public void syncParts() { setChanged(); notifyClients(); }

    @Override public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 1, p.getY() - 1, p.getZ() - 1, p.getX() + 2, p.getY() + 2, p.getZ() + 2);
    }

    public boolean isOn() {
        if (level != null && level.isClientSide) return screen.on;
        return logic != null && logic.isOn();
    }

    /** Действие клиента: 0 клавиша, 1 питание, 2 reset, 5 клик, 6 наведение. */
    public void handleAction(Player p, int type, char ch, int arg) {
        if (level == null || level.isClientSide) return;
        if (logic == null) logic = new ComputerLogic(this);
        logic.setActor(p);
        try { handleAction(type, ch, arg); } finally { logic.clearActor(); }
    }
    public void handleAction(int type, char ch, int arg) {
        if (level == null || level.isClientSide) return;
        if (logic == null) logic = new ComputerLogic(this);
        if (type == 0) logic.onKey(ch, arg);
        else if (type == 1) logic.power();
        else if (type == 2) logic.reset();
        else if (type == 5) logic.onClick(ch, arg);
        else if (type == 6) logic.onHover(ch, arg);
    }

    // --- детали ---
    public PartSpec spec(int i) {
        ItemStack s = inv[i];
        return !s.isEmpty() && s.getItem() instanceof ItemPart p ? p.spec : null;
    }
    public int totalRamMB() { int mb = 0; for (int i = RAM0; i <= RAM3; i++) { PartSpec s = spec(i); if (s != null) mb += s.memMB; } return mb; }
    public int ramCount() { int n = 0; for (int i = RAM0; i <= RAM3; i++) if (spec(i) != null) n++; return n; }

    public List<int[]> monitors() {
        if (level == null) return monCache;
        long t = level.getGameTime();
        if (t - monCacheAt >= 10 || t < monCacheAt) {
            monCacheAt = t;
            BlockPos p = getBlockPos();
            monCache = VideoLink.monitorsOf(level, p.getX(), p.getY(), p.getZ());
        }
        return monCache;
    }
    public int monitorType() {
        if (level == null) return -1;
        for (int[] m : monitors()) {
            if (!BlockAt.loaded(level, m[0], m[1], m[2])) continue;
            Block b = BlockAt.block(level, m[0], m[1], m[2]);
            if (b instanceof BlockMonitor bm) return bm.type;
        }
        return -1;
    }
    public int gpuTier() { PartSpec g = spec(GPU); return g == null ? 0 : g.tier; }
    public int requiredWatts() {
        PartSpec cpu = spec(CPU), gpu = spec(GPU);
        return 30 + (cpu == null ? 0 : cpu.watts) + (gpu == null ? 0 : gpu.watts) + 3 * ramCount() + (spec(STORAGE) == null ? 0 : 5);
    }

    public List<String> errors() {
        List<String> e = new ArrayList<>();
        PartSpec mb = spec(MB), cpu = spec(CPU), gpu = spec(GPU), psu = spec(PSU);
        if (mb == null) e.add("No motherboard");
        if (cpu == null) e.add("No CPU");
        else if (mb != null && cpu.socket != mb.socket)
            e.add("CPU " + PartSpec.socketName(cpu.socket) + " does not fit board " + PartSpec.socketName(mb.socket));
        int rams = 0;
        for (int i = RAM0; i <= RAM3; i++) {
            PartSpec r = spec(i);
            if (r == null) continue;
            rams++;
            if (mb == null) continue;
            if (r.ramType != mb.ramType) e.add("RAM" + (i - RAM0 + 1) + " is " + PartSpec.ramName(r.ramType) + ", board needs " + PartSpec.ramName(mb.ramType));
            else if (i - RAM0 >= mb.ramSlots) e.add("Board has only " + mb.ramSlots + " RAM slots (RAM" + (i - RAM0 + 1) + ")");
        }
        if (rams == 0) e.add("No RAM");
        if (gpu == null) e.add("No GPU (no video output)");
        if (psu == null) e.add("No PSU");
        else if (psu.watts < requiredWatts()) e.add("PSU too weak: " + psu.watts + " W < " + requiredWatts() + " W");
        return e;
    }
    public List<String> warnings() {
        List<String> w = new ArrayList<>();
        PartSpec cpu = spec(CPU), cooler = spec(COOLER);
        if (cpu != null) {
            if (cooler == null) w.add("No cooler: CPU will overheat");
            else if (25.0 + cpu.watts * 45.0 / cooler.cooling >= 95.0) w.add("Cooler too weak for this CPU");
        }
        if (spec(STORAGE) == null) w.add("No storage drive");
        if (monitorType() < 0) w.add("No monitor connected (next to the case or via video cable)");
        return w;
    }

    // --- NBT ---
    @Override public void load(CompoundTag t) {
        super.load(t);
        readItems(t.getList("Items", Tag.TAG_COMPOUND));
        bootOrder = t.getInt("bootOrder");
        fastBoot = t.getBoolean("fastBoot");
        computerId = ComputerIds.valid(t.getInt("cid")) ? t.getInt("cid") : 0;
        idRegistered = false;
        viruses.clear();
        ListTag l = t.getList("viruses", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size() && viruses.size() < VIRUS_LIBRARY_MAX; i++) {
            Virus v = Virus.readFromNBT(l.getCompound(i));
            if (v != null) viruses.add(v);
        }
    }

    public void readItems(ListTag l) {
        java.util.Arrays.fill(inv, ItemStack.EMPTY);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            int s = c.getByte("Slot");
            if (s >= 0 && s < SLOTS) inv[s] = ItemStack.of(c);
        }
    }

    /** strip=true — для клиента: без NBT предметов. */
    public ListTag writeItems(boolean strip) {
        ListTag l = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            if (inv[i].isEmpty()) continue;
            CompoundTag c = new CompoundTag();
            c.putByte("Slot", (byte) i);
            ItemStack s = inv[i];
            if (strip) { s = s.copy(); s.setTag(null); }
            s.save(c);
            l.add(c);
        }
        return l;
    }

    @Override protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.put("Items", writeItems(false));
        t.putInt("bootOrder", bootOrder);
        t.putBoolean("fastBoot", fastBoot);
        t.putInt("cid", computerId);
        ListTag l = new ListTag();
        for (Virus v : viruses) { CompoundTag c = new CompoundTag(); v.writeToNBT(c); l.add(c); }
        t.put("viruses", l);
    }

    // --- Container ---
    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { for (ItemStack s : inv) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int i) { return i >= 0 && i < SLOTS ? inv[i] : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int i, int n) {
        if (i < 0 || i >= SLOTS || inv[i].isEmpty()) return ItemStack.EMPTY;
        ItemStack r;
        if (inv[i].getCount() <= n) { r = inv[i]; inv[i] = ItemStack.EMPTY; }
        else { r = inv[i].split(n); if (inv[i].isEmpty()) inv[i] = ItemStack.EMPTY; }
        syncParts();
        return r;
    }
    @Override public ItemStack removeItemNoUpdate(int i) {
        if (i < 0 || i >= SLOTS) return ItemStack.EMPTY;
        ItemStack s = inv[i]; inv[i] = ItemStack.EMPTY;
        if (!s.isEmpty()) syncParts();
        return s;
    }
    @Override public void setItem(int i, ItemStack s) {
        if (i < 0 || i >= SLOTS) return;
        inv[i] = s == null ? ItemStack.EMPTY : s;
        if (inv[i].getCount() > 1) inv[i].setCount(1);
        syncParts();
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean stillValid(Player p) {
        BlockPos b = getBlockPos();
        return level != null && level.getBlockEntity(b) == this && p.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5) <= 64.0;
    }
    @Override public boolean canPlaceItem(int i, ItemStack s) {
        return i >= 0 && i < SLOTS && !s.isEmpty() && s.getItem() instanceof ItemPart p && p.spec.cat == SLOT_CAT[i];
    }
    @Override public void clearContent() { java.util.Arrays.fill(inv, ItemStack.EMPTY); }
    public Component getName() { return Component.translatable("container.pcmod.computer"); }
}
