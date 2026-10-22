package dev.myclient.module;

import net.minecraft.client.MinecraftClient;

public abstract class Module {
    public final String name;
    private boolean enabled;
    protected Module(String name) { this.name = name; }
    public boolean isEnabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        if (on) onEnable(); else onDisable();
    }
    protected void onEnable() {}
    protected void onDisable() {}
    public void tick(MinecraftClient mc) {}
}
