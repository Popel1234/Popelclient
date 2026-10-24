package dev.myclient.module;

import net.minecraft.client.MinecraftClient;

public abstract class Module {
    public final String name;
    private boolean enabled;

    // optional slider shown under the module in the menu
    public String settingLabel;
    public float settingMin, settingMax, settingValue;
    public String settingUnit = "";

    protected Module(String name) { this.name = name; }

    protected void configure(String label, float min, float max, float value, String unit) {
        settingLabel = label; settingMin = min; settingMax = max; settingValue = value; settingUnit = unit;
    }

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
