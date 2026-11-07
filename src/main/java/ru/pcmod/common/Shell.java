package ru.pcmod.common;

import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Команды оболочки PCOS / PixelOS. */
public class Shell {
    private static String abs(ComputerLogic l, String a) {
        if (a.equals("~") || a.startsWith("~/")) a = "/home/" + l.user + a.substring(1);
        return VFS.resolve(l.cwd, a);
    }

    private static void err(Screen s, String m) {
        s.color = 12;
        s.println(m);
        s.color = 7;
    }

    private static String unq(String x) {
        if (x.length() >= 2 && ((x.startsWith("\"") && x.endsWith("\"")) || (x.startsWith("'") && x.endsWith("'")))) {
            return x.substring(1, x.length() - 1);
        }
        return x;
    }

    private static boolean protectedPath(String p, String user) {
        return p.equals("/") || p.equals("/home") || p.equals("/etc") || p.equals("/bin") || p.equals("/home/" + user);
    }

    public static void exec(ComputerLogic l, String line) {
        if (line.length() == 0) return;
        Screen s = l.s;
        TileComputer t = l.t;
        CompoundTag r = l.root();
        PartSpec cpu = t.spec(TileComputer.CPU), mb = t.spec(TileComputer.MB), gpu = t.spec(TileComputer.GPU),
                sto = t.spec(TileComputer.STORAGE), cool = t.spec(TileComputer.COOLER), psu = t.spec(TileComputer.PSU);
        int cap = sto.storageKB;
        String[] p = line.split("\\s+");
        String c = p[0];

        if (c.equals("help")) {
            s.println("Commands:");
            s.println("  ls cd pwd cat echo mkdir touch rm cp mv edit");
            s.println("  clear whoami hostname uname date uptime calc");
            s.println("  free df lshw sensors neofetch");
            s.println("  net wifi ping chat   (network, see 'chat')");
            s.println("  virgen               (virus generator, see 'virgen help')");
            s.println("  pcid                 (this computer's 9-digit ID)");
            s.println("  av                   (antivirus, if installed)");
            s.println("  reboot shutdown desktop exit");
            s.println("echo hi > file   writes a file, >> appends.");
            s.println("edit [-o] file   simple text editor.");
            s.println("desktop [on|off]  graphical desktop (autostart).");
        } else if (c.equals("clear")) {
            s.clear();
        } else if (c.equals("pwd")) {
            s.println(l.cwd);
        } else if (c.equals("whoami")) {
            s.println(l.user);
        } else if (c.equals("hostname")) {
            s.println(l.host);
        } else if (c.equals("uname")) {
            s.println(l.osName() + " 1.0 " + cpu.label + " " + cpu.cores + "-core");
        } else if (c.equals("date")) {
            s.println(l.clock());
        } else if (c.equals("uptime")) {
            long sec = (t.getLevel().getGameTime() - l.bootTime) / 20L;
            s.println("up " + (sec / 60) + " min " + (sec % 60) + " s");
        } else if (c.equals("ls")) {
            String dir = p.length > 1 ? abs(l, p[1]) : l.cwd;
            if (VFS.isFile(r, dir)) {
                s.println(VFS.name(dir));
            } else if (!VFS.isDir(r, dir)) {
                err(s, "ls: " + dir + ": no such file or directory");
            } else {
                List<String> names = VFS.listDir(r, dir);
                StringBuilder b = new StringBuilder();
                for (String n : names) {
                    if (b.length() + n.length() + 2 > Screen.W - 1) {
                        s.println(b.toString());
                        b.setLength(0);
                    }
                    b.append(n).append("  ");
                }
                if (b.length() > 0) s.println(b.toString());
            }
        } else if (c.equals("cd")) {
            String dir = p.length > 1 ? abs(l, p[1]) : "/home/" + l.user;
            if (VFS.isDir(r, dir)) l.cwd = dir;
            else err(s, "cd: " + dir + ": not a directory");
        } else if (c.equals("cat")) {
            if (p.length < 2) {
                err(s, "usage: cat file");
            } else {
                for (int i = 1; i < p.length; i++) {
                    String path = abs(l, p[i]);
                    String txt = VFS.read(r, path);
                    if (txt == null) {
                        err(s, "cat: " + p[i] + ": no such file");
                    } else {
                        if (txt.endsWith("\n")) txt = txt.substring(0, txt.length() - 1);
                        for (String ln : txt.split("\n", -1)) s.println(ln);
                    }
                }
            }
        } else if (c.equals("echo")) {
            String rest = line.length() > 4 ? line.substring(4).trim() : "";
            int ov = rest.indexOf('>');
            if (ov < 0) {
                s.println(unq(rest));
            } else {
                boolean append = rest.startsWith(">>", ov);
                String text = unq(rest.substring(0, ov).trim());
                String target = rest.substring(ov + (append ? 2 : 1)).trim();
                if (target.length() == 0) {
                    err(s, "echo: missing file name");
                } else {
                    String path = abs(l, target);
                    String old = (append && VFS.isFile(r, path)) ? VFS.read(r, path) : "";
                    if (!VFS.write(r, path, old + text + "\n", cap)) err(s, "echo: cannot write (bad path or no space)");
                }
            }
        } else if (c.equals("mkdir")) {
            if (p.length < 2) err(s, "usage: mkdir dir");
            else if (!VFS.mkdir(r, abs(l, p[1]))) err(s, "mkdir: cannot create " + p[1]);
        } else if (c.equals("touch")) {
            if (p.length < 2) {
                err(s, "usage: touch file");
            } else {
                String path = abs(l, p[1]);
                if (!VFS.isFile(r, path) && !VFS.write(r, path, "", cap)) err(s, "touch: cannot create " + p[1]);
            }
        } else if (c.equals("rm")) {
            if (p.length < 2) {
                err(s, "usage: rm path");
            } else {
                String path = abs(l, p[1]);
                if (protectedPath(path, l.user)) {
                    err(s, "rm: permission denied");
                } else {
                    int res = VFS.remove(r, path);
                    if (res == 1) err(s, "rm: " + p[1] + ": no such file");
                    else if (res == 2) err(s, "rm: " + p[1] + ": directory not empty");
                }
            }
        } else if (c.equals("cp") || c.equals("mv")) {
            if (p.length < 3) {
                err(s, "usage: " + c + " src dst");
            } else {
                String src = abs(l, p[1]), dst = abs(l, p[2]);
                if (!VFS.isFile(r, src)) {
                    err(s, c + ": " + p[1] + ": not a file");
                } else {
                    if (VFS.isDir(r, dst)) dst = VFS.resolve(dst, VFS.name(src));
                    if (!VFS.write(r, dst, VFS.read(r, src), cap)) {
                        err(s, c + ": cannot write " + p[2]);
                    } else if (c.equals("mv") && !src.equals(dst)) {
                        VFS.remove(r, src);
                    }
                }
            }
        } else if (c.equals("edit")) {
            boolean ow = p.length > 2 && p[1].equals("-o");
            String name = p.length > 2 ? p[2] : (p.length > 1 ? p[1] : null);
            if (name == null) {
                err(s, "usage: edit [-o] file");
            } else {
                String path = abs(l, name);
                if (VFS.isDir(r, path) || !VFS.isDir(r, VFS.parent(path))) err(s, "edit: bad path");
                else l.beginEdit(path, ow);
            }
        } else if (c.equals("free")) {
            int tot = t.totalRamMB();
            int used = Math.min(tot, 256);
            s.println("        total   used   free");
            s.println("Mem:  " + pad6(tot) + " " + pad6(used) + " " + pad6(tot - used) + "  MB");
        } else if (c.equals("df")) {
            int used = (VFS.osSize(r) + VFS.usedBytes(r) + 1023) / 1024;
            s.println("Filesystem   Size   Used  Avail  (KB)");
            s.println("/dev/sda   " + pad6(cap) + " " + pad6(used) + " " + pad6(Math.max(0, cap - used)));
        } else if (c.equals("lshw")) {
            s.println("CPU     : " + cpu.label + " " + cpu.cores + "c @ " + cpu.ghz + " GHz");
            s.println("Board   : " + mb.label + " " + PartSpec.socketName(mb.socket));
            for (int i = TileComputer.RAM0; i <= TileComputer.RAM3; i++) {
                PartSpec rm = t.spec(i);
                if (rm != null) s.println("RAM" + (i - TileComputer.RAM0 + 1) + "    : " + rm.label);
            }
            s.println("GPU     : " + gpu.label + " " + gpu.vram + " MB");
            s.println("Disk    : " + sto.label + " " + sto.storageKB + " KB");
            s.println("Cooler  : " + (cool == null ? "none" : cool.label));
            s.println("PSU     : " + psu.label);
            int mon = t.monitorType();
            s.println("Screen  : " + (mon < 0 ? "none" : MonitorType.get(mon).describe()));
            PartSpec nic = t.spec(TileComputer.NET);
            s.println("Network : Ethernet" + (nic == null ? "" : ", " + nic.label));
        } else if (c.equals("sensors")) {
            int temp = (int) t.temp;
            s.color = temp >= 85 ? 12 : 10;
            s.println("CPU temp : " + temp + " C (limit 100 C)");
            s.color = 7;
            s.println("Cooler   : " + (cool == null ? "none" : cool.label));
            s.println("Power    : " + t.requiredWatts() + " W / " + psu.watts + " W");
        } else if (c.equals("neofetch")) {
            int tot = t.totalRamMB();
            int used = (VFS.osSize(r) + VFS.usedBytes(r) + 1023) / 1024;
            s.color = 11;
            s.println("   ____   ____     " + l.user + "@" + l.host);
            s.println("  |  _ \\ / ___|    OS    : " + l.osName() + " 1.0");
            s.println("  | |_) | |        Board : " + mb.label);
            s.println("  |  __/| |___     CPU   : " + cpu.label + " (" + cpu.cores + ") @ " + cpu.ghz + " GHz");
            s.println("  |_|    \\____|    GPU   : " + gpu.label);
            s.println("                   RAM   : 256 / " + tot + " MB");
            s.println("                   Disk  : " + used + " / " + cap + " KB");
            s.println("                   Temp  : " + (int) t.temp + " C");
            s.println("                   PC ID : " + t.getComputerId());
            s.color = 7;
        } else if (c.equals("calc")) {
            if (p.length != 4) {
                err(s, "usage: calc a + b   (+ - * / %)");
            } else {
                try {
                    double a = Double.parseDouble(p[1]), b = Double.parseDouble(p[3]), res;
                    char op = p[2].charAt(0);
                    if (op == '+') res = a + b;
                    else if (op == '-') res = a - b;
                    else if (op == '*') res = a * b;
                    else if (op == '/') res = a / b;
                    else if (op == '%') res = a % b;
                    else throw new NumberFormatException();
                    s.println(res == Math.floor(res) && Math.abs(res) < 1e12 ? String.valueOf((long) res) : String.valueOf(res));
                } catch (NumberFormatException e) {
                    err(s, "calc: bad expression");
                }
            }
        } else if (c.equals("desktop") || c.equals("startx") || c.equals("exit")) {
            if (p.length > 1 && p[1].equals("off")) {
                r.putBoolean("nodesktop", true);
                s.println("Desktop autostart disabled.");
            } else if (p.length > 1 && p[1].equals("on")) {
                r.putBoolean("nodesktop", false);
                s.println("Desktop autostart enabled.");
            } else {
                String why = l.desktopReason();
                if (why != null) err(s, "desktop: " + why);
                else l.startDesktop();
            }
        } else if (c.equals("reboot")) {
            s.println("Rebooting...");
            l.startUp();
        } else if (c.equals("shutdown") || c.equals("poweroff")) {
            l.shutDown(null);
        } else if (NetShell.is(c)) {
            NetShell.run(l, line, p);
        } else if (c.equals("av") && Antivirus.installed(r)) {
            String[] args = new String[p.length - 1];
            System.arraycopy(p, 1, args, 0, args.length);
            Antivirus.shell(l, args);
        } else if (c.equals("pcid")) {
            s.println("Computer ID: " + t.getComputerId());
        } else if (c.equals("virgen")) {
            String[] args = new String[p.length - 1];
            System.arraycopy(p, 1, args, 0, args.length);
            VirusGeneratorCommand.execute(l, args);
        } else {
            err(s, c + ": command not found");
        }
    }

    private static String pad6(int n) {
        String x = String.valueOf(n);
        StringBuilder b = new StringBuilder();
        for (int i = x.length(); i < 6; i++) b.append(' ');
        return b.append(x).toString();
    }
}
