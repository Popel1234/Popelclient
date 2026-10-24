package dev.myclient.cosmetic;

import java.util.List;

/** The accessory being built in the Editor window, shown live on your character. */
public final class Preview {
    private Preview() {}

    public static boolean enabled;
    public static Cosmetic draft;

    public static void update(Cosmetic.Type type, Cosmetic.Animation anim, int rgb, float speed) {
        if (draft == null || draft.type != type) {
            draft = new Cosmetic("preview", type, List.of("custom"), Cosmetic.defaultAnims(type));
            draft.custom = true;
        }
        draft.animation = anim;
        draft.tintRgb = rgb;
        draft.speed = speed;
    }
}
