package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Виртуальная файловая система PCOS. Хранится в NBT предмета-накопителя (ключ "pcos"). */
public class VFS {
    public static final int OS_SIZE = 24 * 1024;
    /** PixelOS занимает на накопителе заметно больше места, чем PCOS. */
    public static final int PIXEL_OS_SIZE = 96 * 1024;

    /** Код установленной ОС: "pixel" или "pcos" (старые диски без поля - PCOS). */
    public static String osId(CompoundTag r) {
        return r != null && "pixel".equals(r.getString("os")) ? "pixel" : "pcos";
    }

    public static String osName(CompoundTag r) {
        return osId(r).equals("pixel") ? "PixelOS" : "PCOS";
    }

    public static int osSize(CompoundTag r) {
        return osId(r).equals("pixel") ? PIXEL_OS_SIZE : OS_SIZE;
    }

    public static CompoundTag root(ItemStack d) {
        if (d == null || d.isEmpty() || !d.hasTag()) return null;
        CompoundTag tg = d.getTag();
        return tg.contains("pcos", 10) ? tg.getCompound("pcos") : null;
    }

    public static boolean hasOS(ItemStack d) {
        CompoundTag r = root(d);
        return r != null && r.getBoolean("installed");
    }

    public static void format(ItemStack d) {
        if (d == null || d.isEmpty()) return;
        CompoundTag tg = d.hasTag() ? d.getTag() : new CompoundTag();
        tg.put("pcos", new CompoundTag());
        d.setTag(tg);
    }

    public static String hash(String p) {
        int h = 7;
        for (int i = 0; i < p.length(); i++) h = h * 31 + p.charAt(i);
        return Integer.toHexString(h ^ 0x5bd1e995);
    }

    public static void install(ItemStack d, String user, String pass, String host) {
        install(d, user, pass, host, "pcos");
    }

    public static void install(ItemStack d, String user, String pass, String host, String os) {
        boolean px = "pixel".equals(os);
        String name = px ? "PixelOS" : "PCOS";
        CompoundTag tg = d.hasTag() ? d.getTag() : new CompoundTag();
        CompoundTag r = new CompoundTag();
        r.putBoolean("installed", true);
        if (px) r.putString("os", "pixel");
        r.putString("user", user);
        r.putString("pass", hash(pass));
        r.putString("host", host);
        r.put("dirs", new ListTag());
        r.put("files", new ListTag());
        tg.put("pcos", r);
        d.setTag(tg);
        mkdirRaw(r, "/");
        mkdirRaw(r, "/home");
        mkdirRaw(r, "/home/" + user);
        mkdirRaw(r, "/etc");
        mkdirRaw(r, "/bin");
        setFile(r, "/etc/hostname", host + "\n");
        setFile(r, "/etc/motd", "Welcome to " + name + " 1.0\n");
        setFile(r, "/home/" + user + "/readme.txt", px
                ? "Welcome to PixelOS!\nOpen Paint on the desktop and draw your own pixel art.\n"
                + "Terminal commands: help, neofetch, lshw, sensors\n"
                : "Welcome to PCOS!\nTry: help, neofetch, lshw, sensors, edit notes.txt\n");
    }

    // ---------- низкоуровневые списки ----------
    private static ListTag list(CompoundTag r, String key) {
        if (!r.contains(key, 9)) r.put(key, new ListTag());
        return r.getList(key, 10);
    }

    private static int find(ListTag l, String path) {
        for (int i = 0; i < l.size(); i++) {
            if (l.getCompound(i).getString("p").equals(path)) return i;
        }
        return -1;
    }

    private static void removeAt(CompoundTag r, String key, int idx) {
        ListTag old = list(r, key);
        ListTag nw = new ListTag();
        for (int i = 0; i < old.size(); i++) {
            if (i != idx) nw.add(old.getCompound(i));
        }
        r.put(key, nw);
    }

    private static void mkdirRaw(CompoundTag r, String path) {
        CompoundTag c = new CompoundTag();
        c.putString("p", path);
        list(r, "dirs").add(c);
    }

    private static void setFile(CompoundTag r, String path, String content) {
        ListTag l = list(r, "files");
        int i = find(l, path);
        if (i >= 0) {
            l.getCompound(i).putString("c", content);
        } else {
            CompoundTag c = new CompoundTag();
            c.putString("p", path);
            c.putString("c", content);
            l.add(c);
        }
    }

