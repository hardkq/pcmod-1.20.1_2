package ru.pcmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import ru.pcmod.PCMod;
import ru.pcmod.common.ContainerScreen;
import ru.pcmod.common.MonitorType;
import ru.pcmod.common.PacketAction;
import ru.pcmod.common.Screen;
import ru.pcmod.common.Sprites;
import ru.pcmod.common.TileComputer;

/** Экран монитора: рисует текстовый экран ПК и отправляет нажатия клавиш на сервер (коды клавиш - LWJGL 2, см. KeyMap). */
public class GuiMonitor extends AbstractContainerScreen<ContainerScreen> {
    private static final int CW = 6, CH = 9;
    private static final int[] PAL = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final int[] RGB = {0xFF5555, 0x55FF55, 0x5555FF, 0xFF55FF, 0x55FFFF, 0xFFFF55};
    private final TileComputer tile;
    /** Последняя отправленная клетка под курсором (-1: вне экрана) и время отправки. */
    private int hoverX = -2, hoverY = -2;
    private long hoverT;
    /** Печатная клавиша ждёт символ из charTyped (код LWJGL 2, 0 - нет). */
    private int pendingKey;

    public GuiMonitor(ContainerScreen c, Inventory inv, Component title) {
        super(c, inv, title);
        this.tile = c.tile;
        this.imageWidth = 380;
        this.imageHeight = 250;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Power"), b -> send(1, (char) 0, 0))
                .bounds(leftPos + 10, topPos + 218, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> send(2, (char) 0, 0))
                .bounds(leftPos + 86, topPos + 218, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Hardware"), b -> send(3, (char) 0, 0))
                .bounds(leftPos + 162, topPos + 218, 90, 20).build());
    }

    private void send(int type, char c, int key) {
        BlockPos p = tile.getBlockPos();
        PCMod.sendToServer(new PacketAction(p.getX(), p.getY(), p.getZ(), type, c, key));
    }

    @Override
    public void removed() {
        flushPending();
        send(6, (char) 0xFFFF, -1);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        flushPending(); // клавиша без символа (например, с Ctrl) - отправляем как есть
    }

