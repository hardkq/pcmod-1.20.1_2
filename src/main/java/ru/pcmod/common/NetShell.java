package ru.pcmod.common;

import java.util.List;
import net.minecraft.nbt.CompoundTag;

/** Сетевые команды оболочки PCOS: net, wifi, ping, chat. */
public class NetShell {
    private NetShell() { }

    public static boolean is(String c) {
        return c.equals("net") || c.equals("ifconfig") || c.equals("wifi") || c.equals("ping") || c.equals("chat");
    }

    private static void err(Screen s, String m) {
        s.color = 12;
        s.println(m);
        s.color = 7;
    }

    public static void run(ComputerLogic l, String line, String[] p) {
        String c = p[0];
        if (c.equals("net") || c.equals("ifconfig")) net(l);
        else if (c.equals("wifi")) wifi(l, p);
        else if (c.equals("ping")) ping(l, p);
        else chat(l, line, p);
    }

    // ---------- net ----------
    private static void net(ComputerLogic l) {
        Screen s = l.s;
        NetLink k = l.link();
        s.println("link   : " + k.describe());
        if (k.type == NetLink.WIFI) s.println("signal : " + NetLink.bars(k.signal));
        if (k.up()) {
            s.println("gateway: " + (k.router == null ? "-" : k.router.ssid));
            StringBuilder b = new StringBuilder();
            for (TileServer sv : k.servers) {
                if (b.length() > 0) b.append(", ");
                b.append(sv.domain);
            }
            s.println("servers: " + (k.servers.isEmpty() ? "none" : b.toString()));
        }
    }

    // ---------- wifi ----------
    private static void wifi(ComputerLogic l, String[] p) {
        Screen s = l.s;
        CompoundTag r = l.root();
        String sub = p.length > 1 ? p[1] : "";
        if (sub.equals("scan")) {
            if (l.t.spec(TileComputer.NET) == null) {
                err(s, "wifi: no Wi-Fi module installed");
                return;
            }
            List<Net.Ap> aps = Net.scan(l.t);
            if (aps.isEmpty()) s.println("No networks in range.");
            for (Net.Ap a : aps) {
                s.println(ComputerLogic.pad(a.ssid, 18) + NetLink.bars(a.signal) + (a.secured ? "  key" : "  open"));
            }
        } else if (sub.equals("connect") && p.length >= 3) {
            String pass = p.length > 3 ? p[3] : "";
            int res = Net.check(l.t, p[2], pass);
            if (res == Net.OK) {
                r.putString("wifi_ssid", p[2]);
                r.putString("wifi_pass", pass);
                l.relink();
                s.println("Connected to " + p[2] + ".");
            } else if (res == Net.NO_MODULE) {
                err(s, "wifi: no Wi-Fi module installed");
            } else if (res == Net.BAD_PASS) {
                err(s, "wifi: wrong password");
            } else {
                err(s, "wifi: network '" + p[2] + "' not found or out of range");
            }
        } else if (sub.equals("disconnect")) {
            r.remove("wifi_ssid");
            r.remove("wifi_pass");
            l.relink();
            s.println("Wi-Fi disconnected.");
        } else {
            s.println("usage: wifi scan");
            s.println("       wifi connect <ssid> [password]");
            s.println("       wifi disconnect");
        }
    }

    // ---------- ping ----------
    private static void ping(ComputerLogic l, String[] p) {
        Screen s = l.s;
        if (p.length < 2) {
            err(s, "usage: ping <server domain | gateway>");
            return;
        }
        NetLink k = l.link();
        if (!k.up()) {
            err(s, "ping: network is unreachable");
            return;
        }
        String host = p[1].toLowerCase();
        boolean found = host.equals("gateway") || (k.router != null && k.router.ssid.equalsIgnoreCase(p[1]));
        for (TileServer sv : k.servers) {
            if (sv.domain.equals(host)) found = true;
        }
        if (!found) {
            err(s, "ping: " + p[1] + ": unknown host");
            return;
        }
        int base = k.type == NetLink.WIFI ? 6 + (4 - k.signal) * 4 : 1;
        long t = l.t.getLevel().getGameTime();
        for (int i = 0; i < 3; i++) {
            s.println("reply from " + p[1] + ": time=" + (base + (int) ((t + i * 7) % 3)) + " ms");
        }
    }

    // ---------- chat ----------
    private static void chatHelp(Screen s) {
        s.println("chat servers | use <n>     choose chat server");
        s.println("chat register <nick> <pw>  new account");
        s.println("chat login <nick> <pw>     log in");
        s.println("chat logout | who          log out | users");
        s.println("chat send <to> <text>      to = #general or nick");
        s.println("chat read [#general|nick]  last messages");
    }

    private static void chat(ComputerLogic l, String line, String[] p) {
        Screen s = l.s;
        ChatClient ch = l.chat;
        String sub = p.length > 1 ? p[1] : "";
        if (sub.length() == 0) {
            chatHelp(s);
            return;
        }
        List<TileServer> sv = ch.servers();
        if (sub.equals("servers")) {
            if (sv.isEmpty()) s.println("No chat servers on this network.");
            for (int i = 0; i < sv.size(); i++) {
                s.println((i == ch.serverIndex() ? "* " : "  ") + (i + 1) + ": " + sv.get(i).domain);
            }
        } else if (sub.equals("use")) {
            int n = -1;
            try {
                n = p.length > 2 ? Integer.parseInt(p[2]) : -1;
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (n < 1 || n > sv.size()) {
                err(s, "chat: no such server (see 'chat servers')");
            } else {
                ch.selectServer(n - 1);
                s.println("Using " + sv.get(n - 1).domain + ". Log in or register.");
            }
        } else if (sub.equals("register") || sub.equals("login")) {
            if (p.length < 4) {
                err(s, "usage: chat " + sub + " <nick> <password>");
                return;
            }
            String e = sub.equals("login") ? ch.login(p[2], p[3]) : ch.register(p[2], p[3]);
            if (e != null) err(s, "chat: " + e);
            else s.println("Logged in as " + ch.nick + " on " + ch.server().domain + ".");
        } else if (sub.equals("logout")) {
            ch.logout();
            s.println("Logged out.");
        } else if (ch.nick == null) {
            err(s, "chat: not logged in (chat login <nick> <password>)");
        } else if (sub.equals("who")) {
            ch.poll();
            s.println("Users on " + (ch.server() == null ? "-" : ch.server().domain) + ":");
            s.println("  + " + ch.nick + " (you)");
            for (String u : ch.users) s.println("  " + (ch.onlineNicks.contains(u) ? "+ " : "- ") + u);
        } else if (sub.equals("send")) {
            String[] q = line.trim().split("\\s+", 4);
            if (q.length < 4) {
                err(s, "usage: chat send <#general|nick> <text>");
                return;
            }
            String e = ch.send(q[2], q[3]);
            if (e != null) err(s, "chat: " + e);
        } else if (sub.equals("read")) {
            String peer = p.length > 2 ? p[2] : ChatStore.GENERAL;
            ch.poll();
            List<ChatStore.Msg> th = ch.thread(peer);
            if (th.isEmpty()) s.println("(no messages)");
            for (int i = Math.max(0, th.size() - 12); i < th.size(); i++) {
                ChatStore.Msg m = th.get(i);
                s.println(ChatClient.hhmm(m.time) + " " + (m.from.equals(ch.nick) ? "me" : m.from) + ": " + m.text);
            }
            ch.unread.remove(peer);
        } else {
            chatHelp(s);
        }
    }
}
