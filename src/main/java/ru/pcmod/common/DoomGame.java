package ru.pcmod.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Мини-DOOM для экрана ПК: «настоящий» raycasting (как в Wolfenstein/DOOM) на текстовой сетке.
 * Класс не зависит от Minecraft: рисует через интерфейс {@link Out}, поэтому его можно запускать и проверять отдельно.
 * Цвет клетки: attr = цвет | 16 - залитая клетка (символ рисуется чёрным), attr = цвет - обычный символ на чёрном.
 * Уровни генерируются из фиксированного зерна (на каждом уровне карта всегда одна и та же).
 * Камера следует за курсором мыши (см. {@link #look}), клик по виду - выстрел.
 * Это самостоятельная игра в духе DOOM, а не оригинал и не связана с id Software.
 */
public class DoomGame {
    public interface Out {
        void put(int x, int y, char c, int attr);
    }

    /** Размер 3D-вида в клетках экрана и высота панели состояния. */
    public static final int VW = 50, VH = 15, HUDH = 4;
    public static final int LEVELS = 3;
    public static final int TITLE = 0, PLAY = 1, DEAD = 2, INTER = 3, WON = 4;

    // коды клавиш LWJGL (не зависят от раскладки клавиатуры)
    public static final int K_UP = 200, K_DOWN = 208, K_LEFT = 203, K_RIGHT = 205, K_ENTER = 28, K_NENTER = 156,
            K_SPACE = 57, K_W = 17, K_A = 30, K_S = 31, K_D = 32, K_E = 18, K_Q = 16, K_1 = 2, K_2 = 3, K_M = 50, K_R = 19;

    static final int ZOMBIE = 0, IMP = 1, PINKY = 2, BARREL = 3;
    static final double TURN = 0.14, STEP = 0.25, FOVLEN = 0.72;
    /** Поворот за игровой тик у края вида (мёртвая зона в центре, дальше быстрее) и таймаут курсора в тиках. */
    static final double LOOK_MAX = 0.16, LOOK_DEAD = 0.10;
    static final int LOOK_TIMEOUT = 15;

    static class Mob {
        int type, hp, state, cd, pain, atk, dying;
        double x, y;
    }

    static class Item {
        char kind;
        double x, y;
        boolean taken;
    }

    static class Ball {
        double x, y, vx, vy;
        int life;
    }

    static class Fx {
        double x, y;
        int life;
    }

    static class Spr {
        double tx, ty;
        String[] art;
        double scale, lift;
        int flash;
    }

    // ---------- состояние ----------
    public int state = TITLE;
    public boolean dirty = true;
    public int level = 1, kills, total, hp = 100, armor, bullets = 50, shells;
    public boolean hasShotgun;
    int weapon;
    int cooldown, flash, hurt, msgT, deadT, frame, acts, bonus, bonusCol, bobAmp, lookAge;
    /** Поворот камеры от положения курсора: -1..1 (отрицательное - влево). */
    public double lookX;
    double bobT;
    boolean stepped;
    String msg = "";
    boolean mapOn;
    double px, py, pa;
    int sHp = 100, sArmor, sBul = 50, sShe;
    boolean sGun;
    char[][] map = new char[1][1];
    int mw, mh;
    final List<Mob> mobs = new ArrayList<Mob>();
    final List<Item> items = new ArrayList<Item>();
    final List<Ball> balls = new ArrayList<Ball>();
    final List<Fx> fxs = new ArrayList<Fx>();
    final Random rnd = new Random();
    final double[] zbuf = new double[VW];
    final char[] vc = new char[VW * VH];
    final int[] va = new int[VW * VH];

    // ===================== спрайты =====================
    static final String[] A_IMP = {
        "...6666...", "..666666..", ".66E66E66.", ".66666666.", "..644446..",
        "...6666...", "6.666666.6", "66.6666.66", "66.6666.66", ".6.6666.6.",
        "...6666...", "...6..6...", "..66..66..", "..66..66.."};
    static final String[] A_IMP2 = {
        "...6666...", "..666666..", ".66E66E66.", ".66666666.", "..644446..",
        "...6666...", "6.666666.6", "66.6666.66", "66.6666.66", ".6.6666.6.",
        "...6666...", "...66.6...", "..66...66.", ".66.....66"};
    static final String[] A_IMP_ATK = {
        "6..6666..6", "6.666666.6", ".66E66E66.", ".66666666.", "..6C44C6..",
        "...6666...", "..666666..", "..666666..", "..666666..", "...6666...",
        "...6666...", "...6..6...", "..66..66..", "..66..66.."};
    static final String[] A_PINKY = {
        "..DDDDDDDD..", ".DDDDDDDDDD.", "DDDEDDDDEDDD", "DDDDDDDDDDDD", "DDFDFDFDFDDD",
        ".DDFFFFFFDD.", ".DDDDDDDDDD.", ".DDDDDDDDDD.", ".DDD....DDD.", ".555....555."};
    static final String[] A_PINKY2 = {
        "..DDDDDDDD..", ".DDDDDDDDDD.", "DDDEDDDDEDDD", "DDDDDDDDDDDD", "DDFDFDFDFDDD",
        ".DDFFFFFFDD.", ".DDDDDDDDDD.", ".DDDDDDDDDD.", "..DDD..DDD..", "..555..555.."};
    static final String[] A_PINKY_ATK = {
        "..DDDDDDDD..", ".DDDDDDDDDD.", "DDDEDDDDEDDD", "DD44444444DD", "DFFFFFFFFFFD",
        "D4444444444D", ".DDDDDDDDDD.", ".DDDDDDDDDD.", ".DDD....DDD.", ".555....555."};
    static final String[] A_ZOMBIE = {
        "..CCCC..", ".CCCCCC.", ".C0CC0C.", ".CCCCCC.", "..2222..", ".222222.",
        "72222227", "C222222C", ".222222.", ".22..22.", ".22..22.", ".66..66."};
    static final String[] A_ZOMBIE2 = {
        "..CCCC..", ".CCCCCC.", ".C0CC0C.", ".CCCCCC.", "..2222..", ".222222.",
        "72222227", "C222222C", ".222222.", "..22.22.", "..22.22.", "..66.66."};
    static final String[] A_ZOMBIE_ATK = {
        "..CCCC..", ".CCCCCC.", ".C0CC0C.", ".CCCCCC.", "..2222.E", ".2222277",
        "22222277", "C222222.", ".222222.", ".22..22.", ".22..22.", ".66..66."};
    static final String[] A_BARREL = {
        ".AAAA.", "AAAAAA", "EEEEEE", "AAAAAA", "AAAAAA", "EEEEEE", "AAAAAA", ".AAAA."};
    static final String[] A_MEDKIT = {"FFFFFF", "FF44FF", "F4444F", "FF44FF", "FFFFFF"};
    static final String[] A_AMMO = {"EEEEEE", "E6666E", "E6EE6E", "EEEEEE"};
    static final String[] A_SHELLS = {"CCCCCC", "C4E4EC", "C4E4EC", "CCCCCC"};
    static final String[] A_ARMOR = {"A....A", "AAAAAA", "A2AA2A", "AAAAAA", ".AAAA."};
    static final String[] A_GUN = {"7777777.", "88888888", "..8..6.."};
    static final String[] A_BALL = {".C.", "CEC", ".C."};
    static final String[] A_BOOM = {"..E..E..", ".ECEECE.", "EECEEECE", ".ECCCCE.", "..EEEE.."};
    static final String[] A_CORPSE_IMP = {"66444666", "..6666.."};
    static final String[] A_CORPSE_PINKY = {"DD4DDDDD", ".DDDDDD."};
    static final String[] A_CORPSE_ZOMBIE = {"222CC472", ".222222."};
    static final String[] A_PISTOL = {
        "....77....", "...7887...", "...7887...", "...7777...", "..CCCCCC..", ".CCCCCCCC.", "6666666666"};
    static final String[] A_SHOTGUN = {
        "....8888....", "...877778...", "...877778...", "...877778...", "..66666666..", ".6CCCCCCCC6.",
        "CCCCCCCCCCCC", "666666666666"};
    static final String[] A_FLASH = {"..E..E..", ".EEEEEE.", "EEEFFEEE", ".EEEEEE."};

    // ===================== запуск =====================
    public DoomGame() {
        rnd.setSeed(System.nanoTime());
    }

    public void newGame() {
        level = 1;
        hp = 100;
        armor = 0;
        bullets = 50;
        shells = 0;
        hasShotgun = false;
        loadLevel(1);
    }

    /** Строит уровень, запоминает снимок состояния игрока (после смерти уровень начинается с него). */
    void loadLevel(int lv) {
        level = lv;
        sHp = hp;
        sArmor = armor;
        sBul = bullets;
        sShe = shells;
        sGun = hasShotgun;
        build(lv);
        weapon = hasShotgun && shells > 0 && bullets == 0 ? 1 : 0;
        cooldown = 0;
        flash = 0;
        hurt = 0;
        msg = "Level " + lv + ": " + levelName(lv);
        msgT = 40;
        kills = 0;
        lookX = 0;
        lookAge = 0;
        bonus = 0;
        state = PLAY;
        dirty = true;
    }

    void restartLevel() {
        hp = Math.max(sHp, 50);
        armor = sArmor;
        bullets = Math.max(sBul, 30);
        shells = sShe;
        hasShotgun = sGun;
        loadLevel(level);
    }

    static String levelName(int lv) {
        return lv == 1 ? "Hangar" : lv == 2 ? "Toxin Refinery" : "Command Center";
    }

    // ---------- генерация уровня: сетка комнат с дверями ----------
    static final int PX = 10, PY = 8;

    void build(int lv) {
        Random r = new Random(1000L + lv * 31337L);
        int gc = lv == 1 ? 3 : 4, gr = lv == 3 ? 3 : 2;
        mw = gc * PX + 1;
        mh = gr * PY + 1;
        map = new char[mh][mw];
        char[] walls = {'#', 'G', 'M', 'B', 'W'};
        // стены разных типов по областям
        int shift = r.nextInt(walls.length);
        for (int y = 0; y < mh; y++) {
            for (int x = 0; x < mw; x++) {
                int bx = Math.min(gc - 1, x / PX), by = Math.min(gr - 1, y / PY);
                map[y][x] = walls[(bx + by * 2 + shift) % walls.length];
            }
        }
        for (int by = 0; by < gr; by++) {
            for (int bx = 0; bx < gc; bx++) {
                for (int y = by * PY + 1; y < by * PY + PY; y++) {
                    for (int x = bx * PX + 1; x < bx * PX + PX; x++) map[y][x] = '.';
                }
            }
        }
        // остовное дерево (DFS) + пара лишних проходов
        boolean[][] seen = new boolean[gr][gc];
        boolean[][] linkR = new boolean[gr][gc], linkD = new boolean[gr][gc];
        int[] stack = new int[gr * gc];
        int sp = 0;
        stack[sp++] = 0;
        seen[0][0] = true;
        int[] depth = new int[gr * gc];
        while (sp > 0) {
            int cur = stack[sp - 1], cx = cur % gc, cy = cur / gc;
            List<Integer> opts = new ArrayList<Integer>();
            if (cx + 1 < gc && !seen[cy][cx + 1]) opts.add(0);
            if (cx > 0 && !seen[cy][cx - 1]) opts.add(1);
            if (cy + 1 < gr && !seen[cy + 1][cx]) opts.add(2);
            if (cy > 0 && !seen[cy - 1][cx]) opts.add(3);
            if (opts.isEmpty()) {
                sp--;
                continue;
            }
            int o = opts.get(r.nextInt(opts.size()));
            int nx = cx + (o == 0 ? 1 : o == 1 ? -1 : 0), ny = cy + (o == 2 ? 1 : o == 3 ? -1 : 0);
            if (o == 0) linkR[cy][cx] = true;
            else if (o == 1) linkR[cy][nx] = true;
            else if (o == 2) linkD[cy][cx] = true;
            else linkD[ny][cx] = true;
            seen[ny][nx] = true;
            depth[ny * gc + nx] = depth[cur] + 1;
            stack[sp++] = ny * gc + nx;
        }
        for (int i = 0; i < 2 + lv; i++) {
            int cx = r.nextInt(gc), cy = r.nextInt(gr);
            if (r.nextBoolean() && cx + 1 < gc) linkR[cy][cx] = true;
            else if (cy + 1 < gr) linkD[cy][cx] = true;
        }
        for (int by = 0; by < gr; by++) {
            for (int bx = 0; bx < gc; bx++) {
                if (linkR[by][bx]) map[by * PY + PY / 2][(bx + 1) * PX] = 'D';
                if (linkD[by][bx]) map[(by + 1) * PY][bx * PX + PX / 2] = 'D';
            }
        }
        // дальняя от старта комната - выход; настоящее расстояние по связям считаем BFS
        int[] dist = new int[gr * gc];
        java.util.Arrays.fill(dist, -1);
        int[] q = new int[gr * gc];
        int qh = 0, qt = 0;
        q[qt++] = 0;
        dist[0] = 0;
        while (qh < qt) {
            int cur = q[qh++], cx = cur % gc, cy = cur / gc;
            int[] nb = {cx + 1 < gc && linkR[cy][cx] ? cur + 1 : -1, cx > 0 && linkR[cy][cx - 1] ? cur - 1 : -1,
                    cy + 1 < gr && linkD[cy][cx] ? cur + gc : -1, cy > 0 && linkD[cy - 1][cx] ? cur - gc : -1};
            for (int n : nb) {
                if (n >= 0 && dist[n] < 0) {
                    dist[n] = dist[cur] + 1;
                    q[qt++] = n;
                }
            }
        }
        int exitRoom = 0;
        for (int i = 0; i < gr * gc; i++) {
            if (dist[i] > dist[exitRoom]) exitRoom = i;
        }
        int ex = exitRoom % gc, ey = exitRoom / gc;
        map[ey * PY][ex * PX + 3] = 'X';
        // колонны в углах комнат (не мешают проходам)
        for (int by = 0; by < gr; by++) {
            for (int bx = 0; bx < gc; bx++) {
                if (bx == 0 && by == 0) continue;
                int[][] spots = {{3, 2}, {7, 2}, {3, 6}, {7, 6}};
                for (int[] sp2 : spots) {
                    if (r.nextInt(3) == 0) map[by * PY + sp2[1]][bx * PX + sp2[0]] = 'G';
                }
            }
        }
        mobs.clear();
        items.clear();
        balls.clear();
        fxs.clear();
        px = 5.5;
        py = 4.5;
        pa = 0;
        // враги и предметы
        int nz = lv == 1 ? 3 : lv == 2 ? 3 : 4, ni = lv == 1 ? 3 : lv == 2 ? 5 : 7, np = lv == 1 ? 0 : lv == 2 ? 3 : 5;
        total = 0;
        int[] kinds = new int[nz + ni + np];
        for (int i = 0; i < kinds.length; i++) kinds[i] = i < nz ? ZOMBIE : i < nz + ni ? IMP : PINKY;
        for (int k : kinds) {
            double[] p = freeSpot(r, gc, gr, true);
            Mob m = new Mob();
            m.type = k;
            m.hp = k == ZOMBIE ? 20 : k == IMP ? 40 : 70;
            m.x = p[0];
            m.y = p[1];
            mobs.add(m);
            total++;
        }
        for (int by = 0; by < gr; by++) {
            for (int bx = 0; bx < gc; bx++) {
                if (r.nextInt(2) == 0) {
                    double[] p = freeSpot(r, gc, gr, false);
                    Mob b = new Mob();
                    b.type = BARREL;
                    b.hp = 1;
                    b.x = p[0];
                    b.y = p[1];
                    mobs.add(b);
                }
                char[] kindsI = {'h', 'a', 'e', 'r', 'a'};
                if (bx == 0 && by == 0 && lv == 1) {
                    addItem('a', bx * PX + 3.5, by * PY + 2.5);
                    continue;
                }
                double[] p = freeSpot(r, gc, gr, false);
                addItem(kindsI[r.nextInt(kindsI.length)], p[0], p[1]);
            }
        }
        // дробовик - в одной из комнат рядом со стартом (на втором и третьем уровне он у игрока уже есть, добавляем патроны)
        int target = 1;
        for (int i = 1; i < gr * gc; i++) {
            if (dist[i] == 1) {
                target = i;
                break;
            }
        }
        addItem(hasShotgun ? 'e' : 's', (target % gc) * PX + 2.5, (target / gc) * PY + 4.5);
        addItem('h', px + 1, py + 2);
    }

    void addItem(char kind, double x, double y) {
        Item it = new Item();
        it.kind = kind;
        it.x = x;
        it.y = y;
        items.add(it);
    }

    /** Случайная свободная клетка (враги не появляются рядом со стартом). */
    double[] freeSpot(Random r, int gc, int gr, boolean farFromStart) {
        for (int tries = 0; tries < 200; tries++) {
            int bx = r.nextInt(gc), by = r.nextInt(gr);
            if (farFromStart && bx == 0 && by == 0) continue;
            int x = bx * PX + 1 + r.nextInt(PX - 1), y = by * PY + 1 + r.nextInt(PY - 1);
            if (map[y][x] != '.') continue;
            boolean crowd = false;
            for (Mob m : mobs) {
                if (Math.abs(m.x - x - 0.5) < 1.2 && Math.abs(m.y - y - 0.5) < 1.2) crowd = true;
            }
            for (Item i : items) {
                if (Math.abs(i.x - x - 0.5) < 0.9 && Math.abs(i.y - y - 0.5) < 0.9) crowd = true;
            }
            if (crowd) continue;
            return new double[] {x + 0.5, y + 0.5};
        }
        return new double[] {PX + 5.5, PY / 2 + 0.5};
    }

    // ===================== геометрия =====================
    boolean solid(int ix, int iy) {
        if (ix < 0 || iy < 0 || ix >= mw || iy >= mh) return true;
        char c = map[iy][ix];
        return c != '.' && c != 'd';
    }

    boolean blocked(double x, double y, double rad) {
        return solid((int) Math.floor(x - rad), (int) Math.floor(y - rad)) || solid((int) Math.floor(x + rad), (int) Math.floor(y - rad))
                || solid((int) Math.floor(x - rad), (int) Math.floor(y + rad)) || solid((int) Math.floor(x + rad), (int) Math.floor(y + rad));
    }

    boolean barrelAt(double x, double y) {
        for (Mob m : mobs) {
            if (m.type == BARREL && m.state != 2 && Math.abs(m.x - x) < 0.45 && Math.abs(m.y - y) < 0.45) return true;
        }
        return false;
    }

    boolean los(double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0, d = Math.sqrt(dx * dx + dy * dy);
        int n = (int) (d / 0.15) + 1;
        for (int i = 1; i < n; i++) {
            if (solid((int) Math.floor(x0 + dx * i / n), (int) Math.floor(y0 + dy * i / n))) return false;
        }
        return true;
    }

    void walk(double dx, double dy) {
        double nx = px + dx, ny = py + dy, ox = px, oy = py;
        if (!blocked(nx, py, 0.22) && !barrelAt(nx, py)) px = nx;
        if (!blocked(px, ny, 0.22) && !barrelAt(px, ny)) py = ny;
        if (px != ox || py != oy) stepped = true;
    }

    /** Расстояние до стены вдоль луча (DDA); через открытые двери луч идёт дальше. */
    double wallDist(double ox, double oy, double rdx, double rdy) {
        int mx = (int) Math.floor(ox), my = (int) Math.floor(oy);
        double ddx = rdx == 0 ? 1e30 : Math.abs(1.0 / rdx), ddy = rdy == 0 ? 1e30 : Math.abs(1.0 / rdy);
        int sx = rdx < 0 ? -1 : 1, sy = rdy < 0 ? -1 : 1;
        double sdx = (rdx < 0 ? ox - mx : mx + 1.0 - ox) * ddx, sdy = (rdy < 0 ? oy - my : my + 1.0 - oy) * ddy;
        for (int i = 0; i < 80; i++) {
            if (sdx < sdy) {
                sdx += ddx;
                mx += sx;
                if (solid(mx, my)) return sdx - ddx;
            } else {
                sdy += ddy;
                my += sy;
                if (solid(mx, my)) return sdy - ddy;
            }
        }
        return 40;
    }

    // ===================== ввод =====================
    public void key(char c, int key) {
        if (state == TITLE) {
            if (key == K_ENTER || key == K_NENTER || key == K_SPACE) newGame();
            dirty = true;
            return;
        }
        if (state == DEAD) {
            if (deadT > 8 && (key == K_ENTER || key == K_NENTER || key == K_SPACE || key == K_R)) restartLevel();
            dirty = true;
            return;
        }
        if (state == INTER) {
            if (key == K_ENTER || key == K_NENTER || key == K_SPACE) {
                if (level >= LEVELS) state = WON;
                else loadLevel(level + 1);
            }
            dirty = true;
            return;
        }
        if (state == WON) {
            if (key == K_ENTER || key == K_NENTER || key == K_SPACE) state = TITLE;
            dirty = true;
            return;
        }
        boolean mv = key == K_UP || key == K_DOWN || key == K_LEFT || key == K_RIGHT || key == K_W || key == K_S
                || key == K_A || key == K_D || key == K_Q;
        if (mv && ++acts > 2) return; // не быстрее ~20 шагов в секунду, даже при повторе клавиши
        double dx = Math.cos(pa), dy = Math.sin(pa);
        if (key == K_UP || key == K_W) walk(dx * STEP, dy * STEP);
        else if (key == K_DOWN || key == K_S) walk(-dx * STEP * 0.8, -dy * STEP * 0.8);
        else if (key == K_LEFT || key == K_Q) pa -= TURN;
        else if (key == K_RIGHT) pa += TURN;
        else if (key == K_A) walk(dy * STEP * 0.8, -dx * STEP * 0.8);
        else if (key == K_D) walk(-dy * STEP * 0.8, dx * STEP * 0.8);
        else if (key == K_SPACE) fire();
        else if (key == K_E || key == K_ENTER || key == K_NENTER) use();
        else if (key == K_1) selectWeapon(0);
        else if (key == K_2) selectWeapon(1);
        else if (key == K_M) mapOn = !mapOn;
        else if (key == K_R) {
            msg = "Hold on - die or finish the level to restart.";
            msgT = 20;
        }
        keepAngle();
        dirty = true;
    }

    void keepAngle() {
        while (pa < 0) pa += Math.PI * 2;
        while (pa >= Math.PI * 2) pa -= Math.PI * 2;
    }

    /**
     * Положение курсора мыши относительно 3D-вида (клетки vx, vy). Пока курсор над видом, камера плавно поворачивается
     * в его сторону: чем дальше курсор от центра, тем быстрее поворот. Клики для поворота не нужны.
     * Курсор вне вида (в том числе vx < 0) останавливает поворот.
     */
    public void look(int vx, int vy) {
        lookAge = 0;
        if (state != PLAY || vx < 0 || vx >= VW || vy < 0 || vy >= VH) {
            lookX = 0;
            return;
        }
        double n = (vx + 0.5) / VW * 2 - 1, a = Math.abs(n);
        if (a < LOOK_DEAD) {
            lookX = 0;
            return;
        }
        double t = (a - LOOK_DEAD) / (1 - LOOK_DEAD);
        lookX = Math.signum(n) * (0.3 * t + 0.7 * t * t);
    }

    /** Клик мышью по 3D-виду - выстрел (поворачивает камеру курсор, см. {@link #look}). */
    public void click(int vx, int vy) {
        if (state != PLAY) {
            key('\n', K_ENTER);
            return;
        }
        if (vy < 0 || vy >= VH || vx < 0 || vx >= VW) return;
        fire();
        dirty = true;
    }

    void selectWeapon(int w) {
        if (w == 1 && !hasShotgun) {
            say("You have no shotgun yet", 20);
            return;
        }
        weapon = w;
        say(w == 0 ? "Pistol" : "Shotgun", 15);
    }

    void say(String m, int t) {
        msg = m;
        msgT = t;
    }

    void use() {
        double dx = Math.cos(pa), dy = Math.sin(pa);
        for (double d = 0.6; d <= 1.3; d += 0.35) {
            int ix = (int) Math.floor(px + dx * d), iy = (int) Math.floor(py + dy * d);
            if (ix < 0 || iy < 0 || ix >= mw || iy >= mh) continue;
            char c = map[iy][ix];
            if (c == 'D') {
                map[iy][ix] = 'd';
                say("Door opened", 15);
                return;
            }
            if (c == 'X') {
                state = INTER;
                dirty = true;
                return;
            }
            if (c != '.' && c != 'd') {
                say("Nothing to use here", 10);
                return;
            }
        }
    }

    // ===================== бой =====================
    void fire() {
        if (cooldown > 0) return;
        if (weapon == 1 && shells <= 0) weapon = 0;
        if (weapon == 0 && bullets <= 0) {
            if (hasShotgun && shells > 0) {
                weapon = 1;
            } else {
                say("Out of ammo!", 15);
                return;
            }
        }
        if (weapon == 0) {
            bullets--;
            cooldown = 4;
            shootRay(pa + (rnd.nextDouble() - 0.5) * 0.03, 8 + rnd.nextInt(7));
        } else {
            shells--;
            cooldown = 9;
            for (int i = 0; i < 7; i++) shootRay(pa + (rnd.nextDouble() - 0.5) * 0.22, 5 + rnd.nextInt(5));
        }
        flash = 2;
        // выстрел слышат враги рядом
        for (Mob m : mobs) {
            if (m.state == 0 && m.type != BARREL && dist(m.x, m.y) < 10) m.state = 1;
        }
    }

    double dist(double x, double y) {
        double dx = x - px, dy = y - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    void shootRay(double ang, int dmg) {
        double rdx = Math.cos(ang), rdy = Math.sin(ang);
        double wd = wallDist(px, py, rdx, rdy);
        Mob best = null;
        double bt = wd;
        for (Mob m : mobs) {
            if (m.state == 2) continue;
            double vx = m.x - px, vy = m.y - py;
            double t = vx * rdx + vy * rdy;
            if (t < 0.15 || t > bt) continue;
            double perp = Math.abs(vx * rdy - vy * rdx);
            double rad = m.type == PINKY ? 0.42 : m.type == BARREL ? 0.3 : 0.32;
            if (perp < rad) {
                best = m;
                bt = t;
            }
        }
        if (best != null) hurtMob(best, dmg);
    }

    void hurtMob(Mob m, int d) {
        if (m.state == 2) return;
        m.hp -= d;
        if (m.hp <= 0) {
            killMob(m);
            return;
        }
        if (m.type != BARREL) {
            if (m.state == 0) m.state = 1;
            if (rnd.nextInt(3) == 0) m.pain = 3;
        }
    }

    void killMob(Mob m) {
        m.state = 2;
        m.dying = 0;
        if (m.type == BARREL) {
            explode(m.x, m.y);
            return;
        }
        kills++;
        if (m.type == ZOMBIE) addItem('a', m.x, m.y);
    }

    void explode(double x, double y) {
        Fx f = new Fx();
        f.x = x;
        f.y = y;
        f.life = 4;
        fxs.add(f);
        double dp = dist(x, y);
        if (dp < 2.6) damage((int) (45 * (1 - dp / 2.6)) + 3);
        for (int i = 0; i < mobs.size(); i++) {
            Mob m = mobs.get(i);
            if (m.state == 2) continue;
            double dx = m.x - x, dy = m.y - y, d = Math.sqrt(dx * dx + dy * dy);
            if (d < 2.6) hurtMob(m, (int) (70 * (1 - d / 2.6)) + 5);
        }
    }

    void damage(int d) {
        if (state != PLAY) return;
        int absorbed = Math.min(armor, d / 2);
        armor -= absorbed;
        hp -= d - absorbed;
        hurt = 3;
        if (hp <= 0) {
            hp = 0;
            state = DEAD;
            deadT = 0;
        }
    }

    // ===================== игровой тик (10 раз в секунду) =====================
    public void tick() {
        frame++;
        acts = 0;
        if (state == TITLE) {
            if (frame % 2 == 0) dirty = true; // огонь на заставке
            return;
        }
        if (state == DEAD) {
            deadT++;
            if (deadT < VH + 8) dirty = true;
            return;
        }
        if (state != PLAY) return;
        boolean act = false;
        if (lookX != 0) {
            if (++lookAge > LOOK_TIMEOUT) {
                lookX = 0;
            } else {
                pa += lookX * LOOK_MAX;
                keepAngle();
                act = true;
            }
        }
        if (stepped) {
            bobT += 0.8;
            bobAmp = 4;
            stepped = false;
            act = true;
        } else if (bobAmp > 0) {
            bobAmp--;
            act = true;
        }
        if (cooldown > 0) {
            cooldown--;
            act = true;
        }
        if (flash > 0) {
            flash--;
            act = true;
        }
        if (hurt > 0) {
            hurt--;
            act = true;
        }
        if (bonus > 0) {
            bonus--;
            act = true;
        }
        if (msgT > 0 && --msgT == 0) {
            msg = "";
            act = true;
        }
        for (int i = 0; i < mobs.size(); i++) {
            Mob m = mobs.get(i);
            if (m.atk > 0) {
                m.atk--;
                act = true;
            }
            if (m.state == 2 && m.dying < 4) {
                m.dying++;
                act = true;
            }
            if (m.state == 1 || m.pain > 0) {
                think(m);
                act = true;
            }
        }
        for (int i = balls.size() - 1; i >= 0; i--) {
            Ball b = balls.get(i);
            act = true;
            b.x += b.vx;
            b.y += b.vy;
            if (--b.life <= 0 || solid((int) Math.floor(b.x), (int) Math.floor(b.y))) {
                balls.remove(i);
                continue;
            }
            if (Math.abs(b.x - px) < 0.4 && Math.abs(b.y - py) < 0.4) {
                damage(5 + rnd.nextInt(5));
                balls.remove(i);
            }
        }
        for (int i = fxs.size() - 1; i >= 0; i--) {
            act = true;
            if (--fxs.get(i).life <= 0) fxs.remove(i);
        }
        if (state == PLAY) act |= pickups();
        if (act) dirty = true;
    }

    boolean pickups() {
        boolean got = false;
        for (Item it : items) {
            if (it.taken || Math.abs(it.x - px) > 0.55 || Math.abs(it.y - py) > 0.55) continue;
            char k = it.kind;
            if (k == 'h') {
                if (hp >= 100) continue;
                hp = Math.min(100, hp + 25);
                say("Medkit: +25 health", 20);
            } else if (k == 'r') {
                if (armor >= 100) continue;
                armor = Math.min(100, armor + 50);
                say("Armor vest: +50", 20);
            } else if (k == 'a') {
                if (bullets >= 99) continue;
                bullets = Math.min(99, bullets + 15);
                say("Clip: +15 bullets", 20);
            } else if (k == 'e') {
                if (shells >= 50) continue;
                shells = Math.min(50, shells + 8);
                say("Shells: +8", 20);
            } else if (k == 's') {
                hasShotgun = true;
                shells = Math.min(50, shells + 8);
                weapon = 1;
                say("You got the SHOTGUN! (key 2)", 30);
            }
            it.taken = true;
            got = true;
            bonus = 3;
            bonusCol = k == 'h' ? 10 : k == 'r' ? 9 : 14;
        }
        return got;
    }

    void think(Mob m) {
        double dx = px - m.x, dy = py - m.y, d = Math.sqrt(dx * dx + dy * dy);
        if (m.pain > 0) {
            m.pain--;
            return;
        }
        if (m.cd > 0) m.cd--;
        boolean see = d < 12 && los(m.x, m.y, px, py);
        double speed = m.type == PINKY ? 0.17 : m.type == IMP ? 0.085 : 0.06;
        if (m.type == ZOMBIE) {
            if (see && m.cd == 0 && d < 10) {
                if (rnd.nextDouble() < 0.7 - d * 0.045) damage(3 + rnd.nextInt(6));
                m.cd = 12 + rnd.nextInt(8);
                m.atk = 3;
                return;
            }
        } else if (m.type == IMP) {
            if (d < 1.4 && m.cd == 0) {
                damage(3 + rnd.nextInt(5));
                m.cd = 8;
                m.atk = 3;
                return;
            }
            if (see && d < 10 && m.cd == 0 && d > 1.5 && rnd.nextInt(3) == 0) {
                Ball b = new Ball();
                b.x = m.x;
                b.y = m.y;
                b.vx = dx / d * 0.45;
                b.vy = dy / d * 0.45;
                b.life = 40;
                balls.add(b);
                m.cd = 14 + rnd.nextInt(10);
                m.atk = 4;
                return;
            }
        } else if (m.type == PINKY) {
            if (d < 1.15 && m.cd == 0) {
                damage(5 + rnd.nextInt(6));
                m.cd = 7;
                m.atk = 3;
                return;
            }
        }
        if (d < 0.8 || (m.type == ZOMBIE && see && d < 4)) return;
        double vx = dx / d * speed, vy = dy / d * speed;
        boolean moved = false;
        if (!blocked(m.x + vx, m.y + vy, 0.3)) {
            m.x += vx;
            m.y += vy;
            moved = true;
        } else if (!blocked(m.x + vx, m.y, 0.3)) {
            m.x += vx;
            moved = true;
        } else if (!blocked(m.x, m.y + vy, 0.3)) {
            m.y += vy;
            moved = true;
        }
        if (!moved) {
            double s = rnd.nextBoolean() ? 1 : -1;
            if (!blocked(m.x - vy * s * 2, m.y + vx * s * 2, 0.3)) {
                m.x -= vy * s * 2;
                m.y += vx * s * 2;
            }
        }
    }

    // ===================== отрисовка =====================
    static final int[][] WALLCOL = {
        {12, 4}, {15, 7}, {10, 2}, {9, 1}, {14, 6}, {14, 6}, {11, 3}};

    static int wallIdx(char c) {
        return c == '#' ? 0 : c == 'G' ? 1 : c == 'M' ? 2 : c == 'B' ? 3 : c == 'W' ? 4 : c == 'D' ? 5 : 6;
    }

    static double frac(double v) {
        return v - Math.floor(v);
    }

    /** Символ-«текстура» на закрашенной клетке стены (чёрным цветом поверх заливки). */
    static char texChar(int idx, double u, int row, double v, int step) {
        switch (idx) {
            case 0: {
                int br = (int) (v * 5);
                double bu = frac(u * 4 + (br % 2) * 0.5);
                if (frac(v * 5) < 0.2) return '_';
                if (bu < 0.12) return '|';
                return ' ';
            }
            case 1:
                if (frac(u * 3) < 0.08) return '|';
                if (v > 0.4 && v < 0.55 && frac(u * 3) > 0.25 && frac(u * 3) < 0.8) return '=';
                return ' ';
            case 2: {
                int h = ((int) (u * 12) * 31 + row * 17) % 7;
                return h == 0 ? '.' : h == 3 ? ':' : ' ';
            }
            case 3:
                if (frac(u * 5) < 0.1 && row % 3 == 0) return 'o';
                if (frac(v * 3) < 0.1) return '-';
                return ' ';
            case 4:
                return frac(u * 4) < 0.1 ? '|' : ' ';
            case 5:
                if (u > 0.78 && u < 0.88 && v > 0.45 && v < 0.6) return '*';
                return frac(v * 4) < 0.1 ? '-' : ' ';
            default: {
                if (v > 0.3 && v < 0.7 && u > 0.1 && u < 0.9) {
                    int k = (int) ((u - 0.1) / 0.2);
                    return "EXIT".charAt(Math.min(3, Math.max(0, k)));
                }
                return ' ';
            }
        }
    }

    static int darken(int c) {
        return c >= 8 ? c - 8 : (c == 0 ? 0 : 8);
    }

    void cell(int x, int y, char c, int attr) {
        vc[y * VW + x] = c;
        va[y * VW + x] = attr;
    }

    /** Рисует весь экран игры в прямоугольнике VW x (VH + HUDH) через out. */
    public void draw(Out o) {
        if (state == TITLE) {
            drawTitle(o);
            return;
        }
        renderView();
        for (int y = 0; y < VH; y++) {
            for (int x = 0; x < VW; x++) o.put(x, y, vc[y * VW + x], va[y * VW + x]);
        }
        drawHud(o);
        if (state == DEAD) drawBlood(o);
        if (state == DEAD) box(o, new String[] {"YOU DIED", "", "Press ENTER to try again"}, 4);
        else if (state == INTER) box(o, new String[] {"LEVEL " + level + " COMPLETE", "Kills: " + kills + " / " + total, "", level >= LEVELS ? "Press ENTER" : "Press ENTER for the next level"}, 2);
        else if (state == WON) box(o, new String[] {"YOU WIN!", "", "The Command Center is yours.", "Press ENTER"}, 10);
    }

    void box(Out o, String[] lines, int col) {
        int w = 0;
        for (String s : lines) w = Math.max(w, s.length());
        w += 4;
        int h = lines.length + 2, x0 = (VW - w) / 2, y0 = (VH - h) / 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) o.put(x0 + x, y0 + y, ' ', 16);
        }
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i];
            for (int k = 0; k < s.length(); k++) o.put(x0 + (w - s.length()) / 2 + k, y0 + 1 + i, s.charAt(k), col);
        }
    }

    void text(Out o, int x, int y, String s, int attr) {
        for (int i = 0; i < s.length() && x + i < VW; i++) o.put(x + i, y, s.charAt(i), attr);
    }

    void bar(Out o, int y, int attr) {
        for (int x = 0; x < VW; x++) o.put(x, y, ' ', attr);
    }

    void hbar(Out o, int x, int y, int w, int val, int col) {
        int n = (val * w + 99) / 100;
        if (val > 0 && n == 0) n = 1;
        for (int i = 0; i < w; i++) o.put(x + i, y, ' ', (i < n ? col : 8) | 16);
    }

    /** Кровь стекает по виду, когда игрок погиб. */
    void drawBlood(Out o) {
        for (int c = 0; c < VW; c++) {
            int lim = Math.min(VH, deadT + (c * 7) % 4 - 2);
            for (int y = 0; y < lim; y++) o.put(c, y, ' ', ((y + c) % 5 == 0 ? 12 : 4) | 16);
        }
    }

    void drawHud(Out o) {
        int y0 = VH;
        bar(o, y0, 7 | 16);
        text(o, 1, y0, "DOOM   Level " + level + "/" + LEVELS + " " + levelName(level), 7 | 16);
        String kl = "Kills " + kills + "/" + total;
        text(o, VW - kl.length() - 1, y0, kl, 7 | 16);
        for (int y = 1; y < HUDH; y++) bar(o, y0 + y, 0);
        int hc = hp > 50 ? 10 : hp > 25 ? 14 : 12;
        text(o, 1, y0 + 1, "HP", 7);
        hbar(o, 4, y0 + 1, 10, hp, hc);
        text(o, 15, y0 + 1, pad(hp + "%", 4), hc);
        text(o, 21, y0 + 1, "AR", 7);
        hbar(o, 24, y0 + 1, 8, armor, 9);
        text(o, 33, y0 + 1, pad(armor + "%", 4), armor > 0 ? 11 : 8);
        String face = hp > 66 ? "(^_^)" : hp > 33 ? "(o_o)" : hp > 0 ? "(x_x)" : "(X_X)";
        if (hurt > 0 && hp > 0) face = "(>_<)";
        text(o, 39, y0 + 1, face, hurt > 0 ? 12 : hc);
        text(o, 1, y0 + 2, "BUL", 7);
        text(o, 5, y0 + 2, pad(String.valueOf(bullets), 3), bullets > 0 ? 14 : 12);
        text(o, 10, y0 + 2, "SHL", 7);
        text(o, 14, y0 + 2, pad(String.valueOf(shells), 3), hasShotgun ? (shells > 0 ? 14 : 12) : 8);
        text(o, 19, y0 + 2, "[1]Pistol", weapon == 0 ? 15 : 8);
        text(o, 29, y0 + 2, "[2]Shotgun", hasShotgun ? (weapon == 1 ? 15 : 8) : 8);
        if (msg.length() > 0) text(o, 1, y0 + 3, msg, 14);
        else text(o, 1, y0 + 3, "Mouse: turn + click fire  WASD move  E use  M map", 8);
    }

    static String pad(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    static final String[] FONT_D = {"####.", "#...#", "#...#", "#...#", "####."};
    static final String[] FONT_O = {".###.", "#...#", "#...#", "#...#", ".###."};
    static final String[] FONT_M = {"#...#", "##.##", "#.#.#", "#...#", "#...#"};

    static final int[] TITLE_ROW = {14, 12, 12, 4, 4};

    void drawTitle(Out o) {
        for (int y = 0; y < VH + HUDH; y++) {
            for (int x = 0; x < VW; x++) o.put(x, y, ' ', 0);
        }
        String[][] letters = {FONT_D, FONT_O, FONT_O, FONT_M};
        for (int pass = 0; pass < 2; pass++) {
            for (int li = 0; li < 4; li++) {
                for (int ry = 0; ry < 5; ry++) {
                    for (int rx = 0; rx < 5; rx++) {
                        if (letters[li][ry].charAt(rx) != '#') continue;
                        int x = 1 + li * 12 + rx * 2, y = 1 + ry;
                        if (pass == 0) {
                            o.put(x + 1, y + 1, ' ', 8 | 16);
                            o.put(x + 2, y + 1, ' ', 8 | 16);
                        } else {
                            o.put(x, y, ' ', TITLE_ROW[ry] | 16);
                            o.put(x + 1, y, ' ', TITLE_ROW[ry] | 16);
                        }
                    }
                }
            }
        }
        text(o, 1, 8, "A tiny first-person shooter for PCOS / PixelOS", 7);
        text(o, 1, 9, "(own raycasting engine; not the original game)", 8);
        text(o, 1, 11, "MOUSE   cursor left/right turns, click fires", 11);
        text(o, 1, 12, "W S / Up Down  move        A D  strafe", 15);
        text(o, 1, 13, "SPACE fire     E / ENTER  open door, EXIT", 15);
        text(o, 1, 14, "1 / 2 weapon   M map   Q / Left / Right  turn", 15);
        text(o, 1, 16, "Find the red EXIT switch on each of 3 levels.", 14);
        text(o, 1, 17, "Press ENTER to start", 10);
        // огонь внизу заставки
        int[] fire = {4, 12, 14, 6};
        for (int x = 0; x < VW; x++) {
            int h = ((x * 73856093) ^ (frame * 19349663) ^ (x * frame * 83492791)) >>> 9 & 3;
            o.put(x, VH + HUDH - 1, ' ', fire[h] | 16);
            if (x >= 24 && h != 0) o.put(x, VH + HUDH - 2, ' ', fire[((h + frame) & 1) == 0 ? 0 : 1] | 16);
        }
    }

    // ---------- 3D-вид ----------
    void renderView() {
        double dirX = Math.cos(pa), dirY = Math.sin(pa);
        double plX = -dirY * FOVLEN, plY = dirX * FOVLEN;
        double lite = flash > 0 ? 1.45 : 1.0; // вспышка выстрела освещает комнату
        for (int c = 0; c < VW; c++) {
            double camX = 2.0 * (c + 0.5) / VW - 1;
            double rdx = dirX + plX * camX, rdy = dirY + plY * camX;
            int mx = (int) Math.floor(px), my = (int) Math.floor(py);
            double ddx = rdx == 0 ? 1e30 : Math.abs(1.0 / rdx), ddy = rdy == 0 ? 1e30 : Math.abs(1.0 / rdy);
            int sx = rdx < 0 ? -1 : 1, sy = rdy < 0 ? -1 : 1;
            double sdx = (rdx < 0 ? px - mx : mx + 1.0 - px) * ddx, sdy = (rdy < 0 ? py - my : my + 1.0 - py) * ddy;
            int side = 0;
            boolean hit = false;
            for (int i = 0; i < 80 && !hit; i++) {
                if (sdx < sdy) {
                    sdx += ddx;
                    mx += sx;
                    side = 0;
                } else {
                    sdy += ddy;
                    my += sy;
                    side = 1;
                }
                if (solid(mx, my)) hit = true;
            }
            double perp = side == 0 ? sdx - ddx : sdy - ddy;
            if (perp < 0.05) perp = 0.05;
            zbuf[c] = perp;
            double wx = side == 0 ? py + perp * rdy : px + perp * rdx;
            double u = frac(wx);
            char tc = mx < 0 || my < 0 || mx >= mw || my >= mh ? '#' : map[my][mx];
            int idx = wallIdx(tc);
            double pl = perp / lite;
            int step = pl < 3.2 ? 0 : pl < 6 ? 1 : pl < 9 ? 2 : 3;
            if (side == 1 && step < 3) step++;
            boolean edge = u < 0.04 || u > 0.96; // стык блоков стены
            double wh = VH * 1.1 / perp;
            double topF = (VH - wh) / 2.0;
            int top = (int) Math.round(topF), bot = (int) Math.round(topF + wh);
            for (int y = 0; y < VH; y++) {
                if (y < top) {
                    flat(c, y, rdx, rdy, true, lite);
                } else if (y < bot) {
                    double v = (y - topF + 0.5) / wh;
                    int shade = step;
                    if (shade == 0 && (edge || y == top || y == bot - 1)) shade = 1;
                    if (shade == 0) cell(c, y, texChar(idx, u, y - top, v, 0), WALLCOL[idx][0] | 16);
                    else if (shade == 1) cell(c, y, texChar(idx, u, y - top, v, 1), WALLCOL[idx][1] | 16);
                    else if (shade == 2) cell(c, y, ':', WALLCOL[idx][1]);
                    else cell(c, y, pl < 13 ? '.' : ' ', 8);
                } else {
                    flat(c, y, rdx, rdy, false, lite);
                }
            }
        }
        drawSprites(dirX, dirY, plX, plY);
        drawWeapon();
        crosshair();
        if (hurt > 0) edges(Math.min(3, hurt), 4);
        else if (bonus > 0) edges(1, bonusCol);
        if (mapOn) drawMiniMap();
    }

    /** Пол (с шахматной плиткой) и потолок (с лампами): проекция строки экрана на плоскость. */
    void flat(int c, int y, double rdx, double rdy, boolean ceil, double lite) {
        double yy = ceil ? VH / 2.0 - y - 0.5 : y - VH / 2.0 + 0.5;
        if (yy < 0.35) {
            cell(c, y, ' ', 0);
            return;
        }
        double rd = VH * 0.55 / yy;
        double fx = px + rd * rdx, fy = py + rd * rdy, d = rd / lite;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        int par = (ix + iy) & 1;
        if (ceil) {
            double ux = frac(fx), uy = frac(fy);
            boolean lamp = ux > 0.25 && ux < 0.75 && uy > 0.25 && uy < 0.75 && Math.floorMod(ix + iy * 3, 4) == 0;
            if (lamp && d < 11) cell(c, y, ' ', (d < 7 ? 15 : 7) | 16);
            else if (d < 4.5) cell(c, y, ' ', 1 | 16);
            else if (d < 8) cell(c, y, ':', 1);
            else cell(c, y, ' ', 0);
        } else {
            if (d < 2.8) cell(c, y, ' ', (par == 0 ? 6 : 8) | 16);
            else if (d < 5.5) cell(c, y, ':', par == 0 ? 6 : 8);
            else if (d < 9) cell(c, y, '.', 8);
            else cell(c, y, ' ', 0);
        }
    }

    void edges(int w, int col) {
        for (int y = 0; y < VH; y++) {
            for (int x = 0; x < w; x++) {
                cell(x, y, ' ', col | 16);
                cell(VW - 1 - x, y, ' ', col | 16);
            }
        }
    }

    /** Есть ли враг (или бочка) точно по линии прицела. */
    boolean aimed() {
        double rdx = Math.cos(pa), rdy = Math.sin(pa), wd = wallDist(px, py, rdx, rdy);
        for (Mob m : mobs) {
            if (m.state == 2) continue;
            double vx = m.x - px, vy = m.y - py, t = vx * rdx + vy * rdy;
            if (t < 0.15 || t > wd) continue;
            double rad = m.type == PINKY ? 0.42 : m.type == BARREL ? 0.3 : 0.32;
            if (Math.abs(vx * rdy - vy * rdx) < rad) return true;
        }
        return false;
    }

    void crosshair() {
        int i = (VH / 2 - 1) * VW + VW / 2, a = va[i];
        vc[i] = '+';
        if (aimed()) va[i] = 12;
        else if ((a & 16) != 0 && (a & 15) >= 8) va[i] = a; // чёрный плюс на яркой заливке
        else va[i] = 15;
    }

    void drawSprites(double dirX, double dirY, double plX, double plY) {
        List<Spr> list = new ArrayList<Spr>();
        for (Mob m : mobs) {
            Spr s = new Spr();
            s.tx = m.x;
            s.ty = m.y;
            double base = m.type == PINKY ? 1.0 : m.type == BARREL ? 0.6 : 0.95;
            int fr = m.atk > 0 ? 2 : m.state == 1 ? (frame / 2) % 2 : 0;
            if (m.state == 2) {
                if (m.type == BARREL) continue;
                if (m.dying < 4) {
                    s.art = frames(m.type)[0];
                    s.scale = base - (base - 0.22) * (m.dying / 4.0);
                    s.flash = 2;
                } else {
                    s.art = m.type == IMP ? A_CORPSE_IMP : m.type == PINKY ? A_CORPSE_PINKY : A_CORPSE_ZOMBIE;
                    s.scale = 0.22;
                }
            } else {
                s.art = frames(m.type)[fr];
                s.scale = base;
                s.flash = m.pain > 0 ? 1 : 0;
            }
            list.add(s);
        }
        int bobI = 0;
        for (Item it : items) {
            if (it.taken) continue;
            Spr s = new Spr();
            s.lift = 0.02 + 0.03 * (1 + Math.sin((frame + bobI++ * 3) / 2.5)); // предметы покачиваются
            s.tx = it.x;
            s.ty = it.y;
            s.art = it.kind == 'h' ? A_MEDKIT : it.kind == 'a' ? A_AMMO : it.kind == 'e' ? A_SHELLS : it.kind == 'r' ? A_ARMOR : A_GUN;
            s.scale = 0.28;
            list.add(s);
        }
        for (Ball b : balls) {
            Spr s = new Spr();
            s.tx = b.x;
            s.ty = b.y;
            s.art = A_BALL;
            s.scale = 0.22;
            s.lift = 0.3;
            list.add(s);
        }
        for (Fx f : fxs) {
            Spr s = new Spr();
            s.tx = f.x;
            s.ty = f.y;
            s.art = A_BOOM;
            s.scale = 1.1;
            s.lift = 0.05;
            list.add(s);
        }
        for (Spr s : list) {
            double rx = s.tx - px, ry = s.ty - py;
            double inv = 1.0 / (plX * dirY - dirX * plY);
            s.tx = inv * (dirY * rx - dirX * ry);
            s.ty = inv * (-plY * rx + plX * ry);
        }
        Collections.sort(list, new Comparator<Spr>() {
            public int compare(Spr a, Spr b) {
                return Double.compare(b.ty, a.ty);
            }
        });
        for (Spr s : list) {
            double ty = s.ty;
            if (ty < 0.25 || ty > 12) continue;
            int cx = (int) Math.round(VW / 2.0 * (1 + s.tx / ty));
            double full = VH * 1.1 / ty;
            int sh = (int) Math.round(full * s.scale), aw = s.art[0].length(), ah = s.art.length;
            if (sh < 1) sh = 1;
            int sw = (int) Math.round(sh * 1.5 * aw / ah);
            if (sw < 1) sw = 1;
            int bottom = (int) Math.round((VH + full) / 2.0 - full * s.lift);
            int y0 = bottom - sh, x0 = cx - sw / 2;
            for (int x = Math.max(0, x0); x < Math.min(VW, x0 + sw); x++) {
                if (ty >= zbuf[x]) continue;
                int ax = Math.min(aw - 1, (x - x0) * aw / sw);
                for (int y = Math.max(0, y0); y < Math.min(VH, y0 + sh); y++) {
                    int ay = Math.min(ah - 1, (y - y0) * ah / sh);
                    char pc = s.art[ay].charAt(ax);
                    if (pc == '.') continue;
                    int col = Character.digit(pc, 16);
                    if (s.flash == 2) col = col == 0 ? 0 : 12;
                    else if (s.flash > 0) col = 15;
                    else if (ty / (flash > 0 ? 1.45 : 1.0) > 7) col = darken(col);
                    cell(x, y, ' ', col | 16);
                }
            }
        }
    }

    static String[][] frames(int type) {
        return type == IMP ? new String[][] {A_IMP, A_IMP2, A_IMP_ATK}
                : type == PINKY ? new String[][] {A_PINKY, A_PINKY2, A_PINKY_ATK}
                : type == ZOMBIE ? new String[][] {A_ZOMBIE, A_ZOMBIE2, A_ZOMBIE_ATK}
                : new String[][] {A_BARREL, A_BARREL, A_BARREL};
    }

    void blit(String[] art, int x0, int y0) {
        for (int y = 0; y < art.length; y++) {
            for (int x = 0; x < art[y].length(); x++) {
                char pc = art[y].charAt(x);
                int xx = x0 + x, yy = y0 + y;
                if (pc == '.' || xx < 0 || yy < 0 || xx >= VW || yy >= VH) continue;
                cell(xx, yy, ' ', Character.digit(pc, 16) | 16);
            }
        }
    }

    void drawWeapon() {
        String[] art = weapon == 1 ? A_SHOTGUN : A_PISTOL;
        int w = art[0].length();
        double amp = Math.min(1.0, bobAmp / 2.0);
        int sway = (int) Math.round(Math.sin(bobT) * 2 * amp);
        int lift = (int) Math.round(Math.abs(Math.sin(bobT)) * amp);
        int recoil = cooldown > 2 ? 2 : cooldown > 0 ? 1 : 0;
        int x0 = VW / 2 - w / 2 + sway, y0 = VH - art.length + lift + recoil;
        blit(art, x0, y0);
        if (flash > 0) blit(A_FLASH, VW / 2 - A_FLASH[0].length() / 2 + sway, y0 - A_FLASH.length);
    }

    void drawMiniMap() {
        int rx = 10, ry = 5;
        int pcx = (int) Math.floor(px), pcy = (int) Math.floor(py);
        for (int dy = -ry; dy <= ry; dy++) {
            for (int dx = -rx; dx <= rx; dx++) {
                int x = pcx + dx, y = pcy + dy;
                int sx = 1 + dx + rx, sy = 1 + dy + ry;
                if (sy >= VH) continue;
                char c = x < 0 || y < 0 || x >= mw || y >= mh ? '#' : map[y][x];
                int at = c == '.' || c == 'd' ? 0 : c == 'D' ? 14 | 16 : c == 'X' ? 10 | 16 : 7 | 16;
                cell(sx, sy, ' ', at);
            }
        }
        for (Mob m : mobs) {
            if (m.state == 2) continue;
            int sx = 1 + (int) Math.floor(m.x) - pcx + rx, sy = 1 + (int) Math.floor(m.y) - pcy + ry;
            if (sx >= 1 && sx <= 2 * rx + 1 && sy >= 1 && sy <= 2 * ry + 1 && sy < VH) cell(sx, sy, m.type == BARREL ? 'o' : '!', 12);
        }
        int q = (int) Math.round(pa / (Math.PI / 4)) % 8;
        char[] arrows = {'>', '\\', 'v', '/', '<', '\\', '^', '/'};
        char arrow = arrows[q];
        cell(1 + rx, 1 + ry, arrow, 15);
    }
}
