package dev.myclient.module;

/**
 * Settings holder. The actual blur needs a post-processing pass (frame accumulation):
 * hook the end of GameRenderer.render with a mixin, blend the previous frame's texture
 * into the main framebuffer using `strength` as the blend factor. That code differs
 * between 1.21.x render-pipeline versions, so it belongs in per-version mixin modules.
 */
public class MotionBlur extends Module {
    public float strength = 0.5f; // 0..0.95
    public MotionBlur() { super("Motion Blur"); }
}
