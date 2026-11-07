package ru.pcmod.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/**
 * Графический рабочий стол PCOS и PixelOS: окна, панель задач, меню «Пуск», мышь.
 * В PixelOS поверх клеток рисуются пиксельные иконки (слой Screen.sprite), плюс есть приложение Paint.
 * Рисуется на текстовом экране 60x22: цветные «пиксели» — это пробелы с инверсией
 * (цвет заливки = цвет символа, сам текст при этом чёрный).
 */
public class Desktop {
    static final int W = Screen.W, TB = Screen.H - 1;
    static final int TEAL = 3, GRAY = 7, WHITE = 15, CYAN = 11, GREEN = 10, RED = 12, YELLOW = 14;
    static final int MAXWIN = 5, NOTE_MAX = 31, NOTE_LINES = 200;
    static final int INFO = 0, FILES = 1, NOTE = 2, CALC = 3, CHAT = 4, NETW = 5, PAINT = 6, SHOP = 7, BANK = 8,
            BROWSER = 9, SETUP = 10, DOOM = 11, AVWIN = 12;
    static final int LW = 14;

    // Классическое меню и значки (PCOS). Варианты *_D содержат DOOM: он появляется, только когда игра установлена.
    static final String[] MENU = {"My Computer", "Files", "Notepad", "Calculator", "Chat", "Network", "Browser", "Shop", "Bank", "Terminal", "Restart", "Shut down"};
    static final int[] MENU_ACT = {0, 1, 2, 3, 4, 5, 12, 10, 11, 6, 7, 8};
    static final String[] MENU_D = {"My Computer", "Files", "Notepad", "Calculator", "Chat", "Network", "Browser", "Shop", "Bank", "DOOM", "Terminal", "Restart", "Shut down"};
    static final int[] MENU_ACT_D = {0, 1, 2, 3, 4, 5, 12, 10, 11, 13, 6, 7, 8};
    static final String[] ICON_TAG = {"PC", "DIR", "TXT", "123", "MSG", "NET", "WEB", "SHP", "BNK", ">_"};
    static final String[] ICON_TAG_D = {"PC", "DIR", "TXT", "123", "MSG", "NET", "WEB", "SHP", "BNK", ">_", "DM"};
    static final String[] ICON_NAME = {"My PC", "Files", "Notepad", "Calc", "Chat", "Network", "Browser", "Shop", "Bank", "Terminal"};
    static final String[] ICON_NAME_D = {"My PC", "Files", "Notepad", "Calc", "Chat", "Network", "Browser", "Shop", "Bank", "Terminal", "DOOM"};
    static final int[] ICON_ACT = {0, 1, 2, 3, 4, 5, 12, 10, 11, 6};
    static final int[] ICON_ACT_D = {0, 1, 2, 3, 4, 5, 12, 10, 11, 6, 13};
    static final String[] SHORT = {"My PC", "Files", "Note", "Calc", "Chat", "Net", "Paint", "Shop", "Bank", "Web", "Setup", "Doom", "AV"};

    // ---- PixelOS ----
    static final int PBG = 8, PTITLE = 13, PMW = 17, PW = 16, PH = 12;
    /** Пункты меню PixelOS: подпись, иконка, действие (номер для menuAction). */
    static final String[] PMENU = {"My Computer", "Files", "Notepad", "Calculator", "Paint", "Chat", "Network", "Browser", "Shop", "Bank", "Console", "Restart", "Shut down"};
    static final int[] PMENU_SPR = {Sprites.PC, Sprites.FILES, Sprites.NOTE, Sprites.CALC, Sprites.PAINT, Sprites.CHAT,
            Sprites.NET, Sprites.BROWSER, Sprites.SHOP, Sprites.BANK, Sprites.TERM, Sprites.RESTART, Sprites.POWER};
    static final int[] PMENU_ACT = {0, 1, 2, 3, 9, 4, 5, 12, 10, 11, 6, 7, 8};
    static final String[] PMENU_D = {"My Computer", "Files", "Notepad", "Calculator", "Paint", "Chat", "Network", "Browser", "Shop", "Bank", "DOOM", "Console", "Restart", "Shut down"};
    static final int[] PMENU_SPR_D = {Sprites.PC, Sprites.FILES, Sprites.NOTE, Sprites.CALC, Sprites.PAINT, Sprites.CHAT,
            Sprites.NET, Sprites.BROWSER, Sprites.SHOP, Sprites.BANK, Sprites.DOOM, Sprites.TERM, Sprites.RESTART, Sprites.POWER};
    static final int[] PMENU_ACT_D = {0, 1, 2, 3, 9, 4, 5, 12, 10, 11, 13, 6, 7, 8};
    /** Значки рабочего стола PixelOS (столбцы по четыре). */
    static final String[] PNAME = {"My PC", "Files", "Notepad", "Calc", "Chat", "Network", "Console", "Paint", "Shop", "Bank", "Browser"};
    static final String[] PNAME_D = {"My PC", "Files", "Notepad", "Calc", "Chat", "Network", "Console", "Paint", "Shop", "Bank", "Browser", "DOOM"};
    static final int[] PICON_ACT = {0, 1, 2, 3, 4, 5, 6, 9, 10, 11, 12};
    static final int[] PICON_ACT_D = {0, 1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13};
    static final int[] PICON_SPR = {Sprites.PC, Sprites.FILES, Sprites.NOTE, Sprites.CALC, Sprites.CHAT, Sprites.NET,
            Sprites.TERM, Sprites.PAINT, Sprites.SHOP, Sprites.BANK, Sprites.BROWSER};
    static final int[] PICON_SPR_D = {Sprites.PC, Sprites.FILES, Sprites.NOTE, Sprites.CALC, Sprites.CHAT, Sprites.NET,
            Sprites.TERM, Sprites.PAINT, Sprites.SHOP, Sprites.BANK, Sprites.BROWSER, Sprites.DOOM};
    static final String[] PSHORT = {"PC", "File", "Note", "Calc", "Chat", "Net", "Art", "Shop", "Bank", "Web", "Setp", "Doom", "AV"};
    /** Иконка окна по его типу. */
    static final int[] TYPE_SPR = {Sprites.PC, Sprites.FILES, Sprites.NOTE, Sprites.CALC, Sprites.CHAT, Sprites.NET,
            Sprites.PAINT, Sprites.SHOP, Sprites.BANK, Sprites.BROWSER, Sprites.FILES, Sprites.DOOM, Sprites.NET};

    static final String[][] CALC_KEYS = {
            {"C", "<", "/", "*"},
            {"7", "8", "9", "-"},
            {"4", "5", "6", "+"},
            {"1", "2", "3", "="},
            {"0", ".", "", ""}};

    static class Win {
        int type, x, y, w, h;
        boolean min;
        // Files
        String dir = "/";
        List<String> names = new ArrayList<String>();
        int sel = -1, scroll;
        String msg = "";
        // Notepad
        String path = "";
        List<StringBuilder> lines = new ArrayList<StringBuilder>();
        int cr, cc, top;
        boolean mod;
        // Calculator
        String entry = "0";
        double acc;
        char op;
        boolean fresh, err;
        // Chat / Network
        String fNick = "", fPass = "", fIn = "", cerr = "", peer = ChatStore.GENERAL;
        int focus, cscroll, mtop;
        // Shop / Bank
        long epoch = -1;
        boolean msgOk;
        // Paint
        int[] art;
        int pcol = 12;
        String amsg = "";
        // Browser
        String url = "home.pc", addr = "home.pc";
        List<String> hist = new ArrayList<String>();
        int pscroll;
        String dlName = "";
        double dlDone;
        int dlTotal;
        /** Что качается: 1 = DOOM, 2 = антивирус. */
        int dlKind;
        // Setup (установщик)
        int stage;
        double prog;
        String spath = "";
        /** Какую программу ставит установщик: 1 = DOOM, 2 = антивирус. */
        int skind = 1;
        // DOOM
        DoomGame game;
    }

    final ComputerLogic l;
    final Screen s;
    final boolean pixel;
    final List<Win> wins = new ArrayList<Win>();
    boolean menu;
    boolean caret;
    String lastSig = "";
    int spawn;

    public Desktop(ComputerLogic l) {
        this.l = l;
        this.s = l.s;
        this.pixel = l.isPixel();
    }

    // ===================== вспомогательное =====================
    static String cut(String x, int n) {
        if (n <= 0) return "";
        return x.length() <= n ? x : x.substring(0, n);
    }

    static String padR(String x, int n) {
        StringBuilder b = new StringBuilder(cut(x, n));
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static String padL(String x, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = x.length(); i < n; i++) b.append(' ');
        return b.append(cut(x, n)).toString();
    }

    static String lab(PartSpec p) {
        return p == null ? "none" : p.label;
    }