    private void flushPending() {
        if (pendingKey != 0) {
            send(0, (char) 0, pendingKey);
            pendingKey = 0;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        sendHover(mx, my);
        renderBackground(g);
        super.render(g, mx, my, pt);
    }

    /**
     * Сообщает серверу, над какой клеткой экрана курсор (при смене клетки, не чаще 20 раз в секунду, и раз в 0,4 с,
     * пока курсор на экране). DOOM по этому поворачивает камеру.
     */
    private void sendHover(int mx, int my) {
        if (!tile.screen.on || tile.screen.noSignal) return;
        int sx = leftPos + 10, sy = topPos + 8, cx = -1, cy = -1;
        if (mx >= sx && my >= sy) {
            int x = (mx - sx) / CW, y = (my - sy) / CH;
            if (x < Screen.W && y < Screen.H) {
                cx = x;
                cy = y;
            }
        }
        long now = System.currentTimeMillis();
        boolean changed = cx != hoverX || cy != hoverY;
        if (!changed && (cx < 0 || now - hoverT < 400L)) return;
        if (changed && now - hoverT < 50L) return;
        hoverX = cx;
        hoverY = cy;
        hoverT = now;
        send(6, (char) cx, cy);
    }

    /** Все клавиши идут на сервер, кроме Esc; super не вызываем, чтобы E (инвентарь) не закрывало экран. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (!tile.screen.on || KeyMap.modifier(key)) return true;
        int lw = KeyMap.lwjgl(key);
        if (lw == 0) return true;
        flushPending();
        if (KeyMap.printable(key)) pendingKey = lw; // символ придёт в charTyped
        else send(0, KeyMap.special(key), lw);
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!tile.screen.on) return true;
        if (pendingKey != 0) {
            send(0, c, pendingKey);
            pendingKey = 0;
        } else {
            send(0, c, 0);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double dx, double dy, int button) {
        if (super.mouseClicked(dx, dy, button)) return true;
        int mx = (int) dx, my = (int) dy;
        if (button != 0 || !tile.screen.on || tile.screen.noSignal) return false;
        int sx = leftPos + 10, sy = topPos + 8;
        if (mx < sx || my < sy) return false;
        int cx = (mx - sx) / CW, cy = (my - sy) / CH;
        if (cx >= Screen.W || cy >= Screen.H) return false;
        send(5, (char) cx, cy);
        return true;
    }

    /** Цвет символа с учётом видеокарты и типа монитора. */
    private int color(int fg, int tier, MonitorType mt) {
        if (mt.mono) return fg >= 8 ? mt.monoBright : fg == 0 ? 0x000000 : mt.monoDim;
        if (tier == 1) return fg >= 8 ? 0x33FF33 : fg == 0 ? 0x000000 : 0x1E9E1E;
        return PAL[fg & 15];
    }

    /** Пиксельные иконки PixelOS: каждый пиксель иконки - один пиксель экрана GUI, соседние одинаковые склеиваются в полосы. */
    private void drawSprites(GuiGraphics g, Screen sc, int sx, int sy, MonitorType mt) {
        for (int i = 0; i < sc.spN; i++) {
            int id = sc.spId[i] & 0xFF;
            if (id >= Sprites.ART.length) continue;
            String[] art = Sprites.ART[id];
            int sh = art.length, sw = art[0].length();
            int bx = sx + (sc.spX[i] & 0xFF) * CW + (sw >= 16 ? 1 : 2);
            int by = sy + (sc.spY[i] & 0xFF) * CH + (sh >= 16 ? 1 : 0);
            for (int yy = 0; yy < sh; yy++) {
                String row = art[yy];
                int xx = 0;
                while (xx < sw) {
                    char c = row.charAt(xx);
                    if (c == '.') {
                        xx++;
                        continue;
                    }
                    int x2 = xx + 1;
                    while (x2 < sw && row.charAt(x2) == c) x2++;
                    int ci = Character.digit(c, 16);
                    if (ci >= 0) g.fill(bx + xx, by + yy, bx + x2, by + yy + 1, 0xFF000000 | color(ci, sc.gpuTier, mt));
                    xx = x2;
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int l = leftPos, t = topPos;
        int mon = tile.monitorType();
        if (mon < 0) {
            g.fill(l, t, l + imageWidth, t + imageHeight, 0xFF3A3A3A);
            g.drawString(font, "No monitor connected.", l + 130, t + 100, 0xFF5555, false);
            g.drawString(font, "Place a monitor next to the case or use video cables.", l + 85, t + 115, 0xAAAAAA, false);
            return;
        }
        MonitorType mt = MonitorType.get(mon);
        g.fill(l, t, l + imageWidth, t + imageHeight, mt.bezel);
        if (mt.rgb) {
            int c = 0xFF000000 | RGB[(int) ((System.currentTimeMillis() / 400L) % RGB.length)];
            g.fill(l, t + imageHeight - 4, l + imageWidth, t + imageHeight, c);
        }
        g.drawString(font, mt.name, l + 262, t + 224, 0xAAAAAA, false);
        int sx = l + 10, sy = t + 8;
        g.fill(sx - 2, sy - 2, sx + Screen.W * CW + 2, sy + Screen.H * CH + 2, 0xFF000000);

        Screen sc = tile.screen;
        // индикатор питания
        g.fill(l + imageWidth - 24, t + 226, l + imageWidth - 14, t + 232, sc.on ? 0xFF33FF33 : 0xFF551111);

        if (!sc.on) {
            g.drawString(font, "Monitor is off. Press Power.", sx + 90, sy + 95, 0x666666, false);
            return;
        }
        if (sc.noSignal) {
            g.drawString(font, "NO SIGNAL", sx + 140, sy + 95, 0xAAAAAA, false);
            return;
        }
        for (int y = 0; y < Screen.H; y++) {
            int py = sy + y * CH;
            // фон: подряд идущие инверсные клетки одного цвета рисуем одним прямоугольником
            int x = 0;
            while (x < Screen.W) {
                int at = sc.at[y * Screen.W + x] & 0xFF;
                if ((at & 16) == 0) {
                    x++;
                    continue;
                }
                int bg = color(at & 15, sc.gpuTier, mt);
                int x2 = x + 1;
                while (x2 < Screen.W) {
                    int at2 = sc.at[y * Screen.W + x2] & 0xFF;
                    if ((at2 & 16) == 0 || (at2 & 15) != (at & 15)) break;
                    x2++;
                }
                g.fill(sx + x * CW, py, sx + x2 * CW, py + CH, 0xFF000000 | bg);
                x = x2;
            }
            // символы
            for (x = 0; x < Screen.W; x++) {
                int i = y * Screen.W + x;
                char ch = sc.ch[i];
                if (ch == ' ') continue;
                int at = sc.at[i] & 0xFF;
                int fg = (at & 16) != 0 ? 0x000000 : color(at & 15, sc.gpuTier, mt);
                String st = String.valueOf(ch);
                int gw = ch > 126 ? font.width(st) : 0;
                if (gw > CW) {
                    // широкий символ (кириллица и т.п.) сжимаем под клетку, чтобы не налезал на соседний
                    g.pose().pushPose();
                    g.pose().translate((float) (sx + x * CW), (float) (py + 1), 0f);
                    g.pose().scale((float) CW / gw, 1f, 1f);
                    g.drawString(font, st, 0, 0, 0xFF000000 | fg, false);
                    g.pose().popPose();
                } else {
                    g.drawString(font, st, sx + x * CW, py + 1, 0xFF000000 | fg, false);
                }
            }
        }
        drawSprites(g, sc, sx, sy, mt);
        if (sc.cursor && (System.currentTimeMillis() / 500L) % 2L == 0L && sc.cx < Screen.W && sc.cy < Screen.H) {
            int px = sx + sc.cx * CW, py = sy + sc.cy * CH;
            g.fill(px, py + CH - 2, px + CW - 1, py + CH, 0xFF000000 | color(7, sc.gpuTier, mt));
        }
        if (mt.scanlines) {
            for (int y = 1; y < Screen.H * CH; y += 2) {
                g.fill(sx, sy + y, sx + Screen.W * CW, sy + y + 1, 0x44000000);
            }
        }
    }
}
