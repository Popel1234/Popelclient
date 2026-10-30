package dev.myclient.gui;

import dev.myclient.Settings;

/** Colour + corner presets for the menu. */
public final class Theme {
    private Theme() {}

    public static final class T {
        public final String name;
        public final float radius;
        public final int bg1, bg2, panel, border, border2, card, cardHover, tile, accent, accentHover, accent2,
            text, muted, off, danger, btn, btnHover, hover, onAccent, dockBg, dockPill, chip, dim;

        T(String name, float radius, int[] c) {
            this.name = name; this.radius = radius;
            bg1 = c[0]; bg2 = c[1]; panel = c[2]; border = c[3]; border2 = c[4]; card = c[5]; cardHover = c[6]; tile = c[7];
            accent = c[8]; accentHover = c[9]; accent2 = c[10]; text = c[11]; muted = c[12]; off = c[13]; danger = c[14];
            btn = c[15]; btnHover = c[16]; hover = c[17]; onAccent = c[18]; dockBg = c[19]; dockPill = c[20]; chip = c[21]; dim = c[22];
        }
    }

    public static final T[] ALL = {
        // squared, warm black + orange
        new T("Ember", 0.35f, new int[]{0x60000000, 0x90140800, 0xF3120D0A, 0xFF43301F, 0xFF3A2A1E, 0xFF1E1713, 0xFF2B2018, 0xFF0E0A08,
            0xFFFF8A2B, 0xFFFFA24F, 0xFFFFD166, 0xFFF6EDE4, 0xFFA08C7A, 0xFF4A3A2E, 0xFFFF4D4D,
            0xFF2A2018, 0xFF3A2C20, 0xFF30241A, 0xFF1A0F05, 0xF00F0A07, 0xFF1E1713, 0xFF2E2218, 0xFFC9B9A8}),
        // rounded, deep sea teal
        new T("Ocean", 1.3f, new int[]{0x50021018, 0x80041A2A, 0xF30A1722, 0xFF1E3A4C, 0xFF1A3344, 0xFF11212E, 0xFF182E40, 0xFF08111A,
            0xFF2EC4B6, 0xFF58DCCF, 0xFF6FB7FF, 0xFFE6F4F8, 0xFF7E9DAB, 0xFF2D4656, 0xFFFF6B7A,
            0xFF173044, 0xFF21425A, 0xFF1D3A50, 0xFF05201E, 0xF0081520, 0xFF11212E, 0xFF1B3346, 0xFFB5CBD6}),
        // light, soft
        new T("Frost", 1.0f, new int[]{0x40FFFFFF, 0x60DCE6F5, 0xF4F4F7FC, 0xFFC8D2E4, 0xFFD3DBEA, 0xFFFFFFFF, 0xFFE9EFFA, 0xFFE4EAF5,
            0xFF4C6FFF, 0xFF6F8CFF, 0xFF00A6C8, 0xFF1E2433, 0xFF6B7690, 0xFFC3CCDD, 0xFFE5484D,
            0xFFE3E9F6, 0xFFD3DCF0, 0xFFE9EFFA, 0xFFFFFFFF, 0xF0FFFFFF, 0xFFE3E9F6, 0xFFDCE4F5, 0xFF4A5470}),
        // the previous look
        new T("Violet", 1.0f, new int[]{0x50050508, 0x80100820, 0xF2101018, 0xFF25253A, 0xFF2A2A40, 0xFF181824, 0xFF212132, 0xFF0C0C14,
            0xFFB06CFF, 0xFFC38BFF, 0xFF55D6FF, 0xFFECECF6, 0xFF8A8AA6, 0xFF34344A, 0xFFFF5470,
            0xFF222234, 0xFF2C2C42, 0xFF262638, 0xFF14101E, 0xE60E0E16, 0xFF161622, 0xFF26263E, 0xFFB8B8D0})
    };

    public static T current() {
        return ALL[Math.floorMod(Settings.theme, ALL.length)];
    }
}
