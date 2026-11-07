package ru.pcmod.common;

import io.netty.buffer.ByteBuf;
import java.nio.charset.Charset;
import java.util.Arrays;

/** Текстовый экран 60x22 с атрибутами (цвет 0-15, бит 16 = инверсия). */
public class Screen {
    public static final int W = 60, H = 22;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public final char[] ch = new char[W * H];
    public final byte[] at = new byte[W * H];
    public volatile boolean dirty = true;
    public boolean on, noSignal, cursor;
    public int gpuTier, cx, cy, color = 7;
    /** Состояние ПК для индикатора на корпусе: 0 выкл, 1 запускается (POST/BIOS/загрузка/установка), 2 работает (ОС загружена), 3 сбой. */
    public int stage;
    public String led = "";

    /** Слой пиксельных иконок поверх текста (PixelOS): позиция в клетках и номер иконки из Sprites. */
    public static final int MAXSPR = 48;
    public final byte[] spX = new byte[MAXSPR], spY = new byte[MAXSPR], spId = new byte[MAXSPR];
    public int spN;

    public Screen() {
        clear();
    }

    public void clear() {
        Arrays.fill(ch, ' ');
        Arrays.fill(at, (byte) 7);
        cx = 0;
        cy = 0;
        spN = 0;
        dirty = true;
    }

    /** Поставить иконку с левым верхним углом в клетке (x, y). */
    public void sprite(int x, int y, int id) {
        if (spN >= MAXSPR || x < 0 || y < 0 || x >= W || y >= H) return;
        spX[spN] = (byte) x;
        spY[spN] = (byte) y;
        spId[spN] = (byte) id;
        spN++;
        dirty = true;
    }

    public void put(int x, int y, char c, int attr) {
        if (x < 0 || y < 0 || x >= W || y >= H) return;
        ch[y * W + x] = c;
        at[y * W + x] = (byte) attr;
        dirty = true;
    }

    public void text(int x, int y, String str, int attr) {
        for (int i = 0; i < str.length(); i++) put(x + i, y, str.charAt(i), attr);
    }

    private void scrollIfNeeded() {
        while (cy >= H) {
            System.arraycopy(ch, W, ch, 0, W * (H - 1));
            System.arraycopy(at, W, at, 0, W * (H - 1));
            Arrays.fill(ch, W * (H - 1), W * H, ' ');
            Arrays.fill(at, W * (H - 1), W * H, (byte) 7);
            cy--;
        }
    }

    public void printChar(char c) {
        if (c == '\n') {
            cx = 0;
            cy++;
            scrollIfNeeded();
            dirty = true;
            return;
        }
        if (cx >= W) {
            cx = 0;
            cy++;
        }
        scrollIfNeeded();
        put(cx, cy, c, color);
        cx++;
    }

    public void print(String str) {
        for (int i = 0; i < str.length(); i++) printChar(str.charAt(i));
    }

    public void println(String str) {
        print(str);
        printChar('\n');
    }

    public void backspace() {
        if (cx > 0) {
            cx--;
            put(cx, cy, ' ', color);
        }
    }

    public void write(ByteBuf b) {
        b.writeBoolean(on);
        b.writeBoolean(noSignal);
        b.writeBoolean(cursor);
        b.writeByte(gpuTier);
        b.writeByte(stage);
        b.writeByte(cx);
        b.writeByte(cy);
        String l = led == null ? "" : led;
        if (l.length() > 100) l = l.substring(0, 100);
        byte[] lb = l.getBytes(UTF8);
        b.writeByte(lb.length);
        b.writeBytes(lb);
        b.writeByte(spN);
        for (int i = 0; i < spN; i++) {
            b.writeByte(spX[i]);
            b.writeByte(spY[i]);
            b.writeByte(spId[i]);
        }
        for (int i = 0; i < ch.length; i++) {
            char c = ch[i];
            // раньше всё, кроме ASCII, превращалось в '?': кириллица и прочие символы терял даже собственный текст ПК
            b.writeChar(c < 32 || (c >= 127 && c < 160) ? ' ' : c);
        }
        b.writeBytes(at);
    }

    public void read(ByteBuf b) {
        on = b.readBoolean();
        noSignal = b.readBoolean();
        cursor = b.readBoolean();
        gpuTier = b.readUnsignedByte();
        stage = b.readUnsignedByte();
        cx = b.readUnsignedByte();
        cy = b.readUnsignedByte();
        int n = b.readUnsignedByte();
        byte[] lb = new byte[n];
        b.readBytes(lb);
        led = new String(lb, UTF8);
        spN = Math.min(MAXSPR, b.readUnsignedByte());
        for (int i = 0; i < spN; i++) {
            spX[i] = b.readByte();
            spY[i] = b.readByte();
            spId[i] = b.readByte();
        }
        for (int i = 0; i < ch.length; i++) ch[i] = b.readChar();
        b.readBytes(at);
    }
}
