package dev.myclient.module;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Draws see-through boxes around ores (or any blocks you pick) so you can spot them through walls. */
public class XRay extends Module {
    public static final List<String> DEFAULTS = List.of(
        "minecraft:coal_ore", "minecraft:deepslate_coal_ore", "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
        "minecraft:copper_ore", "minecraft:deepslate_copper_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
        "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore", "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
        "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
        "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore", "minecraft:ancient_debris");

    public final Set<String> blocks = new LinkedHashSet<>(DEFAULTS);

    public XRay() { super("X-Ray"); }

    public boolean toggleBlock(String id) {
        boolean added = !blocks.remove(id);
        if (added) blocks.add(id);
        if (added && !isEnabled()) setEnabled(true);
        return added;
    }
}
