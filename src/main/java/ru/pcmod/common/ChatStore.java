package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Чат-сервис сервера: аккаунты и история сообщений (без лимитов на количество, с 0.17.0).
 * Не зависит от Minecraft, сериализацию в NBT делает TileServer.
 */
public class ChatStore {
    public static final String GENERAL = "#general";
    public static final int MAX_TEXT = 100;
    public static final long ONLINE_TICKS = 60;

    public static class Account {
        public String nick = "", hash = "";
        public long seen = -100000L;
    }

    public static class Msg {
        public int id;
        public String from = "", to = "", text = "";
        /** Время суток мира (getWorldTime) на момент отправки. */
        public long time;
    }

    public final List<Account> accounts = new ArrayList<Account>();
    public final List<Msg> msgs = new ArrayList<Msg>();
    public int nextId = 1;

    public static boolean validNick(String n) {
        return n != null && n.matches("[a-z][a-z0-9_]{0,15}");
    }

    public Account find(String nick) {
        if (nick == null) return null;
        for (Account a : accounts) {
            if (a.nick.equals(nick)) return a;
        }
        return null;
    }

    private Account auth(String nick, String hash) {
        Account a = find(nick);
        return (a != null && hash != null && a.hash.equals(hash)) ? a : null;
    }

    /** null = успех, иначе текст ошибки. */
    public String register(String nick, String hash, long now) {
        if (!validNick(nick)) return "Bad nick (a-z, 0-9, _; max 16)";
        if (find(nick) != null) return "Nick is already taken";
        Account a = new Account();
        a.nick = nick;
        a.hash = hash;
        a.seen = now;
        accounts.add(a);
        return null;
    }

    /** null = успех, иначе текст ошибки. */
    public String login(String nick, String hash, long now) {
        Account a = find(nick);
        if (a == null) return "Unknown user";
        if (!a.hash.equals(hash)) return "Wrong password";
        a.seen = now;
        return null;
    }

    /** null = успех, иначе текст ошибки. */
    public String send(String nick, String hash, String to, String text, long now, long dayTime) {
        Account a = auth(nick, hash);
        if (a == null) return "Not logged in";
        a.seen = now;
        if (to == null || (!to.equals(GENERAL) && find(to) == null)) return "No such user";
        if (to.equals(nick)) return "Cannot message yourself";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c < 127) b.append(c);
        }
        String t = b.toString().trim();
        if (t.length() == 0) return "Empty message";
        if (t.length() > MAX_TEXT) return "Message is too long (max " + MAX_TEXT + ")";
        Msg m = new Msg();
        m.id = nextId++;
        m.from = nick;
        m.to = to;
        m.text = t;
        m.time = dayTime;
        msgs.add(m);
        return null;
    }

    /** Сообщения с id > afterId, видимые этому пользователю; null, если авторизация не прошла. */
    public List<Msg> fetch(String nick, String hash, int afterId, long now) {
        Account a = auth(nick, hash);
        if (a == null) return null;
        a.seen = now;
        List<Msg> out = new ArrayList<Msg>();
        for (Msg m : msgs) {
            if (m.id <= afterId) continue;
            if (m.to.equals(GENERAL) || m.to.equals(nick) || m.from.equals(nick)) out.add(m);
        }
        return out;
    }

    public boolean online(Account a, long now) {
        return now - a.seen <= ONLINE_TICKS;
    }

    /** Стирает аккаунты и историю (счётчик id не сбрасывается). */
    public void wipe() {
        accounts.clear();
        msgs.clear();
    }
}
