package com.zafiro.wxentry.kernel;

import android.content.Context;

/** Colors for the module menu, selected from the host Activity's effective theme. */
final class MenuPalette {
    static final MenuPalette LIGHT = new MenuPalette(
            0xFFFFFFFF, 0xFFF6F6F8, 0xFFEDEBFC, 0xFF20212A, 0xFF656873,
            0xFF5146BC, 0x150B1026, 0xFF655BD9, 0xFF574DC7, 0xFFE8E9EF, 0xFFAFB1BC, 0xFFE5E6EB);
    static final MenuPalette DARK = new MenuPalette(
            0xFF232428, 0xFF1B1C20, 0xFF32304D, 0xFFF4F4F6, 0xFFADAFBA,
            0xFFBAB5FF, 0x1EFFFFFF, 0xFF756BE8, 0xFF938BF5, 0xFF36373E, 0xFF72747F, 0xFF383940);

    final int surface, card, highlight, text, muted, accent, ripple;
    final int switchOn, switchOnEdge, switchOff, switchOffEdge, line;

    private MenuPalette(int surface, int card, int highlight, int text, int muted,
                        int accent, int ripple, int switchOn, int switchOnEdge, int switchOff, int switchOffEdge,
                        int line) {
        this.surface = surface;
        this.card = card;
        this.highlight = highlight;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
        this.ripple = ripple;
        this.switchOn = switchOn;
        this.switchOnEdge = switchOnEdge;
        this.switchOff = switchOff;
        this.switchOffEdge = switchOffEdge;
        this.line = line;
    }

    static MenuPalette current(Context context) {
        return WechatAppearance.isDark(context) ? DARK : LIGHT;
    }
}
