package ru.pcmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import ru.pcmod.common.ContainerMiner;
import ru.pcmod.common.TileMiner;

/** Окно майнер-фермы: статус, доход, температура, питание, кнопки Power / Collect и слоты деталей. */
public class GuiMiner extends AbstractContainerScreen<ContainerMiner> {
    private static final String[] STATUS = {"Off", "Mining", "No GPU installed", "PSU too weak",
            "Overheated - restart when cool", "No network (cable to a router)", "Storage full - Collect!"};
    private final ContainerMiner cm;
    private final TileMiner te;
    private Button power, collect;

    public GuiMiner(ContainerMiner c, Inventory inv, Component title) {
        super(c, inv, title);
        this.cm = c;
        this.te = c.miner;
        this.imageWidth = 200;
        this.imageHeight = ContainerMiner.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        power = addRenderableWidget(Button.builder(Component.literal("Power"), b -> press(0))
                .bounds(leftPos + 10, topPos + 70, 85, 20).build());
        collect = addRenderableWidget(Button.builder(Component.literal("Collect"), b -> press(1))
                .bounds(leftPos + 105, topPos + 70, 85, 20).build());
    }

    /** Кнопки идут на сервер как clickMenuButton (раньше enchantItem). */
    private void press(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (power != null) power.setMessage(Component.literal(cm.data[1] != 0 ? "Power: ON" : "Power: OFF"));
        if (collect != null) collect.setMessage(Component.literal("Collect " + cm.data[8] + " K"));
    }

    private static String f2(int v) {
        return (v / 100) + "." + (v % 100 < 10 ? "0" : "") + (v % 100);
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
        for (Slot s : menu.slots) {
            int sx = leftPos + s.x, sy = topPos + s.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int[] d = cm.data;
        int st = d[0] < 0 || d[0] >= STATUS.length ? 0 : d[0];
        String name = STATUS[st] + (st == 1 && d[5] >= 90 ? " (throttled)" : "");
        g.drawString(font, "Miner Farm F" + (te.tier() + 1) + "  (" + te.gpuSlots() + " GPU)", 8, 6, 0x404040, false);
        g.drawString(font, "Status: " + name, 10, 18, st == 1 ? 0x207020 : 0x802020, false);
        g.drawString(font, "Hashrate: " + d[2] + " MH/s   Market x" + f2(d[3]), 10, 28, 0x404040, false);
        g.drawString(font, "Income: " + (d[4] / 10) + "." + (d[4] % 10) + " K/hour", 10, 38, 0x404040, false);
        g.drawString(font, "Temp: " + d[5] + " C    Power: " + d[6] + " / " + d[7] + " W", 10, 48, d[5] >= 90 ? 0x802020 : 0x404040, false);
        g.drawString(font, "Mined: " + d[8] + " / " + d[9] + " K  (to your bank)", 10, 58, 0x404040, false);
        g.drawString(font, "GPU (mining cards)", 10, 94, 0x404040, false);
        g.drawString(font, "PSU", 10, 124, 0x404040, false);
        g.drawString(font, "Cooler", 110, 124, 0x404040, false);
        g.drawString(font, "Inventory", ContainerMiner.INV_X, 156, 0x404040, false);
    }
}
