package dev.myclient.cosmetic;

import dev.myclient.cosmetic.Cosmetic.Animation;
import dev.myclient.cosmetic.Cosmetic.Type;
import java.util.*;

public class CosmeticManager {
    public final List<Cosmetic> catalog = new ArrayList<>();
    public final Set<String> equipped = new LinkedHashSet<>();
    public final List<String> skies = new ArrayList<>(List.of("Default", "Sunset", "Galaxy", "Aurora"));
    public String activeSky = "Default";
    public long ticks;

    public CosmeticManager() {
        catalog.add(new Cosmetic("halo_classic", Type.HALO, List.of("gold", "ice", "neon"), Animation.FLOAT));
        catalog.add(new Cosmetic("halo_spin", Type.HALO, List.of("white", "purple"), Animation.SPIN));
        catalog.add(new Cosmetic("cape_flow", Type.CAPE, List.of("crimson", "ocean", "void"), Animation.WAVE));
        catalog.add(new Cosmetic("cape_rainbow", Type.CAPE, List.of("default"), Animation.RAINBOW));
        catalog.add(new Cosmetic("headband_pulse", Type.HEADBAND, List.of("red", "blue", "green"), Animation.PULSE));
    }

    public void tick() { ticks++; }

    public void toggle(Cosmetic c) {
        boolean wasOn = equipped.contains(c.id);
        catalog.stream().filter(o -> o.type == c.type).forEach(o -> equipped.remove(o.id));
        if (!wasOn) equipped.add(c.id);
    }
    public boolean isEquipped(Cosmetic c) { return equipped.contains(c.id); }
}
