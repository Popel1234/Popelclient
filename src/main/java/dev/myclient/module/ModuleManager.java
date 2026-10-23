package dev.myclient.module;

import net.minecraft.client.MinecraftClient;
import java.util.List;

public class ModuleManager {
    public final Fullbright fullbright = new Fullbright();
    public final MotionBlur motionBlur = new MotionBlur();
    public final Tracers tracers = new Tracers();
    public final List<Module> all = List.of(fullbright, motionBlur, tracers);
    public void tick(MinecraftClient mc) { for (Module m : all) m.tick(mc); }
}
