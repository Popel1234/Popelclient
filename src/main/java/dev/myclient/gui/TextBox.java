package dev.myclient.gui;

import java.util.function.Consumer;

/** Minimal single-line text input drawn by our own UI (no vanilla widget). */
public final class TextBox {
    public String text = "";
    public int cursor;
    public final String placeholder;
    public int max = 120;
    public Consumer<String> onChange = s -> {};

    public TextBox(String placeholder) { this.placeholder = placeholder; }

    public void set(String s) {
        text = s;
        cursor = s.length();
        onChange.accept(text);
    }

    public void type(char c) {
        if (c < 32 || c == 127 || text.length() >= max) return;
        text = text.substring(0, cursor) + c + text.substring(cursor);
        cursor++;
        onChange.accept(text);
    }

    /** Returns true if the key was used. */
    public boolean key(int key, int mods, String clipboard) {
        switch (key) {
            case 259 -> { // backspace
                if (cursor > 0) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                    onChange.accept(text);
                }
                return true;
            }
            case 261 -> { // delete
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                    onChange.accept(text);
                }
                return true;
            }
            case 263 -> { cursor = Math.max(0, cursor - 1); return true; }
            case 262 -> { cursor = Math.min(text.length(), cursor + 1); return true; }
            case 268 -> { cursor = 0; return true; }
            case 269 -> { cursor = text.length(); return true; }
            case 86 -> { // ctrl+V
                if ((mods & 2) != 0 && clipboard != null) {
                    for (char c : clipboard.toCharArray()) type(c);
                    return true;
                }
                return false;
            }
            default -> { return false; }
        }
    }
}
