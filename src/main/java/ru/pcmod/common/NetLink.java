package ru.pcmod.common;

import java.util.ArrayList;
import java.util.List;

/** Состояние сетевого подключения одного ПК. */
public class NetLink {
    public static final int NONE = 0, WIRED = 1, WIFI = 2;

    public int type = NONE;
    /** Причина, если подключения нет. */
    public String status = "Not connected";
    public String ssid = "";
    /** 0..4 (только для Wi-Fi). */
    public int signal;
    public TileRouter router;
    public final List<TileServer> servers = new ArrayList<TileServer>();

    public boolean up() {
        return type != NONE;
    }

    public String describe() {
        if (type == WIRED) return "Ethernet (cable)";
        if (type == WIFI) return "Wi-Fi " + ssid;
        return status;
    }

    /** Короткая подпись состояния: меняется, когда нужно перерисовать окна. */
    public String sig() {
        return type + ":" + ssid + ":" + signal + ":" + servers.size() + ":" + (type == NONE ? status : "");
    }

    public static String bars(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4; i++) b.append(i < n ? '|' : '.');
        return b.toString();
    }
}
