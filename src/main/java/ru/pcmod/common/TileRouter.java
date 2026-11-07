package ru.pcmod.common;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TileRouter extends BlockEntity {
    public String ssid = "";
    public String pass = "";
    public boolean wifiOn = true;
    public boolean hasPassClient;

    public TileRouter(BlockPos pos, BlockState st) { super(ModBlockEntities.ROUTER.get(), pos, st); }

    public int tier() { return level == null ? 0 : (getBlockState().getBlock() instanceof BlockNetDevice d ? d.tier : 0); }
    public int range() { return tier() == 0 ? 24 : 64; }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            if (ssid.length() == 0) {
                BlockPos p = getBlockPos();
                ssid = "PCNet-" + Integer.toHexString((p.getX() * 31 + p.getY() * 7 + p.getZ() * 17) & 0xFFFF).toUpperCase();
            }
            Net.register(this);
        }
    }

    @Override public void setRemoved() { super.setRemoved(); Net.unregister(this); }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); Net.unregister(this); }

    /** @return сообщение об ошибке или null. clearPass — сбросить пароль. */
    public String applyConfig(String newSsid, String newPass, boolean clearPass, boolean wifi) {
        if (!Net.validSsid(newSsid)) return "SSID: 1-16 chars (A-Z, a-z, 0-9, _, -)";
        if (newPass.length() > 0 && !Net.validPass(newPass)) return "Password: 1-16 chars, no spaces";
        ssid = newSsid;
        if (clearPass) pass = ""; else if (newPass.length() > 0) pass = newPass;
        wifiOn = wifi;
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        return null;
    }

    @Override public void load(CompoundTag t) {
        super.load(t);
        ssid = t.getString("ssid"); pass = t.getString("pass");
        wifiOn = !t.contains("wifiOn") || t.getBoolean("wifiOn");
    }

    @Override protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putString("ssid", ssid); t.putString("pass", pass); t.putBoolean("wifiOn", wifiOn);
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putString("ssid", ssid); t.putBoolean("hasPass", pass.length() > 0); t.putBoolean("wifiOn", wifiOn);
        return t;
    }

    @Override public void handleUpdateTag(CompoundTag t) {
        ssid = t.getString("ssid"); hasPassClient = t.getBoolean("hasPass"); wifiOn = t.getBoolean("wifiOn");
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
