package ru.pcmod.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import ru.pcmod.PCMod;
import ru.pcmod.common.ContainerComputer;
import ru.pcmod.common.PacketAction;
import ru.pcmod.common.PartSpec;
import ru.pcmod.common.TileComputer;

/** Сервисная панель: установка комплектующих и диагностика совместимости. */
public class GuiHardware extends AbstractContainerScreen<ContainerComputer> {
    private static final String[] LABELS = {"MB", "CPU", "Cool", "RAM1", "RAM2", "RAM3", "RAM4", "GPU", "Disk", "PSU", "Media", "WiFi", "Data"};
    private final TileComputer tile;
    private Button power;

    public GuiHardware(ContainerComputer c, Inventory inv, Component title) {
        super(c, inv, title);
        this.tile = c.tile;
        this.imageWidth = 176;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        power = addRenderableWidget(Button.builder(Component.literal("Power on"), b -> act(1))
                .bounds(leftPos + 8, topPos + 120, 62, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> act(2))
                .bounds(leftPos + 74, topPos + 120, 44, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Screen"), b -> act(4))
                .bounds(leftPos + 122, topPos + 120, 46, 16).build());
    }

    private void act(int type) {
        BlockPos p = tile.getBlockPos();
        PCMod.sendToServer(new PacketAction(p.getX(), p.getY(), p.getZ(), type, (char) 0, 0));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (power != null) power.setMessage(Component.literal(tile.isOn() ? "Power off" : "Power on"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int l = leftPos, t = topPos;
        g.fill(l, t, l + imageWidth, t + imageHeight, 0xFFC6C6C6);
        PartSpec mb = tile.spec(TileComputer.MB);
        for (Slot s : menu.slots) {
            int x = l + s.x, y = t + s.y;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            g.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
            int idx = s.getContainerSlot();
            if (s.container == tile && idx >= TileComputer.RAM0 && idx <= TileComputer.RAM3
                    && mb != null && idx - TileComputer.RAM0 >= mb.ramSlots) {
                g.fill(x, y, x + 16, y + 16, 0xAAAA0000); // слот недоступен на этой плате
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "PC Hardware", 8, 5, 0x404040, false);
        if (tile.getComputerId() > 0) {
            String id = "ID " + tile.getComputerId();
            g.drawString(font, id, imageWidth - 8 - font.width(id), 5, 0x404040, false);
        }
        for (int i = 0; i < TileComputer.SLOTS; i++) {
            int[] p = ContainerComputer.POS[i];
            g.drawString(font, LABELS[i], p[0], p[1] - 10, 0x404040, false);
        }
        List<String> lines = new ArrayList<String>();
        List<Integer> cols = new ArrayList<Integer>();
        String led = tile.screen.led;
        if (led != null && led.length() > 0) {
            lines.add("LED: " + led);
            cols.add(0xAA0000);
        }
        for (String e : tile.errors()) {
            lines.add("x " + e);
            cols.add(0xAA0000);
        }
        for (String w : tile.warnings()) {
            lines.add("! " + w);
            cols.add(0x996600);
        }
        if (tile.errors().isEmpty() && (led == null || led.length() == 0)) {
            lines.add(tile.isOn() ? "Running. Power off to change parts (Data disk can be swapped)." : "Build OK, ready to boot.");
            cols.add(0x007700);
        }
        int y = 80, rows = 0;
        for (int i = 0; i < lines.size() && rows < 4; i++) {
            List<FormattedCharSequence> wrapped = font.split(Component.literal(lines.get(i)), 160);
            for (FormattedCharSequence w : wrapped) {
                if (rows >= 4) break;
                g.drawString(font, w, 8, y, cols.get(i), false);
                y += 9;
                rows++;
            }
        }
    }
}
