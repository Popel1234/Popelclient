package dev.myclient.macro;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public class MacroManager {
    public final List<Macro> macros = new ArrayList<>();
    private final Set<Integer> held = new HashSet<>();
    private int tick;

    public void tick(MinecraftClient mc) {
        tick++;
        if (mc.player == null || mc.currentScreen != null) return;
        long window = mc.getWindow().getHandle();
        for (Macro m : macros) {
            if (!m.enabled || m.command == null || m.command.isBlank()) continue;
            boolean down = GLFW.glfwGetKey(window, m.keyCode) == GLFW.GLFW_PRESS;
            boolean fresh = down && held.add(m.keyCode);
            if (!down) held.remove(m.keyCode);
            boolean repeat = down && m.repeatEveryTicks > 0 && tick % m.repeatEveryTicks == 0;
            if (fresh || repeat) send(mc, m.command.trim());
        }
    }

    private void send(MinecraftClient mc, String text) {
        if (text.startsWith("/")) mc.player.networkHandler.sendChatCommand(text.substring(1));
        else mc.player.networkHandler.sendChatMessage(text);
    }
}
