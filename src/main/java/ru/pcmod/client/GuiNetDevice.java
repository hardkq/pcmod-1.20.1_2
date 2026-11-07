package ru.pcmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.glfw.GLFW;
import ru.pcmod.PCMod;
import ru.pcmod.common.ContainerBlank;
import ru.pcmod.common.ContainerServer;
import ru.pcmod.common.PacketNetConfig;
import ru.pcmod.common.TileRouter;
import ru.pcmod.common.TileServer;

/** Настройки роутера (SSID, пароль, Wi-Fi вкл/выкл) и сервера (домен, данные чата, отсеки под HDD). */
public class GuiNetDevice extends AbstractContainerScreen<ContainerBlank> {
    private final BlockEntity te;
    private final boolean router;
    private EditBox f1, f2;
    private Button bWifi, bPass, bWipe;
    private boolean wifi, synced, clearPass, confirmWipe;

    public GuiNetDevice(ContainerBlank c, Inventory inv, Component title) {
        super(c, inv, title);
        this.te = c.tile;
        this.router = te instanceof TileRouter;
        this.imageWidth = 200;
        this.imageHeight = router ? 128 : 254;
        if (router) wifi = ((TileRouter) te).wifiOn;
    }

    @Override
    protected void init() {
        super.init();
        f1 = new EditBox(font, leftPos + 62, topPos + 22, 128, 16, Component.literal(router ? "SSID" : "Domain"));
        f1.setMaxLength(router ? 16 : 20);
        addRenderableWidget(f1);
        setInitialFocus(f1);
        if (router) {
            f2 = new EditBox(font, leftPos + 62, topPos + 46, 128, 16, Component.literal("Password"));
            f2.setMaxLength(16);
            addRenderableWidget(f2);
            bWifi = addRenderableWidget(Button.builder(Component.literal("Wi-Fi: ON"), b -> wifi = !wifi)
                    .bounds(leftPos + 10, topPos + 74, 85, 20).build());
            bPass = addRenderableWidget(Button.builder(Component.literal("[ ] No password"), b -> clearPass = !clearPass)
                    .bounds(leftPos + 105, topPos + 74, 85, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Save"), b -> save(false))
                    .bounds(leftPos + 10, topPos + 100, 85, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                    .bounds(leftPos + 105, topPos + 100, 85, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Save"), b -> save(false))
                    .bounds(leftPos + 10, topPos + 112, 85, 20).build());
            bWipe = addRenderableWidget(Button.builder(Component.literal("Wipe chat data"), b -> {
                if (confirmWipe) {
                    save(true);
                    confirmWipe = false;
                } else {
                    confirmWipe = true;
                }
            }).bounds(leftPos + 105, topPos + 112, 85, 20).build());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        f1.tick();
        if (f2 != null) f2.tick();
        if (!synced) {
            // данные блока приходят отдельным пакетом, возможно уже после открытия окна
            String v = router ? ((TileRouter) te).ssid : ((TileServer) te).domain;
            if (v.length() > 0) {
                f1.setValue(v);
                if (router) wifi = ((TileRouter) te).wifiOn;
                synced = true;
            }
        }
        if (router) {
            bWifi.setMessage(Component.literal("Wi-Fi: " + (wifi ? "ON" : "OFF")));
            bPass.setMessage(Component.literal(clearPass ? "[x] No password" : "[ ] No password"));
        } else {
            bWipe.setMessage(Component.literal(confirmWipe ? "Click again!" : "Wipe chat data"));
        }
    }

    private void save(boolean wipe) {
        int flags = 0;
        String a = f1.getValue().trim(), b = "";
        if (router) {
            if (wifi) flags |= 1;
            if (clearPass) flags |= 2;
            b = f2.getValue();
        } else if (wipe) {
            flags |= 4;
        }
        BlockPos p = te.getBlockPos();
        PCMod.sendToServer(new PacketNetConfig(p.getX(), p.getY(), p.getZ(), flags, a, b));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && f2 != null) {
            boolean first = f1.isFocused();
            f1.setFocused(!first);
            f2.setFocused(first);
            setFocused(first ? f2 : f1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save(false);
            return true;
        }
        // пока поле в фокусе, клавиша инвентаря (E) не должна закрывать окно
        if (f1.isFocused() || (f2 != null && f2.isFocused())) {
            (f1.isFocused() ? f1 : f2).keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        if (!router) {
            // подложка под слоты (отсеки и инвентарь)
            for (Slot s : menu.slots) {
                int sx = leftPos + s.x, sy = topPos + s.y;
                g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
                g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        if (router) {
            TileRouter r = (TileRouter) te;
            g.drawString(font, "Router - Wi-Fi range " + r.range() + " blocks", 8, 6, 0x404040, false);
            g.drawString(font, "SSID", 10, 26, 0x404040, false);
            g.drawString(font, "Password", 10, 50, 0x404040, false);
            g.drawString(font, r.hasPassClient ? "Now: secured (empty field = keep it)" : "Now: open network, no password",
                    10, 64, 0x666666, false);
        } else {
            TileServer s = (TileServer) te;
            g.drawString(font, "Server - chat service", 8, 6, 0x404040, false);
            g.drawString(font, "Domain", 10, 26, 0x404040, false);
            g.drawString(font, "Drives: " + s.driveCount() + " / " + s.bays() + "  (" + s.driveKB(-1) + " KB)", 10, 44, 0x404040, false);
            g.drawString(font, "Accounts: " + s.cAcc + "   Messages: " + s.cMsg + "   (no limit)", 10, 56, 0x404040, false);
            int tc = s.cDown || s.cTemp >= 100 ? 0xCC0000 : s.cTemp >= 85 ? 0xDD5500 : s.cTemp >= 70 ? 0xBB9900 : 0x007700;
            String st = s.cDown ? "OVERHEATED - OFFLINE" : s.cTemp >= 85 ? "throttling" : s.cTemp >= 70 ? "warm" : "normal";
            g.drawString(font, "Temperature: " + s.cTemp + " C  (" + st + ")", 10, 70, tc, false);
            g.drawString(font, "Heat " + s.cHeat + " W   Cooling " + s.cCool + " W", 10, 82, 0x404040, false);
            String ch = s.cClosed
                    ? "Chamber: sealed " + s.cSeal + "%, chillers " + s.cChill + ", servers " + s.cSrv
                    : "No chamber" + (s.cChill > 0 ? " (chillers nearby: " + s.cChill + ", half power)" : " (open air)");
            g.drawString(font, ch, 10, 94, s.cClosed ? 0x205080 : 0x666666, false);
            g.drawString(font, "Drive bays (HDD only, +14 W heat each)", 8, 134, 0x404040, false);
            g.drawString(font, "Inventory", ContainerServer.INV_X, 162, 0x404040, false);
        }
    }
}