    void fill(int x, int y, int w, int h, int col) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) s.put(xx, yy, ' ', col | 16);
        }
    }

    void txt(int x, int y, String str, int col) {
        s.text(x, y, str, col | 16);
    }

    Win top() {
        for (int i = wins.size() - 1; i >= 0; i--) {
            if (!wins.get(i).min) return wins.get(i);
        }
        return null;
    }

    void raise(Win w) {
        wins.remove(w);
        w.min = false;
        wins.add(w);
    }

    void close(Win w) {
        if (w.type == AVWIN) Antivirus.actionWait(l);
        if (w.type == NOTE && w.mod) noteSave(w);
        wins.remove(w);
    }

    // ===================== открытие окон =====================
    Win open(int type) {
        if (type != NOTE) {
            for (Win e : wins) {
                if (e.type == type) {
                    raise(e);
                    return e;
                }
            }
        }
        if (wins.size() >= MAXWIN) return null;
        Win w = new Win();
        w.type = type;
        w.w = type == BROWSER ? 56 : type == SETUP ? 44 : type == DOOM ? DoomGame.VW + 2 : type == SHOP ? 56 : type == BANK ? 44 : type == PAINT ? 36 : type == INFO ? 36 : type == FILES ? 34 : type == NOTE ? 36 : type == CHAT ? 52 : type == NETW ? 40 : 22;
        w.h = type == BROWSER ? 19 : type == SETUP ? 13 : type == DOOM ? DoomGame.VH + DoomGame.HUDH + 2 : type == SHOP ? 19 : type == BANK ? 17 : type == PAINT ? 17 : type == INFO ? 14 : type == FILES ? 14 : type == NOTE ? 15 : type == CHAT ? 18 : type == NETW ? 15 : 13;
        w.x = (pixel ? 16 : 11) + (spawn % 4) * 3;
        w.y = 1 + spawn % 3;
        spawn++;
        if (type == AVWIN) {
            w.w = 46;
            w.h = 12;
            w.x = (W - w.w) / 2;
            w.y = 3;
        }
        if (w.x + w.w > W) w.x = W - w.w;
        if (w.y + w.h > TB) w.y = TB - w.h;
        if (w.x < 0) w.x = 0;
        if (w.y < 0) w.y = 0;
        if (type == DOOM) {
            w.x = (W - w.w) / 2;
            w.y = 0;
            w.game = new DoomGame();
        }
        if (type == BROWSER) {
            w.focus = 1;
            w.hist.add(w.url);
        }
        if (type == CHAT) {
            w.fNick = l.chat.savedNick();
            w.focus = w.fNick.length() > 0 ? 1 : 0;
        }
        if (type == NETW || type == SHOP) w.sel = -1;
        if (type == PAINT) {
            w.art = new int[PW * PH];
            paintLoad(w);
        }
        if (type == FILES) {
            CompoundTag r = l.root();
            w.dir = "/home/" + l.user;
            if (r == null || !VFS.isDir(r, w.dir)) w.dir = "/";
            filesRefresh(w);
        }
        wins.add(w);
        return w;
    }

    void openNotepad(String path) {
        for (Win e : wins) {
            if (e.type == NOTE && e.path.equals(path)) {
                raise(e);
                return;
            }
        }
        Win w = open(NOTE);
        if (w == null) return;
        w.path = path;
        CompoundTag r = l.root();
        String txt = r == null ? null : VFS.read(r, path);
        if (txt == null) txt = "";
        if (txt.endsWith("\n")) txt = txt.substring(0, txt.length() - 1);
        for (String ln : txt.split("\n", -1)) w.lines.add(new StringBuilder(ln));
        if (w.lines.isEmpty()) w.lines.add(new StringBuilder());
    }

    void menuAction(int i) {
        switch (i) {
            case 0: open(INFO); break;
            case 1: open(FILES); break;
            case 2: openNotepad("/home/" + l.user + "/notes.txt"); break;
            case 3: open(CALC); break;
            case 4: open(CHAT); break;
            case 5: open(NETW); break;
            case 6: l.enterTerminal(); break;
            case 7: l.startUp(); break;
            case 8: l.shutDown(null); break;
            case 9: open(PAINT); break;
            case 10: open(SHOP); break;
            case 11: open(BANK); break;
            case 12: open(BROWSER); break;
            case 13: openDoom(); break;
            default: break;
        }
    }

    // ===================== отрисовка =====================
    String sig() {
        boolean info = false;
        for (Win w : wins) {
            if (w.type == INFO && !w.min) info = true;
        }
        return l.clock() + "|" + (info ? (int) l.t.temp : -1) + "|" + l.chat.rev + ":" + l.chat.totalUnread() + "|" + l.link().sig() + "|" + shopSig()
                + "|" + (Antivirus.installed(l.root()) ? 1 + Antivirus.threats(l.t, true) : 0)
                + "|" + (l.fxToastUntil > l.t.getLevel().getGameTime() ? 1 : 0);
    }

    public void tick() {
        if (!sig().equals(lastSig)) draw();
    }

    /** Какой диалог сейчас на экране (для него не копятся непрочитанные). */
    void updateViewing() {
        Win tp = top();
        if (tp != null && tp.type == CHAT && l.chat.nick != null) {
            l.chat.viewing = tp.peer;
            l.chat.unread.remove(tp.peer);
        } else {
            l.chat.viewing = null;
        }
    }

    public void draw() {
        dropStaleDoom();
        updateViewing();
        s.clear();
        caret = false;
        if (pixel) {
            drawPixelBg();
        } else {
            fill(0, 0, W, TB, TEAL);
            String[] inames = doomOn() ? ICON_NAME_D : ICON_NAME, itags = doomOn() ? ICON_TAG_D : ICON_TAG;
            for (int i = 0; i < inames.length; i++) {
                int ix = 2 + 9 * (i / 5), y = 1 + 3 * (i % 5);
                String g = padR(" " + itags[i], 6);
                txt(ix, y, g, WHITE);
                txt(ix, y + 1, cut(inames[i], 8), TEAL);
            }
        }
        Win tp = top();
        for (Win w : wins) {
            if (!w.min) drawWin(w, w == tp);
        }
        if (l.fxToastUntil > l.t.getLevel().getGameTime() && l.fxToast.length() > 0) {
            fill(0, TB - 1, W, 1, RED);
            txt(1, TB - 1, cut(l.fxToast, W - 2), WHITE);
        }
        drawTaskbar();
        if (menu) drawMenu();
        if (pixel) drawPixelSprites();
        s.cursor = caret;
        lastSig = sig();
    }

    void drawTaskbar() {
        fill(0, TB, W, 1, GRAY);
        txt(0, TB, pixel ? "  Start" : " Start ", menu ? CYAN : (pixel ? PTITLE : GREEN));
        Win tp = top();
        for (int i = 0; i < wins.size() && i < MAXWIN; i++) {
            Win w = wins.get(i);
            String nm = pixel ? PSHORT[w.type] : SHORT[w.type];
            int un = l.chat.totalUnread();
            if (w.type == CHAT && un > 0) nm = "Msg" + (un > 9 ? "+" : String.valueOf(un));
            String lab = pixel ? "  " + padR(nm, 4) + " " : " " + padR(nm, 5) + " ";
            txt(8 + 7 * i, TB, lab, w == tp ? CYAN : (w.min ? YELLOW : WHITE));
        }
        String clk = l.clock();
        int unr = l.chat.totalUnread();
        if (unr > 0) {
            String m = " MSG " + (unr > 9 ? "9+" : String.valueOf(unr)) + " ";
            txt(W - clk.length() - m.length() - 2, TB, m, YELLOW);
        }
        txt(W - clk.length() - 1, TB, clk, GRAY);
        int avx = avTrayX();
        if (avx >= 0) txt(avx, TB, AV_TAG, Antivirus.threats(l.t, true) > 0 ? RED : GREEN);
    }

    static final String AV_TAG = " AV ";

    /** Где в панели задач значок антивируса (-1, если антивирус не установлен). */
    int avTrayX() {
        if (!Antivirus.installed(l.root())) return -1;
        String clk = l.clock();
        int unr = l.chat.totalUnread();
        int left = W - clk.length() - 1;
        if (unr > 0) left = W - clk.length() - (" MSG " + (unr > 9 ? "9+" : String.valueOf(unr)) + " ").length() - 2;
        return left - AV_TAG.length() - 1;
    }

    void drawMenu() {
        if (pixel) {
            drawPixelMenu();
            return;
        }
        String[] mn = doomOn() ? MENU_D : MENU;
        int y0 = TB - mn.length;
        fill(0, y0, 16, mn.length, WHITE);
        fill(0, y0, 1, mn.length, GRAY);
        for (int i = 0; i < mn.length; i++) txt(2, y0 + i, padR(mn[i], 13), WHITE);
    }

    void drawWin(Win w, boolean active) {
        int tc = active ? (pixel ? PTITLE : CYAN) : GRAY;
        fill(w.x, w.y, w.w, w.h, GRAY);
        fill(w.x + 1, w.y + 1, w.w - 2, w.h - 2, WHITE);
        fill(w.x, w.y, w.w, 1, tc);
        String title;
        if (w.type == INFO) title = "My Computer";
        else if (w.type == FILES) title = "Files - " + w.dir;
        else if (w.type == NOTE) title = "Notepad - " + VFS.name(w.path);
        else if (w.type == CHAT) title = l.chat.nick != null ? "Chat - " + l.chat.nick : "Chat";
        else if (w.type == NETW) title = "Network";
        else if (w.type == PAINT) title = "Paint - art.px";
        else if (w.type == SHOP) title = "Shop - internet store";
        else if (w.type == BANK) title = "Bank";
        else if (w.type == BROWSER) title = "Browser - " + w.url;
        else if (w.type == SETUP) title = "Setup";
        else if (w.type == DOOM) title = "DOOM";
        else if (w.type == AVWIN) title = "Антивирус";
        else title = "Calculator";
        txt(w.x + 1, w.y, cut(title, w.w - 9), tc);
        txt(w.x + w.w - 7, w.y, " _ ", GRAY);
        txt(w.x + w.w - 4, w.y, " X ", RED);
        if (w.type == INFO) drawInfo(w);
        else if (w.type == FILES) drawFiles(w);
        else if (w.type == NOTE) drawNote(w, active);
        else if (w.type == CHAT) drawChat(w, active);
        else if (w.type == NETW) drawNet(w, active);
        else if (w.type == PAINT) drawPaint(w);
        else if (w.type == SHOP) drawShop(w, active);
        else if (w.type == BANK) drawBank(w, active);
        else if (w.type == BROWSER) drawBrowser(w, active);
        else if (w.type == SETUP) drawSetup(w);
        else if (w.type == DOOM) drawDoom(w);
        else if (w.type == AVWIN) drawAv(w);
        else drawCalc(w);
    }

    List<String> infoLines() {
        TileComputer t = l.t;
        PartSpec cpu = t.spec(TileComputer.CPU), mb = t.spec(TileComputer.MB), gpu = t.spec(TileComputer.GPU),
                sto = t.spec(TileComputer.STORAGE), cool = t.spec(TileComputer.COOLER), psu = t.spec(TileComputer.PSU);
        PartSpec ram = null;
        for (int i = TileComputer.RAM0; i <= TileComputer.RAM3 && ram == null; i++) ram = t.spec(i);
        int mon = t.monitorType();
        List<String> o = new ArrayList<String>();
        o.add(pixel ? "PixelOS 1.0 Desktop" : "PCOS Desktop 2.0");
        o.add("User   : " + l.user + "@" + l.host);
        o.add("CPU    : " + lab(cpu) + "  " + (cpu == null ? 0 : cpu.cores) + "c "
                + (cpu == null ? 0.0 : cpu.ghz) + " GHz");
        o.add("Board  : " + lab(mb) + (mb == null ? "" : " (" + PartSpec.socketName(mb.socket) + ")"));
        o.add("RAM    : " + t.totalRamMB() + " MB " + (ram == null ? "" : PartSpec.ramName(ram.ramType)));
        o.add("GPU    : " + lab(gpu) + "  " + (gpu == null ? 0 : gpu.vram) + " MB");
        o.add("Disk   : " + lab(sto) + " (" + (sto == null ? 0 : sto.storageKB) + " KB)");
        o.add("Cooler : " + lab(cool));
        o.add("PSU    : " + (psu == null ? 0 : psu.watts) + " W, load " + t.requiredWatts() + " W");
        o.add("Temp   : " + (int) t.temp + " C");
        o.add("Screen : " + (mon < 0 ? "none" : MonitorType.get(mon).describe()));
        o.add("Net    : " + l.link().describe());
        return o;
    }

    void drawInfo(Win w) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        List<String> lines = infoLines();
        for (int i = 0; i < lines.size() && i < ih; i++) txt(ix + 1, iy + i, cut(lines.get(i), iw - 2), WHITE);
    }

    // ---------- Files ----------
    void filesRefresh(Win w) {
        CompoundTag r = l.root();
        if (r == null) return;
        w.names = VFS.listDir(r, w.dir);
        if (w.sel >= w.names.size()) w.sel = w.names.size() - 1;
        int rows = w.h - 5;
        if (w.scroll > Math.max(0, w.names.size() - rows)) w.scroll = Math.max(0, w.names.size() - rows);
    }

    void filesUp(Win w) {
        w.dir = VFS.parent(w.dir);
        w.sel = -1;
        w.scroll = 0;
        w.msg = "";
        filesRefresh(w);
    }

    void filesOpen(Win w) {
        if (w.sel < 0 || w.sel >= w.names.size()) return;
        String n = w.names.get(w.sel);
        if (n.endsWith("/")) {
            w.dir = VFS.resolve(w.dir, n.substring(0, n.length() - 1));
            w.sel = -1;
            w.scroll = 0;
            w.msg = "";
            filesRefresh(w);
        } else {
            String p = VFS.resolve(w.dir, n);
            if (isSetupFile(p)) openSetup(p);
            else openNotepad(p);
        }
    }

    void filesNew(Win w, boolean dir) {
        CompoundTag r = l.root();
        if (r == null) return;
        int cap = l.t.spec(TileComputer.STORAGE).storageKB;
        for (int i = 0; i < 100; i++) {
            String nm = dir ? (i == 0 ? "NewFolder" : "NewFolder" + i) : (i == 0 ? "newfile.txt" : "newfile" + i + ".txt");
            String p = VFS.resolve(w.dir, nm);
            if (VFS.isFile(r, p) || VFS.isDir(r, p)) continue;
            boolean ok = dir ? VFS.mkdir(r, p) : VFS.write(r, p, "", cap);
            w.msg = ok ? "Created " + nm : "No space or bad path";
            filesRefresh(w);
            return;
        }
        w.msg = "Too many files";
    }

    void filesDelete(Win w) {
        CompoundTag r = l.root();
        if (r == null || w.sel < 0 || w.sel >= w.names.size()) return;
        String n = w.names.get(w.sel);
        if (n.endsWith("/")) n = n.substring(0, n.length() - 1);
        String p = VFS.resolve(w.dir, n);
        if (p.equals("/") || p.equals("/home") || p.equals("/etc") || p.equals("/bin") || p.equals("/home/" + l.user)) {
            w.msg = "Cannot delete this";
            return;
        }
        int res = VFS.remove(r, p);
        w.msg = res == 0 ? "Deleted " + n : res == 2 ? "Folder is not empty" : "Not found";
        filesRefresh(w);
    }

    void drawFiles(Win w) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        int rows = ih - 3;
        txt(ix + 1, iy, cut("C:" + w.dir, iw - 2), WHITE);
        txt(ix + 1, iy + 1, " Up ", GRAY);
        txt(ix + 6, iy + 1, " +File ", GRAY);
        txt(ix + 14, iy + 1, " +Dir ", GRAY);
        txt(ix + 21, iy + 1, " Del ", RED);
        for (int r = 0; r < rows; r++) {
            int idx = w.scroll + r;
            if (idx >= w.names.size()) break;
            txt(ix + 1, iy + 2 + r, padR(w.names.get(idx), iw - 4), idx == w.sel ? CYAN : WHITE);
        }
        txt(ix + iw - 2, iy + 2, "^", GRAY);
        txt(ix + iw - 2, iy + 1 + rows, "v", GRAY);
        CompoundTag r = l.root();
        String st = w.msg.length() > 0 ? w.msg
                : w.names.size() + " items, free " + (r == null ? 0 : Math.max(0, VFS.free(r, l.t.spec(TileComputer.STORAGE).storageKB)) / 1024) + " KB";
        fill(ix, iy + ih - 1, iw, 1, GRAY);
        txt(ix + 1, iy + ih - 1, cut(st, iw - 2), GRAY);
    }

    void clickFiles(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        int rows = ih - 3;
        w.msg = "";
        if (cy == iy + 1) {
            if (cx >= ix + 1 && cx <= ix + 4) filesUp(w);
            else if (cx >= ix + 6 && cx <= ix + 12) filesNew(w, false);
            else if (cx >= ix + 14 && cx <= ix + 19) filesNew(w, true);
            else if (cx >= ix + 21 && cx <= ix + 25) filesDelete(w);
            return;
        }
        if (cx == ix + iw - 2) {
            if (cy == iy + 2) w.scroll = Math.max(0, w.scroll - 3);
            else if (cy == iy + 1 + rows) w.scroll = Math.max(0, Math.min(w.scroll + 3, Math.max(0, w.names.size() - rows)));
            return;
        }
        int r = cy - (iy + 2);
        if (r >= 0 && r < rows && cx >= ix + 1 && cx <= ix + iw - 3) {
            int idx = w.scroll + r;
            if (idx < w.names.size()) {
                if (idx == w.sel) filesOpen(w);
                else w.sel = idx;
            }
        }
    }

    void keyFiles(Win w, int key) {
        int rows = w.h - 5;
        if (key == ComputerLogic.K_UP) {
            if (w.sel > 0) w.sel--;
            else if (w.sel < 0 && !w.names.isEmpty()) w.sel = 0;
        } else if (key == ComputerLogic.K_DOWN) {
            if (w.sel < w.names.size() - 1) w.sel++;
        } else if (key == ComputerLogic.K_ENTER || key == ComputerLogic.K_NENTER) {
            filesOpen(w);
            return;
        } else if (key == ComputerLogic.K_BACK) {
            filesUp(w);
            return;
        } else if (key == ComputerLogic.K_DEL) {
            filesDelete(w);
            return;
        }
        if (w.sel >= 0) {
            if (w.sel < w.scroll) w.scroll = w.sel;
            if (w.sel >= w.scroll + rows) w.scroll = w.sel - rows + 1;
        }
    }

    // ---------- Notepad ----------
    void noteSave(Win w) {
        StringBuilder b = new StringBuilder();
        for (StringBuilder ln : w.lines) b.append(ln.toString()).append('\n');
        CompoundTag r = l.root();
        int cap = l.t.spec(TileComputer.STORAGE).storageKB;
        if (r != null && VFS.write(r, w.path, b.toString(), cap)) {
            w.mod = false;
            w.msg = "Saved.";
        } else {
            w.msg = "Cannot save!";
        }
    }

    void noteScroll(Win w) {
        int rows = w.h - 4;
        if (w.cr < w.top) w.top = w.cr;
        if (w.cr >= w.top + rows) w.top = w.cr - rows + 1;
        if (w.top < 0) w.top = 0;
    }

    void drawNote(Win w, boolean active) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        txt(ix + 1, iy, " Save ", GRAY);
        txt(ix + 8, iy, cut(VFS.name(w.path) + (w.mod ? " *" : ""), iw - 9), WHITE);
        int rows = ih - 2;
        for (int r = 0; r < rows; r++) {
            int li = w.top + r;
            if (li >= w.lines.size()) break;
            txt(ix + 1, iy + 1 + r, cut(w.lines.get(li).toString(), iw - 2), WHITE);
        }
        fill(ix, iy + ih - 1, iw, 1, GRAY);
        txt(ix + 1, iy + ih - 1, cut("Ln " + (w.cr + 1) + " Col " + (w.cc + 1) + "  " + w.msg, iw - 2), GRAY);
        if (active) {
            s.cx = ix + 1 + Math.min(w.cc, iw - 3);
            s.cy = iy + 1 + (w.cr - w.top);
            caret = true;
        }
    }

    void clickNote(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1, ih = w.h - 2;
        if (cy == iy) {
            if (cx >= ix + 1 && cx <= ix + 6) noteSave(w);
            return;
        }
        int r = cy - (iy + 1);
        if (r >= 0 && r < ih - 2) {
            int li = w.top + r;
            if (li < w.lines.size()) {
                w.cr = li;
                w.cc = Math.max(0, Math.min(cx - (ix + 1), w.lines.get(li).length()));
                w.msg = "";
            }
        }
    }

    void keyNote(Win w, char c, int key) {
        StringBuilder ln = w.lines.get(w.cr);
        w.msg = "";
        if (key == ComputerLogic.K_LEFT) {
            if (w.cc > 0) {
                w.cc--;
            } else if (w.cr > 0) {
                w.cr--;
                w.cc = w.lines.get(w.cr).length();
            }
        } else if (key == ComputerLogic.K_RIGHT) {
            if (w.cc < ln.length()) {
                w.cc++;
            } else if (w.cr < w.lines.size() - 1) {
                w.cr++;
                w.cc = 0;
            }
        } else if (key == ComputerLogic.K_UP) {
            if (w.cr > 0) {
                w.cr--;
                w.cc = Math.min(w.cc, w.lines.get(w.cr).length());
            }
        } else if (key == ComputerLogic.K_DOWN) {
            if (w.cr < w.lines.size() - 1) {
                w.cr++;
                w.cc = Math.min(w.cc, w.lines.get(w.cr).length());
            }
        } else if (key == 199) {
            w.cc = 0;
        } else if (key == 207) {
            w.cc = ln.length();
        } else if (key == ComputerLogic.K_BACK) {
            if (w.cc > 0) {
                ln.deleteCharAt(w.cc - 1);
                w.cc--;
                w.mod = true;
            } else if (w.cr > 0) {
                StringBuilder prev = w.lines.get(w.cr - 1);
                if (prev.length() + ln.length() <= NOTE_MAX) {
                    w.cc = prev.length();
                    prev.append(ln.toString());
                    w.lines.remove(w.cr);
                    w.cr--;
                    w.mod = true;
                }
            }
        } else if (key == ComputerLogic.K_DEL) {
            if (w.cc < ln.length()) {
                ln.deleteCharAt(w.cc);
                w.mod = true;
            }
        } else if (key == ComputerLogic.K_ENTER || key == ComputerLogic.K_NENTER) {
            if (w.lines.size() < NOTE_LINES) {
                StringBuilder rest = new StringBuilder(ln.substring(w.cc));
                ln.setLength(w.cc);
                w.lines.add(w.cr + 1, rest);
                w.cr++;
                w.cc = 0;
                w.mod = true;
            }
        } else if (c >= 32 && c < 127 && ln.length() < NOTE_MAX) {
            ln.insert(w.cc, c);
            w.cc++;
            w.mod = true;
        }
        noteScroll(w);
    }

    // ---------- Calculator ----------
    static boolean isOp(String k) {
        return k.equals("+") || k.equals("-") || k.equals("*") || k.equals("/");
    }

    static String fmt(double v) {
        String x = (v == Math.floor(v) && Math.abs(v) < 1e12) ? String.valueOf((long) v) : String.valueOf(v);
        return x.length() > 14 ? x.substring(0, 14) : x;
    }

    void calcPress(Win w, char k) {
        if (w.err && k != 'C') return;
        if (k >= '0' && k <= '9') {
            if (w.fresh) {
                w.entry = String.valueOf(k);
                w.fresh = false;
            } else if (w.entry.length() < 14) {
                w.entry = w.entry.equals("0") ? String.valueOf(k) : w.entry + k;
            }
        } else if (k == '.') {
            if (w.fresh) {
                w.entry = "0.";
                w.fresh = false;
            } else if (w.entry.indexOf('.') < 0 && w.entry.length() < 14) {
                w.entry = w.entry + ".";
            }
        } else if (k == 'C') {
            w.entry = "0";
            w.acc = 0;
            w.op = 0;
            w.fresh = false;
            w.err = false;
        } else if (k == 'B') {
            if (!w.fresh) w.entry = w.entry.length() > 1 ? w.entry.substring(0, w.entry.length() - 1) : "0";
        } else if (k == '+' || k == '-' || k == '*' || k == '/' || k == '=') {
            double cur;
            try {
                cur = Double.parseDouble(w.entry);
            } catch (NumberFormatException e) {
                cur = 0;
            }
            if (w.op == 0) {
                w.acc = cur;
            } else if (!w.fresh) {
                if (w.op == '+') {
                    w.acc = w.acc + cur;
                } else if (w.op == '-') {
                    w.acc = w.acc - cur;
                } else if (w.op == '*') {
                    w.acc = w.acc * cur;
                } else {
                    if (cur == 0) {
                        w.err = true;
                        w.entry = "Error";
                        w.op = 0;
                        return;
                    }
                    w.acc = w.acc / cur;
                }
            }
            w.entry = fmt(w.acc);
            w.op = k == '=' ? 0 : k;
            w.fresh = true;
        }
    }

    void drawCalc(Win w) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2;
        fill(ix + 1, iy, iw - 2, 1, GREEN);
        txt(ix + 1, iy, padL(w.entry, iw - 2), GREEN);
        for (int r = 0; r < CALC_KEYS.length; r++) {
            for (int c = 0; c < 4; c++) {
                String k = CALC_KEYS[r][c];
                if (k.length() == 0) continue;
                int col = k.equals("=") ? GREEN : k.equals("C") ? RED : isOp(k) ? YELLOW : GRAY;
                txt(ix + 2 + 4 * c, iy + 2 + 2 * r, " " + k + " ", col);
            }
        }
    }

    void clickCalc(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1;
        for (int r = 0; r < CALC_KEYS.length; r++) {
            for (int c = 0; c < 4; c++) {
                if (cy == iy + 2 + 2 * r && cx >= ix + 2 + 4 * c && cx < ix + 5 + 4 * c) {
                    String k = CALC_KEYS[r][c];
                    if (k.length() > 0) calcPress(w, k.equals("<") ? 'B' : k.charAt(0));
                    return;
                }
            }
        }
    }

    void keyCalc(Win w, char c, int key) {
        if (key == ComputerLogic.K_ENTER || key == ComputerLogic.K_NENTER) calcPress(w, '=');
        else if (key == ComputerLogic.K_BACK) calcPress(w, 'B');
        else if (c == 'c' || c == 'C') calcPress(w, 'C');
        else if ((c >= '0' && c <= '9') || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' || c == '=') calcPress(w, c);
    }

    // ---------- Chat (мессенджер) ----------
    static String mask(String x) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < x.length(); i++) b.append('*');
        return b.toString();
    }

    List<String> chatContacts() {
        List<String> o = new ArrayList<String>();
        o.add(ChatStore.GENERAL);
        o.addAll(l.chat.users);
        return o;
    }

    String contactLabel(String c) {
        ChatClient ch = l.chat;
        String base = c.equals(ChatStore.GENERAL) ? "# general" : (ch.onlineNicks.contains(c) ? "+ " : "- ") + c;
        int n = ch.unreadFor(c);
        String un = n > 0 ? "(" + (n > 9 ? "9+" : String.valueOf(n)) + ")" : "";
        return padR(cut(base, LW - un.length()), LW - un.length()) + un;
    }

    void chatPick(Win w, String peer) {
        w.peer = peer;
        w.mtop = 0;
        l.chat.unread.remove(peer);
        l.chat.viewing = peer;
    }

    void chatSelect(Win w, int delta) {
        List<String> cs = chatContacts();
        int n = cs.size();
        int idx = cs.indexOf(w.peer);
        if (idx < 0) idx = 0;
        idx = ((idx + delta) % n + n) % n;
        chatPick(w, cs.get(idx));
        int rows = w.h - 5;
        if (idx < w.cscroll) w.cscroll = idx;
        if (idx >= w.cscroll + rows) w.cscroll = idx - rows + 1;
    }

    void chatAuth(Win w, boolean register) {
        ChatClient ch = l.chat;
        if (w.fNick.length() == 0 || w.fPass.length() == 0) {
            w.cerr = "Enter nick and password";
            return;
        }
        String e = register ? ch.register(w.fNick, w.fPass) : ch.login(w.fNick, w.fPass);
        if (e != null) {
            w.cerr = e;
            return;
        }
        w.fPass = "";
        w.cerr = "";
        w.fIn = "";
        w.peer = ChatStore.GENERAL;
        w.cscroll = 0;
        w.mtop = 0;
    }

    void chatSend(Win w) {
        String text = w.fIn.trim();
        if (text.length() == 0) return;
        String e = l.chat.send(w.peer, text);
        if (e != null) {
            w.cerr = e;
        } else {
            w.fIn = "";
            w.mtop = 0;
        }
    }

    /** Строки ленты диалога с переносами (свои сообщения голубые). */
    void chatLines(Win w, int mw, List<String> txts, List<Integer> cols) {
        ChatClient ch = l.chat;
        List<ChatStore.Msg> th = ch.thread(w.peer);
        for (int i = Math.max(0, th.size() - 40); i < th.size(); i++) {
            ChatStore.Msg m = th.get(i);
            boolean me = m.from.equals(ch.nick);
            String full = ChatClient.hhmm(m.time) + " " + (me ? "me" : m.from) + ": " + m.text;
            int col = me ? CYAN : WHITE;
            int pos = 0;
            boolean first = true;
            while (pos < full.length()) {
                int room = first ? mw : mw - 2;
                int end = Math.min(full.length(), pos + room);
                txts.add((first ? "" : "  ") + full.substring(pos, end));
                cols.add(col);
                pos = end;
                first = false;
            }
        }
    }

    void drawChat(Win w, boolean active) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2;
        ChatClient ch = l.chat;
        NetLink k = l.link();
        if (!k.up()) {
            txt(ix + 1, iy + 1, "No network connection.", WHITE);
            txt(ix + 1, iy + 3, cut(k.status, iw - 2), WHITE);
            txt(ix + 1, iy + 5, "Put a cable or router next to the PC,", WHITE);
            txt(ix + 1, iy + 6, "or connect to Wi-Fi in 'Network'.", WHITE);
            return;
        }
        List<TileServer> sv = ch.servers();
        if (sv.isEmpty()) {
            txt(ix + 1, iy + 1, "No chat servers on this network.", WHITE);
            txt(ix + 1, iy + 3, "Place a Server block next to a cable", WHITE);
            txt(ix + 1, iy + 4, "or router of this network.", WHITE);
            return;
        }
        if (ch.nick == null) drawChatLogin(w, active, sv.size());
        else drawChatView(w, active);
    }

    void drawChatLogin(Win w, boolean active, int servers) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2;
        ChatClient ch = l.chat;
        TileServer srv = ch.server();
        txt(ix + 1, iy, cut("Server: " + (srv == null ? "-" : srv.domain), iw - 12), WHITE);
        if (servers > 1) txt(ix + iw - 8, iy, " Next ", GRAY);
        txt(ix + 1, iy + 2, "Nick:", WHITE);
        txt(ix + 11, iy + 2, padR(w.fNick, 18), w.focus == 0 ? CYAN : GRAY);
        txt(ix + 1, iy + 4, "Password:", WHITE);
        txt(ix + 11, iy + 4, padR(mask(w.fPass), 18), w.focus == 1 ? CYAN : GRAY);
        txt(ix + 2, iy + 6, " Login ", GREEN);
        txt(ix + 11, iy + 6, " Register ", YELLOW);
        String err = w.cerr.length() > 0 ? w.cerr : ch.err;
        if (err.length() > 0) txt(ix + 1, iy + 8, padR(err, iw - 2), RED);
        txt(ix + 1, iy + 10, "Nick: a-z, 0-9, _ (max 16 chars).", WHITE);
        txt(ix + 1, iy + 11, "Tab: next field. Enter: log in.", WHITE);
        txt(ix + 1, iy + 12, "No account yet? Fill it in, Register.", WHITE);
        if (active) {
            String f = w.focus == 0 ? w.fNick : w.fPass;
            s.cx = ix + 11 + Math.min(17, f.length());
            s.cy = iy + (w.focus == 0 ? 2 : 4);
            caret = true;
        }
    }

    void drawChatView(Win w, boolean active) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        ChatClient ch = l.chat;
        TileServer srv = ch.server();
        List<String> cs = chatContacts();
        if (!cs.contains(w.peer)) w.peer = ChatStore.GENERAL;
        if (active) ch.unread.remove(w.peer);
        int rows = ih - 3;

        // шапка
        fill(ix, iy, iw, 1, GRAY);
        txt(ix + 1, iy, cut(ch.nick + "@" + (srv == null ? "-" : srv.domain), iw - 12), GRAY);
        txt(ix + iw - 9, iy, " Logout ", RED);

        // контакты
        fill(ix, iy + 1, LW, rows, GRAY);
        int maxScroll = Math.max(0, cs.size() - rows);
        if (w.cscroll > maxScroll) w.cscroll = maxScroll;
        for (int r = 0; r < rows; r++) {
            int idx = w.cscroll + r;
            if (idx >= cs.size()) break;
            String c = cs.get(idx);
            txt(ix, iy + 1 + r, contactLabel(c), c.equals(w.peer) ? CYAN : GRAY);
        }

        // лента сообщений
        int mx = ix + LW + 1, mw = iw - LW - 1;
        List<String> tx = new ArrayList<String>();
        List<Integer> cl = new ArrayList<Integer>();
        chatLines(w, mw, tx, cl);
        if (tx.isEmpty()) txt(mx, iy + 1, "(no messages yet)", WHITE);
        int maxTop = Math.max(0, tx.size() - rows);
        if (w.mtop > maxTop) w.mtop = maxTop;
        if (w.mtop < 0) w.mtop = 0;
        int end = tx.size() - w.mtop, start = Math.max(0, end - rows);
        int y0 = iy + 1 + rows - (end - start);
        for (int i = start; i < end; i++) txt(mx, y0 + (i - start), padR(tx.get(i), mw), cl.get(i));

        // строка состояния
        int sy = iy + ih - 2;
        String st;
        int sc = GRAY;
        if (w.cerr.length() > 0) {
            st = w.cerr;
            sc = RED;
        } else if (!ch.online) {
            st = "offline: no connection to the server";
            sc = YELLOW;
        } else {
            st = (w.mtop > 0 ? "[older] " : "") + "To: " + w.peer + "   (PgUp/PgDn scroll)";
        }
        txt(ix, sy, padR(" " + st, iw), sc);

        // поле ввода
        int iy2 = iy + ih - 1;
        int vis = iw - 10;
        String tail = w.fIn.length() > vis ? w.fIn.substring(w.fIn.length() - vis) : w.fIn;
        txt(ix, iy2, padR("> " + tail, iw - 7), CYAN);
        txt(ix + iw - 7, iy2, " Send ", GREEN);
        if (active) {
            s.cx = ix + 2 + tail.length();
            s.cy = iy2;
            caret = true;
        }
    }

    void clickChat(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        ChatClient ch = l.chat;
        w.cerr = "";
        if (!l.link().up()) return;
        List<TileServer> sv = ch.servers();
        if (sv.isEmpty()) return;
        if (ch.nick == null) {
            if (cy == iy && sv.size() > 1 && cx >= ix + iw - 8 && cx <= ix + iw - 3) ch.nextServer();
            else if (cy == iy + 2 && cx >= ix + 11) w.focus = 0;
            else if (cy == iy + 4 && cx >= ix + 11) w.focus = 1;
            else if (cy == iy + 6 && cx >= ix + 2 && cx <= ix + 8) chatAuth(w, false);
            else if (cy == iy + 6 && cx >= ix + 11 && cx <= ix + 20) chatAuth(w, true);
            return;
        }
        if (cy == iy && cx >= ix + iw - 9 && cx <= ix + iw - 2) {
            ch.logout();
            w.fPass = "";
            w.fIn = "";
            return;
        }
        int rows = ih - 3;
        if (cy >= iy + 1 && cy < iy + 1 + rows && cx >= ix && cx < ix + LW) {
            List<String> cs = chatContacts();
            int idx = w.cscroll + (cy - iy - 1);
            if (idx < cs.size()) chatPick(w, cs.get(idx));
            return;
        }
        if (cy == iy + ih - 1 && cx >= ix + iw - 7 && cx <= ix + iw - 2) chatSend(w);
    }

    void keyChat(Win w, char c, int key) {
        ChatClient ch = l.chat;
        boolean enter = key == ComputerLogic.K_ENTER || key == ComputerLogic.K_NENTER;
        w.cerr = "";
        if (!l.link().up() || ch.servers().isEmpty()) return;
        if (ch.nick == null) {
            if (key == 15) {
                w.focus = 1 - w.focus;
            } else if (key == ComputerLogic.K_UP || key == ComputerLogic.K_DOWN) {
                ch.nextServer();
            } else if (enter) {
                chatAuth(w, false);
            } else if (key == ComputerLogic.K_BACK) {
                if (w.focus == 0 && w.fNick.length() > 0) w.fNick = w.fNick.substring(0, w.fNick.length() - 1);
                else if (w.focus == 1 && w.fPass.length() > 0) w.fPass = w.fPass.substring(0, w.fPass.length() - 1);
            } else if (c >= 33 && c < 127) {
                if (w.focus == 0) {
                    if (w.fNick.length() < 16) w.fNick = w.fNick + Character.toLowerCase(c);
                } else if (w.fPass.length() < 20) {
                    w.fPass = w.fPass + c;
                }
            }
            return;
        }
        if (enter) {
            chatSend(w);
        } else if (key == ComputerLogic.K_UP) {
            chatSelect(w, -1);
        } else if (key == ComputerLogic.K_DOWN || key == 15) {
            chatSelect(w, 1);
        } else if (key == 201) {
            w.mtop += 5;
        } else if (key == 209) {
            w.mtop = Math.max(0, w.mtop - 5);
        } else if (key == ComputerLogic.K_BACK) {
            if (w.fIn.length() > 0) w.fIn = w.fIn.substring(0, w.fIn.length() - 1);
        } else if (c >= 32 && c < 127 && w.fIn.length() < ChatStore.MAX_TEXT) {
            w.fIn = w.fIn + c;
        }
    }

    // ---------- Network (кабель и Wi-Fi) ----------
    void netConnect(Win w) {
        List<Net.Ap> aps = Net.scan(l.t);
        if (w.sel < 0 || w.sel >= aps.size()) {
            w.msg = "Select a network first";
            return;
        }
        Net.Ap a = aps.get(w.sel);
        int res = Net.check(l.t, a.ssid, w.fPass);
        CompoundTag r = l.root();
        if (res == Net.OK && r != null) {
            r.putString("wifi_ssid", a.ssid);
            r.putString("wifi_pass", w.fPass);
            l.relink();
            w.fPass = "";
            w.msg = "Connected to " + a.ssid;
        } else if (res == Net.BAD_PASS) {
            w.msg = "Wrong password";
        } else if (res == Net.NO_MODULE) {
            w.msg = "No Wi-Fi module installed";
        } else {
            w.msg = "Network is out of range";
        }
    }

    void netDisconnect(Win w) {
        CompoundTag r = l.root();
        if (r == null) return;
        r.remove("wifi_ssid");
        r.remove("wifi_pass");
        l.relink();
        w.msg = "Wi-Fi disconnected";
    }

    void drawNet(Win w, boolean active) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        NetLink k = l.link();
        txt(ix + 1, iy, cut("Link   : " + k.describe(), iw - 2), WHITE);
        String gw = (!k.up() || k.router == null) ? "-" : k.router.ssid;
        txt(ix + 1, iy + 1, cut("Gateway: " + gw + (k.type == NetLink.WIFI ? "  " + NetLink.bars(k.signal) : ""), iw - 2), WHITE);
        StringBuilder sb = new StringBuilder();
        for (TileServer sv : k.servers) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(sv.domain);
        }
        txt(ix + 1, iy + 2, cut("Servers: " + (k.servers.isEmpty() ? "none" : sb.toString()), iw - 2), WHITE);

        boolean mod = l.t.spec(TileComputer.NET) != null;
        fill(ix, iy + 3, iw, 1, GRAY);
        txt(ix + 1, iy + 3, mod ? "Wi-Fi networks" : "No Wi-Fi module installed", GRAY);
        fill(ix, iy + ih - 1, iw, 1, GRAY);
        txt(ix + 1, iy + ih - 1, cut(w.msg, iw - 2), GRAY);
        if (!mod) {
            txt(ix + 1, iy + 5, "Install a Wi-Fi Module in the PC", WHITE);
            txt(ix + 1, iy + 6, "hardware panel, or use a cable.", WHITE);
            return;
        }
        List<Net.Ap> aps = Net.scan(l.t);
        int rows = 5;
        if (w.sel >= aps.size()) w.sel = aps.size() - 1;
        if (w.sel >= 0) {
            if (w.sel < w.scroll) w.scroll = w.sel;
            if (w.sel >= w.scroll + rows) w.scroll = w.sel - rows + 1;
        }
        if (w.scroll > Math.max(0, aps.size() - rows)) w.scroll = Math.max(0, aps.size() - rows);
        if (aps.isEmpty()) txt(ix + 1, iy + 4, "No networks in range.", WHITE);
        for (int r = 0; r < rows; r++) {
            int idx = w.scroll + r;
            if (idx >= aps.size()) break;
            Net.Ap a = aps.get(idx);
            boolean cur = k.type == NetLink.WIFI && a.ssid.equals(k.ssid);
            String row = (cur ? "* " : "  ") + padR(a.ssid, 16) + " " + NetLink.bars(a.signal) + (a.secured ? "  key" : "  open");
            txt(ix + 1, iy + 4 + r, padR(row, iw - 2), idx == w.sel ? CYAN : WHITE);
        }
        txt(ix + 1, iy + 9, "Password:", WHITE);
        txt(ix + 11, iy + 9, padR(mask(w.fPass), 18), CYAN);
        txt(ix + 2, iy + 10, " Connect ", GREEN);
        txt(ix + 13, iy + 10, " Disconnect ", YELLOW);
        if (active) {
            s.cx = ix + 11 + Math.min(17, w.fPass.length());
            s.cy = iy + 9;
            caret = true;
        }
    }

    void clickNet(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1;
        w.msg = "";
        if (l.t.spec(TileComputer.NET) == null) return;
        int r = cy - (iy + 4);
        if (r >= 0 && r < 5) {
            int idx = w.scroll + r;
            if (idx < Net.scan(l.t).size()) w.sel = idx;
            return;
        }
        if (cy == iy + 10) {
            if (cx >= ix + 2 && cx <= ix + 10) netConnect(w);
            else if (cx >= ix + 13 && cx <= ix + 24) netDisconnect(w);
        }
    }

    void keyNet(Win w, char c, int key) {
        w.msg = "";
        if (l.t.spec(TileComputer.NET) == null) return;
        int n = Net.scan(l.t).size();
        if (key == ComputerLogic.K_UP) {
            if (w.sel > 0) w.sel--;
            else if (w.sel < 0 && n > 0) w.sel = 0;
        } else if (key == ComputerLogic.K_DOWN) {
            if (w.sel < n - 1) w.sel++;
        } else if (key == ComputerLogic.K_ENTER || key == ComputerLogic.K_NENTER) {
            netConnect(w);
        } else if (key == ComputerLogic.K_BACK) {
            if (w.fPass.length() > 0) w.fPass = w.fPass.substring(0, w.fPass.length() - 1);
        } else if (c >= 33 && c < 127 && w.fPass.length() < 16) {
            w.fPass = w.fPass + c;
        }
    }

    // ===================== ввод =====================
    public void key(char c, int key) {
        Win f = top();
        if (f != null) {
            if (f.type == FILES) keyFiles(f, key);
            else if (f.type == NOTE) keyNote(f, c, key);
            else if (f.type == CALC) keyCalc(f, c, key);
            else if (f.type == CHAT) keyChat(f, c, key);
            else if (f.type == NETW) keyNet(f, c, key);
            else if (f.type == PAINT) keyPaint(f, c, key);
            else if (f.type == SHOP) keyShop(f, c, key);
            else if (f.type == BANK) keyBank(f, c, key);
            else if (f.type == BROWSER) keyBrowser(f, c, key);
            else if (f.type == SETUP) keySetup(f, key);
            else if (f.type == DOOM && f.game != null) f.game.key(c, key);
            else if (f.type == AVWIN) keyAv(f, c, key);
        }
        if (l.st != ComputerLogic.St.DESKTOP || l.desk != this) return; // ПК перезапустился (антивирус)
        draw();
        if (f != null && f.type == DOOM && f.game != null) f.game.dirty = false;
    }

    /**
     * Курсор мыши над клеткой экрана (cx, cy); cx < 0 - курсор вне экрана. DOOM по нему поворачивает камеру:
     * без кликов, пока курсор над 3D-видом окна игры.
     */
    public void hover(int cx, int cy) {
        Win w = top();
        if (w == null || w.type != DOOM || w.game == null) return;
        if (cx < 0) w.game.look(-1, -1);
        else w.game.look(cx - (w.x + 1), cy - (w.y + 1));
    }

    public void click(int cx, int cy) {
        if (cy == TB) {
            int avx = avTrayX();
            if (avx >= 0 && cx >= avx && cx < avx + AV_TAG.length()) {
                menu = false;
                if (Antivirus.threats(l.t, true) > 0) openAvAlert();
                else draw();
                return;
            }
            if (cx < 7) {
                menu = !menu;
            } else {
                menu = false;
                int i = (cx - 8) / 7;
                if (cx >= 8 && i < wins.size()) {
                    Win w = wins.get(i);
                    if (w == top()) w.min = true;
                    else raise(w);
                }
            }
            draw();
            return;
        }
        if (menu && pixel) {
            int rows = pRows(), y0 = TB - 2 * rows;
            menu = false;
            if (cx < 2 * PMW && cy >= y0 && cy < TB) {
                int idx = (cx / PMW) * rows + (cy - y0) / 2;
                if (idx < pMenu().length) menuAction(pMenuAct()[idx]);
            }
            if (l.st == ComputerLogic.St.DESKTOP && l.desk == this) draw();
            return;
        }
        if (menu) {
            int mlen = (doomOn() ? MENU_D : MENU).length;
            int y0 = TB - mlen;
            menu = false;
            if (cx < 16 && cy >= y0 && cy < TB) menuAction((doomOn() ? MENU_ACT_D : MENU_ACT)[cy - y0]);
            if (l.st == ComputerLogic.St.DESKTOP && l.desk == this) draw();
            return;
        }
        for (int i = wins.size() - 1; i >= 0; i--) {
            Win w = wins.get(i);
            if (w.min) continue;
            if (cx >= w.x && cx < w.x + w.w && cy >= w.y && cy < w.y + w.h) {
                raise(w);
                if (cy == w.y) {
                    if (cx >= w.x + w.w - 4 && cx <= w.x + w.w - 2) close(w);
                    else if (cx >= w.x + w.w - 7 && cx <= w.x + w.w - 5) w.min = true;
                } else if (w.type == FILES) {
                    clickFiles(w, cx, cy);
                } else if (w.type == NOTE) {
                    clickNote(w, cx, cy);
                } else if (w.type == CALC) {
                    clickCalc(w, cx, cy);
                } else if (w.type == CHAT) {
                    clickChat(w, cx, cy);
                } else if (w.type == NETW) {
                    clickNet(w, cx, cy);
                } else if (w.type == PAINT) {
                    clickPaint(w, cx, cy);
                } else if (w.type == SHOP) {
                    clickShop(w, cx, cy);
                } else if (w.type == BANK) {
                    clickBank(w, cx, cy);
                } else if (w.type == BROWSER) {
                    clickBrowser(w, cx, cy);
                } else if (w.type == SETUP) {
                    clickSetup(w, cx, cy);
                } else if (w.type == DOOM && w.game != null) {
                    w.game.click(cx - (w.x + 1), cy - (w.y + 1));
                    w.game.dirty = false;
                } else if (w.type == AVWIN) {
                    if (clickAv(w, cx, cy)) return; // ПК перезапускается: рабочий стол больше не рисуем
                }
                draw();
                return;
            }
        }
        if (pixel) {
            int[] pacts = doomOn() ? PICON_ACT_D : PICON_ACT;
            for (int i = 0; i < pacts.length; i++) {
                int ix = 1 + 7 * (i / 4), iy = 1 + 3 * (i % 4);
                if (cx >= ix && cx <= ix + 6 && cy >= iy && cy <= iy + 2) {
                    menuAction(pacts[i]);
                    if (l.st == ComputerLogic.St.DESKTOP && l.desk == this) draw();
                    return;
                }
            }
            draw();
            return;
        }
        int[] cacts = doomOn() ? ICON_ACT_D : ICON_ACT;
        for (int i = 0; i < cacts.length; i++) {
            int ix = 2 + 9 * (i / 5), y = 1 + 3 * (i % 5);
            if (cx >= ix && cx <= ix + 7 && cy >= y && cy <= y + 1) {
                menuAction(cacts[i]);
                if (l.st == ComputerLogic.St.DESKTOP && l.desk == this) draw();
                return;
            }
        }
        draw();
    }

    // ===================== Shop и Bank (интернет-сервисы) =====================
    static final int SHOP_ROWS = 10;
    /** Сколько консов наличными было у игрока при последней отрисовке (-1 = неизвестно). */
    long cashSeen = -1;

    static String mmss(int sec) {
        int m = sec / 60, s2 = sec % 60;
        return m + ":" + (s2 < 10 ? "0" : "") + s2;
    }

    /** Нужны сеть и хотя бы один сервер: Shop и Bank работают через сервер сети. */
    boolean netOk() {
        return l.link().up() && l.chat.server() != null;
    }

    /** Рисует подсказку и возвращает false, если сервисы недоступны. */
    boolean servicesReady(int ix, int iy, int iw) {
        NetLink k = l.link();
        if (!k.up()) {
            txt(ix + 1, iy + 1, "No network connection.", WHITE);
            txt(ix + 1, iy + 3, cut(k.status, iw - 2), WHITE);
            txt(ix + 1, iy + 5, "Connect a cable or Wi-Fi first.", WHITE);
            return false;
        }
        if (l.chat.server() == null) {
            txt(ix + 1, iy + 1, "No server on this network.", WHITE);
            txt(ix + 1, iy + 3, "Place a Server block next to a cable", WHITE);
            txt(ix + 1, iy + 4, "or router of this network.", WHITE);
            return false;
        }
        return true;
    }

    /** Подпись для перерисовки: окно магазина/банка обновляется по секундам и при смене баланса. */
    String shopSig() {
        boolean on = false;
        for (Win w : wins) {
            if ((w.type == SHOP || w.type == BANK) && !w.min) on = true;
        }
        if (!on) return "";
        Bank b = Bank.get();
        String bal = b == null ? "-" : b.balance(l.actorId) + "/" + b.history(l.actorId).size();
        return Shop.stamp() + ":" + Shop.secondsLeft() + ":" + bal + ":" + l.actorId.length();
    }

    String account() {
        return l.actorName.length() == 0 ? "(press any key)" : l.actorName;
    }

    // ---------- Shop ----------
    void drawShop(Win w, boolean active) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2;
        if (!servicesReady(ix, iy, iw)) return;
        Bank b = Bank.get();
        if (b == null) {
            txt(ix + 1, iy + 1, "Bank is unavailable.", WHITE);
            return;
        }
        List<Shop.Offer> cat = Shop.catalog();
        long stamp = Shop.stamp();
        if (w.epoch != stamp) {
            if (w.epoch >= 0) {
                w.msg = "The catalog has changed!";
                w.msgOk = true;
            }
            w.epoch = stamp;
            w.sel = -1;
        }
        if (w.sel >= cat.size()) w.sel = cat.size() - 1;
        String id = l.actorId;
        TileServer srv = l.chat.server();
        fill(ix, iy, iw, 1, GRAY);
        txt(ix + 1, iy, cut("Shop @ " + (srv == null ? "-" : srv.domain), 30), GRAY);
        String nx = "New catalog in " + mmss(Shop.secondsLeft());
        txt(ix + iw - nx.length() - 1, iy, nx, GRAY);
        txt(ix + 1, iy + 1, cut("Balance: " + Bank.fmt(b.balance(id)) + "    " + account(), iw - 2), WHITE);
        fill(ix, iy + 2, iw, 1, GRAY);
        txt(ix + 1, iy + 2, padR("  Item", 26) + padL("Price", 7) + padL("You", 8) + padL("Stock", 8), GRAY);
        if (cat.isEmpty()) txt(ix + 1, iy + 4, "The catalog is empty (see config/pcmod_shop.cfg).", WHITE);
        for (int r = 0; r < SHOP_ROWS && r < cat.size(); r++) {
            Shop.Offer o = cat.get(r);
            int pl = Shop.playerLeft(b, id, o), st = Shop.stockLeft(b, o);
            boolean out = pl <= 0 || st <= 0;
            String you = pl == Integer.MAX_VALUE ? "-" : pl + "/" + o.limit;
            String stock = st == Integer.MAX_VALUE ? "inf" : String.valueOf(st);
            String row = padR((r == w.sel ? "> " : "  ") + o.name, 26) + padL(o.price + " K", 7) + padL(you, 8) + padL(stock, 8);
            txt(ix + 1, iy + 3 + r, padR(row, iw - 2), r == w.sel ? CYAN : (out ? GRAY : WHITE));
        }
        String det = "Select an item: Up/Down or click.";
        if (w.sel >= 0 && w.sel < cat.size()) {
            Shop.Offer o = cat.get(w.sel);
            det = o.price + " K each. Limit " + (o.limit == 0 ? "none" : o.limit + " per player")
                    + ", stock " + (o.stock == 0 ? "unlimited" : String.valueOf(o.stock));
        }
        txt(ix + 1, iy + 13, padR(det, iw - 2), WHITE);
        if (w.msg.length() > 0) txt(ix + 1, iy + 14, padR(w.msg, iw - 2), w.msgOk ? GREEN : RED);
        txt(ix + 1, iy + 15, " Buy 1 ", GREEN);
        txt(ix + 9, iy + 15, " Buy 5 ", GREEN);
        txt(ix + 17, iy + 15, " Buy max ", YELLOW);
        txt(ix + 28, iy + 15, "Enter=1  5  M=max", WHITE);
        fill(ix, iy + 16, iw, 1, GRAY);
        txt(ix + 1, iy + 16, cut("Pay with kons: top up in the Bank app.", iw - 2), GRAY);
    }

    void shopBuy(Win w, int qty) {
        w.msgOk = false;
        if (l.actor == null) return;
        if (!netOk()) {
            w.msg = "No connection to the server";
            return;
        }
        if (w.sel < 0) {
            w.msg = "Select an item first";
            return;
        }
        Bank.Res r = Shop.buy(l.actor, w.sel, qty, w.epoch);
        w.msg = r.msg;
        w.msgOk = r.ok;
    }

    void clickShop(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1;
        w.msg = "";
        if (!netOk()) return;
        int r = cy - (iy + 3);
        if (r >= 0 && r < SHOP_ROWS) {
            if (r < Shop.catalog().size()) w.sel = r;
            return;
        }
        if (cy == iy + 15) {
            if (cx >= ix + 1 && cx <= ix + 7) shopBuy(w, 1);
            else if (cx >= ix + 9 && cx <= ix + 15) shopBuy(w, 5);
            else if (cx >= ix + 17 && cx <= ix + 25) shopBuy(w, 0);
        }
    }

    void keyShop(Win w, char c, int key) {
        w.msg = "";
        if (!netOk()) return;
        int n = Shop.catalog().size();
        if (key == ComputerLogic.K_UP) {
            if (w.sel > 0) w.sel--;
            else if (w.sel < 0 && n > 0) w.sel = 0;
        } else if (key == ComputerLogic.K_DOWN) {
            if (w.sel < n - 1) w.sel++;
        } else if (key == ComputerLogic.K_ENTER || key == ComputerLogic.K_NENTER || c == '1') {
            shopBuy(w, 1);
        } else if (c == '5') {
            shopBuy(w, 5);
        } else if (c == 'm' || c == 'M') {
            shopBuy(w, 0);
        }
    }

    // ---------- Bank ----------
    void drawBank(Win w, boolean active) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2;
        if (!servicesReady(ix, iy, iw)) return;
        Bank b = Bank.get();
        if (b == null) {
            txt(ix + 1, iy + 1, "Bank is unavailable.", WHITE);
            return;
        }
        if (l.actor != null) cashSeen = Bank.cash(l.actor);
        String id = l.actorId;
        TileServer srv = l.chat.server();
        fill(ix, iy, iw, 1, GRAY);
        txt(ix + 1, iy, cut("Bank @ " + (srv == null ? "-" : srv.domain), iw - 2), GRAY);
        txt(ix + 1, iy + 2, cut("Account : " + account(), iw - 2), WHITE);
        txt(ix + 1, iy + 3, cut("Balance : " + Bank.fmt(b.balance(id)), iw - 2), WHITE);
        txt(ix + 1, iy + 4, cut("Cash    : " + (cashSeen < 0 ? "?" : Bank.fmt(cashSeen)) + " in inventory", iw - 2), WHITE);
        txt(ix + 1, iy + 6, "Amount  :", WHITE);
        String am = w.fIn.length() == 0 ? "" : w.fIn;
        txt(ix + 11, iy + 6, padR(am, 12), CYAN);
        txt(ix + 1, iy + 7, cut("Keys: D deposit, W withdraw, A all", iw - 2), WHITE);
        txt(ix + 2, iy + 8, " Deposit ", GREEN);
        txt(ix + 13, iy + 8, " Withdraw ", YELLOW);
        txt(ix + 25, iy + 8, " Deposit all ", GREEN);
        if (w.msg.length() > 0) txt(ix + 1, iy + 10, padR(w.msg, iw - 2), w.msgOk ? GREEN : RED);
        fill(ix, iy + 11, iw, 1, GRAY);
        txt(ix + 1, iy + 11, "History", GRAY);
        List<String> h = b.history(id);
        for (int r = 0; r < 3; r++) {
            int idx = h.size() - 1 - r;
            if (idx < 0) break;
            txt(ix + 1, iy + 12 + r, cut(h.get(idx), iw - 2), WHITE);
        }
        if (active) {
            s.cx = ix + 11 + Math.min(11, am.length());
            s.cy = iy + 6;
            caret = true;
        }
    }

    /** op: 0 внести сумму, 1 снять, 2 внести всё. */
    void bankDo(Win w, int op) {
        w.msgOk = false;
        if (l.actor == null) return;
        if (!netOk()) {
            w.msg = "No connection to the server";
            return;
        }
        Bank b = Bank.get();
        if (b == null) {
            w.msg = "Bank is unavailable";
            return;
        }
        long amt = 0;
        try {
            if (w.fIn.length() > 0) amt = Long.parseLong(w.fIn);
        } catch (NumberFormatException e) {
            amt = 0;
        }
        Bank.Res r = op == 1 ? b.withdraw(l.actor, amt) : b.deposit(l.actor, op == 2 ? -1L : amt);
        w.msg = r.msg;
        w.msgOk = r.ok;
        if (r.ok) w.fIn = "";
    }

    void clickBank(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1;
        w.msg = "";
        if (cy == iy + 8) {
            if (cx >= ix + 2 && cx <= ix + 10) bankDo(w, 0);
            else if (cx >= ix + 13 && cx <= ix + 22) bankDo(w, 1);
            else if (cx >= ix + 25 && cx <= ix + 37) bankDo(w, 2);
        }
    }

    void keyBank(Win w, char c, int key) {
        w.msg = "";
        if (key == ComputerLogic.K_ENTER || key == ComputerLogic.K_NENTER) {
            bankDo(w, w.fIn.length() == 0 ? 2 : 0);
        } else if (key == ComputerLogic.K_BACK) {
            if (w.fIn.length() > 0) w.fIn = w.fIn.substring(0, w.fIn.length() - 1);
        } else if (c >= '0' && c <= '9') {
            if (w.fIn.length() < 9) w.fIn = w.fIn + c;
        } else if (c == 'd' || c == 'D') {
            bankDo(w, 0);
        } else if (c == 'w' || c == 'W') {
            bankDo(w, 1);
        } else if (c == 'a' || c == 'A') {
            bankDo(w, 2);
        }
    }

    // ===================== PixelOS: фон, иконки, меню =====================
    /** Тёмный рабочий стол с пиксельной сеткой и подписанными значками. */
    void drawPixelBg() {
        for (int y = 0; y < TB; y++) {
            for (int x = 0; x < W; x++) {
                if (x % 6 == 3 && y % 3 == 1) s.put(x, y, '.', PBG);
            }
        }
        String[] pnames = doomOn() ? PNAME_D : PNAME;
        for (int i = 0; i < pnames.length; i++) {
            int ix = 1 + 7 * (i / 4), iy = 1 + 3 * (i % 4);
            String nm = pnames[i];
            s.text(ix + (7 - nm.length()) / 2, iy + 2, nm, WHITE);
        }
    }

    /** Закрыт ли прямоугольник (в клетках) окном или меню: иконка поверх них смотрелась бы неправильно. */
    boolean covered(int x, int y, int w, int h) {
        for (Win k : wins) {
            if (k.min) continue;
            if (x < k.x + k.w && x + w > k.x && y < k.y + k.h && y + h > k.y) return true;
        }
        return menu && x < 2 * PMW && y + h > TB - 2 * pRows();
    }

    void drawPixelMenu() {
        int rows = pRows(), y0 = TB - 2 * rows;
        String[] pm = pMenu();
        fill(0, y0, 2 * PMW, 2 * rows, WHITE);
        fill(0, y0, 1, 2 * rows, PTITLE);
        fill(PMW, y0, 1, 2 * rows, GRAY);
        for (int i = 0; i < pm.length; i++) {
            txt((i / rows) * PMW + 5, y0 + 2 * (i % rows), padR(pm[i], 11), WHITE);
        }
    }

    /** Пиксельные иконки кладутся поверх текста, поэтому ставим их в самом конце отрисовки. */
    void drawPixelSprites() {
        int[] pspr = doomOn() ? PICON_SPR_D : PICON_SPR;
        for (int i = 0; i < pspr.length; i++) {
            int ix = 1 + 7 * (i / 4) + 2, iy = 1 + 3 * (i % 4);
            if (!covered(ix, iy, 3, 2)) s.sprite(ix, iy, pspr[i]);
        }
        s.sprite(0, TB, Sprites.mini(Sprites.LOGO));
        for (int i = 0; i < wins.size() && i < MAXWIN; i++) {
            s.sprite(8 + 7 * i, TB, Sprites.mini(TYPE_SPR[wins.get(i).type]));
        }
        if (menu) {
            int rows = pRows(), y0 = TB - 2 * rows;
            int[] ms = doomOn() ? PMENU_SPR_D : PMENU_SPR;
            for (int i = 0; i < ms.length; i++) {
                s.sprite((i / rows) * PMW + 1, y0 + 2 * (i % rows), ms[i]);
            }
        }
    }

    // ===================== Paint (PixelOS) =====================
    String paintPath() {
        return "/home/" + l.user + "/art.px";
    }

    void paintLoad(Win w) {
        Arrays.fill(w.art, 0);
        CompoundTag r = l.root();
        String txt = r == null ? null : VFS.read(r, paintPath());
        if (txt == null) {
            w.amsg = "New picture";
            return;
        }
        String[] rows = txt.split("\n");
        for (int y = 0; y < PH && y < rows.length; y++) {
            for (int x = 0; x < PW && x < rows[y].length(); x++) {
                int v = Character.digit(rows[y].charAt(x), 16);
                if (v >= 0) w.art[y * PW + x] = v;
            }
        }
        w.amsg = "Loaded";
    }

    void paintSave(Win w) {
        CompoundTag r = l.root();
        PartSpec sto = l.t.spec(TileComputer.STORAGE);
        if (r == null || sto == null) {
            w.amsg = "No drive";
            return;
        }
        StringBuilder b = new StringBuilder();
        for (int y = 0; y < PH; y++) {
            for (int x = 0; x < PW; x++) b.append(Character.forDigit(w.art[y * PW + x], 16));
            b.append('\n');
        }
        w.amsg = VFS.write(r, paintPath(), b.toString(), sto.storageKB) ? "Saved" : "No space";
        l.t.setChanged();
    }

    void drawPaint(Win w) {
        int ix = w.x + 2, y0 = w.y + 1;
        for (int y = 0; y < PH; y++) {
            for (int x = 0; x < PW; x++) fill(ix + 2 * x, y0 + y, 2, 1, w.art[y * PW + x]);
        }
        for (int c = 0; c < 16; c++) fill(ix + 2 * c, w.y + 13, 2, 1, c);
        txt(ix, w.y + 14, "Color", WHITE);
        txt(ix + 5, w.y + 14, "[", WHITE);
        fill(ix + 6, w.y + 14, 4, 1, w.pcol);
        txt(ix + 10, w.y + 14, "]", WHITE);
        txt(ix + 12, w.y + 14, cut(w.amsg, 20), WHITE);
        txt(ix, w.y + 15, " Clear ", GRAY);
        txt(ix + 8, w.y + 15, " Save ", GRAY);
        txt(ix + 15, w.y + 15, " Load ", GRAY);
        txt(ix + 24, w.y + 15, "Keys 0-F", WHITE);
    }

    void clickPaint(Win w, int cx, int cy) {
        int ix = w.x + 2, y0 = w.y + 1;
        if (cy >= y0 && cy < y0 + PH && cx >= ix && cx < ix + 2 * PW) {
            w.art[(cy - y0) * PW + (cx - ix) / 2] = w.pcol;
            w.amsg = "";
        } else if (cy == w.y + 13 && cx >= ix && cx < ix + 32) {
            w.pcol = (cx - ix) / 2;
        } else if (cy == w.y + 15) {
            if (cx >= ix && cx < ix + 7) {
                Arrays.fill(w.art, 0);
                w.amsg = "Cleared";
            } else if (cx >= ix + 8 && cx < ix + 14) {
                paintSave(w);
            } else if (cx >= ix + 15 && cx < ix + 21) {
                paintLoad(w);
            }
        }
    }

    /** Клавиши 0-9 и A-F выбирают цвет кисти. */
    void keyPaint(Win w, char c, int key) {
        int v = Character.digit(c, 16);
        if (v >= 0) w.pcol = v;
    }

    // ===================== Browser, установщик и DOOM (0.9.0) =====================
    static final String DOOM_DIR = "/games/doom", DOOM_WAD = "/games/doom/doom.wad", SETUP_FILE = "doom_setup.exe";
    static final int SETUP_SIZE = 12 * 1024, WAD_SIZE = 20 * 1024;
    static final String SETUP_MAGIC = "MZ\nDOOM-SETUP 1.0\n";
    int fc;

    boolean doomOn() {
        CompoundTag r = l.root();
        return r != null && VFS.isFile(r, DOOM_WAD);
    }

    int capKB() {
        PartSpec sto = l.t.spec(TileComputer.STORAGE);
        return sto == null ? 0 : sto.storageKB;
    }

    String downloadsDir() {
        return "/home/" + l.user + "/Downloads";
    }

    String[] pMenu() {
        return doomOn() ? PMENU_D : PMENU;
    }

    int[] pMenuAct() {
        return doomOn() ? PMENU_ACT_D : PMENU_ACT;
    }

    /** Строк в каждом из двух столбцов меню PixelOS (минимум 6). */
    int pRows() {
        return Math.max(6, (pMenu().length + 1) / 2);
    }

    /** Если игру удалили (файл doom.wad), её окна закрываются. */
    void dropStaleDoom() {
        if (doomOn()) return;
        for (int i = wins.size() - 1; i >= 0; i--) {
            if (wins.get(i).type == DOOM) wins.remove(i);
        }
    }

    void openDoom() {
        if (!doomOn()) return;
        open(DOOM);
    }

    /** Вызывается каждый серверный тик (20 раз в секунду): загрузки, установка, игровой цикл DOOM. */
    public void fast() {
        fc++;
        boolean redraw = false;
        for (int i = 0; i < wins.size(); i++) {
            Win w = wins.get(i);
            if (w.type == BROWSER) redraw |= dlStep(w);
            else if (w.type == SETUP) redraw |= setupStep(w);
        }
        Win tp = top();
        if (tp != null && tp.type == DOOM && tp.game != null && fc % 2 == 0) {
            tp.game.tick();
            if (tp.game.dirty) {
                tp.game.dirty = false;
                redraw = true;
            }
        }
        if (redraw) draw();
    }

    // ---------- Browser ----------
    static String normUrl(String raw) {
        String u = raw.trim().toLowerCase();
        String[] pre = {"pc://", "http://", "https://"};
        for (String p : pre) {
            if (u.startsWith(p)) u = u.substring(p.length());
        }
        if (u.startsWith("www.")) u = u.substring(4);
        int sl = u.indexOf('/');
        if (sl >= 0) u = u.substring(0, sl);
        if (u.length() == 0) u = "home.pc";
        if (u.indexOf('.') < 0) u = u + ".pc";
        return u;
    }

    static void row(List<String[]> o, String kind, String text, String target) {
        o.add(new String[] {kind, text, target});
    }

    /** Страницы внутриигрового интернета. Строки: вид (h заголовок, t текст, d серый, e ошибка, l ссылка), текст, адрес ссылки. */
    List<String[]> webPage(String url) {
        List<String[]> o = new ArrayList<String[]>();
        TileServer srv = l.chat.server();
        if (url.equals("home.pc")) {
            row(o, "h", "PC-Net - start page", null);
            row(o, "t", "", null);
            row(o, "t", "Welcome to the in-game internet!", null);
            row(o, "d", "Sites on this network:", null);
            row(o, "t", "", null);
            row(o, "l", "GameZone  (games.pc) - free game downloads", "games.pc");
            row(o, "l", "PC-News   (news.pc)  - latest from the network", "news.pc");
            row(o, "l", "SafeGuard (av.pc)    - antivirus download", "av.pc");
            row(o, "l", "Help      (help.pc)  - how to download and install", "help.pc");
            row(o, "t", "", null);
            row(o, "d", "Tip: type an address in the bar (Tab) and press Enter.", null);
        } else if (url.equals("games.pc")) {
            row(o, "h", "GameZone - free downloads", null);
            row(o, "d", "Download games, install them and play on your PC.", null);
            row(o, "t", "", null);
            row(o, "h", "DOOM  (shareware edition, 3 levels)", null);
            row(o, "t", "Demons, a shotgun and a lot of explosive barrels.", null);
            row(o, "t", "Setup: " + SETUP_SIZE / 1024 + " KB.  Installed: " + WAD_SIZE / 1024 + " KB.", null);
            if (doomOn()) {
                row(o, "t", "Already installed: run it from the Start menu.", null);
            } else {
                row(o, "l", "Download " + SETUP_FILE, "dl:doom");
            }
            row(o, "t", "", null);
            row(o, "l", "Back to start page", "home.pc");
        } else if (url.equals("av.pc")) {
            row(o, "h", "SafeGuard Antivirus - free", null);
            row(o, "d", "Finds unknown programs that arrive from other PCs.", null);
            row(o, "t", "", null);
            row(o, "t", "How it works:", null);
            row(o, "t", "* 3 seconds after a foreign virus lands on your PC,", null);
            row(o, "t", "  a warning window appears.", null);
            row(o, "t", "* Remove: the PC restarts and cleans up (up to 11 s).", null);
            row(o, "t", "* Wait: the threat stays; AV icon turns red.", null);
            row(o, "t", "Setup: " + Antivirus.SETUP_SIZE / 1024 + " KB.  Installed: " + Antivirus.INSTALLED_SIZE / 1024 + " KB.", null);
            if (Antivirus.installed(l.root())) {
                row(o, "t", "Already installed: AV icon is in the taskbar.", null);
            } else {
                row(o, "l", "Download " + Antivirus.SETUP_FILE, "dl:av");
            }
            row(o, "t", "", null);
            row(o, "l", "Back to start page", "home.pc");
        } else if (url.equals("news.pc")) {
            row(o, "h", "PC-News", null);
            row(o, "t", "", null);
            row(o, "t", "* GameZone now hosts DOOM. Free. Really.", null);
            row(o, "t", "* Miners report record profits (and hot GPUs).", null);
            row(o, "t", "* Forecast: sunny, with a chance of creepers.", null);
            row(o, "t", "", null);
            row(o, "d", "Your network server: " + (srv == null ? "none" : srv.domain), null);
            row(o, "t", "", null);
            row(o, "l", "Back to start page", "home.pc");
        } else if (url.equals("help.pc")) {
            row(o, "h", "Help - how to get DOOM", null);
            row(o, "t", "1. Open GameZone (games.pc) and press Download.", null);
            row(o, "t", "2. The file lands in ~/Downloads (see Files).", null);
            row(o, "t", "3. In Files, open " + SETUP_FILE + " and press Install.", null);
            row(o, "t", "4. Start DOOM from the Start menu or the desktop.", null);
            row(o, "t", "", null);
            row(o, "d", "Uninstall: delete /games/doom/doom.wad in Files.", null);
            row(o, "d", "Wi-Fi downloads are slower than a cable.", null);
            row(o, "t", "", null);
            row(o, "l", "Back to start page", "home.pc");
        } else {
            row(o, "h", "404 - site not found", null);
            row(o, "e", "Nothing lives at " + cut(url, 30) + " on this network.", null);
            row(o, "t", "", null);
            row(o, "l", "Go to the start page", "home.pc");
        }
        return o;
    }

    List<String[]> offlinePage() {
        NetLink k = l.link();
        List<String[]> o = new ArrayList<String[]>();
        row(o, "h", "No network connection", null);
        row(o, "t", "", null);
        row(o, "e", cut(k.status, 50), null);
        row(o, "t", "", null);
        row(o, "t", "Connect a cable or Wi-Fi first (Network app).", null);
        return o;
    }

    List<String[]> pageOf(Win w) {
        return l.link().up() ? webPage(w.url) : offlinePage();
    }

    void browserGo(Win w, String raw) {
        String u = normUrl(raw);
        w.url = u;
        w.addr = u;
        w.pscroll = 0;
        w.msg = "";
        if (w.hist.isEmpty() || !w.hist.get(w.hist.size() - 1).equals(u)) w.hist.add(u);
        if (w.hist.size() > 20) w.hist.remove(0);
    }

    void browserBack(Win w) {
        if (w.hist.size() < 2) return;
        w.hist.remove(w.hist.size() - 1);
        w.url = w.hist.get(w.hist.size() - 1);
        w.addr = w.url;
        w.pscroll = 0;
        w.msg = "";
    }

    void browserLink(Win w, String target) {
        if (target == null) return;
        if (target.equals("dl:doom")) startDownload(w, 1);
        else if (target.equals("dl:av")) startDownload(w, 2);
        else browserGo(w, target);
    }

    void startDownload(Win w, int kind) {
        w.msgOk = false;
        CompoundTag r = l.root();
        if (r == null) return;
        boolean av = kind == 2;
        String file = av ? Antivirus.SETUP_FILE : SETUP_FILE;
        int size = av ? Antivirus.SETUP_SIZE : SETUP_SIZE;
        if (w.dlTotal > 0) {
            w.msg = "A download is already running";
            return;
        }
        if (av ? Antivirus.installed(r) : doomOn()) {
            w.msg = av ? "Antivirus is already installed" : "DOOM is already installed";
            return;
        }
        if (VFS.isFile(r, downloadsDir() + "/" + file)) {
            w.msg = "Already downloaded: open ~/Downloads in Files";
            return;
        }
        if (VFS.free(r, capKB()) < size + 128) {
            w.msg = "Not enough disk space (need " + size / 1024 + " KB free)";
            return;
        }
        w.dlKind = kind;
        w.dlName = file;
        w.dlTotal = size;
        w.dlDone = 0;
        w.msg = "";
    }

    /** Скорость канала, байт в секунду: кабель быстрее Wi-Fi, у Wi-Fi зависит от уровня сигнала. */
    double linkRate() {
        NetLink k = l.link();
        double fx = VirusEffects.netSpeed(l); // вирус-перехватчик замедляет сеть
        if (k.type == NetLink.WIRED) return 3072 * fx;
        if (k.type == NetLink.WIFI) return (1024 + 512 * Math.max(0, Math.min(4, k.signal))) * fx;
        return 0;
    }

    boolean dlStep(Win w) {
        if (w.dlTotal <= 0) return false;
        if (!l.link().up()) return fc % 20 == 0;
        int before = (int) (w.dlDone * 100 / w.dlTotal);
        w.dlDone += linkRate() / 20.0;
        if (w.dlDone >= w.dlTotal) {
            finishDownload(w);
            return true;
        }
        return (int) (w.dlDone * 100 / w.dlTotal) != before;
    }

    void finishDownload(Win w) {
        w.dlTotal = 0;
        w.dlDone = 0;
        CompoundTag r = l.root();
        if (r == null) return;
        String dir = downloadsDir();
        if (!VFS.isDir(r, dir)) VFS.mkdir(r, dir);
        boolean av = w.dlKind == 2;
        String file = av ? Antivirus.SETUP_FILE : SETUP_FILE;
        String magic = av ? Antivirus.SETUP_MAGIC : SETUP_MAGIC;
        int size = av ? Antivirus.SETUP_SIZE : SETUP_SIZE;
        boolean ok = VFS.isDir(r, dir)
                && VFS.writeSized(r, dir + "/" + file, magic, size - magic.length(), capKB());
        w.msgOk = ok;
        w.msg = ok ? "Saved ~/Downloads/" + file + " - open it in Files" : "Disk full: the download failed";
        l.t.setChanged();
    }

    void drawBrowser(Win w, boolean active) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        int rows = ih - 4;
        fill(ix, iy, iw, 1, GRAY);
        txt(ix + 1, iy, " < ", w.hist.size() > 1 ? WHITE : GRAY);
        txt(ix + 5, iy, " Home ", GREEN);
        int fx = ix + 12, fw = iw - 18;
        txt(fx, iy, padR(w.addr, fw), w.focus == 0 ? CYAN : WHITE);
        txt(ix + iw - 5, iy, " Go ", YELLOW);
        List<String[]> pg = pageOf(w);
        fill(ix, iy + 1, iw, 1, GRAY);
        String title = pg.isEmpty() ? "" : pg.get(0)[1];
        txt(ix + 1, iy + 1, cut("pc://" + w.url + "  -  " + title, iw - 2), GRAY);
        int maxScroll = Math.max(0, pg.size() - rows);
        if (w.pscroll > maxScroll) w.pscroll = maxScroll;
        if (w.pscroll < 0) w.pscroll = 0;
        int n = 0;
        for (int i = 0; i < w.pscroll && i < pg.size(); i++) {
            if (pg.get(i)[0].equals("l")) n++;
        }
        for (int r = 0; r < rows; r++) {
            int idx = w.pscroll + r;
            if (idx >= pg.size()) break;
            String[] ln = pg.get(idx);
            String k = ln[0];
            String t;
            int col = WHITE;
            if (k.equals("l")) {
                n++;
                t = " " + n + ". " + ln[1];
                col = CYAN;
            } else if (k.equals("h")) {
                t = " " + ln[1];
                col = YELLOW;
            } else if (k.equals("d")) {
                t = " " + ln[1];
                col = GRAY;
            } else if (k.equals("e")) {
                t = " " + ln[1];
                col = RED;
            } else {
                t = " " + ln[1];
            }
            txt(ix, iy + 2 + r, padR(t, iw - 3), col);
        }
        txt(ix + iw - 2, iy + 2, "^", GRAY);
        txt(ix + iw - 2, iy + 1 + rows, "v", GRAY);
        int sy = iy + ih - 2;
        if (w.dlTotal > 0) {
            int pct = (int) (w.dlDone * 100 / w.dlTotal);
            int bw = 20, fl = pct * bw / 100;
            fill(ix, sy, iw, 1, WHITE);
            txt(ix + 1, sy, cut(w.dlName, 15), WHITE);
            fill(ix + 17, sy, bw, 1, GRAY);
            fill(ix + 17, sy, fl, 1, GREEN);
            String info = l.link().up() ? pct + "%  " + (int) (linkRate() / 1024 * 10) / 10.0 + " KB/s" : "waiting for network...";
            txt(ix + 17 + bw + 1, sy, cut(info, iw - 17 - bw - 2), WHITE);
        } else if (w.msg.length() > 0) {
            txt(ix + 1, sy, padR(w.msg, iw - 2), w.msgOk ? GREEN : RED);
        } else {
            txt(ix + 1, sy, padR("Online: " + l.link().describe(), iw - 2), GRAY);
        }
        fill(ix, iy + ih - 1, iw, 1, GRAY);
        txt(ix + 1, iy + ih - 1, cut("Tab: address bar  1-9 / click: open link  Up/Down", iw - 2), GRAY);
        if (active && w.focus == 0) {
            s.cx = fx + Math.min(fw - 1, w.addr.length());
            s.cy = iy;
            caret = true;
        }
    }

    void keyBrowser(Win w, char c, int key) {
        boolean enter = key == ComputerLogic.K_ENTER || key == ComputerLogic.K_NENTER;
        w.msg = w.dlTotal > 0 ? w.msg : "";
        if (key == 15) {
            w.focus = 1 - w.focus;
            return;
        }
        if (w.focus == 0) {
            if (enter) {
                browserGo(w, w.addr);
                w.focus = 1;
            } else if (key == ComputerLogic.K_BACK) {
                if (w.addr.length() > 0) w.addr = w.addr.substring(0, w.addr.length() - 1);
            } else if (c >= 32 && c < 127 && w.addr.length() < 40) {
                w.addr = w.addr + c;
            }
            return;
        }
        if (key == ComputerLogic.K_UP) w.pscroll--;
        else if (key == ComputerLogic.K_DOWN) w.pscroll++;
        else if (key == 201) w.pscroll -= 5;
        else if (key == 209) w.pscroll += 5;
        else if (key == ComputerLogic.K_BACK) browserBack(w);
        else if (c >= '1' && c <= '9') {
            int want = c - '0', n = 0;
            for (String[] ln : pageOf(w)) {
                if (ln[0].equals("l") && ++n == want) {
                    browserLink(w, ln[2]);
                    return;
                }
            }
        }
    }

    void clickBrowser(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2, ih = w.h - 2;
        int rows = ih - 4;
        if (w.dlTotal <= 0) w.msg = "";
        if (cy == iy) {
            int fx = ix + 12, fw = iw - 18;
            if (cx >= ix + 1 && cx <= ix + 3) browserBack(w);
            else if (cx >= ix + 5 && cx <= ix + 10) browserGo(w, "home.pc");
            else if (cx >= fx && cx < fx + fw) w.focus = 0;
            else if (cx >= ix + iw - 5 && cx <= ix + iw - 2) {
                browserGo(w, w.addr);
                w.focus = 1;
            }
            return;
        }
        w.focus = 1;
        if (cx == ix + iw - 2) {
            if (cy == iy + 2) w.pscroll -= 3;
            else if (cy == iy + 1 + rows) w.pscroll += 3;
            return;
        }
        int r = cy - (iy + 2);
        if (r >= 0 && r < rows) {
            List<String[]> pg = pageOf(w);
            int idx = w.pscroll + r;
            if (idx >= 0 && idx < pg.size() && pg.get(idx)[0].equals("l")) browserLink(w, pg.get(idx)[2]);
        }
    }

    // ---------- Окно антивируса ----------
    static final String AV_BTN_REMOVE = " [1] Удалить программу ", AV_BTN_WAIT = " [2] Ждать ";

    /** Показать окно антивируса поверх остальных (место освобождаем, если окон уже максимум). */
    void openAvAlert() {
        menu = false;
        boolean have = false;
        for (Win e : wins) if (e.type == AVWIN) have = true;
        if (!have && wins.size() >= MAXWIN) {
            for (Win e : wins) {
                if (e.type != DOOM) {
                    close(e);
                    break;
                }
            }
            if (wins.size() >= MAXWIN) close(wins.get(0));
        }
        Win w = open(AVWIN);
        if (w != null) raise(w);
        if (l.st == ComputerLogic.St.DESKTOP && l.desk == this) draw();
    }

    void drawAv(Win w) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2;
        int n = Antivirus.threats(l.t, true);
        fill(ix, iy, iw, 1, RED);
        txt(ix + 1, iy, padR(" ВНИМАНИЕ: найдена угроза", iw - 2), WHITE);
        txt(ix + 2, iy + 2, "Неизвестная программа была найдена", RED);
        txt(ix + 2, iy + 3, "у вас в компьютере.", RED);
        txt(ix + 2, iy + 5, "Найдено угроз: " + n, GRAY);
        txt(ix + 2, iy + 7, AV_BTN_REMOVE, GREEN);
        txt(ix + 2 + AV_BTN_REMOVE.length() + 1, iy + 7, AV_BTN_WAIT, YELLOW);
        txt(ix + 2, iy + 9, "Удаление перезапустит ПК (до 11 сек).", GRAY);
    }

    /** true - выбрано удаление и ПК перезапускается (рисовать рабочий стол уже нельзя). */
    boolean clickAv(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1;
        if (cy != iy + 7) return false;
        int rx = ix + 2, wx = ix + 2 + AV_BTN_REMOVE.length() + 1;
        if (cx >= rx && cx < rx + AV_BTN_REMOVE.length()) {
            wins.remove(w);
            Antivirus.actionRemove(l);
            return true;
        }
        if (cx >= wx && cx < wx + AV_BTN_WAIT.length()) close(w);
        return false;
    }

    void keyAv(Win w, char c, int key) {
        if (c == '1') {
            wins.remove(w);
            Antivirus.actionRemove(l);
        } else if (c == '2') {
            close(w);
        }
    }

    // ---------- Установщик (открывается из Files по .exe) ----------
    /** Что за установщик лежит по пути: 0 = не установщик, 1 = DOOM, 2 = антивирус. */
    int setupKind(String path) {
        CompoundTag r = l.root();
        if (r == null || !path.endsWith(".exe")) return 0;
        String c = VFS.read(r, path);
        if (c == null) return 0;
        if (c.startsWith("MZ\nDOOM-SETUP")) return 1;
        if (c.startsWith("MZ\nAV-SETUP")) return 2;
        return 0;
    }

    boolean isSetupFile(String path) {
        return setupKind(path) != 0;
    }

    void openSetup(String path) {
        Win w = open(SETUP);
        if (w == null) return;
        if (w.stage != 1) w.skind = setupKind(path) == 2 ? 2 : 1;
        w.spath = path;
        if (w.stage != 1) {
            w.stage = 0;
            w.prog = 0;
            w.msg = "";
        }
    }

    /** Установка идёт быстрее на быстром накопителе (HDD x1, SSD x3, NVMe x6). */
    double setupSeconds() {
        PartSpec sto = l.t.spec(TileComputer.STORAGE);
        int sp = sto == null ? 1 : Math.max(1, sto.speed);
        return 9.0 / sp;
    }

    boolean setupStep(Win w) {
        if (w.stage != 1) return false;
        int before = (int) (w.prog * 100);
        w.prog += 1.0 / (20.0 * setupSeconds());
        if (w.prog >= 1) {
            w.prog = 1;
            doInstall(w);
            return true;
        }
        return (int) (w.prog * 100) != before;
    }

    int needBytes() {
        return WAD_SIZE + "IWAD DOOM1\n".length() + 16 * 3;
    }

    int needBytes(Win w) {
        return w.skind == 2 ? Antivirus.INSTALLED_SIZE + 16 * 4 : needBytes();
    }

    boolean installedFor(Win w) {
        return w.skind == 2 ? Antivirus.installed(l.root()) : doomOn();
    }

    void doInstall(Win w) {
        CompoundTag r = l.root();
        w.stage = 3;
        if (r == null) {
            w.msg = "No drive";
            return;
        }
        if (installedFor(w)) {
            w.stage = 2;
            return;
        }
        int inst = VFS.sizeOf(r, w.spath);
        int avail = VFS.free(r, capKB()) + (inst < 0 ? 0 : inst + 16);
        if (inst < 0) {
            w.msg = "Installer file not found";
            return;
        }
        if (avail < needBytes(w)) {
            w.msg = "Not enough disk space: need " + (needBytes(w) + 1023) / 1024 + " KB";
            return;
        }
        if (w.skind == 2) {
            VFS.remove(r, w.spath);
            if (!VFS.isDir(r, "/programs")) VFS.mkdir(r, "/programs");
            if (!VFS.isDir(r, Antivirus.DIR)) VFS.mkdir(r, Antivirus.DIR);
            boolean okAv = VFS.writeSized(r, Antivirus.FILE, Antivirus.CORE_TEXT,
                    Antivirus.INSTALLED_SIZE - Antivirus.CORE_TEXT.length(), capKB());
            l.t.setChanged();
            if (okAv) {
                w.stage = 2;
                Antivirus.onInstalled(l);
            } else {
                w.msg = "Installation failed: disk full";
            }
            return;
        }
        VFS.remove(r, w.spath);
        if (!VFS.isDir(r, "/games")) VFS.mkdir(r, "/games");
        if (!VFS.isDir(r, DOOM_DIR)) VFS.mkdir(r, DOOM_DIR);
        boolean ok = VFS.writeSized(r, DOOM_WAD, "IWAD DOOM1\n", WAD_SIZE - "IWAD DOOM1\n".length(), capKB());
        l.t.setChanged();
        if (ok) {
            w.stage = 2;
        } else {
            w.msg = "Installation failed: disk full";
        }
    }

    void drawSetup(Win w) {
        int ix = w.x + 1, iy = w.y + 1, iw = w.w - 2;
        fill(ix, iy, iw, 1, GRAY);
        boolean av = w.skind == 2;
        txt(ix + 1, iy, av ? "SafeGuard Antivirus setup" : "DOOM - shareware edition setup", GRAY);
        boolean inst = installedFor(w);
        if (w.stage == 0 && inst) {
            txt(ix + 2, iy + 3, (av ? "Antivirus" : "DOOM") + " is already installed on this PC.", WHITE);
            if (av) {
                txt(ix + 2, iy + 8, " Close ", RED);
            } else {
                txt(ix + 2, iy + 8, " Launch ", GREEN);
                txt(ix + 11, iy + 8, " Close ", RED);
            }
        } else if (w.stage == 0 && av) {
            int avail = setupAvailFor(w);
            txt(ix + 2, iy + 2, "Install SafeGuard Antivirus?", WHITE);
            txt(ix + 2, iy + 4, "Folder : C:" + Antivirus.DIR, WHITE);
            txt(ix + 2, iy + 5, "Needs  : " + (needBytes(w) + 1023) / 1024 + " KB     Free: " + Math.max(0, avail) / 1024 + " KB", avail >= needBytes(w) ? WHITE : RED);
            txt(ix + 2, iy + 6, "Protects against viruses from other PCs.", GRAY);
            txt(ix + 2, iy + 8, " Install ", GREEN);
            txt(ix + 12, iy + 8, " Cancel ", RED);
        } else if (w.stage == 0) {
            CompoundTag r = l.root();
            int avail = setupAvailFor(w);
            txt(ix + 2, iy + 2, "Install DOOM on this computer?", WHITE);
            txt(ix + 2, iy + 4, "Folder : C:" + DOOM_DIR, WHITE);
            txt(ix + 2, iy + 5, "Needs  : " + (needBytes() + 1023) / 1024 + " KB     Free: " + Math.max(0, avail) / 1024 + " KB", avail >= needBytes() ? WHITE : RED);
            txt(ix + 2, iy + 6, "Setup deletes the installer afterwards.", GRAY);
            txt(ix + 2, iy + 8, " Install ", GREEN);
            txt(ix + 12, iy + 8, " Cancel ", RED);
        } else if (w.stage == 1) {
            txt(ix + 2, iy + 2, av ? "Installing antivirus..." : "Installing DOOM...", WHITE);
            int bw = iw - 4, fl = (int) (w.prog * bw);
            fill(ix + 2, iy + 4, bw, 1, GRAY);
            fill(ix + 2, iy + 4, fl, 1, GREEN);
            txt(ix + 2, iy + 6, (int) (w.prog * 100) + "%  copying files", WHITE);
            txt(ix + 2, iy + 8, "SSD / NVMe drives install faster.", GRAY);
        } else if (w.stage == 2 && av) {
            txt(ix + 2, iy + 3, "Antivirus installed. Protection is ON.", GREEN);
            txt(ix + 2, iy + 4, "Look for the AV icon in the taskbar.", WHITE);
            txt(ix + 2, iy + 8, " Close ", RED);
        } else if (w.stage == 2) {
            txt(ix + 2, iy + 3, "DOOM was installed successfully!", GREEN);
            txt(ix + 2, iy + 4, "Find it in the Start menu and on the desktop.", WHITE);
            txt(ix + 2, iy + 8, " Launch ", GREEN);
            txt(ix + 11, iy + 8, " Close ", RED);
        } else {
            txt(ix + 2, iy + 3, "Setup failed", RED);
            txt(ix + 2, iy + 4, cut(w.msg, iw - 4), RED);
            txt(ix + 2, iy + 8, " Close ", RED);
        }
    }

    int setupAvailFor(Win w) {
        CompoundTag r = l.root();
        if (r == null) return 0;
        int inst = VFS.sizeOf(r, w.spath);
        return VFS.free(r, capKB()) + (inst < 0 ? 0 : inst + 16);
    }

    void clickSetup(Win w, int cx, int cy) {
        int ix = w.x + 1, iy = w.y + 1;
        if (cy != iy + 8) return;
        boolean left = cx >= ix + 2 && cx <= ix + 9, right = cx >= ix + 11 && cx <= ix + 19;
        if (w.stage == 0 && !installedFor(w)) {
            if (cx >= ix + 2 && cx <= ix + 10) w.stage = 1;
            else if (cx >= ix + 12 && cx <= ix + 19) close(w);
        } else if (w.stage == 3) {
            if (cx >= ix + 2 && cx <= ix + 8) close(w);
        } else if (w.stage != 1) {
            if (w.skind == 2) {
                if (cx >= ix + 2 && cx <= ix + 8) close(w);
            } else if (left) {
                close(w);
                openDoom();
            } else if (right) {
                close(w);
            }
        }
    }

    void keySetup(Win w, int key) {
        if (key != ComputerLogic.K_ENTER && key != ComputerLogic.K_NENTER) return;
        if (w.stage == 0 && !installedFor(w)) {
            w.stage = 1;
            w.prog = 0;
        } else if (w.stage == 3) {
            close(w);
        } else if (w.stage != 1) {
            close(w);
            if (w.skind != 2) openDoom();
        }
    }

    // ---------- DOOM ----------
    void drawDoom(Win w) {
        if (w.game == null) return;
        final int ox = w.x + 1, oy = w.y + 1;
        w.game.draw(new DoomGame.Out() {
            public void put(int x, int y, char c, int attr) {
                s.put(ox + x, oy + y, c, attr);
            }
        });
    }
}
