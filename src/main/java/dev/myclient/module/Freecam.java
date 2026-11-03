package dev.myclient.module;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Detached camera. The game camera is moved by a small hook (CameraMixin) every frame: it looks where your mouse
 * looks and flies with W/A/S/D, Space (up), Shift (down) and Ctrl (fast). Your character stays where it is.
 */
public class Freecam extends Module {
    public volatile boolean hooked;
    private Input savedInput;
    private double x, y, z;
    private Perspective savedPerspective = Perspective.FIRST_PERSON;
    private long last;
    private int ticks;

    public Freecam() {
        super("Freecam");
        configure("Speed", 0.3f, 4f, 1f, "x");
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) { setEnabled(false); return; }
        Vec3d eye = mc.player.getEyePos();
        x = eye.x; y = eye.y; z = eye.z;
        savedInput = mc.player.input;
        mc.player.input = new Input();
        savedPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);   // so your own character is drawn
        last = System.nanoTime();
        hooked = false;
        ticks = 0;
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && savedInput != null) mc.player.input = savedInput;
        mc.options.setPerspective(savedPerspective);
        savedInput = null;
    }

    private boolean down(MinecraftClient mc, KeyBinding kb) {
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), KeyBindingHelper.getBoundKeyOf(kb).getCode());
    }

    /** Kept for compatibility with older code. */
    public void suppress(MinecraftClient mc) { }

    public void frame(MinecraftClient mc) { }

    /** Called by the camera hook every frame. Returns {x, y, z, yaw, pitch} or null. */
    public double[] step(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null) return null;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;
        ClientPlayerEntity p = mc.player;
        float yaw = p.getYaw(), pitch = p.getPitch();
        if (mc.currentScreen == null) {
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
            double rx = -Math.cos(yr), rz = -Math.sin(yr);
            double mx = 0, my = 0, mz = 0;
            if (down(mc, mc.options.forwardKey)) { mx += fx; my += fy; mz += fz; }
            if (down(mc, mc.options.backKey)) { mx -= fx; my -= fy; mz -= fz; }
            if (down(mc, mc.options.rightKey)) { mx += rx; mz += rz; }
            if (down(mc, mc.options.leftKey)) { mx -= rx; mz -= rz; }
            if (down(mc, mc.options.jumpKey)) my += 1;
            if (down(mc, mc.options.sneakKey)) my -= 1;
            double len = Math.sqrt(mx * mx + my * my + mz * mz);
            if (len > 0) {
                double boost = down(mc, mc.options.sprintKey) ? 2.5 : 1.0;
                double sp = 10.0 * settingValue * boost * dt / len;
                x += mx * sp; y += my * sp; z += mz * sp;
            }
        }
        return new double[]{x, y, z, yaw, pitch};
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (!isEnabled()) return;
        if (mc.world == null || mc.player == null) { setEnabled(false); return; }
        if (++ticks == 60 && !hooked) {
            mc.player.sendMessage(Text.literal("[Lumen] Freecam: the camera hook did not load in this game version - tell the developer."), false);
        }
    }
}
