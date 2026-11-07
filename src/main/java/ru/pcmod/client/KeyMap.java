package ru.pcmod.client;

import org.lwjgl.glfw.GLFW;

/**
 * Серверная логика (ComputerLogic, Desktop, DoomGame) работает с кодами клавиш LWJGL 2 (Keyboard.KEY_*), поэтому клавиши
 * GLFW переводятся в них. Печатные символы приходят отдельно (charTyped), как поле char в keyTyped 1.12.
 */
final class KeyMap {
    private KeyMap() {
    }

    /** A..Z в кодах LWJGL 2. */
    private static final int[] LETTERS = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};

    /** Код LWJGL 2 для клавиши GLFW; 0 - клавиша серверу не нужна. */
    static int lwjgl(int k) {
        if (k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z) return LETTERS[k - GLFW.GLFW_KEY_A];
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9) return 2 + (k - GLFW.GLFW_KEY_1);
        if (k >= GLFW.GLFW_KEY_KP_0 && k <= GLFW.GLFW_KEY_KP_9) return new int[]{82, 79, 80, 81, 75, 76, 77, 71, 72, 73}[k - GLFW.GLFW_KEY_KP_0];
        if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F10) return 59 + (k - GLFW.GLFW_KEY_F1);
        switch (k) {
            case GLFW.GLFW_KEY_0: return 11;
            case GLFW.GLFW_KEY_SPACE: return 57;
            case GLFW.GLFW_KEY_APOSTROPHE: return 40;
            case GLFW.GLFW_KEY_COMMA: return 51;
            case GLFW.GLFW_KEY_MINUS: return 12;
            case GLFW.GLFW_KEY_PERIOD: return 52;
            case GLFW.GLFW_KEY_SLASH: return 53;
            case GLFW.GLFW_KEY_SEMICOLON: return 39;
            case GLFW.GLFW_KEY_EQUAL: return 13;
            case GLFW.GLFW_KEY_LEFT_BRACKET: return 26;
            case GLFW.GLFW_KEY_BACKSLASH: return 43;
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return 27;
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return 41;
            case GLFW.GLFW_KEY_KP_DECIMAL: return 83;
            case GLFW.GLFW_KEY_KP_DIVIDE: return 181;
            case GLFW.GLFW_KEY_KP_MULTIPLY: return 55;
            case GLFW.GLFW_KEY_KP_SUBTRACT: return 74;
            case GLFW.GLFW_KEY_KP_ADD: return 78;
            case GLFW.GLFW_KEY_KP_ENTER: return 156;
            case GLFW.GLFW_KEY_ENTER: return 28;
            case GLFW.GLFW_KEY_BACKSPACE: return 14;
            case GLFW.GLFW_KEY_TAB: return 15;
            case GLFW.GLFW_KEY_DELETE: return 211;
            case GLFW.GLFW_KEY_INSERT: return 210;
            case GLFW.GLFW_KEY_HOME: return 199;
            case GLFW.GLFW_KEY_END: return 207;
            case GLFW.GLFW_KEY_PAGE_UP: return 201;
            case GLFW.GLFW_KEY_PAGE_DOWN: return 209;
            case GLFW.GLFW_KEY_UP: return 200;
            case GLFW.GLFW_KEY_DOWN: return 208;
            case GLFW.GLFW_KEY_LEFT: return 203;
            case GLFW.GLFW_KEY_RIGHT: return 205;
            case GLFW.GLFW_KEY_F11: return 87;
            case GLFW.GLFW_KEY_F12: return 88;
            default: return 0;
        }
    }

    /** Клавиша даёт символ (приходит в charTyped): буквы, цифры, знаки, пробел, цифровой блок. */
    static boolean printable(int k) {
        return k == GLFW.GLFW_KEY_SPACE || k >= GLFW.GLFW_KEY_APOSTROPHE && k <= GLFW.GLFW_KEY_GRAVE_ACCENT
                && k != GLFW.GLFW_KEY_ESCAPE || k >= GLFW.GLFW_KEY_KP_0 && k <= GLFW.GLFW_KEY_KP_ADD;
    }

    /** Клавиши-модификаторы сами по себе серверу не отправляются. */
    static boolean modifier(int k) {
        return k == GLFW.GLFW_KEY_LEFT_SHIFT || k == GLFW.GLFW_KEY_RIGHT_SHIFT || k == GLFW.GLFW_KEY_LEFT_CONTROL
                || k == GLFW.GLFW_KEY_RIGHT_CONTROL || k == GLFW.GLFW_KEY_LEFT_ALT || k == GLFW.GLFW_KEY_RIGHT_ALT
                || k == GLFW.GLFW_KEY_CAPS_LOCK;
    }

    /** Символ, который 1.12 отдавал в keyTyped для непечатных клавиш. */
    static char special(int k) {
        switch (k) {
            case GLFW.GLFW_KEY_ENTER: case GLFW.GLFW_KEY_KP_ENTER: return '\r';
            case GLFW.GLFW_KEY_BACKSPACE: return '\b';
            case GLFW.GLFW_KEY_TAB: return '\t';
            default: return 0;
        }
    }
}
