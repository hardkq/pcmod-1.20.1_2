package ru.pcmod.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * Клиентская часть мессенджера PCOS: вход на чат-сервер сети, опрос новых сообщений,
 * счётчики непрочитанных. Общая для окна «Chat» на рабочем столе и команды `chat` в терминале.
 */
public class ChatClient {
    final ComputerLogic l;
    public String nick, hash;
    /** Последняя ошибка для показа в окне. */
    public String err = "";
    /** Растёт при любом изменении, видимом пользователю (для перерисовки). */
    public int rev;
    public boolean online;
    /** Диалог, открытый сейчас на экране (для него не считаем непрочитанные). */
    public String viewing;
    public final Map<String, Integer> unread = new HashMap<String, Integer>();
    /** Ники остальных пользователей сервера по алфавиту. */
    public List<String> users = new ArrayList<String>();
    public final Set<String> onlineNicks = new HashSet<String>();

    private int serverIdx, lastId;
    private TileServer bound;
    private final List<ChatStore.Msg> cache = new ArrayList<ChatStore.Msg>();
    private String usersSig = "";

    public ChatClient(ComputerLogic l) {
        this.l = l;
    }

    public static String hhmm(long tt) {
        long h = ((tt % 24000L) / 1000L + 6L) % 24L;
        long m = (tt % 1000L) * 60L / 1000L;
        return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
    }

    // ===================== серверы =====================
    public List<TileServer> servers() {
        List<TileServer> out = new ArrayList<TileServer>();
        for (TileServer s : l.link().servers) {
            if (!s.isRemoved()) out.add(s);
        }
        Collections.sort(out, new Comparator<TileServer>() {
            @Override
            public int compare(TileServer a, TileServer b) {
                return a.domain.compareTo(b.domain);
            }
        });
        return out;
    }

    public TileServer server() {
        List<TileServer> s = servers();
        if (s.isEmpty()) return null;
        if (serverIdx < 0 || serverIdx >= s.size()) serverIdx = 0;
        return s.get(serverIdx);
    }

    public int serverIndex() {
        return serverIdx;
    }

    public void selectServer(int i) {
        int n = servers().size();
        logout();
        serverIdx = n == 0 ? 0 : ((i % n) + n) % n;
        err = "";
        rev++;
    }

    public void nextServer() {
        selectServer(serverIdx + 1);
    }

    // ===================== вход / выход =====================
    public String savedNick() {
        CompoundTag r = l.root();
        return r == null ? "" : r.getString("chat_nick");
    }

    public String login(String n, String pass) {
        TileServer s = server();
        if (s == null) return "No server found";
        String h = VFS.hash(pass);
        String e = s.login(n, h);
        if (e == null) begin(n, h, s);
        return e;
    }

    public String register(String n, String pass) {
        TileServer s = server();
        if (s == null) return "No server found";
        if (pass.length() == 0) return "Password must not be empty";
        String h = VFS.hash(pass);
        String e = s.register(n, h);
        if (e == null) begin(n, h, s);
        return e;
    }

    private void begin(String n, String h, TileServer s) {
        nick = n;
        hash = h;
        bound = s;
        lastId = 0;
        cache.clear();
        unread.clear();
        usersSig = "";
        online = true;
        err = "";
        CompoundTag r = l.root();
        if (r != null) r.putString("chat_nick", n);
        doPoll(true);
        rev++;
    }

    public void logout() {
        nick = null;
        hash = null;
        bound = null;
        lastId = 0;
        cache.clear();
        unread.clear();
        users = new ArrayList<String>();
        onlineNicks.clear();
        usersSig = "";
        online = false;
        rev++;
    }

    // ===================== сообщения =====================
    /** Ключ диалога, к которому относится сообщение. */
    public String conv(ChatStore.Msg m) {
        if (m.to.equals(ChatStore.GENERAL)) return ChatStore.GENERAL;
        return m.from.equals(nick) ? m.to : m.from;
    }

    public List<ChatStore.Msg> thread(String peer) {
        List<ChatStore.Msg> out = new ArrayList<ChatStore.Msg>();
        for (ChatStore.Msg m : cache) {
            if (conv(m).equals(peer)) out.add(m);
        }
        return out;
    }

    public int totalUnread() {
        int n = 0;
        for (Integer v : unread.values()) n += v;
        return n;
    }

    public int unreadFor(String peer) {
        Integer n = unread.get(peer);
        return n == null ? 0 : n;
    }

    /** null = отправлено, иначе текст ошибки. */
    public String send(String to, String text) {
        TileServer s = server();
        if (nick == null || s == null || s != bound) return "Not connected to the server";
        String e = s.send(nick, hash, to, text);
        if (e == null) doPoll(false);
        return e;
    }

    /** Забирает новые сообщения. true, если что-то изменилось на экране. */
    public boolean poll() {
        return doPoll(false);
    }

    private boolean doPoll(boolean silent) {
        if (nick == null) return false;
        int before = rev;
        TileServer s = server();
        if (s == null) {
            if (online) {
                online = false;
                rev++;
            }
            return rev != before;
        }
        if (!s.isUp()) {
            // сервер перегрелся и выключен: ждём, пока остынет
            if (online) {
                online = false;
                rev++;
            }
            return rev != before;
        }
        if (s != bound) {
            bound = s;
            lastId = 0;
            cache.clear();
            unread.clear();
            usersSig = "";
            silent = true;
        }
        ChatStore st = s.store();
        long now = s.getLevel().getGameTime();
        List<ChatStore.Msg> fresh = st.fetch(nick, hash, lastId, now);
        if (fresh == null) {
            logout();
            err = "Session expired, please log in again";
            return true;
        }
        if (!online) {
            online = true;
            rev++;
        }
        for (ChatStore.Msg m : fresh) {
            cache.add(m);
            if (m.id > lastId) lastId = m.id;
            if (!silent && !m.from.equals(nick)) {
                String c = conv(m);
                if (!c.equals(viewing)) unread.put(c, unreadFor(c) + 1);
            }
            rev++;
        }

        List<String> names = new ArrayList<String>();
        for (ChatStore.Account a : st.accounts) {
            if (!a.nick.equals(nick)) names.add(a.nick);
        }
        Collections.sort(names);
        StringBuilder sig = new StringBuilder();
        for (String n : names) sig.append(n).append(st.online(st.find(n), now) ? '+' : '-').append(',');
        String ns = sig.toString();
        if (!ns.equals(usersSig)) {
            usersSig = ns;
            users = names;
            onlineNicks.clear();
            for (String n : names) {
                if (st.online(st.find(n), now)) onlineNicks.add(n);
            }
            rev++;
        }
        return rev != before;
    }
}
