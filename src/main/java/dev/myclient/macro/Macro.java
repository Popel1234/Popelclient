package dev.myclient.macro;

public class Macro {
    public String name;
    public int keyCode;
    public String command;
    public int repeatEveryTicks;
    public boolean enabled = true;
    public Macro(String name, int keyCode, String command, int repeat) {
        this.name = name; this.keyCode = keyCode; this.command = command; this.repeatEveryTicks = repeat;
    }
}
