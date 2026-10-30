package dev.myclient.module;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Detached camera: your character stands still (its movement input is switched off) while the camera
 * follows your mouse and flies with W/A/S/D, Space (up), Shift (down) and Ctrl (fast).
 */
public class Freecam extends Module {
    private OtherClientPlayerEntity cam;
    private Input savedInput;
    private double x, y, z;
    private Perspective savedPerspective = Perspective.FIRST_PERSON;
    private long last;

    public Freecam() {
        super("Freecam");
        configure("Speed", 0.3f, 4f, 1f, "x");
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) { setEnabled(false); return; }
        cam = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "Freecam"));
        Vec3d eye = mc.player.getEyePos();
        x = eye.x; y = eye.y; z = eye.z;
        savedInput = mc.player.input;
        mc.player.input = new Input();
        savedPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.FIRST_PERSON);
        apply(mc.player.getYaw(), mc.player.getPitch());
        mc.setCameraEntity(cam);
        last = System.nanoTime();
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            if (savedInput != null) mc.player.input = savedInput;
            mc.setCameraEntity(mc.player);
        }
        mc.options.setPerspective(savedPerspective);
        cam = null;
        savedInput = null;
    }

    private void apply(float yaw, float pitch) {
        cam.setPosition(x, y - 1.62, z);
        cam.prevX = cam.lastRenderX = x;
        cam.prevY = cam.lastRenderY = y - 1.62;
        cam.prevZ = cam.lastRenderZ = z;
        cam.setYaw(yaw);
        cam.prevYaw = yaw;
        cam.setPitch(pitch);
        cam.prevPitch = pitch;
    }

    private boolean down(MinecraftClient mc, KeyBinding kb) {
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), KeyBindingHelper.getBoundKeyOf(kb).getCode());
    }

    /** Kept for compatibility: the player's input is already replaced, nothing to suppress. */
    public void suppress(MinecraftClient mc) { }

    /** Called every rendered frame: the camera simply copies the mouse look and flies. */
    public void frame(MinecraftClient mc) {
        if (!isEnabled() || cam == null) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) { setEnabled(false); return; }
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1.0e9f);
        last = now;

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
        apply(yaw, pitch);
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (isEnabled() && (mc.world == null || mc.player == null)) setEnabled(false);
    }
}