    // ---------- пути ----------
    public static String resolve(String cwd, String p) {
        String full = p.startsWith("/") ? p : (cwd.equals("/") ? "/" + p : cwd + "/" + p);
        ArrayList<String> out = new ArrayList<String>();
        for (String part : full.split("/")) {
            if (part.length() == 0 || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
            } else {
                out.add(part);
            }
        }
        if (out.isEmpty()) return "/";
        StringBuilder b = new StringBuilder();
        for (String o : out) b.append('/').append(o);
        return b.toString();
    }

    public static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i <= 0 ? "/" : path.substring(0, i);
    }

    public static String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // ---------- операции ----------
    public static boolean isDir(CompoundTag r, String path) {
        return find(list(r, "dirs"), path) >= 0;
    }

    public static boolean isFile(CompoundTag r, String path) {
        return find(list(r, "files"), path) >= 0;
    }

    public static String read(CompoundTag r, String path) {
        ListTag l = list(r, "files");
        int i = find(l, path);
        return i < 0 ? null : l.getCompound(i).getString("c");
    }

    /** Пути всех файлов на диске. */
    public static List<String> files(CompoundTag r) {
        List<String> out = new ArrayList<String>();
        ListTag f = list(r, "files");
        for (int i = 0; i < f.size(); i++) out.add(f.getCompound(i).getString("p"));
        return out;
    }

    public static int usedBytes(CompoundTag r) {
        int n = 0;
        ListTag f = list(r, "files");
        for (int i = 0; i < f.size(); i++) {
            CompoundTag e = f.getCompound(i);
            n += e.getString("c").length() + 16 + e.getInt("sz");
        }
        n += list(r, "dirs").size() * 16;
        return n;
    }

    public static int free(CompoundTag r, int capKB) {
        return capKB * 1024 - osSize(r) - usedBytes(r);
    }

    public static boolean write(CompoundTag r, String path, String content, int capKB) {
        if (path.equals("/") || isDir(r, path) || !isDir(r, parent(path))) return false;
        int oldLen = 0, extra = 16;
        if (isFile(r, path)) {
            oldLen = read(r, path).length();
            extra = 0;
        }
        if (free(r, capKB) < content.length() - oldLen + extra) return false;
        setFile(r, path, content);
        return true;
    }

    /** «Виртуальный» размер файла: сверх длины текста (так игры занимают место на диске, не раздувая NBT). */
    public static int extra(CompoundTag r, String path) {
        ListTag l = list(r, "files");
        int i = find(l, path);
        return i < 0 ? 0 : l.getCompound(i).getInt("sz");
    }

    /** Полный размер файла в байтах (текст + виртуальная часть), -1 если файла нет. */
    public static int sizeOf(CompoundTag r, String path) {
        String c = read(r, path);
        return c == null ? -1 : c.length() + extra(r, path);
    }

    /** Записывает новый файл, занимающий на диске content.length() + extraBytes байт. */
    public static boolean writeSized(CompoundTag r, String path, String content, int extraBytes, int capKB) {
        if (isFile(r, path)) return false;
        if (!write(r, path, content, capKB)) return false;
        ListTag l = list(r, "files");
        l.getCompound(find(l, path)).putInt("sz", extraBytes);
        if (free(r, capKB) < 0) {
            remove(r, path);
            return false;
        }
        return true;
    }

    public static boolean mkdir(CompoundTag r, String path) {
        if (isDir(r, path) || isFile(r, path) || !isDir(r, parent(path))) return false;
        mkdirRaw(r, path);
        return true;
    }

    /** 0 = ok, 1 = не найдено, 2 = каталог не пуст. */
    public static int remove(CompoundTag r, String path) {
        int fi = find(list(r, "files"), path);
        if (fi >= 0) {
            removeAt(r, "files", fi);
            return 0;
        }
        int di = find(list(r, "dirs"), path);
        if (di < 0) return 1;
        if (!listDir(r, path).isEmpty()) return 2;
        removeAt(r, "dirs", di);
        return 0;
    }

    public static List<String> listDir(CompoundTag r, String dir) {
        TreeSet<String> out = new TreeSet<String>();
        ListTag d = list(r, "dirs");
        for (int i = 0; i < d.size(); i++) {
            String p = d.getCompound(i).getString("p");
            if (!p.equals("/") && parent(p).equals(dir)) out.add(name(p) + "/");
        }
        ListTag f = list(r, "files");
        for (int i = 0; i < f.size(); i++) {
            String p = f.getCompound(i).getString("p");
            if (parent(p).equals(dir)) out.add(name(p));
        }
        return new ArrayList<String>(out);
    }
}
