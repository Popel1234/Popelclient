package dev.myclient.module;

import net.minecraft.client.MinecraftClient;
import java.util.List;

public class ModuleManager {
    public final Fullbright fullbright = new Fullbright();
    public final MotionBlur motionBlur = new MotionBlur();
    public final Tracers tracers = new Tracers();
    public final XRay xray = new XRay();
    public final Freecam freecam = new Freecam();
    public final AutoTotem autoTotem = new AutoTotem();
    public final AirPlace airPlace = new AirPlace();
    public final Reach reach = new Reach();
    public final List<Module> all = List.of(fullbright, motionBlur, tracers, xray, freecam, autoTotem, airPlace, reach);
    public void tick(MinecraftClient mc) { for (Module m : all) m.tick(mc); }
}
